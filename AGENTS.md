---
name: project02-java-agent
description: D:\project02 仓库级 Agent 工作规范，适用于管控平台、发布网关、终端网关和本地信发客户端的分析、开发、审查、部署和文档维护。
version: 2026.07.06
tags: [java8, spring-boot, spring-cloud, gateway, monitor-platform, doc-sync, svn, windows]
---

# D:\project02 Agent Guide

## 项目上下文摘要

本仓库是面向信息发布/监管链路的多工程 Java 工作区，不是单一服务。处理任何代码、配置、部署、接口、端口、协议或文档任务前，必须先确认当前任务落在哪个工程、哪个部署形态、哪条链路上。

主要工程：

| 工程 | 角色 | 技术基线 | 关键边界 |
| --- | --- | --- | --- |
| `monitor-platform` | 管控平台，包含微服务模块和 `monitor-platform-monolith` 融合部署模块 | Java 8, Spring Boot 2.7.18, Spring Cloud 2021.0.8, Spring Cloud Alibaba 2021.0.5.0, MyBatis Plus 3.5.3.1, Liquibase 4.20.0 | 设备、内容、告警、规则、转发、角色、UKey、日志、WebSocket、注册服务、MQTT 核心 |
| `publish-gateway` | 加密/发布网关，承接客户端或平台侧内容与控制链路 | Java 8, Spring Boot 2.7.18, `gateway-standardization`, `gateway-udp-proxy`, 复用 `monitor-platform-mqtt-core` | 安全封装、UDP/TCP 转发、ACK/回退、发布网关到终端网关 |
| `terminal-gateway` | 解密/终端网关，面向显示设备协议和终端注册/控制 | Java 8, Spring Boot 2.7.14, Netty 4.2.15.Final, JNA, MyBatis Plus | `gateway-device-protocol`, `gateway-device-transport`, `gateway-device-core`, `gateway-udp-proxy` |
| `info-publish-client` | Windows 本地信发客户端 | Java 8, Spring Boot 2.7.18, JNA, Hutool, FastJSON2, MyBatis Plus | UKey/VAuth、Sigma 进程绑定、WinDivert/ETW、本地转发、客户端到发布网关 |
| `deploy-standalone` | 已落地的现场部署样例/快照 | Docker Compose, application.yml | 只按实际任务读取，不把样例端口当成通用事实 |
| `terminal-gateway-V15` | 历史/对照网关工程 | Java 8 | 默认不是当前主线，除非用户明确指定 |

核心业务术语必须沿用现有代码和文档：设备、情报板、内容发布、内容审计、告警、规则、UKey、Sigma、发布网关、终端网关、青松/QingSong、JetFileII、卡莱特/ColorLight、诺瓦/NovaStar、SVAC/PackData、mDNS、MQTT、sourceIp 白名单。

## 最高优先级规则

1. 以当前仓库事实为准。先读相关 POM、启动类、Controller/Service/Mapper、配置、部署模板和文档，再输出判断。
2. Java 代码必须兼容 Java 8。禁止使用 `var`、record、switch expression、text block 等高版本语法。
3. 不引入新依赖，除非用户明确批准并同步评估所有受影响模块的 POM、部署包和构建命令。
4. 不擅自改变公共接口、DTO 字段、协议枚举、MQTT topic、UDP/TCP 包格式、设备厂商别名或数据库结构。
5. 不覆盖用户或他人已有改动。当前工作区可能同时是 Git 与 SVN 工作副本，开始改动前先看状态，发现不相关脏改只绕开。
6. 任何涉及真实主机、端口、容器、数据库、证书、UKey、MQTT 账号或 SSH 的操作，都按“先确认、先备份、再验证、后变更”执行。
7. 用户明确说“先别改代码”“先分析”“帮我看看”时，只做追踪、证据收集和方案，不落地代码变更。

## 技术与架构约定

### monitor-platform

- 父 POM：`monitor-platform/pom.xml`。
- 模块：`monitor-platform-device`, `monitor-platform-forward`, `monitor-platform-content`, `monitor-platform-Ukey`, `monitor-platform-alarm`, `monitor-platform-rule`, `monitor-platform-common`, `monitor-platform-log`, `monitor-platform-websocket`, `monitor-platform-gateway`, `monitor-platform-role`, `monitor-platform-provider`, `monitor-platform-registry-client`, `monitor-platform-registry-server`, `monitor-platform-register-service`, `monitor-platform-monolith`, `monitor-platform-mqtt-core`。
- 依赖版本以父 POM 为准：MySQL Connector 8.0.33, Lombok 1.18.30, Hutool 5.8.23, FastJSON2 2.0.43, JJWT 0.11.5, MinIO 8.5.9, Knife4j 2.0.2, Paho MQTT 1.2.5。
- 包结构通常为 `controller`, `service`, `service.impl`, `mapper`, `entity`, `entity.dto`, `entity.vo`, `feign`, `config`, `handler`。
- 微服务和 `monitor-platform-monolith` 常有双路径。修公共能力时必须确认是否需要同步 monolith 路径。
- 数据库变更使用 Liquibase，优先修改对应模块的 `db/changelog`，并检查 monolith changelog 是否需要同等变更。

### terminal-gateway

- 父 POM：`terminal-gateway/pom.xml`。
- 模块：`gateway-auth`, `gateway-common`, `gateway-crypto`, `gateway-device-protocol`, `gateway-device-transport`, `gateway-device-core`, `gateway-udp-proxy`。
- `gateway-device-core` 为冻结模块，除非用户明确解除冻结，禁止修改其中任何代码；该模块只作为对外暴露的 Service 能力边界，外层业务必须调用其现有 Service，不得把业务逻辑下沉到 core。
- `gateway-device-protocol` 为冻结模块，除非用户明确解除冻结，禁止修改其中任何代码；外层只能复用其既有 capability、DTO/参数模型和协议能力，不得为业务需求改写协议实现。
- `gateway-device-transport` 为冻结模块，除非用户明确解除冻结，禁止修改其中任何代码；业务编排统一放在 `gateway-device-controller`，由 controller 调用 core 暴露的 Service，不得直接改造或绕过 transport。
- 协议层分为 adapter/base/common/model/params 等区域。新增厂商或能力时先找现有 `DeviceVendor`, capability, handler, params, orchestrator 和测试。
- 青松/QingSong、JetFileII、ColorLight、NovaStar 兼容逻辑不能只改单入口；设备注册、批量命令、secure command、协议元数据通常要分别核对。
- 终端网关涉及实际设备端口、mDNS、快照、UDP/TCP 转发和协议文件格式，不能用文档猜测替代代码或抓包证据。

### publish-gateway

- 父 POM：`publish-gateway/pom.xml`。
- 模块：`gateway-standardization`, `gateway-udp-proxy`，并通过相对路径复用 `monitor-platform/monitor-platform-mqtt-core`。
- 发布链路重点检查 `SecureGatewayAck`, envelope/fallback, UDP/TCP 转发、sourceIp 白名单、终端设备注册代理、设备协议元数据代理。
- 安全链路失败时默认不能透传密文或伪造成功；要保留失败原因、任务 ID、ruleId、source 地址等诊断信息。

### info-publish-client

- POM：`info-publish-client/pom.xml`。
- 这是 Windows 本地客户端，涉及 UKey/VAuth SDK、Sigma 进程绑定、WinDivert、ETW、ARP、客户端配置下发和本地转发。
- 本地运行配置可能存在于 `config/application.yml`, `client/config/application.yml`, `dist/config/application.yml` 等多个位置。修改配置前先确认当前进程实际读取哪个文件。
- 与 UKey、证书、进程绑定、网卡/ARP、Windows 服务相关的任务必须区分代码事实、当前进程事实和用户现场事实。

## 分层和代码风格

接口层只做协议适配、参数校验、权限/上下文读取和响应封装；业务层封装业务规则；Mapper/Repository 只做持久化；网关/协议/SDK 适配细节放在对应基础设施或协议包内。

统一约定：

- 响应对象优先使用当前模块已有 `Result<T>` 或同名公共结果对象。
- 管控平台公共异常优先使用 `BusinessException`、`ResultCode` 和 `GlobalExceptionHandler`。
- Controller 参数使用 DTO/VO，不让 Entity/DO 穿透 API 边界。
- 写数据库的业务入口必须考虑事务、幂等、并发和失败回滚。
- 日志使用 `@Slf4j` 与参数化日志。异常日志保留堆栈，禁止吞异常，禁止用 `System.out.println` 替代业务日志。
- 关键链路日志保留定位信息：`deviceId`, `taskId`, `chainId`, `ruleId`, `requestId`, `clientId`, `targetIp`, `sourceIp`, `topic`, `statusCode` 等。
- 注释以解释复杂业务边界、协议兼容原因和风险为主，不写重复代码表面的注释。

## 任务路由

### 新功能或接口

1. 先确认业务场景、调用方、部署形态、兼容范围和验收口径。
2. 先定契约：路径、方法、DTO、响应、错误码、数据库变更、MQTT topic 或协议字段。
3. 在现有模块边界内实现，优先复用已有工具类、枚举、适配器、Feign client、Mapper 和配置类。
4. 同步补测试。窄改动补单元测试；跨模块/链路改动至少补关键路径回归测试或给出可执行验证命令。
5. 检查 `monitor-platform-monolith`、部署模板和接口文档是否需要同步。三份活文档由每周定时扫描统一维护，除非用户明确要求即时更新。

### Bug 排查或故障修复

1. 先复现或收集证据：报错栈、接口响应、日志、数据库状态、MQTT/UDP/TCP 抓包、容器状态或编译结果。
2. 源码事实和运行时事实分开写，不把本地配置推断成现场真实状态。
3. 修复要最小化，优先改根因，不用大重构掩盖问题。
4. 必须补能复现问题的测试或给出明确的手工回归步骤。

### Code Review

按 P0/P1/P2/P3 分级，优先级为正确性、安全性、兼容性、数据一致性、性能、可维护性、风格。发现问题时给文件和行号，说明触发条件、影响和建议修法。没有发现阻断问题也要说明测试缺口和残余风险。

### 重构

先给诊断报告和影响范围。重构必须保持接口、协议、数据库结构、日志关键字段和部署行为兼容。遗留链路采用小步替换，不做跨工程“大爆炸式”改写。

### 性能和容量

先定位瓶颈类型：SQL/N+1、锁竞争、序列化、网络、MQTT、UDP/TCP 转发、SDK 调用、文件重组、GC、线程池或容器资源。方案必须给 Quick Win、长期方案和验证办法，如 SQL EXPLAIN、压测参数、日志指标或抓包指标。

## 文档同步定时规则

在 `D:\project02` 内，三份活文档不再随每个功能点即时更新。Codex 内置定时任务在每周五 20:00 扫描当前工作区的代码、配置、部署脚本、接口契约、端口、模块边界、跨服务链路、MQTT/HTTP/UDP 交互、功能状态和验收口径变更，并集中更新：

| 文档 | 定时扫描时的更新范围 |
| --- | --- |
| `D:\project02\系统架构.md` | 模块/服务/组件职责、部署角色、调用链路、注册发现、MQTT/HTTP/UDP 交互、安全体系或核心业务流程变化 |
| `D:\project02\监管平台端口梳理.md` | 服务端口、容器端口、Nginx 入口、健康检查、环境变量、部署模板、访问方向或协议类型变化 |
| `D:\project02\信发管理系统功能清单.xlsx` | 用户可见功能、菜单、页面入口、接口能力、部署形态、验收口径、功能状态或功能边界变化 |

维护方式：

- Markdown 文档做最小必要修改，保留原标题层级、表格结构和术语。
- Excel 工作簿保留现有 sheet、列名、样式和排序；优先更新相关行，不重建工作簿。
- 文档事实只能来自当前代码、配置、部署模板、运行验证或用户明确口径。
- 定时任务先检查工作区状态并保留未提交的用户改动；仅对确认需要同步的内容做最小修改。
- 代码任务无需因三份活文档阻塞或逐项汇报；用户明确要求即时更新时，仍在该任务内完成并说明修改结果。

## 故障经验台账

本项目维护专用故障经验文件：`.agents/debug-ledger.md`。

使用规则：

- 遇到需要多次调试、流程容易出错、历史上反复出现或修复成本较高的问题时，先用当前关键词搜索 `.agents/debug-ledger.md`。
- 只有在有明确证据时才新增或更新条目，不能把猜测写成事实。
- 同类问题复发时优先更新原条目的“最近复发”“新增证据”“验证方式”，避免重复创建相同条目。
- 每条记录必须包含：现象、触发条件、根因、解决方案、验证方式、涉及文件/模块、预防规则。
- 禁止写入密码、token、UKey PIN、证书私钥、数据库口令、MQTT secret、客户敏感数据或现场一次性凭据。
- 如果本轮任务确认了新的复发性问题，必须在同一轮更新 `.agents/debug-ledger.md`，并在最终回复说明新增或更新的条目 ID。

## 版本控制和工作区安全

本目录同时存在 `.git` 和 `.svn`。不要假设只有一种版本控制。

Git：

- 开始前查看 `git status --short`。
- 不回滚用户已有改动，不使用 `git reset --hard` 或 `git checkout --`，除非用户明确要求。
- 提交前只 stage 本次任务相关文件。

SVN：

- 做 SVN 同步或风险评估时，优先使用 `svn status --xml -u`、`svn diff --summarize -r BASE:HEAD`、`svn log`。
- 不使用 `svn update --dry-run`，该工作流不可用。
- 本地改动和远端改动有重叠时，先备份再更新，先解释冲突风险。
- Windows 控制台可能显示中文路径乱码，必要时用 PowerShell UTF-8 输出或 `rg --files` 交叉确认。

## 构建和验证

优先用仓库内 Maven：

```powershell
D:\project02\.tools\apache-maven-3.9.9\bin\mvn.cmd -f monitor-platform\pom.xml -DskipTests compile
D:\project02\.tools\apache-maven-3.9.9\bin\mvn.cmd -f terminal-gateway\pom.xml -DskipTests compile
D:\project02\.tools\apache-maven-3.9.9\bin\mvn.cmd -f publish-gateway\pom.xml -DskipTests compile
D:\project02\.tools\apache-maven-3.9.9\bin\mvn.cmd -f info-publish-client\pom.xml -DskipTests compile
```

按改动范围缩小验证：

- 管控平台单模块：`-pl <module> -am test` 或 `compile`。
- 网关协议/控制改动：跑对应 `gateway-device-core`、`gateway-device-protocol` 或 `gateway-udp-proxy` 测试。
- 发布网关安全封装：重点跑 ACK、envelope、fallback、JPEG/分片、注册代理相关测试。
- 客户端 UKey/进程绑定：区分单元测试、Windows 权限、SDK/硬件依赖和现场手工验证。

如果未能运行测试或构建，最终回复必须写清楚原因、已做的替代检查和剩余风险。

## 运行环境和凭据策略

禁止把 SSH 密码、云登录 token、私钥、UKey PIN、数据库密码、MQTT secret、证书私钥或现场口令写入仓库、命令历史、文档、记忆或日志。

SSH 使用优先级：

1. 本机 `~/.ssh/config` 中已有 Host alias 和 SSH agent key。
2. Windows Credential Manager 或批准的本地密钥存储。
3. 用户为本次会话临时提供的一次性凭据。

已知主机别名和用途：

| Alias | Host | Port | User | Role |
| --- | --- | ---: | --- | --- |
| `monitor-cloud-ecs` | `120.26.33.225` | 22 | `root` | 阿里云公网测试平台，历史上用于 EMQX 和 MQTT 云联调 |
| `monitor-site-25` | `192.168.1.25` | 22 | `root` | 公司 LAN 管控/发布网关主机，常见部署目录 `/opt/monitor-platform/deploy` |
| `terminal-gateway-26` | `192.168.1.26` | 22 | `root` | 公司 LAN 终端/解密网关主机，历史探测服务端口 `8093` |
| `base-services-31` | `192.168.1.31` | 22 | `root` | MySQL/Redis/MinIO/Nacos 等基础服务主机 |

非 SSH 环境端点：

| Endpoint | Purpose | Rule |
| --- | --- | --- |
| `192.168.1.31:3306` | MySQL | 凭据只能来自安全存储或用户临时输入 |
| `192.168.1.31:8848` | Nacos | 修改配置前先备份并记录命名空间/分组 |
| `192.168.1.31:9000` | MinIO | 不输出 access key 或 secret key |
| `192.168.1.251` | SVN | 不是 SSH 目标，按 SVN 工具访问 |
| `192.168.1.253` | SMB/NAS | 用 Windows Credential Manager，不保存共享密码 |

远程运维规则：

- 重启服务前先查看 live 状态、容器名、挂载目录、当前配置和最近日志。
- 修改远程配置前必须备份原文件，备份文件名带时间戳。
- `192.168.1.25` 上 Nginx 变更必须先执行 `docker exec monitor-nginx nginx -t`，通过后才 reload/restart。
- MQTT 云联调时区分公网 ECS `120.26.33.225` 和 LAN 网关 `192.168.1.25`。
- 临时 IP 切换测试前确认当前活跃 IP，不能沿用历史 IP 假设。

## Windows/PowerShell 习惯

- 搜索文件和文本优先用 `rg` / `rg --files`。
- PowerShell 内联 Python 使用：

```powershell
@'
print("ok")
'@ | python -
```

- 避免 Bash heredoc。
- Node/npm 命令在本机优先用 `npm.cmd`，避免 `npm.ps1` 执行策略问题。
- 中文路径或中文输出异常时，先设置 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`，再读文件。

## 交付输出要求

首次回复必须包含简短的【项目上下文摘要】，至少说明：

- 本次任务涉及哪个工程/模块。
- 探测到的技术版本或运行形态。
- 关键接口、配置、部署或文档约束。

最终回复必须包含：

- 改了哪些文件和为什么。
- 运行了哪些构建/测试/探测命令，结果如何。
- 没跑的验证及原因。
- 用户明确要求即时更新三份活文档时，说明已更新的文档和原因。
- 如遇到复发性问题，说明 `.agents/debug-ledger.md` 的新增或更新条目；如未触发，说明未新增。
- 如果涉及远程环境，说明备份、验证和重启情况。

引用外部资料时必须给来源；未联网验证的运行时事实只能标注为本地代码/历史记忆/当前命令输出，不写成已验证的现场事实。
