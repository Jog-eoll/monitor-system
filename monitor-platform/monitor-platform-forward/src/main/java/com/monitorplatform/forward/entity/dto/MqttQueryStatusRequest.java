package com.monitorplatform.forward.entity.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

public class MqttQueryStatusRequest {

    @NotBlank(message = "gatewayDeviceId cannot be blank")
    private String gatewayDeviceId;

    private String businessId;

    private Boolean waitForReply = true;

    @Min(value = 1, message = "waitTimeoutSec must be between 1 and 120")
    @Max(value = 120, message = "waitTimeoutSec must be between 1 and 120")
    private Integer waitTimeoutSec = 30;

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("probe", "status");
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
}
