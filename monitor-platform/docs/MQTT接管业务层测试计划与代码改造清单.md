# MQTT 接管业务层测试计划与代码改造清单

版本日期：2026-07-07

适用环境：

- 平台：`192.168.1.70`
- 发布网关：`192.168.77.11`，`publish-gateway-11`
- 终端网关：`192.168.77.12`，`terminal-gateway-12`

## 1. 当前结论

MQTT 基础链路已经具备：

- 网关主动连接 EMQX。
- 网关主动注册。
- 网关周期心跳。
- 平台侧 EMQX / `monitor-forward` / 设备表均能确认在线。
- 平台到网关的 `down/proxy-command` 可达。
- 网关能通过 `up/reply` 回传 `PROCESSING` / `FAILED` 等状态。

当前还没有完成的是“业务层真正走 MQTT”。也就是说，不能只验证直接往 EMQX 发一条诊断消息，而要验证平台原有业务 API 发起后，平台内部创建命令记录、经 MQTT 下发、网关执行业务服务、回执更新平台业务状态。

## 2. 接管目标定义

“MQTT 接手原有业务层”应定义为：

1. 前端和外部系统仍调用平台原有 HTTP API。
2. 平台业务服务不再直接访问网关 HTTP 地址，而是通过统一命令分发器发送 MQTT 命令。
3. 网关 MQTT Agent 接收命令后调用原有业务服务执行。
4. 网关通过 MQTT 回执上报 `PROCESSING`、`SUCCESS`、`FAILED`、`REJECTED`。
5. 平台通过 `commandMessageId` 更新命令表和业务表状态。
6. 对长耗时业务，平台能持续跟踪进度、超时、失败原因和重试状态。

不建议把“直接向 EMQX 发布诊断消息”当作业务验收。直接发布只能验证 Broker 到网关、网关到平台回执的链路，不能验证平台业务状态机。

## 3. 测试前置条件

| 前置项 | 当前状态 | 要求 |
| --- | --- | --- |
| 平台登录态 / Authorization | 缺失 | 需要平台账号或 Token，用于调用正式业务 API |
| 11 到 12 网络互通 | 未通，`No route to host` | 至少开通 `192.168.77.11 -> 192.168.77.12:8093` |
| 11 数据库初始化 | 日志提示 `udp_proxy_rule` 表缺失 | 需要导入发布网关初始化 SQL |
| 11/12 时间同步 | 11 曾出现 MinIO 时间差错误 | 需要 NTP 或手动同步时间 |
| UKey / 证书 | 11/12 均有 VAuth 认证异常 | 需要确认 `VAUTH_AUTH_ID`、`VAUTH_SERVER_ID`、证书文件 |
| MinIO 配置 | 11/12 仍有访问或匿名配置问题 | 需要正确 endpoint、accessKey、secretKey、bucket |
| 非破坏性成功命令 | 发布网关和终端网关均已补 `QUERY_STATUS` / `NOOP` / `ECHO` 原生命令；HTTP action 业务 `code` 非 200 时按 `FAILED` 回执；`CHANGE_SYSTEM_IP dryRun=true` 已允许在安全开关关闭时返回参数校验和计划结果 | 现场复测 11/12 返回 `SUCCESS`，并确认未执行真实网络变更 |

## 4. 推荐测试阶段

### 阶段 1：平台正式下发链路

目标：验证平台 API 发起后，命令表能被创建、发布、回执更新。

测试项：

| 序号 | 测试项 | 触发方式 | 预期 |
| ---: | --- | --- | --- |
| 1 | 平台创建 MQTT 命令记录 | 调用平台正式 API | `device_mqtt_command` 新增记录，状态 `PENDING` 或 `PUBLISHED` |
| 2 | 平台发布 MQTT 消息 | 平台服务内部发布 | EMQX topic 为 `/{tenant}/{site}/{device}/down/proxy-command` |
| 3 | 网关收到命令 | 网关日志 | 出现 messageId、command、payload |
| 4 | 网关回 `PROCESSING` | 网关执行前 | 平台收到并更新中间状态 |
| 5 | 网关回最终状态 | 执行完成 | 平台命令表更新为 `SUCCESS` / `FAILED` |
| 6 | 平台 API 返回业务结果 | API 响应或状态查询接口 | 可查到最终状态和失败原因 |

建议先使用非破坏性命令：

- `QUERY_STATUS`
- `NOOP`
- `CHANGE_SYSTEM_IP` 的 `dryRun=true`

当前建议优先使用 `QUERY_STATUS` 或 `NOOP` 验收 MQTT 命令状态机；`CHANGE_SYSTEM_IP dryRun=true` 可作为网络变更参数校验入口，安全开关关闭时仍不执行真实 native command。

### 阶段 2：业务命令契约测试

目标：把原 HTTP 业务动作逐项抽象成 MQTT command，并验证请求、响应、幂等。

建议命令清单：

| 命令 | 目标设备 | 业务含义 | 预期结果 |
| --- | --- | --- | --- |
| `QUERY_STATUS` | 11 / 12 | 查询网关运行状态 | 返回健康、版本、IP、端口、角色 |
| `CHANGE_SYSTEM_IP` | 11 / 12 | 修改 IP / dry-run | dry-run 不改变网络，返回计划结果 |
| `PROXY_RULE_APPLY` | 11 | 下发代理规则 | 本地规则表写入并启动代理 |
| `PROXY_RULE_REMOVE` | 11 | 移除代理规则 | 规则停用并回执 |
| `CHAIN_DEPLOY` | 11 | 平台链路部署 | 生成 / 更新 11 到 12 的链路配置 |
| `CONTROL_DELIVERY` | 11 或 12 | 亮度、黑屏、校时、查询状态等控制命令 | 网关处理并返回设备响应 |
| `SECURE_DELIVERY` | 11 | 加密内容投递 | 生成任务、上传/下载文件、回执任务状态 |
| `SNAPSHOT_REPORT` | 12 | 终端截图 / 状态采集 | 上传截图或返回采集结果 |
| `REMOTE_UPGRADE` | 11 / 12 | 远程升级 | 下载、校验、执行、回执进度 |

每个命令都要验证：

- `messageId` 唯一。
- `commandMessageId` 与平台下发 `messageId` 一致。
- 重复消息不会重复执行业务副作用。
- 参数非法时返回 `REJECTED` 或 `FAILED`，并带明确 `errorCode`。
- 执行超时时平台命令状态能转为 `TIMEOUT`。

### 阶段 3：端到端业务流测试

目标：验证真实业务从平台 API 到网关再到终端设备完整闭环。

#### 3.1 设备状态查询

步骤：

1. 平台调用“查询设备状态”API。
2. 平台下发 MQTT `QUERY_STATUS` 到 11 和 12。
3. 网关返回版本、角色、IP、端口、运行状态。
4. 平台更新设备状态。

通过标准：

- 11、12 均返回 `SUCCESS`。
- 平台页面或接口能看到最新状态。

#### 3.2 发布网关到终端网关链路

步骤：

1. 修复 `77.11 -> 77.12:8093` 网络互通。
2. 平台创建链路配置，目标设备为 `terminal-gateway-12`。
3. 平台通过 MQTT 向 `publish-gateway-11` 下发 `CHAIN_DEPLOY`。
4. 11 写入代理规则并尝试访问 12。
5. 11 回传执行结果。

通过标准：

- 11 规则表存在对应记录。
- 11 能访问 12 的 `8093`。
- 平台命令记录为 `SUCCESS`。

#### 3.3 控制命令投递

步骤：

1. 平台发起亮度、黑屏、校时或查询状态命令。
2. 平台通过 MQTT 下发到 11 或 12。
3. 网关调用原控制服务。
4. 网关回传设备响应。

通过标准：

- 命令被正确路由到目标网关。
- 原控制服务被调用。
- 平台能看到设备响应和最终状态。

#### 3.4 文件 / 内容投递

步骤：

1. 平台准备小文件或测试素材。
2. 平台创建内容投递任务。
3. 平台通过 MQTT 下发 `SECURE_DELIVERY` 或等价命令。
4. 网关下载文件、校验 hash、执行加密/转发。
5. 网关上报进度和最终状态。

通过标准：

- MinIO 下载 / 上传可用。
- 任务过程状态可见。
- 失败时能定位到下载、校验、加密、投递哪个环节。

#### 3.5 远程升级

步骤：

1. 上传小型升级包。
2. 创建单设备升级任务。
3. 平台通过 MQTT 下发 `REMOTE_UPGRADE`。
4. 网关下载、校验 SHA256、执行 dry-run 脚本或测试脚本。
5. 网关上报 `PROCESSING`、进度、最终状态。

通过标准：

- 不影响当前 SSH 和容器可恢复性。
- 失败可回滚。
- 平台记录完整。

### 阶段 4：异常与稳定性测试

| 场景 | 操作 | 预期 |
| --- | --- | --- |
| Broker 重启 | 重启 EMQX | 网关自动重连，心跳恢复 |
| 网关重启 | 重启 11 / 12 容器 | 注册和心跳恢复，clientId 不冲突 |
| 平台重启 | 重启 `monitor-forward` | 订阅恢复，后续心跳继续处理 |
| 重复命令 | 重复发布同一 `messageId` | 网关去重，不重复产生副作用 |
| 乱序回执 | 先收到 `SUCCESS` 后收到迟到 `PROCESSING` | 平台状态不回退 |
| 超时 | 网关不回执 | 平台命令变 `TIMEOUT` |
| 大 payload | 下发较大任务参数 | 网关拒绝或处理有明确限制 |
| 非法 payload | 缺字段、类型错误 | 返回 `REJECTED`，日志可定位 |
| 多设备并发 | 同时下发到 11、12 | 命令互不串扰 |

## 5. 需要修改的代码

### 5.1 平台侧

#### 5.1.1 增加统一业务命令分发器

现有平台业务服务不要直接拼网关 HTTP 地址。建议抽象：

```java
public interface DeviceCommandDispatcher {
    CommandSubmitResult submit(DeviceCommand command);
    CommandSubmitResult submitAndWait(DeviceCommand command, Duration timeout);
}
```

实现：

- `MqttDeviceCommandDispatcher`
- `HttpDeviceCommandDispatcher`，仅保留为兼容或回退
- `HybridDeviceCommandDispatcher`，按设备能力和配置选择

配置：

```properties
dispatch.mode=mqtt
dispatch.fallback-http=false
dispatch.default-timeout-ms=30000
```

#### 5.1.2 业务 API 改为创建命令记录后发布 MQTT

每个原业务 API 应遵循统一流程：

1. 校验业务参数。
2. 生成 `messageId`。
3. 写入 `device_mqtt_command`，状态为 `PENDING`。
4. 发布 MQTT。
5. 发布成功后状态改为 `PUBLISHED`。
6. 回执到达后由 `MqttReplyHandler` 更新为 `PROCESSING` / `SUCCESS` / `FAILED`。

需要重点改造的模块：

- 网络配置 / 改 IP API
- 链路部署 API
- 控制命令投递 API
- 内容投递 / 加密投递 API
- 截图 / 状态采集 API
- 远程升级 API

#### 5.1.3 命令状态查询接口

平台需要提供命令状态查询：

```http
GET /api/mqtt/commands/{messageId}
GET /api/mqtt/commands?targetDeviceId=...&status=...
```

返回字段至少包括：

- `messageId`
- `targetDeviceId`
- `command`
- `status`
- `publishedAt`
- `ackTime`
- `errorCode`
- `errorMessage`
- `replyPayload`

#### 5.1.4 回执状态机加固

平台 `MqttReplyHandler` 需要防止状态回退：

允许流转：

```text
PENDING -> PUBLISHED -> PROCESSING -> SUCCESS
PENDING -> PUBLISHED -> PROCESSING -> FAILED
PENDING -> PUBLISHED -> TIMEOUT
```

禁止：

- `SUCCESS` 被迟到的 `PROCESSING` 覆盖。
- `FAILED` 被迟到的 `PROCESSING` 覆盖。
- 无命令记录的回执直接当成功。

#### 5.1.5 设备能力模型

设备注册时应上报能力：

```json
{
  "supportsMqtt": true,
  "supportedCommands": [
    "QUERY_STATUS",
    "CHANGE_SYSTEM_IP",
    "CHAIN_DEPLOY",
    "CONTROL_DELIVERY",
    "REMOTE_UPGRADE"
  ],
  "schemaVersion": "1.0"
}
```

平台设备表或扩展表需要保存：

- `supports_mqtt`
- `mqtt_client_id`
- `supported_commands`
- `last_mqtt_online_time`
- `dispatch_mode`

### 5.2 网关侧

#### 5.2.1 避免仅靠 HTTP 回环

当前网关 `GatewayCommandDispatcher` 对 `QUERY_STATUS` / `NOOP` / `ECHO` 已走原生非破坏性处理；其它业务 action 仍可能通过 HTTP 回环调用本机 Controller。短期可用，但业务接管时建议把 Controller 背后的业务服务抽成可直接调用的 Handler：

```java
public interface GatewayCommandHandler {
    String command();
    MqttReplyMessage handle(MqttCommandContext context);
}
```

每类业务一个 Handler：

- `QueryStatusCommandHandler`
- `NetworkChangeCommandHandler`
- `ProxyRuleApplyCommandHandler`
- `ChainDeployCommandHandler`
- `ControlDeliveryCommandHandler`
- `SecureDeliveryCommandHandler`
- `SnapshotCommandHandler`
- `RemoteUpgradeCommandHandler`

这样 HTTP API 和 MQTT 命令可以复用同一业务服务，避免 Controller 到 Controller 的绕路。

#### 5.2.2 增加非破坏性成功命令

`CHANGE_SYSTEM_IP dryRun` 已调整为先完成参数和计划校验，安全开关关闭时也不执行真实 native command；仍建议保留以下非破坏性命令作为最小验收入口：

- `QUERY_STATUS`
- `NOOP`
- `ECHO`

用于平台正式 API 验证命令表 `SUCCESS`，不依赖危险开关。

#### 5.2.3 统一回执结构

网关回执必须包含：

```json
{
  "commandMessageId": "平台下发 messageId",
  "status": "SUCCESS",
  "message": "执行成功",
  "gatewayDeviceId": "terminal-gateway-12",
  "timestamp": 1783000000000,
  "errorCode": null,
  "data": {}
}
```

失败时：

```json
{
  "status": "FAILED",
  "errorCode": "NETWORK_CHANGE_DISABLED",
  "message": "system network change is disabled"
}
```

网关侧执行 action 时，HTTP 状态为 2xx 但响应体业务 `code != 200` 的结果必须按 `FAILED` 回执处理，避免平台命令表出现业务“假成功”。

#### 5.2.4 幂等记录持久化

现在命令去重如果只在内存里，容器重启后会失效。业务接管后建议落库：

- `message_id`
- `command`
- `payload_hash`
- `status`
- `first_seen_time`
- `last_update_time`
- `reply_payload`

重复 `messageId` 到达时，直接返回已有最终结果，不再次执行业务。

#### 5.2.5 长任务进度上报

远程升级、文件投递、加密转码等任务不应只等最终回执。建议新增进度消息：

```text
/{tenantId}/{siteId}/{deviceId}/up/progress
```

或在 `up/reply` 中多次上报：

- `PROCESSING` 10%
- `PROCESSING` 50%
- `SUCCESS` 100%

平台需能保存过程事件；当前 `device_mqtt_command_event` 已接入 `CREATED`、`PUBLISHED`、`PROCESSING`、`SUCCESS`、`FAILED`、`TIMEOUT` 等状态变化。

### 5.3 公共协议包

平台和网关应共用同一套协议定义：

- `MqttEnvelope`
- `MqttCommandMessage`
- `MqttReplyMessage`
- `MqttMessageTypes`
- `GatewayCommandType`
- `GatewayReplyStatus`
- `GatewayErrorCode`

需要补充：

- JSON Schema 或契约测试用例。
- `schemaVersion` 字段。
- 新字段向后兼容策略。

### 5.4 数据库

平台侧建议补充或确认以下表字段：

`device_mqtt_command`：

- `message_id`
- `tenant_id`
- `site_id`
- `gateway_device_id`
- `target_device_id`
- `command`
- `message_type`
- `topic`
- `payload_json`
- `status`
- `qos`
- `retry_count`
- `timeout_at`
- `published_at`
- `ack_time`
- `error_code`
- `error_message`
- `reply_payload`
- `business_id`
- `create_time`
- `update_time`

命令事件表：

`device_mqtt_command_event`：

- `command_message_id`
- `status`
- `event_payload`
- `event_time`

设备表建议扩展：

- `supports_mqtt`
- `mqtt_client_id`
- `supported_commands`
- `last_mqtt_online_time`
- `dispatch_mode`

## 6. 测试数据准备

需要准备：

| 数据 | 用途 |
| --- | --- |
| 平台账号 / Token | 调用正式业务 API |
| 11/12 设备 ID | 确保目标设备与 MQTT 注册一致 |
| 小型测试文件 | 内容投递 / 远程升级测试 |
| 链路配置样例 | 测试 `CHAIN_DEPLOY` |
| 控制命令样例 | 测试 `CONTROL_DELIVERY` |
| 数据库初始化 SQL | 修复 11 规则表缺失 |
| UKey 证书和配置 | 国密业务验收 |

## 7. 验收标准

MQTT 接管业务层的验收标准不是“网关能收到 MQTT 消息”，而是：

1. 平台正式业务 API 可触发 MQTT 下发。
2. 平台命令表有完整状态流转。
3. 网关能复用原业务逻辑执行业务动作。
4. 网关回执能正确更新平台业务状态。
5. 失败原因可追踪到具体业务环节。
6. 重启、重连、重复消息、超时都不会造成重复副作用。
7. HTTP 直连可关闭或仅作为 fallback，不影响主流程。

## 8. 推荐执行顺序

1. 已补 `QUERY_STATUS` / `NOOP` / `ECHO` 命令，下一步打通正式平台 API 到 11/12 命令表 `SUCCESS`。
2. 把平台网络配置 API 改到 `DeviceCommandDispatcher`。
3. 把链路部署 API 改到 `DeviceCommandDispatcher`。
4. 修复 `77.11 -> 77.12:8093` 网络互通。
5. 验证 `CHAIN_DEPLOY` 和代理规则生效。
6. 接入控制命令投递。
7. 接入内容投递和文件类任务。
8. 最后验证远程升级和异常恢复。

## 9. 当前最小下一步

为了最快证明“业务层被 MQTT 接管”，建议下一步只做一个闭环：

1. 平台新增或确认 `POST /api/mqtt/commands/query-status`。
2. 该接口创建 `device_mqtt_command` 记录。
3. 平台分别发布 `QUERY_STATUS` 到 `publish-gateway-11` 和 `terminal-gateway-12`。
4. 77.12 返回 `SUCCESS`，包含版本、IP、端口、角色。
5. 平台命令表更新为 `SUCCESS`。
6. 页面或查询接口能看到该命令结果。

这个闭环风险最低，不依赖网络改 IP、不依赖 11 到 12 互访、不依赖 UKey、不依赖 MinIO，是最适合作为业务接管第一验收点的测试。
