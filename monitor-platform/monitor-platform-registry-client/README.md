# 设备注册客户端

## 功能说明

简单的服务注册功能，将服务当作设备注册到平台。

## 配置示例

```yaml
registry:
  client:
    enabled: true
    server-addr: monitor-platform-registry-server:8066
    service-name: my-device-service
    device-type: gateway
```

## 配置说明

| 属性 | 说明 | 默认值 |
|------|------|--------|
| registry.client.enabled | 是否启用 | true |
| registry.client.server-addr | 注册中心地址 | localhost:8066 |
| registry.client.service-name | 服务名称 | 必填 |
| registry.client.host | IP地址 | **自动获取** |
| registry.client.port | 运行端口 | **自动获取**(从server.port或local.server.port) |
| registry.client.mac-address | MAC地址 | **自动获取** |
| registry.client.device-type | 设备类型 | 可选 |
| registry.client.heartbeat-interval | 心跳间隔(ms) | 10000 |
| registry.client.auto-register | 自动注册 | true |
| registry.client.auto-deregister | 自动注销 | true |

## 自动获取的信息

客户端会自动获取以下设备信息：

- **IP地址**：自动获取本机IP地址
- **端口**：从 `server.port` 或 `local.server.port` 环境变量获取，默认8080
- **MAC地址**：自动获取网卡MAC地址

只需配置 `service-name` 和 `device-type` 即可完成注册！