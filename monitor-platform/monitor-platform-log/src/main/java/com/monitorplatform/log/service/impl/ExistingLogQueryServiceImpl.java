package com.monitorplatform.log.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.log.dto.LinkLogQueryDTO;
import com.monitorplatform.log.dto.OperationLogQueryDTO;
import com.monitorplatform.log.dto.PublishAuditLogQueryDTO;
import com.monitorplatform.log.dto.PublishLogQueryDTO;
import com.monitorplatform.log.dto.UnifiedPageResult;
import com.monitorplatform.log.mapper.ExistingLogQueryMapper;
import com.monitorplatform.log.service.ExistingLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExistingLogQueryServiceImpl implements ExistingLogQueryService {

    private final ExistingLogQueryMapper existingLogQueryMapper;

    @Value("${minio.endpoint:}")
    private String minioEndpoint;

    @Value("${log.audit.minio-bucket:${minio.bucket:monitor-content}}")
    private String minioBucketName;

    @Override
    public IPage<Map<String, Object>> pagePublish(PublishLogQueryDTO queryDTO) {
        PublishLogQueryDTO query = queryDTO == null ? new PublishLogQueryDTO() : queryDTO;
        Page<Map<String, Object>> page = PageSupport.page(query);
        return existingLogQueryMapper.pagePublish(page, query);
    }

    @Override
    public IPage<Map<String, Object>> pageOperation(OperationLogQueryDTO queryDTO) {
        OperationLogQueryDTO query = queryDTO == null ? new OperationLogQueryDTO() : queryDTO;
        Page<Map<String, Object>> page = PageSupport.page(query);
        return existingLogQueryMapper.pageOperation(page, query);
    }

    @Override
    public IPage<Map<String, Object>> pageLink(LinkLogQueryDTO queryDTO) {
        LinkLogQueryDTO query = queryDTO == null ? new LinkLogQueryDTO() : queryDTO;
        Page<Map<String, Object>> page = PageSupport.page(query);
        return existingLogQueryMapper.pageLink(page, query);
    }

    @Override
    public UnifiedPageResult<Map<String, Object>> pagePublishAudit(PublishAuditLogQueryDTO queryDTO) {
        PublishAuditLogQueryDTO query = queryDTO == null ? new PublishAuditLogQueryDTO() : queryDTO;
        Page<Map<String, Object>> page = PageSupport.page(query);
        IPage<Map<String, Object>> result = existingLogQueryMapper.pagePublishAudit(page, query);
        fillFileUrls(result);
        fillOperationPageFields(result, query);
        return UnifiedPageResult.of(result);
    }

    private void fillFileUrls(IPage<Map<String, Object>> page) {
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return;
        }
        for (Map<String, Object> record : page.getRecords()) {
            if (record == null) {
                continue;
            }
            String fileUrl = buildFileUrl(record.get("minioPath"));
            record.put("fileUrl", fileUrl);
            record.put("previewUrl", fileUrl);
            record.put("downloadUrl", fileUrl);
        }
    }

    private void fillOperationPageFields(IPage<Map<String, Object>> page, PublishAuditLogQueryDTO query) {
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return;
        }
        long serialNo = Math.max(page.getCurrent() - 1, 0) * page.getSize() + 1;
        for (Map<String, Object> record : page.getRecords()) {
            if (record == null) {
                continue;
            }
            record.put("serialNo", serialNo++);
            Object eventTime = record.get("eventTime");
            record.put("operationTime", eventTime);
            record.put("createTime", eventTime);

            record.put("type", "operation");
            record.put("logType", "operation");
            record.put("logTypeName", "操作");

            String operationType = resolveOperationType(record, query);
            record.put("actType", operationType);
            record.put("operationType", operationType);
            record.put("operationTypeName", operationTypeName(operationType));

            record.put("actModule", "publish_audit");
            record.put("operationModule", "publish_audit");
            record.put("operationModuleName", "发布审计");

            Object action = record.get("action");
            record.put("actAction", action);
            record.put("operationFunction", action);

            String ip = firstText(record.get("clientIp"), record.get("sourceIp"), record.get("boardIp"));
            record.put("ip", ip);
            if (!StringUtils.hasText(asText(record.get("clientIp")))) {
                record.put("clientIp", ip);
            }

            String result = resolveOperationResult(record);
            record.put("result", result);
            record.put("resultStatus", result);
            record.put("resultName", resultName(result));
            record.put("actResult", actResultCode(result));
            record.put("detailAvailable", hasAnyText(record.get("traceId"), record.get("contentId"), record.get("fileUrl"), record.get("reason"), record.get("alarmDetail")));
        }
    }

    private String resolveOperationType(Map<String, Object> record, PublishAuditLogQueryDTO query) {
        String requestedType = normalizeOperationType(firstText(query.getOperationType(), query.getActType()));
        if (StringUtils.hasText(requestedType)) {
            return requestedType;
        }
        if ("fail".equalsIgnoreCase(asText(record.get("verifyStatus")))) {
            return "verify";
        }
        if ("fail".equalsIgnoreCase(asText(record.get("gatewayReportStatus")))) {
            return "gateway_report";
        }
        String complianceStatus = asText(record.get("complianceStatus"));
        if ("violation".equalsIgnoreCase(complianceStatus) || "pending".equalsIgnoreCase(complianceStatus)) {
            return "audit";
        }
        return "publish";
    }

    private String normalizeOperationType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if ("签名".equals(value)) {
            return "sign";
        }
        if ("验签".equals(value)) {
            return "verify";
        }
        if ("网关上报".equals(value)) {
            return "gateway_report";
        }
        if ("内容审核".equals(value) || "审核".equals(value)) {
            return "audit";
        }
        if ("发布".equals(value)) {
            return "publish";
        }
        return value;
    }

    private String resolveOperationResult(Map<String, Object> record) {
        if ("fail".equalsIgnoreCase(asText(record.get("signStatus")))
                || "fail".equalsIgnoreCase(asText(record.get("verifyStatus")))
                || "fail".equalsIgnoreCase(asText(record.get("gatewayReportStatus")))
                || "violation".equalsIgnoreCase(asText(record.get("complianceStatus")))) {
            return "fail";
        }
        if ("pending".equalsIgnoreCase(asText(record.get("signStatus")))
                || "pending".equalsIgnoreCase(asText(record.get("verifyStatus")))
                || "pending".equalsIgnoreCase(asText(record.get("gatewayReportStatus")))
                || "pending".equalsIgnoreCase(asText(record.get("complianceStatus")))) {
            return "pending";
        }
        return "success";
    }

    private String operationTypeName(String operationType) {
        if ("sign".equals(operationType)) {
            return "签名";
        }
        if ("verify".equals(operationType)) {
            return "验签";
        }
        if ("gateway_report".equals(operationType)) {
            return "网关上报";
        }
        if ("audit".equals(operationType)) {
            return "内容审核";
        }
        return "发布";
    }

    private String resultName(String result) {
        if ("fail".equals(result)) {
            return "失败";
        }
        if ("pending".equals(result)) {
            return "待处理";
        }
        return "成功";
    }

    private String actResultCode(String result) {
        if ("fail".equals(result)) {
            return "500";
        }
        if ("pending".equals(result)) {
            return "102";
        }
        return "200";
    }

    private boolean hasAnyText(Object... values) {
        if (values == null) {
            return false;
        }
        for (Object value : values) {
            if (StringUtils.hasText(asText(value))) {
                return true;
            }
        }
        return false;
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String text = asText(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String buildFileUrl(Object minioPathValue) {
        if (minioPathValue == null || !StringUtils.hasText(String.valueOf(minioPathValue))) {
            return null;
        }
        if (!StringUtils.hasText(minioEndpoint) || !StringUtils.hasText(minioBucketName)) {
            return null;
        }
        String endpoint = trimTrailingSlash(minioEndpoint.trim());
        String bucket = trimSlash(minioBucketName.trim());
        String objectName = trimLeadingSlash(String.valueOf(minioPathValue).trim());
        return endpoint + "/" + bucket + "/" + objectName;
    }

    private String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String trimLeadingSlash(String value) {
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private String trimSlash(String value) {
        return trimTrailingSlash(trimLeadingSlash(value));
    }
}
