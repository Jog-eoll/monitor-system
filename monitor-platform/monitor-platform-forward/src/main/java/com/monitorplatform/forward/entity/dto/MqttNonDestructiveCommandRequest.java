package com.monitorplatform.forward.entity.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 非破坏命令请求（NOOP / ECHO）。
 * <p>
 * 仅允许 QUERY_STATUS / NOOP / ECHO 三个非破坏命令使用，不开放任意 command 透传。
 * </p>
 */
public class MqttNonDestructiveCommandRequest {

    @NotBlank(message = "gatewayDeviceId cannot be blank")
    private String gatewayDeviceId;

    private String businessId;

    private Boolean waitForReply = true;

    @Min(value = 1, message = "waitTimeoutSec must be between 1 and 120")
    @Max(value = 120, message = "waitTimeoutSec must be between 1 and 120")
    private Integer waitTimeoutSec = 30;

    /** ECHO 命令回显的载荷，NOOP 命令可忽略 */
    private Map<String, Object> echoPayload;

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (echoPayload != null && !echoPayload.isEmpty()) {
            payload.putAll(echoPayload);
        }
        return payload;
    }

    public String getGatewayDeviceId() {
        return gatewayDeviceId;
    }

    public void setGatewayDeviceId(String gatewayDeviceId) {
        this.gatewayDeviceId = gatewayDeviceId;
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

    public Map<String, Object> getEchoPayload() {
        return echoPayload;
    }

    public void setEchoPayload(Map<String, Object> echoPayload) {
        this.echoPayload = echoPayload;
    }
}
