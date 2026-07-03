# 阶段 0 部署基线说明

版本：v0.1  
日期：2026-06-10  
适用项目：公共区域电子屏安全播控平台加解密部署  
依据文档：`公共区域电子屏安全播控平台加解密部署方式要求V1.0-20260608(2).docx`

## 1. 阶段目标

阶段 0 不修改业务代码和部署脚本，目标是把后续实施前必须确认的部署边界、环境基线、端口规则、证书规则、验收口径固化为可执行依据。

后续阶段 1 只能基于本文档中已确认的内容改造配置模板和部署脚本；本文档中标记为“待确认”的内容，不能直接作为生产默认值使用。

## 2. Done 定义

| 检查项 | 阶段 0 状态 | 说明 |
| --- | --- | --- |
| 部署对象已列清 | 已完成 | 见第 4 节 |
| 支持的部署拓扑已列清 | 已完成 | 见第 5 节 |
| 当前项目实现证据已记录 | 已完成 | 见第 3 节 |
| 环境最低要求已给出 | 部分待确认 | 见第 6 节 |
| 端口规则已给出 | 部分待确认 | 见第 7 节 |
| 密码与证书规则已给出 | 已完成 | 见第 8、9 节 |
| 部署输入物已定义 | 已完成 | 见第 10 节 |
| 验收用例已定义 | 已完成 | 见第 12 节 |
| 待确认问题已收敛 | 已完成 | 见第 14 节 |

## 3. 当前项目事实

以下内容来自当前仓库，不是推测。

| 方向 | 当前证据 | 结论 |
| --- | --- | --- |
| 管控平台微服务 | `monitor-platform/pom.xml` 包含 `device`、`forward`、`content`、`ukey`、`alarm`、`rule`、`common`、`log`、`websocket`、`gateway`、`role`、`provider`、`registry-client`、`registry-server`、`register-service`、`monolith` 等模块 | 已具备微服务拆分基础 |
| 管控平台容器部署 | `monitor-platform/deploy/docker-compose.yml` 包含 MySQL、Redis、Nacos、MinIO、Nginx、业务服务 | 已具备 Compose 部署雏形 |
| 轻量版管控平台 | `monitor-platform/monitor-platform-monolith`，`pom.xml` 描述为单体融合项目，且包含独立 Dockerfile | 已具备轻量化部署载体 |
| 平台侧 UKey/VAuth | `monitor-platform/monitor-platform-Ukey/src/main/java/.../VAuthServerSDKLibrary.java` | 已映射 VAuth 服务端 SDK |
| 终端网关 | `terminal-gateway/pom.xml` 包含 `gateway-auth`、`gateway-crypto`、`gateway-udp-proxy` | 已具备网关侧工程 |
| 网关侧加解密 | `terminal-gateway/gateway-crypto/src/main/java/.../CryptoServiceImpl.java` | 已有 VAuth 认证、加密、解密、回调逻辑 |
| 设备自动注册 | `monitor-platform/monitor-platform-device/src/main/java/.../DeviceAutoRegisterController.java` | 已有注册、心跳、注销接口雏形 |
| 独立注册中心 | `monitor-platform/monitor-platform-registry-server/src/main/java/.../ServiceRegistryController.java` | 已有服务注册、发现、心跳接口 |
| 部署脚本 | `monitor-platform/deploy/install.sh` | 已有环境检查、端口检查、镜像检查、健康检查雏形 |

当前项目还存在候选目录 `monitor-platform-new`、独立目录 `publish-gateway`。阶段 0 暂不默认把它们纳入正式管控平台部署基线，除非后续明确它们仍是生产交付物。

## 4. 部署对象基线

| 部署对象 | 统一编码（沿用现有） | 当前项目映射 | 是否纳入后续实施 |
| --- | --- | --- | --- |
| 完整管控平台 | `monitor_platform` | `monitor-platform` | 是 |
| 轻量版管控平台 | `monitor_platform_monolith` | `monitor-platform/monitor-platform-monolith` | 是 |
| 发布侧加密网关 | `publish_gateway` | 当前可能复用 `terminal-gateway/gateway-udp-proxy` 或 `publish-gateway`，待确认 | 是，待确认正式包 |
| 终端侧解密网关 | `terminal_encrypt_gateway` | `terminal-gateway/gateway-udp-proxy`、`gateway-auth`、`gateway-crypto` | 是 |
| 发布服务器 / 播控主机 | `publish_server` | 作为链路节点存在，非当前仓库核心服务 | 只纳入配置管理 |
| 情报板 / 电子屏 | `info_board` | 作为设备资产存在 | 只纳入配置管理与连通性检测 |
| 运维 Agent | `ops_agent` | 当前未实现 | 待确认是否必须 |
| Portainer | `portainer` | 当前未实现 | 待确认是否必须 |

说明：业务描述中可称“发布侧加密网关、终端侧解密网关”，但落库、注册、CSV、配置下发中的 `deviceType` 必须沿用当前项目已有编码：发布侧使用 `publish_gateway`，终端侧使用 `terminal_encrypt_gateway`。其中 `role` 可继续表达 `encrypt` / `decrypt` 职责，避免另起一套 `publish_encrypt_gateway` / `terminal_decrypt_gateway` 枚举。

## 5. 部署拓扑基线

### 5.1 加密网关一对一

链路：

```text
发布服务器 -> 发布侧加密网关 -> 终端侧解密网关 -> 电子屏
```

阶段 0 验收口径：

| 项目 | 要求 |
| --- | --- |
| 平台部署 | 数据库、Nacos、Redis、MinIO、UKey、网关、业务服务按依赖启动 |
| 网关注册 | 加密/解密网关部署完成后自动注册到管控平台 |
| 配置下发 | 管控平台可为一条链路生成并下发加密/解密配置 |
| 功能自检 | 能完成平台认证、网关连通、样包加密、样包解密 |

### 5.2 加密网关一对多

一对多部署时，管控平台采用轻量化部署方案，即依托 `monitor-platform/monitor-platform-monolith`。平台服务与发布侧加密网关部署在同一台服务器上，形成“轻量版平台 + 单个加密网关”的同机部署节点。

链路：

```text
同机服务器
  ├─ 轻量版管控平台（monitor-platform-monolith）
  └─ 发布侧加密网关（1 个）
        ├─ 终端侧解密网关 1 -> 电子屏 1
        ├─ 终端侧解密网关 2 -> 电子屏 2
        └─ 终端侧解密网关 N -> 电子屏 N
```

阶段 0 验收口径：

| 项目 | 要求 |
| --- | --- |
| 轻量化平台 | 一对多场景下管控平台必须使用 `monitor-platform-monolith` 部署 |
| 同机部署 | 轻量版管控平台和发布侧加密网关部署在同一台服务器，部署工具需要校验端口、UKey、SDK 动态库不冲突 |
| 单加密网关 | 一个轻量版平台只对应一个发布侧加密网关 |
| 多解密网关 | 一个发布侧加密网关对应多个终端侧解密网关 |
| 屏幕映射 | 每个终端侧解密网关再对应其电子屏，设备关系需要在平台侧保存 |
| 自动发现 | 轻量版平台能发现同机发布侧加密网关，并能纳管多个终端侧解密网关 |
| 证书注册 | 发布侧加密网关、终端侧解密网关均必须通过 UKey 证书完成身份绑定 |
| 配置拉取 | 解密网关启动后能主动拉取专属配置，配置中必须包含所属发布侧加密网关信息 |
| 批量部署 | 支持从 CSV 导入多台解密网关的 IP、证书、UKey、电子屏映射关系 |

### 5.3 轻量版管控平台

轻量版管控平台以 `monitor-platform/monitor-platform-monolith` 为正式实现载体，不再按“从微服务裁剪”理解。

阶段 0 验收口径：

| 项目 | 要求 |
| --- | --- |
| 应用入口 | 使用 `monitor-platform-monolith` 构建单体服务镜像或 JAR |
| 配置入口 | 轻量版配置从单体模块的 `application.yml` / 外部环境变量加载 |
| 依赖组件 | 按 `monitor-platform-monolith` 实际依赖保留 MySQL、Redis、MinIO、UKey SDK 等必要组件 |
| 服务暴露 | 默认应用端口以 monolith Dockerfile 当前 `8080` 为基础，宿主机映射端口仍必须避开常用端口 |
| 健康检查 | 使用单体服务自身健康检查作为轻量版主验收点 |
| 待确认 | 前端静态资源、Nginx、AI 内容审核、告警、地图是否纳入轻量版交付包 |

## 6. 环境最低要求

以下为建议初始值，不是最终生产承诺值；需要结合现场设备规模确认。

| 部署对象 | OS | CPU | 内存 | 磁盘 | 其他 |
| --- | --- | --- | --- | --- | --- |
| 完整管控平台 | Linux x86_64，建议 CentOS 7+/Ubuntu 20.04+ | 8 核以上 | 16 GB 以上 | 200 GB 以上 | Docker、Compose、可访问网关网段 |
| 轻量版管控平台 | Linux x86_64 | 4 核以上 | 8 GB 以上 | 100 GB 以上 | 基于 `monitor-platform-monolith` 部署 |
| 发布侧加密网关 | Linux x86_64 | 2 核以上 | 4 GB 以上 | 50 GB 以上 | USB UKey 透传、SDK 动态库 |
| 终端侧解密网关 | Linux x86_64 | 2 核以上 | 4 GB 以上 | 50 GB 以上 | USB UKey 透传、SDK 动态库 |

环境预检必须覆盖：

- OS 类型和版本。
- CPU 核数。
- 内存容量。
- 磁盘剩余空间。
- Docker / Docker Compose 是否可用。
- 网络连通性。
- 防火墙端口开放情况。
- UKey USB 设备是否可见。
- VAuth SDK 动态库是否存在。

## 7. 端口规则

文档要求：所有服务端口不能使用常用端口，如 `22`、`80`、`3306`、`6379`。

阶段 0 约束：

1. 宿主机对外暴露端口必须避开常用端口。
2. 容器内部端口是否也禁止使用 `3306/6379/80` 需要确认。
3. 如果部署工具通过 SSH 连接目标主机，是否允许继续使用 `22` 需要确认。

当前项目端口状态：

| 服务 | 当前宿主端口 | 当前容器/应用端口 | 阶段 0 判断 |
| --- | --- | --- | --- |
| Nginx | `80`、`443` | `80`、`443` | 不满足文档宿主端口要求 |
| MySQL | `23306` | `3306` | 宿主端口可接受；容器内部待确认 |
| Redis | `26379` | `6379` | 宿主端口可接受；容器内部待确认 |
| Nacos | `18848`、`19848`、`19849`、`9848`、`9849` | `8848`、`9848`、`9849` | 基本可接受，需确认是否全部必须暴露 |
| MinIO | `19000`、`19002` | `9000`、`9001` | 可接受 |
| API Gateway | `8060` | `8060` | 可接受 |
| Device | `8062` | `8062` | 可接受 |
| UKey | `8063` | `8063` | 可接受 |
| Alarm | `8064` | `8064` | 可接受 |
| Content | `8065` | `8065` | 可接受 |
| Rule | `8066` | `8066` | 可接受 |
| Forward | `8067` | `8067` | 可接受 |
| Role | `8068` | `8068` | 可接受 |
| Registry Server | `8069` | `8069` | 可接受 |
| 终端网关 HTTP | host 网络模式 | `8093` | 可接受 |
| 终端网关 MySQL | host 网络模式，默认 `3306` | `3306` | 不满足文档端口要求，需改造 |

建议目标端口：

| 类型 | 建议端口 |
| --- | --- |
| 平台前端 HTTP | `18080` |
| 平台前端 HTTPS | `18443` |
| 平台 MySQL | `23306` |
| 平台 Redis | `26379` |
| 平台 Nacos HTTP | `18848` |
| 平台 MinIO API | `19000` |
| 平台 MinIO Console | `19002` |
| 发布侧加密网关 HTTP | `18092` |
| 终端侧解密网关 HTTP | `18093` |
| 网关本地 MySQL | `23307` |

## 8. 密码与密钥规则

所有部署密码必须满足：

| 规则 | 要求 |
| --- | --- |
| 长度 | 大于等于 8 位 |
| 字符类型 | 必须同时包含数字、字母、特殊字符 |
| 禁用值 | 禁止 `123456`、`admin123456`、`root123456`、`password` 等弱口令 |
| 禁用账号 | 业务管理账号禁止使用 `root`、`admin`、`test`、`guest` |
| 存储 | 不允许写死在 SQL、脚本、镜像、前端包中 |
| 注入方式 | 通过 `.env`、密钥文件、部署工具输入或密钥管理服务注入 |

当前风险：

| 文件 | 风险 |
| --- | --- |
| `monitor-platform/deploy/nacos.sql` | 存在默认 MySQL、Redis、JWT、UKey、MinIO 等配置示例 |
| `monitor-platform/deploy/mysql/init/nacos.sql` | 存在默认配置示例 |
| `terminal-gateway/gateway-udp-proxy/src/main/resources/application.yml` | 存在默认平台地址、证书 ID 示例 |

阶段 1 必须把上述默认值改为模板变量或部署时注入值。

## 9. 证书与 UKey 规则

证书目录建议：

```text
certs/
  platform/
    platform_SIGN.cer
  publish-gateway/
    <deviceId>_SIGN.cer
  terminal-gateway/
    <deviceId>_SIGN.cer
```

每台网关必须绑定：

| 字段 | 说明 |
| --- | --- |
| `deviceId` | 平台内唯一设备编号 |
| `deviceType` | 平台已有设备类型编码，如 `publish_gateway` 或 `terminal_encrypt_gateway` |
| `role` | `encrypt` 或 `decrypt` |
| `ip` | 网关业务 IP |
| `httpPort` | 网关管理端口 |
| `certFile` | 签名证书文件名 |
| `certSerialNo` | 证书序列号 |
| `ukeySn` | UKey 设备 SN |
| `authId` | VAuth 认证 ID |

证书校验必须覆盖：

- 证书文件存在。
- 证书格式可解析。
- 证书序列号与 CSV / 配置一致。
- 证书未过期。
- 证书状态不是挂失、注销、禁用。
- UKey 中可找到对应证书。

## 10. 部署输入物

阶段 1 开始建议统一为以下输入：

```text
install-package/
  deploy.env
  devices.csv
  certs/
  images/
  sdk/
  scripts/
```

`deploy.env` 负责平台级参数：

| 参数类型 | 示例 |
| --- | --- |
| 平台 IP | `PLATFORM_HOST` |
| 平台端口 | `PLATFORM_HTTP_PORT`、`UKEY_PORT`、`DEVICE_PORT` |
| 数据库密码 | `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD` |
| Redis 密码 | `REDIS_PASSWORD` |
| MinIO 密钥 | `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` |
| UKey 参数 | `VAUTH_SERVER_AUTH_ID`、`VAUTH_SERVER_UKEY_SN` |

`devices.csv` 负责设备级参数：

```csv
deviceId,deviceType,role,ip,httpPort,sshPort,sshUser,certFile,certSerialNo,ukeySn,authId,parentDeviceId,screenDeviceId,screenIp,screenPort,remark
PLATFORM-LITE-001,monitor_platform_monolith,control,192.168.1.10,18080,22,ops,,,,,,,,,one-to-many light platform
GW-PUB-001,publish_gateway,encrypt,192.168.1.10,18092,22,ops,publish-gateway/GW-PUB-001_SIGN.cer,,UKEY-PUB-001,44030000003330000305,,,,,same host as lightweight platform
GW-TERM-001,terminal_encrypt_gateway,decrypt,192.168.1.26,18093,22,ops,terminal-gateway/GW-TERM-001_SIGN.cer,,UKEY-TERM-001,44030000003330000310,GW-PUB-001,SCREEN-001,192.168.1.126,9520,
GW-TERM-002,terminal_encrypt_gateway,decrypt,192.168.1.27,18093,22,ops,terminal-gateway/GW-TERM-002_SIGN.cer,,UKEY-TERM-002,44030000003330000311,GW-PUB-001,SCREEN-002,192.168.1.127,9520,
```

说明：`sshPort`、`sshUser` 是否必须保留，取决于最终选择 SSH 部署还是 Agent 部署。

## 11. 自动注册与配置拉取基线

网关部署完成后必须完成以下流程：

1. 网关启动。
2. 网关读取本机 IP、端口、证书、UKey 信息。
3. 网关向管控平台注册。
4. 管控平台校验证书与 UKey 绑定关系。
5. 管控平台生成或返回该网关专属配置。
6. 解密网关主动拉取配置并应用。
7. 网关上报自检结果。

建议接口基线：

| 接口 | 方向 | 说明 |
| --- | --- | --- |
| `POST /deploy/gateway/register` | 网关 -> 平台 | 网关自动注册 |
| `GET /deploy/gateway/{deviceId}/config` | 网关 -> 平台 | 网关主动拉取专属配置 |
| `POST /deploy/gateway/{deviceId}/self-test` | 网关 -> 平台 | 上报自检结果 |
| `POST /deploy/logs` | 部署工具 -> 平台 | 上报部署日志 |

当前项目已有 `device/registry/auto-register`、`registry/register`、`registry/client-config` 等接口，但是否复用这些接口，需要阶段 1 设计时统一。

## 12. 部署验收用例

| 编号 | 用例 | 成功标准 |
| --- | --- | --- |
| ST0-01 | 环境预检 | OS、CPU、内存、磁盘、网络、防火墙、Docker、Compose 检查均给出明确结论 |
| ST0-02 | 密码校验 | 弱口令、默认账号、空密码、占位符均被拒绝 |
| ST0-03 | 端口校验 | 常用端口被拒绝；端口占用时提示冲突 |
| ST0-04 | 平台部署 | 管控平台所有必选容器启动，健康检查通过 |
| ST0-05 | UKey 检测 | 平台侧能识别 UKey，能打开指定证书 |
| ST0-06 | 加密网关注册 | 发布侧加密网关注册到平台，状态在线 |
| ST0-07 | 解密网关注册 | 终端侧解密网关注册到平台，状态在线 |
| ST0-08 | 配置拉取 | 解密网关启动后拉取到专属配置并应用 |
| ST0-09 | 样包加密 | 发布侧网关可对测试数据加密 |
| ST0-10 | 样包解密 | 终端侧网关可对测试密文解密，明文一致 |
| ST0-11 | 部署日志 | 本地生成部署日志，平台可查询部署结果 |
| ST0-12 | 失败重试 | 中断后重新执行，不重复创建已完成资源 |

## 13. 阶段 1 入口条件

开始阶段 1 前，至少确认以下事项：

- 正式部署对象是否包含 `publish-gateway` 独立目录。
- 轻量版管控平台是否只交付 `monitor-platform-monolith`，以及是否额外交付前端/Nginx/AI 审核能力。
- 端口禁用规则是否只限制宿主机端口。
- SSH `22` 是否允许作为部署管理端口。
- 部署工具第一版采用 CLI、Web UI 还是二者都要。
- 是否必须集成 Portainer 和 Agent。
- 现场目标 OS 版本范围。
- 单站点最大网关数量、最大电子屏数量。
- 加密网关自动发现使用网段扫描、mDNS、Agent 上报还是混合方案。

## 14. 待确认问题

| 编号 | 问题 | 影响 |
| --- | --- | --- |
| Q1 | 文档中的“所有服务端口不能使用 22、80、3306、6379”是否包含容器内部端口 | 影响 MySQL、Redis、Nginx、终端网关 MySQL 设计 |
| Q2 | 部署工具是否必须有图形界面 | 影响阶段 4、6 的实现路线 |
| Q3 | 是否允许使用 SSH 批量部署 | 影响是否需要 Agent |
| Q4 | Portainer、Agent 是否为强制组件 | 影响阶段 2 的依赖安装范围 |
| Q5 | `publish-gateway` 目录是否为正式发布侧加密网关 | 影响网关包选择 |
| Q6 | 轻量版除 `monitor-platform-monolith` 外是否还需要前端、Nginx、AI 内容审核等组件 | 影响轻量版 Compose、安装包和健康检查 |
| Q7 | UKey 证书字段以 `certSerialNo`、`authId` 还是 `cerId` 作为主键 | 影响 CSV 与注册接口 |
| Q8 | 是否需要离线部署 | 影响 Docker 镜像、SDK、安装包结构 |
| Q9 | 是否要求部署过程全程无人工交互 | 影响 IP 冲突处理和密码输入方式 |
| Q10 | 现场网络是否允许平台主动扫描网段 | 影响自动发现实现 |

## 15. 阶段 0 结论

当前项目已有加解密网关、UKey、注册、配置下发、Compose 部署等基础能力，但还没有形成部署产品化基线。阶段 0 的后续工作不是继续堆功能，而是先确认本文档第 14 节问题；确认后进入阶段 1，改造统一参数模板、端口策略、密码策略和部署输入物。
