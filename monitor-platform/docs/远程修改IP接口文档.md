# 远程配置接口文档

本文档供前端页面接入"远程配置"功能使用，涵盖 **远程修改 IP**、**远程升级** 和 **网关转发配置** 三类接口。

---

## 通用说明

### 响应格式

所有接口统一返回：

```json
{
  "code": 200,
  "msg": "success",
  "time": 1782540011454,
  "data": {}
}
```

### 通用状态码

| code | 含义 |
| --- | --- |
| 200 | 平台接口调用成功 |
| 非200 | 平台接口调用失败，展示 `msg` 错误信息 |

### 登录态

所有接口需要携带平台登录态，通过 `Authorization` 请求头传递。

---

# 一、远程修改 IP

> 通过 MQTT 向目标网关设备下发系统网络修改命令。当前版本不支持 `persist=true`（设备侧会拒绝），因此前端应将能力展示为"安全变更"，不要承诺一定完成系统持久化替换。

## 1.1 前置条件

- 设备已接入平台 MQTT 链路
- 设备侧运行 `gateway-udp-proxy` MQTT Agent
- 设备侧启用 `SYSTEM_NETWORK_CHANGE_ENABLED=true`
- 允许修改的网卡包含在 `SYSTEM_NETWORK_CHANGE_ALLOWED_INTERFACES` 中

## 1.2 通用 MQTT 命令状态

IP 修改和确认接口返回的 `data.status` 含义：

| 状态 | 含义 |
| --- | --- |
| `CREATED` | 平台已创建命令记录 |
| `PUBLISHED` | 平台已发布 MQTT 命令 |
| `PROCESSING` | 设备已收到并处理中 |
| `SUCCESS` | 设备执行成功 |
| `FAILED` | 设备执行失败 |
| `TIMEOUT` | 等待设备回执超时 |

**前端判断规则**：

- `code === 200` 表示平台接口调用成功
- `data.status === "SUCCESS"` 表示设备侧 MQTT 命令执行成功
- 非 `SUCCESS` 时展示 `msg` 或 `data.errorMessage`

---

## 1.3 下发修改目标 IP

```http
POST /gateway/network/change-ip
Content-Type: application/json
Authorization: <沿用平台登录态>
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `targetDeviceId` | string | 是 | 目标设备编号，通用字段，不限定网关设备 |
| `gatewayDeviceId` | string | 否 | 旧字段，仅用于兼容历史调用；新页面不要使用 |
| `interfaceName` | string | 是 | 网卡名，例如 `enp1s0` |
| `newIp` | string | 是 | 目标 IP，例如 `192.168.1.25` |
| `prefixLength` | number | 是 | 掩码长度，取值 `1-32`，常用 `24` |
| `gateway` | string | 否 | 默认网关。`applyDefaultRoute=true` 时必填 |
| `applyDefaultRoute` | boolean | 否 | 是否替换默认路由，默认 `false` |
| `dryRun` | boolean | 否 | 是否只校验不执行，默认 `false` |
| `persist` | boolean | 否 | 当前设备侧不支持，前端固定传 `false` |
| `rollbackSeconds` | number | 否 | 回滚窗口秒数，默认 `300`。建议 `600-1200` |
| `changeId` | string | 建议 | 本次变更 ID，用于后续确认。建议前端生成并保存 |
| `operator` | string | 否 | 操作人账号 |
| `remark` | string | 否 | 备注 |

### Dry Run 示例（仅校验）

```json
{
  "targetDeviceId": "publish-gateway-001",
  "interfaceName": "enp1s0",
  "newIp": "192.168.1.25",
  "prefixLength": 24,
  "applyDefaultRoute": false,
  "dryRun": true,
  "persist": false,
  "rollbackSeconds": 600,
  "changeId": "ip-change-20260627-001",
  "operator": "admin"
}
```

### 正式下发示例

```json
{
  "targetDeviceId": "publish-gateway-001",
  "interfaceName": "enp1s0",
  "newIp": "192.168.1.25",
  "prefixLength": 24,
  "gateway": "192.168.1.3",
  "applyDefaultRoute": true,
  "dryRun": false,
  "persist": false,
  "rollbackSeconds": 900,
  "changeId": "ip-change-20260627-001",
  "operator": "admin",
  "remark": "现场网关 IP 调整"
}
```

### 成功响应示例

```json
{
  "code": 200,
  "msg": "MQTT命令执行成功",
  "time": 1782539711145,
  "data": {
    "commandId": 12,
    "messageId": "22b273547441422d88c57798670ceca8",
    "targetDeviceId": "publish-gateway-001",
    "gatewayDeviceId": "publish-gateway-001",
    "command": "CHANGE_SYSTEM_IP",
    "status": "SUCCESS",
    "errorMessage": null,
    "ackTime": "2026-06-27T13:55:11",
    "updateTime": "2026-06-27T13:55:11"
  }
}
```

### 响应 `data` 字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `commandId` | number | 命令记录 ID |
| `messageId` | string | MQTT 消息 ID |
| `targetDeviceId` | string | 目标设备编号 |
| `gatewayDeviceId` | string | 网关设备编号 |
| `command` | string | 命令类型，此处为 `CHANGE_SYSTEM_IP` |
| `status` | string | 命令执行状态 |
| `errorMessage` | string | 错误信息，成功时为 `null` |
| `ackTime` | string | 设备确认时间 |
| `updateTime` | string | 记录更新时间 |

---

## 1.4 确认 IP 变更

设备侧设置了回滚窗口时，前端应在确认设备可达后调用确认接口。确认后设备侧删除回滚标记，不再自动回滚。

```http
POST /gateway/network/confirm-ip
Content-Type: application/json
Authorization: <沿用平台登录态>
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `targetDeviceId` | string | 是 | 目标设备编号，通用字段，不限定网关设备 |
| `gatewayDeviceId` | string | 否 | 旧字段，仅用于兼容历史调用；新页面不要使用 |
| `changeId` | string | 是 | 与修改 IP 接口的 `changeId` 保持一致 |
| `operator` | string | 否 | 操作人账号 |

### 请求示例

```json
{
  "targetDeviceId": "publish-gateway-001",
  "changeId": "ip-change-20260627-001",
  "operator": "admin"
}
```

### 成功响应示例

```json
{
  "code": 200,
  "msg": "MQTT命令执行成功",
  "time": 1782540011454,
  "data": {
    "commandId": 13,
    "messageId": "7068881765884c0d8b04d556dfff91bc",
    "targetDeviceId": "publish-gateway-001",
    "gatewayDeviceId": "publish-gateway-001",
    "command": "CONFIRM_SYSTEM_IP",
    "status": "SUCCESS",
    "errorMessage": null,
    "ackTime": "2026-06-27T14:00:11",
    "updateTime": "2026-06-27T14:00:11"
  }
}
```

### 响应 `data` 字段说明

同 [1.3 响应 data 字段说明](#响应-data-字段说明)，`command` 字段值为 `CONFIRM_SYSTEM_IP`。

---

## 1.5 前端交互建议（IP 修改流程）

推荐页面流程：

1. 用户选择目标设备
2. 填写网卡名、目标 IP、掩码、默认网关
3. 先调用 `dryRun=true` 校验
4. Dry Run 成功后允许点击"下发修改"
5. 下发成功后页面进入"待确认"状态，展示倒计时 `rollbackSeconds`
6. 前端提示用户确认设备新 IP 可访问
7. 用户确认后调用 `/gateway/network/confirm-ip`

页面需要明确提示：

- 修改 IP 可能导致旧 IP 连接断开
- 当前接口不负责删除旧 IP 和持久化正式割接
- `rollbackSeconds` 到期前未确认，设备侧可能自动回滚
- 若命令返回 `TIMEOUT` 或 `FAILED`，不要自动确认

### 前端字段映射

| 页面字段 | 接口字段 | 示例 |
| --- | --- | --- |
| 目标设备 | `targetDeviceId` | `publish-gateway-001` |
| 网卡名称 | `interfaceName` | `enp1s0` |
| 目标 IP | `newIp` | `192.168.1.25` |
| 子网掩码 | `prefixLength` | `24` |
| 默认网关 | `gateway` | `192.168.1.3` |
| 是否切默认路由 | `applyDefaultRoute` | `true` |
| 回滚窗口 | `rollbackSeconds` | `900` |
| 操作人 | `operator` | `admin` |
| 备注 | `remark` | `现场网关 IP 调整` |

### 注意事项

- `changeId` 建议由前端生成，例如 `ip-change-${yyyyMMddHHmmss}-${deviceId}`，并在待确认状态中缓存
- 前端提交 `persist` 固定为 `false`
- 设备列表中的 IP 更新依赖设备重新注册或心跳逻辑，不能仅凭接口返回立刻认为设备台账已刷新

---

