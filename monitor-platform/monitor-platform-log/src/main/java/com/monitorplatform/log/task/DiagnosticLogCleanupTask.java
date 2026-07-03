package com.monitorplatform.log.task;

import com.monitorplatform.log.service.DiagnosticEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosticLogCleanupTask {

    private final DiagnosticEventService diagnosticEventService;

    @Scheduled(cron = "${log.cleanup-cron:0 30 2 * * ?}")
    public void cleanupExpiredLogs() {
        try {
            diagnosticEventService.cleanupExpired();
        } catch (Exception e) {
            log.warn("cleanup diagnostic event log failed", e);
        }
    }
}
