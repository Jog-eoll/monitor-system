#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="${1:-/opt/monitor-platform/deploy}"
SERVICE_NAME="${SERVICE_NAME:-monitor-forward}"
IMAGE_NAME="${IMAGE_NAME:-monitor-platform-forward:latest}"
PORT="${FORWARD_PORT:-8067}"
TS="$(date +%Y%m%d%H%M%S)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="/tmp/monitor-forward-build-${TS}"

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo "ERROR: docker compose or docker-compose is required" >&2
  exit 1
fi

if [ ! -f "${SCRIPT_DIR}/monitor-platform-forward-1.0.0.jar" ]; then
  echo "ERROR: missing monitor-platform-forward-1.0.0.jar" >&2
  exit 1
fi

if [ ! -f "${SCRIPT_DIR}/monitor-platform-forward.Dockerfile" ]; then
  echo "ERROR: missing monitor-platform-forward.Dockerfile" >&2
  exit 1
fi

if [ ! -f "${DEPLOY_DIR}/docker-compose.yml" ]; then
  echo "ERROR: ${DEPLOY_DIR}/docker-compose.yml not found" >&2
  exit 1
fi

RUNNING_IMAGE_ID="$(docker inspect -f '{{.Image}}' "${SERVICE_NAME}" 2>/dev/null || true)"
if [ -n "${RUNNING_IMAGE_ID}" ] && docker image inspect "${RUNNING_IMAGE_ID}" >/dev/null 2>&1; then
  docker tag "${RUNNING_IMAGE_ID}" "monitor-platform-forward:backup-${TS}"
  echo "backup image: monitor-platform-forward:backup-${TS}"
elif docker image inspect "${IMAGE_NAME}" >/dev/null 2>&1; then
  docker tag "${IMAGE_NAME}" "monitor-platform-forward:backup-${TS}"
  echo "backup image: monitor-platform-forward:backup-${TS}"
fi

rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/target"
cp "${SCRIPT_DIR}/monitor-platform-forward-1.0.0.jar" "${BUILD_DIR}/target/"
cp "${SCRIPT_DIR}/monitor-platform-forward.Dockerfile" "${BUILD_DIR}/Dockerfile"

docker build -t "${IMAGE_NAME}" "${BUILD_DIR}"

cd "${DEPLOY_DIR}"
"${COMPOSE[@]}" up -d "${SERVICE_NAME}"

echo "container status:"
docker ps --filter "name=${SERVICE_NAME}"

echo "tail logs:"
docker logs --tail 120 "${SERVICE_NAME}" || true

if command -v curl >/dev/null 2>&1; then
  echo "health check:"
  curl -fsS "http://127.0.0.1:${PORT}/actuator/health" || true
  echo
fi

echo "done"
