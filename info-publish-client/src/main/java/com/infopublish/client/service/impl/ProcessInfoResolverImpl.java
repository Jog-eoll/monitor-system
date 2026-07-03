package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig.ProcessPolicyProperties;
import com.infopublish.client.service.ProcessInfoResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resolves process name/path by PID with a short TTL cache.
 */
@Slf4j
@Service
public class ProcessInfoResolverImpl implements ProcessInfoResolver {

    private static final int MAX_CACHE_SIZE = 4096;

    @Resource
    private ProcessPolicyProperties properties;

    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong resolveErrors = new AtomicLong();

    @Override
    public ProcessInfo resolve(long pid) {
        if (pid <= 0) {
            cacheMisses.incrementAndGet();
            return ProcessInfo.miss(pid, "INVALID_PID");
        }

        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(pid);
        if (cached != null && cached.expireAt > now) {
            cacheHits.incrementAndGet();
            return cached.processInfo;
        }

        cacheMisses.incrementAndGet();
        ProcessInfo processInfo = queryProcessInfo(pid);
        cache.put(pid, new CacheEntry(processInfo, now + Math.max(1000L, properties.getProcessInfoCacheMs())));
        if (cache.size() > MAX_CACHE_SIZE) {
            cleanupExpired();
        }
        return processInfo;
    }

    @Override
    public Map<String, Object> getStatus() {
        cleanupExpired();
        Map<String, Object> status = new HashMap<>();
        status.put("cacheSize", cache.size());
        status.put("cacheHits", cacheHits.get());
        status.put("cacheMisses", cacheMisses.get());
        status.put("resolveErrors", resolveErrors.get());
        status.put("cacheTtlMs", properties.getProcessInfoCacheMs());
        return status;
    }

    @Override
    public void clear() {
        cache.clear();
        cacheHits.set(0L);
        cacheMisses.set(0L);
        resolveErrors.set(0L);
    }

    private ProcessInfo queryProcessInfo(long pid) {
        if (!isWindows()) {
            return ProcessInfo.miss(pid, "NOT_WINDOWS");
        }

        ProcessInfo byTasklist = queryByTasklist(pid);
        if (byTasklist.isAvailable()) {
            return byTasklist;
        }

        ProcessInfo byCim = queryByPowerShell(pid);
        if (byCim.isAvailable()) {
            return byCim;
        }

        resolveErrors.incrementAndGet();
        return byCim;
    }

    private ProcessInfo queryByPowerShell(long pid) {
        try {
            String script = "$OutputEncoding=[System.Text.Encoding]::UTF8; " +
                    "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; " +
                    "$p=Get-CimInstance Win32_Process -Filter \"ProcessId=" + pid + "\"; " +
                    "if ($p) { Write-Output ($p.Name + [char]9 + $p.ExecutablePath) }";
            String encodedCommand = Base64.getEncoder().encodeToString(
                    script.getBytes(StandardCharsets.UTF_16LE));
            ProcessBuilder builder = new ProcessBuilder(
                    resolvePowerShellPath(),
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-EncodedCommand",
                    encodedCommand);
            Process process = builder.start();
            String line = readFirstNonBlankLine(process);
            boolean exited = process.waitFor(3, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                return ProcessInfo.miss(pid, "PROCESS_QUERY_TIMEOUT");
            }
            if (process.exitValue() != 0) {
                return ProcessInfo.miss(pid, "PROCESS_QUERY_FAILED");
            }
            if (line == null) {
                return ProcessInfo.miss(pid, "PROCESS_NOT_FOUND");
            }
            String[] parts = line.split("\t", 2);
            String name = trimToNull(parts.length > 0 ? parts[0] : null);
            String path = trimToNull(parts.length > 1 ? parts[1] : null);
            if (name == null && path != null) {
                name = new File(path).getName();
            }
            if (name == null) {
                return ProcessInfo.miss(pid, "PROCESS_NAME_EMPTY");
            }
            return ProcessInfo.hit(pid, name, path);
        } catch (Exception e) {
            log.debug("[ProcessInfo] PowerShell query failed, pid={}, reason={}", pid, e.getMessage());
            return ProcessInfo.miss(pid, "PROCESS_QUERY_FAILED");
        }
    }

    private ProcessInfo queryByTasklist(long pid) {
        try {
            Process process = Runtime.getRuntime().exec("tasklist /FI \"PID eq " + pid + "\" /FO CSV /NH");
            String line = readFirstNonBlankLine(process);
            boolean exited = process.waitFor(3, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                return ProcessInfo.miss(pid, "TASKLIST_TIMEOUT");
            }
            if (line == null || line.toLowerCase(Locale.ROOT).contains("no tasks are running")) {
                return ProcessInfo.miss(pid, "PROCESS_NOT_FOUND");
            }
            String name = parseCsvFirstColumn(line);
            if (trimToNull(name) == null) {
                return ProcessInfo.miss(pid, "PROCESS_NAME_EMPTY");
            }
            return ProcessInfo.hit(pid, name.trim(), null);
        } catch (Exception e) {
            log.debug("[ProcessInfo] tasklist query failed, pid={}, reason={}", pid, e.getMessage());
            return ProcessInfo.miss(pid, "TASKLIST_QUERY_FAILED");
        }
    }

    private String readFirstNonBlankLine(Process process) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    return line;
                }
            }
            return null;
        } finally {
            reader.close();
        }
    }

    private String parseCsvFirstColumn(String line) {
        String value = line;
        int comma = value.indexOf(',');
        if (comma >= 0) {
            value = value.substring(0, comma);
        }
        value = value.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace("\"\"", "\"");
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, CacheEntry>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, CacheEntry> entry = iterator.next();
            if (entry.getValue().expireAt <= now) {
                cache.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private String resolvePowerShellPath() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.trim().isEmpty()) {
            systemRoot = "C:\\Windows";
        }
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

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class CacheEntry {
        private final ProcessInfo processInfo;
        private final long expireAt;

        private CacheEntry(ProcessInfo processInfo, long expireAt) {
            this.processInfo = processInfo;
            this.expireAt = expireAt;
        }
    }
}
