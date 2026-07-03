# 加密网关数据格式分析

本文只分析 `publish-gateway/gateway-udp-proxy` 中“收到数据后上报平台”和“转发给解密网关”的代码路径，不展开平台下发配置、终端网关转发到情报板后的业务处理。

## 1. 范围结论

| 方向 | 传输方式 | 当前代码中的数据形态 |
| --- | --- | --- |
| 加密网关 -> 平台 | HTTP POST JSON | 先按厂商协议解析原始明文数据，组装 `ReportPayload`，再按 `contentType` 投递到内容服务接口 |
| 加密网关 -> 解密网关 UDP | UDP 二进制 | `Encrypt([20B MessageHeader][Body分片])`，无额外长度前缀 |
| 加密网关 -> 解密网关 TCP | TCP 二进制流 | `[4B密文长度][Encrypt([20B MessageHeader][Body])]` |
| 加密网关 -> 解密网关 CatchAll 动态 TCP | TCP 二进制流 | 连接建立先发 `[2B原始目标端口]`，之后发 `[4B密文长度][Encrypt(原始TCP字节块)]`，不封装 `MessageHeader` |

关键点：

1. 平台上报使用的是加密前的原始明文字节，入口在 `DataReportService.reportAsync(...)`。
2. 解密网关收到的是加密后的二进制，不包含 `ruleId`、`chainId`、`sourceIp` 等平台业务字段。
3. 静态 UDP/TCP 转发会把原始数据先封装成内部 `Message`，再整体加密。
4. CatchAll 动态端口路径只做端口握手和原始 TCP 字节加密转发，当前代码没有调用平台上报逻辑。

## 2. 代码入口

| 入口 | 主要代码 | 行为 |
| --- | --- | --- |
| 直接 UDP 接入 | `forward/UdpProxyServer.java` | 校验来源，先上报平台，再构造 UDP `Message`，按需加密后发到终端网关 |
| 直接 TCP 接入 | `forward/TcpProxyServer.java` | 建立到终端网关的 TCP 连接，收到每段入站数据后上报平台，再构造 TCP `Message`，加密后带 4 字节长度前缀发送 |
| Windows 客户端 Relay 接入 | `relay/ClientRelayReceiver.java` | 解出 Relay 包中的原始 UDP payload，再复用 UDP `Message` 格式发送终端网关 |
| Nova 动态端口 CatchAll | `forward/CatchAllTcpProxyServer.java` | 通过原始端口握手告诉终端网关目标端口，后续直接加密原始 TCP 字节块 |

重要代码位置：

- UDP 上报和加密转发：`publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/forward/UdpProxyServer.java:331`、`:345`、`:363`
- TCP 上报和加密转发：`publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/forward/TcpProxyServer.java:398`、`:407`、`:415`、`:431`
- CatchAll 握手和加密帧：`publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/forward/CatchAllTcpProxyServer.java:612`、`:644`、`:647`
- 平台上报组装：`publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/service/DataReportService.java:75`、`:162`、`:197`

## 3. 发给解密网关的数据格式

### 3.1 内部 Message 明文结构

静态 UDP/TCP 转发在加密前先构造内部 `Message`：

```text
MessageBytes = MessageHeader(20 bytes) + Body(N bytes)
```

`MessageHeader` 固定 20 字节，大端序写入：

| 偏移 | 长度 | 字段 | 当前含义 |
| --- | ---: | --- | --- |
| 0 | 1 | magic | 固定 `0xAB` |
| 1 | 1 | version | 固定 `0x01` |
| 2 | 1 | msgType | 当前调用固定传 `0x01`，即透传原始数据 |
| 3 | 1 | encoding | 当前调用固定传 `0x00`，即无编码 |
| 4 | 4 | messageId | 全局递增 ID，UDP 分片使用同一个 ID |
| 8 | 2 | fragmentIndex | 分片序号，从 0 开始 |
| 10 | 2 | fragmentTotal | 总分片数 |
| 12 | 4 | bodyLength | 当前 Body 分片长度 |
| 16 | 4 | totalLength | TCP 为 `20 + bodyLength`；UDP 固定写 0 |

字段来源：

- Header 序列化在 `MessageHeader.toBytes()`，见 `MessageHeader.java:74`。
- `MessageBytes = header + body` 在 `Message.toBytes()`，见 `Message.java:30`。
- UDP 分片阈值 `UDP_MAX_BODY_SIZE = 1400`，见 `MessageBuilder.java:26`。
- TCP 不分片，单次 `channelRead` 构造一个 `Message`，见 `MessageBuilder.java:100`。

Body 的来源：

```text
Body = transcodeService.transcode(原始入站数据, TEXT)
```

当前默认配置 `gateway.transcode.enabled=false`，实际使用 `PassthroughTranscodeServiceImpl`，因此 Body 等于原始入站字节。虽然代码传入了转码开关，但当前 `UdpProxyServer` 和 `TcpProxyServer` 调用 `MessageBuilder` 时仍固定传 `MSG_TYPE_PASSTHROUGH` 与 `ENCODING_NONE`。

### 3.2 UDP 发给解密网关

启用加密的判断条件：

```text
encryptEnabled == true
terminalGatewayIp != null
terminalGatewayPort != null
```

发送目标：

```text
targetIp   = terminalGatewayIp
targetPort = terminalGatewayPort
```

UDP payload 格式：

```text
UDP Datagram Payload = CipherBytes
CipherBytes = cryptoService.encrypt(MessageBytes)
MessageBytes = [20B MessageHeader][Body分片]
```

注意：

1. UDP 模式没有 4 字节长度前缀。
2. 如果 Body 超过 1400 字节，会按 1400 字节切分为多个 `Message`，每个分片单独加密成一个 UDP 包。
3. 解密网关解密后根据 `messageId + fragmentIndex + fragmentTotal` 重组 Body。

### 3.3 TCP 发给解密网关

TCP 是流式协议，密文中已经看不到 `MessageHeader.totalLength`，因此发布网关在密文外层加了明文长度前缀。

TCP frame 格式：

```text
TCP Frame = CipherLength(4 bytes, big-endian) + CipherBytes
CipherBytes = cryptoService.encrypt(MessageBytes)
MessageBytes = [20B MessageHeader][Body]
```

字段说明：

| 字段 | 长度 | 说明 |
| --- | ---: | --- |
| CipherLength | 4 | `CipherBytes.length`，大端序 |
| CipherBytes | N | 对完整 `MessageBytes` 加密后的结果 |

当前 TCP 路径每次 Netty `channelRead` 收到的字节块都会构造成一个独立 `Message`。因此 TCP 的业务边界取决于 Netty 本次读到的 ByteBuf，不一定等于上层业务协议的一条完整消息。

### 3.4 CatchAll 动态 TCP 发给解密网关

CatchAll 用于 Nova 动态端口场景，与静态 TCP 不同，它不构造 `MessageHeader`。

连接建立后的第一段数据：

```text
Handshake = OriginalPort(2 bytes, big-endian)
```

后续数据帧：

```text
TCP Frame = CipherLength(4 bytes, big-endian) + CipherBytes
CipherBytes = cryptoService.encrypt(RawTcpBytes)
```

说明：

1. `OriginalPort` 是 iptables REDIRECT 前的真实目标端口，用于让解密网关连接情报板真实端口。
2. `RawTcpBytes` 是当前 TCP 流读到的原始字节块，不带 20 字节 `MessageHeader`。
3. 当前 `CATCHALL_ENCRYPT_ENABLED = true`。如果规则未启用加密，则代码会走透明转发分支。
4. 当前 CatchAll 类虽然注入了 `DataReportService`，但没有调用 `reportAsync`，因此这条路径不会上传平台。

### 3.5 加密算法输出

`cryptoService.encrypt(...)` 的输出是二进制密文：

| 模式 | 条件 | 实现 | 说明 |
| --- | --- | --- | --- |
| 生产国密模式 | `vauth.mock-mode=false` | `VAuthCryptoServiceImpl` | 调用 VAuth SDK，默认 `VAuth_EncryptData`；`vauth.svac-mode=true` 时调用 `VAuth_EncryptPackData` |
| 开发模拟模式 | `vauth.mock-mode=true` | `SimpleCryptoServiceImpl` | 使用 AES 模拟加解密 |

国密模式下密文内部格式由 VAuth SDK 决定，Java 代码只能看到 SDK 返回的 `byte[]`。如果需要精确到 SDK 密文包内部字段，需要查 VAuth SDK 协议文档或抓包后配合 SDK 解析，单靠当前 Java 代码无法确定。

## 4. 平台上报的数据格式

### 4.1 上报时机

静态 UDP/TCP 和 ClientRelay 路径中，上报都发生在转码、封装 `Message`、加密之前。

```text
原始入站数据 -> dataReportService.reportAsync(...) -> 解析/上报平台
             -> transcode -> Message -> encrypt -> 解密网关
```

因此平台看到的是原始明文侧内容解析结果，而不是密文，也不是 `[MessageHeader][Body]`。

### 4.2 平台地址

平台内容服务地址来自配置：

```yaml
monitor:
  platform:
    content-url: http://${MONITOR_HOST:127.0.0.1}:${MONITOR_CONTENT_PORT:8065}
```

实际投递接口：

| 内容类型 | URL | 条件 |
| --- | --- | --- |
| 图片 | `${content-url}/content/detection/detect` | `contentType=image` 且 `minioPath` 或 `screenshotBase64` 有值 |
| 视频 | `${content-url}/content/detection/detect` | `contentType=video` 且 `minioPath` 有值 |
| 文本 | `${content-url}/content/receive` | `contentType=text` |
| 文件引用 | `${content-url}/content/receive` | `contentType=file_reference`，发送前会归一化为 `text` |
| 二进制/未知 | 不上传 | `contentType=binary` 或其他未命中类型会被跳过 |

### 4.3 JSON 主体

加密网关直接把 `ReportPayload` 序列化为 JSON：

```text
JSON.toJSONString(payload, SerializerFeature.IgnoreNonFieldGetter)
Content-Type: application/json; charset=UTF-8
```

公共字段由 `DataReportService.fillCommonFields(...)` 填充：

| 字段 | 来源/组成 |
| --- | --- |
| businessId | 默认 `ruleId + "-" + 当前毫秒时间戳`；播放批次则为 `playBatchId + "-" + playBatchSeq` |
| contentId | 文本或文件引用上报前设置为 `businessId` |
| gatewayId | `ruleId` |
| deviceId | 优先 `boardIp`，为空则用 `ruleId` |
| deviceName | 优先 `"情报板-" + boardIp`，为空则 `"网关-" + ruleId` |
| chainId | 链路 ID |
| sourceIp | 原始数据来源 IP |
| captureTime | 当前时间，格式 `yyyy-MM-dd HH:mm:ss` |
| timestamp | 当前毫秒时间戳 |
| rawPacket | 原始入站数据的 Base64，不是加密后的数据 |
| boardIp | 规则目标情报板 IP，即 `targetIp` |
| boardPort | 规则目标情报板端口，即 `targetPort` |
| playBatchId/playBatchSeq/playBatchSize | 多文件播放批次命中时填充 |

协议解析字段由 Sigma/Nova 解析器填充：

| 字段组 | 字段 |
| --- | --- |
| 协议标识 | `protocol`、`commandType`、`mainCmd`、`subCmd`、`packetSerial` |
| 地址 | `sourceAddr`、`destAddr`、`address` |
| 内容 | `contentType`、`data`、`fileName`、`filePath`、`fileExtension`、`description` |
| 媒体 | `minioPath`、`screenshotBase64`、`imageFormat` |
| 文件统计 | `totalPackets`、`totalSize` |
| 文件签名审计 | `relaySignatureEnabled`、`relaySignatureSigned`、`relaySignatureVerified`、`relaySignatureAllowed`、`relaySignatureMode`、`relaySignatureFileHash` 等 |

### 4.4 图片/视频文件组成

图片上传逻辑：

1. 解析器拿到图片二进制。
2. 调用 `MinioUploadService.uploadImage(...)`。
3. MinIO 成功时，平台 JSON 使用 `minioPath`。
4. MinIO 不可用且允许降级时，平台 JSON 使用 `screenshotBase64`。

图片 `minioPath` 格式：

```text
images/yyyy/MM/dd/{12位uuid}.{ext}
```

视频上传逻辑：

1. 解析器重组完整视频文件。
2. 调用 `MinioUploadService.uploadVideo(...)`。
3. MinIO 成功时，平台 JSON 使用 `minioPath`。
4. MinIO 失败时，当前 Sigma/Nova 解析器会跳过视频平台上报，避免把大视频塞进 JSON。

视频 `minioPath` 格式：

```text
videos/yyyy/MM/dd/{12位uuid}.{ext}
```

### 4.5 文本上报示例

实际 JSON 会包含 `ReportPayload` 中的更多字段，下面只列核心字段：

```json
{
  "businessId": "rule-001-1780290600000",
  "contentId": "rule-001-1780290600000",
  "gatewayId": "rule-001",
  "deviceId": "192.168.1.60",
  "deviceName": "情报板-192.168.1.60",
  "chainId": 1001,
  "sourceIp": "192.168.1.10",
  "captureTime": "2026-06-01 10:30:00",
  "timestamp": 1780290600000,
  "boardIp": "192.168.1.60",
  "boardPort": 16606,
  "rawPacket": "BASE64_OF_ORIGINAL_PACKET",
  "protocol": "JetFileII-Type1",
  "contentType": "text",
  "data": "实际解析出的文本内容",
  "fileName": "program.nmg",
  "description": "协议解析生成的描述"
}
```

投递到：

```text
POST {monitor.platform.content-url}/content/receive
```

### 4.6 图片上报示例

MinIO 成功时：

```json
{
  "businessId": "rule-001-1780290600000",
  "gatewayId": "rule-001",
  "deviceId": "192.168.1.60",
  "deviceName": "情报板-192.168.1.60",
  "chainId": 1001,
  "sourceIp": "192.168.1.10",
  "captureTime": "2026-06-01 10:30:00",
  "boardIp": "192.168.1.60",
  "boardPort": 16606,
  "rawPacket": "BASE64_OF_ORIGINAL_PACKET",
  "protocol": "Sigma-FileTransfer",
  "contentType": "image",
  "minioPath": "images/2026/06/01/a1b2c3d4e5f6.jpg",
  "imageFormat": "JPEG",
  "fileName": "ad.jpg",
  "description": "Sigma图片文件传输: ..."
}
```

MinIO 不可用且降级时：

```json
{
  "businessId": "rule-001-1780290600000",
  "deviceId": "192.168.1.60",
  "contentType": "image",
  "screenshotBase64": "BASE64_OF_IMAGE_BYTES",
  "imageFormat": "JPEG",
  "rawPacket": "BASE64_OF_ORIGINAL_PACKET"
}
```

投递到：

```text
POST {monitor.platform.content-url}/content/detection/detect
```

### 4.7 视频上报示例

视频只在 MinIO 上传成功时上报：

```json
{
  "businessId": "rule-001-1780290600000",
  "gatewayId": "rule-001",
  "deviceId": "192.168.1.60",
  "chainId": 1001,
  "sourceIp": "192.168.1.10",
  "captureTime": "2026-06-01 10:30:00",
  "boardIp": "192.168.1.60",
  "boardPort": 16606,
  "protocol": "Nova-FileTransfer",
  "contentType": "video",
  "minioPath": "videos/2026/06/01/a1b2c3d4e5f6.mp4",
  "fileName": "play.mp4",
  "description": "Nova视频文件传输: ..."
}
```

投递到：

```text
POST {monitor.platform.content-url}/content/detection/detect
```

## 5. ClientRelay 接入格式

如果启用 `client-relay.enabled=true`，加密网关接收的不是原始 UDP 包，而是 Windows 客户端封装后的 Relay 包。`ClientRelayReceiver` 解包后取 `payload` 作为原始业务数据，再按 UDP 加密格式发给解密网关。

Relay 包头固定 104 字节，大端序：

| 偏移 | 长度 | 字段 |
| --- | ---: | --- |
| 0 | 4 | magic，固定 `0x49504352`，即 ASCII `IPCR` |
| 4 | 4 | version，固定 `1` |
| 8 | 4 | pid |
| 12 | 64 | processName，UTF-8，0 填充 |
| 76 | 4 | originalSrcIp |
| 80 | 2 | originalSrcPort |
| 82 | 4 | originalDstIp |
| 86 | 2 | originalDstPort |
| 88 | 8 | timestamp |
| 96 | 4 | payloadLength |
| 100 | 2 | flags |
| 102 | 2 | reserved |

可选部分：

1. `flags & 0x01`：带 32 字节 HMAC-SHA256 签名。
2. `flags & 0x02`：带内容令牌扩展，格式为 `[4B扩展长度][2B token长度][2B fileId长度][token][fileId]`。
3. 最后是 `payloadLength` 指定的原始 UDP payload。

## 6. 平台数据与解密网关数据的差异

| 对比项 | 平台 | 解密网关 |
| --- | --- | --- |
| 传输协议 | HTTP JSON | UDP/TCP 二进制 |
| 数据来源 | 原始明文入站数据解析结果 | 原始数据封装 `Message` 后的密文，CatchAll 为原始 TCP 字节密文 |
| 是否包含业务字段 | 包含 `gatewayId(值为ruleId)/chainId/sourceIp/boardIp/rawPacket/protocol/contentType` 等 | 不包含这些字段 |
| 是否包含原始包 | `rawPacket` 是原始入站字节 Base64 | 不包含 Base64；Body 是原始字节或转码后字节 |
| 是否经过加密 | 不加密，由 HTTP 直接发送 | 已经 `cryptoService.encrypt(...)` |
| 失败影响 | 上报异常只记录日志，不影响转发 | 加密失败时当前包会被丢弃 |

## 7. 当前代码中的限制和需确认点

1. VAuth 国密密文内部结构无法从 Java 代码确认，只能确认 Java 侧传给 SDK 的明文是 `MessageBytes` 或 CatchAll 原始字节。
2. CatchAll 动态端口路径当前不调用 `DataReportService`，所以动态端口文件流不会按该路径直接上传平台。
3. 静态 TCP 每次 `channelRead` 组一个 `Message`，如果上游协议消息被拆成多个 TCP 读事件，平台解析和解密网关转发都按当前读到的字节块处理。
4. 当前默认转码是透传；如果未来真正启用转码，需要同步调整 `msgType/encoding`，否则 Header 仍显示透传/无编码。

## 8. 验证方式

1. UDP 验证：在解密网关侧解密单个 UDP payload，解密结果前 2 字节应为 `AB 01`，第 13-16 字节是当前 Body 长度。
2. TCP 验证：抓包看每帧前 4 字节是否等于后续密文字节数；解密后前 20 字节应符合 `MessageHeader`。
3. CatchAll 验证：连接建立后前 2 字节为原始目标端口；后续每帧前 4 字节为密文长度，解密后应直接是原始 TCP 业务字节。
4. 平台验证：观察内容服务 `/content/receive` 与 `/content/detection/detect` 日志，确认 JSON 中 `rawPacket` 为原始入站数据 Base64，`minioPath` 或 `screenshotBase64` 与内容类型匹配。
