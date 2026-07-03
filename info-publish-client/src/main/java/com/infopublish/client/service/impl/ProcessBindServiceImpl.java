package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig.ProcessBindProperties;
import com.infopublish.client.event.ProcessGuardEvent;
import com.infopublish.client.service.ProcessBindService;
import com.infopublish.client.service.UdpPortOwnerService;
import com.infopublish.client.service.WindivertMonitorService;
import com.infopublish.client.utils.Md5Util;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 进程绑定服务实现
 *
 * <p>核心策略：PID 动态绑定 + WinDivert/GetExtendedUdpTable 实时 UDP 端口归属校验
 * 1. UKey 认证成功时，通过 FindFirstFileW / FindNextFileW 在受控目录发现目标文件，
 *    再校验绝对路径与 MD5，之后自动拉起/查询目标进程并绑定 PID
 * 2. 校验时优先通过 WinDivert Monitor 查询来源端口 PID；未启用或未命中时回退 GetExtendedUdpTable
 * 3. 端口匹配授权 PID → 放行；端口属于其他 PID → 拒绝；端口已关闭 → 检查 IP 白名单
 *
 * <p>严格模式（无降级）：authorized=true 只能通过 PID 校验返回。
 * PID 未绑定、进程不存在时均返回 false（拒绝）。
 */
@Slf4j
@Service
public class ProcessBindServiceImpl implements ProcessBindService {

    @Value("${process-bind.endpoint-source-validation-enabled:false}")
    private boolean endpointSourceValidationEnabled;

    @Value("${process-bind.periodic-integrity-recheck-enabled:false}")
    private boolean periodicProcessIntegrityRecheckEnabled;

    private static final int MAX_PATH = 260;
    private static final int INVALID_HANDLE_VALUE = -1;
    private static final int FILE_ATTRIBUTE_DIRECTORY = 0x10;
    private static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x400;
    private static final int ERROR_NO_MORE_FILES = 18;
    private static final long PROCESS_CANDIDATE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final String DEFAULT_PROCESS_PICKER_TASK_NAME = "InfoPublishClientSigmaPicker";
    private static final String DEFAULT_SIGMA_LAUNCHER_TASK_NAME = "InfoPublishClientSigmaLauncher";
    private static final String SIGMA_LAUNCH_REQUEST_FILE = "agent\\sigma-launch-request.json";
    private static final Pattern PROCESS_PICKER_TASK_PATTERN =
            Pattern.compile("\"processPickerTaskName\"\\s*:\\s*\"([^\"]+)\"");

    @Resource
    private ProcessBindProperties properties;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    /**
     * 当前授权 PID，volatile 保证多线程可见性
     * -1L 表示未绑定或目标进程未找到
     */
    private volatile long authorizedPid = -1L;

    /**
     * 合法来源 IP 集合（本机所有 IPv4 地址）
     * Sigma Play 发 UDP 使用临时端口，无法通过 netstat 捕获端口，因此只维护 IP 白名单。
     * 用 volatile + 不可变集合替换保证线程安全
     */
    private volatile Set<String> authorizedEndpoints = Collections.emptySet();

    @Resource
    private UdpPortOwnerService udpPortOwnerService;

    @Resource
    private WindivertMonitorService windivertMonitorService;

    private final Object bindingLock = new Object();
    private final Object guardLock = new Object();
    private final Object candidateLock = new Object();
    private final Map<String, CandidateCacheEntry> processCandidateCache = new LinkedHashMap<>();

    private volatile long guardedPid = -1L;
    private volatile boolean guardEventPublished = false;
    private volatile Process wmiGuardProcess;

    private volatile long pidAliveCachePid = -1L;
    private volatile boolean pidAliveCacheValue = false;
    private volatile long pidAliveCacheExpireAt = 0L;

    private volatile BoundProcessSnapshot boundProcessSnapshot;
    private volatile ProcessIntegrityCacheEntry integrityCacheEntry;
    private final Object integrityLock = new Object();

    // ========== 接口实现 ==========

    @Override
    public long resolveTargetPid() {
        if (!properties.isEnabled()) {
            log.debug("[进程绑定] 功能未启用，跳过 PID 查找");
            return -1L;
        }

        File executableFile = discoverAndValidateTargetExecutable();
        if (executableFile == null) {
            return -1L;
        }

        return resolveTargetPidForExecutable(executableFile);
    }

    @Override
    public ProcessCandidatesResult scanProcessCandidates() {
        String targetName = getEffectiveTargetProcessName(getConfiguredTargetFileReference());
        List<ProcessCandidateInfo> candidates = getCachedCandidateInfos();

        return new ProcessCandidatesResult(
                targetName,
                "",
                Collections.emptyList(),
                Collections.unmodifiableList(candidates),
                authorizedPid
        );
    }

    @Override
    public ProcessSelectionResult requestProcessCandidatePicker() {
        if (!properties.isEnabled()) {
            return new ProcessSelectionResult(false, -1L, "进程绑定功能未启用", null);
        }

        File pickerScript = resolveProcessPickerScript();
        if (pickerScript == null || !pickerScript.isFile()) {
            log.warn("[进程选择] watcher helper 脚本不存在，无法打开 Sigma 文件选择器");
            return new ProcessSelectionResult(false, -1L, "Sigma 程序选择器不存在，请检查 watcher 文件", null);
        }

        try {
            boolean startedByTask = runProcessPickerTask(pickerScript);
            if (!startedByTask) {
                startProcessPickerScript(pickerScript);
            }
            return new ProcessSelectionResult(true, -1L, "已打开 Sigma 程序选择窗口", null);
        } catch (Exception e) {
            log.warn("[进程选择] 启动 watcher helper 失败: {}", e.getMessage());
            return new ProcessSelectionResult(false, -1L, "打开 Sigma 程序选择窗口失败", null);
        }
    }

    @Override
    public ProcessSelectionResult addProcessCandidateFromHelper(String selectedPath) {
        if (!properties.isEnabled()) {
            return new ProcessSelectionResult(false, -1L, "进程绑定功能未启用", null);
        }
        if (selectedPath == null || selectedPath.trim().isEmpty()) {
            return new ProcessSelectionResult(false, -1L, "未选择 Sigma 程序", null);
        }

        File selectedFile = normalizeSelectedExecutableFile(new File(selectedPath.trim()));
        if (!selectedFile.isFile()) {
            return new ProcessSelectionResult(false, -1L, "非法 Sigma 程序", null);
        }

        ProcessCandidateInfo candidate = registerProcessCandidate(selectedFile);
        return new ProcessSelectionResult(true, -1L, "Sigma 程序候选已添加", candidate);
    }
    @Override
    public ProcessSelectionResult selectAndStartCandidate(String candidateId) {
        if (!properties.isEnabled()) {
            return new ProcessSelectionResult(false, -1L, "进程绑定功能未启用", null);
        }
        if (candidateId == null || candidateId.trim().isEmpty()) {
            return new ProcessSelectionResult(false, -1L, "非法 Sigma 程序或选择已过期", null);
        }

        CandidateCacheEntry entry = getCandidateEntry(candidateId.trim());
        if (entry == null) {
            return new ProcessSelectionResult(false, -1L, "非法 Sigma 程序或选择已过期", null);
        }

        ProcessCandidateInfo selectedCandidate = buildPickedProcessCandidateInfo(entry.candidateId, entry.executableFile);
        updateCandidateEntry(selectedCandidate, entry.executableFile);
        if (!selectedCandidate.isValid()) {
            return new ProcessSelectionResult(false, -1L, "非法 Sigma 程序", selectedCandidate);
        }

        File executableFile = entry.executableFile;
        if (!executableFile.isFile()) {
            return new ProcessSelectionResult(false, -1L, "非法 Sigma 程序或选择已过期", selectedCandidate);
        }

        long pid = resolveTargetPidForExecutable(executableFile);
        if (pid <= 0) {
            pid = startTargetProcessAndWaitForPid(executableFile);
        }
        if (pid <= 0) {
            pid = resolveRecentlyStartedPidByProcessName(executableFile, true);
        }

        if (pid <= 0) {
            return new ProcessSelectionResult(false, -1L,
                    "合法 Sigma 程序启动后未完成 PID 绑定", selectedCandidate);
        }

        applyBinding(pid);
        ProcessCandidateInfo updatedCandidate = buildPickedProcessCandidateInfo(entry.candidateId, executableFile);
        clearProcessCandidateCache();
        return new ProcessSelectionResult(true, pid,
                "Sigma 程序已启动并完成 PID 绑定", updatedCandidate);
    }

    @Override
    public void clearProcessCandidateCache() {
        synchronized (candidateLock) {
            processCandidateCache.clear();
        }
    }

    private List<ProcessCandidateInfo> getCachedCandidateInfos() {
        synchronized (candidateLock) {
            pruneExpiredCandidatesLocked();
            List<ProcessCandidateInfo> candidates = new ArrayList<>();
            for (CandidateCacheEntry entry : processCandidateCache.values()) {
                candidates.add(entry.candidateInfo);
            }
            return candidates;
        }
    }

    private ProcessCandidateInfo registerProcessCandidate(File selectedFile) {
        File executableFile = normalizeSelectedExecutableFile(selectedFile);
        String normalizedPath = normalizeFilePath(executableFile.getAbsolutePath()).toLowerCase(Locale.ROOT);
        synchronized (candidateLock) {
            pruneExpiredCandidatesLocked();
            for (CandidateCacheEntry entry : processCandidateCache.values()) {
                String cachedPath = normalizeFilePath(entry.executableFile.getAbsolutePath()).toLowerCase(Locale.ROOT);
                if (cachedPath.equals(normalizedPath)) {
                    ProcessCandidateInfo refreshed = buildPickedProcessCandidateInfo(entry.candidateId, executableFile);
                    entry.executableFile = executableFile;
                    entry.candidateInfo = refreshed;
                    entry.expireAt = System.currentTimeMillis() + PROCESS_CANDIDATE_TTL_MILLIS;
                    return refreshed;
                }
            }

            String candidateId = UUID.randomUUID().toString().replace("-", "");
            ProcessCandidateInfo candidateInfo = buildPickedProcessCandidateInfo(candidateId, executableFile);
            processCandidateCache.put(candidateId, new CandidateCacheEntry(
                    candidateId,
                    executableFile,
                    candidateInfo,
                    System.currentTimeMillis() + PROCESS_CANDIDATE_TTL_MILLIS
            ));
            return candidateInfo;
        }
    }

    private void updateCandidateEntry(ProcessCandidateInfo candidateInfo, File executableFile) {
        if (candidateInfo == null || candidateInfo.getCandidateId() == null) {
            return;
        }
        synchronized (candidateLock) {
            CandidateCacheEntry entry = processCandidateCache.get(candidateInfo.getCandidateId());
            if (entry != null) {
                entry.executableFile = executableFile;
                entry.candidateInfo = candidateInfo;
                entry.expireAt = System.currentTimeMillis() + PROCESS_CANDIDATE_TTL_MILLIS;
            }
        }
    }

    private CandidateCacheEntry getCandidateEntry(String candidateId) {
        synchronized (candidateLock) {
            pruneExpiredCandidatesLocked();
            CandidateCacheEntry entry = processCandidateCache.get(candidateId);
            if (entry == null) {
                return null;
            }
            if (entry.expireAt < System.currentTimeMillis()) {
                processCandidateCache.remove(candidateId);
                return null;
            }
            return entry;
        }
    }

    private void pruneExpiredCandidatesLocked() {
        long now = System.currentTimeMillis();
        processCandidateCache.entrySet().removeIf(entry -> entry.getValue().expireAt < now);
    }

    private File normalizeSelectedExecutableFile(File selectedFile) {
        if (selectedFile == null) {
            throw new IllegalArgumentException("未选择 Sigma 程序");
        }
        try {
            return selectedFile.getCanonicalFile();
        } catch (IOException e) {
            return selectedFile.getAbsoluteFile();
        }
    }

    private ProcessCandidateInfo buildPickedProcessCandidateInfo(String candidateId, File executableFile) {
        String path = executableFile != null ? executableFile.getAbsolutePath() : "";
        String fileName = executableFile != null ? executableFile.getName() : "";
        String targetName = getEffectiveTargetProcessName(getConfiguredTargetFileReference());
        boolean nameMatched = targetName.equalsIgnoreCase(fileName);

        Set<String> expectedMd5Set = isMd5CheckEnabled()
                ? parseExpectedMd5Set(properties.getTargetProcessMd5())
                : Collections.emptySet();
        String actualMd5 = calculateFileMd5(executableFile);
        boolean md5Matched = isMd5CheckEnabled()
                && !expectedMd5Set.isEmpty()
                && expectedMd5Set.contains(normalizeMd5(actualMd5));

        long runningPid = executableFile != null ? findRunningPidByExecutablePath(executableFile.getAbsolutePath()) : -1L;
        boolean valid = nameMatched && md5Matched;
        String reason = valid ? "校验通过" : "非法 Sigma 程序";
        return new ProcessCandidateInfo(candidateId, path, fileName, nameMatched, md5Matched, valid, runningPid, reason);
    }

    private File resolveProcessPickerScript() {
        File userDir = new File(System.getProperty("user.dir", "."));
        List<File> candidates = Arrays.asList(
                new File(userDir, "watcher\\pick-sigma-file.ps1"),
                new File(userDir, "pick-sigma-file.ps1"),
                new File(userDir, "dist\\watcher\\pick-sigma-file.ps1")
        );
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return normalizeSelectedExecutableFile(candidate);
            }
        }
        return null;
    }

    private File resolveProcessPickerConfig(File pickerScript) {
        if (pickerScript == null || pickerScript.getParentFile() == null) {
            return null;
        }
        File configFile = new File(pickerScript.getParentFile(), "watcher.config.json");
        return configFile.isFile() ? configFile : null;
    }

    private boolean runProcessPickerTask(File pickerScript) {
        File configFile = resolveProcessPickerConfig(pickerScript);
        String taskName = resolveProcessPickerTaskName(configFile);
        List<String> command = Arrays.asList("schtasks", "/Run", "/TN", taskName);
        try {
            ProcessResult result = runCommand(command, 10);
            if (result.exitCode == 0) {
                log.info("[进程选择] 已通过计划任务启动 Sigma 选择 helper: task={}", taskName);
                return true;
            }
            log.warn("[进程选择] 计划任务启动 Sigma 选择 helper 失败，将回退到直接启动。task={}, exitCode={}, output={}",
                    taskName, result.exitCode, result.output);
            return false;
        } catch (Exception e) {
            log.warn("[进程选择] 调用计划任务启动 Sigma 选择 helper 异常，将回退到直接启动。task={}, reason={}",
                    taskName, e.getMessage());
            return false;
        }
    }

    private void startProcessPickerScript(File pickerScript) throws IOException {
        File configFile = resolveProcessPickerConfig(pickerScript);
        List<String> command = new ArrayList<>();
        command.add(resolvePowerShellPath());
        command.add("-NoProfile");
        command.add("-STA");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-WindowStyle");
        command.add("Hidden");
        command.add("-File");
        command.add(pickerScript.getAbsolutePath());
        if (configFile != null) {
            command.add("-ConfigPath");
            command.add(configFile.getAbsolutePath());
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(pickerScript.getParentFile());
        processBuilder.redirectErrorStream(true);
        processBuilder.start();
        log.info("[进程选择] 已直接启动 Sigma 选择 helper: {}", pickerScript.getAbsolutePath());
    }

    private String resolveProcessPickerTaskName(File configFile) {
        if (configFile == null || !configFile.isFile()) {
            return DEFAULT_PROCESS_PICKER_TASK_NAME;
        }
        try {
            String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            Matcher matcher = PROCESS_PICKER_TASK_PATTERN.matcher(content);
            if (matcher.find() && matcher.group(1) != null && !matcher.group(1).trim().isEmpty()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {
            log.debug("[进程选择] 读取 watcher helper 任务名失败，使用默认任务名: {}", e.getMessage());
        }
        return DEFAULT_PROCESS_PICKER_TASK_NAME;
    }

    private ProcessResult runCommand(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append(' ');
                }
                output.append(line.trim());
            }
        }

        boolean finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(-1, "timeout");
        }
        return new ProcessResult(process.exitValue(), output.toString());
    }

    private String getEffectiveTargetProcessName(File configuredFile) {
        String targetName = properties.getTargetProcessName();
        if (targetName != null && !targetName.trim().isEmpty()) {
            return targetName.trim();
        }
        if (configuredFile != null && configuredFile.getName() != null && !configuredFile.getName().trim().isEmpty()) {
            return configuredFile.getName();
        }
        return "Sigma Play.exe";
    }

    private List<String> toPathList(List<File> files) {
        List<String> paths = new ArrayList<>();
        if (files == null) {
            return paths;
        }
        for (File file : files) {
            if (file != null) {
                paths.add(file.getAbsolutePath());
            }
        }
        return paths;
    }

    private ProcessCandidateInfo findScannedCandidate(List<ProcessCandidateInfo> candidates, String selectedPath) {
        if (candidates == null || selectedPath == null) {
            return null;
        }
        for (ProcessCandidateInfo candidate : candidates) {
            if (candidate != null && isPathMatched(candidate.getPath(), selectedPath)) {
                return candidate;
            }
        }
        return null;
    }

    private ProcessCandidateInfo buildProcessCandidateInfo(File discoveredFile,
                                                           File configuredFile,
                                                           Set<String> expectedMd5Set) {
        String path = discoveredFile != null ? discoveredFile.getAbsolutePath() : "";
        String fileName = discoveredFile != null ? discoveredFile.getName() : "";
        boolean pathMatched = configuredFile != null && isPathMatched(path, configuredFile.getAbsolutePath());
        String actualMd5 = calculateFileMd5(discoveredFile);

        boolean md5Matched = true;
        if (isMd5CheckEnabled()) {
            md5Matched = !expectedMd5Set.isEmpty() && expectedMd5Set.contains(normalizeMd5(actualMd5));
        }

        long runningPid = discoveredFile != null ? findRunningPidByExecutablePath(discoveredFile.getAbsolutePath()) : -1L;
        boolean valid = pathMatched && md5Matched;
        String reason;
        if (!pathMatched) {
            reason = "非法 Sigma 程序：路径与配置不一致";
        } else if (isMd5CheckEnabled() && expectedMd5Set.isEmpty()) {
            valid = false;
            md5Matched = false;
            reason = "非法 Sigma 程序：MD5 配置无效";
        } else if (!md5Matched) {
            reason = "非法 Sigma 程序：MD5 校验失败";
        } else {
            reason = "校验通过";
        }

        return new ProcessCandidateInfo(UUID.randomUUID().toString().replace("-", ""),
                path, fileName, pathMatched, md5Matched, valid, runningPid, reason);
    }

    private String calculateFileMd5(File file) {
        if (file == null || !file.isFile()) {
            return "";
        }
        try {
            return normalizeMd5(Md5Util.getFileMd5(file));
        } catch (Exception e) {
            log.warn("[进程绑定] 计算候选文件 MD5 失败: path={}, reason={}", file.getAbsolutePath(), e.getMessage());
            return "";
        }
    }

    private String calculateFileMd5Quietly(File file, String subject) {
        if (file == null || !file.isFile()) {
            log.warn("[进程完整性] {} 文件不存在，无法计算 MD5", subject);
            return "";
        }
        try {
            return normalizeMd5(Md5Util.getFileMd5(file));
        } catch (Exception e) {
            log.warn("[进程完整性] {} 计算 MD5 失败: {}", subject, e.getMessage());
            return "";
        }
    }

    private long findRunningPidByExecutablePath(String executablePath) {
        List<ProcessCandidate> processCandidates = findProcessCandidatesByExecutablePath(executablePath);
        for (ProcessCandidate candidate : processCandidates) {
            if (candidate != null && candidate.sessionId != 0 && isPathMatched(candidate.executablePath, executablePath)) {
                return candidate.pid;
            }
        }
        return -1L;
    }

    private long resolveTargetPidForExecutable(File executableFile) {
        String targetPath = executableFile.getAbsolutePath();
        boolean md5CheckEnabled = isMd5CheckEnabled();
        Set<String> expectedMd5Set = md5CheckEnabled
                ? parseExpectedMd5Set(properties.getTargetProcessMd5())
                : Collections.emptySet();

        if (md5CheckEnabled && expectedMd5Set.isEmpty()) {
            log.warn("[进程绑定] target-process-md5 已配置但没有有效的 MD5 值，拒绝绑定。");
            return -1L;
        }

        if (md5CheckEnabled && !isExecutableFileMd5Valid(executableFile, expectedMd5Set, "配置的目标文件")) {
            return -1L;
        }

        List<ProcessCandidate> candidates = findProcessCandidatesByExecutablePath(targetPath);
        if (candidates.isEmpty()) {
            long fallbackPid = resolveRecentlyStartedPidByProcessName(executableFile, false);
            if (fallbackPid > 0) {
                return fallbackPid;
            }
            log.warn("[进程绑定] 未找到由 target-process-path 指向的目标进程: {}", targetPath);
            return -1L;
        }

        log.info("[进程绑定] 开始按绝对路径校验进程，共 {} 个候选进程，MD5校验: {}，期望绝对路径: {}",
                candidates.size(), md5CheckEnabled, targetPath);
        for (ProcessCandidate candidate : candidates) {
            if (!isPathMatched(candidate.executablePath, targetPath)) {
                log.warn("[进程绑定] PID={} 路径不匹配（疑似伪造）！实际路径: {}，期望绝对路径: {}",
                        candidate.pid, candidate.executablePath, targetPath);
                continue;
            }

            if (candidate.sessionId == 0) {
                log.warn("[杩涚▼缁戝畾] PID={} is in Windows Service Session 0, skip invisible process binding.", candidate.pid);
                continue;
            }

            if (md5CheckEnabled && !isProcessFileMd5Valid(candidate.pid, candidate.executablePath, expectedMd5Set)) {
                continue;
            }

            log.info("[进程绑定] PID={} 进程文件校验通过: {}", candidate.pid, candidate.executablePath);
            return candidate.pid;
        }

        log.warn("[进程绑定] 按绝对路径匹配到的进程均未通过文件校验，共 {} 个。拒绝绑定。", candidates.size());
        return -1L;
    }

    private BoundProcessSnapshot buildBoundProcessSnapshot(long pid, File executableFile) {
        if (pid <= 0 || executableFile == null) {
            return null;
        }
        String processPath = getProcessExecutablePath(pid);
        if (processPath == null || processPath.trim().isEmpty()) {
            processPath = executableFile.getAbsolutePath();
        }
        File processFile = new File(processPath);
        long now = System.currentTimeMillis();
        String md5 = "";
        if (isMd5CheckEnabled()) {
            md5 = calculateFileMd5Quietly(processFile, "绑定快照");
        }
        return new BoundProcessSnapshot(
                pid,
                normalizeFilePath(processPath),
                getProcessCreationDate(pid),
                processFile.length(),
                processFile.lastModified(),
                md5,
                now
        );
    }

    private String getProcessCreationDate(long pid) {
        try {
            String command = "$OutputEncoding=[System.Text.Encoding]::UTF8; " +
                    "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; " +
                    "Get-CimInstance Win32_Process -Filter \"ProcessId=" + pid + "\" " +
                    "| Select-Object -ExpandProperty CreationDate";
            String[] cmd = new String[] {
                    resolvePowerShellPath(),
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    command
            };
            Process proc = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    reader.close();
                    proc.destroy();
                    return line;
                }
            }
            reader.close();
            proc.destroy();
        } catch (Exception e) {
            log.debug("[进程绑定] 查询 PID={} 创建时间失败: {}", pid, e.getMessage());
        }
        return "";
    }

    private List<ProcessCandidate> findProcessCandidatesByExecutablePath(String targetPath) {
        List<ProcessCandidate> candidates = new ArrayList<>();
        String normalizedTargetPath = normalizeFilePath(targetPath);
        String command = "$OutputEncoding=[System.Text.Encoding]::UTF8; " +
                "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; " +
                "$target='" + escapePowerShellSingleQuotedString(normalizedTargetPath) + "'; " +
                "$target=[System.IO.Path]::GetFullPath($target); " +
                "Get-CimInstance Win32_Process | " +
                "Where-Object { try { $_.ExecutablePath -and ([System.IO.Path]::GetFullPath($_.ExecutablePath) -ieq $target) } catch { $false } } | " +
                "Sort-Object @{Expression={ if ($_.SessionId -gt 0) { 0 } else { 1 } }}, @{Expression='CreationDate';Descending=$true} | " +
                "ForEach-Object { [string]$_.ProcessId + '|' + [string]$_.SessionId + '|' + $_.ExecutablePath }";

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    resolvePowerShellPath(),
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    command
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null) {
                ProcessCandidate candidate = parseProcessCandidateLine(line);
                if (candidate != null) {
                    candidates.add(candidate);
                } else if (!line.trim().isEmpty()) {
                    log.debug("[进程绑定] 忽略无法解析的按路径查找输出: {}", line);
                }
            }
            reader.close();

            int exitCode = process.waitFor();
            process.destroy();
            if (exitCode != 0 && candidates.isEmpty()) {
                log.warn("[进程绑定] PowerShell 按绝对路径查询进程失败，exitCode={}, targetPath={}",
                        exitCode, normalizedTargetPath);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[进程绑定] PowerShell 按绝对路径查询进程被中断: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[进程绑定] PowerShell 按绝对路径查询进程失败: {}, targetPath={}",
                    e.getMessage(), normalizedTargetPath);
        }

        return candidates;
    }

    private long resolveRecentlyStartedPidByProcessName(File executableFile, boolean selectedCandidateVerified) {
        if (executableFile == null || !executableFile.isFile()) {
            return -1L;
        }
        if (!selectedCandidateVerified && !isConfiguredExecutableVerified(executableFile)) {
            return -1L;
        }

        String processName = executableFile.getName();
        String command = "$OutputEncoding=[System.Text.Encoding]::UTF8; " +
                "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; " +
                "$name='" + escapePowerShellSingleQuotedString(processName) + "'; " +
                "Get-CimInstance Win32_Process | " +
                "Where-Object { $_.Name -ieq $name -and $_.SessionId -gt 0 } | " +
                "Sort-Object @{Expression='CreationDate';Descending=$true} | " +
                "ForEach-Object { [string]$_.ProcessId + '|' + [string]$_.SessionId + '|' + $_.Name }";

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    resolvePowerShellPath(),
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    command
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null) {
                ProcessCandidate candidate = parseProcessCandidateLine(line);
                if (candidate != null && candidate.sessionId > 0) {
                    reader.close();
                    process.destroy();
                    log.warn("[process-bind] executable path unavailable; bind verified process by name fallback. pid={}, name={}",
                            candidate.pid, processName);
                    return candidate.pid;
                } else if (!line.trim().isEmpty()) {
                    log.debug("[process-bind] ignore unparsable process-name lookup output: {}", line);
                }
            }
            reader.close();

            int exitCode = process.waitFor();
            process.destroy();
            if (exitCode != 0) {
                log.warn("[process-bind] process-name pid lookup failed. exitCode={}, name={}", exitCode, processName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[process-bind] process-name pid lookup interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[process-bind] process-name pid lookup failed: {}, name={}", e.getMessage(), processName);
        }
        return -1L;
    }

    private boolean isConfiguredExecutableVerified(File executableFile) {
        String targetName = getEffectiveTargetProcessName(getConfiguredTargetFileReference());
        if (targetName == null || executableFile == null || !targetName.equalsIgnoreCase(executableFile.getName())) {
            return false;
        }
        if (!isMd5CheckEnabled()) {
            return false;
        }
        Set<String> expectedMd5Set = parseExpectedMd5Set(properties.getTargetProcessMd5());
        return !expectedMd5Set.isEmpty()
                && isExecutableFileMd5Valid(executableFile, expectedMd5Set, "process-name-fallback");
    }

    private ProcessCandidate parseProcessCandidateLine(String line) {
        if (line == null) {
            return null;
        }
        String value = line.trim();
        if (value.isEmpty()) {
            return null;
        }
        String[] parts = value.split("\\|", 3);
        if (parts.length < 2) {
            return null;
        }
        try {
            long pid = Long.parseLong(parts[0].trim());
            int sessionId = -1;
            String executablePath;
            if (parts.length >= 3) {
                sessionId = Integer.parseInt(parts[1].trim());
                executablePath = parts[2].trim();
            } else {
                executablePath = parts[1].trim();
            }
            if (executablePath.isEmpty()) {
                return null;
            }
            return new ProcessCandidate(pid, sessionId, executablePath);
        } catch (NumberFormatException e) {
            log.debug("[进程绑定] PID 查询输出解析失败: {}", line);
            return null;
        }
    }

    private String escapePowerShellSingleQuotedString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }

    private static class ProcessCandidate {
        private final long pid;
        private final int sessionId;
        private final String executablePath;

        private ProcessCandidate(long pid, int sessionId, String executablePath) {
            this.pid = pid;
            this.sessionId = sessionId;
            this.executablePath = executablePath;
        }
    }

    private static class BoundProcessSnapshot {
        private final long pid;
        private final String executablePath;
        private final String creationDate;
        private final long fileLength;
        private final long fileLastModified;
        private final String fileMd5;
        private final long hashCheckedAt;

        private BoundProcessSnapshot(long pid,
                                     String executablePath,
                                     String creationDate,
                                     long fileLength,
                                     long fileLastModified,
                                     String fileMd5,
                                     long hashCheckedAt) {
            this.pid = pid;
            this.executablePath = executablePath;
            this.creationDate = creationDate;
            this.fileLength = fileLength;
            this.fileLastModified = fileLastModified;
            this.fileMd5 = fileMd5;
            this.hashCheckedAt = hashCheckedAt;
        }
    }

    private static class ProcessIntegrityCacheEntry {
        private final long pid;
        private final ProcessIntegrityResult result;
        private final long expireAt;
        private final long hashExpireAt;
        private final long fileLength;
        private final long fileLastModified;
        private final String fileMd5;

        private ProcessIntegrityCacheEntry(long pid,
                                           ProcessIntegrityResult result,
                                           long expireAt,
                                           long hashExpireAt,
                                           long fileLength,
                                           long fileLastModified,
                                           String fileMd5) {
            this.pid = pid;
            this.result = result;
            this.expireAt = expireAt;
            this.hashExpireAt = hashExpireAt;
            this.fileLength = fileLength;
            this.fileLastModified = fileLastModified;
            this.fileMd5 = fileMd5;
        }
    }

    private static class ProcessResult {
        private final int exitCode;
        private final String output;

        private ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static class CandidateCacheEntry {
        private final String candidateId;
        private File executableFile;
        private ProcessCandidateInfo candidateInfo;
        private long expireAt;

        private CandidateCacheEntry(String candidateId,
                                    File executableFile,
                                    ProcessCandidateInfo candidateInfo,
                                    long expireAt) {
            this.candidateId = candidateId;
            this.executableFile = executableFile;
            this.candidateInfo = candidateInfo;
            this.expireAt = expireAt;
        }
    }

    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        Pointer FindFirstFileW(WString lpFileName, Kernel32FindData lpFindFileData);

        boolean FindNextFileW(Pointer hFindFile, Kernel32FindData lpFindFileData);

        boolean FindClose(Pointer hFindFile);

        int GetLastError();
    }

    public static class Kernel32FileTime extends Structure {
        public int dwLowDateTime;
        public int dwHighDateTime;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("dwLowDateTime", "dwHighDateTime");
        }
    }

    public static class Kernel32FindData extends Structure {
        public int dwFileAttributes;
        public Kernel32FileTime ftCreationTime = new Kernel32FileTime();
        public Kernel32FileTime ftLastAccessTime = new Kernel32FileTime();
        public Kernel32FileTime ftLastWriteTime = new Kernel32FileTime();
        public int nFileSizeHigh;
        public int nFileSizeLow;
        public int dwReserved0;
        public int dwReserved1;
        public char[] cFileName = new char[MAX_PATH];
        public char[] cAlternateFileName = new char[14];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "dwFileAttributes",
                    "ftCreationTime",
                    "ftLastAccessTime",
                    "ftLastWriteTime",
                    "nFileSizeHigh",
                    "nFileSizeLow",
                    "dwReserved0",
                    "dwReserved1",
                    "cFileName",
                    "cAlternateFileName"
            );
        }

        private boolean isDirectory() {
            return (dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
        }

        private boolean isReparsePoint() {
            return (dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) != 0;
        }

        private String getFileName() {
            return Native.toString(cFileName);
        }
    }

    private boolean isPathMatched(String execPath, String targetPath) {
        if (execPath == null || targetPath == null) {
            return false;
        }
        String expectedPath = targetPath.trim();
        if (expectedPath.isEmpty()) {
            return true;
        }
        String normalizedActualPath = normalizeFilePath(execPath);
        String normalizedExpectedPath = normalizeFilePath(expectedPath);
        return normalizedActualPath.equalsIgnoreCase(normalizedExpectedPath);
    }

    private String normalizeFilePath(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (Exception e) {
            return new File(path).getAbsolutePath();
        }
    }

    private boolean isMd5CheckEnabled() {
        String targetMd5 = properties.getTargetProcessMd5();
        return targetMd5 != null && !targetMd5.trim().isEmpty();
    }

    private boolean isProcessFileMd5Valid(long pid, String execPath, Set<String> expectedMd5Set) {
        return isExecutableFileMd5Valid(new File(execPath), expectedMd5Set, "PID=" + pid);
    }

    private boolean isExecutableFileMd5Valid(File executableFile, Set<String> expectedMd5Set, String subject) {
        try {
            String actualMd5 = normalizeMd5(Md5Util.getFileMd5(executableFile));
            if (expectedMd5Set.contains(actualMd5)) {
                log.info("[进程绑定] {} MD5校验通过: {}", subject, actualMd5);
                return true;
            }

            log.warn("[进程绑定] {} MD5不匹配（疑似文件被替换），实际MD5: {}，期望MD5: {}",
                    subject, actualMd5, expectedMd5Set);
            return false;
        } catch (Exception e) {
            log.warn("[进程绑定] {} 计算可执行文件MD5失败，拒绝绑定。路径: {}，原因: {}",
                    subject, executableFile, e.getMessage());
            return false;
        }
    }

    private Set<String> parseExpectedMd5Set(String md5Config) {
        Set<String> expectedMd5Set = new HashSet<>();
        if (md5Config == null || md5Config.trim().isEmpty()) {
            return expectedMd5Set;
        }

        String[] md5Items = md5Config.split(",");
        for (String md5Item : md5Items) {
            String normalizedMd5 = normalizeMd5(md5Item);
            if (isValidMd5(normalizedMd5)) {
                expectedMd5Set.add(normalizedMd5);
            } else {
                log.warn("[进程绑定] 忽略无效的 target-process-md5 配置项: {}", md5Item);
            }
        }
        return expectedMd5Set;
    }

    private String normalizeMd5(String md5) {
        if (md5 == null) {
            return "";
        }
        return md5.trim()
                .replace(" ", "")
                .replace(":", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean isValidMd5(String md5) {
        if (md5 == null || md5.length() != 32) {
            return false;
        }
        for (int i = 0; i < md5.length(); i++) {
            char ch = md5.charAt(i);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 通过 wmic 查询指定 PID 对应的可执行文件完整路径
     * JDK 1.8 兼容：wmic process where processid=1234 get ExecutablePath /format:csv
     *
     * @param pid 目标进程 PID
     * @return 可执行文件完整路径（如 C:\Program Files\Sigma\Sigma Play.exe）；失败返回 null
     */
    private String getProcessExecutablePath(long pid) {
        String path = getProcessExecutablePathByWmic(pid);
        if (path != null) {
            return path;
        }
        return getProcessExecutablePathByPowerShell(pid);
    }

    private String getProcessExecutablePathByWmic(long pid) {
        try {
            // wmic 输出 CSV 格式便于解析，示例：
            // Node,ExecutablePath
            // DESKTOP-ABC,C:\Program Files\Sigma\Sigma Play.exe
            String wmicPath = resolveWmicPath();
            String[] cmd = new String[] {
                    wmicPath,
                    "process",
                    "where",
                    "processid=" + pid,
                    "get",
                    "ExecutablePath",
                    "/format:csv"
            };
            Process proc = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), "GBK"));

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 跳过空行和表头行
                if (line.isEmpty() || line.startsWith("Node")) continue;
                // CSV 格式：节点名,路径
                int commaIdx = line.indexOf(',');
                if (commaIdx >= 0 && commaIdx < line.length() - 1) {
                    String path = line.substring(commaIdx + 1).trim();
                    if (!path.isEmpty()) {
                        reader.close();
                        proc.destroy();
                        return path;
                    }
                }
            }
            reader.close();
            proc.destroy();
            return null;
        } catch (Exception e) {
            log.warn("[进程绑定] wmic 查询 PID={} 可执行路径失败: {}", pid, e.getMessage());
            return null;
        }
    }

    private String resolveWmicPath() {
        String systemRoot = getSystemRoot();
        String[] candidates = new String[] {
                systemRoot + "\\System32\\wbem\\wmic.exe",
                systemRoot + "\\Sysnative\\wbem\\wmic.exe"
        };
        for (String candidate : candidates) {
            if (new File(candidate).isFile()) {
                return candidate;
            }
        }
        return "wmic";
    }

    private String getProcessExecutablePathByPowerShell(long pid) {
        try {
            String command = "$OutputEncoding=[System.Text.Encoding]::UTF8; " +
                    "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; " +
                    "Get-CimInstance Win32_Process -Filter \"ProcessId=" + pid + "\" " +
                    "| Select-Object -ExpandProperty ExecutablePath";
            String[] cmd = new String[] {
                    resolvePowerShellPath(),
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    command
            };
            Process proc = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    reader.close();
                    proc.destroy();
                    return line;
                }
            }
            reader.close();
            proc.destroy();
            return null;
        } catch (Exception e) {
            log.warn("[进程绑定] PowerShell 查询 PID={} 可执行路径失败: {}", pid, e.getMessage());
            return null;
        }
    }

    private String resolvePowerShellPath() {
        String systemRoot = getSystemRoot();
        String[] candidates = new String[] {
                systemRoot + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe",
                systemRoot + "\\Sysnative\\WindowsPowerShell\\v1.0\\powershell.exe"
        };
        for (String candidate : candidates) {
            if (new File(candidate).isFile()) {
                return candidate;
            }
        }
        return "powershell";
    }

    private String getSystemRoot() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.trim().isEmpty()) {
            return "C:\\Windows";
        }
        return systemRoot;
    }

    @Override
    public void bindCurrentPid() {
        if (!properties.isEnabled()) {
            log.info("[进程绑定] 功能未启用，跳过绑定");
            return;
        }

        long pid = -1L;
        File executableFile = discoverAndValidateTargetExecutable();
        if (executableFile != null) {
            pid = resolveTargetPidForExecutable(executableFile);
        }
        applyBinding(pid);
    }

    private void applyBinding(long pid) {
        BoundProcessSnapshot snapshot = null;
        if (pid > 0) {
            File configuredFile = getConfiguredTargetExecutable();
            snapshot = buildBoundProcessSnapshot(pid, configuredFile);
        }
        synchronized (bindingLock) {
            this.authorizedPid = pid;
            this.boundProcessSnapshot = snapshot;
            this.integrityCacheEntry = null;
            resetPidAliveCache();
        }

        if (pid == -1L) {
            log.warn("[进程绑定] 目标进程未完成绑定，所有来源将被拒绝。target-process-path={}",
                      properties.getTargetProcessPath());
            this.authorizedEndpoints = Collections.emptySet();
            stopPidGuard();
        } else {
            log.info("[进程绑定] 授权 PID 已绑定: {} -> PID={}",
                     properties.getTargetProcessPath(), pid);
            // PID 绑定成功后立即反查 IP:Port 白名单
            resolveAuthorizedEndpoints();
            startPidGuard(pid);
        }
    }

    private long startTargetProcessAndWaitForPid(File executableFile) {
        if (executableFile == null) {
            return -1L;
        }

        if (!startTargetProcessByInteractiveTask(executableFile)) {
            try {
            ProcessBuilder processBuilder = new ProcessBuilder(executableFile.getAbsolutePath());
            File parent = executableFile.getParentFile();
            if (parent != null && parent.isDirectory()) {
                processBuilder.directory(parent);
            }
            processBuilder.start();
            log.info("[进程绑定] 已拉起目标进程，等待 PID 绑定: {}", executableFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("[进程绑定] 拉起目标进程失败: {}, 原因: {}",
                    executableFile.getAbsolutePath(), e.getMessage());
                return -1L;
            }
        }

        long deadline = System.currentTimeMillis() + Math.max(1000L, properties.getStartWaitMillis());
        long pollMillis = Math.max(200L, properties.getStartPollMillis());
        while (System.currentTimeMillis() < deadline) {
            sleepQuietly(pollMillis);
            long pid = resolveTargetPidForExecutable(executableFile);
            if (pid > 0) {
                log.info("[进程绑定] 自动拉起后绑定成功: PID={}", pid);
                return pid;
            }
        }

        log.warn("[进程绑定] 自动拉起后未在 {} ms 内完成 PID 绑定。", properties.getStartWaitMillis());
        return -1L;
    }

    private boolean startTargetProcessByInteractiveTask(File executableFile) {
        File requestFile = resolveSigmaLaunchRequestFile();
        if (requestFile == null) {
            return false;
        }

        try {
            writeSigmaLaunchRequest(requestFile, executableFile);
        } catch (Exception e) {
            log.warn("[进程绑定] 写入 Sigma 交互式启动请求失败，将直接启动。path={}, reason={}",
                    requestFile.getAbsolutePath(), e.getMessage());
            return false;
        }

        List<String> command = Arrays.asList("schtasks", "/Run", "/TN", DEFAULT_SIGMA_LAUNCHER_TASK_NAME);
        try {
            ProcessResult result = runCommand(command, 10);
            if (result.exitCode == 0) {
                log.info("[进程绑定] 已通过交互式任务拉起 Sigma: task={}, path={}",
                        DEFAULT_SIGMA_LAUNCHER_TASK_NAME, executableFile.getAbsolutePath());
                return true;
            }
            log.warn("[进程绑定] 交互式任务拉起 Sigma 失败，将直接启动。task={}, exitCode={}, output={}",
                    DEFAULT_SIGMA_LAUNCHER_TASK_NAME, result.exitCode, result.output);
        } catch (Exception e) {
            log.warn("[进程绑定] 调用交互式任务拉起 Sigma 异常，将直接启动。task={}, reason={}",
                    DEFAULT_SIGMA_LAUNCHER_TASK_NAME, e.getMessage());
        }

        return false;
    }

    private File resolveSigmaLaunchRequestFile() {
        File userDir = new File(System.getProperty("user.dir", "."));
        File requestFile = new File(userDir, SIGMA_LAUNCH_REQUEST_FILE);
        File parent = requestFile.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            return null;
        }
        return requestFile;
    }

    private void writeSigmaLaunchRequest(File requestFile, File executableFile) throws IOException {
        String escapedPath = executableFile.getAbsolutePath()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        String json = "{\"path\":\"" + escapedPath + "\",\"updatedAt\":" + System.currentTimeMillis() + "}";
        Files.write(requestFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    private File discoverAndValidateTargetExecutable() {
        File configuredFile = getConfiguredTargetExecutable();
        if (configuredFile == null) {
            return null;
        }

        String targetName = properties.getTargetProcessName();
        if (targetName == null || targetName.trim().isEmpty()) {
            targetName = configuredFile.getName();
        }

        List<File> searchRoots = resolveTargetProcessSearchRoots(configuredFile);
        if (searchRoots.isEmpty()) {
            log.warn("[进程绑定] 未配置有效的受控搜索目录，无法发现目标文件: {}", targetName);
            return null;
        }

        List<File> discoveredFiles = new ArrayList<>();
        for (File searchRoot : searchRoots) {
            findExecutableFilesByName(searchRoot, targetName.trim(), discoveredFiles);
        }

        if (discoveredFiles.isEmpty()) {
            log.warn("[进程绑定] 在受控目录内未发现目标文件: name={}, roots={}", targetName, searchRoots);
            return null;
        }

        for (File discoveredFile : discoveredFiles) {
            if (!isPathMatched(discoveredFile.getAbsolutePath(), configuredFile.getAbsolutePath())) {
                log.warn("[进程绑定] 发现同名文件但路径不匹配，拒绝作为启动目标。发现路径: {}，期望路径: {}",
                        discoveredFile.getAbsolutePath(), configuredFile.getAbsolutePath());
                continue;
            }

            Set<String> expectedMd5Set = isMd5CheckEnabled()
                    ? parseExpectedMd5Set(properties.getTargetProcessMd5())
                    : Collections.emptySet();
            if (isMd5CheckEnabled()) {
                if (expectedMd5Set.isEmpty()) {
                    log.warn("[进程绑定] target-process-md5 已配置但没有有效值，拒绝使用发现的目标文件。");
                    return null;
                }
                if (!isExecutableFileMd5Valid(discoveredFile, expectedMd5Set, "发现文件")) {
                    return null;
                }
            }

            log.info("[进程绑定] 受控目录发现目标文件并通过校验: {}", discoveredFile.getAbsolutePath());
            return discoveredFile;
        }

        log.warn("[进程绑定] 已发现 {} 个同名文件，但均未通过 target-process-path 一致性校验。", discoveredFiles.size());
        return null;
    }

    private List<File> resolveTargetProcessSearchRoots(File configuredFile) {
        List<File> roots = new ArrayList<>();
        String rootConfig = properties.getTargetProcessSearchRoots();
        if (rootConfig != null && !rootConfig.trim().isEmpty()) {
            String[] items = rootConfig.split("[;,]");
            for (String item : items) {
                if (item == null || item.trim().isEmpty()) {
                    continue;
                }
                File root = new File(item.trim());
                if (root.isDirectory()) {
                    roots.add(root);
                } else {
                    log.warn("[进程绑定] 忽略无效的 target-process-search-roots 目录: {}", root.getAbsolutePath());
                }
            }
        }

        if (roots.isEmpty()) {
            File parent = configuredFile != null ? configuredFile.getParentFile() : null;
            if (parent != null && parent.isDirectory()) {
                roots.add(parent);
            }
        }
        return roots;
    }

    private void findExecutableFilesByName(File root, String targetName, List<File> result) {
        if (root == null || targetName == null || targetName.trim().isEmpty()) {
            return;
        }
        if (!root.isDirectory()) {
            return;
        }

        String searchPattern = new File(root, "*").getAbsolutePath();
        Kernel32FindData findData = new Kernel32FindData();
        Pointer handle = null;
        try {
            handle = Kernel32.INSTANCE.FindFirstFileW(new WString(searchPattern), findData);
            if (isInvalidFindHandle(handle)) {
                int errorCode = Kernel32.INSTANCE.GetLastError();
                if (errorCode != ERROR_NO_MORE_FILES) {
                    log.debug("[进程绑定] FindFirstFileW 搜索目录失败: root={}, errorCode={}",
                            root.getAbsolutePath(), errorCode);
                }
                return;
            }

            do {
                String fileName = findData.getFileName();
                if (fileName == null || fileName.isEmpty() || ".".equals(fileName) || "..".equals(fileName)) {
                    continue;
                }

                File child = new File(root, fileName);
                if (findData.isDirectory() && !findData.isReparsePoint()) {
                    findExecutableFilesByName(child, targetName, result);
                } else if (fileName.equalsIgnoreCase(targetName)) {
                    result.add(child);
                }
            } while (Kernel32.INSTANCE.FindNextFileW(handle, findData));

            int errorCode = Kernel32.INSTANCE.GetLastError();
            if (errorCode != ERROR_NO_MORE_FILES) {
                log.debug("[进程绑定] FindNextFileW 搜索目录提前结束: root={}, errorCode={}",
                        root.getAbsolutePath(), errorCode);
            }
        } catch (Exception e) {
            log.warn("[进程绑定] FindFirstFileW/FindNextFileW 搜索异常: root={}, reason={}",
                    root.getAbsolutePath(), e.getMessage());
        } finally {
            if (handle != null && !isInvalidFindHandle(handle)) {
                Kernel32.INSTANCE.FindClose(handle);
            }
        }
    }

    private boolean isInvalidFindHandle(Pointer handle) {
        return handle == null || Pointer.nativeValue(handle) == INVALID_HANDLE_VALUE;
    }

    private File getConfiguredTargetFileReference() {
        String targetPath = properties.getTargetProcessPath();
        if (targetPath == null || targetPath.trim().isEmpty()) {
            log.warn("[进程绑定] target-process-path 未配置，无法做路径一致性校验。");
            return null;
        }
        return new File(targetPath.trim());
    }

    private File getConfiguredTargetExecutable() {
        String targetPath = properties.getTargetProcessPath();
        if (targetPath == null || targetPath.trim().isEmpty()) {
            log.warn("[进程绑定] target-process-path 未配置，无法按绝对路径拉起或绑定目标进程。");
            return null;
        }

        File executableFile = new File(targetPath.trim());
        if (!executableFile.isFile()) {
            log.warn("[进程绑定] target-process-path 不存在或不是文件，无法拉起目标进程: {}",
                    executableFile.getAbsolutePath());
            return null;
        }
        return executableFile;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void resolveAuthorizedEndpoints() {
        if (authorizedPid <= 0) {
            log.warn("[进程绑定] 尚未绑定有效 PID，跳过 IP 白名单获取");
            return;
        }

        try {
            // 通过 ipconfig 获取本机所有 IPv4 地址，作为合法来源 IP 集合。
            // 原因：Sigma Play 发 UDP 使用临时端口（每次随机分配，发包后立即关闭），
            // netstat 无法在 Socket 关闭前完成采样，因此放弃端口维度，只维护 IP 维度。
            Process process = Runtime.getRuntime().exec("ipconfig");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"));

            Set<String> ips = new HashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                // 匹配 "IPv4 地址" 或 "IPv4 Address" 行，例如：
                //   IPv4 地址 . . . . . . . . . . . : 192.168.113.102
                //   IPv4 Address. . . . . . . . . . . : 192.168.1.100
                if (line.contains("IPv4")) {
                    int colonIdx = line.lastIndexOf(':');
                    if (colonIdx >= 0) {
                        String ip = line.substring(colonIdx + 1).trim();
                        // 去掉末尾可能的星号（首选 IP 标记）
                        ip = ip.replace("(", "").replace(")", "").trim();
                        if (ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") && !ip.equals("127.0.0.1")) {
                            ips.add(ip);
                            log.debug("[进程绑定] 本机 IP 白名单新增: {}", ip);
                        }
                    }
                }
            }
            reader.close();
            process.destroy();

            if (ips.isEmpty()) {
                log.warn("[进程绑定] 未获取到本机 IPv4 地址，白名单为空（降级放行）");
            } else {
                log.info("[进程绑定] PID={} 本机 IP 白名单已更新: {}", authorizedPid, ips);
            }

            this.authorizedEndpoints = Collections.unmodifiableSet(ips);

        } catch (Exception e) {
            log.error("[进程绑定] 执行 ipconfig 命令失败: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEndpointAuthorized(String ip, int port) {
        if (!properties.isEnabled()) {
            return true;
        }
        if (!endpointSourceValidationEnabled) {
            return true;
        }

        ProcessIntegrityResult integrityResult = verifyAuthorizedProcessIntegrity("validate-source");
        if (!integrityResult.isValid()) {
            log.warn("[进程绑定] 授权进程完整性复核失败，拒绝来源 {}:{}，reasonCode={}",
                    ip, port, integrityResult.getReasonCode());
            return false;
        }

        // PID 未绑定（UKey 认证未完成 / 目标进程未找到）→ 拒绝
        if (authorizedPid <= 0) {
            log.warn("[进程绑定] PID 未绑定，拒绝来源 {}:{}", ip, port);
            return false;
        }

        // 授权进程必须仍在运行（防止 Sigma 退出后仍放行）
        if (!isPidAlive(authorizedPid)) {
            log.warn("[进程绑定] 授权进程 PID={} 已不存在，拒绝来源 {}:{}", authorizedPid, ip, port);
            return false;
        }

        WindivertMonitorService.PidLookupResult windivertResult =
                windivertMonitorService.findPid(ip, port);
        if (windivertResult.isHit()) {
            if (windivertResult.getPid() == authorizedPid) {
                log.debug("[进程绑定] WinDivert PID 校验通过: {}:{} 属于 PID={}",
                        ip, port, authorizedPid);
                return true;
            }
            log.warn("[进程绑定] WinDivert PID 校验确认非法来源！{}:{} 属于 PID={}，非授权 PID={}",
                    ip, port, windivertResult.getPid(), authorizedPid);
            return false;
        }

        if (!windivertMonitorService.isFallbackToUdpTableEnabled()) {
            log.warn("[进程绑定] WinDivert 未命中且回退已关闭，拒绝来源 {}:{}，reasonCode={}",
                    ip, port, windivertResult.getReasonCode());
            return false;
        }

        if (windivertResult.isAvailable()) {
            log.debug("[进程绑定] WinDivert 未命中，回退 GetExtendedUdpTable: {}:{}，reasonCode={}",
                    ip, port, windivertResult.getReasonCode());
        } else {
            log.debug("[进程绑定] WinDivert 不可用，回退 GetExtendedUdpTable: {}:{}，reasonCode={}",
                    ip, port, windivertResult.getReasonCode());
        }

        // 回退路径：通过 GetExtendedUdpTable Windows API 直接校验 PID 归属
        if (!udpPortOwnerService.isAvailable()) {
            log.warn("[进程绑定] GetExtendedUdpTable API 不可用，拒绝来源 {}:{}", ip, port);
            return false;
        }

        List<UdpPortOwnerService.UdpEntry> table = udpPortOwnerService.queryUdpTable();
        for (UdpPortOwnerService.UdpEntry entry : table) {
            if (entry.localPort == port) {
                if (entry.owningPid == authorizedPid) {
                    log.debug("[进程绑定] UDP 端口 PID 校验通过: port={} 属于 PID={}", port, authorizedPid);
                    return true;
                } else {
                    log.warn("[进程绑定] UDP 端口校验确认非法来源！port={} 属于 PID={}，非授权 PID={}",
                            port, entry.owningPid, authorizedPid);
                    return false;
                }
            }
        }

        // 端口未在 UDP 表中找到（短连接已关闭），检查 IP 白名单作为补充
        if (authorizedEndpoints.contains(ip)) {
            log.debug("[进程绑定] UDP 端口已关闭，但来源 IP={} 在本机白名单中，放行", ip);
            return true;
        }

        log.warn("[进程绑定] UDP 端口校验无结果且 IP 不在白名单，拒绝来源 {}:{}", ip, port);
        return false;
    }

    @Override
    public ProcessIntegrityResult verifyAuthorizedProcessIntegrity(String trigger) {
        if (!properties.isEnabled()) {
            return integrityOk(false);
        }

        long pid = authorizedPid;
        if (pid <= 0) {
            return integrityFail("PID_UNBOUND", "授权 PID 未绑定", true, false, pid, trigger);
        }

        long now = System.currentTimeMillis();
        ProcessIntegrityCacheEntry cached = integrityCacheEntry;
        if (cached != null && cached.pid == pid && cached.expireAt > now && cached.result != null) {
            return cached.result;
        }

        synchronized (integrityLock) {
            now = System.currentTimeMillis();
            cached = integrityCacheEntry;
            if (cached != null && cached.pid == pid && cached.expireAt > now && cached.result != null) {
                return cached.result;
            }

            ProcessIntegrityResult result = verifyAuthorizedProcessIntegrityNow(pid, trigger, now, cached);
            if (!result.isValid()) {
                integrityCacheEntry = null;
            }
            return result;
        }
    }

    private ProcessIntegrityResult verifyAuthorizedProcessIntegrityNow(long pid,
                                                                       String trigger,
                                                                       long now,
                                                                       ProcessIntegrityCacheEntry cached) {
        BoundProcessSnapshot snapshot = boundProcessSnapshot;
        if (snapshot == null || snapshot.pid != pid) {
            return integrityFail("PROCESS_SNAPSHOT_MISSING", "授权进程快照缺失", true, true, pid, trigger);
        }

        if (!doPidAliveCheck(pid)) {
            return integrityFail("PID_NOT_ALIVE", "授权进程已退出", true, true, pid, trigger);
        }

        String currentPath = getProcessExecutablePath(pid);
        if (currentPath == null || currentPath.trim().isEmpty()) {
            log.warn("[process-bind] authorized process path unavailable, continue with verified snapshot. pid={}", pid);
        } else if (!isPathMatched(currentPath, snapshot.executablePath)) {
            return integrityFail("PROCESS_PATH_MISMATCH", "授权进程路径不一致", true, true, pid, trigger);
        }

        String creationDate = getProcessCreationDate(pid);
        if (snapshot.creationDate != null && !snapshot.creationDate.isEmpty()
                && creationDate != null && !creationDate.isEmpty()
                && !snapshot.creationDate.equals(creationDate)) {
            return integrityFail("PROCESS_CREATION_CHANGED", "授权 PID 创建时间变化", true, true, pid, trigger);
        }

        Set<String> expectedMd5Set = parseExpectedMd5Set(properties.getTargetProcessMd5());
        if (expectedMd5Set.isEmpty()) {
            return integrityFail("PROCESS_MD5_NOT_CONFIGURED", "目标进程 MD5 未配置或无效", true, true, pid, trigger);
        }

        File processFile = new File(snapshot.executablePath);
        if (!processFile.isFile()) {
            return integrityFail("PROCESS_FILE_MISSING", "授权进程文件不存在", true, true, pid, trigger);
        }

        long fileLength = processFile.length();
        long fileLastModified = processFile.lastModified();
        String actualMd5 = null;
        long hashCacheMillis = Math.max(0L, properties.getIntegrityHashCacheMillis());
        boolean canUseHashCache = cached != null
                && cached.pid == pid
                && cached.hashExpireAt > now
                && cached.fileLength == fileLength
                && cached.fileLastModified == fileLastModified
                && cached.fileMd5 != null
                && !cached.fileMd5.isEmpty();
        if (canUseHashCache) {
            actualMd5 = cached.fileMd5;
        } else if (snapshot.fileLength == fileLength
                && snapshot.fileLastModified == fileLastModified
                && now - snapshot.hashCheckedAt < hashCacheMillis
                && snapshot.fileMd5 != null
                && !snapshot.fileMd5.isEmpty()) {
            actualMd5 = snapshot.fileMd5;
        } else {
            actualMd5 = calculateFileMd5Quietly(processFile, "授权进程复核");
        }

        if (actualMd5 == null || actualMd5.isEmpty() || !expectedMd5Set.contains(normalizeMd5(actualMd5))) {
            return integrityFail("PROCESS_MD5_MISMATCH", "授权进程 MD5 校验失败", true, true, pid, trigger);
        }

        integrityCacheEntry = new ProcessIntegrityCacheEntry(
                pid,
                integrityOk(true),
                now + Math.max(0L, properties.getIntegrityCacheMillis()),
                now + hashCacheMillis,
                fileLength,
                fileLastModified,
                normalizeMd5(actualMd5)
        );
        return integrityOk(true);
    }

    private ProcessIntegrityResult integrityOk(boolean checked) {
        return new ProcessIntegrityResult(true, checked, "OK", "授权进程完整性校验通过");
    }

    private ProcessIntegrityResult integrityFail(String reasonCode,
                                                String reason,
                                                boolean checked,
                                                boolean triggerRemoval,
                                                long pid,
                                                String trigger) {
        if (triggerRemoval && pid > 0) {
            handleAuthorizedProcessInvalid(pid, trigger == null ? "integrity" : trigger, reasonCode);
        }
        return new ProcessIntegrityResult(false, checked, reasonCode, reason);
    }

    private void handleAuthorizedProcessInvalid(long pid, String source, String reason) {
        synchronized (bindingLock) {
            if (authorizedPid != pid) {
                return;
            }
            authorizedPid = -1L;
            authorizedEndpoints = Collections.emptySet();
            boundProcessSnapshot = null;
            integrityCacheEntry = null;
            resetPidAliveCache();
        }

        stopPidGuard();
        log.warn("[进程完整性] 授权进程 PID={} 复核失败，触发 UKey 拔出流程。source={}, reason={}",
                pid, source, reason);
        eventPublisher.publishEvent(new ProcessGuardEvent(
                pid,
                properties.getTargetProcessName(),
                source,
                reason
        ));
    }

    @Override
    public void clearBinding() {
        long oldPid;
        Set<String> oldEndpoints;
        synchronized (bindingLock) {
            oldPid = this.authorizedPid;
            oldEndpoints = this.authorizedEndpoints;
            this.authorizedPid = -1L;
            this.authorizedEndpoints = Collections.emptySet();
            this.boundProcessSnapshot = null;
            this.integrityCacheEntry = null;
            resetPidAliveCache();
        }
        stopPidGuard();
        log.info("[进程绑定] 绑定已清除（原授权 PID={}，原白名单端点数={}）",
                oldPid, oldEndpoints.size());
    }

    @Override
    public long getAuthorizedPid() {
        return authorizedPid;
    }

    @Override
    public Set<String> getAuthorizedEndpoints() {
        return authorizedEndpoints;
    }

    @Override
    public void refreshBinding() {
        log.info("[进程绑定] 手动/自动刷新进程绑定...");
        bindCurrentPid();
    }

    // ========== PID 守护与定时刷新 ==========

    /**
     * PID 守护兜底检查。WMI 事件可降低延迟，但仍保留定时检查以覆盖 WMI 不可用、PowerShell 被禁用等情况。
     */
    @Scheduled(fixedDelayString = "${process-bind.guard-check-interval-millis:2000}")
    public void guardAuthorizedPid() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!properties.isGuardEnabled()) {
            return;
        }
        if (authorizedPid <= 0) {
            return;
        }
        long pid = authorizedPid;
        if (!periodicProcessIntegrityRecheckEnabled) {
            if (!isPidAlive(pid)) {
                log.warn("[process-guard] authorized PID exited: {}", pid);
                handleAuthorizedProcessInvalid(pid, "scheduled", "PID_NOT_ALIVE");
            }
            return;
        }
        ProcessIntegrityResult integrityResult = verifyAuthorizedProcessIntegrity("scheduled");
        if (!integrityResult.isValid()) {
            log.warn("[进程绑定] 定时守护复核失败: PID={}, reasonCode={}", pid, integrityResult.getReasonCode());
        }
    }

    /**
     * 每 30 秒刷新一次本机 IP 白名单（本机 IP 变化频率低，无需过于频繁）。
     */
    @Scheduled(fixedDelay = 30000)
    public void refreshAuthorizedEndpointsIfAlive() {
        if (!properties.isEnabled()) {
            return;
        }
        long pid = authorizedPid;
        if (pid <= 0) {
            return;
        }
        if (!periodicProcessIntegrityRecheckEnabled) {
            if (isPidAlive(pid)) {
                resolveAuthorizedEndpoints();
            } else if (!properties.isGuardEnabled()) {
                clearBinding();
            }
            return;
        }
        if (verifyAuthorizedProcessIntegrity("endpoint-refresh").isValid()) {
            resolveAuthorizedEndpoints();
        } else if (!properties.isGuardEnabled()) {
            log.warn("[进程绑定] 授权进程 PID={} 已消亡，清除绑定。", pid);
            clearBinding();
        }
    }

    private void startPidGuard(long pid) {
        if (!properties.isGuardEnabled()) {
            stopPidGuard();
            return;
        }

        synchronized (guardLock) {
            stopWmiGuardLocked();
            guardedPid = pid;
            guardEventPublished = false;
            if (isWmiGuardEnabled()) {
                startWmiGuardLocked(pid);
            }
        }
    }

    private boolean isWmiGuardEnabled() {
        String mode = properties.getGuardMode();
        if (mode == null || mode.trim().isEmpty()) {
            return true;
        }
        return !"scheduled".equalsIgnoreCase(mode.trim());
    }

    private void startWmiGuardLocked(long pid) {
        String command = "$ErrorActionPreference='Stop'; " +
                "$query=\"SELECT * FROM Win32_ProcessStopTrace WHERE ProcessID = " + pid + "\"; " +
                "$watcher=New-Object System.Management.ManagementEventWatcher $query; " +
                "try { $null=$watcher.WaitForNextEvent(); Write-Output 'process-exited' } " +
                "finally { if ($watcher -ne $null) { $watcher.Stop(); $watcher.Dispose() } }";
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    resolvePowerShellPath(),
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    command
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            wmiGuardProcess = process;

            Thread thread = new Thread(() -> waitForWmiGuardExit(pid, process),
                    "process-wmi-guard-" + pid);
            thread.setDaemon(true);
            thread.start();
            log.info("[进程绑定] WMI PID 守护已启动: PID={}", pid);
        } catch (Exception e) {
            wmiGuardProcess = null;
            log.warn("[进程绑定] WMI PID 守护启动失败，将仅依赖定时守护: {}", e.getMessage());
        }
    }

    private void waitForWmiGuardExit(long pid, Process process) {
        try {
            int exitCode = process.waitFor();
            if (exitCode == 0 && isCurrentWmiGuard(pid, process)) {
                handleGuardedPidTerminated(pid, "wmi", "WMI 监听到目标进程退出");
            } else if (isCurrentWmiGuard(pid, process)) {
                log.warn("[进程绑定] WMI PID 守护异常退出: PID={}, exitCode={}", pid, exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (isCurrentWmiGuard(pid, process)) {
                log.warn("[进程绑定] WMI PID 守护线程异常: PID={}, reason={}", pid, e.getMessage());
            }
        }
    }

    private boolean isCurrentWmiGuard(long pid, Process process) {
        synchronized (guardLock) {
            return guardedPid == pid && wmiGuardProcess == process && !guardEventPublished;
        }
    }

    private void stopPidGuard() {
        synchronized (guardLock) {
            guardedPid = -1L;
            guardEventPublished = false;
            stopWmiGuardLocked();
        }
    }

    private void stopWmiGuardLocked() {
        if (wmiGuardProcess != null) {
            try {
                wmiGuardProcess.destroy();
            } catch (Exception e) {
                log.debug("[进程绑定] 停止 WMI PID 守护失败: {}", e.getMessage());
            } finally {
                wmiGuardProcess = null;
            }
        }
    }

    private void handleGuardedPidTerminated(long pid, String source, String reason) {
        synchronized (guardLock) {
            if (guardEventPublished || guardedPid != pid) {
                return;
            }
            guardEventPublished = true;
        }

        synchronized (bindingLock) {
            if (authorizedPid != pid) {
                return;
            }
            authorizedPid = -1L;
            authorizedEndpoints = Collections.emptySet();
            boundProcessSnapshot = null;
            integrityCacheEntry = null;
            resetPidAliveCache();
        }

        stopPidGuard();
        log.warn("[进程绑定] 授权进程 PID={} 已消亡，触发 UKey 拔出流程。source={}, reason={}",
                pid, source, reason);
        eventPublisher.publishEvent(new ProcessGuardEvent(
                pid,
                properties.getTargetProcessName(),
                source,
                reason
        ));
    }

    private void resetPidAliveCache() {
        pidAliveCachePid = -1L;
        pidAliveCacheValue = false;
        pidAliveCacheExpireAt = 0L;
    }

    /**
     * 检查指定 PID 对应的进程是否还在运行
     * JDK 1.8 兼容：通过 tasklist 过滤 PID
     */
    private boolean isPidAlive(long pid) {
        long now = System.currentTimeMillis();
        long cacheMillis = properties != null ? properties.getPidAliveCacheMillis() : 2000L;
        if (cacheMillis > 0 && pid == pidAliveCachePid && pidAliveCacheExpireAt > now) {
            return pidAliveCacheValue;
        }

        boolean alive = doPidAliveCheck(pid);
        if (cacheMillis > 0) {
            pidAliveCachePid = pid;
            pidAliveCacheValue = alive;
            pidAliveCacheExpireAt = now + cacheMillis;
        }
        return alive;
    }

    private boolean doPidAliveCheck(long pid) {
        try {
            Process process = Runtime.getRuntime().exec("tasklist /FI \"PID eq " + pid + "\" /FO CSV /NH");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(String.valueOf(pid))) {
                    reader.close();
                    process.destroy();
                    return true;
                }
            }
            reader.close();
            process.destroy();
            return false;
        } catch (Exception e) {
            log.warn("[进程绑定] 检查 PID 存活失败: {}", e.getMessage());
            return true; // 检查失败时保守处理，认为进程仍存活
        }
    }
}
