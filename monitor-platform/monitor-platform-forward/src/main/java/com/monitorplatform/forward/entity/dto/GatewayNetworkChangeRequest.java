package com.monitorplatform.forward.entity.dto;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 目标设备系统 IP 变更请求。
 */
public class GatewayNetworkChangeRequest {

    private String targetDeviceId;

    private String gatewayDeviceId;

    @NotBlank(message = "interfaceName不能为空")
    private String interfaceName;

    @NotBlank(message = "newIp不能为空")
    private String newIp;

    @NotNull(message = "prefixLength不能为空")
    @Min(value = 1, message = "prefixLength必须在1-32之间")
    @Max(value = 32, message = "prefixLength必须在1-32之间")
    private Integer prefixLength;

    private String gateway;

    private Boolean applyDefaultRoute = false;

    private Boolean dryRun = false;

    private Boolean persist = false;

    @Min(value = 0, message = "rollbackSeconds不能小于0")
    private Integer rollbackSeconds = 300;

    private String changeId;

    private String operator;

    private String remark;

    @AssertTrue(message = "targetDeviceId不能为空")
    public boolean isTargetDeviceIdPresent() {
        return hasText(targetDeviceId) || hasText(gatewayDeviceId);
    }

    public String resolveTargetDeviceId() {
        return hasText(targetDeviceId) ? targetDeviceId.trim() : gatewayDeviceId.trim();
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("interfaceName", interfaceName);
        payload.put("newIp", newIp);
        payload.put("prefixLength", prefixLength);
        payload.put("gateway", gateway);
        payload.put("applyDefaultRoute", Boolean.TRUE.equals(applyDefaultRoute));
        payload.put("dryRun", Boolean.TRUE.equals(dryRun));
        payload.put("persist", Boolean.TRUE.equals(persist));
        payload.put("rollbackSeconds", rollbackSeconds == null ? 300 : rollbackSeconds);
        payload.put("changeId", changeId);
        payload.put("operator", operator);
        payload.put("remark", remark);
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

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getNewIp() {
        return newIp;
    }

    public void setNewIp(String newIp) {
        this.newIp = newIp;
    }

    public Integer getPrefixLength() {
        return prefixLength;
    }

    public void setPrefixLength(Integer prefixLength) {
        this.prefixLength = prefixLength;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public Boolean getApplyDefaultRoute() {
        return applyDefaultRoute;
    }

    public void setApplyDefaultRoute(Boolean applyDefaultRoute) {
        this.applyDefaultRoute = applyDefaultRoute;
    }

    public Boolean getDryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Boolean getPersist() {
        return persist;
    }

    public void setPersist(Boolean persist) {
        this.persist = persist;
    }

    public Integer getRollbackSeconds() {
        return rollbackSeconds;
    }

    public void setRollbackSeconds(Integer rollbackSeconds) {
        this.rollbackSeconds = rollbackSeconds;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
