package com.monitorplatform.common.log;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class DiagnosticLogReporter {

    private final RestTemplate restTemplate;

    @Value("${diagnostic.log.enabled:true}")
    private boolean enabled;

    @Value("${monitor.log.url:http://monitor-log:8071}")
    private String logServiceUrl;

    @Value("${log.report-token:}")
    private String reportToken;

    @Value("${spring.application.name:unknown-service}")
    private String defaultServiceName;

    public DiagnosticLogReporter() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(1000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void reportAsync(DiagnosticLogReport report) {
        if (!enabled || report == null || !StringUtils.hasText(report.getEventType())) {
            return;
        }
        CompletableFuture.runAsync(() -> reportSafely(report));
    }

    private void reportSafely(DiagnosticLogReport report) {
        try {
            if (!StringUtils.hasText(report.getServiceName())) {
                report.setServiceName(defaultServiceName);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(reportToken)) {
                headers.set("X-Log-Token", reportToken);
            }
            ResponseEntity<String> response = restTemplate.postForEntity(
                    normalizeUrl(logServiceUrl) + "/log/event/report",
                    new HttpEntity<DiagnosticLogReport>(report, headers),
                    String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("report diagnostic log failed, status={}, eventType={}",
                        response.getStatusCodeValue(), report.getEventType());
            }
        } catch (Exception e) {
            log.warn("report diagnostic log exception, eventType={}, error={}",
                    report.getEventType(), e.getMessage());
        }
    }

    private String normalizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "http://monitor-log:8071";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
