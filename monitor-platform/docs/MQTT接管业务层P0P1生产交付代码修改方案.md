# MQTT 接管业务层 P0/P1 生产交付代码修改方案

版本日期：2026-07-08

## 1. 范围和目标

本方案基于 `192.168.1.70` 新平台与 `192.168.77.11/12` 网关新镜像联调结果，以及当前 `D:\project02` 源码审查结论制定。

目标是在暂不考虑 EMQX TLS、ACL、账号安全加固的前提下，使 MQTT 接管业务层测试计划中的 P0 和 P1 测试项具备生产交付条件。

涉及工程：

| 工程 | 角色 | 修改重点 |
| --- | --- | --- |
| `monitor-platform/monitor-platform-forward` | 平台业务入口和 MQTT 命令分发 | 补齐非破坏命令入口、链路部署命令语义、控制/内容/截图/升级命令下发 |
| `publish-gateway/gateway-udp-proxy` | 加密网关 MQTT Agent | 修复 dry-run、action 失败判定、受控业务命令处理、持久化幂等 |
| `terminal-gateway/gateway-udp-proxy` | 解密网关 MQTT Agent | 修复 dry-run、action 失败判定、截图/控制/升级命令处理、持久化幂等 |
| `terminal-gateway/gateway-device-core` | 终端控制与设备协议服务 | 支撑控制投递、截图、设备响应回传 |
| 部署包和 SQL 初始化脚本 | 生产落地 | 增加幂等表、环境变量、镜像/回滚材料 |

## 2. 当前结论

当前代码已经支持 MQTT 基础闭环，但不能完整支撑 P0/P1 生产交付测试。

已具备：

- 平台可创建 MQTT 命令记录并发布到 `/{tenant}/{site}/{device}/down/proxy-command`。
- 网关可连接 EMQX、注册、心跳、接收命令并通过 `up/reply` 回执。
- `QUERY_STATUS` 在发布网关和终端网关均已走 native probe。
- 发布网关 action 结果已按 `success`、HTTP 状态和业务 `code` 汇总。
- 平台命令表和事件表可记录 `CREATED`、`PUBLISHED`、`PROCESSING`、`SUCCESS/FAILED/TIMEOUT`。

仍需补齐：

- `CHANGE_SYSTEM_IP dryRun=true` 在 11/12 上仍不满足非破坏成功验收。
- 终端网关 action 返回 `code=500` 时仍可能被汇总为 `SUCCESS`。
- 网关幂等仅在内存中，容器重启后重复 `messageId` 防重失效。
- `CHAIN_DEPLOY` 与当前 `APPLY_CHAIN_CONFIG` 命令名和测试语义未统一。
- `CONTROL_DELIVERY`、`SECURE_DELIVERY`、`SNAPSHOT_REPORT` 还没有全部形成平台正式 API -> MQTT -> 网关业务服务 -> 回执的闭环。
- `REMOTE_UPGRADE` 只能作为 verify-only 或测试脚本执行，不能直接视为真实升级生产能力。

## 3. 关键文件

| 文件 | 角色 | 修改方式 |
| --- | --- | --- |
| `monitor-platform/monitor-platform-forward/src/main/java/com/monitorplatform/forward/controller/MqttCommandController.java` | 非破坏 MQTT 命令入口 | 增加 `NOOP`、`ECHO`，保留 `QUERY_STATUS` |
| `monitor-platform/monitor-platform-forward/src/main/java/com/monitorplatform/forward/controller/GatewayNetworkController.java` | 改 IP 正式入口 | 补 dry-run 回归测试，确保不触发 DB callback |
| `monitor-platform/monitor-platform-forward/src/main/java/com/monitorplatform/forward/service/impl/PublishGatewayConfigServiceImpl.java` | 链路和代理规则下发 | 对齐 `CHAIN_DEPLOY` 命令语义，保留 `APPLY_CHAIN_CONFIG` 兼容 |
| `monitor-platform/monitor-platform-forward/src/main/java/com/monitorplatform/upgrade/service/impl/RemoteUpgradeServiceImpl.java` | 远程升级下发 | 明确 verify-only 和测试脚本执行状态 |
| `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/mqtt/GatewayCommandDispatcher.java` | 发布网关 MQTT 执行器 | 增加受控业务命令或统一 action 处理 |
| `terminal-gateway/gateway-udp-proxy/src/main/java/com/gateway/udpproxy/mqtt/GatewayCommandDispatcher.java` | 终端网关 MQTT 执行器 | 修复 action 失败判定，补 native 命令 |
| `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/mqtt/SystemNetworkChangeService.java` | 发布网关改 IP | dry-run 先于 enabled/root/ip 检查返回计划 |
| `terminal-gateway/gateway-udp-proxy/src/main/java/com/gateway/udpproxy/mqtt/SystemNetworkChangeService.java` | 终端网关改 IP | dry-run 先于 root/ip/网卡检查返回计划 |
| 两侧 `MqttCommandRecordService.java` | 网关命令幂等 | 从内存幂等扩展为 DB 唯一键幂等 |
| 两侧部署 SQL / init 脚本 | 本地数据库初始化 | 增加 `mqtt_command_record` 表 |

## 4. P0 修改方案

### 4.1 修复 `CHANGE_SYSTEM_IP dryRun`

目标语义：

- `dryRun=true` 只做参数校验、接口名白名单校验和计划生成。
- `dryRun=true` 不执行 `id -u`、`command -v ip`、`ip link show`、`ip addr add`。
- `dryRun=true` 不要求 `system-network-change.enabled=true`。
- `dryRun=false` 才进入真实权限、命令、网卡和安全开关检查。

返回字段建议：

| 字段 | 含义 |
| --- | --- |
| `success` | dry-run 校验是否通过 |
| `dryRun` | 固定为 `true` |
| `systemNetworkChangeEnabled` | 当前真实变更安全开关 |
| `changeId` | 变更 ID |
| `interfaceName` | 目标网卡 |
| `newCidr` | 计划新增地址 |
| `plannedAddCommand` | 计划执行的新增 IP 命令 |
| `plannedRouteCommand` | 计划执行的默认路由命令，可为空 |
| `message` | `dry run passed` 或明确失败原因 |

测试要求：

- 11/12 安全开关关闭时 dry-run 返回 `SUCCESS`。
- dry-run 不修改网卡。
- dry-run 不触发平台设备表和链路节点 IP 回写。
- 非 dry-run 且安全开关关闭时仍返回 `FAILED`。

### 4.2 统一 action 失败判定

两侧 `GatewayCommandDispatcher` 的 action 汇总规则必须一致：

- `result == null` -> `FAILED`
- `success=false` 或字符串 `"false"` -> `FAILED`
- `httpStatus` 存在且非 2xx -> `FAILED`
- `code` 存在且不等于 `200` -> `FAILED`
- 失败信息按 `message`、`msg`、`errorMessage`、`error`、`reason` 顺序提取

测试要求：

- HTTP 200 + `success=true` + `code=500` 必须回 `FAILED`。
- HTTP 500 必须回 `FAILED`。
- `success=false` 必须回 `FAILED`。
- 最终命令状态不能误写为 `SUCCESS`。

### 4.3 增加 `NOOP` / `ECHO` 平台正式入口

在 `MqttCommandController` 中补充：

- `POST /api/mqtt/commands/noop`
- `POST /api/mqtt/commands/echo`

约束：

- 只允许 `QUERY_STATUS`、`NOOP`、`ECHO` 三个非破坏命令。
- 支持 `waitForReply`。
- 不开放任意 command 透传，避免平台成为未审计命令发布口。

测试要求：

- 11/12 `NOOP` 返回 `SUCCESS`。
- 11/12 `ECHO` 返回原 payload。
- 三条命令均不触发本地 HTTP 回环。

### 4.4 网关命令幂等持久化

当前 `MqttCommandRecordService` 使用进程内 `ConcurrentHashMap`。生产 P0 应补本地数据库幂等。

建议表名：`mqtt_command_record`

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `message_id` | varchar(64) | MQTT messageId，唯一索引 |
| `command` | varchar(64) | 命令名 |
| `first_seen_at` | datetime | 首次接收时间 |
| `expire_at` | datetime | 过期时间 |
| `status` | varchar(32) | `PROCESSING/SUCCESS/FAILED/SKIPPED` |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

实现方式：

- `markProcessed(messageId)` 使用唯一键插入语义。
- 插入成功表示首次处理。
- 唯一键冲突表示重复消息，应直接跳过副作用。
- 保留内存缓存作为加速，但不能作为唯一依据。
- 定时清理 `expire_at < now()` 的记录。

测试要求：

- 同一 `messageId` 重复发送不重复执行。
- 模拟重启后同一 `messageId` 仍不重复执行。
- 过期记录清理后，新消息可重新进入。

### 4.5 平台状态机回归

必须补齐以下平台侧回归：

- `SUCCESS` 后迟到 `PROCESSING` 不能回退状态。
- 网关不回执时转 `TIMEOUT`。
- MQTT 客户端未连接时命令入库后标记 `FAILED`。
- dry-run 成功时不触发 `IpChangeCallbackService`。
- 非 dry-run 成功时才触发设备表和链路节点 IP 回写。

## 5. P1 修改方案

### 5.1 对齐 `CHAIN_DEPLOY`

当前链路部署实际命令是 `APPLY_CHAIN_CONFIG`。建议调整为：

- 平台正式业务语义和测试计划使用 `CHAIN_DEPLOY`。
- 网关保留 `APPLY_CHAIN_CONFIG` 作为兼容别名。
- 底层仍使用 actions：
  - 发布网关自身规则：`SELF_APPLY` -> `/udp-proxy/config`
  - 发布网关转发到终端网关：`HTTP` -> `targetIp:targetPort/udp-proxy/config`
  - 发布网关转发到客户端：`HTTP` -> `targetIp:targetPort/udp-proxy/config`

测试要求：

- `DISPATCH_MODE=mqtt` 时平台不直连网关 HTTP。
- 11 规则表写入成功。
- 11 能访问 12 的 `8093`。
- 失败时平台命令表为 `FAILED`，错误信息可定位到 11、12 或客户端哪一步。

### 5.2 `CONTROL_DELIVERY`

新增受控控制命令能力，不开放任意 HTTP 透传。

目标：

- 平台业务 API 创建 `CONTROL_DELIVERY`。
- 目标为发布网关时，action 到 11 的 `/api/secure-delivery/control-tasks`。
- 目标为终端网关时，action 到 12 的 `/api/secure-command/control`。
- 支持亮度、黑屏、校时、查询状态等最小控制命令。

修改点：

- 平台新增控制命令 DTO/API 或在现有控制入口内接入 `DeviceCommandDispatcher`。
- 发布网关 allowed-paths 明确加入 `/api/secure-delivery/control-tasks`。
- 终端网关 allowed-paths 明确加入 `/api/secure-command/control`。
- 网关回执将业务失败映射为 MQTT `FAILED`。

测试要求：

- 亮度命令成功。
- 黑屏命令成功。
- 校时命令成功。
- 缺少目标设备或非法参数返回 `FAILED/REJECTED`。

### 5.3 `SECURE_DELIVERY`

建议发布网关增加 `SECURE_DELIVERY` native command，避免只依赖 HTTP 回环。

目标：

- 平台内容投递业务创建 `SECURE_DELIVERY`。
- 发布网关调用 `SecureDeliveryService.createTask(...)`。
- 返回 `deliveryTaskId`、任务状态和失败原因。
- P1 优先支持小文件成功投递和失败定位。

实现选项：

| 方案 | 说明 | 建议 |
| --- | --- | --- |
| Native handler | dispatcher 直接调用 `SecureDeliveryService` | 推荐 |
| HTTP action | 通过本机 `/api/secure-delivery/tasks` 回环 | 可作为兼容 |

测试要求：

- 小文件成功投递。
- MinIO 下载失败返回 `FAILED`。
- SHA256 校验失败返回 `FAILED`。
- UKey/证书不可用时错误信息明确。

### 5.4 `SNAPSHOT_REPORT`

当前终端网关截图更偏定时任务。P1 需要补按需命令：

- 终端网关增加 `SNAPSHOT_REPORT` native command。
- 从 `SnapshotSchedulerService` 抽取 `captureOnce(ruleId/deviceId)`。
- 成功返回 MinIO object key、上报 URL、ruleId、deviceId。
- MinIO 未配置时返回明确 `FAILED`。

测试要求：

- 已配置 MinIO 时截图上传成功。
- 未配置 MinIO 时返回明确失败。
- 无可用规则时返回明确失败。

### 5.5 `REMOTE_UPGRADE`

当前已有 `REMOTE_UPGRADE`，P1 按安全模式验收：

- 默认 `upgrade.executor.enabled=false`，只做下载和 SHA256 校验。
- 增加测试脚本模式，启用 executor 时只执行 harmless 脚本。
- 不在 P1 中直接替换真实生产镜像。

测试要求：

- verify-only 成功。
- SHA256 mismatch 返回 `FAILED`。
- 下载地址不可达返回 `FAILED`。
- executor 测试脚本退出码非 0 返回 `FAILED`。

### 5.6 设备重复注册治理

针对 `terminal-udp-gateway-192.168.77.12-8093` 与 `terminal-gateway-12` 重复：

- 平台注册处理按 `deviceType + host + port` 检查旧记录。
- 同一物理网关旧 ID 标记离线或合并到新 ID。
- 提供一次性清理 SQL 或管理接口。
- 生产配置固定使用 `MQTT_DEVICE_ID` / `REGISTRY_CLIENT_ID`。

测试要求：

- 重启网关不会生成重复设备记录。
- 旧设备 ID 不再参与新命令路由。
- 设备页面只显示一个在线终端网关。

## 6. 测试计划

### 6.1 P0 自动化测试

```powershell
D:\project02\.tools\apache-maven-3.9.9\bin\mvn.cmd -s outputs\svn-sync-backup\terminal-gateway-20260702-192756\maven-settings-aliyun.xml -f monitor-platform\pom.xml -pl monitor-platform-forward -am "-Dtest=MqttCommandPublishServiceTest,MqttReplyHandlerTest,GatewayNetworkControllerTest,MqttCommandControllerContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test

D:\project02\.tools\apache-maven-3.9.9\bin\mvn.cmd -s outputs\svn-sync-backup\terminal-gateway-20260702-192756\maven-settings-aliyun.xml -f publish-gateway\pom.xml -pl gateway-udp-proxy -am "-Dtest=GatewayCommandDispatcherTest,*MqttCommandRecord*" "-DfailIfNoTests=false" test

D:\project02\.tools\apache-maven-3.9.9\bin\mvn.cmd -s outputs\svn-sync-backup\terminal-gateway-20260702-192756\maven-settings-aliyun.xml -f terminal-gateway\pom.xml -pl gateway-udp-proxy -am "-Dtest=TerminalMqttAgentContractTest,*MqttCommandRecord*" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### 6.2 P0 现场联调

| 测试项 | 目标 | 预期 |
| --- | --- | --- |
| `QUERY_STATUS` | 11 / 12 | `SUCCESS`，native probe，不走 HTTP 回环 |
| `NOOP` | 11 / 12 | `SUCCESS` |
| `ECHO` | 11 / 12 | `SUCCESS`，返回原 payload |
| `CHANGE_SYSTEM_IP dryRun=true` | 11 / 12 | `SUCCESS`，返回计划，不改网卡，不写库 |
| 重复 `messageId` | 11 / 12 | 不重复执行副作用 |
| 网关不回执 | 平台 | 命令转 `TIMEOUT` |
| 非法 payload | 11 / 12 | `FAILED` 或 `REJECTED`，错误明确 |

### 6.3 P1 现场联调

| 测试项 | 目标 | 预期 |
| --- | --- | --- |
| `CHAIN_DEPLOY` | 11 | 规则写入，代理启动，11 能访问 12 |
| `PROXY_RULE_REMOVE` | 11 | 规则停止并回执成功 |
| `CONTROL_DELIVERY` 亮度 | 11 / 12 | 设备响应成功 |
| `CONTROL_DELIVERY` 黑屏 | 11 / 12 | 设备响应成功 |
| `CONTROL_DELIVERY` 校时 | 11 / 12 | 设备响应成功 |
| `SECURE_DELIVERY` 小文件 | 11 | 任务成功，过程状态可查 |
| `SECURE_DELIVERY` MinIO 失败 | 11 | `FAILED`，错误定位到下载/配置 |
| `SNAPSHOT_REPORT` | 12 | 截图上传或明确失败 |
| `REMOTE_UPGRADE` verify-only | 11 / 12 | 下载和 SHA256 校验成功 |
| `REMOTE_UPGRADE` SHA256 mismatch | 11 / 12 | `FAILED` |

## 7. 实施顺序

1. 修复 P0 当前红灯：`CHANGE_SYSTEM_IP dryRun` 与终端网关 action 失败判定。
2. 补 `NOOP` / `ECHO` 平台入口。
3. 增加两侧网关持久化幂等。
4. 对齐 `CHAIN_DEPLOY` 与 `APPLY_CHAIN_CONFIG`。
5. 增加 `CONTROL_DELIVERY` 受控命令。
6. 增加 `SECURE_DELIVERY` native handler。
7. 增加 `SNAPSHOT_REPORT` 按需命令。
8. 完善 `REMOTE_UPGRADE` verify-only 和测试脚本执行。
9. 补设备重复注册治理。
10. 统一更新三份活文档和 MQTT 接管测试计划。

## 8. 交付门禁

P0 门禁：

- 三个关键模块编译通过：
  - `monitor-platform-forward`
  - `publish-gateway/gateway-udp-proxy`
  - `terminal-gateway/gateway-udp-proxy`
- P0 自动化测试全部通过。
- 11/12 `QUERY_STATUS`、`NOOP`、`ECHO`、`CHANGE_SYSTEM_IP dryRun=true` 现场均通过。
- 命令表和事件表状态流正确。
- 重复 `messageId` 在容器重启前后均不重复执行。

P1 门禁：

- 链路部署和代理规则下发成功。
- 控制命令至少覆盖亮度、黑屏、校时。
- 内容投递小文件成功，失败路径可定位。
- 截图按需命令或明确失败路径可验证。
- 远程升级 verify-only 和 SHA256 失败路径可验证。
- 设备重复记录治理完成。

## 9. 暂不纳入范围

本阶段暂不处理：

- EMQX TLS / 8883 证书切换。
- EMQX ACL deny-by-default。
- EMQX dashboard 账号安全。
- MQTT mTLS。
- 大文件容量压测。
- 多站点多租户隔离压测。
- 真实生产镜像自动升级替换。

这些内容应在 P0/P1 业务闭环稳定后作为生产安全加固阶段单独推进。
