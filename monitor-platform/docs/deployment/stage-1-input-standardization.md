# 阶段 1 部署输入标准化说明

版本：v0.1  
日期：2026-06-10  
依赖阶段：阶段 0 部署基线说明

## 1. 阶段目标

阶段 1 的目标是统一部署输入，不直接重构现有 `deploy/install.sh` 主流程。

本阶段新增标准安装包模板：

```text
monitor-platform/deploy/package-template/
  deploy.env.example
  devices.csv.example
  certs/
  images/
  sdk/
  scripts/validate-stage1-input.sh
```

后续阶段 2 再把当前 `deploy.conf`、`gateway.conf`、`.env` 的生成逻辑接入这套标准输入。

## 2. 已交付内容

| 文件 | 作用 |
| --- | --- |
| `deploy/package-template/deploy.env.example` | 平台级参数模板 |
| `deploy/package-template/devices.csv.example` | 设备、网关、电子屏映射模板 |
| `deploy/package-template/certs/` | 证书目录规范 |
| `deploy/package-template/images/` | 离线镜像包目录规范 |
| `deploy/package-template/sdk/lib/` | VAuth SDK 动态库目录规范 |
| `deploy/package-template/scripts/validate-stage1-input.sh` | 阶段 1 输入校验脚本 |
| `deploy/package-template/README.md` | 标准输入包使用说明 |

## 3. 统一编码规则

编码必须沿用当前项目已有命名，不再新增同义枚举。

| 对象 | 统一编码 |
| --- | --- |
| 完整管控平台 | `monitor_platform` |
| 轻量版管控平台 | `monitor_platform_monolith` |
| 发布侧加密网关 | `publish_gateway` |
| 终端侧解密网关 | `terminal_encrypt_gateway` |
| 发布服务器 / 播控主机 | `publish_server` |
| 情报板 / 电子屏 | `info_board` |
| 内容识别服务器 | `content_server` |

说明：`terminal_encrypt_gateway` 是现有项目的设备类型编码。它在部署链路中承担解密职责时，通过 `role=decrypt` 表达，不另建 `terminal_decrypt_gateway`。

## 4. deploy.env 字段边界

`deploy.env` 只描述平台级参数和全局策略。

| 类型 | 字段 |
| --- | --- |
| 部署模式 | `DEPLOY_SCENARIO` |
| 平台身份 | `PLATFORM_CODE`、`PLATFORM_HOST` |
| 平台端口 | `PLATFORM_HTTP_PORT`、`PLATFORM_HTTPS_PORT`、`PLATFORM_API_GATEWAY_PORT`、`MONOLITH_PORT` |
| 安全策略 | `FORBID_COMMON_HOST_PORTS`、`ALLOW_SSH_PORT_22` |
| MySQL | `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD` |
| Redis | `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` |
| Nacos | `NACOS_HOST`、`NACOS_PORT`、`NACOS_NAMESPACE` |
| MinIO | `MINIO_ENDPOINT`、`MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET` |
| UKey/VAuth | `UKEY_ADMIN_USERNAME`、`UKEY_ADMIN_PASSWORD`、`VAUTH_SERVER_MODE`、`VAUTH_SERVER_PASSWORD`、`VAUTH_SERVER_AUTH_ID` |
| 安装包目录 | `CERT_DIR`、`IMAGE_DIR`、`SDK_LIB_DIR` |

`DEPLOY_SCENARIO` 可选值：

| 值 | 含义 |
| --- | --- |
| `full_platform` | 完整管控平台 |
| `lite_platform` | 轻量版管控平台 |
| `one_to_one` | 加密网关一对一 |
| `one_to_many` | 轻量版平台与一个发布侧加密网关同机，多台终端侧网关 |

## 5. devices.csv 字段边界

`devices.csv` 只描述设备级参数和拓扑关系。

字段固定为：

```csv
deviceId,deviceType,role,ip,httpPort,sshPort,sshUser,certFile,certSerialNo,ukeySn,authId,parentDeviceId,screenDeviceId,screenIp,screenPort,remark
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `deviceId` | 平台内唯一设备编号 |
| `deviceType` | 现有设备类型编码 |
| `role` | 职责：`control`、`encrypt`、`decrypt`、`source`、`display`、`content` |
| `ip` | 设备业务 IP |
| `httpPort` | 设备管理端口 |
| `sshPort` | SSH 部署端口；是否允许 `22` 由 `ALLOW_SSH_PORT_22` 控制 |
| `sshUser` | SSH 部署用户；若后续改 Agent 部署可为空 |
| `certFile` | 相对 `certs/` 的证书路径 |
| `certSerialNo` | 证书序列号，可由后续证书解析补齐 |
| `ukeySn` | UKey SN |
| `authId` | VAuth 认证 ID |
| `parentDeviceId` | 上级设备，终端侧网关通常指向 `publish_gateway` |
| `screenDeviceId` | 终端侧网关对应的电子屏编号 |
| `screenIp` | 电子屏 IP |
| `screenPort` | 电子屏端口 |
| `remark` | 备注 |

一对多场景要求：

- 必须有且只有一个 `monitor_platform_monolith`。
- 必须有且只有一个 `publish_gateway`。
- `monitor_platform_monolith` 与 `publish_gateway` 的 `ip` 必须一致。
- 至少有一个 `terminal_encrypt_gateway`。
- 每个 `terminal_encrypt_gateway` 必须配置 `parentDeviceId`、`screenDeviceId`、`screenIp`、`screenPort`。

## 6. 校验规则

`validate-stage1-input.sh` 当前覆盖：

| 类型 | 校验内容 |
| --- | --- |
| 文件 | `deploy.env`、`devices.csv` 是否存在 |
| 环境变量 | 必填项、部署模式、平台编码 |
| 密码 | 长度、字母、数字、特殊字符、弱口令/占位符 |
| 用户名 | 禁止 `root/admin/test/guest` 等默认业务账号 |
| 端口 | 数字范围、常用宿主机端口禁用 |
| CSV | 表头、列数、重复 `deviceId` |
| 编码 | `deviceType`、`role` 是否在允许范围内 |
| 证书 | 网关证书路径是否存在 |
| 拓扑 | 一对一、一对多的数量和同机关系 |

运行方式：

```bash
cd monitor-platform/deploy/package-template
cp deploy.env.example deploy.env
cp devices.csv.example devices.csv
bash scripts/validate-stage1-input.sh .
```

注意：示例 CSV 中的证书文件是占位路径。真实校验前必须放入对应 `.cer` 文件，否则校验会失败，这是预期行为。

## 7. 与现有部署脚本的关系

当前 `monitor-platform/deploy/install.sh` 仍使用：

- `deploy.conf`
- `gateway.conf`
- 自动生成 `.env`

阶段 1 不直接替换这些入口。阶段 2 的改造目标是：

1. 从 `deploy.env` 生成或兼容 `deploy.conf`。
2. 从 `devices.csv` 生成网关部署配置。
3. 将证书、SDK、镜像目录纳入预检。
4. 保留现有 `install.sh` 能力，逐步迁移，不一次性推翻。

## 8. 阶段 1 Done 定义

| 检查项 | 状态 |
| --- | --- |
| 标准安装包目录已创建 | 已完成 |
| 平台级参数模板已创建 | 已完成 |
| 设备拓扑 CSV 模板已创建 | 已完成 |
| 证书、镜像、SDK 目录规范已创建 | 已完成 |
| 输入校验脚本已创建 | 已完成 |
| 文档说明已创建 | 已完成 |
| `install.sh` 已消费新输入 | 未开始，归入阶段 2 |

## 9. 后续阶段入口

阶段 2 可以开始处理：

- 将 `deploy.env` 接入现有 `install.sh`。
- 将 `devices.csv` 接入发布侧加密网关和终端侧网关部署。
- 把端口、密码、证书、SDK 检查从独立校验脚本迁移到正式部署流程。
