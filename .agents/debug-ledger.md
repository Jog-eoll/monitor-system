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
| DBG-20260706-008 | Terminal device discovery sync | `POST /api/devices/scan`, `/discovery/scan`, `led_controller`, `unified_device_info` | Terminal gateway can list QingSong/JetFileII devices but platform search/device list misses some of them | Separate terminal gateway screen scan from platform mDNS service discovery, then verify sync/registration path |
| DBG-20260706-009 | Terminal gateway startup | `DeviceCapability`, `DEVICE_SEARCH`, `gateway-device-protocol`, `gateway-device-core` | New terminal gateway image restarts before HTTP health is available | Fix duplicate capability registration before treating SVAC or UKey config as the blocker |
| DBG-20260706-010 | Terminal gateway build | `Maven Central`, `PKIX`, `netty-all`, `gateway-device-transport`, `maven-settings-aliyun.xml` | `package` fails while resolving Netty native runtime artifacts | Use the existing Aliyun Maven settings or fix the local JDK trust store before diagnosing source code |
| DBG-20260706-011 | Publish gateway transparent VIP bind | `192.168.113.88`, `BindException`, `ip route add local`, `/udp-proxy/config`, `49_B1` | Platform chain deploy partially fails because publish gateway cannot bind the info-board IP | Add or persist a local route/VIP plan on the publish gateway host before redeploying transparent rules |
| DBG-20260706-012 | SVAC USB gate selector | `VAUTH_SVAC_MODULE_DEVICE_PATH`, `0ac8:7180`, `VIMICRO`, `3a59:4458`, `USBKEY` | Pulling the real SVAC module does not interrupt the chain, but pulling UKey does | Configure the gate by VIMICRO vendor/product instead of a volatile UKey sysfs path |
| DBG-20260707-013 | VAuth microservice URL overrides | `CONTROL_PLATFORM_URL`, `MONITOR_UKEY_PORT`, `8063`, `8080`, `未找到会话密钥` | Terminal gateway is in real crypto mode but decrypt fails because key query reaches the wrong UKey endpoint | Check explicit URL overrides before trusting split MONITOR_* port variables |
| DBG-20260707-014 | Content publish result aggregation | `/api/client/publish/execute`, `/api/secure-delivery/tasks/{deliveryTaskId}`, `DELIVERY_FAILED`, `success=false` | Publish gateway worker fails after task creation, but client publish entry still reports success | Query secure-delivery task terminal status and propagate any failed sub-step to the main response |
| DBG-20260708-015 | MQTT gateway command semantics | `QUERY_STATUS`, `GatewayCommandDispatcher`, `LocalHttpForwardService`, `code=500`, `up/reply` | Gateway MQTT command looks successful while it actually fell back to `/udp-proxy/config` or business `code` failed | Handle probe commands natively and judge action success by `success` + HTTP status + business `code` |
| DBG-20260708-016 | Terminal gateway transport facade | `DeviceChannelPool`, `NettyTransportManager`, `HttpTransport`, `TcpTransport`, `UdpTransport` | `gateway-device-transport` compile fails because the manager still references removed pool type | Keep `NettyTransportManager` as a facade over split HTTP/TCP/UDP transports and pools |
| DBG-20260706-001 | SVN 同步 | `svn update --dry-run`, `svn status --xml -u` | 用无效 dry-run 评估更新风险，命令失败或误判冲突 | 改用 `svn status --xml -u`、`svn diff --summarize -r BASE:HEAD`、`svn log` |
| DBG-20260706-002 | Git/SVN 混合工作区 | `git log`, `svn status`, dirty tree | 只看 Git 历史导致漏掉 SVN 状态或未版本化文件 | 同时检查 Git 与 SVN 状态，提交/更新前限定文件范围 |
| DBG-20260706-003 | 管控平台双部署形态 | `monitor-platform-monolith`, microservice, callback | 微服务路径修好了，融合/monolith 部署仍旧缺行为 | 改公共能力时同步核对 monolith 控制器、Service、Liquibase |
| DBG-20260706-004 | 远程改 IP | `dryRun`, `IpChangeCallbackService`, `FeignDeviceIpUpdater` | dryRun 仍写库，或下游失败却返回成功 | callback 必须受请求模式约束，并校验 Feign 业务码和 DB 更新数 |
| DBG-20260706-005 | QingSong/JetFileII 兼容 | `QINGSONG`, `TargetToSelectorMapper`, `/api/devices/register`, `DEVICE_NOT_LOGGED_IN` | 注册列表可见但协议登录态缺失，或老批量命令路径失败 | 同时核对设备注册登录、secure command、旧 batch-command 映射 |
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
- 验证方式：最终 diff 或 status 里能明确区分本轮改动与已有脏改。
- 涉及文件/模块：全仓库。
- 预防规则：不能把 Git 最近 20 次提交当作唯一团队风格来源；必要时直接读当前代码。

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

### DBG-20260706-005 QingSong/JetFileII 兼容不能只验证新接口

- 首次记录：2026-07-06
- 最近复发：2026-07-07
- 领域：终端网关设备厂商兼容、批量命令
- 关键词：`QINGSONG`, `QING_SONG`, `JET_FILE_II_STANDARD`, `TargetToSelectorMapper`, `/api/command/submit`, `/api/devices/register`, `DEVICE_NOT_LOGGED_IN`, `loggedIn`
- 现象：设备注册或 secure command 已支持 QingSong/JetFileII 别名，但旧批量命令 `/api/command/submit` 仍因枚举解析或 selector 映射失败；`/api/devices/register` 能把设备写入列表，但未走 JetFileII 登录流程时发布编排在清屏/上传步骤报 `DEVICE_NOT_LOGGED_IN`。
- 触发条件：新增或调整厂商别名、设备注册、控制命令、协议元数据、批量命令路径，或用 `/api/devices/register` 手工兜底注册青松屏幕后直接发布。
- 根因：厂商兼容分散在注册解析、发布/控制编排和旧 batch-command 目标映射等多个入口；手工注册入口只构造 `DeviceContext` 并写内存表，没有复用自动扫描/显式 IP 注册里的 `DeviceRegistrationProvider` 登录注册流水线。
- 解决方案：同时检查 `DeviceManagementController`、secure command 相关 orchestrator、`TargetToSelectorMapper`；别名统一映射到既有 `DeviceVendor`，不能各写一套规则。`/api/devices/register` 对 JetFileII/QingSong 在线注册复用 `AutoDiscoveryService.registerDevice(new ExplicitIpDiscoveredDevice(ip, port), mapping, RegistrationSource.MANUAL_IP)`，失败直接返回注册失败，不再 fallback 成“在线但未登录”的内存设备；仅 `online=false` 保留离线 seed 语义。
- 验证方式：覆盖 `QINGSONG`, `QING_SONG`, `JETFILEII`, `JET_FILEII`, `JET_FILE_II` 等别名；跑对应 Controller/selector 测试；手工注册后执行发布任务并查询 `/api/secure-command/publish-tasks/{orchestrationTaskId}`，不能再出现 `DEVICE_NOT_LOGGED_IN`。2026-07-07 本地新增并通过 `DeviceManagementControllerTest`、`AutoDiscoveryServiceTest`，命令：`mvn -s D:\project02\.tools\maven-aliyun-settings.xml -f terminal-gateway\pom.xml -pl gateway-device-core -am "-Dtest=DeviceManagementControllerTest,AutoDiscoveryServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`。
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
- Last recurrence: 2026-07-06
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
- Symptom: Pulling the actual SVAC USB module does not interrupt the data path, but pulling a UKey does interrupt it.
- Trigger: `VAUTH_SVAC_MODULE_REQUIRED=true` is enabled while `VAUTH_SVAC_MODULE_DEVICE_PATH=/sys/bus/usb/devices/1-1` points at a `3a59:4458 USBKEY` instead of the SVAC module.
- Root cause: The physical SVAC gate was bound to a volatile sysfs path that belonged to UKey. The real SVAC module appeared as `0ac8:7180 VIMICRO` and its sysfs path changed after replug, for example from `1-5` to `1-6`.
- Solution: Clear `VAUTH_SVAC_MODULE_DEVICE_PATH` and configure `VAUTH_SVAC_MODULE_VENDOR_ID=0ac8`, `VAUTH_SVAC_MODULE_PRODUCT_ID=7180`, leaving UKey selection untouched. Recreate the gateway containers so the environment variables take effect.
- Verification: On 2026-07-06, after changing both gateways to `vendorId=0ac8/productId=7180`, pulling VIMICRO from `192.168.1.25` made publish-gateway log `USB_DEVICE_NOT_FOUND:vendorId=0ac8, productId=7180`; replugging VIMICRO as sysfs path `1-6` removed the SVAC gate error and the test packet proceeded to the later source-IP whitelist check. UKey devices `3a59:4458 USBKEY` remained present and were no longer the SVAC gate selector.
- Files/modules: remote `.env` in `/opt/publish-gateway/gateway-udp-proxy` and `/opt/terminal-gateway/gateway-udp-proxy`, `publish-gateway/gateway-udp-proxy/service/SvacModulePresenceGuard`, `terminal-gateway/gateway-crypto/service/SvacModulePresenceGuard`.
- Prevention rule: Before any SVAC unplug test, print `/sys/bus/usb/devices/*/idVendor`, `idProduct`, and `product`; verify that the configured selector matches `VIMICRO 0ac8:7180`, not `USBKEY 3a59:4458`.

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
