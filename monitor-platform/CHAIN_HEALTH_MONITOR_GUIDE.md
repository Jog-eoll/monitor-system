# 链路健康度监控使用指南

## 概述

链路健康度监控模块用于实时检测任务链路中所有节点的运行状态，自动计算健康度评分，并对异常/离线节点提供详细的故障信息（含告警次数、告警类型等），帮助运维人员快速定位和处理故障。

## 核心功能

- **单链路健康检查**：遍历链路所有节点，探测设备实际在线状态，计算健康度评分
- **批量链路巡检**：一键检查所有启用链路的健康状态
- **定时自动巡检**：每 10 分钟自动执行一次全量链路健康度检查
- **异常节点详情**：异常节点返回具体的告警信息（告警次数、告警类型、告警时间等）

## 相关配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `monitor.device.url` | `http://localhost:8062` | 设备服务基础地址，用于探测设备状态 |
| 定时巡检间隔 | 10 分钟 | `ChainStatusSyncTask.healthCheckAllChains()` |
| 定时状态同步间隔 | 1 小时 | `ChainStatusSyncTask.syncAllChainStatus()` |

## API 接口

### 1. 检查单条链路健康度

```
GET /forward/chain/health/{chainId}
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| chainId | Long | 是 | 链路ID |

**请求示例：**

```bash
curl -X GET http://localhost:8080/forward/chain/health/1
```

**响应示例（全部正常）：**

```json
{
  "code": 200,
  "message": "健康度检查完成: 100分(A级), 在线5/总5",
  "data": {
    "chainId": 1,
    "chainName": "链路 A",
    "chainCode": "CHAIN-001",
    "healthScore": 100,
    "healthLevel": "A",
    "status": 1,
    "totalNodes": 5,
    "onlineNodes": 5,
    "offlineNodes": 0,
    "errorNodes": 0,
    "coreOnlineNodes": 4,
    "coreTotalNodes": 4,
    "unhealthyNodes": [],
    "checkTime": "2026-05-29 10:00:00",
    "checkDurationMs": 320,
    "enabled": 1
  }
}
```

**响应示例（包含异常节点）：**

```json
{
  "code": 200,
  "message": "健康度检查完成: 72分(B级), 在线3/总5",
  "data": {
    "chainId": 1,
    "chainName": "链路 A",
    "chainCode": "CHAIN-001",
    "healthScore": 72,
    "healthLevel": "B",
    "status": 2,
    "totalNodes": 5,
    "onlineNodes": 3,
    "offlineNodes": 1,
    "errorNodes": 1,
    "coreOnlineNodes": 3,
    "coreTotalNodes": 4,
    "unhealthyNodes": [
      {
        "nodeId": 10,
        "deviceId": "IB-001",
        "deviceName": "情报板01",
        "deviceType": "info_board",
        "deviceTypeDesc": "情报板",
        "deviceIp": "192.168.1.100",
        "nodeStatus": "异常",
        "coreNode": true,
        "statusDesc": "设备状态: 告警; 累计告警 3 次; 最近告警类型: content_violation; 最近告警时间: 2026-05-29 09:30:00",
        "errorDetail": "设备状态: 告警; 累计告警 3 次; 最近告警类型: content_violation; 最近告警时间: 2026-05-29 09:30:00",
        "alarmCount": 3,
        "lastAlarmTime": "2026-05-29 09:30:00",
        "lastAlarmType": "content_violation"
      },
      {
        "nodeId": 11,
        "deviceId": "CS-001",
        "deviceName": "内容识别服务器01",
        "deviceType": "content_server",
        "deviceTypeDesc": "内容识别服务器",
        "deviceIp": "192.168.1.101",
        "nodeStatus": "离线",
        "coreNode": false,
        "statusDesc": "设备无响应或已断开连接",
        "errorDetail": null,
        "alarmCount": null,
        "lastAlarmTime": null,
        "lastAlarmType": null
      }
    ],
    "checkTime": "2026-05-29 10:00:00",
    "checkDurationMs": 450,
    "enabled": 1
  }
}
```

---

### 2. 批量检查所有链路健康度

```
POST /forward/chain/health/all
```

**请求示例：**

```bash
curl -X POST http://localhost:8080/forward/chain/health/all
```

**响应示例：**

```json
{
  "code": 200,
  "message": "批量健康度检查完成",
  "data": {
    "checkCount": 10
  }
}
```

---

## 数据字段说明

### ChainHealthVO（链路健康度结果）

| 字段 | 类型 | 说明 |
|------|------|------|
| `chainId` | Long | 链路ID |
| `chainName` | String | 链路名称 |
| `chainCode` | String | 链路编码 |
| `healthScore` | Integer | 健康度评分（0-100） |
| `healthLevel` | String | 健康等级：A/B/C/D/F |
| `status` | Integer | 链路状态：0-离线, 1-在线, 2-部分可用 |
| `totalNodes` | Integer | 总节点数 |
| `onlineNodes` | Integer | 在线节点数 |
| `offlineNodes` | Integer | 离线节点数 |
| `errorNodes` | Integer | 异常节点数 |
| `coreOnlineNodes` | Integer | 核心节点在线数 |
| `coreTotalNodes` | Integer | 核心节点总数 |
| `unhealthyNodes` | List | 不健康节点列表（离线/异常） |
| `checkTime` | String | 检查时间 |
| `checkDurationMs` | Long | 检查耗时（毫秒） |
| `enabled` | Integer | 是否启用：0-禁用, 1-启用 |

### UnhealthyNodeInfo（不健康节点信息）

| 字段 | 类型 | 说明 |
|------|------|------|
| `nodeId` | Long | 节点ID |
| `deviceId` | String | 设备唯一标识 |
| `deviceName` | String | 设备名称 |
| `deviceType` | String | 设备类型编码 |
| `deviceTypeDesc` | String | 设备类型中文描述 |
| `deviceIp` | String | 设备IP地址 |
| `nodeStatus` | String | 节点状态：`离线` / `异常` |
| `coreNode` | Boolean | 是否为核心节点 |
| `statusDesc` | String | 状态描述 |
| `errorDetail` | String | **异常详情**（仅异常节点有值） |
| `alarmCount` | Integer | **告警次数**（仅异常节点有值） |
| `lastAlarmTime` | String | **最后告警时间**（仅异常节点有值） |
| `lastAlarmType` | String | **最后告警类型**（仅异常节点有值） |

> **注意：** `errorDetail`、`alarmCount`、`lastAlarmTime`、`lastAlarmType` 四个字段仅在 `nodeStatus = "异常"` 时填充，离线节点这些字段为 `null`。

## 健康度评分规则

### 评分计算

| 组成 | 说明 |
|------|------|
| **基础分** | `在线节点数 / 总节点数 × 100` |
| **核心节点奖励** | 核心节点全部在线额外 +10 分，部分在线按比例计算 |
| **异常节点扣分** | 每个异常节点额外 -5 分 |

最终分数限制在 **[0, 100]** 范围内。

### 健康等级

| 等级 | 评分范围 | 含义 |
|------|----------|------|
| A | ≥ 90 | 优秀 |
| B | ≥ 70 | 良好 |
| C | ≥ 50 | 一般 |
| D | ≥ 30 | 较差 |
| F | < 30 | 故障 |

## 节点状态判定逻辑

健康检查通过调用设备服务接口 `GET /device/unified/{deviceType}/{deviceId}/detail` 获取设备实时状态：

| 设备服务返回 status | 判定结果 | 说明 |
|---------------------|----------|------|
| `在线` | 在线 | 设备正常运行 |
| `告警` | 异常 | 设备告警中，附带告警详情 |
| 其他 / 接口不通 | 离线 | 设备无响应或不可达 |

> 当 RestTemplate 未配置或设备服务不可用时，回退使用节点数据库中存储的状态。

## 核心节点类型定义

| 分类 | 设备类型 | 编码 |
|------|----------|------|
| 主节点核心 | 信息发布服务器 | `publish_server` |
| 主节点核心 | 发布端加密网关 | `publish_gateway` |
| 分支核心 | 终端加密网关 | `terminal_encrypt_gateway` |
| 分支核心 | 情报板 | `info_board` |
| 可选设备 | 内容识别服务器 | `content_server` |

## 定时任务

| 任务 | 执行频率 | 说明 |
|------|----------|------|
| 链路状态同步 | 每 1 小时 | 根据节点状态汇总更新链路状态 |
| 链路健康度巡检 | 每 10 分钟（延迟1分钟启动） | 对所有启用链路执行健康度检查 |

定时任务类：`ChainStatusSyncTask`（位于 `monitor-platform-forward` 模块）

## 前端使用建议

### 1. 链路列表页展示健康度

在链路列表接口 `POST /forward/chain/page` 返回的 `TaskChainVO` 中已包含 `healthScore`、`healthLevel`、`onlineNodes`、`offlineNodes`、`errorNodes` 字段，可直接用于列表展示。

### 2. 链路详情页展示异常节点

调用 `GET /forward/chain/health/{chainId}` 获取完整的健康检查结果，重点关注 `unhealthyNodes` 数组：

```javascript
// 示例：处理异常节点信息
const healthData = response.data;

healthData.unhealthyNodes.forEach(node => {
  if (node.nodeStatus === '异常') {
    // 异常节点有详细的告警信息
    console.log(`设备 ${node.deviceName} 异常:`);
    console.log(`  告警次数: ${node.alarmCount}`);
    console.log(`  告警类型: ${node.lastAlarmType}`);
    console.log(`  告警时间: ${node.lastAlarmTime}`);
    console.log(`  异常详情: ${node.errorDetail}`);
  } else if (node.nodeStatus === '离线') {
    // 离线节点无告警详情
    console.log(`设备 ${node.deviceName} 离线`);
  }
});
```

### 3. 健康度等级颜色映射建议

| 等级 | 建议颜色 | 用途 |
|------|----------|------|
| A | 绿色 `#52c41a` | 状态优秀 |
| B | 蓝色 `#1890ff` | 状态良好 |
| C | 橙色 `#faad14` | 需要关注 |
| D | 橙红 `#fa541c` | 需要处理 |
| F | 红色 `#f5222d` | 严重故障 |

## 相关接口汇总

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 链路健康检查 | GET | `/forward/chain/health/{chainId}` | 检查单条链路 |
| 批量健康检查 | POST | `/forward/chain/health/all` | 检查所有启用链路 |
| 链路分页列表 | POST | `/forward/chain/page` | 列表含健康度评分 |
| 链路树形结构 | GET | `/forward/chain/tree/{chainId}` | 含节点状态 |
| 同步链路状态 | POST | `/forward/chain/sync/status/{chainId}` | 手动同步状态 |
| 批量同步状态 | POST | `/forward/chain/sync/all` | 手动批量同步 |
| 启用/停用链路 | POST | `/forward/chain/enable/{chainId}?enabled=0\|1` | 含网关联动 |

## 注意事项

1. **设备服务依赖**：健康检查依赖设备服务 (`monitor-device`) 提供的设备状态接口，请确保设备服务正常运行
2. **超时控制**：单节点探测超时不影响其他节点，超时后回退使用数据库存储状态
3. **性能考量**：批量检查为串行执行，链路数量较多时耗时较长，建议通过定时任务自动巡检
4. **状态延迟**：节点状态依赖设备服务实时数据，若设备服务数据滞后，健康检查结果也会相应滞后
5. **异常详情来源**：异常节点的 `alarmCount`、`lastAlarmTime`、`lastAlarmType` 来自设备服务的 `DeviceDetailDTO`，需确保设备服务正确维护这些字段
