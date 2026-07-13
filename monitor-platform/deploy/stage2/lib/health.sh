#!/usr/bin/env bash

container_state() {
  docker inspect -f '{{.State.Status}}' "$1" 2>/dev/null || true
}

container_health() {
  docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$1" 2>/dev/null || true
}

wait_container_healthy() {
  local container="$1"
  local retries="${2:-$HEALTH_RETRIES}"
  local interval="${3:-$HEALTH_INTERVAL_SECONDS}"
  local i status

  for ((i = 1; i <= retries; i++)); do
    status="$(container_health "$container")"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      log_success "$container is $status"
      return
    fi
    sleep "$interval"
  done

  fail "$container is not healthy, last status: ${status:-missing}"
}

wait_http_ok() {
  local label="$1"
  local url="$2"
  local retries="${3:-$HEALTH_RETRIES}"
  local interval="${4:-$HEALTH_INTERVAL_SECONDS}"
  local i

  for ((i = 1; i <= retries; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log_success "$label ok: $url"
      return
    fi
    sleep "$interval"
  done

  fail "$label health check failed: $url"
}

wait_container_exited_success() {
  local container="$1"
  local retries="${2:-$HEALTH_RETRIES}"
  local interval="${3:-$HEALTH_INTERVAL_SECONDS}"
  local i state exit_code

  for ((i = 1; i <= retries; i++)); do
    state="$(container_state "$container")"
    if [[ "$state" == "exited" ]]; then
      exit_code="$(docker inspect -f '{{.State.ExitCode}}' "$container" 2>/dev/null || echo 1)"
      [[ "$exit_code" == "0" ]] && {
        log_success "$container exited successfully"
        return
      }
      fail "$container exited with code $exit_code"
    fi
    sleep "$interval"
  done

  fail "$container did not finish in time"
}

check_mysql_connection() {
  log_info "checking MySQL connection"
  docker exec mysql mysqladmin ping -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent >/dev/null
  docker exec mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "SELECT 1" >/dev/null
  log_success "MySQL connection ok"
}

check_redis_connection() {
  log_info "checking Redis connection"
  docker exec redis redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null | grep -q PONG
  log_success "Redis connection ok"
}

check_infra_health() {
  wait_container_healthy mysql
  wait_container_healthy redis
  wait_container_healthy minio
  wait_container_healthy nacos
  wait_container_healthy emqx
  wait_http_ok "Nacos HTTP" "http://127.0.0.1:${NACOS_HOST_PORT}/nacos"
  wait_http_ok "MinIO HTTP" "http://127.0.0.1:${MINIO_API_PORT}/minio/health/live"
  wait_http_ok "EMQX Dashboard" "http://127.0.0.1:${EMQX_DASHBOARD_PORT}"
  if [[ "${DISPATCH_MODE:-mqtt}" == "mqtt" || "${DISPATCH_MODE:-mqtt}" == "dual" ]]; then
    wait_http_ok "EMQX MQTT port" "http://127.0.0.1:${EMQX_MQTT_PORT}" 5 3 || log_warn "EMQX MQTT port ${EMQX_MQTT_PORT} not reachable (TCP check skipped for HTTP probe)"
  fi
  check_mysql_connection
  check_redis_connection
}

check_minio_init() {
  wait_container_exited_success minio-init
}

actuator_checks=(
  "monitor-gateway:${GATEWAY_PORT:-8060}:/actuator/health"
  "monitor-device:${DEVICE_PORT:-8062}:/actuator/health"
  "monitor-ukey:${UKEY_PORT:-8063}:/actuator/health"
  "monitor-alarm:${ALARM_PORT:-8064}:/actuator/health"
  "monitor-content:${CONTENT_PORT:-8065}:/actuator/health"
  "monitor-rule:${RULE_PORT:-8066}:/actuator/health"
  "monitor-forward:${FORWARD_PORT:-8067}:/actuator/health"
  "monitor-role:${ROLE_PORT:-8068}:/role/actuator/health"
  "monitor-platform-registry-server:${REGISTRY_PORT:-8069}:/actuator/health"
  "monitor-websocket:${WEBSOCKET_PORT:-8070}:/actuator/health"
  "monitor-log:${LOG_PORT:-8071}:/actuator/health"
)

nacos_services=(
  monitor-gateway monitor-device monitor-ukey monitor-alarm monitor-content monitor-rule
  monitor-forward monitor-role monitor-platform-registry-server monitor-websocket monitor-log
)

check_actuator_health() {
  local item service remainder port path
  for item in "${actuator_checks[@]}"; do
    service="${item%%:*}"
    remainder="${item#*:}"
    port="${remainder%%:*}"
    path="${remainder#*:}"
    wait_http_ok "$service actuator" "http://127.0.0.1:${port}${path}"
  done
}

check_nacos_service_registered() {
  local service="$1"
  local url body
  url="http://127.0.0.1:${NACOS_HOST_PORT}/nacos/v1/ns/instance/list?serviceName=${service}&namespaceId=${NACOS_NAMESPACE}"
  body="$(curl -fsS "$url" 2>/dev/null || true)"
  if echo "$body" | grep -q '"healthy"[[:space:]]*:[[:space:]]*true'; then
    log_success "Nacos registered: $service"
    return
  fi
  fail "Nacos registration not healthy for service: $service"
}

check_nacos_registrations() {
  local service
  log_info "checking Nacos registrations"
  for service in "${nacos_services[@]}"; do
    check_nacos_service_registered "$service"
  done
}
