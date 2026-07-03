# Stage 2 Single-Node Microservice Deployment

Stage 2 deploys the control platform microservices on one Linux host with local MySQL, Redis, Nacos and MinIO.

## Entry

Run from `deploy/`:

```bash
bash install.sh --check
bash install.sh --dry-run
bash install.sh
bash install.sh --report
```

You can also call the module directly:

```bash
bash stage2/install.sh --config ./deploy.conf --check
```

## Config

Copy `stage2/deploy.conf.example` to `deploy.conf` and replace all passwords.

Do not reuse the stage 1 `deploy.env.example` directly. In stage 2:

- `MYSQL_HOST=db`, `MYSQL_PORT=3306` are container-internal values.
- `MYSQL_HOST_PORT=23306` is the host port.
- `REDIS_HOST=redis`, `NACOS_HOST=nacos`, and `MINIO_ENDPOINT=http://minio:9000` are required for single-node Compose networking.
- `sdk/lib/libvauthsdk.so` is always required because `monitor-ukey` loads the SDK during startup.
- `UKEY_SETUP_MODE=deferred` allows base deployment when onsite UKey, PIN and AuthId are not ready yet.
- `UKEY_SETUP_MODE=required` requires `VAUTH_SERVER_PASSWORD` and `VAUTH_SERVER_AUTH_ID`.
- `DISPATCH_MODE=http` keeps the existing HTTP gateway dispatch path. Change it to `mqtt` or `dual` only after EMQX and gateway `MQTT_AGENT_ENABLED=true` are ready.
- `MQTT_BROKER_URL`, `MQTT_USERNAME`, `MQTT_PASSWORD`, `MQTT_TENANT_ID`, `MQTT_SITE_ID` and `MQTT_PLATFORM_CLIENT_ID` are rendered into `monitor-forward`.

## Checks

The installer checks:

- OS, CPU, memory and disk.
- Docker and Docker Compose availability, with optional automatic installation.
- Host port conflicts, with interactive replacement unless `--yes` is used.
- firewalld, ufw or iptables ports, with automatic opening unless disabled.
- MySQL, Redis, Nacos and MinIO health before business services start.
- `/actuator/health`, Nacos registration and database connectivity after startup.

`--check`, `--dry-run` and `--report` do not start containers or modify firewall rules. They render `.env`, validate configuration, and generate a report under `report/`.

Use `bash install.sh --legacy` only when you explicitly need the old deployment script.
