# MQTT IP Change Dev Delivery

This directory contains the build artifacts for development validation.

## Artifacts

- `monitor-platform-forward-1.0.0.jar`
- `monitor-platform-forward.Dockerfile`
- `deploy-forward.sh`
- `monitor-platform-monolith-1.0.0.jar`
- `monitor-platform-monolith.Dockerfile`
- `deploy-monolith.sh`

## Verified Locally

- Maven package succeeded for `monitor-platform-mqtt-core`, `monitor-platform-forward`, and `monitor-platform-monolith`.
- `MqttAccessControllerTest`: 18 tests, 0 failures, 0 errors.

## Deploy Microservice Forward

Run on the target Linux host that owns `/opt/monitor-platform/deploy`:

```bash
cd /path/to/mqtt-ip-change-dev
chmod +x deploy-forward.sh
./deploy-forward.sh /opt/monitor-platform/deploy
```

The script backs up the current `monitor-platform-forward:latest` image, rebuilds it from the included jar, restarts `monitor-forward`, and prints logs plus actuator health output.

## Deploy Monolith

Run only when the target environment uses the fusion monolith deployment:

```bash
cd /path/to/mqtt-ip-change-dev
chmod +x deploy-monolith.sh
./deploy-monolith.sh /opt/monitor-platform-monolith
```

The script backs up the current `monitor-platform-monolith:1.0.0` image, rebuilds it from the included jar, restarts `monitor-platform-monolith`, and prints logs plus actuator health output.

## Rollback

For `monitor-forward`:

```bash
docker tag monitor-platform-forward:backup-<timestamp> monitor-platform-forward:latest
cd /opt/monitor-platform/deploy
docker compose up -d monitor-forward
```

For `monitor-platform-monolith`:

```bash
docker tag monitor-platform-monolith:backup-<timestamp> monitor-platform-monolith:1.0.0
cd /opt/monitor-platform-monolith
docker compose up -d monitor-platform-monolith
```
