# sourceIp 白名单逻辑修改说明

## 1. 修改背景

### 1.1 问题描述

在链路下发过程中，解密网关（终端网关）收到的 `sourceIp` 白名单配置存在错误：

- **错误现象**：解密网关的 `sourceIp` 被设置为客户端（发布服务器）的 IP 地址
- **正确行为**：解密网关的 `sourceIp` 应该设置为加密网关的 IP 地址

### 1.2 问题影响

由于白名单配置错误，加密网关转发给解密网关的数据包会被白名单校验拒绝，导致：

1. 解密网关无法接收加密网关转发的数据
2. 情报板无法正常显示内容
3. 链路通信中断

### 1.3 数据流分析

```
客户端（发布服务器）    →    加密网关    →    解密网关    →    情报板
  192.168.1.100           192.168.1.25      192.168.1.26      192.168.1.200
       ↓                      ↓
  sourceIp应为          实际发送数据包
  加密网关IP            的源IP地址
```

## 2. 修改内容

### 2.1 修改文件

| 文件路径 | 说明 |
|---------|------|
| `monitor-platform/monitor-platform-forward/src/main/java/.../PublishGatewayConfigServiceImpl.java` | 微服务架构下的配置下发服务 |
| `monitor-platform/monitor-platform-monolith/src/main/java/.../PublishGatewayConfigServiceImpl.java` | 单体架构下的配置下发服务 |

### 2.2 修改方法

#### 2.2.1 `doDeployToTerminalGateway` 方法

**功能**：下发配置到解密网关（终端网关）

**修改前**：
```java
// 源IP白名单：将发布服务器（Sigma主机）的IP作为授权白名单下发
if (publishServerNode != null && StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
    requestBody.put("sourceIp", publishServerNode.getDeviceIp());
    log.info("【终端网关下发】配置源IP白名单: {}", publishServerNode.getDeviceIp());
} else {
    log.warn("【终端网关下发】链路未配置发布服务器节点或IP为空，sourceIp 回退发布网关IP");
}
```

**修改后**：
```java
// 源IP白名单：将发布网关（加密网关）的IP作为授权白名单下发
if (publishGwNode != null && StrUtil.isNotBlank(publishGwNode.getDeviceIp())) {
    requestBody.put("sourceIp", publishGwNode.getDeviceIp());
    log.info("【终端网关下发】配置源IP白名单（加密网关IP）: {}", publishGwNode.getDeviceIp());
} else {
    log.warn("【终端网关下发】链路未配置发布网关节点或IP为空，sourceIp 不设置（不限制来源）");
}
```

**修改说明**：
- `publishServerNode` → `publishGwNode`
- 将白名单 IP 从客户端 IP 改为加密网关 IP
- 更新日志信息，明确标识为"加密网关IP"

#### 2.2.2 `doDeployToPublishGateway` 方法

**功能**：下发配置到加密网关（发布网关）

**修改前**：
```java
// 源IP白名单：将发布服务器（Sigma主机）的IP作为授权白名单下发
if (publishServerNode != null && StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
    requestBody.put("sourceIp", publishServerNode.getDeviceIp());
    log.info("【发布网关下发】配置源IP白名单: {}", publishServerNode.getDeviceIp());
} else {
    log.warn("【发布网关下发】链路未配置发布服务器节点或IP为空，sourceIp 不设置（不限制来源）");
}
```

**修改后**：
```java
// 源IP白名单：将发布服务器（Sigma主机）的IP作为授权白名单下发
if (publishServerNode != null && StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
    requestBody.put("sourceIp", publishServerNode.getDeviceIp());
    log.info("【发布网关下发】配置源IP白名单（客户端IP）: {}", publishServerNode.getDeviceIp());
} else {
    log.warn("【发布网关下发】链路未配置发布服务器节点或IP为空，sourceIp 不设置（不限制来源）");
}
```

**修改说明**：
- 逻辑保持不变（加密网关确实需要客户端 IP 作为白名单）
- 更新日志信息，明确标识为"客户端IP"

## 3. 代码逻辑说明

### 3.1 节点类型定义

| 节点类型 | deviceType | 说明 |
|---------|------------|------|
| 发布服务器 | `publish_server` | 客户端/Sigma主机 |
| 发布加密网关 | `publish_gateway` | 加密网关 |
| 终端加密网关 | `terminal_encrypt_gateway` | 解密网关 |
| 情报板 | `info_board` | 显示终端 |

### 3.2 白名单配置逻辑

| 网关类型 | sourceIp 配置 | 原因 |
|---------|--------------|------|
| 加密网关（发布网关） | 客户端 IP | 接收来自客户端的数据 |
| 解密网关（终端网关） | 加密网关 IP | 接收来自加密网关的数据 |

### 3.3 关键代码片段

```java
// 查找发布加密网关节点
TaskChainNode publishGatewayNode = allNodes.stream()
        .filter(n -> TYPE_PUBLISH_GATEWAY.equals(n.getDeviceType()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("链路缺少发布加密网关节点"));

// 查找发布服务器节点（用于加密网关的白名单）
TaskChainNode publishServerNode = allNodes.stream()
        .filter(n -> "publish_server".equals(n.getDeviceType()))
        .findFirst()
        .orElse(null);

// 下发到解密网关时，sourceIp 使用加密网关 IP
if (publishGwNode != null && StrUtil.isNotBlank(publishGwNode.getDeviceIp())) {
    requestBody.put("sourceIp", publishGwNode.getDeviceIp());
}

// 下发到加密网关时，sourceIp 使用客户端 IP
if (publishServerNode != null && StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
    requestBody.put("sourceIp", publishServerNode.getDeviceIp());
}
```

## 4. 影响范围

### 4.1 功能影响

| 功能模块 | 影响 | 说明 |
|---------|------|------|
| 链路配置下发 | 正向 | 解密网关白名单配置正确 |
| 数据包转发 | 正向 | 加密网关数据能通过白名单校验 |
| 情报板显示 | 正向 | 链路通信恢复正常 |

### 4.2 兼容性

- 向后兼容：不影响现有链路的删除、更新操作
- 数据库兼容：`udp_proxy_rule` 表结构无变化

## 5. 验证方法

### 5.1 日志验证

**管控平台日志**：
```
【终端网关下发】配置源IP白名单（加密网关IP）: 192.168.1.25
【发布网关下发】配置源IP白名单（客户端IP）: 192.168.1.100
```

**解密网关日志**：
```
[terminal-gateway] receive config
sourceIp=192.168.1.25
```

### 5.2 数据库验证

```sql
-- 查询解密网关数据库
SELECT rule_id, chain_id, source_ip, target_ip 
FROM udp_proxy_rule 
WHERE deleted = 0;

-- 期望结果：source_ip 字段为加密网关 IP
```

### 5.3 运行时验证

```powershell
# 查询解密网关运行中的规则
Invoke-RestMethod -Uri "http://192.168.1.26:8093/udp-proxy/rules/running"
```

## 6. 测试用例

### 6.1 测试场景

| 测试项 | 输入 | 预期输出 |
|--------|------|----------|
| 创建新链路 | 链路节点数据 | 解密网关 sourceIp = 加密网关 IP |
| 白名单校验 | 加密网关数据包 | 校验通过 |
| 数据转发 | 测试数据 | 正常转发到情报板 |

### 6.2 测试脚本

位置：`monitor-platform-forward/scripts/test-sourceip-whitelist.ps1`

## 7. 相关文档

- 测试方案：`monitor-platform-forward/scripts/TEST_PLAN_SOURCEIP.md`
- 系统架构：`系统架构.md`
- 部署配置：`deploy-standalone/` 目录

## 8. 修改记录

| 日期 | 版本 | 修改人 | 修改内容 |
|------|------|--------|----------|
| 2026-06-26 | 1.0 | - | 初始版本，修复 sourceIp 白名单逻辑 |

## 9. 附录

### 9.1 完整修改 diff

**monitor-platform-forward 模块**：
```diff
- if (publishServerNode != null && StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
-     requestBody.put("sourceIp", publishServerNode.getDeviceIp());
-     log.info("【终端网关下发】配置源IP白名单: {}", publishServerNode.getDeviceIp());
+ if (publishGwNode != null && StrUtil.isNotBlank(publishGwNode.getDeviceIp())) {
+     requestBody.put("sourceIp", publishGwNode.getDeviceIp());
+     log.info("【终端网关下发】配置源IP白名单（加密网关IP）: {}", publishGwNode.getDeviceIp());
  } else {
-     log.warn("【终端网关下发】链路未配置发布服务器节点或IP为空，sourceIp 回退发布网关IP");
+     log.warn("【终端网关下发】链路未配置发布网关节点或IP为空，sourceIp 不设置（不限制来源）");
  }
```

**monitor-platform-monolith 模块**：
```diff
- if (publishServerNode != null && StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
-     requestBody.put("sourceIp", publishServerNode.getDeviceIp());
-     log.info("【终端网关下发】配置源IP白名单: {}", publishServerNode.getDeviceIp());
+ if (publishGwNode != null && StrUtil.isNotBlank(publishGwNode.getDeviceIp())) {
+     requestBody.put("sourceIp", publishGwNode.getDeviceIp());
+     log.info("【终端网关下发】配置源IP白名单（加密网关IP）: {}", publishGwNode.getDeviceIp());
  } else {
-     log.warn("【终端网关下发】链路未配置发布服务器节点或IP为空，sourceIp 回退发布网关IP");
+     log.warn("【终端网关下发】链路未配置发布网关节点或IP为空，sourceIp 不设置（不限制来源）");
  }
```
