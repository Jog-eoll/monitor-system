# 加密网关与解密网关 JSON 通信改造设计

> 生成日期：2026-06-27  
> 适用范围：`D:/project02/publish-gateway`、`D:/project02/terminal-gateway`  
> 交付目的：作为后续 agent 执行加密网关、解密网关联动改造时的上下文输入  
> 硬约束：`terminal-gateway/gateway-device-protocol` 不修改，只基于其能力模型向上集成

## 1. 项目上下文摘要

当前仓库是 Java/Spring 多模块后端工程。相关模块分为两侧：

- 加密网关：`publish-gateway/gateway-udp-proxy`
- 解密网关：`terminal-gateway/gateway-udp-proxy`、`terminal-gateway/gateway-device-core`
- 设备协议能力层：`terminal-gateway/gateway-device-protocol`

技术基线：

- JDK：Java 8
- Spring Boot：2.7.x 系列
- JSON：`fastjson2` 与 Jackson 均已在现有代码中使用
- 代码风格：Spring Bean + Lombok + DTO/Service/Orchestrator 分层

本次设计只处理加密网关和解密网关之间的“密文 HTTP + 明文 JSON 契约”。传统 UDP/TCP 转发链路不参与本次 JSON 指令改造。

## 2. 现有链路理解

### 2.1 发布链路

```text
平台或上游系统
  -> 加密网关 SecureDeliveryServiceImpl
  -> 下载发布文件、校验 permit/hash、构造发布包
  -> CryptoService.encrypt(JSON bytes)
  -> POST 解密网关 /api/secure-command/publish
  -> 解密网关 SecureCommandController
  -> CryptoService.decrypt(cipher bytes)
  -> SecureEnvelopeParser 解析 v2 信封或旧扁平 JSON
  -> PublishPackageOrchestrator 编排清屏、文件上传、播放列表设置
  -> gateway-device-protocol 厂商适配器
  -> 结构化 ACK 返回加密网关
```

### 2.2 控制链路

```text
平台或上游系统
  -> 加密网关 ControlDeliveryServiceImpl
  -> 解密控制任务包、校验 command/params
  -> CryptoService.encrypt(JSON bytes)
  -> POST 解密网关 /api/secure-command/control
  -> 解密网关 SecureCommandController
  -> CryptoService.decrypt(cipher bytes)
  -> SecureEnvelopeParser 解析 v2 信封或旧扁平 JSON
  -> ControlCommandOrchestrator 映射 command 到 DeviceCapability
  -> gateway-device-protocol 厂商适配器
  -> 结构化 ACK 返回加密网关
```

### 2.3 必须保持的边界

- `gateway-device-protocol` 是底层协议能力契约，不能为了 JSON 契约改它。
- `UdpProxyServer`、`TcpProxyServer`、`CatchAllTcpProxyServer`、`MessageAssembler` 继续服务传统密文流量，不和 JSON 指令链路交叉。
- 平台上报 JSON、加密网关内部发布任务 JSON、解密网关密文 JSON 是不同边界，不能混成同一种数据格式描述。

## 3. 改造目标

1. 稳定加密网关与解密网关之间的 JSON 通信契约。
2. 引入 v2 统一信封，支持发布和控制两类消息。
3. 保留旧扁平 JSON 回退能力，便于灰度和生产回滚。
4. 使用统一状态码和结构化 ACK，让加密网关能准确识别失败原因。
5. 通过 `requestId`、`deliveryTaskId`、`commandTaskId` 形成幂等和链路追踪。
6. 补齐 V2 控制能力映射，不修改 `gateway-device-protocol`。
7. 为发布编排任务提供查询入口，便于加密网关跟踪解密侧执行结果。

## 4. 统一 JSON 契约

### 4.1 HTTP 端点

解密网关暴露：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/api/secure-command/publish` | 接收加密后的发布 JSON |
| `POST` | `/api/secure-command/control` | 接收加密后的控制 JSON |
| `GET` | `/api/secure-command/publish-tasks/{orchestrationTaskId}` | 查询解密侧发布编排任务 |
| `GET` | `/api/secure-command/control-tasks/{batchTaskId}` | 查询解密侧控制批量任务 |

请求体为 `application/octet-stream`，内容是加密后的 JSON 字节。

推荐请求头：

| Header | 发布 | 控制 | 说明 |
| --- | --- | --- | --- |
| `X-Request-Id` | 必填 | 必填 | 链路追踪与幂等主键之一 |
| `X-Delivery-Task-Id` | 必填 | 可选 | 加密网关发布任务 ID |
| `X-Command-Task-Id` | 无 | 必填 | 控制命令任务 ID |

### 4.2 v2 发布信封

解密前是密文字节，解密后的明文 JSON 结构如下：

```json
{
  "schemaVersion": "2.0",
  "messageType": "PUBLISH",
  "requestId": "REQ-001",
  "deliveryTaskId": "DLV-001",
  "source": {
    "gatewayId": "publish-gateway",
    "clientId": "client-001"
  },
  "target": {
    "deviceId": "device-001",
    "ip": "192.168.1.100",
    "port": 5200,
    "vendorHint": "NOVA"
  },
  "publish": {
    "action": "PUBLISH_PLAYLIST",
    "sigmaPublishId": "SIGMA-001",
    "publishPermit": "jwt-token",
    "playlist": {
      "playlistId": "playlist-001",
      "digest": "sha256..."
    },
    "files": [
      {
        "orderNo": 1,
        "fileName": "001.jpg",
        "fileType": "IMAGE",
        "contentBase64": "...",
        "durationSeconds": 5,
        "fileHash": "sha256..."
      }
    ],
    "options": {
      "clearBeforePublish": true,
      "checkExistence": true
    }
  },
  "extensions": {}
}
```

### 4.3 v2 控制信封

```json
{
  "schemaVersion": "2.0",
  "messageType": "CONTROL",
  "requestId": "CMD-001",
  "commandTaskId": "CMD-001",
  "source": {
    "gatewayId": "publish-gateway",
    "clientId": "client-001"
  },
  "target": {
    "deviceId": "device-001",
    "ip": "192.168.1.100",
    "port": 5200,
    "vendorHint": "NOVA"
  },
  "control": {
    "action": "CONTROL_SCREEN",
    "command": "BRIGHTNESS",
    "params": {
      "brightness": 80
    }
  },
  "extensions": {}
}
```

### 4.4 旧扁平 JSON 回退契约

`secure-delivery.envelope.enabled=false` 时，加密网关必须发送真正的旧扁平 JSON。旧扁平 JSON 不能包含：

- `schemaVersion`
- `messageType`
- `publish` 包裹节点
- `control` 包裹节点

发布旧扁平 JSON 应直接在根节点放：

```json
{
  "deliveryTaskId": "DLV-001",
  "requestId": "REQ-001",
  "action": "PUBLISH_PLAYLIST",
  "publishPermit": "jwt-token",
  "target": {
    "deviceId": "device-001",
    "ip": "192.168.1.100",
    "port": 5200,
    "vendorHint": "NOVA"
  },
  "playlist": {},
  "files": [],
  "options": {}
}
```

控制旧扁平 JSON 应直接在根节点放：

```json
{
  "taskId": "CMD-001",
  "commandTaskId": "CMD-001",
  "requestId": "CMD-001",
  "action": "CONTROL_SCREEN",
  "command": "BRIGHTNESS",
  "source": {
    "clientId": "client-001"
  },
  "target": {
    "deviceId": "device-001",
    "ip": "192.168.1.100",
    "port": 5200
  },
  "params": {
    "brightness": 80
  }
}
```

注意：解密网关 `SecureEnvelopeParser` 通过 `schemaVersion` 或 `messageType` 判断是否为信封。只要任一标记存在，就不会按真正旧扁平 DTO 的语义处理。

### 4.5 统一 ACK

解密网关外层继续返回项目通用 `Result`，内层 `data` 为结构化 ACK。

发布 ACK：

```json
{
  "code": 200,
  "msg": "发布包已接受并开始编排执行",
  "data": {
    "accepted": true,
    "status": "ACCEPTED",
    "code": 1000,
    "message": "密文包已解密并进入编排执行",
    "requestId": "REQ-001",
    "deliveryTaskId": "DLV-001",
    "orchestrationTaskId": "PUB-ORCH-001",
    "steps": [
      {
        "step": "FILE_UPLOAD",
        "fileOrderNo": 1,
        "fileName": "001.jpg",
        "batchTaskId": "BATCH-001",
        "status": "SUCCESS",
        "message": "ok",
        "devicePath": "/media/001.jpg"
      }
    ]
  }
}
```

控制 ACK：

```json
{
  "code": 200,
  "msg": "控制指令已接受",
  "data": {
    "accepted": true,
    "status": "ACCEPTED",
    "code": 1000,
    "message": "控制指令已接受并下发执行",
    "requestId": "CMD-001",
    "commandTaskId": "CMD-001",
    "batchTaskId": "BATCH-002",
    "mappedCapability": "BRIGHTNESS_SET"
  }
}
```

加密网关判断成败时以 `data.accepted`、`data.status`、`data.code`、`data.steps[].status` 为准，不能只看外层 `Result.code`。

## 5. 状态码语义

统一状态码枚举建议两侧同名，但不共享类，不引入跨模块依赖。

| status | 语义 | 加密网关是否按失败处理 |
| --- | --- | --- |
| `ACCEPTED` | 解密、解析、校验、编排启动成功 | 否 |
| `DUPLICATE_REQUEST` | 幂等命中，回放已接受结果 | 否，前提是 `accepted=true` |
| `REJECTED` | 业务拒绝 | 是 |
| `DECRYPT_FAILED` | 解密失败 | 是 |
| `BAD_JSON` | 明文 JSON 解析失败 | 是 |
| `VALIDATION_FAILED` | 参数或契约校验失败 | 是 |
| `TARGET_NOT_FOUND` | 目标设备不存在 | 是 |
| `UNSUPPORTED_CAPABILITY` | 能力不支持 | 是 |
| `ERROR` | 未分类异常 | 是 |
| `FAILED` | 执行失败 | 是 |
| `TIMEOUT` | 解密侧执行超时或 HTTP 超时 | 是 |
| `PARTIAL_SUCCESS` | 部分设备或步骤成功 | 建议按失败处理 |

`PARTIAL_SUCCESS` 是否允许业务放行必须由上游明确配置。默认发布和控制任务都应按失败处理，因为加密网关面向的是一次完整投递或完整控制命令。

## 6. 解密网关改造设计

### 6.1 新增 DTO 与工具

建议文件：

- `gateway-device-core/controller/dto/SecureStatusCode.java`
- `gateway-device-core/controller/dto/SecureGatewayEnvelope.java`
- `gateway-device-core/controller/dto/SecureGatewayAck.java`
- `gateway-device-core/orchestrator/SecureEnvelopeParser.java`
- `gateway-device-core/service/SecureCommandIdempotencyCache.java`
- `gateway-device-core/service/PublishTaskRegistry.java`

职责：

- `SecureStatusCode`：统一解密侧返回状态，包含数字 code、状态名、默认文案。
- `SecureGatewayEnvelope`：v2 信封 DTO，承载 `publish` 与 `control` 两类 payload。
- `SecureGatewayAck`：面向加密网关的统一响应 DTO。
- `SecureEnvelopeParser`：解密后探测 `schemaVersion/messageType`，有标记走信封解析，无标记走旧 DTO。
- `SecureCommandIdempotencyCache`：按 `requestId`、`deliveryTaskId`、`commandTaskId` 做短期幂等回放。
- `PublishTaskRegistry`：保存发布编排结果快照，供 `/publish-tasks/{orchestrationTaskId}` 查询。

### 6.2 Controller 改造

目标文件：

- `terminal-gateway/gateway-device-core/src/main/java/com/gateway/device/core/controller/SecureCommandController.java`

要求：

- `POST /publish` 和 `POST /control` 接收 `byte[] encryptedPayload`。
- 增加 payload 大小限制，默认 10 MB。
- 读取并打印 `X-Request-Id`、`X-Delivery-Task-Id`、`X-Command-Task-Id`。
- 调用编排器时透传 `requestId`。
- 非接受类响应使用结构化 code，不再固定返回 500。
- 增加 `GET /publish-tasks/{orchestrationTaskId}`。

### 6.3 发布编排器改造

目标文件：

- `terminal-gateway/gateway-device-core/src/main/java/com/gateway/device/core/orchestrator/PublishPackageOrchestrator.java`

要求：

- 注入 `SecureEnvelopeParser`、`SecureCommandIdempotencyCache`、`PublishTaskRegistry`。
- 解密失败返回 `DECRYPT_FAILED`。
- JSON 解析失败返回 `BAD_JSON`。
- 参数校验失败返回 `VALIDATION_FAILED`。
- 目标设备不存在返回 `TARGET_NOT_FOUND`。
- 编排步骤失败返回 `FAILED` 或具体步骤状态。
- 成功时返回 `accepted=true`、`orchestrationTaskId`、`steps`。
- 幂等命中时返回 `DUPLICATE_REQUEST`，且 `accepted=true`。

### 6.4 控制编排器改造

目标文件：

- `terminal-gateway/gateway-device-core/src/main/java/com/gateway/device/core/orchestrator/ControlCommandOrchestrator.java`

要求：

- 新增 `orchestrate(commandTaskId, requestId, encryptedData)` 重载。
- 解密、解析、校验失败按结构化状态码返回。
- command 到 capability 的映射不能越过 `gateway-device-protocol` 既有能力模型。
- 目标设备不存在返回 `TARGET_NOT_FOUND`。
- 不支持能力返回 `UNSUPPORTED_CAPABILITY`。

### 6.5 V2 控制能力映射

需要支持的 command：

| command | 映射能力 |
| --- | --- |
| `BRIGHTNESS` | 现有亮度能力 |
| `BLACKOUT` | 现有黑屏/开关屏能力 |
| `TIME_SYNC` | 现有校时能力 |
| `QUERY_STATUS` | 现有查询能力 |
| `REBOOT` | 现有重启能力 |
| `NTP_SET` | `CommonDeviceCapability.NTP_SET` |
| `DEVICE_IP_SET` | `CommonDeviceCapability.DEVICE_IP_SET` |
| `SCREEN_ATTRIBUTE_SET` | `CommonDeviceCapability.SCREEN_ATTRIBUTE_SET` |
| `FONTS_GET` | `CommonDeviceCapability.FONTS_GET` |
| `FONTS_SYNC` | `CommonDeviceCapability.FONTS_SYNC` |
| `AP_NETWORK_SWITCH` | `NovaViplexCoreCapability.AP_NETWORK_SWITCH` |

`POWER` 暂不放行，除非解密网关已补齐真实能力映射和协议适配。

### 6.6 参数映射

目标文件：

- `terminal-gateway/gateway-device-core/src/main/java/com/gateway/device/core/service/CommandParamsMapper.java`

要求：

- `FONTS_SYNC` 转 `FontSyncParams`。
- `jetFileIIFonts`、`novaStarFonts` 支持字符串列表到枚举列表转换。
- `AP_NETWORK_SWITCH` 转 `ApNetworkSwitchParams`。
- `enable`、`enabled`、`on` 三种字段别名兼容。
- `bytesValue` 支持：
  - `byte[]`
  - Base64 字符串
  - `List<Number>`

## 7. 加密网关改造设计

### 7.1 新增本地 DTO 与工具

目标包：

- `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/entity/dto/secure`

建议文件：

- `SecureStatusCode.java`
- `SecureGatewayEnvelope.java`
- `SecureGatewayAck.java`
- `SecureGatewayEnvelopeFactory.java`
- `SecureGatewayAckParser.java`
- `SecureTerminalClient.java`

目标配置类：

- `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/config/SecureDeliveryEnvelopeProperties.java`

职责：

- 加密网关本地定义 DTO，不依赖解密网关模块。
- `SecureGatewayEnvelopeFactory` 统一构造 v2 publish/control 信封。
- `SecureGatewayAckParser` 统一解析外层 `Result` 和内层 `data`。
- `SecureTerminalClient` 统一访问解密网关发布、控制、查询接口。
- `SecureDeliveryEnvelopeProperties` 提供 `secure-delivery.envelope.enabled` 和 `schema-version`。

### 7.2 发布服务改造

目标文件：

- `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/service/impl/SecureDeliveryServiceImpl.java`

要求：

- `executeDelivery` 中根据配置开关选择 v2 信封或旧扁平 JSON。
- v2 模式使用 `SecureGatewayEnvelopeFactory.publish()`。
- legacy 模式必须构造真正旧扁平 JSON，不能带 `schemaVersion/messageType/publish`。
- 投递使用 `SecureTerminalClient.postPublish()`。
- 响应使用 `SecureGatewayAckParser.parse()`。
- 保存 `orchestrationTaskId` 到 `DeliveryTaskStatus`。
- `DUPLICATE_REQUEST + accepted=true` 按成功回放。
- `TIMEOUT`、`PARTIAL_SUCCESS`、步骤失败不能误判为成功。

### 7.3 控制服务改造

目标文件：

- `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/service/impl/ControlDeliveryServiceImpl.java`

要求：

- 控制白名单与解密网关支持能力一致。
- `POWER` 暂不放行。
- v2 模式使用 `SecureGatewayEnvelopeFactory.control()`。
- legacy 模式必须构造真正旧扁平 JSON，不能带 `schemaVersion/messageType/control`。
- 投递使用 `SecureTerminalClient.postControl()`。
- 响应使用 `SecureGatewayAckParser.parse()`。
- 参数别名兼容：
  - `ntpServer` / `server`
  - `mask` / `netmask` / `subnetMask`
  - `enable` / `enabled` / `on`
- 白名单报错信息应包含当前白名单，便于现场排查。

### 7.4 任务状态扩展

目标文件：

- `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/entity/dto/delivery/DeliveryTaskStatus.java`

要求：

- 新增 `orchestrationTaskId`。
- 查询发布任务时能回显解密侧编排 ID。

### 7.5 配置项

目标文件：

- `publish-gateway/gateway-udp-proxy/src/main/resources/application.yml`

建议配置：

```yaml
secure-delivery:
  terminal-gateway-url: http://127.0.0.1:8093
  envelope:
    enabled: false
    schema-version: "2.0"
  terminal-http:
    connect-timeout-ms: 5000
    read-timeout-ms: 10000
```

上线建议默认 `enabled=false`，确认解密网关已上线兼容解析后再切换为 `true`。

## 8. 审查发现的必修项

以下问题是加密网关当前改造中必须修复的项。

### 8.1 legacy 回退路径不是真正旧扁平 JSON

当前 `SecureDeliveryServiceImpl.buildLegacyEnvelope()` 仍写入 `schemaVersion/messageType`，并把业务数据放在 `publish` 子节点。

当前 `ControlDeliveryServiceImpl.buildLegacyControlPackage()` 仍写入 `schemaVersion/messageType`，并把业务数据放在 `control` 子节点。

这会导致解密网关继续按 v2 信封路径解析，`secure-delivery.envelope.enabled=false` 无法作为真实回滚开关。

修复要求：

- 发布 legacy JSON 去掉 `schemaVersion/messageType/publish`。
- 控制 legacy JSON 去掉 `schemaVersion/messageType/control`。
- 方法命名建议改为 `buildLegacyPublishPayload()` 和 `buildLegacyControlPayload()`。

### 8.2 `TIMEOUT` 和 `PARTIAL_SUCCESS` 不能误判成功

加密网关 `SecureStatusCode.isFailure()` 必须包含：

- `TIMEOUT`
- `PARTIAL_SUCCESS`

或者新增更清晰的方法：

- `isTerminalFailure()`
- `isRetryableFailure()`
- `isAcceptedReplay()`

默认策略：

- `DUPLICATE_REQUEST + accepted=true` 不算失败。
- `accepted=false` 一律失败。
- step 中任一 `FAILED/TIMEOUT/PARTIAL_SUCCESS` 默认使整体任务失败。

### 8.3 `SecureTerminalClient` 必须设置 HTTP 超时

不能直接使用默认 `new RestTemplate()`，否则连接或读取可能无限等待。

修复要求：

- 使用 `SimpleClientHttpRequestFactory` 设置连接超时和读取超时。
- 超时配置来自 `secure-delivery.terminal-http.*` 或复用已有 `control-delivery.http-timeout-ms`。
- 超时异常应转为可识别的 `TIMEOUT` 或明确失败消息。

### 8.4 ACK steps 字段兼容

解密网关发布步骤字段当前使用 `step`，加密网关解析器不能只读取 `stepName`。

修复要求：

- `SecureGatewayAckParser` 同时兼容 `step` 和 `stepName`。
- `code` 同时兼容数字和字符串。
- 解析未知 status 时保留原始字符串，并按失败降级。

## 9. 建议实施顺序

### 阶段一：解密网关稳住契约

1. 新增/校正 `SecureStatusCode`、`SecureGatewayEnvelope`、`SecureGatewayAck`。
2. 完成 `SecureEnvelopeParser`，确保 v2 与旧扁平 JSON 均可解析。
3. 改造 `SecureCommandController`，统一 payload 校验、日志、结构化响应。
4. 改造 `PublishPackageOrchestrator`，补齐结构化错误码、幂等、发布任务登记。
5. 改造 `ControlCommandOrchestrator` 和 `CommandParamsMapper`，补齐 V2 能力映射。

### 阶段二：加密网关对接 v2

1. 新增本地 secure DTO、ACK parser、envelope factory、terminal client。
2. 改造发布服务，支持 v2 信封和真实 legacy 扁平 JSON。
3. 改造控制服务，支持 v2 信封和真实 legacy 扁平 JSON。
4. 修复失败状态判断和 HTTP 超时。
5. 补齐 `orchestrationTaskId` 状态保存。

### 阶段三：联调与灰度

1. 解密网关先上线，保证同时兼容 v2 信封和旧扁平 JSON。
2. 加密网关先以 `secure-delivery.envelope.enabled=false` 验证旧链路。
3. 切换 `enabled=true` 验证发布和控制。
4. 构造异常场景验证结构化错误码。
5. 验证重复 `requestId/deliveryTaskId/commandTaskId` 的幂等回放。

## 10. 测试计划

### 10.1 单元测试

解密网关：

- `SecureEnvelopeParserTest`
  - v2 publish 信封解析
  - v2 control 信封解析
  - 旧 publish 扁平 JSON 解析
  - 旧 control 扁平 JSON 解析
  - 存在 `schemaVersion/messageType` 时不走旧 DTO 解析

- `SecureCommandIdempotencyCacheTest`
  - 首次请求缓存
  - 重复请求回放
  - TTL 过期

- `CommandParamsMapperTest`
  - `FONTS_SYNC`
  - `AP_NETWORK_SWITCH`
  - `bytesValue` 三种输入

加密网关：

- `SecureGatewayEnvelopeFactoryTest`
  - publish 信封字段完整
  - control 信封字段完整

- `SecureGatewayAckParserTest`
  - `ACCEPTED`
  - `DUPLICATE_REQUEST + accepted=true`
  - `DECRYPT_FAILED`
  - `BAD_JSON`
  - `VALIDATION_FAILED`
  - `TARGET_NOT_FOUND`
  - `UNSUPPORTED_CAPABILITY`
  - `TIMEOUT`
  - `PARTIAL_SUCCESS`
  - `steps[].step` 与 `steps[].stepName`

- `SecureDeliveryServiceImplTest`
  - v2 模式输出包含信封标记
  - legacy 模式输出不包含信封标记

- `ControlDeliveryServiceImplTest`
  - `POWER` 被拒绝
  - V2 command 白名单通过
  - 参数别名归一

### 10.2 编译命令

优先使用项目 Maven：

```powershell
cd D:\project02\terminal-gateway
mvn -pl gateway-udp-proxy -am -DskipTests compile

cd D:\project02\publish-gateway
mvn -pl gateway-udp-proxy -am -DskipTests compile
```

如果系统 Maven 不可用，可使用仓库内 Maven：

```powershell
D:\project02\.tools\apache-maven-3.9.9\bin\mvn.cmd -pl gateway-udp-proxy -am -DskipTests compile
```

### 10.3 联调场景

| 场景 | 预期 |
| --- | --- |
| v2 发布成功 | 加密网关任务成功，保存 `orchestrationTaskId` |
| v2 控制成功 | 加密网关任务进入 RUNNING 或 SUCCESS，保存 `batchTaskId` |
| 旧扁平发布 | 解密网关走 legacy DTO，仍可接受 |
| 旧扁平控制 | 解密网关走 legacy DTO，仍可接受 |
| 重复 requestId | 解密网关返回 `DUPLICATE_REQUEST`，加密网关按成功回放 |
| 坏密文 | 解密网关返回 `DECRYPT_FAILED`，加密网关标记失败 |
| 坏 JSON | 解密网关返回 `BAD_JSON`，加密网关标记失败 |
| 缺少目标 | 解密网关返回 `TARGET_NOT_FOUND`，加密网关标记失败 |
| 不支持能力 | 解密网关返回 `UNSUPPORTED_CAPABILITY`，加密网关标记失败 |
| 解密网关不可达 | 加密网关在超时配置内失败，不阻塞 worker |

## 11. 灰度与回滚

上线顺序：

1. 先部署解密网关，因为它需要同时兼容 v2 和 legacy。
2. 部署加密网关，但保持 `secure-delivery.envelope.enabled=false`。
3. 验证 legacy 发布和控制链路。
4. 切换 `secure-delivery.envelope.enabled=true`。
5. 验证 v2 发布、控制、幂等、异常码。

回滚方式：

- 若 v2 信封联调异常，将加密网关配置切回：

```yaml
secure-delivery:
  envelope:
    enabled: false
```

- 回滚前提是 legacy 分支已经按本文第 4.4 节修成真正旧扁平 JSON。
- 不需要回滚 `gateway-device-protocol`，因为本设计不修改它。

## 12. Agent 执行注意事项

1. 不要修改 `terminal-gateway/gateway-device-protocol`。
2. 不要引入新依赖。
3. 不要把 JSON 指令链路塞进 UDP/TCP 转发链路。
4. 先修复 legacy 真回退，再开启 v2。
5. 状态判断以 `accepted/status/code/steps` 为准，不只看 HTTP 2xx 或外层 `Result.code`。
6. 修改 `application.yml` 时只改 `secure-delivery` 相关配置，避免带入无关格式化变更。
7. 当前工作副本可能存在未提交或未跟踪文件，提交前应按白名单核对变更范围。

## 13. 验收清单

- [ ] `gateway-device-protocol` 无变更。
- [ ] 解密网关支持 v2 publish/control 信封。
- [ ] 解密网关支持旧 publish/control 扁平 JSON。
- [ ] 加密网关 `envelope.enabled=false` 时不发送 `schemaVersion/messageType`。
- [ ] 加密网关 `envelope.enabled=true` 时发送 v2 信封。
- [ ] `DUPLICATE_REQUEST + accepted=true` 按成功回放。
- [ ] `TIMEOUT/PARTIAL_SUCCESS` 不被误判为成功。
- [ ] 解密失败、坏 JSON、参数校验失败、目标不存在、能力不支持均有结构化状态码。
- [ ] `SecureTerminalClient` 有连接和读取超时。
- [ ] 发布任务能保存并查询 `orchestrationTaskId`。
- [ ] 控制任务能保存并查询 `batchTaskId`。
- [ ] 发布和控制链路日志都包含 `requestId` 以及对应任务 ID。
