# MQTT 云平台通信链路打通记录

## 1. 背景

平台采用"公网平台 + EMQX Broker + 现场发布网关 MQTT Agent"架构，通过 MQTT 协议实现命令下发与结果回传。本次工作从零开始，完成了网关侧 MQTT Agent 的源码添加、镜像构建、部署，以及平台侧 forward 服务的镜像更新和全链路验证。

## 2. 最终架构

```
平台 Forward (120.26.33.225)  ←→  EMQX (120.26.33.225:1883)  ←→  网关 (192.168.1.25)
     monitor-platform-001              Broker                    publish-gateway-001
     订阅: /+/+/+/up/#                                          订阅: /.../down/proxy-command
     发布: /.../down/proxy-command                               发布: /.../up/reply
                                                                 /.../up/heartbeat
```

### 服务器信息

| 服务器 | IP | 用途 | SSH 凭证 |
|--------|-----|------|----------|
| 平台服务器（公网） | 120.26.33.225 | EMQX + 平台微服务 | root / SJWG@2026 |
| 网关服务器（内网） | 192.168.1.25 | 发布网关 | root / 123456 |

### MQTT Topic 规范

```
/{tenantId}/{siteId}/{deviceId}/down/proxy-command    平台下发指令
/{tenantId}/{siteId}/{deviceId}/up/reply              网关上报回复
/{tenantId}/{siteId}/{deviceId}/up/heartbeat          网关上报心跳
```

默认参数：tenantId=default, siteId=site-001, deviceId=publish-gateway-001

## 3. 完成的工作

### 3.1 网关侧 - 从源码添加 MQTT Agent

**原因**：原 `gateway-udp-proxy:1.0.0` 不含 MQTT 功能（pom.xml 无 Paho 依赖，源码无 MQTT 类）。

**修改的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `pom.xml` | 修改 | 添加 `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` 依赖 |
| `UdpProxyGatewayApplication.java` | 修改 | 添加 `@EnableScheduling` 注解 |
| `src/main/resources/application.yml` | 修改 | 添加 `mqtt-agent` 配置段 |
| `mqtt/MqttAgentProperties.java` | 新建 | MQTT 配置属性类，前缀 `mqtt-agent` |
| `mqtt/MqttConnectionManager.java` | 新建 | Paho MQTT 连接管理，自动重连 |
| `mqtt/MqttCommandHandler.java` | 新建 | 下行命令接收与分发（SELF_APPLY / HTTP） |
| `mqtt/MqttHeartbeatPublisher.java` | 新建 | 定时心跳上报（默认30秒） |
| `mqtt/MqttReplyPublisher.java` | 新建 | 回执消息发布 |

**application.yml 新增配置段**：

```yaml
mqtt-agent:
  enabled: ${MQTT_AGENT_ENABLED:false}
  broker-url: ${MQTT_BROKER_URL:tcp://127.0.0.1:1883}
  username: ${MQTT_USERNAME:}
  password: ${MQTT_PASSWORD:}
  client-id: ${MQTT_CLIENT_ID:publish-gateway-001}
  tenant-id: ${MQTT_TENANT_ID:default}
  site-id: ${MQTT_SITE_ID:site-001}
  command-dedup-ttl-ms: ${MQTT_COMMAND_DEDUP_TTL_MS:86400000}
  reconnect-interval-ms: ${MQTT_RECONNECT_INTERVAL_MS:30000}
  heartbeat-interval-sec: ${MQTT_HEARTBEAT_INTERVAL_SEC:30}
```

**构建方式**：在 192.168.1.25 上使用 Docker 多阶段构建：

```bash
cd /tmp/gateway-build
docker build -t gateway-udp-proxy:mqtt .
```

Dockerfile 使用阿里云 Maven 镜像加速依赖下载：

```dockerfile
FROM maven:3.8-openjdk-8 AS builder
WORKDIR /build
COPY settings.xml /root/.m2/settings.xml  # 阿里云镜像配置
COPY pom.xml .
RUN mvn dependency:resolve -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM openjdk:8-jre-slim
WORKDIR /app
COPY --from=builder /build/target/gateway-udp-proxy-1.0.0.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**网关 .env 关键配置**（`/tmp/publish-gateway-standalone/.env`）：

```bash
# MQTT Agent
MQTT_AGENT_ENABLED=true
MQTT_BROKER_URL=tcp://120.26.33.225:1883
MQTT_CLIENT_ID=publish-gateway-001
MQTT_TENANT_ID=default
MQTT_SITE_ID=site-001

# 关闭 UKey（测试环境）
VAUTH_MOCK_MODE=true
SECURE_PUBLISH_ENABLED=false
CLIENT_RELAY_ENABLED=false

# 平台服务地址（公网）
MONITOR_HOST=120.26.33.225
MONITOR_CONTENT_PORT=8067
MONITOR_UKEY_PORT=8063
MONITOR_DEVICE_PORT=8062
MONITOR_LOG_URL=http://120.26.33.225:8071
```

**docker-compose.yml** 使用新镜像：

```yaml
services:
  gateway-udp-proxy-standalone:
    image: gateway-udp-proxy:mqtt  # 从 1.0.0 改为 mqtt
    # ... 其余配置不变
```

### 3.2 平台侧 - Forward 服务更新

**原因**：服务器上部署的 `monitor-platform-forward:latest` jar 是旧版本（6月10日构建），不含 MQTT 类。源码中已有完整的 MQTT 实现。

**已有源码文件**（无需新建）：

| 文件 | 说明 |
|------|------|
| `config/MqttDispatchProperties.java` | 配置属性，前缀 `forward.dispatch` |
| `service/MqttCommandPublishService.java` | MQTT 命令发布、连接管理、消息分发 |
| `service/MqttReplyHandler.java` | 回执消息处理，更新命令状态 |
| `service/MqttHeartbeatHandler.java` | 心跳处理，更新设备在线状态 |
| `task/MqttCommandTimeoutTask.java` | 超时扫描定时任务 |
| `entity/DeviceMqttCommand.java` | 命令记录实体 |
| `mapper/DeviceMqttCommandMapper.java` | MyBatis Plus Mapper |

**配置映射**（`bootstrap.yml`）：

```yaml
forward:
  dispatch:
    mode: ${DISPATCH_MODE:http}                    # → 环境变量 DISPATCH_MODE
    mqtt-broker-url: ${MQTT_BROKER_URL:ssl://127.0.0.1:8883}  # → MQTT_BROKER_URL
    mqtt-username: ${MQTT_USERNAME:}
    mqtt-password: ${MQTT_PASSWORD:}
    tenant-id: ${MQTT_TENANT_ID:default}
    site-id: ${MQTT_SITE_ID:site-001}
    platform-client-id: ${MQTT_PLATFORM_CLIENT_ID:monitor-platform-001}
```

**环境变量**（`/opt/monitor-platform/.env`）：

```bash
DISPATCH_MODE=mqtt
MQTT_BROKER_URL=tcp://172.17.0.1:1883  # Docker 网关 IP，指向宿主机 EMQX
```

**构建方式**：本地 `mvn install` 构建镜像，通过 192.168.1.25 中转传输到 120.26.33.225：

```bash
# 本地构建
mvn clean install -pl monitor-platform-forward -am -DskipTests

# 传输到网关服务器
docker save monitor-platform-forward:latest | ssh root@192.168.1.25 "docker load"

# 从网关服务器传输到公网服务器
# 通过 SFTP 流式传输（SCP 因 HostKey 问题失败）
```

### 3.3 EMQX 部署

**网关侧 EMQX**（192.168.1.25）：

```yaml
# /tmp/emqx-standalone/docker-compose.yml
services:
  emqx:
    image: emqx/emqx:5.4.0
    ports:
      - "1883:1883"   # MQTT TCP
      - "8883:8883"   # MQTT TLS
      - "18083:18083" # Dashboard
```

**平台侧 EMQX**（120.26.33.225）：已存在，使用相同配置。

**ACL 配置**（`emqx/etc/acl.conf`）：

```erlang
{allow, all, publish, ["/+/+/+/down/#"]}.
{allow, all, subscribe, ["/+/+/+/up/#"]}.
{allow, all, publish, ["/+/+/+/up/#"]}.
{allow, all, subscribe, ["/+/+/+/down/#"]}.
{deny, all, subscribe, ["$SYS/#"]}.
{deny, all, publish, ["$SYS/#"]}.
{allow, all}.
```

### 3.4 网络配置

**阿里云安全组**：开放端口 1883/TCP（入方向，0.0.0.0/0）。

**防火墙**：两台服务器 iptables/firewalld 均未阻拦。

**验证命令**：

```bash
# 从网关测试平台 EMQX
timeout 5 bash -c 'echo >/dev/tcp/120.26.33.225/1883' && echo OK

# 从平台测试网关（不需要，因为网关主动连接平台 EMQX）
```

## 4. 验证结果

### 4.1 EMQX 客户端连接

```
# docker exec emqx emqx ctl clients list

Client(publish-gateway-001, peername=183.14.29.53:29831, connected=true, subscriptions=1)
Client(monitor-platform-001, peername=172.18.0.1:41626, connected=true, subscriptions=1)
```

### 4.2 EMQX 订阅

```
monitor-platform-001 -> topic:/+/+/+/up/# qos:1
publish-gateway-001 -> topic:/default/site-001/publish-gateway-001/down/proxy-command qos:1
```

### 4.3 心跳上报

网关每 30 秒自动发送心跳，平台已收到：

```
[MQTT-HEARTBEAT] 已发布心跳  (网关日志，每30秒)
[MQTT发布] 收到消息: topic=/default/site-001/publish-gateway-001/up/heartbeat  (平台日志)
```

### 4.4 命令下发测试（mosquitto_pub 模拟）

```bash
mosquitto_pub -h 127.0.0.1 \
  -t "/default/site-001/publish-gateway-001/down/proxy-command" \
  -m '{"messageId":"test-001","tenantId":"default","siteId":"site-001",
       "deviceId":"publish-gateway-001","messageType":"PROXY_COMMAND",
       "timestamp":1782391631101,
       "payload":"{\"command\":\"APPLY_CHAIN_CONFIG\",\"actions\":[{\"mode\":\"SELF_APPLY\",\"path\":\"/udp-proxy/config\"}]}"}'
```

**网关响应**：

```
[CMD] 收到下行命令, topic=/default/site-001/publish-gateway-001/down/proxy-command
[CMD] 命令: APPLY_CHAIN_CONFIG, messageId: test-001, deviceId: publish-gateway-001
[CMD] 执行 Action[0]: mode=SELF_APPLY, path=/udp-proxy/config
[CMD] self apply -> 调用本机
[MQTT-REPLY] 已发布回执: messageId=test-001, status=SUCCESS
```

**平台收到回执**：

```
/default/site-001/publish-gateway-001/up/reply
{"commandMessageId":"test-001","gatewayDeviceId":"publish-gateway-001","status":"SUCCESS","timestamp":1782391700972}
```

### 4.5 幂等测试

重复发送相同 messageId，网关日志显示：

```
[CMD] 命令已处理过，跳过: messageId=test-001
```

## 5. 数据库

### device_mqtt_command 表

```sql
CREATE TABLE IF NOT EXISTS device_mqtt_command (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL UNIQUE,
    tenant_id VARCHAR(50) NOT NULL DEFAULT 'default',
    site_id VARCHAR(50) NOT NULL DEFAULT 'site-001',
    gateway_device_id VARCHAR(100) NOT NULL,
    target_device_id VARCHAR(100),
    command VARCHAR(100) NOT NULL,
    message_type VARCHAR(50) NOT NULL DEFAULT 'PROXY_COMMAND',
    topic VARCHAR(255),
    payload_json TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    qos INT DEFAULT 1,
    retry_count INT DEFAULT 0,
    timeout_at DATETIME,
    published_at DATETIME,
    ack_time DATETIME,
    error_message TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_message_id (message_id),
    INDEX idx_status (status),
    INDEX idx_gateway_device_id (gateway_device_id),
    INDEX idx_timeout_at (timeout_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

命令状态流转：`CREATED → PUBLISHED → RECEIVED → PROCESSING → SUCCESS/FAILED/TIMEOUT`

## 6. 已知问题与注意事项

### 6.1 心跳消息格式不匹配

网关发送的心跳是简单 JSON 格式：

```json
{"gatewayDeviceId":"publish-gateway-001","deviceType":"publish_gateway","status":"ONLINE","timestamp":...}
```

平台 `MqttCommandPublishService.onMessage()` 期望 `MqttEnvelope` 格式（包含 messageType 字段），导致心跳被标记为"无 messageType 的消息"而忽略。**不影响功能，但心跳不会更新设备在线状态。**

**修复方案**：修改 `MqttHeartbeatPublisher.java`，将心跳消息包装为 `MqttEnvelope` 格式，或修改平台侧 `onMessage()` 兼容简单 JSON 格式。

### 6.2 Forward 镜像传输

SCP 因 SSH HostKey 验证失败，最终通过 paramiko SFTP 流式传输完成。文件大小约 227MB（gzip 压缩后）。

### 6.3 Docker 网络

Forward 容器使用 `monitor-network` Docker 网络，EMQX 使用宿主机网络。Forward 连接 EMQX 需使用 Docker 网关 IP `172.17.0.1` 而非 `127.0.0.1`。

### 6.4 Maven 构建

服务器无 Maven/Java，使用 Docker 多阶段构建。阿里云 Maven 镜像（`maven.aliyun.com`）显著加速依赖下载（从 5-50 kB/s 提升到 200-600 kB/s）。

## 7. 后续待办

1. **通过平台 API 测试真实命令下发**：需要先创建链路配置（`POST /chain/create`），添加节点，然后触发部署（`POST /chain/deploy/{chainId}`）
2. **修复心跳消息格式**：让平台正确处理网关心跳，更新设备在线状态
3. **HTTP 模式测试**：测试 `mode=HTTP` 的命令转发到局域网设备
4. **断线重连测试**：重启 EMQX 后验证双端自动重连
5. **超时测试**：验证命令超时后状态正确标记为 TIMEOUT
6. **生产环境切换**：启用 TLS（8883端口）、EMQX 设备认证、收紧 ACL

## 8. 关键命令速查

```bash
# 查看 EMQX 客户端
docker exec emqx emqx ctl clients list

# 查看 EMQX 订阅
docker exec emqx emqx ctl subscriptions list

# 手动发送测试命令
mosquitto_pub -h 127.0.0.1 -t "/default/site-001/publish-gateway-001/down/proxy-command" -m '{...}' -q 1

# 订阅上行消息
mosquitto_sub -h 127.0.0.1 -t "/+/+/+/up/#" -v

# 查看网关 MQTT 日志
docker logs --tail 50 gateway-udp-proxy-standalone 2>&1 | grep -i mqtt

# 查看平台 Forward MQTT 日志
docker logs --tail 50 monitor-forward 2>&1 | grep -i mqtt

# 重启网关
cd /tmp/publish-gateway-standalone && docker-compose down && docker-compose up -d

# 重启平台 Forward
cd /opt/monitor-platform && docker compose up -d monitor-forward

# 查询命令记录
docker exec mysql mysql -uroot -p'密码' monitor_platform -e "SELECT * FROM device_mqtt_command ORDER BY id DESC LIMIT 10;"
```
