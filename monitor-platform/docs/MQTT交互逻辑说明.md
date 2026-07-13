# MQTT 交互逻辑说明

> 适用范围：`monitor-platform`（管控平台）、`publish-gateway`（加密/发布网关）、`terminal-gateway`（解密/终端网关）
> 技术基线：Java 8 / Spring Boot 2.7.x / Eclipse Paho MQTT v3 1.2.5 / EMQX 5
> 文档版本：2026.07.08
> 事实来源：当前仓库代码与配置，非文档推测

---

## 0. 总体结论

本系统 MQTT 链路是一条**远程运维控制通道**，而非业务数据通道。三者角色定位：

| 工程 | MQTT 角色 | 默认状态 | 说明 |
| --- | --- | --- | --- |
| `monitor-platform` | **平台侧**：MQTT 客户端 + EMQX 认证回调服务端 | 受 `forward.dispatch.mode` 控制（默认 `http`，需切到 `mqtt`/`dual`） | 下发命令到 `down/#`，订阅 `up/#` 接收回执/心跳/注册；同时向 EMQX 暴露 `/mqtt/auth`、`/mqtt/acl`、`/mqtt/webhook` 三个 HTTP 回调 |
| `publish-gateway` | **设备侧**：MQTT 客户端 | 默认关闭（`mqtt-agent.enabled=false`） | 订阅自身 `down/#` 接收平台命令，上行 `heartbeat`/`reply`/`register`；MQTT 仅用于运维通道，业务数据走 HTTP+UDP/TCP |
| `terminal-gateway` | **设备侧**：MQTT 客户端 | 默认关闭（`mqtt-agent.enabled=false`） | 与 publish-gateway 完全同构的 MQTT Agent；MQTT 是旁路命令通道，主通信走加密 HTTP + 加密 UDP/TCP |

**关键边界**：
- MQTT 承载的是**命令下发与状态回执**（链路配置、状态查询、远程升级、网络变更等）。
- **业务数据（节目发布内容、播放控制指令）不走 MQTT**：publish-gateway 与 terminal-gateway 之间通过加密 HTTP（`/api/secure-command/*`，国密 SM4 加密 + SecureGatewayEnvelope 信封）和加密 UDP/TCP（Netty 代理 + 20 字节 MessageHeader + 国密加密）通信。
- 两个网关复用同一套 MQTT 核心模块 `monitor-platform-mqtt-core`，Topic 格式、信封结构、回执机制完全一致。

---

## 1. MQTT 连接与认证机制

### 1.1 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                    EMQX 5 Broker                            │
│              (ssl://broker:8883)                            │
│   ┌─────────────────────────────────────────┐               │
│   │ HTTP Auth 插件 → POST /mqtt/auth         │              │
│   │ HTTP ACL  插件 → POST /mqtt/acl          │              │
│   │ Webhook       → POST /mqtt/webhook       │              │
│   └──────────────┬──────────────────────────┘               │
└─────────┬────────┴───────────┬───────────────┬──────────────┘
          │                    │               │
   平台客户端连接          publish-gw连接   terminal-gw连接
  (monitor-platform-001)  (publish-gateway-001) (terminal-gateway-001)
          │                    │               │
          ▼                    ▼               ▼
┌─────────────────┐   ┌──────────────┐   ┌──────────────┐
│ monitor-platform│   │publish-gateway│   │terminal-gw   │
│  forward 模块    │   │  udp-proxy   │   │  udp-proxy   │
│                 │   │  mqtt 包     │   │  mqtt 包     │
│ /mqtt/auth 回调  │   │              │   │              │
│ /mqtt/acl  回调  │   │              │   │              │
│ /mqtt/webhook回调│   │              │   │              │
└─────────────────┘   └──────────────┘   └──────────────┘
```

### 1.2 EMQX 认证回调三端点（平台侧）

平台 `monitor-platform-forward` 模块的 `MqttAccessController`（`monitor-platform-forward/.../controller/MqttAccessController.java`）向 EMQX 暴露三个 HTTP 回调端点，EMQX 在客户端连接、发布/订阅时回调：

| 端点 | 触发时机 | 作用 | 返回 |
| --- | --- | --- | --- |
| `POST /mqtt/auth` | 客户端连接 EMQX 时 | 校验 clientId/username/password | 204（放行）/ 401（拒绝） |
| `POST /mqtt/acl` | 客户端发布或订阅 topic 时 | 校验客户端是否有权操作该 topic | 200 + `{result:"allow"}`（放行）/ 403 + `{result:"deny"}`（拒绝） |
| `POST /mqtt/webhook` | 客户端连接/断开生命周期事件 | 通知平台设备上下线，刷新设备状态 | 200 |

> 来源：`MqttAccessController.java:49-134`

### 1.3 双重凭证体系

系统区分**平台客户端**与**设备客户端**两套凭证：

**平台客户端**（`monitor-platform`）：
- clientId：`monitor-platform-001`（或匹配 `platformClientPrefix = monitor-platform,platform,server` 前缀）
- username/password：由 `forward.dispatch.mqtt-username` / `mqtt-password` 配置
- 识别逻辑：`MqttAccessController.isPlatformClient()`（第 263-280 行）

**设备客户端**（`publish-gateway` / `terminal-gateway`）：
- clientId：`publish-gateway-001` / `terminal-gateway-001`（需全局唯一）
- username/password：由 `mqtt-agent.username` / `mqtt-agent.password` 配置，设备密码统一校验 `forward.dispatch.device-default-password`
- 识别逻辑：非平台前缀的 clientId 即视为设备客户端

> 生产约束：`allowEmptyDevicePassword = false`，拒绝未配置密码的设备接入（`MqttDispatchProperties.java:92`）。

### 1.4 ACL 授权规则

EMQX 的 `acl.conf` 设置默认拒绝（`authorization.no_match = deny`），所有细粒度授权由 HTTP ACL 回调 `/mqtt/acl` 决策：

| 客户端类型 | 允许的发布（publish） | 允许的订阅（subscribe） |
| --- | --- | --- |
| 平台客户端 | `down/#`（任意 `{tenantId}/{siteId}/{deviceId}`） | `up/#`（通配 `/+/+/+/up/#`） |
| 设备客户端 | **仅自己的** `/{tenantId}/{siteId}/{deviceId}/up/#` | **仅自己的** `/{tenantId}/{siteId}/{deviceId}/down/#` |

设备 ACL 强制校验 topic 中的 `deviceId` 段必须等于客户端的 `clientId`，防止设备越权访问其他设备的 topic（`MqttAccessController.evaluateDeviceAcl()` 第 167-198 行）。

### 1.5 连接参数

三个客户端的连接参数对比：

| 参数 | 平台（MqttDispatchProperties） | publish-gateway（MqttAgentProperties） | terminal-gateway（MqttAgentProperties） |
| --- | --- | --- | --- |
| 配置前缀 | `forward.dispatch.*` | `mqtt-agent.*` | `mqtt-agent.*` |
| Broker URL | `ssl://127.0.0.1:8883` | `ssl://mqtt.example.com:8883` | `ssl://mqtt.example.com:8883` |
| clientId | `monitor-platform-001` | `publish-gateway-001` | `terminal-gateway-001` |
| QoS | 1 | 1 | 1 |
| cleanSession | true | true | true |
| keepAlive | 30s | 30s | 30s |
| autoReconnect | true（Paho 默认） | true | true |
| 应用层心跳间隔 | 30s | 30s | 30s |
| 重连扫描间隔 | 30s | 30s | 30s |
| TLS truststore | 可选（空则用 JVM 默认信任链） | 可选 | 可选 |
| 主机名校验 | 开启 | 开启 | 开启 |

> 平台 MQTT 启用受 `forward.dispatch.mode` 控制：`http`（默认，不连 MQTT）/ `mqtt`（仅 MQTT）/ `dual`（双通道）。两个网关受 `mqtt-agent.enabled` 控制，默认 `false`。

### 1.6 连接生命周期管理

三个客户端共用 `MqttClientFactory`（`monitor-platform-mqtt-core/.../client/MqttClientFactory.java`）创建 Paho v3 客户端，生命周期一致：

| 阶段 | 行为 |
| --- | --- |
| 初始化 | `@PostConstruct` 调用 `MqttClientFactory.createAndConnect()`，失败则 `mqttClient=null`，不阻断 Spring 启动 |
| 重连 | `@Scheduled` 定时（30s）检测 `mqttClient==null` 时重新初始化；Paho `autoReconnect=true` 处理连接丢失后的自动重连 |
| 连接丢失 | `connectionLost()` 回调仅记日志，依赖 Paho 自动重连 |
| 销毁 | `@PreDestroy` 调用 `MqttClientFactory.disconnectQuietly()` |

> TLS 配置（`MqttClientFactory.configureSsl()` 第 103-129 行）：自定义 truststore 加载失败时回退 JVM 默认信任链；keystore 预留 mTLS 双向认证（当前未启用）。

---

## 2. 消息主题（Topic）的订阅与发布关系

### 2.1 Topic 命名规范

所有 Topic 遵循统一格式（`MqttTopicBuilder.java`，`monitor-platform-mqtt-core`）：

```
/{tenantId}/{siteId}/{deviceId}/{direction}/{messageType}
```

- `direction`：`up`（设备→平台）或 `down`（平台→设备）
- `messageType`：`proxy-command` / `command` / `reply` / `heartbeat` / `register` / `upgrade` / `config` / `property-set` / `cert` / `model` / `event` / `property` / `discovery`

> 示例：`/default/site-001/publish-gateway-001/down/proxy-command`

### 2.2 平台侧订阅与发布

**平台订阅**（接收所有设备上行）：
- `MqttTopicBuilder.upSubscribeAll()` = `/+/+/+/up/#`（通配所有租户、站点、设备的上行消息）

> 来源：`MqttCommandPublishService.init()` 第 104-106 行

**平台发布**（向设备下发命令）：
- `MqttTopicBuilder.downCommand(tenantId, siteId, gatewayDeviceId)` = `/{tenantId}/{siteId}/{deviceId}/down/proxy-command`

> 来源：`MqttCommandPublishService.publishCommand()` 第 152 行。平台当前仅使用 `down/proxy-command` 下发命令。

### 2.3 网关侧订阅与发布（publish-gateway / terminal-gateway 完全同构）

**网关订阅**（接收平台下发）：
- `MqttTopicBuilder.downSubscribeAll(tenantId, siteId, deviceId)` = `/{tenantId}/{siteId}/{deviceId}/down/#`

> 一次性通配订阅所有下行子 topic。来源：`MqttConnectionManager.subscribeDownTopics()`（第 109-117 行）

**网关发布**（向平台上行）：

| Topic | 构建方法 | 触发 |
| --- | --- | --- |
| `/{tenantId}/{siteId}/{deviceId}/up/heartbeat` | `MqttTopicBuilder.upHeartbeat()` | `@Scheduled` 每 30s |
| `/{tenantId}/{siteId}/{deviceId}/up/reply` | `MqttTopicBuilder.upReply()` | 每次收到下行命令后回执 |
| `/{tenantId}/{siteId}/{deviceId}/up/register` | `MqttTopicBuilder.upRegister()` | 连接建立时（reason=CONNECT）+ 每 300s 周期（reason=PERIODIC） |

### 2.4 Topic 全景对照表

| 方向 | Topic | 发布方 | 订阅方 | 消息类型 |
| --- | --- | --- | --- | --- |
| 下行 | `/{t}/{s}/{d}/down/proxy-command` | monitor-platform | publish-gateway / terminal-gateway | PROXY_COMMAND |
| 下行 | `/{t}/{s}/{d}/down/command` | monitor-platform | 网关（V2 新格式，当前平台未使用） | COMMAND |
| 下行 | `/{t}/{s}/{d}/down/upgrade` | monitor-platform | 网关（归一化为 REMOTE_UPGRADE） | UPGRADE |
| 下行 | `/{t}/{s}/{d}/down/config` | monitor-platform | 网关（保留，网关当前忽略） | CONFIG |
| 下行 | `/{t}/{s}/{d}/down/property-set` | monitor-platform | 网关（保留） | PROPERTY_SET |
| 下行 | `/{t}/{s}/{d}/down/cert` | monitor-platform | 网关（保留） | CERT |
| 下行 | `/{t}/{s}/{d}/down/model` | monitor-platform | 网关（保留） | MODEL |
| 上行 | `/{t}/{s}/{d}/up/reply` | 网关 | monitor-platform | REPLY |
| 上行 | `/{t}/{s}/{d}/up/heartbeat` | 网关 | monitor-platform | HEARTBEAT |
| 上行 | `/{t}/{s}/{d}/up/register` | 网关 | monitor-platform | REGISTER |

> 消息类型常量定义于 `MqttMessageTypes.java`（共 13 种），网关 `MqttConnectionManager.normalizeEnvelope()` 仅真正处理 `down/proxy-command`、`down/command`、`down/upgrade` 三类，其余记录 warn 日志后忽略。

---

## 3. 平台与网关之间的消息协议与数据格式

### 3.1 统一消息信封 MqttEnvelope

所有上行/下行 MQTT 消息的外层都是 `MqttEnvelope`（`monitor-platform-mqtt-core/.../dto/MqttEnvelope.java`），序列化为 JSON（UTF-8，FastJSON2）：

```json
{
  "messageId": "UUID（去横线，用于幂等和回执关联）",
  "messageType": "PROXY_COMMAND | REPLY | HEARTBEAT | REGISTER | UPGRADE | ...",
  "tenantId": "default",
  "siteId": "site-001",
  "deviceId": "publish-gateway-001",
  "deviceType": "publish_gateway | terminal_encrypt_gateway | publish_server | info_board",
  "timestamp": 1780000000000,
  "payload": "<内层 JSON 字符串>"
}
```

> **关键设计**：`payload` 字段是 **JSON 字符串**（而非嵌套对象），由 `messageType` 决定内层结构。

### 3.2 下行命令 MqttCommandMessage（信封 payload）

平台下发的命令内层结构（`MqttCommandMessage.java`）：

```json
{
  "schemaVersion": "1.0",
  "businessId": "业务追踪ID",
  "command": "APPLY_CHAIN_CONFIG | STOP_CHAIN | SET_CHAIN_STATUS | QUERY_STATUS | NOOP | ECHO | CHANGE_SYSTEM_IP | CONFIRM_SYSTEM_IP | REMOTE_UPGRADE | ...",
  "payload": { "命令参数，与 REST 接口 DTO 兼容" },
  "actions": [
    {
      "targetDeviceType": "publish_gateway | terminal_encrypt_gateway | publish_server | info_board",
      "mode": "SELF_APPLY | HTTP",
      "path": "/udp-proxy/config",
      "httpMethod": "POST | PUT | DELETE | GET",
      "targetIp": "127.0.0.1",
      "targetPort": 8092,
      "body": { "转发请求体" },
      "targetDeviceId": "..."
    }
  ],
  "qos": 1,
  "timeoutAt": 1780000060000
}
```

`actions` 是命令执行的核心：每条 action 指定一个执行目标，网关逐条执行并汇总结果。

### 3.3 上行回执 MqttReplyMessage（信封 payload）

网关执行命令后回执的内层结构（`MqttReplyMessage.java`）：

```json
{
  "schemaVersion": "1.0",
  "commandMessageId": "<原命令的 messageId，用于关联>",
  "status": "SUCCESS | FAILED | PROCESSING | RECEIVED | REJECTED",
  "message": "执行成功",
  "errorCode": "EXECUTION_FAILED | REJECTED",
  "progress": 100,
  "data": { "actions": [{ "success": true, "httpStatus": 200, "body": {} }] },
  "gatewayDeviceId": "publish-gateway-001",
  "timestamp": 1780000000000
}
```

状态流转：`PROCESSING`（已接收，开始执行）→ `SUCCESS` / `FAILED` / `REJECTED`。

### 3.4 上行心跳 MqttHeartbeatMessage（信封 payload）

```json
{
  "deviceId": "publish-gateway-001",
  "deviceType": "publish_gateway",
  "status": "ONLINE",
  "timestamp": 1780000000000,
  "metadata": {
    "clientId": "publish-gateway-001",
    "innerIp": "192.168.1.25",
    "serverPort": 8092,
    "version": "...",
    "location": "...",
    "manufacturer": "...",
    "model": "..."
  }
}
```

### 3.5 上行注册 MqttDeviceRegisterMessage（信封 payload）

```json
{
  "serviceName": "publish-gateway",
  "instanceId": "publish-gateway-001",
  "deviceId": "publish-gateway-001",
  "deviceType": "publish_gateway",
  "host": "192.168.1.25",
  "port": 8092,
  "macAddress": "...",
  "location": "...",
  "version": "...",
  "manufacturer": "...",
  "model": "...",
  "remark": "...",
  "timestamp": 1780000000000,
  "metadata": { "reason": "CONNECT | PERIODIC", "innerIp": "...", "serverPort": 8092 }
}
```

### 3.6 消息类型常量

`MqttMessageTypes.java` 定义了全部 13 种消息类型常量：

| 常量 | 值 | 方向 | 用途 |
| --- | --- | --- | --- |
| `PROXY_COMMAND` | PROXY_COMMAND | 下行 | 代理命令（主用） |
| `COMMAND` | COMMAND | 下行 | 标准命令（V2） |
| `CONFIG` | CONFIG | 下行 | 配置下发（保留） |
| `PROPERTY_SET` | PROPERTY_SET | 下行 | 属性设置（保留） |
| `UPGRADE` | UPGRADE | 下行 | 远程升级 |
| `CERT` | CERT | 下行 | 证书下发（保留） |
| `MODEL` | MODEL | 下行 | 模型下发（保留） |
| `REPLY` | REPLY | 上行 | 命令回执 |
| `HEARTBEAT` | HEARTBEAT | 上行 | 心跳 |
| `REGISTER` | REGISTER | 上行 | 设备注册 |
| `DISCOVERY` | DISCOVERY | 上行 | 设备发现（保留） |
| `PROPERTY` | PROPERTY | 上行 | 属性上报（保留） |
| `EVENT` | EVENT | 上行 | 设备事件（保留） |

---

## 4. 关键业务场景下的消息流转链路

### 4.1 场景一：平台下发命令到网关（控制链路）

这是 MQTT 通道的核心业务场景。平台通过 MQTT 向网关下发链路配置、状态查询、远程升级等运维命令。

```
[monitor-platform] MqttAccessController / 业务 Service
    │  调用 MqttCommandPublishService.publishCommand(gatewayDeviceId, command, payload, actions)
    │  ├─ 生成 messageId (UUID)
    │  ├─ 构建 MqttCommandMessage → 序列化为 payloadJson
    │  ├─ 构建 MqttEnvelope(messageType=PROXY_COMMAND)
    │  ├─ 入库 DeviceMqttCommand (status=CREATED)
    │  └─ mqttClient.publish(topic=/{t}/{s}/{d}/down/proxy-command, QoS=1)
    │     └─ 更新 status=PUBLISHED
    ▼
[EMQX Broker]
    │  ACL 校验：平台客户端仅允许 publish 到 down/#（MqttAccessController.evaluatePlatformAcl）
    ▼
[publish-gateway / terminal-gateway] MqttConnectionManager (subscribe /{t}/{s}/{d}/down/#)
    │  MqttCallback.messageArrived(topic, message)
    │  → handleIncomingMessage(topic, message)
    │     ├─ JSON.parseObject(payload, MqttEnvelope.class)
    │     ├─ normalizeEnvelope()  // upgrade → 包装为 PROXY_COMMAND+REMOTE_UPGRADE
    │     └─ GatewayCommandDispatcher.onCommand(envelope)
    │        ├─ MqttCommandRecordService.isProcessed(messageId)  // 幂等去重
    │        ├─ JSON.parseObject(envelope.payload, MqttCommandMessage.class)
    │        ├─ ReplyPublisher.publishReply(PROCESSING) → up/reply  ★ 立即上行
    │        ├─ 按命令类型分发执行：
    │        │   ├─ QUERY_STATUS/NOOP/ECHO → executeNativeProbeCommand (原生处理)
    │        │   ├─ CHANGE_SYSTEM_IP/CONFIRM_SYSTEM_IP → SystemNetworkChangeService
    │        │   ├─ REMOTE_UPGRADE → RemoteUpgradeService
    │        │   └─ 其他业务命令 → 按 actions[] 逐条 executeAction
    │        │       ├─ SELF_APPLY → LocalHttpForwardService.forward(127.0.0.1, 8092, path, method, body)
    │        │       │   → 本机 REST 接口（如 /udp-proxy/config 启停链路规则）
    │        │       └─ HTTP → LocalHttpForwardService.forward(targetIp, targetPort, path, method, body)
    │        │           → 局域网内其他设备 REST 接口
    │        └─ ReplyPublisher.publishReply(SUCCESS|FAILED, data={actions:[...]}) → up/reply  ★ 最终回执
    ▼
[monitor-platform] MqttCommandPublishService (subscribe /+/+/+/up/#)
    │  messageArrived → dispatchIncomingMessage → onMessage
    │  ├─ messageType=REPLY → MqttReplyHandler.handleReply(reply)
    │  │   ├─ 按 commandMessageId 查 DeviceMqttCommand 记录
    │  │   ├─ 已是终态则忽略（防重复回执）
    │  │   ├─ status=PROCESSING → 更新 status=PROCESSING
    │  │   ├─ status=SUCCESS → 更新 status=SUCCESS
    │  │   ├─ status=FAILED → 更新 status=FAILED + errorMessage
    │  │   ├─ status=REJECTED → 更新 status=FAILED + errorCode=REJECTED
    │  │   └─ 同步 RemoteUpgradeReplyService（远程升级状态）
    │  └─ 记录命令事件 (MqttCommandEventService)
```

**命令状态机**（`DeviceMqttCommand`）：
```
CREATED → PUBLISHED → (PROCESSING) → SUCCESS / FAILED / TIMEOUT / CANCELED
```

> 平台侧 `MqttCommandPublishService.waitForFinalStatus()` 提供同步等待回执的能力，超时后标记 `TIMEOUT`。

### 4.2 场景二：网关心跳上报

网关定时向平台上报心跳，平台据此维护设备在线状态。

```
[publish-gateway / terminal-gateway] HeartbeatPublisher
    │  @Scheduled(fixedDelay = 30s)
    │  ├─ 检查 mqttClient.isConnected()，未连接则跳过
    │  ├─ 构建 MqttHeartbeatMessage(deviceId, deviceType, status=ONLINE, metadata)
    │  ├─ 构建 MqttEnvelope(messageType=HEARTBEAT, payload=心跳JSON)
    │  └─ mqttClient.publish(topic=/{t}/{s}/{d}/up/heartbeat, QoS=1)
    ▼
[EMQX Broker] ACL 校验：设备仅允许 publish 到自己的 up/#
    ▼
[monitor-platform] MqttCommandPublishService.messageArrived
    │  → onMessage → messageType=HEARTBEAT
    │  → MqttHeartbeatHandler.handleHeartbeat(heartbeat)
    │     └─ DeviceFeignClient.heartbeat(deviceId)  // 刷新设备在线状态
```

此外，EMQX 的 Webhook 在设备连接/断开时也会通知平台（`/mqtt/webhook`），连接事件触发 `deviceFeignClient.heartbeat()`，断开事件触发 `deviceFeignClient.updateStatus(instanceId, "离线")`。

### 4.3 场景三：设备注册上线

网关连接 MQTT 后立即注册，并周期性（300s）重注册。

```
[publish-gateway / terminal-gateway] DeviceRegisterPublisher
    │  触发：(1) MqttConnectionManager.init() 连接成功后 reason="CONNECT"
    │        (2) @Scheduled(300s) reason="PERIODIC"
    │  ├─ 构建 MqttDeviceRegisterMessage(serviceName, instanceId, host, port, macAddress, ...)
    │  ├─ 构建 MqttEnvelope(messageType=REGISTER, payload=注册JSON)
    │  └─ mqttClient.publish(topic=/{t}/{s}/{d}/up/register, QoS=1)
    ▼
[EMQX Broker]
    ▼
[monitor-platform] MqttCommandPublishService.messageArrived
    │  → onMessage → messageType=REGISTER
    │  → MqttRegisterHandler.handleRegister(register)
    │     ├─ 校验 instanceId / host / port 非空
    │     └─ DeviceFeignClient.register(body)  // 注册或更新设备台账
```

### 4.4 场景四：远程升级

平台通过 `down/upgrade` topic 下发升级命令，网关归一化后执行。

```
[monitor-platform] 下发 UPGRADE 消息到 /{t}/{s}/{d}/down/upgrade
    ▼
[网关] MqttConnectionManager.normalizeEnvelope()
    │  检测 messageType=UPGRADE，自动包装为：
    │  MqttCommandMessage(command=REMOTE_UPGRADE, payload=原upgrade payload)
    │  envelope.messageType = PROXY_COMMAND
    │  envelope.payload = 序列化后的 commandMessage
    ▼
[网关] GatewayCommandDispatcher → RemoteUpgradeService.execute()
    │  ├─ 下载升级包（HTTP GET）
    │  ├─ SHA256 校验
    │  ├─ upgrade.executor.enabled=false 时仅校验不执行
    │  └─ 回执 SUCCESS/FAILED → up/reply
    ▼
[monitor-platform] MqttReplyHandler → RemoteUpgradeReplyService.handleReply()
    │  同步升级任务状态
```

### 4.5 场景五：命令幂等与去重

网关侧 `MqttCommandRecordService` 基于 `messageId` 做进程内幂等：

- `ConcurrentHashMap<String, Long>`（key=messageId, value=首次处理时间戳）
- `isProcessed()`：命中且未过期（TTL 默认 24h）则返回 true，过期则移除
- `markProcessed()`：`putIfAbsent` 防并发重复
- `@Scheduled`（默认 10min）定时清理过期记录

> 注意：当前为进程内实现，多实例部署时需替换为 Redis SETNX（代码注释已标注）。

### 4.6 场景六：MQTT 命令的 HTTP 转发白名单

网关收到 MQTT 命令后，通过 `LocalHttpForwardService` 将命令转发到本机或局域网设备的 REST 接口。为防止命令注入，设有路径白名单（`mqtt-agent.http-forward.allowed-paths`）：

默认允许的路径前缀：
- `/udp-proxy/config`、`/udp-proxy/chain/`、`/udp-proxy/stop/`、`/udp-proxy/status/`、`/udp-proxy/rules`
- `/api/client/commands/`、`/api/secure-command/`、`/api/command/`

HTTP 方法仅允许 POST/PUT/DELETE/GET。不匹配则抛 `IllegalArgumentException`。

### 4.7 场景七：业务数据链路（非 MQTT，对比说明）

MQTT 不承载业务数据。业务数据链路如下，与 MQTT 完全独立：

**发布内容链路**（平台/客户端 → publish-gateway → terminal-gateway → 情报板）：
```
info-publish-client → HTTP POST /api/secure-delivery/tasks (含 publishPermit JWT)
  → publish-gateway SecureDeliveryServiceImpl
    → 验签 JWT (HMAC-SHA256)
    → 构建 SecureGatewayEnvelope (v2 信封) 或旧扁平 JSON (fallback)
    → CryptoService.encrypt() (国密 SM4)
    → HTTP POST terminal-gateway /api/secure-command/publish (application/octet-stream)
      → terminal-gateway 解密 → 编排 → 厂商适配器 → 情报板
      → 返回 SecureGatewayAck
```

**实时业务数据链路**（Sigma/Nova → publish-gateway → terminal-gateway → 情报板）：
```
Sigma 客户端 → UDP/TCP → publish-gateway (UdpProxyServer/TcpProxyServer)
  → sourceIp 白名单校验
  → CryptoService.encrypt([20B MessageHeader][Body])
  → UDP/TCP → terminal-gateway (解密 → 转发到情报板)
```

---

## 5. 回执与可靠性机制

### 5.1 三段式回执

网关处理每条命令产生三段回执：

| 阶段 | 状态 | 触发时机 | 含义 |
| --- | --- | --- | --- |
| 1 | `PROCESSING` | 命令解析成功后立即上行 | 已接收，开始执行 |
| 2 | `SUCCESS` / `FAILED` | 所有 actions 执行完后汇总 | 最终结果 |
| 3 | `FAILED`（异常） | catch 任何异常 | 执行异常兜底 |

> 来源：`GatewayCommandDispatcher.onCommand()` 第 112-182 行

### 5.2 action 成功判定

`GatewayCommandDispatcher.isActionSuccess()`（第 307-330 行）：
- `success` 字段为 `false` → 失败
- `httpStatus` 非 2xx → 失败
- 含 `code` 字段时，必须 `code == 200` 才算成功

### 5.3 平台侧超时处理

`MqttCommandPublishService.waitForFinalStatus(commandId, timeoutSec)`：
- 每 500ms 轮询 `DeviceMqttCommand` 状态
- 命令带 `timeoutAt` 字段（发布时 = now + commandTimeoutSec × 1000）
- 超时未收到终态回执 → 标记 `TIMEOUT`
- 已是终态（SUCCESS/FAILED/TIMEOUT/CANCELED）的命令忽略后续回执

### 5.4 平台侧命令超时扫描

`monitor-platform-forward` 还有定时扫描超时命令的任务，将超过 `timeoutAt` 仍未收到终态回执的命令标记为 `TIMEOUT`。

---

## 6. 模块复用关系

### 6.1 monitor-platform-mqtt-core 公共模块

三个工程共用 `monitor-platform-mqtt-core`（`monitor-platform/monitor-platform-mqtt-core`），包含 16 个 Java 文件：

**客户端层**（`client` 包）：
- `MqttClientFactory` — Paho v3 客户端工厂，创建连接、SSL 配置、自动重连
- `MqttSslOptions` — SSL/TLS 选项（truststore/keystore/hostnameVerification）

**DTO 层**（`dto` 包）：
- `MqttEnvelope` — 统一消息信封
- `MqttTopicBuilder` — Topic 构建器（所有上下行 topic 格式）
- `MqttMessageTypes` — 消息类型常量（13 种）
- `MqttCommandMessage` — 下行命令消息体（含 Action 内部类）
- `MqttReplyMessage` — 上行回执消息体（含静态工厂方法 success/failed/processing/rejected）
- `MqttHeartbeatMessage` — 心跳消息体
- `MqttDeviceRegisterMessage` — 注册消息体
- `MqttDeviceEventMessage` — 设备事件消息体（保留）
- `MqttPropertyReportMessage` — 属性上报消息体（保留）
- `MqttAuthRequest` — EMQX HTTP Auth 回调请求体（平台侧用）
- `MqttAclRequest` / `MqttAclResponse` — EMQX HTTP ACL 回调请求/响应体（平台侧用）
- `MqttWebhookRequest` — EMQX 生命周期 Webhook 请求体（平台侧用）

### 6.2 依赖方式

| 工程 | 依赖方式 | POM 位置 |
| --- | --- | --- |
| publish-gateway | GAV 坐标 `com.monitorplatform:monitor-platform-mqtt-core:1.0.0` | `publish-gateway/gateway-udp-proxy/pom.xml:114-119` |
| terminal-gateway | GAV 坐标 + 根 pom 将其作为兄弟模块纳入构建 | `terminal-gateway/gateway-udp-proxy/pom.xml` + `terminal-gateway/pom.xml:12` |
| monitor-platform | 父子模块继承 | `monitor-platform/pom.xml` |

### 6.3 各工程自有 MQTT 类

两个网关的 MQTT 代码结构完全对称，均位于 `gateway-udp-proxy` 模块的 `mqtt` 包下（10 个类）：

| 类 | 职责 |
| --- | --- |
| `MqttAgentProperties` | `@ConfigurationProperties(prefix="mqtt-agent")` 配置类 |
| `MqttConnectionManager` | 连接生命周期管理、订阅 down/#、回调分发 |
| `GatewayCommandDispatcher` | 下行命令分发、幂等、回执、actions 执行 |
| `MqttCommandRecordService` | 命令幂等去重（ConcurrentHashMap + TTL） |
| `ReplyPublisher` | 上行回执发布（up/reply） |
| `HeartbeatPublisher` | 上行心跳发布（up/heartbeat，@Scheduled 30s） |
| `DeviceRegisterPublisher` | 上行注册发布（up/register，连接时 + @Scheduled 300s） |
| `LocalHttpForwardService` | 本地/局域网 HTTP 转发（带路径白名单） |
| `RemoteUpgradeService` | 远程升级执行器（下载 + SHA256 + 可选执行） |
| `SystemNetworkChangeService` | 系统网络变更（修改 IP，支持 dryRun + 回滚） |

---

## 7. 关键代码索引

### 平台侧（monitor-platform）

| 功能 | 文件 | 关键行号 |
| --- | --- | --- |
| MQTT 客户端初始化 + 订阅 up/# | `monitor-platform-forward/.../service/MqttCommandPublishService.java` | 60-111 |
| 命令发布（构建 Envelope + 入库 + publish） | 同上 | 122-210 |
| 上行消息分发 | 同上 | 264-330 |
| 回执处理 | `monitor-platform-forward/.../service/MqttReplyHandler.java` | 28-97 |
| 心跳处理 | `monitor-platform-forward/.../service/MqttHeartbeatHandler.java` | 30-57 |
| 注册处理 | `monitor-platform-forward/.../service/MqttRegisterHandler.java` | 22-47 |
| EMQX 认证/ACL/Webhook 回调 | `monitor-platform-forward/.../controller/MqttAccessController.java` | 49-134 |
| ACL 授权决策 | 同上 | 141-198 |
| 下发配置属性 | `monitor-platform-forward/.../config/MqttDispatchProperties.java` | 全文 |
| monolith 融合部署镜像 | `monitor-platform-monolith/.../forward/service/*` | 同 forward |

### 公共模块（monitor-platform-mqtt-core）

| 功能 | 文件 | 关键行号 |
| --- | --- | --- |
| Topic 构建器 | `dto/MqttTopicBuilder.java` | 24-97 |
| 消息信封 | `dto/MqttEnvelope.java` | 18-45 |
| 消息类型常量 | `dto/MqttMessageTypes.java` | 11-23 |
| 命令消息体 | `dto/MqttCommandMessage.java` | 全文 |
| 回执消息体 | `dto/MqttReplyMessage.java` | 全文 |
| Paho 客户端工厂 | `client/MqttClientFactory.java` | 70-165 |
| SSL 选项 | `client/MqttSslOptions.java` | 全文 |

### 网关侧（publish-gateway / terminal-gateway，结构对称）

| 功能 | 文件 | 关键行号 |
| --- | --- | --- |
| MQTT 连接初始化 | `gateway-udp-proxy/.../mqtt/MqttConnectionManager.java` | 42-88 |
| 订阅 down/# | 同上 | 109-117 |
| 下行消息回调 | 同上 | 119-137 |
| upgrade 归一化 | 同上 | 148-156 |
| 命令分发 + 回执 | `gateway-udp-proxy/.../mqtt/GatewayCommandDispatcher.java` | 69-183 |
| 命令幂等 | `gateway-udp-proxy/.../mqtt/MqttCommandRecordService.java` | 34-61 |
| 心跳发布 | `gateway-udp-proxy/.../mqtt/HeartbeatPublisher.java` | 59-98 |
| 回执发布 | `gateway-udp-proxy/.../mqtt/ReplyPublisher.java` | 43-82 |
| 注册发布 | `gateway-udp-proxy/.../mqtt/DeviceRegisterPublisher.java` | 47-93 |
| HTTP 转发白名单 | `gateway-udp-proxy/.../mqtt/LocalHttpForwardService.java` | 120-141 |
| MQTT 配置属性 | `gateway-udp-proxy/.../mqtt/MqttAgentProperties.java` | 全文 |

### 部署配置

| 配置 | 文件 |
| --- | --- |
| 平台 MQTT 配置 | `monitor-platform/monitor-platform-forward/src/main/resources/application.yml`（`forward.dispatch` 段） |
| publish-gateway MQTT 配置 | `publish-gateway/gateway-udp-proxy/src/main/resources/application.yml`（`mqtt-agent` 段） |
| terminal-gateway MQTT 配置 | `terminal-gateway/gateway-udp-proxy/src/main/resources/application.yml`（`mqtt-agent` 段） |
| EMQX ACL 兜底规则 | `monitor-platform/deploy/emqx/etc/acl.conf` |
| Docker 环境变量 | `publish-gateway/deploy/docker-compose.yml` / `publish-gateway/deploy/.env.example` |

---

## 8. 文档同步检查

按照 `AGENTS.md` 文档同步硬规则，本轮任务为**只读分析与文档输出**，未改动代码、配置、部署脚本、接口契约、端口或模块边界，因此：

- `系统架构.md` — 已检查，MQTT 架构描述与本文档一致，无需调整。
- `监管平台端口梳理.md` — 已检查，MQTT 端口（8883/1883）未变化，无需调整。
- `信发管理系统功能清单.xlsx` — 已检查，MQTT 运维通道为既有能力，无功能状态变化，无需调整。
