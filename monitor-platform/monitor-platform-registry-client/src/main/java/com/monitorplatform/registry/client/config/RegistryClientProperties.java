package com.monitorplatform.registry.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "registry.client")
public class RegistryClientProperties {
    private boolean enabled = true;
    private String serverAddr = "localhost:8069";
    private String apiPrefix = "/device/registry";
    private String registerPath = "/auto-register";
    private String clientId;
    private String serviceName;
    private String host;
    private Integer port;
    private String macAddress;
    private String deviceType;
    // ===== 新增字段（yml 配置优先） =====
    private String location;
    private String version;
    private String manufacturer;
    private String model;
    private String remark;
    // ===== 网络探测控制 =====
    /**
     * 优先使用的网段前缀（如 "192.168.1."）。
     * 当同一张网卡绑了多个 IP 时，优先选择此前缀匹配的 IP。
     * 不配置时自动通过默认路由判断。
     */
    private String preferredNetwork;
    
    // ===== 行为控制 =====
    private int heartbeatInterval = 10000;
    private int registerRetryTimes = 3;
    private int registerRetryInterval = 5000;
    private boolean autoRegister = true;
    private boolean autoDeregister = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getApiPrefix() {
        return apiPrefix;
    }

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    public String getRegisterPath() {
        return registerPath;
    }

    public void setRegisterPath(String registerPath) {
        this.registerPath = registerPath;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getPreferredNetwork() {
        return preferredNetwork;
    }

    public void setPreferredNetwork(String preferredNetwork) {
        this.preferredNetwork = preferredNetwork;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public int getRegisterRetryTimes() {
        return registerRetryTimes;
    }

    public void setRegisterRetryTimes(int registerRetryTimes) {
        this.registerRetryTimes = registerRetryTimes;
    }

    public int getRegisterRetryInterval() {
        return registerRetryInterval;
    }

    public void setRegisterRetryInterval(int registerRetryInterval) {
        this.registerRetryInterval = registerRetryInterval;
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(boolean autoRegister) {
        this.autoRegister = autoRegister;
    }

    public boolean isAutoDeregister() {
        return autoDeregister;
    }

    public void setAutoDeregister(boolean autoDeregister) {
        this.autoDeregister = autoDeregister;
    }
}
