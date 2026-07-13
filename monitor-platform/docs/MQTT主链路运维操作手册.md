# MQTT 主链路运维操作手册

版本日期：2026-07-09

## 1. 适用范围

本手册适用于微服务部署形态：

- 管控平台：`monitor-platform/deploy`
- 发布网关：`publish-gateway/deploy`
- 终端网关：`terminal-gateway/deploy-package`
- MQTT Broker：EMQX

默认链路为 MQTT：

```text
monitor-forward -> EMQX -> publish-gateway / terminal-gateway
```

HTTP 仅作为运维人工降级路径。单体/融合部署有独立模板，`deploy-standalone` 只作为现场快照保留，不作为本手册的标准模板来源。

## 2. 关键默认值

| 配置项 | 标准值 | 说明 |
| --- | --- | --- |
| `DISPATCH_MODE` | `mqtt` | 平台标准下发模式 |
| `MQTT_AGENT_ENABLED` | `true` | 发布网关、终端网关默认连接 EMQX |
| `MONITOR_DEVICE_PORT` | `8062` | 设备服务 |
| `MONITOR_UKEY_PORT` | `8063` | UKey/证书服务 |
| `MONITOR_CONTENT_PORT` | `8065` | 内容服务 |
| `MONITOR_LOG_URL` | `http://<平台IP>:8071` | 日志服务完整 URL |
| `MQTT_ALLOW_EMPTY_DEVICE_PASSWORD` | `false` | 禁止空设备密码默认放行 |

必须设置强密码或现场分配值：

- `EMQX_DASHBOARD_PASSWORD`
- `MQTT_DEVICE_DEFAULT_PASSWORD`
- `MQTT_BROKER_URL`
- `MQTT_USERNAME`
- `MQTT_PASSWORD`
- 网关侧 UKey/VAuth、数据库、Redis、MinIO 等密码

不要在截图、工单、命令历史或文档中粘贴真实密码。

## 3. 上线前检查

在三台角色主机上先备份现有配置：

```bash
cp .env .env.bak-$(date +%Y%m%d%H%M%S) 2>/dev/null || true
cp docker-compose.yml docker-compose.yml.bak-$(date +%Y%m%d%H%M%S) 2>/dev/null || true
cp application.yml application.yml.bak-$(date +%Y%m%d%H%M%S) 2>/dev/null || true
```

检查端口方向：

| 来源 | 目标 | 端口 |
| --- | --- | --- |
| 发布网关、终端网关 | EMQX | `8883` 或 `1883` |
| 发布网关 | 平台设备/UKey/内容/日志服务 | `8062/8063/8065/8071` |
| 终端网关 | 平台设备/UKey/内容服务 | `8062/8063/8065` |
| 运维浏览器 | EMQX Dashboard | `18083` |

## 4. 管控平台配置

目录：

```bash
cd /opt/monitor-platform/deploy
```

从模板生成 `.env`：

```bash
cp .env.example .env
vi .env
```

至少确认：

```env
DISPATCH_MODE=mqtt
EMQX_DASHBOARD_PASSWORD=<强密码>
MQTT_BROKER_URL=ssl://<EMQX地址>:8883
MQTT_USERNAME=<平台MQTT账号>
MQTT_PASSWORD=<平台MQTT密码>
MQTT_DEVICE_DEFAULT_PASSWORD=<网关共享设备密码>
MQTT_ALLOW_EMPTY_DEVICE_PASSWORD=false
MONITOR_DEVICE_URL=http://127.0.0.1:8062
MONITOR_LOG_URL=http://127.0.0.1:8071
```

启动或重启：

```bash
docker compose config >/dev/null
docker compose up -d
```

基础检查：

```bash
docker compose ps
docker logs --tail=200 emqx
docker logs --tail=200 monitor-forward
curl -f http://127.0.0.1:8062/actuator/health
curl -f http://127.0.0.1:8063/actuator/health
curl -f http://127.0.0.1:8065/actuator/health
curl -f http://127.0.0.1:8071/actuator/health
ss -lntp | grep -E '1883|8883|18083'
```

## 5. 发布网关配置

目录：

```bash
cd /opt/publish-gateway
```

从模板生成 `.env`：

```bash
cp .env.example .env
vi .env
```

至少确认：

```env
MONITOR_HOST=<平台IP或域名>
MONITOR_DEVICE_PORT=8062
MONITOR_CONTENT_PORT=8065
MONITOR_UKEY_PORT=8063
MONITOR_LOG_URL=http://<平台IP或域名>:8071
MQTT_AGENT_ENABLED=true
MQTT_BROKER_URL=ssl://<EMQX地址>:8883
MQTT_USERNAME=<发布网关MQTT账号>
MQTT_PASSWORD=<发布网关MQTT密码>
MQTT_CLIENT_ID=publish-gateway-001
MQTT_TENANT_ID=default
MQTT_SITE_ID=site-001
```

如需覆盖完整 URL，可设置：

```env
CONTROL_PLATFORM_URL=http://<平台IP或域名>:8063
MONITOR_CONTENT_URL=http://<平台IP或域名>:8065
MONITOR_LOG_URL=http://<平台IP或域名>:8071
```

完整 URL 优先级最高；只要设置了完整 URL，`MONITOR_*_PORT` 不会再改变该地址。

启动或重启：

```bash
docker compose config >/dev/null
docker compose up -d
docker logs --tail=200 gateway-udp-proxy
```

## 6. 终端网关配置

目录：

```bash
cd /opt/terminal-gateway
```

按部署包生成或编辑配置，确认：

```env
MONITOR_HOST='<平台IP或域名>'
MONITOR_UKEY_PORT='8063'
MONITOR_DEVICE_PORT='8062'
MONITOR_CONTENT_PORT='8065'
MONITOR_LOG_URL='http://<平台IP或域名>:8071'
MQTT_AGENT_ENABLED='true'
MQTT_BROKER_URL='ssl://<EMQX地址>:8883'
MQTT_USERNAME='<终端网关MQTT账号>'
MQTT_PASSWORD='<终端网关MQTT密码>'
MQTT_CLIENT_ID='terminal-gateway-001'
MQTT_TENANT_ID='default'
MQTT_SITE_ID='site-001'
```

如需覆盖完整 URL，可设置：

```env
CONTROL_PLATFORM_URL=http://<平台IP或域名>:8063
PROBE_REPORT_URL=http://<平台IP或域名>:8062/device/unified/batch-status
SNAPSHOT_REPORT_URL=http://<平台IP或域名>:8065/content/receive
```

完整 URL 优先级最高；如发现运行时仍访问 `8080`，先检查这些覆盖项是否残留旧值。

启动或重启：

```bash
docker compose config >/dev/null
docker compose up -d
docker logs --tail=200 terminal-gateway-standalone
```

## 7. 标准验收

平台侧：

```bash
docker logs --tail=300 monitor-forward | grep -Ei 'mqtt|register|heartbeat|reply|dispatch'
docker logs --tail=200 emqx | grep -Ei 'client|auth|connected|disconnected'
```

发布网关侧：

```bash
docker logs --tail=300 gateway-udp-proxy | grep -Ei 'mqtt|register|heartbeat|reply|connected|error'
```

终端网关侧：

```bash
docker logs --tail=300 terminal-gateway-standalone | grep -Ei 'mqtt|register|heartbeat|reply|snapshot|probe|connected|error'
```

验收项：

- EMQX `1883/8883` 已监听，Dashboard 不能使用默认 `public` 口令。
- `monitor-forward` 日志能看到 MQTT 下发或回执处理。
- 发布网关、终端网关日志能看到 MQTT 连接、注册、心跳或回执。
- 平台设备服务能看到网关注册或心跳。
- 业务命令 `NOOP`、`QUERY_STATUS` 或 `CONTROL_DELIVERY` 能拿到最终回执。
- 终端状态上报走 `8062`，UKey 校验走 `8063`，截图/内容上报走 `8065`，日志上报走 `8071`。

## 8. 人工降级到 HTTP

只有在 EMQX、证书、网络或 MQTT Agent 故障需要隔离时执行。

平台侧修改：

```bash
cd /opt/monitor-platform/deploy
cp .env .env.bak-$(date +%Y%m%d%H%M%S)
sed -i 's/^DISPATCH_MODE=.*/DISPATCH_MODE=http/' .env
docker compose up -d monitor-forward
docker logs --tail=200 monitor-forward
```

如果网关侧 MQTT 重连日志影响排查，可临时关闭网关 Agent：

```bash
sed -i 's/^MQTT_AGENT_ENABLED=.*/MQTT_AGENT_ENABLED=false/' .env
docker compose up -d
```

降级前必须确认：

- 平台到发布网关 `8092/tcp` 可达。
- 平台到终端网关 `8093/tcp` 可达。
- HTTP 下发所需网关地址仍是当前现场 IP。

## 9. 恢复 MQTT 主链路

平台侧恢复：

```bash
cd /opt/monitor-platform/deploy
sed -i 's/^DISPATCH_MODE=.*/DISPATCH_MODE=mqtt/' .env
docker compose up -d monitor-forward
```

发布网关和终端网关恢复：

```bash
sed -i 's/^MQTT_AGENT_ENABLED=.*/MQTT_AGENT_ENABLED=true/' .env
docker compose up -d
```

恢复后按第 7 节重新验收 MQTT 注册、心跳、命令回执。

## 10. 常见故障处理

### 10.1 EMQX Dashboard 无法登录

- 检查 `.env` 中 `EMQX_DASHBOARD_PASSWORD` 是否已设置。
- 不允许使用 `public` 作为生产默认口令。
- 修改后重建 EMQX 容器前先备份配置和数据目录。

### 10.2 网关连接不上 MQTT

检查：

```bash
grep -E '^(MQTT_AGENT_ENABLED|MQTT_BROKER_URL|MQTT_CLIENT_ID|MQTT_TENANT_ID|MQTT_SITE_ID|MQTT_TRUSTSTORE_PATH)=' .env
nc -vz <EMQX地址> 8883
docker logs --tail=300 gateway-udp-proxy
docker logs --tail=300 terminal-gateway-standalone
```

重点看：

- `MQTT_AGENT_ENABLED=true`
- `MQTT_BROKER_URL` 地址和协议正确
- `MQTT_TENANT_ID`、`MQTT_SITE_ID` 与平台一致
- `MQTT_CLIENT_ID` 不重复
- TLS truststore 是否匹配现场证书

### 10.3 仍访问旧 `8080`

先查完整 URL 覆盖项：

```bash
grep -E 'CONTROL_PLATFORM_URL|MONITOR_CONTENT_URL|MONITOR_LOG_URL|PROBE_REPORT_URL|SNAPSHOT_REPORT_URL|MONITOR_.*_PORT' .env application.yml
```

处理原则：

- 微服务默认端口应为 `8062/8063/8065/8071`。
- 完整 URL 优先级高于端口变量。
- 单体/融合的 `8080` 只能保留在独立模板内。

### 10.4 平台拒绝空设备密码

这是预期安全行为。检查平台：

```bash
grep -E '^MQTT_ALLOW_EMPTY_DEVICE_PASSWORD=' /opt/monitor-platform/deploy/.env
test -n "$(grep '^MQTT_DEVICE_DEFAULT_PASSWORD=' /opt/monitor-platform/deploy/.env | cut -d= -f2-)" && echo "MQTT_DEVICE_DEFAULT_PASSWORD is set"
```

应满足：

```env
MQTT_DEVICE_DEFAULT_PASSWORD=<非空共享密码>
MQTT_ALLOW_EMPTY_DEVICE_PASSWORD=false
```

## 11. 变更记录要求

每次现场切换必须记录：

- 变更时间、操作人、主机、目录。
- 修改前后的 `DISPATCH_MODE`、`MQTT_AGENT_ENABLED`、Broker 地址、网关 clientId。
- 执行的重启命令。
- 验收结果：注册、心跳、命令回执、端口连通性。
- 回滚或恢复时间。

不要记录真实密码、token、UKey PIN、证书私钥或数据库口令。
