package com.monitorplatform.registry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitorplatform.registry.dto.GatewayDeployLogRequest;
import com.monitorplatform.registry.dto.GatewayRegisterRequest;
import com.monitorplatform.registry.dto.GatewayRegisterResponse;
import com.monitorplatform.registry.dto.GatewaySelfTestRequest;
import com.monitorplatform.registry.dto.GatewaySelfTestResponse;
import com.monitorplatform.registry.dto.UkeyCertValidateRequest;
import com.monitorplatform.registry.entity.GatewayDeployLog;
import com.monitorplatform.registry.entity.GatewaySelfTestReport;
import com.monitorplatform.registry.entity.ServiceInstance;
import com.monitorplatform.registry.exception.GatewayDeployException;
import com.monitorplatform.registry.feign.UkeyDeployFeignClient;
import com.monitorplatform.registry.mapper.GatewayDeployLogMapper;
import com.monitorplatform.registry.mapper.GatewaySelfTestReportMapper;
import com.monitorplatform.registry.mapper.ServiceInstanceMapper;
import com.monitorplatform.registry.service.ClientConfigService;
import com.monitorplatform.registry.service.GatewayDeployService;
import feign.FeignException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class GatewayDeployServiceImpl implements GatewayDeployService {

    private static final int SUCCESS_CODE = 200;
    private static final String GATEWAY_DEVICE_TYPE = "encrypt-gateway";
    private static final String STATUS_ONLINE = "\u5728\u7ebf";
    private static final String SELF_TEST_PASS = "PASS";
    private static final String SELF_TEST_FAIL = "FAIL";

    @Resource
    private ServiceInstanceMapper serviceInstanceMapper;

    @Resource
    private ClientConfigService clientConfigService;

    @Resource
    private UkeyDeployFeignClient ukeyDeployFeignClient;

    @Resource
    private GatewaySelfTestReportMapper gatewaySelfTestReportMapper;

    @Resource
    private GatewayDeployLogMapper gatewayDeployLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GatewayRegisterResponse register(GatewayRegisterRequest request) {
        if (request == null) {
            throw new GatewayDeployException(400, "register request cannot be null");
        }
        normalizeRegisterRequest(request);

        CertBindingResult certResult = validateAndBindCertificate(request);

        LocalDateTime now = LocalDateTime.now();
        ServiceInstance existing = findByDeviceId(request.getDeviceId());
        ServiceInstance instance = existing == null ? new ServiceInstance() : existing;
        fillGatewayInstance(instance, request, certResult, now, existing == null);
        upsertInstance(instance, existing == null);

        try {
            clientConfigService.ensureDefaultConfig(instance);
        } catch (Exception e) {
            log.warn("init gateway config failed: deviceId={}, error={}", request.getDeviceId(), e.getMessage());
        }

        Map<String, Object> configMap = clientConfigService.getConfigMap(request.getDeviceId());
        GatewayRegisterResponse response = buildRegisterResponse(request, certResult, configMap, now);
        log.info("gateway registered: deviceId={}, ip={}, port={}, role={}",
                request.getDeviceId(), request.getIp(), request.getPort(), request.getRole());
        return response;
    }

    @Override
    public Map<String, Object> getConfig(String deviceId) {
        String normalizedDeviceId = trimToNull(deviceId);
        if (normalizedDeviceId == null) {
            throw new GatewayDeployException(400, "deviceId cannot be blank");
        }

        ServiceInstance instance = findByDeviceId(normalizedDeviceId);
        if (instance == null) {
            throw new GatewayDeployException(404, "gateway is not registered");
        }

        try {
            clientConfigService.ensureDefaultConfig(instance);
        } catch (Exception e) {
            log.warn("ensure gateway config failed: deviceId={}, error={}", normalizedDeviceId, e.getMessage());
        }

        Map<String, Object> config = clientConfigService.getConfigMap(normalizedDeviceId);
        if (config == null) {
            throw new GatewayDeployException(404, "gateway config not found or disabled");
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GatewaySelfTestResponse reportSelfTest(String deviceId, GatewaySelfTestRequest request) {
        String normalizedDeviceId = trimToNull(deviceId);
        if (normalizedDeviceId == null) {
            throw new GatewayDeployException(400, "deviceId cannot be blank");
        }
        if (request == null) {
            request = new GatewaySelfTestRequest();
        }

        ServiceInstance instance = findByDeviceId(normalizedDeviceId);
        if (instance == null) {
            throw new GatewayDeployException(404, "gateway is not registered");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reportTime = request.getReportTime() == null ? now : request.getReportTime();
        String overallStatus = isSelfTestPassed(request) ? SELF_TEST_PASS : SELF_TEST_FAIL;

        GatewaySelfTestReport report = new GatewaySelfTestReport();
        report.setDeviceId(normalizedDeviceId);
        report.setOverallStatus(overallStatus);
        report.setEncryptSampleOk(request.getEncryptSampleOk());
        report.setDecryptSampleOk(request.getDecryptSampleOk());
        report.setPlatformHandshakeOk(request.getPlatformHandshakeOk());
        report.setUkeyStatusOk(request.getUkeyStatusOk());
        report.setDetailJson(toJson(request.getDetail()));
        report.setErrorMessage(truncate(request.getErrorMessage(), 1000));
        report.setReportTime(reportTime);
        report.setCreateTime(now);
        gatewaySelfTestReportMapper.insert(report);

        updateLastSelfTest(instance, request, overallStatus, reportTime, now);

        GatewaySelfTestResponse response = new GatewaySelfTestResponse();
        response.setDeviceId(normalizedDeviceId);
        response.setOverallStatus(overallStatus);
        response.setEncryptSampleOk(request.getEncryptSampleOk());
        response.setDecryptSampleOk(request.getDecryptSampleOk());
        response.setPlatformHandshakeOk(request.getPlatformHandshakeOk());
        response.setUkeyStatusOk(request.getUkeyStatusOk());
        response.setReportTime(reportTime);
        log.info("gateway self-test reported: deviceId={}, status={}", normalizedDeviceId, overallStatus);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reportLog(GatewayDeployLogRequest request, String clientIp) {
        if (request == null) {
            throw new GatewayDeployException(400, "log request cannot be null");
        }
        String message = trimToNull(request.getMessage());
        if (message == null) {
            throw new GatewayDeployException(400, "message cannot be blank");
        }

        LocalDateTime now = LocalDateTime.now();
        GatewayDeployLog deployLog = new GatewayDeployLog();
        deployLog.setDeviceId(trimToNull(request.getDeviceId()));
        deployLog.setSourceType(normalizeEnumText(request.getSourceType(), "GATEWAY", 50));
        deployLog.setLogLevel(normalizeEnumText(request.getLevel(), "INFO", 20));
        deployLog.setMessage(truncate(message, 2000));
        deployLog.setContextJson(toJson(request.getContext()));
        deployLog.setClientIp(truncate(trimToNull(clientIp), 64));
        deployLog.setReportTime(request.getReportTime() == null ? now : request.getReportTime());
        deployLog.setCreateTime(now);
        gatewayDeployLogMapper.insert(deployLog);
        return deployLog.getId();
    }

    private void normalizeRegisterRequest(GatewayRegisterRequest request) {
        request.setDeviceId(requireTrimmed(request.getDeviceId(), "deviceId"));
        request.setIp(requireTrimmed(request.getIp(), "ip"));
        request.setRole(requireTrimmed(request.getRole(), "role"));
        request.setCertSerialNo(requireTrimmed(request.getCertSerialNo(), "certSerialNo"));
        request.setUkeySn(requireTrimmed(request.getUkeySn(), "ukeySn"));
        request.setMacAddress(trimToNull(request.getMacAddress()));
        request.setDeviceName(trimToNull(request.getDeviceName()));
        request.setVersion(trimToNull(request.getVersion()));
        request.setManufacturer(trimToNull(request.getManufacturer()));
        request.setModel(trimToNull(request.getModel()));
    }

    private CertBindingResult validateAndBindCertificate(GatewayRegisterRequest request) {
        UkeyCertValidateRequest validateRequest = new UkeyCertValidateRequest();
        validateRequest.setCertSerialNo(request.getCertSerialNo());
        validateRequest.setCertificateContent(request.getCertificateContent());
        validateRequest.setClientId(request.getDeviceId());
        validateRequest.setClientIp(request.getIp());
        validateRequest.setClientMac(request.getMacAddress());

        try {
            Map<String, Object> validateResponse = ukeyDeployFeignClient.validateCertificate(validateRequest);
            assertSuccess(validateResponse, "certificate validation failed");

            Map<String, Object> validateData = asMap(validateResponse.get("data"));
            boolean valid = asBoolean(validateData.get("valid"), true);
            if (!valid) {
                throw new GatewayDeployException(400, messageOf(validateResponse, "certificate validation failed"));
            }

            Map<String, String> bindRequest = new HashMap<>();
            bindRequest.put("certSerialNo", request.getCertSerialNo());
            bindRequest.put("clientId", request.getDeviceId());
            Map<String, Object> bindResponse = ukeyDeployFeignClient.bindClient(bindRequest);
            assertSuccess(bindResponse, "certificate bind failed");

            Map<String, Object> bindData = asMap(bindResponse.get("data"));
            CertBindingResult result = new CertBindingResult();
            result.setValid(true);
            result.setBound(true);
            result.setCertSerialNo(firstNotBlank(
                    stringValue(bindData.get("certSerialNo")),
                    stringValue(validateData.get("certSerialNo")),
                    request.getCertSerialNo()));
            result.setBoundClientId(firstNotBlank(
                    stringValue(bindData.get("boundClientId")),
                    stringValue(validateData.get("boundClientId")),
                    request.getDeviceId()));
            return result;
        } catch (GatewayDeployException e) {
            throw e;
        } catch (FeignException e) {
            log.warn("ukey service unavailable while registering gateway: deviceId={}, status={}, error={}",
                    request.getDeviceId(), e.status(), e.getMessage());
            throw new GatewayDeployException(503, "ukey service unavailable: " + e.status());
        } catch (Exception e) {
            log.error("ukey certificate validate/bind failed: deviceId={}", request.getDeviceId(), e);
            throw new GatewayDeployException(500, "ukey certificate validate/bind failed");
        }
    }

    private void assertSuccess(Map<String, Object> response, String defaultMessage) {
        if (response == null) {
            throw new GatewayDeployException(503, defaultMessage + ": empty response");
        }
        int code = asInt(response.get("code"), 500);
        if (code != SUCCESS_CODE) {
            throw new GatewayDeployException(code, messageOf(response, defaultMessage));
        }
    }

    private void fillGatewayInstance(ServiceInstance instance, GatewayRegisterRequest request,
                                     CertBindingResult certResult, LocalDateTime now, boolean create) {
        instance.setDeviceId(request.getDeviceId());
        instance.setDeviceName(firstNotBlank(request.getDeviceName(), "gateway(" + request.getIp() + ")"));
        instance.setDeviceType(GATEWAY_DEVICE_TYPE);
        instance.setIpAddress(request.getIp());
        instance.setPort(request.getPort());
        instance.setMac(request.getMacAddress());
        instance.setStatus(STATUS_ONLINE);
        instance.setVersion(request.getVersion());
        instance.setManufacturer(request.getManufacturer());
        instance.setModel(request.getModel());
        instance.setLastOnlineTime(now);
        instance.setUpdateTime(now);
        instance.setRemark("gateway role=" + request.getRole());
        if (create) {
            instance.setCreateTime(now);
        }
        fillGatewayExtraInfo(instance, request, certResult, now);
    }

    private void fillGatewayExtraInfo(ServiceInstance instance, GatewayRegisterRequest request,
                                      CertBindingResult certResult, LocalDateTime now) {
        Map<String, Object> extraInfo = readJson(instance.getExtraInfo());
        Map<String, Object> gatewayInfo = new LinkedHashMap<>();
        gatewayInfo.put("role", request.getRole());
        gatewayInfo.put("certSerialNo", certResult.getCertSerialNo());
        gatewayInfo.put("ukeySn", request.getUkeySn());
        gatewayInfo.put("certificateValid", certResult.getValid());
        gatewayInfo.put("certificateBound", certResult.getBound());
        gatewayInfo.put("boundClientId", certResult.getBoundClientId());
        gatewayInfo.put("capabilities", request.getCapabilities() == null ? Collections.emptyMap() : request.getCapabilities());
        gatewayInfo.put("lastRegisterTime", now.toString());
        extraInfo.put("gatewayDeploy", gatewayInfo);
        instance.setExtraInfo(toJson(extraInfo));
    }

    private void updateLastSelfTest(ServiceInstance instance, GatewaySelfTestRequest request,
                                    String overallStatus, LocalDateTime reportTime, LocalDateTime now) {
        Map<String, Object> extraInfo = readJson(instance.getExtraInfo());
        Map<String, Object> selfTest = new LinkedHashMap<>();
        selfTest.put("overallStatus", overallStatus);
        selfTest.put("encryptSampleOk", request.getEncryptSampleOk());
        selfTest.put("decryptSampleOk", request.getDecryptSampleOk());
        selfTest.put("platformHandshakeOk", request.getPlatformHandshakeOk());
        selfTest.put("ukeyStatusOk", request.getUkeyStatusOk());
        selfTest.put("reportTime", reportTime.toString());
        extraInfo.put("lastSelfTest", selfTest);
        instance.setExtraInfo(toJson(extraInfo));
        instance.setUpdateTime(now);
        serviceInstanceMapper.updateById(instance);
    }

    private void upsertInstance(ServiceInstance instance, boolean create) {
        if (!create) {
            serviceInstanceMapper.updateById(instance);
            return;
        }
        try {
            serviceInstanceMapper.insert(instance);
        } catch (DuplicateKeyException e) {
            ServiceInstance existing = findByDeviceId(instance.getDeviceId());
            if (existing == null) {
                throw e;
            }
            instance.setId(existing.getId());
            instance.setCreateTime(existing.getCreateTime());
            serviceInstanceMapper.updateById(instance);
        }
    }

    private GatewayRegisterResponse buildRegisterResponse(GatewayRegisterRequest request,
                                                          CertBindingResult certResult,
                                                          Map<String, Object> configMap,
                                                          LocalDateTime now) {
        GatewayRegisterResponse response = new GatewayRegisterResponse();
        response.setDeviceId(request.getDeviceId());
        response.setIp(request.getIp());
        response.setPort(request.getPort());
        response.setRole(request.getRole());
        response.setCertSerialNo(certResult.getCertSerialNo());
        response.setUkeySn(request.getUkeySn());
        response.setCertificateValid(certResult.getValid());
        response.setCertificateBound(certResult.getBound());
        response.setBoundClientId(certResult.getBoundClientId());
        response.setConfigVersion(extractConfigVersion(configMap));
        response.setConfig(extractConfig(configMap));
        response.setRegisterTime(now);
        return response;
    }

    private ServiceInstance findByDeviceId(String deviceId) {
        String normalizedDeviceId = trimToNull(deviceId);
        if (normalizedDeviceId == null) {
            return null;
        }
        LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServiceInstance::getDeviceId, normalizedDeviceId);
        return serviceInstanceMapper.selectOne(wrapper);
    }

    private boolean isSelfTestPassed(GatewaySelfTestRequest request) {
        return Boolean.TRUE.equals(request.getEncryptSampleOk())
                && Boolean.TRUE.equals(request.getDecryptSampleOk())
                && Boolean.TRUE.equals(request.getPlatformHandshakeOk())
                && Boolean.TRUE.equals(request.getUkeyStatusOk());
    }

    private Map<String, Object> readJson(String json) {
        if (trimToNull(json) == null) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("parse gateway extra_info failed: error={}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Object value) {
        try {
            Object target = value == null ? new LinkedHashMap<String, Object>() : value;
            return objectMapper.writeValueAsString(target);
        } catch (Exception e) {
            throw new GatewayDeployException(400, "invalid json content");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    private String messageOf(Map<String, Object> response, String defaultMessage) {
        String message = stringValue(response == null ? null : response.get("msg"));
        return trimToNull(message) == null ? defaultMessage : message;
    }

    private Long extractConfigVersion(Map<String, Object> configMap) {
        if (configMap == null) {
            return null;
        }
        Object version = configMap.get("configVersion");
        if (version instanceof Number) {
            return ((Number) version).longValue();
        }
        if (version instanceof String) {
            try {
                return Long.parseLong((String) version);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> extractConfig(Map<String, Object> configMap) {
        if (configMap == null) {
            return Collections.emptyMap();
        }
        return asMap(configMap.get("config"));
    }

    private String normalizeEnumText(String value, String defaultValue, int maxLength) {
        String result = trimToNull(value);
        if (result == null) {
            result = defaultValue;
        }
        return truncate(result.toUpperCase(), maxLength);
    }

    private String requireTrimmed(String value, String fieldName) {
        String result = trimToNull(value);
        if (result == null) {
            throw new GatewayDeployException(400, fieldName + " cannot be blank");
        }
        return result;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNotBlank(String first, String second) {
        String firstValue = trimToNull(first);
        return firstValue == null ? trimToNull(second) : firstValue;
    }

    private String firstNotBlank(String first, String second, String third) {
        String value = firstNotBlank(first, second);
        return value == null ? trimToNull(third) : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Data
    private static class CertBindingResult {
        private Boolean valid;
        private Boolean bound;
        private String certSerialNo;
        private String boundClientId;
    }
}
