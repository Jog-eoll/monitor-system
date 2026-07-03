package com.monitorplatform.forward.entity.dto;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 目标设备系统 IP 变更确认请求。
 */
public class GatewayNetworkConfirmRequest {

    private String targetDeviceId;

    private String gatewayDeviceId;

    @NotBlank(message = "changeId不能为空")
    private String changeId;

    private String operator;

    @AssertTrue(message = "targetDeviceId不能为空")
    public boolean isTargetDeviceIdPresent() {
        return hasText(targetDeviceId) || hasText(gatewayDeviceId);
    }

    public String resolveTargetDeviceId() {
        return hasText(targetDeviceId) ? targetDeviceId.trim() : gatewayDeviceId.trim();
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("changeId", changeId);
        payload.put("operator", operator);
        return payload;
    }

    public String getTargetDeviceId() {
        return targetDeviceId;
    }

    public void setTargetDeviceId(String targetDeviceId) {
        this.targetDeviceId = targetDeviceId;
    }

    public String getGatewayDeviceId() {
        return gatewayDeviceId;
    }

    public void setGatewayDeviceId(String gatewayDeviceId) {
        this.gatewayDeviceId = gatewayDeviceId;
    }

    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
