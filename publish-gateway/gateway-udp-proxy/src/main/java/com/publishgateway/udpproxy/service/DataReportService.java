package com.publishgateway.udpproxy.service;

import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Service
public class DataReportService {

    @Resource
    private LegacyCapturedContentReportStrategy legacyCapturedContentReportStrategy;

    @Resource
    private SecurePublishContentReportStrategy securePublishContentReportStrategy;

    public void reportAsync(String ruleId, Long chainId, byte[] data, String sourceIp) {
        reportAsync(ruleId, chainId, data, sourceIp, null, null, null);
    }

    public void reportAsync(String ruleId, Long chainId, byte[] data, String sourceIp,
                            String manufacturer, String boardIp, Integer boardPort) {
        if (legacyCapturedContentReportStrategy == null) {
            log.warn("[content-report] legacy strategy is not available: ruleId={}", ruleId);
            return;
        }
        legacyCapturedContentReportStrategy.reportAsync(ruleId, chainId, data, sourceIp,
                manufacturer, boardIp, boardPort);
    }

    public void doReport(String ruleId, Long chainId, byte[] data, String sourceIp,
                         String manufacturer, String boardIp, Integer boardPort) {
        if (legacyCapturedContentReportStrategy == null) {
            log.warn("[content-report] legacy strategy is not available: ruleId={}", ruleId);
            return;
        }
        legacyCapturedContentReportStrategy.doReport(ruleId, chainId, data, sourceIp,
                manufacturer, boardIp, boardPort);
    }

    public void reportSecurePublishAccepted(SecureDeliveryTaskRequest request) {
        if (securePublishContentReportStrategy == null) {
            log.warn("[content-report] SECURE_PUBLISH strategy is not available: requestId={}",
                    request != null ? request.getRequestId() : null);
            return;
        }
        securePublishContentReportStrategy.reportAccepted(request);
    }

    public void reportSecurePublishDelivered(String deliveryTaskId,
                                             SecureDeliveryTaskRequest request,
                                             List<SecurePublishDeliveredFile> files) {
        if (securePublishContentReportStrategy == null) {
            log.warn("[content-report] SECURE_PUBLISH strategy is not available: deliveryTaskId={}, requestId={}",
                    deliveryTaskId, request != null ? request.getRequestId() : null);
            return;
        }
        securePublishContentReportStrategy.reportDelivered(deliveryTaskId, request, files);
    }
}
