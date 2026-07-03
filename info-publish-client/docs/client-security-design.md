# 客户端安全方案说明

## 1. 文档范围

本文档描述 `info-publish-client` 当前已经实现的客户端安全方案，覆盖以下内容：

- Windows watcher 对 UKey 插拔的外部监控和客户端启停控制。
- 客户端 Java 服务内的 UKey 认证、状态管理、规则恢复和清理。
- 客户端对 Sigma Play 进程的 PID 绑定、路径校验和来源校验。
- 发布网关收到 UDP 数据后的客户端回调校验流程。
- 当前方案的部署要求、验证方式和已知边界。

本文档只描述当前代码逻辑，不描述未实现的能力。

## 2. 总体目标

当前安全方案的目标是：

1. 未插入 UKey 时，客户端服务不应被 watcher 启动，已由 watcher 启动的客户端应被关闭。
2. 插入 UKey 后，watcher 自动启动客户端服务。
3. 客户端启动后，必须完成 UKey 检测、证书校验和与管控平台的双向认证。
4. UKey 认证成功后，恢复本地链路规则和 ARP 绑定。
5. UKey 认证成功后，将当前机器上的 Sigma Play 进程绑定为授权业务进程。
6. 发布网关收到 UDP 数据包后，必须同步回调客户端校验来源 IP:Port。
7. 客户端确认该来源属于已授权 Sigma Play 进程或本机白名单时，发布网关才继续转发。
8. UKey 拔出后，客户端禁用本地链路规则、解除 ARP 绑定、清除进程绑定和认证状态。

## 3. 组件职责

| 组件 | 位置 | 职责 |
|---|---|---|
| watcher | `dist/watcher/*.ps1` | 监听 UKey 插拔，按 UKey 状态启动或停止客户端 |
| info-publish-client | `info-publish-client.jar` | 处理 UKey 认证、进程绑定、来源校验、告警状态 |
| VAuthSDK | `VAuthSDK.dll` | 枚举 UKey、监听 UKey 事件、执行认证相关密码设备能力 |
| Sigma Play | 外部大屏控制软件 | 被授权发送 UDP 数据的业务进程 |
| publish-gateway | `gateway-udp-proxy` | 接收 UDP 包，调用客户端校验，校验通过后转发 |
| 管控平台 | 外部平台 | 证书校验、双向认证、客户端状态通知 |

## 4. watcher 启停机制

### 4.1 安装方式

watcher 不是 Windows Service，而是通过 Windows 计划任务安装。

安装脚本：

- `dist/watcher/install-watcher.ps1`
- `dist/watcher/install-watcher.bat`

安装后会创建以下计划任务：

| 计划任务 | 触发方式 | 作用 |
|---|---|---|
| `InfoPublishClientUkeyWatcher` | Windows 开机启动，任意用户登录时兜底触发 | 以 SYSTEM 身份长期监听 UKey 设备变化 |
| `InfoPublishClientUkeyLauncher` | 默认禁用，由 watcher 手动触发 | 以 SYSTEM 身份启动客户端 |
| `InfoPublishClientSigmaPicker` | 页面请求时触发 | 以当前桌面用户身份打开 Sigma 文件选择器 |
| `InfoPublishClientPageLauncher` | 客户端请求或用户登录时触发 | 以当前桌面用户身份打开客户端页面 |

因此，当前方案支持“Windows 开机后自动启动 watcher”。如果机器未登录用户，交互式 Sigma 文件选择器不会显示，但 watcher 和客户端服务可以在后台运行。

### 4.2 UKey 插入时

watcher 的主流程在 `watcher.ps1` 中。

流程如下：

1. watcher 启动后先执行一次 `startup` 检查。
2. 通过 `VAuthSDK.dll` 枚举当前 UKey。
3. 如果检测到至少一个 UKey：
   - 检查客户端是否已经运行。
   - 检查是否有启动中的 launcher。
   - 如果客户端未运行，则触发 `InfoPublishClientUkeyLauncher`。
4. launcher 执行 `start-client.ps1`。
5. `start-client.ps1` 使用 `dist/jre/bin/javaw.exe` 启动：
   - `dist/info-publish-client.jar`
   - 外部配置文件 `dist/config/application.yml`
6. 启动后通过 `GET /security/process/status` 检查客户端健康状态。

### 4.3 UKey 拔出时

watcher 周期性或通过设备变化事件重新枚举 UKey。

如果当前 UKey 数量为 0：

1. watcher 判断当前客户端是否由 watcher 启动。
2. 如果是 watcher 启动的客户端，则停止该客户端进程。
3. 如果不是 watcher 启动的客户端，则不会接管或关闭。

### 4.4 周期兜底

watcher 使用 Windows 设备变化事件监听，同时设置超时时间。

如果没有收到设备事件，会按 `watcher.config.json` 中的 `reconcileIntervalSeconds` 做周期检查，当前值为 30 秒。

## 5. 客户端启动配置

watcher 启动客户端时使用外部配置文件：

```text
D:\project02\info-publish-client\dist\config\application.yml
```

启动方式等价于：

```powershell
dist\jre\bin\javaw.exe -jar dist\info-publish-client.jar --spring.config.location=file:///D:/project02/info-publish-client/dist/config/application.yml
```

关键配置项如下：

| 配置项 | 当前用途 |
|---|---|
| `server.port` | 客户端 HTTP 服务端口，发布网关必须访问此端口 |
| `vauth.mock-mode` | 是否使用 Mock UKey，生产应为 `false` |
| `vauth.auth-id` | 客户端认证 ID |
| `vauth.password` | UKey 口令 |
| `vauth.monitor-platform-url` | 管控平台认证地址 |
| `vauth.server-id` | 服务端认证 ID |
| `vauth.server-cert-path` | 管控平台证书路径 |
| `vauth.client-cert-path` | 客户端证书路径 |
| `process-bind.enabled` | 是否启用进程绑定和来源校验 |
| `process-bind.target-process-name` | 目标业务进程名，例如 `Sigma Play.exe` |
| `process-bind.target-process-path` | 目标业务进程可执行路径前缀，用于防同名进程伪造 |

部署要求：

1. `server.port` 必须与发布网关 `security.client-port` 一致。
2. 当前环境中客户端端口应使用 `7081`。
3. 发布网关源码配置中 `security.client-port` 默认值存在 `7080` 的情况，实际部署必须通过外部配置或环境变量修正为 `7081`。

## 6. UKey 认证流程

UKey 认证主入口：

```text
com.infopublish.client.service.UkeyAuthenticationHandler
```

### 6.1 启动扫描

客户端启动后，`UkeyAuthenticationHandler` 会注册 UKey 事件监听，并启动一个后台线程做启动扫描。

启动扫描用于处理这种情况：

- 客户端启动前 UKey 已经插入。
- 客户端没有收到插入事件。
- 启动扫描仍能发现 UKey 并触发认证流程。

### 6.2 插入认证

UKey 插入后的处理流程：

1. 调用 `VAuthSDKAdapter.listUkeyInfos()` 枚举 UKey。
2. 解析 UKey 路径和证书编号。
3. 状态机进入 `UKEY_DETECTED`。
4. 读取客户端证书。
5. 调用管控平台校验证书。
6. 证书校验通过后，状态机进入 `UKEY_VALIDATED`。
7. 调用 `ClientAuthService.authenticateWithControlPlatform()` 做双向认证。
8. 双向认证成功后，状态机进入 `AUTHENTICATED`。
9. 通知管控平台客户端认证成功。
10. 恢复本地链路规则和 ARP 绑定。
11. 调用 `ProcessBindService.bindCurrentPid()` 绑定 Sigma Play 进程。

### 6.3 双向认证

双向认证由 `ClientAuthService` 实现。

非 Mock 模式下流程为：

1. 打开 UKey：`VAuth_OpenUkey(path, password, authId)`。
2. 读取管控平台服务端证书。
3. 设置服务端认证信息。
4. 生成认证请求。
5. 调用管控平台 `/auth/server/request`。
6. 根据平台返回生成认证信息。
7. 调用管控平台 `/auth/server/verify`。
8. 调用 SDK 检查认证结果。
9. 成功后设置本地 `authenticated=true`。
10. 关闭 UKey handle。

## 7. UKey 拔出处理

UKey 拔出后，`UkeyAuthenticationHandler.onUkeyRemoved()` 会执行清理：

1. 如果当前通道活跃，停止转发通道。
2. 禁用本地所有链路规则。
3. 解除 ARP 绑定。
4. 清除进程绑定。
5. 通知管控平台客户端断开。
6. 清除本地认证状态。
7. 状态机重置为 `IDLE`。

这部分逻辑用于保证“UKey 不在时，不允许继续使用已经建立的规则或绑定状态”。

## 8. 进程绑定安全策略

进程绑定实现类：

```text
com.infopublish.client.service.impl.ProcessBindServiceImpl
```

### 8.1 绑定时机

当前有三个入口会涉及进程绑定：

| 入口 | 行为 |
|---|---|
| UKey 认证成功后 | 调用 `bindCurrentPid()` |
| `POST /security/process/refresh` | 手动刷新绑定 |
| 30 秒定时任务 | 已有 PID 时刷新白名单；PID 死亡时重新绑定 |

### 8.2 PID 查找

`resolveTargetPid()` 使用两步查找：

1. 执行 `tasklist /FO CSV /NH`，按 `target-process-name` 查找候选进程。
2. 如果配置了 `target-process-path`，继续用 `wmic process where processid=... get ExecutablePath /format:csv` 获取可执行文件路径。
3. 只有路径以 `target-process-path` 为前缀时，才认为该 PID 合法。

安全含义：

- 只配置进程名时，存在同名进程伪造风险。
- 配置路径前缀后，可以降低同名伪造风险。
- 如果 wmic 无法读取进程路径，当前策略是拒绝该候选 PID。

### 8.3 绑定状态

绑定状态保存在内存中：

| 字段 | 含义 |
|---|---|
| `authorizedPid` | 当前授权 Sigma Play 进程 PID，`-1` 表示未绑定 |
| `authorizedEndpoints` | 当前本机 IPv4 白名单集合 |

`authorizedPid` 和 `authorizedEndpoints` 使用 `volatile` 保证多线程可见性。

### 8.4 白名单刷新

`resolveAuthorizedEndpoints()` 会执行 `ipconfig` 获取本机 IPv4 地址集合。

原因是 Sigma Play 发送 UDP 时使用临时端口，端口可能在网关回调客户端前已经关闭，单纯依赖端口表可能漏判。

因此当前策略是：

1. 优先使用 Windows `GetExtendedUdpTable` 校验 UDP 端口归属 PID。
2. 如果端口已经不在 UDP 表中，但来源 IP 属于本机 IPv4 白名单，则放行。
3. 如果端口归属其他 PID，则拒绝。
4. 如果 PID 未绑定或 PID 已死亡，则拒绝。

### 8.5 定时刷新边界

当前 `autoRefreshIfProcessDied()` 每 30 秒执行一次。

现有逻辑是：

- `authorizedPid > 0` 时：
  - 如果 PID 还活着，刷新本机 IP 白名单。
  - 如果 PID 已死亡，重新查找 Sigma Play 并绑定。
- `authorizedPid <= 0` 时：
  - 当前代码直接返回，不会主动重新查找。

这意味着：

1. 如果 UKey 认证时 Sigma Play 已经运行，后续 Sigma Play 重启后，客户端有机会在 30 秒内重新绑定。
2. 如果 UKey 认证时 Sigma Play 没运行，`authorizedPid=-1`，当前代码不会自动绑定后续才启动的 Sigma Play。
3. 这种情况下需要调用 `POST /security/process/refresh`，或者修改定时任务逻辑让 `authorizedPid<=0` 时也尝试绑定。

## 9. 来源校验流程

### 9.1 网关侧调用

发布网关收到 UDP 包后的处理位置：

```text
com.publishgateway.udpproxy.forward.UdpProxyServer
```

处理顺序：

1. 收到 UDP 包。
2. 根据规则做来源 IP 粗粒度校验。
3. 调用 `ClientValidationService.validateSource()` 做来源 IP:Port 精细校验。
4. 校验通过后，才继续：
   - 解析有效规则。
   - 上报数据。
   - 转码。
   - 封装 Message。
   - 加密。
   - 转发到目标。

如果来源校验失败，网关直接 `return`，不会继续转发。

### 9.2 客户端定位方式

发布网关使用 UDP 包的发送者 IP 作为客户端 IP。

请求地址格式：

```text
http://{senderIp}:{security.client-port}/security/validate-source
```

请求体：

```json
{
  "ip": "192.168.113.125",
  "port": 57741
}
```

注意：

- `senderIp` 必须能访问客户端 Spring Boot 端口。
- 客户端必须监听在该网卡地址上。
- 防火墙不能阻断该端口。

### 9.3 客户端校验接口

接口：

```text
POST /security/validate-source
```

实现类：

```text
com.infopublish.client.controller.SecurityAlertController
```

处理流程：

1. 校验请求体是否包含 `ip` 和 `port`。
2. 调用 `processBindService.resolveAuthorizedEndpoints()` 刷新本机 IP 白名单。
3. 调用 `trafficMonitorService.checkAndRecordSource(ip, port)`。
4. `TrafficMonitorServiceImpl` 继续调用 `ProcessBindServiceImpl.isEndpointAuthorized(ip, port)`。
5. 返回 `authorized=true/false`。

### 9.4 客户端放行条件

`ProcessBindServiceImpl.isEndpointAuthorized()` 的放行条件如下：

| 条件 | 结果 |
|---|---|
| `process-bind.enabled=false` | 直接放行 |
| `authorizedPid <= 0` | 拒绝 |
| 授权 PID 已不存在 | 拒绝 |
| `GetExtendedUdpTable` 不可用 | 拒绝 |
| UDP 表中找到该端口，且 owningPid 等于授权 PID | 放行 |
| UDP 表中找到该端口，但 owningPid 不是授权 PID | 拒绝 |
| UDP 表中找不到该端口，但来源 IP 在本机白名单中 | 放行 |
| UDP 表中找不到该端口，且来源 IP 不在本机白名单 | 拒绝 |

### 9.5 告警

如果来源校验失败：

1. `TrafficMonitorServiceImpl` 生成最新一条告警。
2. 告警内容包括来源 IP 和端口。
3. 前端可通过 `GET /security/alert/query` 查询。
4. 用户确认后可调用 `POST /security/alert/dismiss` 清除。

## 10. 网关侧安全策略

发布网关的校验服务：

```text
com.publishgateway.udpproxy.service.ClientValidationService
```

关键配置：

| 配置项 | 默认/用途 |
|---|---|
| `security.client-port` | 客户端 HTTP 端口，必须与客户端 `server.port` 一致 |
| `security.client-validate-timeout-ms` | 调用客户端校验接口的连接和读取超时时间 |
| `security.source-check-enabled` | 是否启用来源校验 |

当前 `ClientValidationService` 代码策略为严格模式：

| 情况 | 网关行为 |
|---|---|
| `source-check-enabled=false` | 跳过校验，直接放行 |
| 规则 `sourceIp` 为空 | 拒绝 |
| 客户端不可达 | 拒绝 |
| 客户端 HTTP 非 200 | 拒绝 |
| 客户端返回 `authorized=false` | 拒绝 |
| 客户端返回 `authorized=true` | 放行 |

注意：`UdpProxyServer` 中的注释仍写着“不可达时降级放行”，但当前 `ClientValidationService` 实现是不可达即拒绝。实际行为以 `ClientValidationService` 为准。

## 11. 对外接口

### 11.1 状态接口

```text
GET /security/process/status
```

返回字段：

| 字段 | 含义 |
|---|---|
| `authorizedPid` | 当前绑定的 Sigma Play PID |
| `bound` | 是否已绑定 PID |
| `endpointCount` | 当前白名单数量 |

### 11.2 白名单查询

```text
GET /security/process/endpoints
```

用于调试当前授权来源白名单。

### 11.3 手动刷新进程绑定

```text
POST /security/process/refresh
```

适用场景：

- Sigma Play 重启后需要立即重新绑定。
- UKey 认证时 Sigma Play 未启动，后续手动启动 Sigma Play 后需要绑定。

### 11.4 来源校验

```text
POST /security/validate-source
```

由发布网关同步调用，不建议普通前端直接调用。

### 11.5 告警查询

```text
GET /security/alert/query
POST /security/alert/dismiss
```

用于展示和清除来源异常告警。

## 12. 部署要求

### 12.1 Windows 客户端

必须包含：

1. `dist/info-publish-client.jar`
2. `dist/jre/bin/javaw.exe`
3. `dist/config/application.yml`
4. `dist/VAuthSDK.dll`
5. VAuthSDK 依赖 DLL
6. 证书文件
7. `dist/watcher` 脚本目录

安装 watcher 时需要管理员权限。

### 12.2 客户端端口

必须保证客户端端口可访问：

```powershell
Test-NetConnection 192.168.113.125 -Port 7081
```

如果 `TcpTestSucceeded=False`，发布网关会无法调用客户端校验接口，当前策略下会拒绝 UDP 数据。

### 12.3 发布网关端口配置

发布网关必须设置：

```yaml
security:
  client-port: 7081
```

或通过环境变量：

```text
CLIENT_PORT=7081
```

### 12.4 Sigma Play 路径配置

建议配置 `process-bind.target-process-path`。

只配置进程名时，安全性不足，因为同名进程可能被误绑定。

### 12.5 管理员权限

以下能力依赖管理员权限或较高权限：

1. watcher 安装计划任务。
2. 高权限启动客户端。
3. ARP 绑定/解绑。
4. 读取目标进程可执行路径。
5. 部分网络状态查询。

## 13. 验证步骤

### 13.1 验证 watcher 状态

```powershell
Get-ScheduledTask -TaskName InfoPublishClientUkeyWatcher
Get-ScheduledTask -TaskName InfoPublishClientUkeyLauncher
```

期望：

- `InfoPublishClientUkeyWatcher` 为 `Running`。
- `InfoPublishClientUkeyLauncher` 可以被 watcher 触发。

### 13.2 验证客户端端口

```powershell
Get-NetTCPConnection -State Listen -LocalPort 7081
Test-NetConnection 127.0.0.1 -Port 7081
Test-NetConnection 192.168.113.125 -Port 7081
```

期望：

- 本机 7081 处于监听。
- 127.0.0.1 和业务网卡 IP 均可访问。

### 13.3 验证客户端状态接口

```powershell
Invoke-RestMethod http://127.0.0.1:7081/security/process/status
```

期望：

- `bound=true`
- `authorizedPid > 0`
- `endpointCount > 0`

### 13.4 验证手动刷新绑定

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:7081/security/process/refresh
```

适用于 Sigma Play 重启后立即刷新。

### 13.5 验证发布网关到客户端连通性

在发布网关所在机器执行：

```powershell
Test-NetConnection 192.168.113.125 -Port 7081
```

或在 Linux 上：

```bash
curl -s http://192.168.113.125:7081/security/process/status
```

### 13.6 验证来源校验

模拟请求：

```powershell
$body = @{ ip = "192.168.113.125"; port = 57741 } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri http://127.0.0.1:7081/security/validate-source `
  -ContentType "application/json" `
  -Body $body
```

注意：端口必须是实际 Sigma Play 发包时使用的 UDP 源端口，否则可能返回 `authorized=false`。

## 14. 当前已知边界和风险

| 风险点 | 当前表现 | 建议 |
|---|---|---|
| watcher 以 SYSTEM 后台运行 | 未登录用户时交互式 Sigma 文件选择器不会显示 | 需要人工选择 Sigma 时先登录桌面用户 |
| `authorizedPid<=0` 时定时任务不主动查找 | UKey 认证时 Sigma 未运行，后续启动 Sigma 可能不会自动绑定 | 修改定时任务，让未绑定时也周期查找 |
| 发布网关源码默认端口可能为 7080 | 如果外部配置未覆盖，会访问错端口 | 统一配置为 7081 |
| `target-process-path` 为空时只按进程名绑定 | 存在同名进程误绑定风险 | 生产必须配置路径前缀 |
| 客户端不可达时网关拒绝 | 客户端未启动、防火墙阻断、端口不一致都会导致业务不通 | 部署时验证端口连通 |
| `validate-source` 每包同步 HTTP 调用 | 高吞吐下会增加延迟 | 后续可考虑短时缓存，但不能破坏安全语义 |
| 源码中部分注释编码显示异常 | 不影响编译，但影响维护 | 建议统一以 UTF-8 保存源码和配置 |
| `UdpProxyServer` 注释与实际策略不一致 | 注释写降级放行，实际为拒绝 | 应修正注释，避免误判 |

## 15. 推荐改进项

按优先级排序：

1. 修复 `ProcessBindServiceImpl.autoRefreshIfProcessDied()`：当 `authorizedPid<=0` 时也尝试查找 Sigma Play。
2. 修正发布网关 `application.yml` 的 `security.client-port` 默认值为 `7081`。
3. 修正 `UdpProxyServer` 中“不可达时降级放行”的过期注释。
4. 统一源码和配置文件编码为 UTF-8。
5. 为 `POST /security/validate-source` 增加轻量认证或来源限制，避免被非网关随意调用。
6. 如果后续需要更强的服务治理能力，可将 watcher 进一步改造为 Windows Service。

## 16. 核心结论

当前方案是“UKey 物理存在 + 管控平台认证 + Sigma Play 进程绑定 + 发布网关同步回调校验”的组合安全方案。

它不是单纯依赖端口、IP 或配置文件放行，而是要求：

1. UKey 存在。
2. 客户端已完成认证。
3. Sigma Play 进程被绑定。
4. 发布网关能回调客户端。
5. 客户端确认来源属于授权进程或本机白名单。

任一关键条件不满足时，当前严格策略会拒绝发布网关继续转发。
