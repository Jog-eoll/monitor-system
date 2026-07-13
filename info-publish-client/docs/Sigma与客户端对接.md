# Sigma 信发软件与安全发布客户端对接接口文档

版本：v1.4  
日期：2026-06-12  
适用范围：Sigma 信发软件、安全发布客户端

## 1. 对接目标

安全发布客户端不接管 Sigma 的内容加密逻辑，也不参与 Sigma 的 JetFile 封包、UDP 分包逻辑。

本次对接目标调整为“发送前验签”：

1. Sigma 在正式发包前调用安全发布客户端预检查接口。
2. 安全发布客户端在预检查流程中完成 UKey、客户端状态、Sigma 进程、网关链路检查。
3. 安全发布客户端从 Sigma 获取当前待播放列表、播放文件和内部文件字节。
4. 安全发布客户端对需要验签的内部文件执行发送前验签。
5. 验签通过后，客户端返回 `publishAllowed=true`，Sigma 才能继续原有 JetFile 封包和 UDP 发送。
6. 验签失败时，客户端返回 `publishAllowed=false`，Sigma 必须停止本次发布。

本版本不设计独立文件签名接口，也不设计独立验签接口。文件验签统一并入安全发布客户端 `precheck` 流程，Sigma 只根据 `publishAllowed` 决定是否继续发布。

## 2. 术语定义

| 术语 | 字段名 | 说明 |
|---|---|---|
| 待播放列表 | `playlist` | Sigma 当前准备发布的一组播放文件 |
| 待播放列表 ID | `playlistId` | 待播放列表唯一标识 |
| 待播放列表版本 | `playlistVersion` | 用于防止验签过程中内容变化 |
| 播放文件 | `playFile` | 待播放列表中的文件项，例如 `.Nmg` |
| 内部文件 | `innerFile` | 播放文件内部实际会被发送的文件，例如图片、视频、配置文件 |
| 验签策略 | `verificationProfile` | 文件验签策略，例如 `GENERIC_FILE_VERIFY_V1` |
| 签名来源 | `signatureSource` | 签名信息来源，例如内嵌、元数据、旁路文件 |

约定：

1. 接口不使用“任务”作为核心概念，统一使用“待播放列表”。
2. 安全发布客户端真正处理的是内部文件，不是播放文件整包，也不是 JetFile 包。
3. 文件验签细节由安全发布客户端内部处理，对接接口不单独暴露验签内部计算字段。
4. 非必须字段统一放入 `extensions`，双方未约定时接收方应忽略。

## 3. 对接边界

| 模块 | 负责内容 |
|---|---|
| Sigma | 维护待播放列表、解析播放文件内部文件、导出内部文件字节、提供签名元数据、继续 JetFile 封包和 UDP 发送 |
| 安全发布客户端 | UKey 检查、客户端认证检查、Sigma 进程检查、网关链路检查、获取待播放数据、发送前验签、返回是否允许发布 |

Sigma 不需要提供：

1. 内容加密接口。
2. SM4 加密接口。
3. 解密接口。
4. publish-gateway 转发接口。
5. terminal-gateway 通信接口。
6. 文件内容替换接口。

## 4. 对接形式

| 项目 | 设计 |
|---|---|
| 通信方式 | HTTP |
| 调用范围 | 本机回环地址 `127.0.0.1` |
| Sigma 接口根地址 | `http://127.0.0.1:{sigmaPort}/api/secure-publish` |
| 客户端接口根地址 | `http://127.0.0.1:{clientPort}`，默认可使用 `7080` |
| JSON 编码 | UTF-8 |
| 文件传输 | `application/octet-stream` |
| 发布策略 | 任一必验文件验签失败，则禁止发布 |

## 5. 总体流程

1. 用户在 Sigma 点击发布。
2. Sigma 调用安全发布客户端 `POST /api/publish/precheck`。
3. 客户端检查 UKey、客户端认证、Sigma 进程绑定、网关链路状态。
4. 客户端调用 Sigma `GET /current-playlist` 获取当前待播放列表。
5. 客户端调用 Sigma `POST /playlists/{playlistId}/lock` 锁定待播放列表。
6. 客户端调用 Sigma `GET /playlists/{playlistId}/inner-files` 获取内部文件清单。
7. 客户端下载 `verificationRequired=true` 的内部文件内容。
8. 客户端获取或提取该内部文件对应的签名信息。
9. 客户端在本机完成发送前验签。
10. 客户端调用 Sigma `POST /playlists/{playlistId}/unlock` 解锁待播放列表。
11. 客户端向 Sigma 返回 `publishAllowed=true` 或 `publishAllowed=false`。
12. Sigma 仅在 `publishAllowed=true` 时继续发包。

## 6. 公共协议

### 6.1 公共请求头

| Header | 必填 | 说明 |
|---|---|---|
| `X-SP-Client-Id` | 是 | 调用方 ID，例如 `sigma-client`、`secure-publish-client` |
| `X-SP-Request-Id` | 是 | 请求唯一 ID |
| `X-SP-Timestamp` | 是 | ISO8601 时间 |
| `Authorization` | 建议 | `Bearer {token}`，用于本机接口调用鉴权 |

说明：公共请求头只用于接口调用鉴权，不参与文件验签。文件验签统一放在客户端 `precheck` 流程内完成。

### 6.2 公共响应

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "requestId": "SP-REQ-20260612-000001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `success` | boolean | 是 | 接口是否成功 |
| `code` | string | 否 | 业务码 |
| `message` | string | 否 | 说明 |
| `requestId` | string | 否 | 请求 ID |
| `extensions` | object | 否 | 扩展字段 |

## 7. Sigma 调用安全发布客户端 API

### 7.1 发布前预检查和发送前验签

```http
POST /api/publish/precheck
Content-Type: application/json;charset=UTF-8
```

作用：Sigma 在正式发布前调用。该接口内部包含状态预检查和发送前验签，Sigma 必须等待该接口返回。

#### 请求字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `requestId` | string | 是 | 本次发布请求 ID，需支持幂等 |
| `sigmaBaseUrl` | string | 是 | Sigma 接口根地址 |
| `playlistId` | string | 否 | 待播放列表 ID；为空时客户端调用 Sigma 获取 |
| `timeoutMs` | integer | 否 | 整体超时时间，默认 `600000` |

#### 请求示例

```json
{
  "requestId": "SIGMA-PUBLISH-20260612-000001",
  "sigmaBaseUrl": "http://127.0.0.1:18080/api/secure-publish",
  "playlistId": "SIGMA-PLAYLIST-20260612-000001",
  "timeoutMs": 600000
}
```

#### 响应字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `success` | boolean | 是 | 接口是否执行成功 |
| `publishAllowed` | boolean | 是 | 是否允许 Sigma 继续发布 |
| `result` | string | 是 | `PASS`、`FAILED` |
| `playlistId` | string | 是 | 待播放列表 ID |
| `checks` | object | 是 | 基础检查结果 |
| `verifyResult` | object | 是 | 发送前验签结果 |
| `message` | string | 否 | 说明 |

#### `checks` 字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `ukeyAuthenticated` | boolean | 是 | UKey 是否认证 |
| `clientAuthenticated` | boolean | 是 | 客户端是否认证 |
| `sigmaProcessBound` | boolean | 是 | Sigma 进程是否绑定 |
| `gatewayReady` | boolean | 是 | 网关链路是否可用 |

#### `verifyResult` 字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `checkedCount` | integer | 是 | 已验签内部文件数量 |
| `passedCount` | integer | 是 | 验签通过数量 |
| `failedCount` | integer | 是 | 验签失败数量 |
| `passedFiles` | array | 是 | 验签通过文件信息 |
| `failedFiles` | array | 是 | 验签失败文件信息 |

#### `passedFiles[]`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `innerFileId` | string | 是 | 内部文件 ID |
| `fileName` | string | 是 | 内部文件名 |
| `verificationProfile` | string | 是 | 验签策略 |

#### `failedFiles[]`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `innerFileId` | string | 是 | 内部文件 ID |
| `fileName` | string | 否 | 内部文件名 |
| `stage` | string | 是 | 失败阶段 |
| `code` | string | 是 | 错误码 |
| `message` | string | 是 | 错误说明 |

#### 响应示例：通过

```json
{
  "success": true,
  "publishAllowed": true,
  "result": "PASS",
  "playlistId": "SIGMA-PLAYLIST-20260612-000001",
  "checks": {
    "ukeyAuthenticated": true,
    "clientAuthenticated": true,
    "sigmaProcessBound": true,
    "gatewayReady": true
  },
  "verifyResult": {
    "checkedCount": 1,
    "passedCount": 1,
    "failedCount": 0,
    "passedFiles": [
      {
        "innerFileId": "INNER-FILE-IMG-001",
        "fileName": "ad.jpg",
        "verificationProfile": "GENERIC_FILE_VERIFY_V1"
      }
    ],
    "failedFiles": []
  },
  "message": "发送前检查和验签通过，允许发布"
}
```

#### 响应示例：失败

```json
{
  "success": true,
  "publishAllowed": false,
  "result": "FAILED",
  "playlistId": "SIGMA-PLAYLIST-20260612-000001",
  "checks": {
    "ukeyAuthenticated": true,
    "clientAuthenticated": true,
    "sigmaProcessBound": true,
    "gatewayReady": true
  },
  "verifyResult": {
    "checkedCount": 1,
    "passedCount": 0,
    "failedCount": 1,
    "passedFiles": [],
    "failedFiles": [
      {
        "innerFileId": "INNER-FILE-IMG-001",
        "fileName": "ad.jpg",
        "stage": "VERIFY",
        "code": "SIGNATURE_VERIFY_FAILED",
        "message": "文件签名验签失败"
      }
    ]
  },
  "message": "发送前验签失败，禁止发布"
}
```

## 8. 安全发布客户端调用 Sigma API

### 8.1 获取当前待播放列表

```http
GET /api/secure-publish/current-playlist
Accept: application/json
```

#### 响应字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `success` | boolean | 是 | 是否成功 |
| `playlistId` | string | 是 | 待播放列表 ID |
| `playlistVersion` | integer | 是 | 待播放列表版本 |
| `status` | string | 是 | `READY`、`LOCKED`、`PUBLISHING` |
| `playFiles` | array | 是 | 播放文件列表 |

#### `playFiles[]`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `playFileId` | string | 是 | 播放文件 ID |
| `fileName` | string | 是 | 播放文件名称 |
| `canExportInnerFiles` | boolean | 是 | 是否可导出内部文件 |

#### 响应示例

```json
{
  "success": true,
  "playlistId": "SIGMA-PLAYLIST-20260612-000001",
  "playlistVersion": 1,
  "status": "READY",
  "playFiles": [
    {
      "playFileId": "PLAY-FILE-001",
      "fileName": "暴力.Nmg",
      "canExportInnerFiles": true
    }
  ]
}
```

### 8.2 锁定待播放列表

```http
POST /api/secure-publish/playlists/{playlistId}/lock
Content-Type: application/json;charset=UTF-8
```

#### 请求字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `requestId` | string | 是 | 请求 ID |
| `clientId` | string | 是 | 固定建议 `secure-publish-client` |
| `playlistVersion` | integer | 是 | 客户端看到的待播放列表版本 |
| `lockTimeoutMs` | integer | 否 | 锁超时，默认 `300000` |

#### 响应字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `success` | boolean | 是 | 是否成功 |
| `playlistId` | string | 是 | 待播放列表 ID |
| `lockId` | string | 是 | 锁 ID |
| `expireAt` | string | 是 | 锁过期时间 |

### 8.3 获取内部文件清单

```http
GET /api/secure-publish/playlists/{playlistId}/inner-files
Accept: application/json
```

#### 响应字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `success` | boolean | 是 | 是否成功 |
| `playlistId` | string | 是 | 待播放列表 ID |
| `playlistVersion` | integer | 是 | 待播放列表版本 |
| `innerFiles` | array | 是 | 内部文件列表 |

#### `innerFiles[]`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `innerFileId` | string | 是 | 内部文件 ID |
| `playFileId` | string | 是 | 所属播放文件 ID |
| `fileName` | string | 是 | 内部文件名 |
| `entryPath` | string | 是 | 播放文件内路径 |
| `mimeType` | string | 是 | MIME 类型 |
| `size` | long | 是 | 文件大小 |
| `verificationRequired` | boolean | 是 | 是否需要发送前验签 |
| `verificationProfile` | string | verificationRequired=true 时必填 | 验签策略 |
| `signatureSource` | string | verificationRequired=true 时必填 | 签名来源 |

#### `signatureSource` 枚举

| 值 | 说明 |
|---|---|
| `EMBEDDED` | 签名内嵌在文件内部，客户端从文件中提取 |
| `METADATA` | 签名由 Sigma 元数据接口提供 |
| `SIDECAR_FILE` | 签名在旁路签名文件中 |

#### 响应示例

```json
{
  "success": true,
  "playlistId": "SIGMA-PLAYLIST-20260612-000001",
  "playlistVersion": 1,
  "innerFiles": [
    {
      "innerFileId": "INNER-FILE-IMG-001",
      "playFileId": "PLAY-FILE-001",
      "fileName": "ad.jpg",
      "entryPath": "materials/images/ad.jpg",
      "mimeType": "image/jpeg",
      "size": 125678,
      "verificationRequired": true,
      "verificationProfile": "GENERIC_FILE_VERIFY_V1",
      "signatureSource": "METADATA"
    },
    {
      "innerFileId": "INNER-FILE-CFG-001",
      "playFileId": "PLAY-FILE-001",
      "fileName": "program.xml",
      "entryPath": "program/program.xml",
      "mimeType": "application/xml",
      "size": 4096,
      "verificationRequired": false
    }
  ]
}
```

### 8.4 下载内部文件内容

```http
GET /api/secure-publish/playlists/{playlistId}/files/{innerFileId}/content
Accept: application/octet-stream
```

#### 响应头

| Header | 必填 | 说明 |
|---|---|---|
| `Content-Type` | 是 | `application/octet-stream` |
| `Content-Length` | 是 | 文件字节长度 |
| `X-SP-Inner-File-Id` | 是 | 内部文件 ID |

响应体为原始内部文件二进制字节。

要求：

1. 返回字节必须是 Sigma 后续封包使用的原始字节。
2. 客户端使用该响应体作为发送前验签的输入。

### 8.5 获取内部文件签名元数据

```http
GET /api/secure-publish/playlists/{playlistId}/files/{innerFileId}/signature-metadata
Accept: application/json
```

仅当 `signatureSource=METADATA` 时必需。

#### 响应字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `success` | boolean | 是 | 是否成功 |
| `innerFileId` | string | 是 | 内部文件 ID |
| `verificationProfile` | string | 是 | 验签策略 |
| `manifest` | object | 是 | 被签名的清单 |
| `signature` | string | 是 | 签名值或签名信封 Base64 |

#### `manifest` 最小字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `playlistId` | string | 是 | 待播放列表 ID |
| `innerFileId` | string | 是 | 内部文件 ID |
| `fileName` | string | 是 | 内部文件名 |
| `signTime` | long | 是 | 签名时间，毫秒时间戳 |
| `algorithm` | string | 是 | 签名算法 |

#### 响应示例

```json
{
  "success": true,
  "innerFileId": "INNER-FILE-IMG-001",
  "verificationProfile": "GENERIC_FILE_VERIFY_V1",
  "manifest": {
    "playlistId": "SIGMA-PLAYLIST-20260612-000001",
    "innerFileId": "INNER-FILE-IMG-001",
    "fileName": "ad.jpg",
    "signTime": 1781231400000,
    "algorithm": "SM2-SIGN"
  },
  "signature": "base64-signature-envelope"
}
```

### 8.6 解锁待播放列表

```http
POST /api/secure-publish/playlists/{playlistId}/unlock
Content-Type: application/json;charset=UTF-8
```

#### 请求字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `requestId` | string | 是 | 请求 ID |
| `lockId` | string | 是 | 锁 ID |
| `reason` | string | 否 | `DONE`、`FAILED`、`TIMEOUT` |

#### 响应字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `success` | boolean | 是 | 是否成功 |
| `unlocked` | boolean | 是 | 是否已解锁 |

## 9. 客户端发送前验签流程

客户端对每个 `verificationRequired=true` 的内部文件执行以下流程：

1. 下载内部文件字节。
2. 根据 `signatureSource` 获取签名信息：
   - `EMBEDDED`：从文件内部提取签名信息。
   - `METADATA`：调用 `signature-metadata` 接口获取。
   - `SIDECAR_FILE`：下载旁路签名文件后提取。
3. 使用内部文件字节和签名信息完成本地验签。
4. 校验 `manifest.playlistId` 是否等于当前待播放列表 ID。
5. 校验 `manifest.innerFileId` 是否等于当前内部文件 ID。
6. 校验签名时间、证书、算法、策略等是否符合本地安全策略。
7. 任一必验文件失败，则本次预检查失败。

## 10. 枚举

### 10.1 待播放列表状态

| 值 | 说明 |
|---|---|
| `READY` | 待发布，允许预检查 |
| `LOCKED` | 已锁定 |
| `PUBLISHING` | 正在发布 |
| `DONE` | 发布完成 |
| `FAILED` | 发布失败 |

### 10.2 验签策略

| 值 | 说明 |
|---|---|
| `GENERIC_FILE_VERIFY_V1` | 通用文件验签，默认建议使用 |

### 10.3 失败阶段

| 值 | 说明 |
|---|---|
| `BASIC_CHECK` | UKey、认证、进程、网关等基础检查 |
| `CURRENT_PLAYLIST` | 获取当前待播放列表 |
| `LOCK` | 锁定待播放列表 |
| `LIST_INNER_FILES` | 获取内部文件清单 |
| `DOWNLOAD` | 下载内部文件 |
| `LOAD_SIGNATURE` | 获取或提取签名信息 |
| `VERIFY` | 验签 |
| `UNLOCK` | 解锁待播放列表 |

### 10.4 常见失败码

| 值 | 说明 |
|---|---|
| `UKEY_NOT_AUTHENTICATED` | UKey 未认证 |
| `CLIENT_NOT_AUTHENTICATED` | 客户端未认证 |
| `SIGMA_PROCESS_NOT_BOUND` | Sigma 进程未绑定 |
| `GATEWAY_NOT_READY` | 网关链路不可用 |
| `PLAYLIST_NOT_READY` | 待播放列表状态不允许发布 |
| `PLAYLIST_VERSION_CHANGED` | 待播放列表版本变化 |
| `SIGNATURE_MISSING` | 签名信息缺失 |
| `SIGNATURE_VERIFY_FAILED` | 签名验签失败 |
| `SIGNATURE_CONTEXT_MISMATCH` | 签名上下文不匹配 |
| `VERIFY_POLICY_REJECTED` | 本地验签策略拒绝 |
| `TIMEOUT` | 处理超时 |

## 11. 最小接口清单

Sigma 至少提供：

```http
GET  /api/secure-publish/current-playlist
POST /api/secure-publish/playlists/{playlistId}/lock
GET  /api/secure-publish/playlists/{playlistId}/inner-files
GET  /api/secure-publish/playlists/{playlistId}/files/{innerFileId}/content
GET  /api/secure-publish/playlists/{playlistId}/files/{innerFileId}/signature-metadata   # signatureSource=METADATA 时需要
POST /api/secure-publish/playlists/{playlistId}/unlock
```

安全发布客户端至少提供：

```http
POST /api/publish/precheck
```

## 12. Sigma 必须确认的问题

| 序号 | 问题 | 原因 |
|---|---|---|
| 1 | 是否能解析播放文件内部文件清单 | 客户端需要知道哪些文件需要验签 |
| 2 | 下载接口返回的字节是否就是最终封包字节 | 验签必须基于最终发送内容 |
| 3 | 哪些内部文件必须验签 | 决定 `verificationRequired` |
| 4 | 签名来源是 `EMBEDDED`、`METADATA` 还是 `SIDECAR_FILE` | 决定客户端如何获取签名 |
| 5 | 如果签名来自元数据，Sigma 是否能提供 `signature-metadata` 接口 | 客户端验签需要签名清单和签名值 |
| 6 | 是否支持锁定和解锁待播放列表 | 防止验签过程中内容变化 |
| 7 | 是否能等待客户端预检查返回后再正式发包 | 防止验签未完成就发送 |

## 13. 验收标准

1. UKey 未认证时，`precheck.publishAllowed=false`。
2. Sigma 调用 `precheck` 后必须等待返回。
3. 客户端能获取、锁定、解锁待播放列表。
4. 客户端能获取内部文件清单。
5. 客户端能下载并校验 `verificationRequired=true` 的内部文件。
6. 客户端能按 `signatureSource` 获取或提取签名信息。
7. 客户端能在发送前完成验签。
8. 任一必验文件验签失败时，`publishAllowed=false`。
9. 所有必验文件验签通过时，`publishAllowed=true`。
10. Sigma 只在 `publishAllowed=true` 时继续 JetFile 封包和 UDP 发送。
