# 客户端本地配置瘦身与平台配置库落地说明

## 目标

本地 `config/application.yml` 只保留客户端启动所必需的引导配置：

- HTTP 端口
- 日志基础配置
- 注册中心地址
- 服务名、设备类型、本机网段偏好

以下配置不再放在客户端本地 yml 中，统一放入平台注册配置库 `registry_client_config.config_content`：

- `spring.datasource.*`
- `monitorPlatformUrl`
- `vauth.*`
- `security.*`
- `process-bind.*`
- `traffic.*`
- `windivert.*`
- `process-policy.*`
- `transparent-proxy.*`
- `secure-publish.*`

## 启动配置边界

客户端启动顺序：

1. 读取本地 `config/application.yml`。
2. 使用 `registry.client.server-addr` 注册客户端。
3. 按 `clientId` 调用：

```text
GET http://<registry-server>/registry/client-config/{clientId}
```

4. 将返回的 `config` 扁平化注入 Spring 环境。
5. Spring Bean 初始化时绑定远程配置。

因此本地 yml 不能删除 `registry.client.*`，否则客户端不知道去哪里拉取平台配置。

## 已调整的本地文件

- `D:/project02/info-publish-client/config/application.yml`
- `D:/project02/info-publish-client/config/application-dev.yml`
- `D:/project02/info-publish-client/dist/config/application.yml`
- `D:/project02/info-publish-client/client/config/application.yml`

这些文件已移除数据库、UKey、WinDivert、Relay、签名验签等业务配置。

## 平台配置库写入

使用模板：

```text
D:/project02/info-publish-client/docs/registry-client-config-example.sql
```

执行前必须替换：

- `__CLIENT_ID__`
- `__DB_HOST__`
- `__DB_PORT__`
- `__DB_USERNAME__`
- `__DB_PASSWORD__`
- `__VAUTH_AUTH_ID__`
- `__VAUTH_PASSWORD__`
- `__VAUTH_SERVER_ID__`
- `__VAUTH_SERVER_CERT_PATH__`
- `__VAUTH_CLIENT_CERT_PATH__`

## 配置保存行为

旧的 `PUT /api/config/vauth` 会把 UKey 配置重新写回本机 `config/application.yml`。

现在已改为：

- 先更新当前客户端内存配置。
- 再读取平台配置库原有 `config_content`。
- 合并新的 UKey 配置。
- 写回 `registry_client_config`。

这样不会覆盖 WinDivert、进程绑定、签名验签等已有远程配置，也不会把敏感配置重新落到本机 yml。

## 验证

1. 查询平台配置：

```powershell
Invoke-RestMethod "http://192.168.1.233:8069/registry/client-config/<clientId>" |
  ConvertTo-Json -Depth 10
```

2. 启动客户端后查看日志：

```text
[EarlyRemoteConfig] config applied before context refresh
[RemoteConfig] config applied
```

3. 确认本地配置中没有敏感项：

```powershell
Select-String -Path "D:/project02/info-publish-client/client/config/application.yml" `
  -Pattern "password|auth-id|server-id|datasource|signature-secret|signer-private-key"
```

没有输出即符合预期。

4. 确认业务配置生效：

```powershell
Invoke-RestMethod "http://localhost:7081/security/windivert/proxy/status" |
  ConvertTo-Json -Depth 6

Invoke-RestMethod "http://localhost:7081/security/secure-publish/status" |
  ConvertTo-Json -Depth 6
```
