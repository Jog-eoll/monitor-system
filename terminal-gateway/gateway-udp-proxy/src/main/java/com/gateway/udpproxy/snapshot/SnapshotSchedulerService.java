package com.gateway.udpproxy.snapshot;

import com.alibaba.fastjson2.JSON;
import com.gateway.udpproxy.entity.UdpProxyRule;
import com.gateway.udpproxy.manager.UdpProxyRuleManager;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大屏截图回显调度服务（第一阶段）
 */
@Slf4j
@Service
public class SnapshotSchedulerService {

    @Resource
    private UdpProxyRuleManager ruleManager;

    @Resource
    private ScreenSnapshotStrategyRegistry strategyRegistry;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private MinioClient minioClient;

    @Value("${snapshot.enabled:true}")
    private boolean snapshotEnabled;

    @Value("${snapshot.interval-seconds:10}")
    private int intervalSeconds;

    @Value("${snapshot.timeout-ms:5000}")
    private int snapshotTimeoutMs;

    @Value("${snapshot.tmp-dir:${java.io.tmpdir}/terminal-gateway-snapshots}")
    private String snapshotTmpDir;

    @Value("${snapshot.report-url:http://${MONITOR_HOST:127.0.0.1}:${MONITOR_CONTENT_PORT:8065}/content/receive}")
    private String reportUrl;

    @Value("${snapshot.minio.bucket-name:monitor-platform}")
    private String minioBucketName;

    @Value("${snapshot.report-retry-once:true}")
    private boolean reportRetryOnce;

    private final Map<String, Integer> ruleFailCount = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${snapshot.interval-seconds:10}000")
    public void scheduledSnapshot() {
        if (!snapshotEnabled) {
            return;
        }
        long start = System.currentTimeMillis();
        List<UdpProxyRule> rules = ruleManager.getAllEnabledRules();
        if (rules == null || rules.isEmpty()) {
            log.debug("[snapshot] no enabled rules");
            return;
        }

        for (UdpProxyRule rule : rules) {
            processRule(rule);
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[snapshot] cycle done, rules={}, costMs={}, intervalSeconds={}",
                rules.size(), cost, intervalSeconds);
    }

    private void processRule(UdpProxyRule rule) {
        String ruleTag = ruleManager.summarizeRule(rule);
        try {
            ScreenSnapshotStrategy strategy = strategyRegistry.resolve(rule.getManufacturer());
            String tmpFilePath = buildTmpFilePath(rule);

            SnapshotResult captureResult = strategy.capture(rule, tmpFilePath, snapshotTimeoutMs);
            if (!captureResult.isSuccess()) {
                recordFailure(rule, "capture failed: " + captureResult.getMessage());
                safeDelete(tmpFilePath);
                return;
            }

            String objectPath = uploadToMinio(rule, captureResult.getLocalFilePath());
            if (!StringUtils.hasText(objectPath)) {
                recordFailure(rule, "upload minio failed");
                safeDelete(captureResult.getLocalFilePath());
                return;
            }

            boolean reported = reportContent(rule, objectPath);
            if (!reported) {
                recordFailure(rule, "report failed");
            } else {
                ruleFailCount.remove(rule.getRuleId());
                log.info("[snapshot] success {}", ruleTag);
            }
            safeDelete(captureResult.getLocalFilePath());
        } catch (Exception e) {
            recordFailure(rule, "process exception: " + e.getMessage());
            log.warn("[snapshot] exception {}", ruleTag, e);
        }
    }

    private String uploadToMinio(UdpProxyRule rule, String localFilePath) {
        File file = new File(localFilePath);
        if (!file.exists() || file.length() == 0) {
            return null;
        }

        try {
            ensureBucketExists(minioBucketName);
            String objectPath = buildObjectPath(rule, file.getName());
            try (FileInputStream fis = new FileInputStream(file)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioBucketName)
                                .object(objectPath)
                                .stream(fis, file.length(), -1)
                                .contentType("image/jpeg")
                                .build()
                );
            }
            return objectPath;
        } catch (Exception e) {
            log.warn("[snapshot] upload minio failed, ruleId={}, chainId={}, err={}",
                    rule.getRuleId(), rule.getChainId(), e.getMessage());
            return null;
        }
    }

    private boolean reportContent(UdpProxyRule rule, String minioPath) {
        if (!StringUtils.hasText(reportUrl)) {
            log.warn("[snapshot] report-url empty, skip report");
            return false;
        }

        long now = System.currentTimeMillis();
        Map<String, Object> body = new HashMap<>();
        body.put("contentId", "SNAP-" + rule.getChainId() + "-" + now);
        body.put("gatewayId", rule.getChainId() + "_terminal");
        body.put("chainId", rule.getChainId());
        body.put("contentType", "image");
        body.put("minioPath", minioPath);
        body.put("sourceIp", rule.getTargetIp());
        body.put("boardIp", rule.getTargetIp());
        body.put("boardPort", rule.getTargetPort());
        body.put("fileName", "snapshot-" + now + ".jpg");
        body.put("description", "terminal-gateway snapshot " + LocalDateTime.now());
        body.put("timestamp", now);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(JSON.toJSONString(body), headers);

        boolean ok = tryReport(request);
        if (!ok && reportRetryOnce) {
            ok = tryReport(request);
        }
        return ok;
    }

    private boolean tryReport(HttpEntity<String> request) {
        try {
            Map<?, ?> resp = restTemplate.postForObject(reportUrl, request, Map.class);
            if (resp == null) {
                return false;
            }
            Object success = resp.get("success");
            if (success instanceof Boolean) {
                return (Boolean) success;
            }
            return true;
        } catch (Exception e) {
            log.warn("[snapshot] report failed: {}", e.getMessage());
            return false;
        }
    }

    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("[snapshot] minio bucket created: {}", bucket);
        }
    }

    private String buildTmpFilePath(UdpProxyRule rule) {
        String fileName = "snap-" + rule.getChainId() + "-" + System.currentTimeMillis() + ".jpg";
        File dir = Paths.get(snapshotTmpDir).toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("create tmp dir failed: " + snapshotTmpDir);
        }
        return new File(dir, fileName).getAbsolutePath();
    }

    private String buildObjectPath(UdpProxyRule rule, String fileName) {
        LocalDate now = LocalDate.now();
        return String.format("snapshots/%d/%02d/%02d/%s/%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                rule.getChainId(), fileName);
    }

    private void recordFailure(UdpProxyRule rule, String reason) {
        int count = ruleFailCount.merge(rule.getRuleId(), 1, Integer::sum);
        log.warn("[snapshot] failed {}, count={}, reason={}",
                ruleManager.summarizeRule(rule), count, reason);
    }

    private void safeDelete(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            File file = new File(filePath);
            if (file.exists() && !file.delete()) {
                log.debug("[snapshot] tmp file delete failed: {}", filePath);
            }
        } catch (Exception ignore) {
            // ignore
        }
    }
}
