# Agent Debug Ledger

本文件用于记录 `D:\project02` 中反复出现、流程中容易出错、需要多次调试才能定位的故障经验。Agent 在排查 Bug、做联调、改部署或改接口前，应先按关键词检索本文件；确认新的复发性问题后，应在同一轮任务中更新本文件。

## 维护规则

1. 只记录已经有证据支撑的问题，不记录猜测。
2. 复发问题优先更新已有条目，不重复新增。
3. 每个条目必须包含现象、触发条件、根因、解决方案、验证方式、涉及文件/模块、预防规则。
4. 不写入任何密码、token、私钥、UKey PIN、数据库口令、MQTT secret 或一次性现场凭据。
5. 条目编号格式：`DBG-YYYYMMDD-NNN`。

## 索引

| ID | 领域 | 关键词 | 典型现象 | 优先处理 |
| --- | --- | --- | --- | --- |
| DBG-20260718-031 | SVN revision log encoding | `svn:log`, `--encoding UTF-8`, `-F`, `E175008`, `pre-revprop-change` | Windows 上 SVN 文件提交成功，但中文修订说明回读为乱码且服务器禁止修改 revprop | 提交时显式指定日志文件编码，立即回读验证；乱码后由管理员启用 hook 再修复 |
| DBG-20260717-030 | Secure publish monitoring batch replacement | `SPB-`, `playBatchId`, `playBatchSeq`, `playBatchSize`, `contentId` | The screen replaces its playlist but the regulatory view keeps media from previous publish rounds | Generate one monitoring batch per completed delivery and activate only the latest complete batch per board |
| DBG-20260706-008 | Terminal device discovery sync | `POST /api/devices/scan`, `/discovery/scan`, `led_controller`, `unified_device_info` | Terminal gateway can list QingSong/JetFileII devices but platform search/device list misses some of them | Separate terminal gateway screen scan from platform mDNS service discovery, then verify sync/registration path |
| DBG-20260706-009 | Terminal gateway startup | `DeviceCapability`, `DEVICE_SEARCH`, `gateway-device-protocol`, `gateway-device-core` | New terminal gateway image restarts before HTTP health is available | Fix duplicate capability registration before treating SVAC or UKey config as the blocker |
| DBG-20260706-010 | Terminal gateway build | `Maven Central`, `PKIX`, `netty-all`, `gateway-device-transport`, `maven-settings-aliyun.xml` | `package` fails while resolving Netty native runtime artifacts | Use the existing Aliyun Maven settings or fix the local JDK trust store before diagnosing source code |
| DBG-20260706-011 | Publish gateway transparent VIP bind | `192.168.113.88`, `BindException`, `ip route add local`, `/udp-proxy/config`, `49_B1` | Platform chain deploy partially fails because publish gateway cannot bind the info-board IP | Add or persist a local route/VIP plan on the publish gateway host before redeploying transparent rules |
| DBG-20260706-012 | SVAC USB gate selector | `VAUTH_SVAC_MODULE_DEVICE_PATH`, `0ac8:7180`, `VIMICRO`, `3a59:4458`, `USBKEY` | Pulling the real SVAC module does not interrupt the chain, but pulling UKey does | Configure the gate by VIMICRO vendor/product instead of a volatile UKey sysfs path |
| DBG-20260707-013 | VAuth microservice URL overrides | `CONTROL_PLATFORM_URL`, `MONITOR_UKEY_PORT`, `8063`, `8080`, `未找到会话密钥` | Terminal gateway is in real crypto mode but decrypt fails because key query reaches the wrong UKey endpoint | Check explicit URL overrides before trusting split MONITOR_* port variables |
| DBG-20260707-014 | Content publish result aggregation | `/api/client/publish/execute`, `/api/secure-delivery/tasks/{deliveryTaskId}`, `DELIVERY_FAILED`, `success=false` | Publish gateway worker fails after task creation, but client publish entry still reports success | Query secure-delivery task terminal status and propagate any failed sub-step to the main response |
| DBG-20260708-015 | MQTT gateway command semantics | `QUERY_STATUS`, `GatewayCommandDispatcher`, `LocalHttpForwardService`, `code=500`, `up/reply` | Gateway MQTT command looks successful while it actually fell back to `/udp-proxy/config` or business `code` failed | Handle probe commands natively and judge action success by `success` + HTTP status + business `code` |
| DBG-20260708-016 | Terminal gateway transport facade | `DeviceChannelPool`, `NettyTransportManager`, `HttpTransport`, `TcpTransport`, `UdpTransport` | `gateway-device-transport` compile fails because the manager still references removed pool type | Keep `NettyTransportManager` as a facade over split HTTP/TCP/UDP transports and pools |
| DBG-20260708-017 | MQTT gateway deploy schema | `mqtt_command_record`, `udp_proxy_gateway`, `terminal_gateway`, image-only deploy | New gateway image starts but logs SQLSyntaxError for missing MQTT dedup table | Apply the gateway init DDL or migration before recreating MQTT-enabled gateways |
| DBG-20260708-018 | Chain deploy MQTT routing | `CHAIN_DEPLOY`, `TaskChainNode.deviceId`, `publish-001`, `publish-gateway-25` | Platform chain deploy publishes to an offline logical node topic and times out | Resolve the routable MQTT client/device ID before dispatching chain deploy commands |
| DBG-20260708-019 | Public-platform screen control routing | `BLACKOUT`, `CUT_VIOLATION`, `control-delivery`, EMQX, Feign timeout | Native MQTT control reaches the terminal gateway, but Alarm can time out while the device command is still executing | Keep native MQTT routing and layer the command, HTTP wait, and Feign timeout budgets without finalizing active commands early |
| DBG-20260709-022 | Secure delivery large video | `FILE_REF`, `MEDIA_MULTI_UPLOAD_FTP`, `29520`, `WRITE_BIGFILE_CRC`, `0x0215` | Multi-GB publish stalls, exhausts heap, fragments UDP, or times out before the screen finishes | Keep FILE_REF to terminal; for QingSong playlists containing video, upload all media in one FTP batch with per-screen concurrency control |
| DBG-20260709-023 | MinIO content bucket alignment | `MINIO_BUCKET_NAME`, `MINIO_BUCKET`, `CONTENT_MINIO_ENDPOINT`, `monitor-content`, `图片准备失败` | `SECURE_PUBLISH` 媒体已上传但内容服务检测记录停在 pending，原因是图片准备失败或 MinIO 返回 403/404 | 同时核对发布网关上传桶、内容服务读取桶、内容服务 MinIO endpoint 和桶匿名下载策略 |
| DBG-20260713-025 | Nacos runtime source drift | `NACOS_IP_PORT`, `CONTENT_NACOS_IP_PORT`, `application-dev.yml`, `StdOutImpl`, `NoLoggingImpl` | Nacos 页面已改成 `NoLoggingImpl`，但 `monitor-forward` 仍持续打印 MyBatis SQL | 先看容器环境变量和实际拉取的 Nacos 地址，再改对应 Nacos 或切换业务容器配置源并重建容器 |
| DBG-20260713-026 | Nginx Docker upstream stale IP | `monitor-nginx`, `monitor-gateway`, `proxy_pass`, Docker DNS, `502 Bad Gateway`, `/api/cert/online-status` | 重建业务容器后直连 gateway 正常，但经 Nginx `/api/**` 返回 HTML 502 | 重建或切换业务容器后同步 reload/restart `monitor-nginx`，或改成可动态解析的 upstream |
| DBG-20260713-027 | VAuth server UKey password env drift | `VAUTH_SERVER_PASSWORD`, `SERVER_UKEY_REMOVED`, `serverUkeyOnline=false`, `用户名或密码不正确`, `monitor-ukey` | 登录后立即提示服务端认证硬件异常，接口返回服务端 UKey 不在线 | 先查 `monitor-ukey` 容器环境和 VAuth 日志，修正服务端 UKey 口令后重建服务 |
| DBG-20260713-028 | Alarm cut/restore content boundary | `CUT_VIOLATION`, `BLACKOUT`, `SEQUENT.SYS`, `contentId`, `IMAGE_DELETE` | 非告警切断误删内容，或告警切断清空全部图片/恢复时写默认底图 | 仅待处理告警可精确删当前图片；恢复只解除黑屏并播放剩余列表 |
| DBG-20260714-029 | Gateway MQTT heartbeat recovery | `HeartbeatPublisher`, `MqttConnectionManager`, `Paho`, `Timed out waiting for a response from the server`, `up/heartbeat` | 网关心跳 publish 等待 Broker 响应失败后持续复用半失效 MQTT client | 心跳 publish 异常或 connectionLost 后废弃当前 client，由定时重连重建连接和订阅 |
| DBG-20260706-001 | SVN 同步 | `svn update --dry-run`, `svn status --xml -u` | 用无效 dry-run 评估更新风险，命令失败或误判冲突 | 改用 `svn status --xml -u`、`svn diff --summarize -r BASE:HEAD`、`svn log` |
| DBG-20260706-002 | Git/SVN 混合工作区 | `git log`, `svn status`, dirty tree | 只看 Git 历史导致漏掉 SVN 状态或未版本化文件 | 同时检查 Git 与 SVN 状态，提交/更新前限定文件范围 |
| DBG-20260706-003 | 管控平台双部署形态 | `monitor-platform-monolith`, microservice, callback | 微服务路径修好了，融合/monolith 部署仍旧缺行为 | 改公共能力时同步核对 monolith 控制器、Service、Liquibase |
| DBG-20260706-004 | 远程改 IP | `dryRun`, `IpChangeCallbackService`, `FeignDeviceIpUpdater` | dryRun 仍写库，或下游失败却返回成功 | callback 必须受请求模式约束，并校验 Feign 业务码和 DB 更新数 |
| DBG-20260706-005 | QingSong/JetFileII 兼容 | `QINGSONG`, `TargetToSelectorMapper`, `/api/devices/register`, `DEVICE_NOT_LOGGED_IN` | 注册列表可见但协议登录态缺失，或老批量命令路径失败 | 同时核对设备注册登录、secure command、旧 batch-command 映射；固定资产清单也必须复用协议登录注册 |
| DBG-20260706-006 | Windows/PowerShell 环境 | `@'`, `npm.cmd`, UTF8, Chinese path | heredoc、npm.ps1、中文路径或编码导致命令失败 | 使用 PowerShell here-string、`npm.cmd`、UTF-8 输出和 `rg --files` |
| DBG-20260706-007 | 文档同步遗漏 | `系统架构.md`, `监管平台端口梳理.md`, `信发管理系统功能清单.xlsx` | 代码/配置已改，但最终交付漏写文档状态 | 每次代码/接口/端口/部署/功能变更都检查三份活文档 |

## 条目

### DBG-20260706-001 SVN 更新风险不能用 dry-run

- 首次记录：2026-07-06
- 领域：SVN 同步、冲突风险评估
- 关键词：`svn update --dry-run`, `svn status --xml -u`, `svn diff --summarize -r BASE:HEAD`, `svn log`
- 现象：尝试用 `svn update --dry-run` 预估更新风险时，命令不可用，导致同步前风险判断中断或被误判。
- 触发条件：在 `D:\project02` 或子目录里做 SVN 更新、同步、冲突评估。
- 根因：本环境的 SVN update 工作流不支持 `--dry-run`；该仓库还有大量本地改动和未版本化文件，必须先做状态和远端差异评估。
- 解决方案：使用 `svn status --xml -u <path>` 查看本地/远端状态，使用 `svn diff --summarize -r BASE:HEAD <path>` 看远端形状，配合 `svn log` 查版本历史。存在重叠风险时先备份再更新。
- 验证方式：确认状态输出能列出本地修改、远端修订和冲突；更新前有备份路径；更新后运行对应 Maven 编译或测试。
- 涉及文件/模块：常见于 `terminal-gateway`，也适用于整个 SVN 工作副本。
- 预防规则：Agent 不得再建议或执行 `svn update --dry-run` 作为风险评估方式。

### DBG-20260706-002 Git/SVN 混合工作区不能只看一种状态

- 首次记录：2026-07-06
- 领域：版本控制、提交/同步安全
- 关键词：`git status --short`, `git log`, `svn status`, unversioned
- 现象：只看 Git 历史或 Git 状态会漏掉 SVN 工作副本状态、未版本化文件或 SVN 冲突；只看 SVN 又可能漏掉 Git 暂存/提交边界。
- 触发条件：做 review、提交、同步、回滚、生成变更说明或判断最近风格。
- 根因：`D:\project02` 同时存在 `.git` 和 `.svn`，历史上 Git 提交和 SVN 修订并不等价。
- 解决方案：开始改动前先看 `git status --short`；涉及 SVN 同步时再看 `svn status --depth immediates` 或更精确的 `svn status --xml -u <path>`。提交或汇报时只纳入本轮任务相关文件。
- 最近复发：2026-07-14，`terminal-gateway` 根目录已显示 SVN r242，但递归节点仍混有 r214/r215，并保留 18 个 tree conflict；远端新 `ResultData`/`model.params.request` API 与冲突保留的旧 `CommandResult`/参数类混编，导致 reactor 在 `gateway-device-protocol` 首先编译失败。最终未在冲突树上继续修补，而是新建干净 r242 工作副本，仅白名单迁入 `gateway-device-controller` 和 `gateway-udp-proxy`，并用 589 个冻结文件 SHA-256 前后零差异证明未修改 core/protocol/transport。
- 最近复发：2026-07-15，`publish-gateway/gateway-udp-proxy` 有 66 个仍在使用的 Java 节点被本地 SVN 调度删除，但文件被 Git 保留在磁盘，直接提交会误删远端源码。处理时先备份 141 个源码文件，再用干净 SVN r245 副本确认 22 个最小删除根全部仍存在，随后仅对这些根取消调度删除并恢复备份；141 个文件 SHA-256 零差异，目标树剩余删除数为 0，发布网关定向编译通过。
- 验证方式：最终 diff 或 status 里能明确区分本轮改动与已有脏改。
- 涉及文件/模块：全仓库。
- 预防规则：不能把 Git 最近 20 次提交当作唯一团队风格来源；必要时直接读当前代码。物理文件仍存在不代表 SVN 状态安全，发现 `Schedule: delete` 时禁止直接提交，必须先备份、用干净 HEAD 副本核对，再按最小路径取消调度删除并做哈希验证。`svn info` 的根修订号也不能证明子树一致，更新后必须同时检查 `svnversion`、递归 tree conflict 和目标模块编译；冲突数量较多时优先采用干净 HEAD 副本加文件白名单迁移。

### DBG-20260706-003 monitor-platform 微服务和 monolith 路径容易漏同步

- 首次记录：2026-07-06
- 领域：管控平台部署形态、接口行为一致性
- 关键词：`monitor-platform-monolith`, `ForwardApplication`, `GatewayNetworkController`, Liquibase
- 现象：微服务模块修复或新增能力后，融合部署/monolith 路径仍保持旧行为，现场验证失败。
- 触发条件：修改 `monitor-platform-device`、`monitor-platform-forward`、`monitor-platform-content` 等微服务模块的公共接口、回调、数据库结构或链路逻辑。
- 根因：仓库同时保留微服务部署和 `monitor-platform-monolith` 聚合部署，部分 Controller、配置或 changelog 需要双路径维护。
- 解决方案：改公共行为时检查 monolith 是否有对应 Controller/Service/Mapper/changelog/配置；数据库变更同时核对 monolith 的 Liquibase master 和 changes。
- 验证方式：分别编译或测试被改微服务模块和 `monitor-platform-monolith`；必要时做接口路径对照。
- 涉及文件/模块：`monitor-platform/*`, `monitor-platform/monitor-platform-monolith`。
- 预防规则：最终回复必须说明是否检查 monolith 路径，或为什么本次不涉及。
- 最近复发：2026-07-16，微服务侧 `InfoBoardModelVendorController`、`/device/unified/info-board/manual`、`/api/mqtt/commands/*` 和 `/mqtt/acl` 已存在，但 `monitor-platform-monolith` 缺对应 Controller/DTO/Service/实体字段。
- 新增验证：新增 `MonolithApiParityTest` 先失败于缺类/缺映射，补齐后 `monitor-platform-monolith test` 通过；`monitor-platform-device,monitor-platform-forward,monitor-platform-monolith -am -DskipTests compile` 通过。

### DBG-20260706-004 远程改 IP 流程容易出现假成功或 dryRun 写库

- 首次记录：2026-07-06
- 领域：远程设备 IP 修改、MQTT 回调、设备/链路持久化
- 关键词：`dryRun`, `GatewayNetworkController`, `IpChangeCallbackService`, `FeignDeviceIpUpdater`, `LinkNodeIpUpdater`
- 现象：验证模式请求仍然更新数据库；或者 MQTT 命令成功后，下游设备表/链路节点更新失败但接口仍返回成功。
- 触发条件：处理远程 IP 修改接口、MQTT `CHANGE_SYSTEM_IP` 命令、设备 IP 回写、链路节点 IP 同步。
- 根因：callback 路径如果不受 `dryRun` 约束会产生写库副作用；Feign 调用如果只看 HTTP 是否抛异常而不看业务响应码，会误报成功；DB 更新数被吞掉也会掩盖失败。
- 解决方案：只有非 dryRun 且 MQTT 结果满足业务成功条件时才触发持久化；Feign 适配器必须校验业务 `code`/`success`；链路节点更新数应纳入最终结果或至少进入告警日志。
- 验证方式：补 dryRun 不写库测试；模拟 Feign 业务失败；验证设备表和 `task_chain_node.device_ip` 更新计数。
- 涉及文件/模块：`monitor-platform-forward`, `monitor-platform-device`, `monitor-platform-monolith`。
- 预防规则：任何“远程命令成功”都不能直接等价于“平台持久化成功”。
- 最近复发：2026-07-08 内网 MQTT P0 复测中，`publish-gateway-25` 切换到镜像 `d9b4...` 后 `CHANGE_SYSTEM_IP dryRun=true` 通过；`terminal-gateway-26` 仍返回 `FAILED - native command failed: ip command check failed`，说明 dryRun 行为必须以实际部署镜像复测为准，不能只看本地源码。
- 解决复测：2026-07-08 用户重新部署包含 dryRun 修复的 `terminal-gateway-26` 镜像 `b8c2...` 后，平台 API 到 `terminal-gateway-26` 的 `CHANGE_SYSTEM_IP dryRun=true` 返回 `SUCCESS`，replyPayload 包含 `dryRun=true` 和 planned result；非法 loopback 参数仍明确 `FAILED`，未发生真实网卡变更。

### DBG-20260706-005 QingSong/JetFileII 兼容不能只验证新接口

- 首次记录：2026-07-06
- 最近复发：2026-07-09
- 领域：终端网关设备厂商兼容、批量命令
- 关键词：`QINGSONG`, `QING_SONG`, `JET_FILE_II_STANDARD`, `TargetToSelectorMapper`, `/api/command/submit`, `/api/devices/register`, `DEVICE_NOT_LOGGED_IN`, `loggedIn`
- 现象：设备注册或 secure command 已支持 QingSong/JetFileII 别名，但旧批量命令 `/api/command/submit` 仍因枚举解析或 selector 映射失败；`/api/devices/register` 能把设备写入列表，但未走 JetFileII 登录流程时发布编排在清屏/上传步骤报 `DEVICE_NOT_LOGGED_IN`。
- 触发条件：新增或调整厂商别名、设备注册、控制命令、协议元数据、批量命令路径，或用 `/api/devices/register` 手工兜底注册青松屏幕后直接发布。
- 根因：厂商兼容分散在注册解析、发布/控制编排和旧 batch-command 目标映射等多个入口；手工注册入口只构造 `DeviceContext` 并写内存表，没有复用自动扫描/显式 IP 注册里的 `DeviceRegistrationProvider` 登录注册流水线。
- 解决方案：同时检查 `DeviceManagementController`、secure command 相关 orchestrator、`TargetToSelectorMapper`；别名统一映射到既有 `DeviceVendor`，不能各写一套规则。`/api/devices/register` 对 JetFileII/QingSong 在线注册复用 `AutoDiscoveryService.registerDevice(new ExplicitIpDiscoveredDevice(ip, port), mapping, RegistrationSource.MANUAL_IP)`，失败直接返回注册失败，不再 fallback 成“在线但未登录”的内存设备；仅 `online=false` 保留离线 seed 语义。
- 验证方式：覆盖 `QINGSONG`, `QING_SONG`, `JETFILEII`, `JET_FILEII`, `JET_FILE_II` 等别名；跑对应 Controller/selector 测试；手工注册后执行发布任务并查询 `/api/secure-command/publish-tasks/{orchestrationTaskId}`，不能再出现 `DEVICE_NOT_LOGGED_IN`。2026-07-07 本地新增并通过 `DeviceManagementControllerTest`、`AutoDiscoveryServiceTest`，命令：`mvn -s D:\project02\.tools\maven-aliyun-settings.xml -f terminal-gateway\pom.xml -pl gateway-device-core -am "-Dtest=DeviceManagementControllerTest,AutoDiscoveryServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`。2026-07-09 新增 `fixed-screen-registration.screens` 启动注册入口，固定资产屏幕仍复用 `/api/devices/register` 的协议登录注册路径，新增类用 Java 8 `javac` 独立编译通过，`FixedScreenAutoRegistrationServiceTest` 通过 Nashorn 反射调用。
- 涉及文件/模块：`terminal-gateway/gateway-device-core`, `terminal-gateway/gateway-device-protocol`。
- 预防规则：厂商兼容验收至少覆盖“注册并登录、secure command、旧 batch command”三条路径；不要把 `/api/devices` 列表可见等同于协议可执行。

### DBG-20260706-006 Windows/PowerShell 环境问题会伪装成代码问题

- 首次记录：2026-07-06
- 领域：本地命令执行、脚本、编码
- 关键词：PowerShell here-string, `npm.cmd`, UTF-8, Chinese path, `rg --files`
- 现象：Bash heredoc 在 PowerShell 中失败；`npm.ps1` 因执行策略失败；中文路径在命令输出里乱码；部分工具参数在当前 PowerShell 版本不可用。
- 触发条件：运行内联脚本、安装 npm 工具、处理中文文件名、检查二进制或文档路径。
- 根因：本机 shell 是 Windows PowerShell，不是 Bash；控制台编码和执行策略会影响命令行为。
- 解决方案：PowerShell 内联 Python 使用 `@' ... '@ | python -`；npm 使用 `npm.cmd`；中文输出前设置 `[Console]::OutputEncoding=[System.Text.Encoding]::UTF8`；中文文件定位优先用 `rg --files`。
- 验证方式：命令能直接返回预期文件或脚本输出；不要把 shell 语法错误当作项目编译错误。
- 涉及文件/模块：全仓库和本机工具链。
- 预防规则：编写命令前先按当前 shell 选择语法，不复用 Bash 写法。

### DBG-20260706-007 文档同步状态容易在最终交付中遗漏

- 首次记录：2026-07-06
- 领域：交付流程、活文档维护
- 关键词：`系统架构.md`, `监管平台端口梳理.md`, `信发管理系统功能清单.xlsx`, doc-sync
- 现象：代码、接口、配置或部署模板已变更，但最终回复没有说明三份活文档是否更新或无需调整。
- 触发条件：任何代码、配置、部署、接口、端口、模块边界、链路流程、用户可见功能变更。
- 根因：文档同步被当成事后清理，而不是交付范围。
- 解决方案：变更完成后立即检查三份文档；需要同步就同轮更新，不需要也在最终回复说明依据。
- 验证方式：最终回复列出三份文档状态；如文件发生变更，diff 只包含相关最小修改。
- 涉及文件/模块：`D:\project02\系统架构.md`, `D:\project02\监管平台端口梳理.md`, `D:\project02\信发管理系统功能清单.xlsx`。
- 预防规则：每个代码/配置/部署任务的最终回复都必须带文档同步状态。

## 新条目模板

复制以下模板追加到“条目”末尾，并在“索引”中增加一行。

### DBG-20260706-008 Terminal gateway screen discovery does not equal platform device registration

- First recorded: 2026-07-06
- Last recurrence: 2026-07-07
- Area: terminal-gateway device discovery, monitor-platform mDNS discovery, unified device registration
- Keywords: `POST /api/devices/scan`, `GET /api/devices`, `/discovery/scan`, `led_controller`, `terminal_encrypt_gateway`, `unified_device_info`, `QINGSONG`, `JET_FILE_II`
- Symptom: Direct terminal-gateway scan/list can show QingSong/JetFileII screens, but the monitor-platform search/device list does not show every screen.
- Trigger: Troubleshooting LAN QingSong broadcast search from the platform or comparing terminal-gateway `/api/devices` with monitor-platform `/device/unified/page`.
- Root cause: The repo has two discovery domains. Terminal-gateway screen scan uses `AutoDiscoveryService` and `device.discovery.mappings`; platform `/discovery/scan` is mDNS service discovery and filters device types to gateway/server services. Terminal-gateway mDNS advertises discovered screens as `led_controller`, but the platform mDNS whitelist and registration bridge do not accept that type, so discovered screens are not automatically inserted into `unified_device_info`.
- Solution: First verify the terminal endpoint with `POST /api/devices/scan` and `GET /api/devices`. Then verify platform rows with `/device/unified/page`. If platform-side auto-registration is required, add an explicit, reviewed bridge for LED screen registration instead of assuming `/discovery/register-scanned` handles screen devices.
- Verification: On 2026-07-06, `http://192.168.1.26:8093/api/devices` and `POST /api/devices/scan` returned `192.168.113.239:9520` and `192.168.113.88:9520`; `http://192.168.1.25:8080/device/unified/page?pageNum=1&pageSize=50` contained `192.168.113.239` but not `192.168.113.88`; `POST /discovery/scan` returned only mDNS service nodes such as `terminal_encrypt_gateway`, `publish_gateway`, and `publish_server`.
- Files/modules: `terminal-gateway/gateway-device-core`, `terminal-gateway/gateway-udp-proxy/discovery`, `monitor-platform-content/service/discovery`, `monitor-platform-device`.
- Prevention rule: Do not diagnose QingSong search only from the platform mDNS endpoint. Always compare terminal-gateway screen registry, platform unified device rows, and the intended sync bridge separately.

### DBG-20260706-009 Terminal gateway image fails before SVAC gate validation because DeviceCapability name is duplicated

- First recorded: 2026-07-06
- Last recurrence: 2026-07-17
- Area: terminal-gateway startup, device protocol capability registry, remote deployment validation
- Keywords: `DeviceCapability`, `DEVICE_SEARCH`, `gateway-device-protocol`, `gateway-device-core`, `DeviceManagementController`
- Symptom: The new `gateway-udp-proxy` container on `192.168.1.26` repeatedly exits before `http://127.0.0.1:8093/api/secure-command/health` becomes reachable.
- Trigger: Recreating `/opt/terminal-gateway/gateway-udp-proxy` for SVAC USB module gate testing.
- Root cause: Application startup fails while creating `DeviceManagementController`; `DeviceCapability.of(DeviceCapability.java:54)` throws `IllegalStateException: DeviceCapability name duplicated: "DEVICE_SEARCH"`. The same failure occurs with `VAUTH_MOCK_MODE=true` and `VAUTH_MOCK_MODE=false`, so it is not caused by SVAC gate settings or UKey real/mock mode.
- Solution: Define `DEVICE_SEARCH` once in `CommonDeviceCapability` and let `DeviceManagementController` plus `ActionMapper` reference the shared constant. Keep `DeviceCapability` duplicate-name detection enabled. Also keep `DeviceManagementService` as the write facade by delegating register/online/offline/remove to `DeviceRegistryManager`.
- Verification: On 2026-07-06, remote logs from container `gateway-udp-proxy` on `192.168.1.26` showed `DeviceCapability 名称重复: "DEVICE_SEARCH"` in both mock and real VAuth modes; the health endpoint on port `8093` refused connections while the container kept restarting. Local verification after the fix passed `mvn -s outputs\svn-sync-backup\terminal-gateway-20260702-192756\maven-settings-aliyun.xml -f terminal-gateway\pom.xml -pl gateway-device-core -am "-Dtest=DeviceCapabilityRegistrationTest,DeviceManagementControllerTest,DeviceProtocolMetadataControllerTest,DeviceProtocolMetadataServiceTest,SecureCommandSelectorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` with 53 tests and 0 failures, and full gateway package completed successfully.
- Files/modules: `terminal-gateway/gateway-device-protocol`, `terminal-gateway/gateway-device-core`, `terminal-gateway/gateway-udp-proxy`.
- Prevention rule: When terminal gateway health fails during hardware/config tests, inspect startup logs first and separate application bootstrap failures from UKey/SVAC/USB configuration issues.

### DBG-20260706-010 Terminal gateway package fails on Maven Central PKIX while resolving Netty native runtime artifacts

- First recorded: 2026-07-06
- Last recurrence: 2026-07-06
- Area: terminal-gateway build, Maven dependency resolution, local JDK trust store
- Keywords: `Maven Central`, `PKIX`, `netty-all`, `gateway-device-transport`, `netty-transport-native-epoll`, `netty-transport-native-io_uring`, `netty-codec-native-quic`, `maven-settings-aliyun.xml`
- Symptom: `terminal-gateway` `package` fails in `gateway-device-transport` while resolving Netty native runtime artifacts from `https://repo.maven.apache.org/maven2`.
- Trigger: Running Maven without a mirror on this Windows host after Netty runtime artifacts were cached from a repository ID that is not available in the current build context.
- Root cause: The local Java/Maven trust chain cannot validate Maven Central's certificate path, producing `PKIX path building failed`; this is an environment trust-store issue, not a terminal-gateway source error.
- Solution: Use the existing mirror settings `outputs\svn-sync-backup\terminal-gateway-20260702-192756\maven-settings-aliyun.xml` for local packaging, or repair/import the correct CA certificates into the JDK trust store before using Maven Central directly.
- Verification: On 2026-07-06, the same package command failed without `-s` on Maven Central PKIX, then succeeded with `-s outputs\svn-sync-backup\terminal-gateway-20260702-192756\maven-settings-aliyun.xml`.
- Files/modules: `terminal-gateway/gateway-device-transport`, `terminal-gateway/pom.xml`, local Maven/JDK configuration.
- Prevention rule: For this workspace, when terminal-gateway package fails on Netty native runtime downloads, first retry with the known Aliyun settings before changing POM dependencies or source code.

### DBG-20260706-011 Publish gateway transparent info-board IP bind requires a local route on the gateway host

- First recorded: 2026-07-06
- Last recurrence: 2026-07-06
- Area: publish-gateway transparent UDP/TCP proxy, monitor-platform chain deployment, onsite Linux routing
- Keywords: `192.168.113.88`, `49_B1`, `/udp-proxy/config`, `BindException`, `Cannot assign requested address`, `ip route add local`, `ip route del local`, `ARP`, `DEVICE_NOT_LOGGED_IN`
- Symptom: `POST /chain/deploy/{chainId}` returns partial success: terminal gateway succeeds, but publish gateway fails. Publish gateway logs show `UdpProxyServer` failing to start for the branch rule with `java.net.BindException: Cannot assign requested address`. In later secure-publish tests, a leftover transparent VIP can also make the terminal gateway think `192.168.113.88` is reachable while ARP resolves that address to the publish gateway MAC instead of the real screen.
- Trigger: Deploying a transparent chain where the publish gateway rule listens on the final info-board IP, for example `192.168.113.88:9520`, but the publish gateway host does not treat that IP as local; or leaving that local route/VIP in place while testing terminal-gateway direct delivery to the real board IP.
- Root cause: The publish gateway `UdpProxyConfigController` intentionally converts platform config to a transparent rule whose `listenIp` is `infoBoardIp`. Linux cannot bind that address unless it is assigned locally, `ip_nonlocal_bind` is enabled with an appropriate local-delivery setup, or a local route/VIP is present. That same local route/VIP can later hijack terminal-gateway-to-screen traffic because neighboring hosts resolve the board IP to the publish gateway MAC. The failure is not caused by UKey, SVAC mode, SM2/SM3 encryption, or terminal gateway startup.
- Solution: Record the current network state, then add a host-local route for the test VIP before redeploying the transparent chain, for example `ip route add local 192.168.113.88/32 dev lo`. Prefer a `local` route for tests to avoid adding the board IP directly to the NIC and creating ARP conflicts. Before secure-delivery tests where terminal gateway must directly reach the physical board, stop the transparent rule and remove the test local route with `ip route del local 192.168.113.88/32 dev lo table local`, then flush stale ARP on the terminal gateway host.
- Verification: On 2026-07-06, `192.168.1.25` initially had `192.168.113.50/24` but no local route for `192.168.113.88`; deploy of chain `49` failed with `Cannot assign requested address`. After backing up network state to `/opt/publish-gateway/gateway-udp-proxy/backups/network-svac-chain49-20260706201509.txt` and adding `local 192.168.113.88 dev lo`, `POST /chain/deploy/49` returned success for publish gateway, terminal gateway, and publish client. `ss -luntp` then showed Java listening on `[::ffff:192.168.113.88]:9520`, and the client rule `49_B1` pointed to terminal gateway `192.168.1.26:9526`. On 2026-07-07, secure-delivery testing showed terminal gateway ARP for `192.168.113.88` resolving to the publish gateway MAC `00:d0:b4:01:62:38`; after disabling chain `49`, backing up network state to `D:\project02\outputs\qingsong-live\publish-gateway-25-network-before-remove-88-20260707124657.txt`, deleting the local route, and flushing terminal-gateway ARP, `192.168.113.88` became unresolved from `192.168.1.26`, proving prior reachability was the VIP, not the real screen.
- Files/modules: `publish-gateway/gateway-udp-proxy`, `monitor-platform/monitor-platform-forward`, onsite host routing on `192.168.1.25`.
- Prevention rule: Before diagnosing publish-gateway source code for transparent chain deploy failures, compare the rule `listenIp` with `ip route show table local` and `ss -luntp`; if the target board IP is not locally deliverable, fix the host route/VIP plan first.

### DBG-20260706-012 SVAC USB gate must select the VIMICRO module, not the UKey sysfs path

- First recorded: 2026-07-06
- Last recurrence: 2026-07-06
- Area: publish-gateway and terminal-gateway SVAC physical gate, onsite USB device selection
- Keywords: `VAUTH_SVAC_MODULE_DEVICE_PATH`, `VAUTH_SVAC_MODULE_VENDOR_ID`, `VAUTH_SVAC_MODULE_PRODUCT_ID`, `0ac8`, `7180`, `VIMICRO`, `3a59`, `4458`, `USBKEY`
- Symptom: Pulling the actual SVAC USB module does not interrupt the data path, but pulling a UKey does interrupt it. A later recurrence showed control-command encryption still logging `USB_DEVICE_NOT_FOUND:vendorId=0ac8, productId=7180` even when `config/application.yml` visually showed `required: ${VAUTH_SVAC_MODULE_REQUIRED:false}`.
- Trigger: `VAUTH_SVAC_MODULE_REQUIRED=true` is enabled while `VAUTH_SVAC_MODULE_DEVICE_PATH=/sys/bus/usb/devices/1-1` points at a `3a59:4458 USBKEY` instead of the SVAC module, or while the VIMICRO module is unplugged. In Docker Compose deployments, `.env` is loaded through `env_file`, so `VAUTH_SVAC_MODULE_REQUIRED=true` overrides the `application.yml` placeholder default of `false`.
- Root cause: The physical SVAC gate was bound to a volatile sysfs path that belonged to UKey. The real SVAC module appeared as `0ac8:7180 VIMICRO` and its sysfs path changed after replug, for example from `1-5` to `1-6`.
- Solution: Clear `VAUTH_SVAC_MODULE_DEVICE_PATH` and configure `VAUTH_SVAC_MODULE_VENDOR_ID=0ac8`, `VAUTH_SVAC_MODULE_PRODUCT_ID=7180`, leaving UKey selection untouched. Recreate the gateway containers so the environment variables take effect.
- Verification: On 2026-07-06, after changing both gateways to `vendorId=0ac8/productId=7180`, pulling VIMICRO from `192.168.1.25` made publish-gateway log `USB_DEVICE_NOT_FOUND:vendorId=0ac8, productId=7180`; replugging VIMICRO as sysfs path `1-6` removed the SVAC gate error and the test packet proceeded to the later source-IP whitelist check. UKey devices `3a59:4458 USBKEY` remained present and were no longer the SVAC gate selector. On 2026-07-09, publish-gateway container env still showed `VAUTH_SVAC_MODULE_REQUIRED=true` while `VAUTH_SVAC_MODE=false`; this proved that `svac-mode=false` disables only the PackData API choice, not the independent physical USB gate.
- Files/modules: remote `.env` in `/opt/publish-gateway/gateway-udp-proxy` and `/opt/terminal-gateway/gateway-udp-proxy`, `publish-gateway/gateway-udp-proxy/service/SvacModulePresenceGuard`, `terminal-gateway/gateway-crypto/service/SvacModulePresenceGuard`.
- Prevention rule: Before any SVAC unplug test, print `/sys/bus/usb/devices/*/idVendor`, `idProduct`, and `product`; verify that the configured selector matches `VIMICRO 0ac8:7180`, not `USBKEY 3a59:4458`. When switching to UKey-only mode, change `.env` to `VAUTH_SVAC_MODULE_REQUIRED=false` and recreate/restart the gateway container; do not rely on the placeholder default in `application.yml` while the environment variable is set.

### DBG-20260707-013 VAuth microservice URL overrides can silently route terminal key queries to the old monolith port

- First recorded: 2026-07-07
- Last recurrence: 2026-07-07
- Area: publish-gateway and terminal-gateway VAuth real crypto, monitor-platform UKey microservice deployment
- Keywords: `CONTROL_PLATFORM_URL`, `MONITOR_UKEY_PORT`, `MONITOR_DEVICE_PORT`, `PROBE_REPORT_URL`, `SNAPSHOT_REPORT_URL`, `8063`, `8080`, `未找到会话密钥`, `QueryKeyCallback`
- Symptom: Publish gateway and terminal gateway both start in `VAUTH_MOCK_MODE=false` and complete UKey authentication, but secure publish fails on terminal decrypt. Terminal logs show `QueryKeyCallback` requesting the publish gateway authId/version and then `未找到会话密钥`, while `monitor-ukey` on `8063` has already cached that session key.
- Trigger: Running microservices on `192.168.1.25` while a gateway deploy `.env` still contains explicit monolith URLs such as `CONTROL_PLATFORM_URL=http://192.168.1.25:8080`, even after `MONITOR_UKEY_PORT=8063` is configured.
- Root cause: The mounted terminal gateway `application.yml` binds `vauth.control-platform-url` from `CONTROL_PLATFORM_URL` when present, so the explicit URL has higher effective priority than `MONITOR_HOST`/`MONITOR_UKEY_PORT`. The terminal gateway therefore queried the old `8080` endpoint instead of `monitor-ukey:8063`, causing a false missing-session-key error.
- Solution: In microservice deployments, set `CONTROL_PLATFORM_URL=http://<monitor-host>:8063`, `PROBE_REPORT_URL=http://<monitor-host>:8062/device/unified/batch-status`, and `SNAPSHOT_REPORT_URL=http://<monitor-host>:8065/content/receive`; keep `MONITOR_UKEY_PORT=8063`, `MONITOR_DEVICE_PORT=8062`, `MONITOR_CONTENT_PORT=8065`, and recreate the gateway container.
- Verification: On 2026-07-07, `192.168.1.26` initially had `MONITOR_UKEY_PORT=8063` but container env still showed `CONTROL_PLATFORM_URL=http://192.168.1.25:8080`; secure publish `REQ-QS-REALREADY2-PUBLISH-20260707145527` failed with `密文解密失败: USB_DEVICE_PRESENT:/sys/bus/usb/devices/1-5` and terminal logs showed `未找到会话密钥: 44030000003330000305_2026-07-07T14:40:16`. After backing up `.env` as `.env.bak-microservices-url-20260707153415`, changing the URLs to `8063/8062/8065`, and recreating the terminal gateway, publish `REQ-QS-REALFINAL-PUBLISH-20260707153613` completed with delivery task `DLV-3a57414e` status `SUCCESS`.
- Files/modules: remote `/opt/terminal-gateway/gateway-udp-proxy/.env`, `terminal-gateway/gateway-udp-proxy/src/main/resources/application.yml`, `terminal-gateway/gateway-crypto`, `monitor-platform/monitor-platform-Ukey`.
- Prevention rule: When VAuth real-mode decrypt reports missing session key, compare the gateway container's effective `CONTROL_PLATFORM_URL` with the intended UKey service port before restarting UKey or changing crypto code.

### DBG-20260707-014 Content publish must aggregate secure-delivery terminal status

- First recorded: 2026-07-07
- Area: info-publish-client content publish, publish-gateway secure delivery, front-end result semantics
- Keywords: `/api/client/publish/execute`, `/api/secure-delivery/tasks`, `/api/secure-delivery/tasks/{deliveryTaskId}`, `DELIVERY_FAILED`, `success=false`, file download failed
- Symptom: `POST /api/client/publish/execute` receives a `deliveryTaskId` from the publish gateway and returns success, while the publish gateway worker later fails to download a file and records task status `FAILED`; the front end can show delivery success without the real failure message.
- Trigger: Content publish task creation succeeds, but an asynchronous secure-delivery sub-step fails after creation, such as file download `No route to host`, hash mismatch, encryption failure, terminal gateway failure, timeout, or task status lookup failure.
- Root cause: The client publish entry treated task creation as final success and did not query the publish gateway task status endpoint before building the main response.
- Solution: After creating the secure-delivery task, query `/api/secure-delivery/tasks/{deliveryTaskId}` until a terminal state. Map `FAILED`, `TIMEOUT`, `CANCELED`/`CANCELLED`, missing task, or query failure to `ContentPublishResponse.success=false`, `code=DELIVERY_FAILED`, preserving `delivery.deliveryTaskId` and `delivery.status`.
- Verification: On 2026-07-07, `ContentPublishServiceImplRegressionTest` first reproduced the failure where secure-delivery status `FAILED` still produced `success=true`; after the fix, the regression runner passed both `FAILED -> success=false` and `SUCCESS -> success=true`. Compile passed with `D:\project02\.tools\apache-maven-3.9.9\bin\mvn.cmd -f info-publish-client\pom.xml -DskipTests test-compile`.
- Recent recurrence: On 2026-07-08, ColorLight publish `REQ-CL-PUBLISH-20260708161847` returned `code=ACCEPTED` from the client entry while the publish gateway and terminal gateway were still processing. Follow-up queries showed publish gateway task `DLV-5d8fd435` reached `SUCCESS`, and terminal gateway steps `CLEAR_MEDIA`, `FILE_UPLOAD`, and `PLAYLIST_SET` all reached `SUCCESS`. This confirms callers must use the task status endpoint when they need final delivery evidence.
- Files/modules: `info-publish-client/src/main/java/com/infopublish/client/service/impl/ContentPublishServiceImpl.java`, `info-publish-client/src/test/java/com/infopublish/client/service/impl/ContentPublishServiceImplRegressionTest.java`, `publish-gateway/gateway-udp-proxy`.
- Prevention rule: For any entry API that starts an asynchronous gateway task, do not equate task creation with business success; either wait for terminal status or clearly return an explicit non-final state that the front end cannot display as final success.

### DBG-20260708-015 Gateway MQTT probe commands must not fall back to `/udp-proxy/config`

- First recorded: 2026-07-08
- Area: publish-gateway and terminal-gateway MQTT command dispatch semantics
- Keywords: `QUERY_STATUS`, `NOOP`, `ECHO`, `GatewayCommandDispatcher`, `LocalHttpForwardService`, `code=500`, `up/reply`
- Symptom: Platform MQTT command records can show success or ambiguous results even though a gateway `QUERY_STATUS` command fell back to `/udp-proxy/config`, or an HTTP action returned `httpStatus=200` with business `code=500`.
- Trigger: Using non-destructive MQTT commands to verify business takeover, or routing an action through local HTTP loopback where the controller returns JSON with both `success=true` and a failing business `code`.
- Root cause: Gateway dispatch treated empty-action commands as default `SELF_APPLY /udp-proxy/config`, and action aggregation trusted only `success` instead of also checking HTTP status and business `code`.
- Solution: Handle `QUERY_STATUS` / `NOOP` / `ECHO` natively in gateway `GatewayCommandDispatcher`, return `SUCCESS` payload without HTTP loopback, and mark action results as failed when HTTP status is non-2xx or business `code` is present and not 200. Keep `LocalHttpForwardService` allowlists as exact path or child path matches. Keep probe-command string constants local to each dispatcher instead of depending on `MqttCommandMessage.COMMAND_*`, because stale IDE/Maven classpaths can compile against an older `monitor-platform-mqtt-core`.
- Verification: On 2026-07-08, `GatewayCommandDispatcherTest` first failed because publish-gateway `QUERY_STATUS` called `/udp-proxy/config`, `code=500` returned final `SUCCESS`, and `/api/client/commands-bad` passed the allowlist. After the fix, the same test class passed 3 tests with Maven using the Aliyun settings file. Later the terminal-gateway dispatcher compile error for missing `MqttCommandMessage.COMMAND_QUERY_STATUS` / `COMMAND_NOOP` / `COMMAND_ECHO` was removed by switching the dispatcher to private local constants; module compilation then continued to a separate `TerminalGatewayMdnsRegistration` / `DeviceRegisteredEvent` dependency error.
- Files/modules: `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/mqtt/GatewayCommandDispatcher.java`, `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/mqtt/LocalHttpForwardService.java`, `terminal-gateway/gateway-udp-proxy`.
- Prevention rule: For MQTT business takeover checks, always assert both route semantics and final reply semantics: probe commands must not call config endpoints, and any business `code != 200` must become a `FAILED` reply even when HTTP returned 2xx.

### DBG-20260708-016 Terminal gateway transport facade must match split HTTP/TCP/UDP transports

- First recorded: 2026-07-08
- Area: terminal-gateway transport layer, Netty refactor, module compile
- Keywords: `DeviceChannelPool`, `NettyTransportManager`, `HttpTransport`, `TcpTransport`, `UdpTransport`, `HttpChannelPool`, `TcpChannelPool`
- Symptom: `gateway-device-transport` compile fails in `NettyTransportManager.java` with `cannot find symbol: DeviceChannelPool` at the field and constructor references.
- Trigger: After splitting Netty transport into HTTP/TCP/UDP-specific classes and pools, compiling `terminal-gateway` or `gateway-device-transport`.
- Root cause: `DeviceChannelPool` had been removed/replaced by `HttpChannelPool` and `TcpChannelPool`, but `NettyTransportManager` still used the old pool field, constructor, TCP send path, and close path.
- Solution: Keep `NettyTransportManager` as the stable `DeviceTransport` facade and delegate to `HttpTransport`, `TcpTransport`, and `UdpTransport`; wire both HTTP/TCP pools to `ConnectionHealthChecker` and remove stale `DeviceChannelPool` references.
- Verification: `mvn -f terminal-gateway\pom.xml -pl gateway-device-transport -am "-DskipTests" compile` passed, and `mvn -f terminal-gateway\pom.xml -pl gateway-device-core -am "-DskipTests" compile` passed.
- Files/modules: `terminal-gateway/gateway-device-transport/src/main/java/com/gateway/device/transport/netty/NettyTransportManager.java`, `terminal-gateway/gateway-device-core/src/main/java/com/gateway/device/core/config/DeviceCoreAutoConfiguration.java`.
- Prevention rule: When transport pools are split or renamed, update the manager facade and core auto-configuration together; do not leave old pool types in constructor signatures.

### DBG-20260708-017 MQTT gateway idempotency table can be missing after image-only deploy

- First recorded: 2026-07-08
- Area: publish-gateway and terminal-gateway MQTT command dedup persistence, onsite deployment schema
- Keywords: `mqtt_command_record`, `MqttCommandRecordService`, `udp_proxy_gateway`, `terminal_gateway`, `SQLSyntaxErrorException`, image-only deploy
- Symptom: MQTT-enabled gateway starts and connects to EMQX, but logs `Table '<db>.mqtt_command_record' doesn't exist` during dedup cleanup or first command processing.
- Trigger: Deploying or recreating a new gateway image without applying the matching MySQL init SQL or migration for `mqtt_command_record`.
- Root cause: The image contains `MqttCommandRecordService` and mapper code, but the onsite database was created before this table existed; mounting a new image does not replay `/docker-entrypoint-initdb.d` scripts for an existing MySQL data directory.
- Solution: Before enabling MQTT agent or recreating gateways, apply an idempotent DDL for `mqtt_command_record` to `udp_proxy_gateway` and `terminal_gateway`. Verify the table exists before running duplicate-message or restart-idempotency tests.
- Verification: On 2026-07-08 LAN MQTT retest, `192.168.1.25` publish gateway image `d9b4...` logged missing `udp_proxy_gateway.mqtt_command_record`; after applying idempotent DDL, both `udp_proxy_gateway.mqtt_command_record` and `terminal_gateway.mqtt_command_record` existed, and subsequent publish-gateway commands inserted records without the missing-table error.
- Files/modules: `publish-gateway/gateway-udp-proxy`, `terminal-gateway/gateway-udp-proxy`, deploy package MySQL init SQL, onsite MySQL databases.
- Prevention rule: Treat gateway database schema as part of the image rollout checklist; do not validate MQTT dedup, duplicate messageId, or restart replay until `mqtt_command_record` is present on both gateway databases.

### DBG-20260708-018 Chain deploy must use MQTT clientId, not only task-chain node deviceId

- First recorded: 2026-07-08
- Last recurrence: 2026-07-09
- Area: monitor-platform-forward chain deployment, MQTT topic routing, publish-gateway command dispatch
- Keywords: `CHAIN_DEPLOY`, `PublishGatewayConfigServiceImpl`, `TaskChainNode.deviceId`, `publish-001`, `publish-gateway-25`, `/down/proxy-command`
- Symptom: `POST /chain/deploy/49` creates a platform MQTT command and publishes to `/default/site-001/publish-001/down/proxy-command`, then times out. EMQX has the real publish gateway connected as `publish-gateway-25`, so no gateway consumes the `publish-001` topic.
- Trigger: Task-chain publish gateway node uses a business/device ledger ID that does not equal the MQTT client/device ID currently registered by the gateway agent.
- Root cause: The chain deployment path uses `publishGwNode.getDeviceId()` as the MQTT routing device ID. Existing chain data may store logical IDs such as `publish-001`, while the deployed MQTT agent resolves and subscribes as `publish-gateway-25`.
- Solution: Resolve the MQTT routing ID before calling `dispatchViaMqtt`. The current implementation first keeps already routable IDs, then checks explicit `mqttDeviceId`/`mqttClientId`/`instanceId` fields from device detail, and finally derives the onsite gateway agent ID from gateway type plus IP last octet, e.g. `publish_gateway + 192.168.1.25 -> publish-gateway-25`. Keep a stricter registry-backed mapping as the long-term hardening path.
- Verification: On 2026-07-08, platform chain deploy for chain `49` timed out with command `9e4a13e82a4740a9a896d88b8c8d1aea` on topic `/default/site-001/publish-001/down/proxy-command`. A direct MQTT command with the same `CHAIN_DEPLOY` action sent to `/default/site-001/publish-gateway-25/down/proxy-command` was received by publish gateway and forwarded to `192.168.1.26:8093/udp-proxy/config`; terminal gateway then reported rule `49_terminal` as `RUNNING`. The code fix is covered by `PublishGatewayConfigServiceImplTest` and synced to `monitor-platform-monolith`.
- Files/modules: `monitor-platform/monitor-platform-forward/src/main/java/com/monitorplatform/forward/service/impl/PublishGatewayConfigServiceImpl.java`, task-chain data, `publish-gateway/gateway-udp-proxy`.
- Prevention rule: Before P1 chain deploy tests, compare task-chain gateway node IDs with EMQX connected client IDs and platform MQTT registration IDs. Do not assume business device IDs are routable MQTT IDs.

### DBG-20260708-019 Public platform screen control must be native MQTT to terminal gateway

- First recorded: 2026-07-08
- Last recurrence: 2026-07-16
- Area: monitor-platform-forward MQTT control delivery, monitor-platform-alarm screen control, terminal-gateway native command dispatch
- Keywords: `MqttControlDeliveryController`, `BLACKOUT`, `CUT_VIOLATION`, `GatewayCommandDispatcher`, EMQX, `up/reply`, `Feign Read timed out`, `waitTimeoutSec`, `MqttCommandTimeoutTask`
- Symptom: Black-screen or cut control may still fail at the platform with `Feign RetryableException: Read timed out` even though the command has already reached MQTT and the terminal gateway is still executing sequential device operations.
- Trigger: Alarm, Feign and Forward all use a 60-second deadline while `CUT_VIOLATION` may require several sequential device ACK operations. The HTTP waiter and timeout scanner can expire at the same time as a final MQTT reply.
- Root cause: The native MQTT route was correct, but its timeout budgets were not layered. HTTP wait expiry irreversibly persisted `TIMEOUT`, the scanner used a stale read followed by `updateById`, and the reply handler ignored a final device reply after `TIMEOUT`.
- Solution: Keep screen control on the native MQTT route. Use a 120-second business/MQTT command window and a 135-second Alarm-to-Forward Feign read timeout; HTTP wait expiry no longer finalizes the command. The timeout scanner performs an active-state conditional update, and late final replies can reconcile timed-out `BLACKOUT`/`CUT_VIOLATION` records.
- Verification: On 2026-07-16, `MqttCommandPublishServiceWaitTimeoutTest`, `MqttReplyHandlerLateControlReplyTest`, and `MqttCommandTimeoutTaskConcurrencyTest` reproduced the three races and passed after the fix. The Forward suite passed 8/8 tests, the Alarm/Forward reactor build succeeded, and the full monitor-platform compile succeeded for all 18 modules.
- Files/modules: `monitor-platform/monitor-platform-alarm`, `monitor-platform/monitor-platform-forward`, `monitor-platform/monitor-platform-monolith`, `terminal-gateway/gateway-udp-proxy`.
- Prevention rule: For public-platform screen control, reject any design that requires platform-to-gateway HTTP reachability. Prove the route as platform -> EMQX -> terminal-gateway native dispatcher -> device ACK -> MQTT final reply, and keep outer Feign read timeout greater than the inner MQTT/device execution window.

### DBG-20260709-020 Publish gateway MQTT self-apply timeout can misclassify successful chain deploy

- First recorded: 2026-07-09
- Area: publish-gateway MQTT action dispatch, chain deploy self-apply, onsite configuration
- Keywords: `CHAIN_DEPLOY`, `LocalHttpForwardService`, `MQTT_HTTP_FORWARD_TIMEOUT_MS`, `/udp-proxy/config`, `Read timed out`, `49_B1`
- Symptom: `POST /chain/deploy/49` returns partial success: terminal gateway deployment succeeds, but publish gateway deployment fails with `HTTP 转发异常: Read timed out`.
- Trigger: MQTT `CHAIN_DEPLOY` sends a publish-gateway `SELF_APPLY` action to `127.0.0.1:8092/udp-proxy/config`. The local controller stops and restarts UDP/TCP proxy rule `49_B1`, writes the rule, then forwards config to the PC client. This took slightly over the default 5000 ms HTTP forward timeout.
- Root cause: `LocalHttpForwardService` defaulted `mqtt-agent.http-forward.timeout-ms` to 5000 ms. The underlying `/udp-proxy/config` request actually completed successfully after the outer MQTT HTTP action had already timed out and published a final `FAILED` reply.
- Solution: Set the publish gateway MQTT HTTP action timeout to 15000 ms via `MQTT_HTTP_FORWARD_TIMEOUT_MS` / `mqtt-agent.http-forward.timeout-ms`. Keep this setting in source and deploy templates, and mirror it in onsite external config.
- Verification: On 2026-07-09, before the timeout change, publish gateway logs showed `49_B1` UDP/TCP proxy started and the client forward succeeded after the MQTT action had already emitted `Read timed out`. After setting `mqtt-agent.http-forward.timeout-ms=15000` on `192.168.1.25` and restarting the publish gateway, `POST /chain/deploy/49` returned `配置下发成功` with publish gateway, terminal gateway, and publish client success counts all equal to 1.
- Files/modules: `publish-gateway/gateway-udp-proxy/src/main/resources/application.yml`, `publish-gateway/deploy/application.yml`, `publish-gateway/deploy-package/application.yml`, onsite `/opt/publish-gateway/gateway-udp-proxy/config/application.yml`.
- Prevention rule: For MQTT action paths that call local controllers doing rule restart, client forwarding, or other multi-step side effects, size the HTTP action timeout to the full controller duration and verify final reply logs, not only the side effect logs.

### DBG-20260709-021 V1 Qingsong content publish file-size limit must use long counters

- First recorded: 2026-07-09
- Area: info-publish-client V1 Qingsong content publish, local file-manager callback, large video validation
- Keywords: `ContentPublishServiceImpl`, `content-publish.max-file-size-mb`, `2048`, `节目文件超过大小限制`, `ByteArrayOutputStream`, `SHA-256`
- Symptom: V1 `POST /api/client/publish/execute` rejects a large Qingsong video with `节目文件超过大小限制`, even when the active `dist/config/application.yml` sets `content-publish.max-file-size-mb=2048`.
- Trigger: A video around 1.67 GB is published through the V1 Qingsong callback path. The same bug can be reproduced with a tiny file when `maxFileSizeMb=2048`.
- Root cause: `ContentPublishServiceImpl.download()` calculated `maxBytes` with `int`: `2048 * 1024 * 1024` overflows to a negative Java `int`, so the first read is treated as over the limit. The method also buffered the whole file in `ByteArrayOutputStream` only to calculate SHA-256, which is unsafe for multi-GB videos.
- Solution: Keep the V1 API contract unchanged, but stream the download during file verification: calculate SHA-256 incrementally with `MessageDigest`, count bytes with `long`, and derive `maxBytes` with `long` multiplication. Do not change the V2 publish interface.
- Verification: `ContentPublishServiceImplRegressionTest#configuredTwoGigabyteLimitDoesNotOverflow` first failed with `节目文件超过大小限制` for a 4-byte BMP under `maxFileSizeMb=2048`; after the fix, the direct regression runner passed the 2048 MB case, V1 video missing-duration defaulting, secure-delivery failure propagation, and secure-delivery success cases. `D:\project02\.tools\apache-maven-3.9.9\bin\mvn.cmd -f info-publish-client\pom.xml -DskipTests test-compile` passed.
- Files/modules: `info-publish-client/src/main/java/com/infopublish/client/service/impl/ContentPublishServiceImpl.java`, `info-publish-client/src/test/java/com/infopublish/client/service/impl/ContentPublishServiceImplRegressionTest.java`, `info-publish-client/dist/config/application.yml`.
- Prevention rule: For file-size limits expressed in MB, convert to bytes with `long` before multiplying, and do not allocate a full byte array for files that may exceed normal heap size.

### DBG-20260709-022 Secure delivery large video must use file references and streaming upload

- First recorded: 2026-07-09
- Last recurrence: 2026-07-15; acceptance expanded from a 2GB video to at least 5GB, and the QingSong screen's built-in FTP service on control port 29520 became the preferred terminal-gateway-to-screen transport for playlists containing video.
- Area: publish-gateway secure delivery, terminal-gateway secure command, JetFileII media upload
- Keywords: `SecureDeliveryServiceImpl`, `StandardizedPublishPackage`, `FILE_REF`, `MediaUploadParams.sourceFile`, `FileTransfer.writePathFile`, `RUNNING`, `OutOfMemoryError`, `contentBase64`
- Symptom: A V1 Qingsong video around 1.67 GB is accepted by the client and sent to publish-gateway, but publish-gateway keeps the delivery task in `RUNNING`; terminal-gateway never receives the matching secure publish request.
- Last recurrence: 2026-07-10; after fileRef reached terminal-gateway, the same large video failed at device upload with `request 192.168.113.88:9520:20 timeout` because normal JetFileII `WRITE_PATHFILE` tried to avoid the 65535 pack limit by inflating UDP chunks to about 27KB.
- Trigger: A video item is delivered through `/api/secure-delivery/tasks` while publish-gateway has limited heap, for example `-Xmx512m`.
- Root cause: The secure delivery path downloaded every file into `byte[]`, Base64-encoded it into `contentBase64`, serialized the whole package JSON, then encrypted it. Multi-GB videos can exhaust heap before the terminal-gateway HTTP call is made. The worker catch block handled `Exception` only, so `OutOfMemoryError` could bypass final failure status updates. A later device-side recurrence showed that normal JetFileII `WRITE_PATHFILE` has a 16-bit pack count; increasing each UDP payload to keep `totalPacks <= 65535` creates fragmented datagrams that the screen does not answer reliably. JetFileII 2.9.15 defines `WRITE_BIGFILE_CRC (0x0215)` with 4-byte file/chunk/total/current fields and CRC32, but marks `QS0925` unsupported. Pooling TCP introduced another trap: the old response handler waited for channel close, so every acknowledged checkpoint could consume the full request timeout.
- Solution: Keep images and existing small-file behavior on the old inline path, but send video entries as a `FILE_REF` manifest containing URL, SHA-256, size, and file name. Terminal-gateway downloads the referenced file to a temp file, verifies hash/size, and passes `MediaUploadParams.sourceFile` to JetFileII so upload is streamed from disk. Secure delivery workers catch `Throwable` and mark the task failed instead of leaving it `RUNNING`. Normal JetFileII writes retain 1024-byte chunks and switch to `WRITE_PATHFILE_EXT (0x020D)` past 65535 packets. A new default-off `WRITE_BIGFILE_CRC_TCP` mode sends `0x0215` over TCP with file CRC32, no-reply intermediate packets, an ACK every configured checkpoint, and a final ACK; chunks are capped at the protocol limit of 1500 bytes. `QS0925` is rejected before transport send. Known 18-byte status replies complete without closing the pooled TCP channel. On orchestration timeout the fileRef temp file is retained for an in-flight device task and registered for JVM-exit cleanup instead of being deleted mid-stream. This legacy JetFileII write mode still rejects files at or above 2GB; the 2026-07-15 FTP route below removes that device-upload boundary for QingSong.
- 2026-07-15 solution: Keep the client-to-publish-gateway and publish-gateway-to-terminal-gateway contracts unchanged. In `gateway-device-controller`, route only QingSong/JetFileII playlists that contain video to one `MEDIA_MULTI_UPLOAD_FTP` batch, including all images and videos in original order. Do not send `FTP_SWITCH` and do not run a second outer `PLAYLIST_SET`; the existing FTP handler owns media upload and playlist write. Allow only one active FTP task per screen, calculate timeout from total bytes and a configured minimum throughput, and retain FILE_REF temp files and the screen lock until a timed-out underlying task actually completes. Image-only playlists and other vendors remain on the legacy path.
- Verification: On 2026-07-10, `FileTransferStreamingUploadTest` and `JetFileIIResourceUploadHandlerLargeFileTest` passed 11 cases covering 1500-byte clamping, packet serials, CRC32 fields, checkpoint/no-reply behavior, wrong-serial ACK rejection, source-file gating, QS0925 rejection, and TCP routing. `PublishPackageOrchestratorFileRefTest`, `NettyTransportManagerRoutingTest`, and `TcpResponseHandlerTest` passed 10 focused cases covering FILE_REF-only mode gating, inline compatibility, timeout sizing, deferred temp-file cleanup, framed TCP routing, and ACK completion without channel close. The full `gateway-device-core -am test` run passed 11 protocol tests and 74 core tests; the assembly compile also passed through `gateway-udp-proxy`. The reactor compiled 411 protocol sources, 16 transport sources, and 62 core sources on Java 8. The default Maven Central route still fails with the local JDK PKIX chain, so validation used the existing local `aliyun-public` artifact cache in offline mode.
- 2026-07-15 verification: `PublishPackageOrchestratorFtpRoutingTest` passed 5 cases for mixed-media single-batch routing, same-screen rejection, 5GiB dynamic timeout, timeout lifecycle cleanup, and unsafe file-name rejection. The focused client timeout runner passed, and Java 8 reactor compilation succeeded through terminal `gateway-udp-proxy`, publish `gateway-udp-proxy`, and `info-publish-client`.
- Files/modules: `publish-gateway/gateway-standardization`, `publish-gateway/gateway-udp-proxy`, `terminal-gateway/gateway-device-controller`, `terminal-gateway/gateway-device-core`, `terminal-gateway/gateway-device-protocol`, `terminal-gateway/gateway-device-transport`, `info-publish-client`, and gateway deployment templates.
- Prevention rule: Do not represent large video media as heap-resident `byte[]`, Base64, or one giant JSON payload. Never enlarge JetFileII chunks beyond 1500 bytes to reduce packet count. Treat `0x0215` as an explicit device-capability rollout, reject known unsupported hardware, use framed response completion on persistent TCP, and never delete a temporary source file while its device task may still be running. FTP routing must remain vendor-gated, must not send `FTP_SWITCH`, and must keep the screen lock until the underlying transfer completes.

### DBG-20260709-023 SECURE_PUBLISH media detection requires MinIO bucket and endpoint alignment

- First recorded: 2026-07-09
- Last recurrence: 2026-07-09
- Area: publish-gateway SECURE_PUBLISH content report, monitor-platform-content local audit, MinIO deployment config
- Keywords: `MINIO_BUCKET_NAME`, `MINIO_BUCKET`, `CONTENT_MINIO_ENDPOINT`, `monitor-content`, `monitor-platform`, `图片准备失败`, `MinIO下载失败: status=403`
- Symptom: Publish gateway logs `delivered media reported` after uploading a SECURE_PUBLISH image, but `t_content_monitor` keeps the image row in `pending` with `reason=图片准备失败` and an `error-*` request id. Content logs show `从MinIO下载图片` followed by `MinIO下载失败: status=403`.
- Trigger: Publish gateway uploads delivered media to one MinIO bucket/endpoint, while monitor-content reads from another bucket/endpoint or from a bucket without anonymous download permission. On 2026-07-09, publish gateway used `192.168.1.31:9000/monitor-content`, while monitor-content still used local `127.0.0.1:19000/monitor-platform`.
- Root cause: Runtime MinIO configuration drift. Platform `.env` had `MINIO_BUCKET_NAME=monitor-platform` and `CONTENT_MINIO_ENDPOINT=http://127.0.0.1:19000`, while publish gateway had `MINIO_BUCKET=monitor-content` and `MINIO_HOST=192.168.1.31`. Because `LocalAuditService` reads images through plain HTTP GET, private buckets return 403 and wrong endpoints return 404.
- New evidence (2026-07-17): task-level image `FILE_REF` delivery deliberately keeps `SecurePublishDeliveredFile.data=null` so the terminal gateway can fetch media by URL without creating a large publish-gateway heap payload. `SecurePublishContentReportStrategy` previously treated that as `MEDIA_DATA_EMPTY` and returned before both MinIO upload and `/content/detection/detect`; the screen could receive all images while the regulatory platform received no media record.
- Solution: Align both sides to the same object store and bucket. For the verified 2026-07-09 onsite fix, set platform `.env` `MINIO_BUCKET_NAME=monitor-content`, set `CONTENT_MINIO_ENDPOINT=http://192.168.1.31:9000`, recreate `monitor-content`, and set MinIO anonymous download policy for `monitor-content`. In source/deploy templates, default platform buckets to `monitor-content` and allow `minio.bucket-name` to resolve from either `MINIO_BUCKET` or `MINIO_BUCKET_NAME`.
- 2026-07-17 solution: Keep the existing `monitor-content` bucket implementation unchanged. For delivered media without in-memory bytes, publish-gateway fetches the FILE_REF URL over HTTP/HTTPS into a size-limited temporary file, calculates SHA-256 incrementally, validates optional `fileHash`, then streams the verified file to the existing MinIO service and posts its `minioPath`, actual hash, and size to content detection. Inline byte-array media remains on the existing path.
- Verification: `docker exec monitor-content env` showed `MINIO_BUCKET_NAME=monitor-content` and `MINIO_ENDPOINT=http://192.168.1.31:9000`; `curl http://127.0.0.1:8065/actuator/health` returned `{"status":"UP"}`; `curl -o /dev/null -s -w '%{http_code}' http://192.168.1.31:9000/monitor-content/images/2026/07/09/3ae701db0bb4.bmp` returned `200`.
- 2026-07-17 verification: `FileRefMediaUploadServiceTest`, `MinioUploadServiceStreamTest`, and `SecurePublishDeliveredContentReportStrategyTest` passed 8 focused tests. Full `gateway-udp-proxy -am test` passed 27 tests, and `gateway-udp-proxy -am -DskipTests package` produced the Spring Boot JAR.
- Files/modules: `monitor-platform/deploy`, `monitor-platform/monitor-platform-content/service/LocalAuditService`, `monitor-platform/monitor-platform-monolith/src/main/resources/application.yml`, `publish-gateway/gateway-udp-proxy/service/MinioUploadService`, `FileRefMediaUploadService`, `SecurePublishContentReportStrategy`, onsite `/opt/monitor-platform/deploy/.env`.
- Prevention rule: When content detection reports `图片准备失败` or a FILE_REF task reaches the screen without a regulatory media record, first distinguish "no MinIO object was produced" from "content service cannot read the object". Compare upload endpoint/bucket, monitor-content read endpoint/bucket, bucket anonymous policy, and whether the delivered-media branch has a FILE_REF upload path.

### DBG-20260717-030 Secure publish monitoring must replace by complete delivery batch

- First recorded: 2026-07-17
- Last recurrence: 2026-07-17
- Area: publish-gateway SECURE_PUBLISH delivered-media report, monitor-platform-content board monitor aggregation
- Keywords: `SPB-`, `playBatchId`, `playBatchSeq`, `playBatchSize`, `contentId`, `publishRequestId`, FILE_REF, INLINE
- Symptom: A new playlist is correct on the screen, but the regulatory board card includes media retained from one or more previous publish rounds.
- Trigger: Reusing a playlist or publish request identity across rounds, especially when later rounds contain different media or one regulatory upload fails.
- Root cause: Publish-gateway derived media `contentId` from playlist/order/file identity and reused `playlistId` as `playBatchId`. Monitor-content then expanded every row with that batch id, so historical rows accumulated into the current card. There was also no complete-batch gate, allowing a partially reported round to replace the previous complete display.
- Solution: Generate one random `SPB-<uuid>` monitoring batch for each `reportDelivered` call and assign `<batch>-<sequence>` content IDs while retaining the original `publishRequestId` and playlist metadata. In the microservice `monitor-platform-content` path, query the latest complete batch scoped by `board_ip + board_port`, require distinct sequence count to reach the declared batch size, and keep the previous complete batch visible while a newer batch is incomplete. Historical rows are retained. `monitor-platform-monolith` is intentionally not synchronized in this change.
- Verification: TDD first reproduced identical batch IDs across repeated delivery rounds and partial-new-batch display. Focused regression tests cover repeated same request/playlist/files, INLINE, FILE_REF, partial MinIO reporting failure, complete replacement, first incomplete batch, legacy single records, and independent boards. Full `gateway-udp-proxy -am test` passed 29 tests; full `monitor-platform-content -am test` passed 6 tests. Both targeted reactors completed `-DskipTests package` and produced Spring Boot JARs.
- Files/modules: `publish-gateway/gateway-udp-proxy/SecurePublishContentReportStrategy`, `monitor-platform/monitor-platform-content/ContentMonitorMapper`, `ContentMonitorServiceImpl`, and their focused tests.
- Prevention rule: Never use business playlist ID or reusable request ID as the current-display batch identity. A regulatory view may switch only to a board-scoped batch whose distinct received sequence count reaches its declared size; failed partial batches must remain historical diagnostics, not current display state.

### DBG-20260713-025 Nacos runtime source drift can make config edits look ineffective

- First recorded: 2026-07-13
- Last recurrence: 2026-07-13
- Area: monitor-platform microservice deployment, Nacos config/discovery, SQL logging
- Keywords: `NACOS_IP_PORT`, `CONTENT_NACOS_IP_PORT`, `application-dev.yml`, `monitor-forward`, `StdOutImpl`, `NoLoggingImpl`, `192.168.1.31:8848`, `nacos:8848`
- Symptom: `application-dev.yml` in the visible Nacos console is changed from `org.apache.ibatis.logging.stdout.StdOutImpl` to `org.apache.ibatis.logging.nologging.NoLoggingImpl`, but `monitor-forward` keeps printing MyBatis lines such as `==> Preparing`, `==> Parameters`, and `SqlSession ... was not registered`.
- Trigger: The operator edits `192.168.1.31:8848` while the running business container still has `NACOS_IP_PORT=nacos:8848` or `CONTENT_NACOS_IP_PORT=127.0.0.1:18848` and is therefore reading the local Compose Nacos on `192.168.1.25`.
- Root cause: Runtime Nacos source drift between the browser-visible public Nacos and the Nacos address injected into each container. MyBatis `log-impl` is initialized at service startup, so even after correcting the target Nacos config, existing containers must be recreated or restarted to pick up the new environment/config.
- Solution: Verify the actual container environment first with `docker inspect <container> --format '{{range .Config.Env}}{{println .}}{{end}}' | grep Nacos`, compare the config via the exact address seen by the container, then either update that Nacos dataId or change `.env` so `NACOS_IP_PORT` and `CONTENT_NACOS_IP_PORT` point to the intended Nacos. Back up `.env` before changing it and recreate the affected `monitor-*` containers.
- Verification: On 2026-07-13, `monitor-forward` still printed SQL while `192.168.1.31:8848` already returned `NoLoggingImpl`; `docker inspect monitor-forward` showed `NACOS_IP_PORT=nacos:8848`, and `127.0.0.1:18848` still returned `StdOutImpl`. After changing `/opt/monitor-platform/deploy/.env` to `NACOS_IP_PORT=192.168.1.31:8848` and `CONTENT_NACOS_IP_PORT=192.168.1.31:8848`, recreating the business containers, and rechecking logs for five minutes, `monitor-forward` no longer emitted `==> Preparing` SQL lines.
- Files/modules: onsite `/opt/monitor-platform/deploy/.env`, `monitor-platform/monitor-platform-forward/src/main/resources/bootstrap.yml`, `monitor-platform/deploy/docker-compose.yml`, Nacos dataIds `application-dev.yml` and `monitor-forward-dev.yml`.
- Prevention rule: Before diagnosing a Nacos config as ineffective, always prove the target runtime source with container env plus a direct Nacos config fetch from that address. Do not assume the UI endpoint being edited is the endpoint used by the running service.

### DBG-20260713-026 Nginx Docker upstream stale IP can cause 502 after container recreate

- First recorded: 2026-07-13
- Last recurrence: 2026-07-13
- Area: monitor-platform microservice deployment, Nginx reverse proxy, Docker Compose networking
- Keywords: `monitor-nginx`, `monitor-gateway`, `proxy_pass`, Docker DNS, `502 Bad Gateway`, `/api/cert/online-status`, `nginx -s reload`
- Symptom: Browser requests such as `POST /api/cert/online-status` return an HTML `502 Bad Gateway` from Nginx, while direct backend probes return JSON 200 from `monitor-gateway:8060/cert/online-status` and `monitor-ukey:8063/cert/online-status`.
- Trigger: Business containers are recreated, for example while switching `NACOS_IP_PORT` from local `nacos:8848` to `192.168.1.31:8848`, but `monitor-nginx` is left running with a static `proxy_pass http://monitor-gateway:8060` configuration.
- Root cause: Nginx resolves the Docker service name in a static `proxy_pass` when the configuration is loaded. Recreating `monitor-gateway` can change its container IP, but the existing Nginx worker can keep trying the old resolved upstream until Nginx is reloaded or restarted.
- Solution: After recreating `monitor-gateway` or any backend named in Nginx `proxy_pass`, run `docker exec monitor-nginx nginx -t` and then `docker exec monitor-nginx nginx -s reload`, or include `monitor-nginx` in the restart plan. For a structural fix, use an Nginx config pattern that re-resolves Docker DNS at request time and validate it with `nginx -t` before rollout.
- Verification: On 2026-07-13, `curl -X POST http://192.168.1.25:8060/cert/online-status` returned 200 JSON, while `curl -X POST http://192.168.1.25/api/cert/online-status` and `curl -X POST http://192.168.1.174:3006/api/cert/online-status` returned Nginx 502. After `docker exec monitor-nginx nginx -s reload`, both Nginx paths returned 200 JSON.
- Files/modules: onsite `/opt/monitor-platform/deploy/nginx/conf/nginx.conf`, container `monitor-nginx`, container `monitor-gateway`, frontend/proxy entry `192.168.1.174:3006`.
- Prevention rule: Any runbook that recreates business containers behind `monitor-nginx` must either reload/restart Nginx afterward or prove `/api/**` through the Nginx entry, not just direct service health.

### DBG-20260713-027 Empty or wrong VAuth server UKey password causes forced logout after login

- First recorded: 2026-07-13
- Last recurrence: 2026-07-13
- Area: monitor-platform UKey authentication, VAuth server hardware, deployment environment variables
- Keywords: `VAUTH_SERVER_PASSWORD`, `SERVER_UKEY_REMOVED`, `serverUkeyOnline=false`, `用户名或密码不正确`, `monitor-ukey`, `/api/cert/online-status`
- Symptom: A user can enter the large-screen page after login, but the page immediately shows `服务端认证硬件异常`; `/api/cert/online-status` returns `forceLogout=true`, `serverUkeyOnline=false`, and `forceLogoutReasons=["SERVER_UKEY_REMOVED"]`.
- Trigger: `monitor-ukey` is recreated or restarted while `VAUTH_SERVER_PASSWORD` is empty, stale, or not the actual password for the bound server UKey. A Nacos switch commonly exposes the problem because Compose recreation forces the SDK to open and authenticate the hardware again; changing the Nacos address alone does not change the already-open handle in an existing JVM.
- Root cause: `VAuthAuthServerService` can enumerate the physical UKey and match the configured `VAUTH_SERVER_AUTH_ID`, but opening the selected device fails with SDK error `handle=-89, msg=用户名或密码不正确`. The service then keeps `serverUkeyOnline=false`, and the login status poll forces the UI error. A configured value is not proof that it is the current hardware opening password. On the confirmed 25-to-31 incident, the old and new Nacos databases had different overall config records but identical VAuth mode/password-placeholder/authId semantics; the relevant behavioral difference was the container recreation and fresh hardware authentication.
- Solution: Do not guess the UKey password or repeatedly restart the service. Confirm the actual service-side UKey opening password with the UKey provisioning tool or issuer, keep Nacos `vauth.server.password` and Compose `VAUTH_SERVER_PASSWORD` consistent, recreate `monitor-ukey` once, and verify the VAuth log says the UKey opened successfully.
- Verification: On 2026-07-13, `monitor-ukey` logs showed the service UKey was found and bound to `authId=44010100003330003024`, then `打开 UKey 失败, handle=-89, code=-89, msg=用户名或密码不正确`. Initially `VAUTH_SERVER_PASSWORD` was empty. After the operator-provided six-character value was made identical in Nacos, the Compose `.env`, and the container environment, one controlled recreate still returned `-89`; the selected UKey's reported remaining retries dropped from 6 to 5, while `/actuator/health` stayed `UP` and `/api/cert/online-status` still returned `serverUkeyOnline=false`. A live comparison then proved the old 25 Nacos used database `nacos_config`, the new 31 Nacos used database `nacos`, both used the same namespace/group/DataId and equivalent `vauth.server.*` values, and the pre-switch `.env` already had an empty `VAUTH_SERVER_PASSWORD`. USB, udev, SDK mounts and privileged mode remained present after recreation. Subsequent external force-recreates at 18:58 and 19:00 consumed the remaining retries; the final SDK result was `handle=-895, msg=登陆设备密码错误次数过多，设备被锁定`. Docker showed the latest container was newly created with `RestartCount=0`, confirming this was not an application crash loop.
- Files/modules: onsite `/opt/monitor-platform/deploy/.env`, container `monitor-ukey`, `monitor-platform/deploy/docker-compose.yml`, `monitor-platform/monitor-platform-Ukey/src/main/java/com/monitorplatform/ukey/service/VAuthAuthServerService.java`, `monitor-platform/monitor-platform-Ukey/src/main/java/com/monitorplatform/ukey/controller/UkeyCertificateController.java`.
- Prevention rule: After any restart/recreate of `monitor-ukey`, validate both service health and UKey readiness. A 200 health response is not enough; check `serverUkeyOnline=true` or the VAuth open-success log before declaring login authentication normal. Treat the SDK retry counter as a finite hardware resource and stop immediately after a confirmed password fails. Once the SDK reports the device is locked, stop all restarts and password attempts and use the vendor provisioning/admin-unlock procedure before further service validation.

### DBG-20260713-024 JetFileII multi-image playlist must use the documented file structure

- First recorded: 2026-07-13
- Last recurrence: 2026-07-14
- Area: info-publish-client V1 Qingsong publish, terminal-gateway JetFileII playlist
- Keywords: `WRITE_SYSFILE`, `0x0202`, `SEQUENT.SYS`, `PLAYLIST_SET`, `FileTransfer.writeSysFile`, `itemCount`, `FS`, `REQ-PUBLISH-20260713-147`, `REQ-PUBLISH-20260714-158`, `REQ-PUBLISH-20260714-265`
- Symptom: Multi-image uploads finish, but the screen retains only the first image. Strict readback reports `expected=2/3` and `actual=1`; the returned list is 74 or 92 bytes.
- Trigger: Generating `SEQUENT.SYS` with the old guessed type-0x06 model, which treats 18 bytes as a global header and appends variable-length paths and synthetic gaps.
- Root cause: The documented extended playlist has an 8-byte global header. Offset 4 is the number of top-level file/group items, and offset 8 starts the first `FS` item. The old model wrote a constant `1` at offset 4, so firmware parsed one top-level item only. Offset 12 belongs to that first `FS` item's schedule count; it is not a global `addCount`. Packetization and destination-address defects were earlier independent failures in the same path.
- Solution: Generate documented extended type `0x05`: 8-byte header, actual top-level item count at offset 4, list length at offset 6, and one fixed 44-byte `FS` item per file with a 32-byte device path. Keep type-0x06 path scanning only for reading old on-device state. Continue per-packet ACK handling and write-after-read exact path verification.
- Verification: A read-only live `PLAYLIST_GET` on screen `192.168.113.88` returned the 92-byte Base64 payload beginning `SQ 06 00 01 00 54 00 FS`; this proves offset 4 remained one while two files were expected. The protocol regression test first failed with `expected 140 but actual 176`, then passed after generating three documented 44-byte items. The complete reactor run passed 113 tests, and all 9 terminal-gateway modules compiled. A deployed screen rotation test remains required.
- New evidence (2026-07-15): A real 11-image, 82,254,425-byte FILE_REF pressure task against `192.168.113.88:9520` first exposed a stale publish-gateway image that still Base64-serialized image bodies and failed with `OutOfMemoryError` in `SecureDeliveryServiceImpl.executeDelivery`. After replacing only the publish-gateway JAR with the task-level image FILE_REF build, its memory stayed near 483 MiB and it posted the manifest to terminal-gateway immediately. Querying terminal task `FILE-REF-STRESS-20260715-1753-R2` proved files 1-6 succeeded, files 7-8 were marked `TIMEOUT` by the orchestrator, and files 9-11 were never submitted (`total=0`, `未找到支持该能力的目标设备`). The two timed-out batch tasks later both reached `SUCCESS`, proving the board accepted the large JPEG bytes and names. The immediate cause is the ordinary upload path's fixed 75-second orchestration wait: `application.yml` declares size/packet-based `secure-command.file-upload.timeout-*` values, but `resolveStepTimeoutSeconds` applies dynamic sizing only to QingSong FTP and returns the fixed constant for image `WRITE_PATHFILE`. After timeout, the underlying task continues while orchestration starts subsequent steps; current `onlineOnly=true` selection can then produce the downstream no-target failures. The task ends before the complete playlist update.
- Files/modules: `terminal-gateway/gateway-device-protocol/FileTransfer`, `WriteCommands`, `SequentSysHelper`, `JetFileIIPlaylistSetHandler`; onsite `192.168.1.26` terminal gateway.
- Prevention rule: Do not infer SEQUENT fields from names or one-file captures. Tests must assert the documented global header, top-level item count, 44-byte `FS` boundaries, fixed path field, packet boundaries, destination address, and device readback.

### DBG-20260713-028 Alarm cut and restore must preserve non-violating content

- First recorded: 2026-07-13
- Last recurrence: 2026-07-13
- Area: monitor-platform alarm handling, terminal-gateway JetFileII playlist control, content record cleanup
- Keywords: `CUT_VIOLATION`, `BLACKOUT`, `SEQUENT.SYS`, `contentId`, `IMAGE_DELETE`, pending alarm
- Symptom: A normal cut/restore can delete displayed content, or an alarm cut can clear all screen files and replace playback with a default image instead of removing only the current violating image.
- Trigger: Reusing the old broad `delete-violation-by-board`, delete-all-file, or default-image recovery flow without first proving that the current board content matches a pending image alarm.
- Root cause: Screen visibility control, playlist mutation, device file deletion, database cleanup, and alarm handling were coupled into one broad flow without an explicit alarm-state boundary or exact content identity.
- New evidence: `/content/board-monitor-list` expands the selected `latest` row into all rows of its `playBatchId`, but resolves `activeAlarm` only with `latest.contentId`. A violating image elsewhere in the displayed batch therefore appears in `latestContents` with `status=violation/isViolation=1` while the card returns `activeAlarm=null`; the frontend then treats the card as non-alarm and sends no alarm identity on cut. QingSong media IDs can also exceed the current `alarm_record.content_id varchar(64)` width, which is a separate exact-match risk that must be confirmed against the live schema and row values.
- Solution: Non-alarm cut sends only `BLACKOUT=true`; restore always sends only `BLACKOUT=false`. For a pending image alarm, platform sends `CUT_VIOLATION` with exact `contentId` and `fileName`; terminal-gateway reads `SEQUENT.SYS`, removes only `D:\P\<fileName>`, writes the remaining playlist, waits for `IMAGE_DELETE` ACK, and stays black. Platform then physically deletes only that `contentId` and marks that alarm processed. No default image is uploaded, inserted, or displayed.
- Verification: Static inspection confirms both microservice and monolith alarm paths use the same branch, the terminal service performs playlist subtraction plus exact file deletion, and restore contains no content deletion. A production response captured on 2026-07-13 showed one `latestContents` item with `status=violation/isViolation=1` while top-level `activeAlarm` was null, matching the card aggregation defect. Final tests were not run because this round was root-cause analysis only.
- Files/modules: `monitor-platform-alarm`, `monitor-platform-forward`, `monitor-platform-monolith`, `terminal-gateway/gateway-udp-proxy`, `monitor-platform-content` exact delete endpoint.
- Prevention rule: Never infer permission to delete from a cut button alone. Require a matching pending alarm and exact current `contentId`; restore must remain a visibility-only operation, and broad board-level deletion/default-image replacement must not be added to this path.

### DBG-20260714-029 Gateway MQTT heartbeat failure must discard the stale client

- First recorded: 2026-07-14
- Last recurrence: 2026-07-16
- Area: publish-gateway and terminal-gateway MQTT agents, monitor-forward MQTT command publisher, Paho client lifecycle, broker connectivity
- Keywords: `HeartbeatPublisher`, `MqttConnectionManager`, `MqttCommandPublishService`, `GatewayAuthRecoveryPolicy`, `Paho`, `Timed out waiting for a response from the server`, `MQTT Call`, `inflight=32`, `enqueued=1000`, `/up/heartbeat`, `publish-gateway-25`, `terminal-gateway`
- Symptom: A gateway logs `[MQTT-HEARTBEAT] publish heartbeat failed` for its `up/heartbeat` topic, or monitor-forward logs `MQTT客户端未连接，命令仅入库未发送` while an epoch-driven `REAUTH` cannot be delivered. Both commonly follow `org.eclipse.paho.client.mqttv3.MqttException: Timed out waiting for a response from the server` from `ClientState.checkForActivity` / `ClientComms.checkForActivity`.
- Trigger: Paho `client.publish(...)` or keepalive waits for the MQTT Broker response, usually QoS1 PUBACK / ping response, while the client object still exists in the gateway process.
- Root cause: Two failure classes share the same Paho exception. First, a non-null but half-dead client can be reused if failure paths do not clear it. Second, confirmed on 2026-07-16, `monitor-forward` handled heartbeat on Paho's `MQTT Call` thread and synchronously published REAUTH from that callback. The callback blocked in `MqttClient.publish`, the receiver then blocked behind the callback queue, PUBACK/PINGRESP processing stopped, and the persistent session grew to `inflight=32` and `enqueued=1000` before each keepalive disconnect.
- Solution: Keep stale-client identity-safe cleanup and scheduled reconnect. Additionally, reserve/throttle automatic REAUTH on the callback thread but execute the actual publish on a dedicated bounded single-thread executor, so the Paho callback returns immediately. Before deploying this fix, stop Forward and delete the saturated `monitor-platform-001` EMQX client/session to prevent replaying the old queue.
- Verification: The earlier gateway recovery tests remain valid. On 2026-07-16, `GatewayAuthRecoveryPolicyAsyncTest` failed first because a blocked publish prevented heartbeat callback return, then passed after asynchronous single-flight dispatch; the Forward suite passed 10/10. On `192.168.1.25`, the old image repeatedly disconnected about 60 seconds after every reconnect, EMQX showed `inflight=32`, `enqueued=1000`, and about 383% CPU. After backing up the image/config, deleting the session, and deploying image `ca95f942df93`, both gateway REAUTH commands completed `PROCESSING -> SUCCESS`; over three minutes the client stayed connected with `inflight=0`, `enqueued=0`, continuous 30-second heartbeats, no timeout errors, and EMQX CPU fell to about 12%.
- Files/modules: `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/mqtt/MqttConnectionManager.java`, `publish-gateway/gateway-udp-proxy/src/main/java/com/publishgateway/udpproxy/mqtt/HeartbeatPublisher.java`, `terminal-gateway/gateway-udp-proxy/src/main/java/com/gateway/udpproxy/mqtt/MqttConnectionManager.java`, `terminal-gateway/gateway-udp-proxy/src/main/java/com/gateway/udpproxy/mqtt/HeartbeatPublisher.java`, `monitor-platform/monitor-platform-forward/src/main/java/com/monitorplatform/forward/service/MqttCommandPublishService.java`, `monitor-platform/monitor-platform-forward/src/main/java/com/monitorplatform/forward/service/GatewayAuthRecoveryPolicy.java`.
- Prevention rule: Do not diagnose `Timed out waiting for a response from the server` as a monitor-platform business response timeout. First prove broker connectivity, ACL/clientId alignment, Paho callback thread state, EMQX inflight/session queue, and client lifecycle. Never call a synchronous MQTT publish from Paho's `messageArrived` callback path.

### DBG-20260718-031 SVN 中文修订说明必须显式指定日志文件编码

- 首次记录：2026-07-18
- 领域：SVN 提交、Windows/PowerShell 编码、修订属性维护
- 关键词：`svn:log`, `svn commit`, `--encoding UTF-8`, `-F`, `svn propset --revprop`, `E175008`, `pre-revprop-change`
- 现象：文件内容已成功提交，但 `svn log --xml` 回读中文修订说明时显示为 `è§„èŒƒ...` 等乱码；文件 diff 和修订号本身正常。
- 触发条件：Windows PowerShell 中把无 BOM UTF-8 日志文件通过 `svn commit -F <file>` 提交，但没有同时传入 `--encoding UTF-8`。
- 根因：SVN CLI 按本机代码页解释日志文件，再把错误解码后的文本写入 `svn:log`。提交后修复属于 revision property 变更；服务器未配置 `pre-revprop-change` hook 时会以 `E175008` 拒绝修改。
- 解决方案：中文日志统一写入 UTF-8 文件，并使用 `svn commit --encoding UTF-8 -F <file> ...`；提交后立即以 UTF-8 读取 `svn log --xml -r <revision>`。若已经乱码，停止追加错误修订，由仓库管理员启用受控 `pre-revprop-change` hook 后再用 `svn propset --revprop -r <revision> --encoding UTF-8 svn:log -F <file>` 修复。无法启用 hook 时，后续提交暂用 ASCII 日志。
- 验证方式：本轮 `r253-r258` 的文件提交成功且 clean SVN 工作副本为 `r258`，但 UTF-8 日志回读确认六条 `svn:log` 已乱码；对 `r253` 的 revprop 修复被服务器以 `E175008: Repository has not been enabled to accept revision propchanges` 拒绝。
- 涉及文件/模块：`.agents/debug-ledger.md`、SVN 仓库 revision properties、服务端 hook 配置。
- 预防规则：中文 SVN 日志必须同时使用 UTF-8 日志文件和 `--encoding UTF-8`，提交后立即回读；未完成回读前不连续提交下一组变更。

```markdown
### DBG-YYYYMMDD-NNN 标题

- 首次记录：YYYY-MM-DD
- 最近复发：YYYY-MM-DD 或 无
- 领域：
- 关键词：
- 现象：
- 触发条件：
- 根因：
- 解决方案：
- 验证方式：
- 涉及文件/模块：
- 预防规则：
- 新增证据：
```
