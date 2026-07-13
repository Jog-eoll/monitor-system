#!/usr/bin/env bash

# ──────────────────────────────────────────────────────────
# account.sh  —  strong password generation & account.txt
# ──────────────────────────────────────────────────────────

generate_strong_password() {
  local length="${1:-16}"
  # Use /dev/urandom + tr to produce a random password containing
  # uppercase, lowercase, digits and special characters.
  local raw
  raw="$(tr -dc 'A-Za-z0-9!@#%^*_+-=' </dev/urandom | head -c "$((length * 4))")"

  # Guarantee at least one character from each class.
  local upper lower digit special
  upper="$(printf '%s' "$raw" | tr -dc 'A-Z' | head -c 1)"
  lower="$(printf '%s' "$raw" | tr -dc 'a-z' | head -c 1)"
  digit="$(printf '%s' "$raw" | tr -dc '0-9' | head -c 1)"
  special="$(printf '%s' "$raw" | tr -dc '!@#%^*_+-=' | head -c 1)"

  # Fill the rest randomly, then shuffle.
  local rest_len=$(( length - 4 ))
  if (( rest_len < 0 )); then rest_len=0; fi
  local rest
  rest="$(printf '%s' "$raw" | head -c "$rest_len")"

  # Concatenate and shuffle via fold + sort -R (coreutils).
  local combined="${upper}${lower}${digit}${special}${rest}"
  printf '%s' "$combined" | fold -w1 | sort -R | tr -d '\n'
}

auto_generate_passwords() {
  # Replace any password still carrying the Replace_ placeholder prefix
  # with a freshly generated strong password.
  local vars=(
    MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
    REDIS_PASSWORD
    MINIO_ROOT_PASSWORD MINIO_SECRET_KEY
    UKEY_ADMIN_PASSWORD
    EMQX_DASHBOARD_PASSWORD MQTT_DEVICE_DEFAULT_PASSWORD
  )
  local v
  for v in "${vars[@]}"; do
    local cur="${!v:-}"
    if [[ "$cur" == Replace_* ]]; then
      local new_pw
      new_pw="$(generate_strong_password 16)"
      printf -v "$v" '%s' "$new_pw"
      log_info "auto-generated strong password for $v"
    fi
  done
}

write_account_file() {
  local target_dir="${1:-$DEPLOY_DIR}"
  local account_file="$target_dir/account.txt"
  local server_ip
  server_ip="$(hostname -I 2>/dev/null | awk '{print $1}' || echo '127.0.0.1')"

  cat > "$account_file" <<EOF
═══════════════════════════════════════════════════════════
  微服务平台一键部署 — 账户信息
  Generated: $(date '+%Y-%m-%d %H:%M:%S')
═══════════════════════════════════════════════════════════

[访问地址]
  前端页面:    http://${server_ip}:${NGINX_PORT}
  Nginx HTTPS: https://${server_ip}:${NGINX_SSL_PORT}
  Nacos 控制台: http://${server_ip}:${NACOS_HOST_PORT}/nacos
  MinIO 控制台: http://${server_ip}:${MINIO_CONSOLE_PORT}
  EMQX 控制台:  http://${server_ip}:${EMQX_DASHBOARD_PORT}
  Sentinel:     http://${server_ip}:${SENTINEL_PORT}

[安装目录]
  部署目录:    $target_dir
  配置文件:    $target_dir/deploy.conf
  环境变量:    $target_dir/.env
  Compose 文件: $target_dir/docker-compose.yml

[数据库密码]
  MySQL root:          ${MYSQL_ROOT_PASSWORD}
  MySQL app (${MYSQL_USER}): ${MYSQL_PASSWORD}

[中间件密码]
  Redis:               ${REDIS_PASSWORD}
  MinIO root:          ${MINIO_ROOT_PASSWORD}
  MinIO app key:       ${MINIO_ACCESS_KEY}
  MinIO app secret:    ${MINIO_SECRET_KEY}
  EMQX dashboard:      ${EMQX_DASHBOARD_PASSWORD}

[管理账户]
  UKey 管理员:         ${UKEY_ADMIN_USERNAME} / ${UKEY_ADMIN_PASSWORD}
  MinIO 管理员:        ${MINIO_ROOT_USER} / ${MINIO_ROOT_PASSWORD}

[MQTT 配置]
  DISPATCH_MODE:       ${DISPATCH_MODE}
  Broker URL:          ${MQTT_BROKER_URL}
  Device password:     ${MQTT_DEVICE_DEFAULT_PASSWORD}
  EMQX MQTT:           ${server_ip}:${EMQX_MQTT_PORT}
  EMQX MQTTS:          ${server_ip}:${EMQX_MQTTS_PORT}

[常用命令]
  查看服务状态:  bash $target_dir/ctl.sh status
  查看日志:      bash $target_dir/ctl.sh logs <服务名>
  重启全部:      bash $target_dir/ctl.sh restart
  健康检查:      bash $target_dir/ctl.sh health
  部署报告:      bash $target_dir/ctl.sh report

═══════════════════════════════════════════════════════════
  ⚠ 请妥善保管此文件，其中包含所有敏感密码信息。
═══════════════════════════════════════════════════════════
EOF

  chmod 600 "$account_file"
  log_success "account file generated: $account_file"
}
