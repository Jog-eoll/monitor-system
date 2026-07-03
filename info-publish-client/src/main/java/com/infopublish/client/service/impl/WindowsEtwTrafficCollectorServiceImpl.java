package com.infopublish.client.service.impl;

import com.infopublish.client.entity.dto.EtwTrafficEventDTO;
import com.infopublish.client.service.EtwTrafficAccountingService;
import com.infopublish.client.service.EtwTrafficCollectorService;
import com.infopublish.client.service.ProcessBindService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects real Windows ETW UDP send events through logman/tracerpt.
 *
 * <p>The implementation intentionally uses Windows built-in tools so deployment does not require
 * a .NET SDK or native agent binary. It rotates short ETL windows, converts them to XML, parses
 * UDP send events and records only events whose process id equals the authorized PID.</p>
 */
@Slf4j
@Service
public class WindowsEtwTrafficCollectorServiceImpl
        implements EtwTrafficCollectorService, ApplicationRunner, Ordered {

    private static final String PROVIDER_NAME = "Microsoft-Windows-Kernel-Network";
    private static final String IPV4_KEYWORD = "0x10";
    private static final String LEVEL_INFORMATIONAL = "0x4";
    private static final Pattern EVENT_PATTERN = Pattern.compile("<Event\\b[\\s\\S]*?</Event>");
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("<EventID>(\\d+)</EventID>");
    private static final Pattern PROCESS_ID_PATTERN = Pattern.compile("<Execution[^>]*ProcessID=\"(\\d+)\"");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("<Message>([\\s\\S]*?)</Message>");
    private static final Pattern UDP_SEND_MESSAGE_PATTERN = Pattern.compile(
            "UDPv4:\\s*(\\d+)\\s+bytes\\s+transmitted\\s+from\\s+([^:]+):(\\d+)\\s+to\\s+([^:]+):(\\d+)\\.",
            Pattern.CASE_INSENSITIVE);

    @Value("${traffic.etw-collector.enabled:false}")
    private boolean enabled;

    @Value("${traffic.etw-collector.auto-start:true}")
    private boolean autoStart;

    @Value("${traffic.etw-collector.session-name:InfoPublishClientUdpEtw}")
    private String sessionName;

    @Value("${traffic.etw-collector.work-dir:}")
    private String workDir;

    @Value("${traffic.etw-collector.rotate-interval-ms:10000}")
    private long rotateIntervalMs;

    @Value("${traffic.etw-collector.command-timeout-ms:10000}")
    private long commandTimeoutMs;

    @Value("${traffic.etw-collector.delete-etl-after-parse:true}")
    private boolean deleteEtlAfterParse;

    @Resource
    private ProcessBindService processBindService;

    @Resource
    private EtwTrafficAccountingService etwTrafficAccountingService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong windowsParsed = new AtomicLong();
    private final AtomicLong eventsSeen = new AtomicLong();
    private final AtomicLong eventsRecorded = new AtomicLong();
    private final AtomicLong eventsRejected = new AtomicLong();
    private final AtomicLong parseErrors = new AtomicLong();
    private final AtomicLong commandErrors = new AtomicLong();

    private volatile ExecutorService executorService;
    private volatile Future<?> workerFuture;
    private volatile long startedAt;
    private volatile long lastWindowAt;
    private volatile String lastReason = "STOPPED";
    private volatile String lastError;

    @Override
    public void run(ApplicationArguments args) {
        if (enabled && autoStart) {
            startCollector();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Map<String, Object> startCollector() {
        Map<String, Object> result = new HashMap<>();
        if (!enabled) {
            result.put("started", false);
            result.put("reason", "ETW_COLLECTOR_DISABLED");
            return result;
        }
        if (!isWindows()) {
            result.put("started", false);
            result.put("reason", "NOT_WINDOWS");
            return result;
        }
        if (!running.compareAndSet(false, true)) {
            result.put("started", false);
            result.put("reason", "ALREADY_RUNNING");
            return result;
        }

        startedAt = System.currentTimeMillis();
        lastReason = "RUNNING";
        lastError = null;
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "etw-traffic-collector");
            thread.setDaemon(true);
            return thread;
        });
        workerFuture = executorService.submit(this::collectLoop);

        result.put("started", true);
        result.put("reason", "STARTED");
        return result;
    }

    @Override
    public Map<String, Object> stopCollector() {
        boolean wasRunning = running.getAndSet(false);
        stopSessionQuietly();
        ExecutorService service = executorService;
        if (service != null) {
            service.shutdownNow();
        }
        lastReason = "STOPPED";

        Map<String, Object> result = new HashMap<>();
        result.put("stopped", wasRunning);
        result.put("reason", "STOPPED");
        return result;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", enabled);
        status.put("autoStart", autoStart);
        status.put("running", running.get());
        status.put("sessionName", safeSessionName());
        status.put("workDir", resolveWorkDir().getAbsolutePath());
        status.put("rotateIntervalMs", safeRotateIntervalMs());
        status.put("startedAt", startedAt);
        status.put("lastWindowAt", lastWindowAt);
        status.put("lastReason", lastReason);
        status.put("lastError", lastError);
        status.put("windowsParsed", windowsParsed.get());
        status.put("eventsSeen", eventsSeen.get());
        status.put("eventsRecorded", eventsRecorded.get());
        status.put("eventsRejected", eventsRejected.get());
        status.put("parseErrors", parseErrors.get());
        status.put("commandErrors", commandErrors.get());
        status.put("authorizedPid", processBindService.getAuthorizedPid());
        return status;
    }

    @PreDestroy
    public void destroy() {
        stopCollector();
    }

    private void collectLoop() {
        while (running.get()) {
            String suffix = String.valueOf(System.currentTimeMillis());
            File etlFile = new File(resolveWorkDir(), "udp-etw-" + suffix + ".etl");
            File xmlFile = new File(resolveWorkDir(), "udp-etw-" + suffix + ".xml");
            try {
                stopSessionQuietly();
                CommandResult start = runCommand(commandTimeoutMs,
                        "logman", "start", safeSessionName(),
                        "-p", PROVIDER_NAME, IPV4_KEYWORD, LEVEL_INFORMATIONAL,
                        "-ets", "-o", etlFile.getAbsolutePath());
                if (!start.isSuccess()) {
                    markCommandError("START_FAILED: " + start.summary());
                    sleepQuietly(safeRotateIntervalMs());
                    continue;
                }

                sleepQuietly(safeRotateIntervalMs());

                CommandResult stop = runCommand(commandTimeoutMs, "logman", "stop", safeSessionName(), "-ets");
                if (!stop.isSuccess()) {
                    if (etlFile.exists() && etlFile.length() > 0 && isIgnorableStopFailure(stop)) {
                        log.warn("[ETW采集] 停止会话返回非0但ETL已生成，继续解析: {}", stop.summary());
                    } else {
                        markCommandError("STOP_FAILED: " + stop.summary());
                        continue;
                    }
                }

                boolean parsed = parseEtlWithGetWinEvent(etlFile);
                if (!parsed) {
                    CommandResult convert = runCommand(commandTimeoutMs,
                            "tracerpt", etlFile.getAbsolutePath(), "-of", "XML", "-o", xmlFile.getAbsolutePath(), "-y");
                    if (!convert.isSuccess()) {
                        markCommandError("TRACERPT_FAILED: " + convert.summary());
                        continue;
                    }
                    parseXml(xmlFile);
                }
                windowsParsed.incrementAndGet();
                lastWindowAt = System.currentTimeMillis();
                lastReason = "RUNNING";
                lastError = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                parseErrors.incrementAndGet();
                lastError = e.getMessage();
                log.warn("[ETW采集] 采集窗口处理异常: {}", e.getMessage(), e);
            } finally {
                deleteQuietly(xmlFile);
                if (deleteEtlAfterParse) {
                    deleteQuietly(etlFile);
                }
            }
        }
    }

    private boolean isIgnorableStopFailure(CommandResult result) {
        if (result == null) {
            return false;
        }
        if (result.exitCode == -2147020696) {
            return true;
        }
        String output = result.output == null ? "" : result.output.toLowerCase(Locale.ROOT);
        return output.contains("data collector set was not found")
                || output.contains("wmi")
                || output.contains("guid");
    }

    private boolean parseEtlWithGetWinEvent(File etlFile) {
        if (etlFile == null || !etlFile.exists() || etlFile.length() <= 0) {
            return false;
        }
        String escapedPath = etlFile.getAbsolutePath().replace("'", "''");
        String script = "$events = @(Get-WinEvent -Path '" + escapedPath + "' -Oldest -ErrorAction Stop | "
                + "Where-Object { $_.ProviderName -eq 'Microsoft-Windows-Kernel-Network' -and $_.Id -eq 42 } | "
                + "Select-Object Id,ProcessId,Message); "
                + "$events | ConvertTo-Json -Compress";
        try {
            CommandResult result = runCommand(commandTimeoutMs,
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script);
            if (!result.isSuccess() || result.output.trim().isEmpty()) {
                return false;
            }
            long before = eventsSeen.get();
            parseGetWinEventJson(result.output);
            return eventsSeen.get() > before || "[]".equals(result.output.trim()) || result.output.contains("\"Id\"");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            parseErrors.incrementAndGet();
            lastError = e.getMessage();
            log.debug("[ETW采集] Get-WinEvent解析失败: {}", e.getMessage());
            return false;
        }
    }

    private void parseGetWinEventJson(String json) {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return;
        }
        Object parsed = JSON.parse(trimmed);
        if (parsed instanceof JSONObject) {
            parseGetWinEventRecord((JSONObject) parsed);
        } else if (parsed instanceof JSONArray) {
            JSONArray array = (JSONArray) parsed;
            for (int i = 0; i < array.size(); i++) {
                JSONObject record = array.getJSONObject(i);
                if (record != null) {
                    parseGetWinEventRecord(record);
                }
            }
        }
    }

    private void parseGetWinEventRecord(JSONObject record) {
        Integer eventId = record.getInteger("Id");
        Integer processId = record.getInteger("ProcessId");
        String message = record.getString("Message");
        if (eventId == null || eventId != 42 || processId == null || message == null) {
            eventsRejected.incrementAndGet();
            return;
        }
        UdpSendEvent event = parseUdpSendMessage(message, processId);
        if (event == null) {
            eventsRejected.incrementAndGet();
            return;
        }
        eventsSeen.incrementAndGet();
        recordEvent(event);
    }

    private void parseXml(File xmlFile) {
        if (xmlFile == null || !xmlFile.exists() || xmlFile.length() <= 0) {
            return;
        }
        try {
            String xml = readFile(xmlFile);
            Matcher eventMatcher = EVENT_PATTERN.matcher(xml);
            while (eventMatcher.find()) {
                String eventXml = eventMatcher.group();
                Integer eventId = extractInt(EVENT_ID_PATTERN, eventXml);
                if (eventId == null || eventId != 42) {
                    continue;
                }
                Integer processId = extractInt(PROCESS_ID_PATTERN, eventXml);
                String message = extractMessage(eventXml);
                if (processId == null || message == null) {
                    eventsRejected.incrementAndGet();
                    continue;
                }
                UdpSendEvent event = parseUdpSendMessage(message, processId);
                if (event == null) {
                    eventsRejected.incrementAndGet();
                    continue;
                }
                eventsSeen.incrementAndGet();
                recordEvent(event);
            }
        } catch (Exception e) {
            parseErrors.incrementAndGet();
            lastError = e.getMessage();
            log.warn("[ETW采集] 解析ETW XML失败: file={}, reason={}", xmlFile.getAbsolutePath(), e.getMessage());
        }
    }

    private void recordEvent(UdpSendEvent event) {
        EtwTrafficEventDTO dto = new EtwTrafficEventDTO();
        dto.setSourceIp(event.sourceIp);
        dto.setSourcePort(event.sourcePort);
        dto.setTargetIp(event.targetIp);
        dto.setTargetPort(event.targetPort);
        dto.setProcessId((long) event.processId);
        dto.setPacketCount(1L);
        dto.setByteCount(event.byteCount);
        dto.setTimestampMillis(System.currentTimeMillis());
        dto.setProtocol("UDP");
        dto.setDirection("OUTBOUND");

        Map<String, Object> result = etwTrafficAccountingService.recordOutboundEvent(dto);
        if (Boolean.TRUE.equals(result.get("accepted"))) {
            eventsRecorded.incrementAndGet();
        } else {
            eventsRejected.incrementAndGet();
        }
    }

    private UdpSendEvent parseUdpSendMessage(String message, int processId) {
        String normalized = message.replaceAll("\\s+", " ").trim();
        Matcher matcher = UDP_SEND_MESSAGE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        try {
            UdpSendEvent event = new UdpSendEvent();
            event.processId = processId;
            event.byteCount = Long.parseLong(matcher.group(1));
            event.sourceIp = matcher.group(2).trim();
            event.sourcePort = Integer.parseInt(matcher.group(3));
            event.targetIp = matcher.group(4).trim();
            event.targetPort = Integer.parseInt(matcher.group(5));
            return event;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractMessage(String eventXml) {
        Matcher matcher = MESSAGE_PATTERN.matcher(eventXml);
        if (!matcher.find()) {
            return null;
        }
        return unescapeXml(matcher.group(1));
    }

    private Integer extractInt(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readFile(File file) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(file), Charset.forName("UTF-8")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private CommandResult runCommand(long timeoutMs, String... command) throws InterruptedException {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            StreamCollector collector = new StreamCollector(process);
            Thread outputThread = new Thread(collector, "etw-command-output");
            outputThread.setDaemon(true);
            outputThread.start();

            boolean finished = process.waitFor(timeoutMs > 0 ? timeoutMs : 10000L, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(-1, "TIMEOUT");
            }
            outputThread.join(1000L);
            return new CommandResult(process.exitValue(), collector.getOutput());
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            return new CommandResult(-1, e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void stopSessionQuietly() {
        try {
            runCommand(5000L, "logman", "stop", safeSessionName(), "-ets");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void markCommandError(String message) {
        commandErrors.incrementAndGet();
        lastReason = "COMMAND_ERROR";
        lastError = message;
        log.warn("[ETW采集] {}", message);
    }

    private void sleepQuietly(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (running.get()) {
            long remaining = end - System.currentTimeMillis();
            if (remaining <= 0) {
                return;
            }
            Thread.sleep(Math.min(remaining, 500L));
        }
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            log.debug("[ETW采集] 临时文件删除失败: {}", file.getAbsolutePath());
        }
    }

    private File resolveWorkDir() {
        File dir;
        if (workDir == null || workDir.trim().isEmpty()) {
            dir = new File(System.getProperty("java.io.tmpdir"), "info-publish-client-etw");
        } else {
            dir = new File(workDir.trim());
        }
        if (!dir.exists() && !dir.mkdirs()) {
            log.debug("[ETW采集] 工作目录创建失败: {}", dir.getAbsolutePath());
        }
        return dir;
    }

    private String safeSessionName() {
        String name = sessionName == null || sessionName.trim().isEmpty()
                ? "InfoPublishClientUdpEtw"
                : sessionName.trim();
        return name + "-" + Integer.toHexString(UUID.nameUUIDFromBytes(name.getBytes()).hashCode());
    }

    private long safeRotateIntervalMs() {
        return rotateIntervalMs > 0 ? rotateIntervalMs : 10000L;
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("win");
    }

    private String unescapeXml(String value) {
        return value == null ? null : value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private static class UdpSendEvent {
        private int processId;
        private String sourceIp;
        private int sourcePort;
        private String targetIp;
        private int targetPort;
        private long byteCount;
    }

    private static class CommandResult {
        private final int exitCode;
        private final String output;

        private CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }

        private boolean isSuccess() {
            return exitCode == 0;
        }

        private String summary() {
            String trimmed = output.trim();
            if (trimmed.length() > 300) {
                trimmed = trimmed.substring(0, 300);
            }
            return "exitCode=" + exitCode + ", output=" + trimmed;
        }
    }

    private static class StreamCollector implements Runnable {
        private final Process process;
        private final StringBuilder output = new StringBuilder();

        private StreamCollector(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (Exception ignored) {
                // Ignore command output collection failures.
            }
        }

        private String getOutput() {
            return output.toString();
        }
    }
}
