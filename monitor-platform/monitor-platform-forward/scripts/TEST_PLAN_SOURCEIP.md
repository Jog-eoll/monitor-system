# sourceIp 白名单修改测试方案

## 1. 测试背景

### 1.1 问题描述
原代码中，解密网关（终端网关）的 `sourceIp` 白名单被错误地设置为客户端（发布服务器）的 IP 地址，而不是加密网关的 IP 地址。这导致加密网关转发的数据包被解密网关的白名单校验拒绝。

### 1.2 修改内容
- **修改文件**: `PublishGatewayConfigServiceImpl.java`（monitor-platform-forward 和 monitor-platform-monolith）
- **修改逻辑**: 将解密网关的 `sourceIp` 从 `publishServerNode.getDeviceIp()` 改为 `publishGwNode.getDeviceIp()`

### 1.3 数据流
```
客户端（发布服务器） → 加密网关 → 解密网关 → 情报板
   192.168.1.100      192.168.1.25   192.168.1.26
```

## 2. 测试环境

### 2.1 服务器信息
| 服务器 | IP | 部署服务 | 端口 |
|--------|-----|----------|------|
| 管控平台 | 127.0.0.1 | monitor-platform-forward | 8067 |
| 加密网关 | 192.168.1.25 | publish-gateway | 8092 |
| 解密网关 | 192.168.1.26 | terminal-gateway | 8093 |

### 2.2 前置条件
1. 管控平台 forward 模块已启动（端口 8067）
2. 加密网关已启动（端口 8092）
3. 解密网关已启动（端口 8093）
4. 数据库连接正常
5. 测试链路 ID 9999 不存在

## 3. 测试用例

### 3.1 测试用例1：验证 sourceIp 配置下发

**测试目标**: 确认解密网关收到的 `sourceIp` 配置是加密网关 IP

**测试步骤**:
1. 运行测试脚本 `test-sourceip-whitelist.ps1`
2. 检查管控平台日志
3. 检查解密网关日志
4. 查询解密网关数据库

**预期结果**:
- 管控平台日志显示: `【终端网关下发】配置源IP白名单（加密网关IP）: 192.168.1.25`
- 解密网关日志显示: `sourceIp=192.168.1.25`
- 数据库 `source_ip` 字段值为 `192.168.1.25`

### 3.2 测试用例2：验证白名单校验通过

**测试目标**: 确认加密网关转发的数据包能通过解密网关的白名单校验

**测试步骤**:
1. 配置下发完成后，从客户端发送测试数据包
2. 检查解密网关日志中的白名单校验结果

**预期结果**:
- 解密网关日志显示: `【终端网关】来源校验通过: 192.168.1.25 匹配发布网关白名单 [192.168.1.25]`
- 不会出现 `拒绝UDP包 - 来源IP XXX 不在白名单 [XXX] 中` 的日志

### 3.3 测试用例3：验证发布网关 sourceIp 配置

**测试目标**: 确认发布网关的 `sourceIp` 配置仍然是客户端 IP

**测试步骤**:
1. 检查管控平台日志中发布网关的配置下发日志
2. 查询发布网关的规则配置

**预期结果**:
- 管控平台日志显示: `【发布网关下发】配置源IP白名单（客户端IP）: 192.168.1.100`
- 发布网关的 `source_ip` 字段值为 `192.168.1.100`

## 4. 验证方法

### 4.1 查看管控平台日志
```bash
# 搜索终端网关下发日志
grep "终端网关下发.*配置源IP白名单" logs/forward.log

# 期望看到：
# 【终端网关下发】配置源IP白名单（加密网关IP）: 192.168.1.25
```

### 4.2 查看解密网关日志
```bash
# 搜索配置接收日志
grep "receive config" logs/terminal-gateway.log

# 搜索 sourceIp 字段
grep "sourceIp=" logs/terminal-gateway.log

# 期望看到：
# sourceIp=192.168.1.25
```

### 4.3 查询解密网关数据库
```sql
-- 查询测试链路的规则配置
SELECT rule_id, chain_id, source_ip, target_ip, listen_port 
FROM udp_proxy_rule 
WHERE chain_id = 9999 AND deleted = 0;

-- 期望结果：
-- source_ip = 192.168.1.25 (加密网关IP)
-- 不应该是 192.168.1.100 (客户端IP)
```

### 4.4 查询发布网关数据库
```sql
-- 查询测试链路的规则配置
SELECT rule_id, chain_id, source_ip, target_ip, listen_port 
FROM udp_proxy_rule 
WHERE chain_id = 9999 AND deleted = 0;

-- 期望结果：
-- source_ip = 192.168.1.100 (客户端IP)
```

## 5. 测试脚本

### 5.1 自动化测试脚本
位置: `monitor-platform-forward/scripts/test-sourceip-whitelist.ps1`

运行方法:
```powershell
cd d:\project02\monitor-platform\monitor-platform-forward\scripts
.\test-sourceip-whitelist.ps1
```

### 5.2 手动测试步骤

**步骤1: 创建测试链路**
```powershell
$body = @{
    chainId = 9999
    rootNodes = @(
        @{
            deviceId = "TEST-PUB-SERVER-001"
            deviceName = "测试发布服务器"
            deviceType = "publish_server"
            deviceIp = "192.168.1.100"
            children = @(
                @{
                    deviceId = "TEST-PUB-GW-001"
                    deviceName = "测试加密网关"
                    deviceType = "publish_gateway"
                    deviceIp = "192.168.1.25"
                    children = @(
                        @{
                            deviceId = "TEST-TERM-GW-001"
                            deviceName = "测试解密网关"
                            deviceType = "terminal_encrypt_gateway"
                            deviceIp = "192.168.1.26"
                            children = @(
                                @{
                                    deviceId = "TEST-INFO-BOARD-001"
                                    deviceName = "测试情报板"
                                    deviceType = "info_board"
                                    deviceIp = "192.168.1.200"
                                }
                            )
                        }
                    )
                }
            )
        }
    )
    operatorBy = "test"
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "http://127.0.0.1:8067/chain/create/nodes" -Method Post -Body $body -ContentType "application/json"
```

**步骤2: 查询解密网关规则**
```powershell
Invoke-RestMethod -Uri "http://192.168.1.26:8093/udp-proxy/rules/running" -Method Get
```

**步骤3: 清理测试数据**
```sql
-- 删除测试链路
DELETE FROM task_chain_node WHERE chain_id = 9999;
DELETE FROM task_chain_config WHERE id = 9999;

-- 删除网关规则
DELETE FROM udp_proxy_rule WHERE chain_id = 9999;
```

## 6. 预期日志输出

### 6.1 管控平台日志（正确）
```
【终端网关下发】URL=http://192.168.1.26:8093/udp-proxy/config, chainId=9999, branchCode=B1
【终端网关下发】配置源IP白名单（加密网关IP）: 192.168.1.25
【发布网关下发】URL=http://192.168.1.25:8092/udp-proxy/config, chainId=9999, branchCode=B1
【发布网关下发】配置源IP白名单（客户端IP）: 192.168.1.100
```

### 6.2 解密网关日志（正确）
```
[terminal-gateway] receive config
chainId=9999, branchCode=B1, listenPort=9520
publishGatewayIp=192.168.1.25, infoBoard=192.168.1.200:9520
sourceIp=192.168.1.25
decryptEnabled=true
```

### 6.3 数据包校验日志（正确）
```
【终端网关】收到数据包
来源: /192.168.1.25:12345
数据长度: 1024 字节
【终端网关】来源校验通过: 192.168.1.25 匹配发布网关白名单 [192.168.1.25]
```

## 7. 异常情况处理

### 7.1 sourceIp 仍然是客户端 IP
**可能原因**: 代码修改未生效，需要重新编译部署

**解决步骤**:
1. 检查代码修改是否正确
2. 重新编译 forward 模块
3. 重启管控平台服务

### 7.2 白名单校验失败
**可能原因**: 
1. 加密网关 IP 配置错误
2. 网络路由问题

**排查步骤**:
1. 检查加密网关实际 IP
2. 检查网络连通性
3. 检查防火墙规则

### 7.3 配置下发失败
**可能原因**:
1. 解密网关服务未启动
2. 端口不通
3. 设备 ID 不存在

**排查步骤**:
1. 检查解密网关服务状态
2. 测试端口连通性
3. 检查设备管理中的设备配置

## 8. 测试报告模板

| 测试项 | 预期结果 | 实际结果 | 是否通过 |
|--------|----------|----------|----------|
| 解密网关 sourceIp 配置 | 192.168.1.25 | | |
| 发布网关 sourceIp 配置 | 192.168.1.100 | | |
| 白名单校验通过 | 校验通过 | | |
| 数据包正常转发 | 转发成功 | | |

## 9. 相关文件

- 测试脚本: `monitor-platform-forward/scripts/test-sourceip-whitelist.ps1`
- 修改文件: 
  - `monitor-platform-forward/src/main/java/.../PublishGatewayConfigServiceImpl.java`
  - `monitor-platform-monolith/src/main/java/.../PublishGatewayConfigServiceImpl.java`
- 配置文件: `terminal-gateway/gateway-udp-proxy/src/main/resources/application.yml`
