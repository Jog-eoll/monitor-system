# monitor-platform-monolith host 模式部署说明

## 适用场景

测试环境 `192.168.1.233` 使用 Docker host 网络部署单体应用，并让容器直接访问宿主机网络、USB UKey 和本机 MySQL/Redis/MinIO。

## 前置条件

- `192.168.1.233` 已安装 Docker 和 Docker Compose。
- 宿主机已有 MySQL、Redis、MinIO，或已按项目 `deploy/docker-compose.yml` 启动基础组件。
- MySQL 已创建 `monitor_platform` 数据库，单体应用启动后会通过 Liquibase 创建/更新表结构。
- 如果启用真实 UKey，宿主机存在 `/dev/bus/usb`，并且 SDK 动态库已放到部署目录 `lib/`。

## 打包镜像

在开发机或测试机项目根目录执行：

```bash
cd /path/to/monitor-platform
mvn -pl monitor-platform-monolith -am -DskipTests install
```

单体模块已按微服务模块方式集成 `docker-maven-plugin`，镜像构建阶段继承父 POM 配置，绑定在 `install` 阶段。

如果只需要编译 Jar，不触发 Docker 镜像构建：

```bash
mvn -pl monitor-platform-monolith -am -DskipTests package
```

如果本机没有 Maven，但已有 Jar，可手动构建镜像：

```bash
docker build -t monitor-platform-monolith:1.0.0 ./monitor-platform-monolith
```

## 准备测试机目录

```bash
mkdir -p /opt/monitor-platform-monolith/{config,logs,lib}
```

将以下文件复制到 `/opt/monitor-platform-monolith`：

- `deploy/monolith-host/docker-compose.yml`
- `deploy/monolith-host/.env.example`，复制后改名为 `.env`
- 真实 UKey SDK 的 `.so` 依赖库复制到 `lib/`

## 配置 .env

重点修改：

```bash
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DB=monitor_platform
MYSQL_USERNAME=root
MYSQL_PASSWORD=实际密码

REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=实际密码

MINIO_ENDPOINT=http://127.0.0.1:9000
MINIO_ACCESS_KEY=实际账号
MINIO_SECRET_KEY=实际密码

VAUTH_SERVER_PASSWORD=实际UKey密码
VAUTH_SERVER_AUTH_ID=实际认证ID
```

如果测试机基础组件仍使用项目原 compose 的映射端口，则按实际端口改为：

```bash
MYSQL_PORT=23306
REDIS_PORT=26379
MINIO_ENDPOINT=http://127.0.0.1:19000
```

## 启动

```bash
cd /opt/monitor-platform-monolith
docker compose up -d
```

## 验证

```bash
docker ps | grep monitor-platform-monolith
docker logs -f monitor-platform-monolith
curl http://127.0.0.1:8080/actuator/health
curl http://192.168.1.233:8080/actuator/health
```

预期返回 `UP`。如果返回非 `UP` 或启动失败，优先排查 MySQL、Redis、MinIO 和 UKey SDK 路径。

## 常见问题

| 现象 | 高概率原因 | 处理 |
| --- | --- | --- |
| 数据库连接失败 | host 模式下 `127.0.0.1` 指向宿主机，但端口填错 | 确认宿主机 MySQL 监听端口，项目旧 compose 常见是 `23306` |
| Redis 连接失败 | Redis 端口或密码不一致 | 用 `redis-cli -h 127.0.0.1 -p 端口 -a 密码 ping` 验证 |
| UKey 初始化失败 | SDK `.so` 未挂载或 `LD_LIBRARY_PATH` 不正确 | 将依赖库放到 `lib/`，确认 `.env` 中路径为 `/opt/monitor-platform-monolith/lib` |
| 端口被占用 | 宿主机已有进程占用 `8080` | 修改 `.env` 的 `SERVER_PORT` |
