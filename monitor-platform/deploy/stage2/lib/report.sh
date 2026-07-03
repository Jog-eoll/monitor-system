#!/usr/bin/env bash

write_stage2_report() {
  local result="${1:-unknown}"
  local stage="${2:-completed}"
  local report_dir="$DEPLOY_DIR/report"
  local report_file
  report_file="$report_dir/stage2-report-$(date +%Y%m%d-%H%M%S).txt"
  mkdir -p "$report_dir"

  {
    echo "Stage2 microservice deployment report"
    echo "Generated at: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "Result: $result"
    echo "Stage: $stage"
    echo
    echo "[Paths]"
    echo "Deploy dir: $DEPLOY_DIR"
    echo "Config file: $CONFIG_FILE"
    echo "Env file: $ENV_FILE"
    echo "Compose file: $COMPOSE_FILE"
    echo
    echo "[Runtime mode]"
    echo "UKEY_SETUP_MODE=${UKEY_SETUP_MODE:-}"
    echo "VAUTH_SERVER_MODE=${VAUTH_SERVER_MODE:-}"
    echo "PULL_PUBLIC_IMAGES=${PULL_PUBLIC_IMAGES:-}"
    echo "AUTO_OPEN_FIREWALL=${AUTO_OPEN_FIREWALL:-}"
    echo
    echo "[Ports]"
    echo "Nginx: ${NGINX_PORT:-}/ ${NGINX_SSL_PORT:-}"
    echo "Nacos: ${NACOS_HOST_PORT:-}"
    echo "MySQL: ${MYSQL_HOST_PORT:-}"
    echo "Redis: ${REDIS_HOST_PORT:-}"
    echo "MinIO: ${MINIO_API_PORT:-}/${MINIO_CONSOLE_PORT:-}"
    echo "Gateway: ${GATEWAY_PORT:-}"
    echo "Device/UKey/Alarm/Content/Rule: ${DEVICE_PORT:-}/${UKEY_PORT:-}/${ALARM_PORT:-}/${CONTENT_PORT:-}/${RULE_PORT:-}"
    echo "EMQX: MQTT=${EMQX_MQTT_PORT:-} MQTTS=${EMQX_MQTTS_PORT:-} Dashboard=${EMQX_DASHBOARD_PORT:-}"
    echo "DISPATCH_MODE=${DISPATCH_MODE:-}"
    echo
    echo "[Delivery files]"
    for item in \
      "$COMPOSE_FILE" \
      "$DEPLOY_DIR/mysql/init/00-create-app-user.sh" \
      "$DEPLOY_DIR/mysql/init/monitor_platform_init.sql" \
      "$DEPLOY_DIR/mysql/init/nacos.sql" \
      "$DEPLOY_DIR/mysql/init/02-info-publish-client.sql" \
      "$DEPLOY_DIR/nacos/conf/application.properties" \
      "$DEPLOY_DIR/nginx/conf/nginx.conf" \
      "$DEPLOY_DIR/nginx/www/index.html" \
      "$DEPLOY_DIR/emqx/etc/emqx.conf" \
      "$DEPLOY_DIR/emqx/etc/acl.conf" \
      "$DEPLOY_DIR/sdk/lib/libvauthsdk.so"; do
      if [[ -f "$item" ]]; then
        echo "OK      $item"
      else
        echo "MISSING $item"
      fi
    done
    echo
    echo "[Image archives]"
    if has_stage2_image_archive; then
      find "$DEPLOY_DIR/tar" "$DEPLOY_DIR/images" -maxdepth 1 -type f \( -name '*.tar' -o -name '*.tar.gz' -o -name '*.tgz' \) 2>/dev/null | head -n 20
    else
      echo "No image archive found under tar/ or images/"
    fi
    echo
    echo "[Containers]"
    if command -v docker >/dev/null 2>&1; then
      docker ps -a --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null || echo "docker ps failed"
    else
      echo "docker command not found"
    fi
  } > "$report_file"

  STAGE2_REPORT_FILE="$report_file"
  log_success "stage2 report generated: $report_file"
}
