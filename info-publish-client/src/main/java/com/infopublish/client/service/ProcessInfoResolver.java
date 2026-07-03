package com.infopublish.client.service;

import java.util.Map;

/**
 * PID process metadata resolver.
 */
public interface ProcessInfoResolver {

    ProcessInfo resolve(long pid);

    Map<String, Object> getStatus();

    void clear();

    class ProcessInfo {
        private final long pid;
        private final boolean available;
        private final String processName;
        private final String executablePath;
        private final String reasonCode;

        private ProcessInfo(long pid, boolean available, String processName,
                            String executablePath, String reasonCode) {
            this.pid = pid;
            this.available = available;
            this.processName = processName;
            this.executablePath = executablePath;
            this.reasonCode = reasonCode;
        }

        public static ProcessInfo hit(long pid, String processName, String executablePath) {
            return new ProcessInfo(pid, true, processName, executablePath, "PROCESS_INFO_FOUND");
        }

        public static ProcessInfo miss(long pid, String reasonCode) {
            return new ProcessInfo(pid, false, null, null, reasonCode);
        }

        public long getPid() { return pid; }
        public boolean isAvailable() { return available; }
        public String getProcessName() { return processName; }
        public String getExecutablePath() { return executablePath; }
        public String getReasonCode() { return reasonCode; }
    }
}
