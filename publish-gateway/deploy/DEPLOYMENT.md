# 发布网关部署说明

本文档用于交付部署人员在 Linux 服务器上部署 `gateway-udp-proxy:1.0.0`。

## 1. 部署目录

建议固定部署目录：

```bash
/opt/publish-gateway
```

目录结构必须如下：

```text
/opt/publish-gateway
├── docker-compose.yml
├── application.yml
├── .env
├── mysql
│   ├── data
│   └── init
│       └── udp_proxy_rule.sql
├── redis
│   └── data
├── lib
│   ├── libvauthsdk.so
│   └── 其他 VAuth 依赖 so 文件
├── certs
│   ├── ${VAUTH_AUTH_ID}_SIGN.cer
│   └── ${VAUTH_SERVER_ID}_SIGN.cer
├── trust
│   └── default.pub
├── secure-publish
│   └── rejected
└── logs
```

## 2. 需要交付的文件

必须放到 `/opt/publish-gateway`：

| 文件/目录 | 说明 |
|---|---|
| `docker-compose.yml` | Docker Compose 部署文件 |
| `application.yml` | 发布网关外部配置文件 |
| `.env` | 部署环境变量文件，由 `.env.example` 复制后填写 |
| `mysql/init/udp_proxy_rule.sql` | MySQL 首次初始化脚本 |
| `lib/` | VAuth SDK 动态库目录 |
| `certs/` | VAuth 证书目录 |
| `trust/` | Sigma Play 安全包验签公钥目录 |
| `secure-publish/rejected/` | 安全包拒绝记录目录 |

同时必须保证服务器上存在镜像：

```text
gateway-udp-proxy:1.0.0
```

如果现场无法从镜像仓库拉取，需要交付离线镜像包，例如：

```text
gateway-udp-proxy-1.0.0.tar
```

部署前导入：

```bash
docker load -i gateway-udp-proxy-1.0.0.tar
docker images | grep gateway-udp-proxy
```

## 3. 初始化目录

```bash
mkdir -p /opt/publish-gateway/mysql/data
mkdir -p /opt/publish-gateway/mysql/init
mkdir -p /opt/publish-gateway/redis/data
mkdir -p /opt/publish-gateway/lib
mkdir -p /opt/publish-gateway/certs
mkdir -p /opt/publish-gateway/trust
mkdir -p /opt/publish-gateway/secure-publish/rejected
mkdir -p /opt/publish-gateway/logs
```

如果服务器启用了 SELinux 或目录权限较严格，需要确保 Docker 可以读写数据目录：

```bash
chmod -R 755 /opt/publish-gateway
chmod -R 777 /opt/publish-gateway/mysql/data /opt/publish-gateway/redis/data /opt/publish-gateway/secure-publish/rejected /opt/publish-gateway/logs
```

## 4. 编写 .env

在 `/opt/publish-gateway` 下创建 `.env`：

```bash
cp .env.example .env
vi .env
```

`.env` 必填示例：

```env
# MySQL
MYSQL_HOST_PORT=3307
MYSQL_ROOT_PASSWORD=请填写强密码
MYSQL_APP_USER=udp_proxy_app
MYSQL_APP_PASSWORD=请填写强密码

# Redis
REDIS_PASSWORD=请填写强密码

# 管控平台
MONITOR_HOST=192.168.1.233
MONITOR_DEVICE_PORT=8062
MONITOR_CONTENT_PORT=8065
MONITOR_UKEY_PORT=8063

# MQTT Agent
MQTT_AGENT_ENABLED=false
MQTT_BROKER_URL=ssl://CHANGE_ME_emqx_host:8883
MQTT_USERNAME=
MQTT_PASSWORD=
MQTT_CLIENT_ID=publish-gateway-001
MQTT_TENANT_ID=default
MQTT_SITE_ID=site-001
MQTT_RECONNECT_INTERVAL_MS=30000
MQTT_COMMAND_DEDUP_TTL_MS=86400000
MQTT_DEDUP_CLEANUP_INTERVAL_MS=600000

# MinIO
MINIO_HOST=192.168.1.233
MINIO_PORT=9000
MINIO_ACCESS_KEY=请填写
MINIO_SECRET_KEY=请填写
MINIO_BUCKET=monitor-content

# UKey / VAuth
VAUTH_DEVICE_TYPE=ukey
VAUTH_PASSWORD=请填写
VAUTH_AUTH_ID=请填写客户端证书ID
VAUTH_SERVER_ID=请填写服务端证书ID
VAUTH_SVAC_MODE=false

# Sigma Play 安全包验签
SECURE_PUBLISH_ENABLED=true
SECURE_PUBLISH_TRUST_STORE_DIR=/opt/publish-gateway/trust
SECURE_PUBLISH_TRUSTED_KEY_IDS=default
SECURE_PUBLISH_DEFAULT_KEY_ID=default
SECURE_PUBLISH_VERIFIER_PUBLIC_KEY_PATH=
SECURE_PUBLISH_PACKAGE_EXTENSIONS=tar,spkg,zip
SECURE_PUBLISH_MAX_PACKAGE_SIZE_MB=500

# info-publish-client 中继入口
CLIENT_RELAY_ENABLED=true
CLIENT_RELAY_PORT=18092
CLIENT_RELAY_TRUSTED_IPS=
CLIENT_RELAY_VERIFY_SIGNATURE=false
CLIENT_RELAY_SIGNATURE_SECRET=
CLIENT_RELAY_REJECT_DIRECT_UDP=false

# 设备注册元数据
DEVICE_LOCATION=
DEVICE_VERSION=1.0.0
DEVICE_MANUFACTURER=
DEVICE_MODEL=gateway-udp-proxy
DEVICE_REMARK=
```

注意：

- `MYSQL_HOST_PORT` 默认建议使用 `3307`，避免和服务器已有 MySQL 的 `3306` 冲突。
- `MONITOR_HOST`、`MINIO_HOST` 不要带 `http://`。
- `VAUTH_AUTH_ID` 必须能对应 `certs/${VAUTH_AUTH_ID}_SIGN.cer`。
- `VAUTH_SERVER_ID` 必须能对应 `certs/${VAUTH_SERVER_ID}_SIGN.cer`。
- 当前签名机返回的 `keyId` 是 `default`，所以发布网关公钥文件应放在 `trust/default.pub`。
- 如果签名机改为 `spkg-prod-01`，需要同步修改 `SECURE_PUBLISH_TRUSTED_KEY_IDS`、`SECURE_PUBLISH_DEFAULT_KEY_ID` 和 trust 公钥文件名。
- `CLIENT_RELAY_PORT` 是 info-publish-client 转发到加密网关的中继 UDP 端口；使用 host 网络模式时不需要 `ports` 映射，但必须确认宿主机防火墙放行该 UDP 端口。

## 5. MySQL 初始化说明

MySQL 容器首次启动时，会执行：

```text
/opt/publish-gateway/mysql/init/udp_proxy_rule.sql
```

容器内路径为：

```text
/docker-entrypoint-initdb.d/01-udp_proxy_rule.sql
```

注意：

- 只有 `/opt/publish-gateway/mysql/data` 为空时，MySQL 官方镜像才会执行初始化 SQL。
- 如果 `mysql/data` 已经有数据，重新执行 `docker compose up -d` 不会再次更新表结构。
- 如果需要升级已有表结构，应使用单独的 `ALTER TABLE` 迁移 SQL，不要依赖初始化 SQL。

## 6. 启动

进入部署目录：

```bash
cd /opt/publish-gateway
```

检查 Compose 配置：

```bash
docker compose config
```

启动：

```bash
docker compose up -d
```

查看容器：

```bash
docker ps
```

## 7. 验证

检查 MySQL：

```bash
docker exec -it publish-gateway-mysql mysql -uroot -p
```

进入 MySQL 后执行：

```sql
SHOW DATABASES;
USE udp_proxy_gateway;
SHOW TABLES;
DESC udp_proxy_rule;
```

检查 Redis：

```bash
docker exec -it publish-gateway-redis redis-cli -a "$REDIS_PASSWORD" ping
```

预期输出：

```text
PONG
```

检查发布网关日志：

```bash
docker logs -f gateway-udp-proxy
```

重点确认没有以下错误：

```text
Access denied for user
Communications link failure
Connection refused
No such file or directory
UnsatisfiedLinkError
```

检查 HTTP 管理端口：

```bash
curl -v http://127.0.0.1:8092/
```

如果接口需要具体路径，可按现场接口文档或日志中的映射路径验证。

## 8. 停止与重启

停止：

```bash
cd /opt/publish-gateway
docker compose down
```

重启：

```bash
cd /opt/publish-gateway
docker compose up -d
```

## 9. 常见问题

### 9.1 MySQL 初始化 SQL 没执行

检查：

```bash
ls -lah /opt/publish-gateway/mysql/data
```

如果目录中已经存在 `ibdata1`、`mysql/`、`udp_proxy_gateway/`，说明数据库已经初始化过，启动时不会再次执行 SQL。

### 9.2 3307 端口被占用

修改 `.env`：

```env
MYSQL_HOST_PORT=3308
```

然后重启：

```bash
docker compose up -d
```

### 9.3 UKey 动态库加载失败

检查 `lib/` 是否包含 `libvauthsdk.so` 以及它依赖的 so 文件：

```bash
ls -lah /opt/publish-gateway/lib
```

检查容器是否挂载 USB：

```bash
lsusb
ls -lah /dev/bus/usb
```

### 9.4 证书文件找不到

检查证书文件名是否和 `.env` 一致：

```bash
ls -lah /opt/publish-gateway/certs
```

必须存在：

```text
${VAUTH_AUTH_ID}_SIGN.cer
${VAUTH_SERVER_ID}_SIGN.cer
```
