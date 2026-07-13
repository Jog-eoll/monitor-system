# Stage 2 Single-Node Microservice Deployment

Stage 2 deploys the control platform microservices on one Linux host with local MySQL, Redis, Nacos and MinIO.

## Entry

Run from `deploy/`:

```bash
bash install.sh --check
bash install.sh --dry-run
bash install.sh
bash install.sh --report
bash ctl.sh update-images /path/to/new-image-package.tar.gz
```

You can also call the module directly:

```bash
bash stage2/install.sh --config ./deploy.conf --check
bash stage2/install.sh --config ./deploy.conf --update-images /path/to/images
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
- `DISPATCH_MODE=mqtt` is the standard primary dispatch path. Set `DISPATCH_MODE=http` only for manual operational downgrade.
- `MQTT_BROKER_URL`, `MQTT_USERNAME`, `MQTT_PASSWORD`, `MQTT_TENANT_ID`, `MQTT_SITE_ID` and `MQTT_PLATFORM_CLIENT_ID` are rendered into `monitor-forward`.
- `EMQX_DASHBOARD_PASSWORD`, `MQTT_DEVICE_DEFAULT_PASSWORD` and `UKEY_PASSWORD_ENCRYPT_KEY` must be replaced for production acceptance.
- `ALARM_AUTO_BLACK_SCREEN_ENABLED`, `LOG_TIMELINE_DEFAULT_DAYS`, `LOG_CLEANUP_CRON`, `MQTT_AUTH_ENABLED` and `MQTT_PLATFORM_CLIENT_PREFIX` are rendered explicitly so runtime behavior does not depend on source defaults.

## Image Updates

After the first successful deployment, use one of these commands to update a version by image package:

```bash
bash ctl.sh update-images /tmp/monitor-platform-v1.1.0.tar.gz
bash stage2/install.sh --config ./deploy.conf --update-images /tmp/images --yes
```

The update path accepts:

- A Docker image archive created by `docker save`.
- A directory containing `.tar`, `.tar.gz` or `.tgz` image archives.
- A full one-click package archive or extracted package that contains `package/images`.

The script records pre-update private image IDs under `backups/image-updates/`, loads the new archives, detects which `monitor-platform-*` images changed, and recreates only the affected `monitor-*` services with `docker compose up -d --no-deps --force-recreate`.

The image update flow does not apply database schema changes or restart infra images such as MySQL, Redis, Nacos, MinIO, Nginx or EMQX. Apply required SQL/Liquibase changes before updating images when the version changes schema.

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
