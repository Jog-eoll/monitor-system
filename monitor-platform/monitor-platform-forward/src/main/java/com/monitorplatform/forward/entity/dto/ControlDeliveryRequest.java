package com.monitorplatform.forward.entity.dto;

import com.alibaba.fastjson2.JSON;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MQTT control delivery request.
 */
public class ControlDeliveryRequest {

    @NotBlank(message = "gatewayDeviceId cannot be blank")
    private String gatewayDeviceId;

    @NotBlank(message = "targetDeviceType cannot be blank")
    private String targetDeviceType;

    @NotBlank(message = "controlCommand cannot be blank")
    private String controlCommand;

    private Map<String, Object> params;

    private String targetIp;

    private Integer targetPort;

    /**
     * Target device id embedded in the control package. If omitted, gatewayDeviceId
     * is used for backward compatibility.
     */
    private String targetDeviceId;

    /**
     * Source client id embedded in the control package.
     */
    private String sourceClientId = "monitor-platform-forward";

    private String businessId;

    private Boolean waitForReply = true;

    @Min(value = 1, message = "waitTimeoutSec must be between 1 and 120")
    @Max(value = 120, message = "waitTimeoutSec must be between 1 and 120")
    private Integer waitTimeoutSec = 30;

    public Map<String, Object> toActionBody() {
        String commandTaskId = "CTRL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> target = buildTarget();

        Map<String, Object> plainPackage = new LinkedHashMap<>();
        plainPackage.put("command", controlCommand);
        if (params != null && !params.isEmpty()) {
            plainPackage.put("params", params);
        }
        plainPackage.put("commandTaskId", commandTaskId);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("clientId", trimToDefault(sourceClientId, "monitor-platform-forward"));
        source.put("clientType", "monitor-platform");
        plainPackage.put("source", source);
        if (!target.isEmpty()) {
            plainPackage.put("target", new LinkedHashMap<>(target));
        }

        String plainJson = JSON.toJSONString(plainPackage);
        String encryptedCommandPackage = Base64.getEncoder()
                .encodeToString(plainJson.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("commandTaskId", commandTaskId);
        body.put("encryptedCommandPackage", encryptedCommandPackage);
        body.put("command", controlCommand);
        if (!target.isEmpty()) {
            body.put("target", target);
        }
        String resolvedTargetIp = trimToNull(targetIp);
        if (resolvedTargetIp != null) {
            body.put("targetIp", resolvedTargetIp);
        }
        if (targetPort != null && targetPort > 0) {
            body.put("targetPort", targetPort);
        }
        return body;
    }

    private Map<String, Object> buildTarget() {
        Map<String, Object> target = new LinkedHashMap<>();
        String resolvedTargetDeviceId = trimToNull(targetDeviceId);
        if (resolvedTargetDeviceId == null) {
            resolvedTargetDeviceId = trimToNull(gatewayDeviceId);
        }
        if (resolvedTargetDeviceId != null) {
            target.put("deviceId", resolvedTargetDeviceId);
        }
        String resolvedTargetIp = trimToNull(targetIp);
        if (resolvedTargetIp != null) {
            target.put("ip", resolvedTargetIp);
        }
        if (targetPort != null && targetPort > 0) {
            target.put("port", targetPort);
        }
        return target;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    public String getGatewayDeviceId() {
        return gatewayDeviceId;
    }

    public void setGatewayDeviceId(String gatewayDeviceId) {
        this.gatewayDeviceId = gatewayDeviceId;
    }

    public String getTargetDeviceType() {
        return targetDeviceType;
    }

    public void setTargetDeviceType(String targetDeviceType) {
        this.targetDeviceType = targetDeviceType;
    }

    public String getControlCommand() {
        return controlCommand;
    }

    public void setControlCommand(String controlCommand) {
        this.controlCommand = controlCommand;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public String getTargetIp() {
        return targetIp;
    }

    public void setTargetIp(String targetIp) {
        this.targetIp = targetIp;
    }

    public Integer getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
    }

    public String getTargetDeviceId() {
        return targetDeviceId;
    }

    public void setTargetDeviceId(String targetDeviceId) {
        this.targetDeviceId = targetDeviceId;
    }

    public String getSourceClientId() {
        return sourceClientId;
    }

    public void setSourceClientId(String sourceClientId) {
        this.sourceClientId = sourceClientId;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public Boolean getWaitForReply() {
        return waitForReply;
    }

    public void setWaitForReply(Boolean waitForReply) {
        this.waitForReply = waitForReply;
    }

    public Integer getWaitTimeoutSec() {
        return waitTimeoutSec;
    }

    public void setWaitTimeoutSec(Integer waitTimeoutSec) {
        this.waitTimeoutSec = waitTimeoutSec;
    }
}
