#!/usr/bin/env bash

check_os_resources() {
  log_info "checking OS, CPU, memory and disk"
  [[ "$(uname -s)" == "Linux" ]] || fail "stage2 installer supports Linux only"

  local os_id os_version os_major
  os_id="$(. /etc/os-release 2>/dev/null && printf '%s' "${ID:-unknown}" || printf 'unknown')"
  os_version="$(. /etc/os-release 2>/dev/null && printf '%s' "${VERSION_ID:-unknown}" || printf 'unknown')"
  os_major="${os_version%%.*}"
  case "$os_id" in
    centos|rhel|rocky|almalinux|openEuler|anolis)
      [[ "$os_major" =~ ^[0-9]+$ ]] && (( os_major >= 7 )) || fail "unsupported Linux version: ${os_id} ${os_version}"
      ;;
    ubuntu)
      [[ "$os_major" =~ ^[0-9]+$ ]] && (( os_major >= 20 )) || fail "unsupported Linux version: ${os_id} ${os_version}"
      ;;
    debian)
      [[ "$os_major" =~ ^[0-9]+$ ]] && (( os_major >= 10 )) || fail "unsupported Linux version: ${os_id} ${os_version}"
      ;;
    *)
      fail "unsupported Linux distribution: ${os_id} ${os_version}"
      ;;
  esac

  local cpu memory_mb disk_gb
  cpu="$(nproc 2>/dev/null || echo 0)"
  memory_mb="$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo 2>/dev/null || echo 0)"
  disk_gb="$(df -Pm "$DEPLOY_DIR" | awk 'NR==2 {print int($4/1024)}')"

  (( cpu >= MIN_CPU_CORES )) || fail "CPU cores too low: ${cpu}, required: ${MIN_CPU_CORES}"
  (( memory_mb >= MIN_MEMORY_MB )) || fail "memory too low: ${memory_mb}MB, required: ${MIN_MEMORY_MB}MB"
  (( disk_gb >= MIN_DISK_GB )) || fail "disk free space too low: ${disk_gb}GB, required: ${MIN_DISK_GB}GB"

  log_success "resources ok: os=${os_id}-${os_version}, cpu=${cpu}, memory=${memory_mb}MB, disk_free=${disk_gb}GB"
}

install_docker_if_allowed() {
  as_bool "$AUTO_INSTALL_DOCKER" || fail "Docker is not installed. Set AUTO_INSTALL_DOCKER=true to allow automatic installation."
  [[ "$(id -u)" == "0" ]] || fail "automatic Docker installation requires root"

  log_warn "attempting Docker installation"
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update
    apt-get install -y docker.io docker-compose-plugin
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y docker docker-compose-plugin
  elif command -v yum >/dev/null 2>&1; then
    yum install -y docker docker-compose
  else
    fail "unsupported package manager for automatic Docker installation"
  fi

  systemctl enable --now docker >/dev/null 2>&1 || service docker start >/dev/null 2>&1 || true
}

check_docker_compose() {
  log_info "checking Docker and Docker Compose"
  if ! command -v docker >/dev/null 2>&1; then
    install_docker_if_allowed
  fi

  docker info >/dev/null 2>&1 || {
    systemctl start docker >/dev/null 2>&1 || service docker start >/dev/null 2>&1 || true
    docker info >/dev/null 2>&1 || fail "Docker daemon is not available"
  }

  if docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker-compose)
  else
    as_bool "$AUTO_INSTALL_DOCKER" && install_docker_if_allowed
    if docker compose version >/dev/null 2>&1; then
      DOCKER_COMPOSE=(docker compose)
    elif command -v docker-compose >/dev/null 2>&1; then
      DOCKER_COMPOSE=(docker-compose)
    else
      fail "Docker Compose is not installed"
    fi
  fi

  log_success "Docker: $(docker --version)"
  log_success "Compose: $("${DOCKER_COMPOSE[@]}" version 2>/dev/null | head -n 1)"
}

port_listening() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]${port}$"
    return $?
  fi
  if command -v netstat >/dev/null 2>&1; then
    netstat -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]${port}$"
    return $?
  fi
  return 1
}

port_owned_by_stage2_container() {
  local port="$1"
  if [[ "$port" == "${CONTENT_PORT:-8065}" ]] &&
     docker inspect -f '{{.HostConfig.NetworkMode}} {{.State.Running}}' monitor-content 2>/dev/null | grep -q '^host true$'; then
    return 0
  fi
  docker ps --format '{{.Names}}\t{{.Ports}}' 2>/dev/null |
    awk -v port="$port" '
      $1 ~ /^(mysql|redis|minio|nacos|emqx|monitor-nginx|monitor-gateway|monitor-device|monitor-ukey|monitor-alarm|monitor-content|monitor-rule|monitor-forward|monitor-role|monitor-registry-server|monitor-websocket|monitor-log)$/ &&
      $0 ~ ("0\\.0\\.0\\.0:" port "->|:::" port "->") { found=1 }
      END { exit(found ? 0 : 1) }
    '
}

resolve_single_port() {
  local var_name="$1"
  local label="$2"
  local value="${!var_name}"

  while port_listening "$value"; do
    if port_owned_by_stage2_container "$value"; then
      log_info "port ${value} for ${label} is already owned by this stage2 stack"
      break
    fi

    if [[ "$NON_INTERACTIVE" == "true" ]]; then
      fail "port ${value} for ${label} is already in use"
    fi

    log_warn "port ${value} for ${label} is already in use"
    read -r -p "Enter a new port for ${label}: " value
    is_port "$value" || {
      log_warn "invalid port: $value"
      continue
    }
  done

  printf -v "$var_name" '%s' "$value"
}

resolve_port_conflicts() {
  log_info "checking host port conflicts"
  resolve_single_port MYSQL_HOST_PORT "MySQL host port"
  resolve_single_port REDIS_HOST_PORT "Redis host port"
  resolve_single_port NACOS_HOST_PORT "Nacos HTTP port"
  resolve_single_port NACOS_GRPC_PORT "Nacos gRPC port"
  resolve_single_port NACOS_RAFT_PORT "Nacos raft port"
  resolve_single_port MINIO_API_PORT "MinIO API port"
  resolve_single_port MINIO_CONSOLE_PORT "MinIO console port"
  resolve_single_port GATEWAY_PORT "Gateway port"
  resolve_single_port DEVICE_PORT "Device service port"
  resolve_single_port UKEY_PORT "UKey service port"
  resolve_single_port ALARM_PORT "Alarm service port"
  resolve_single_port CONTENT_PORT "Content service port"
  resolve_single_port RULE_PORT "Rule service port"
  resolve_single_port FORWARD_PORT "Forward service port"
  resolve_single_port ROLE_PORT "Role service port"
  resolve_single_port REGISTRY_PORT "Registry service port"
  resolve_single_port WEBSOCKET_PORT "WebSocket service port"
  resolve_single_port LOG_PORT "Log service port"
  resolve_single_port NGINX_PORT "Nginx HTTP port"
  resolve_single_port NGINX_SSL_PORT "Nginx HTTPS port"
  resolve_single_port EMQX_MQTT_PORT "EMQX MQTT port"
  resolve_single_port EMQX_MQTTS_PORT "EMQX MQTTS port"
  resolve_single_port EMQX_DASHBOARD_PORT "EMQX Dashboard port"
  sync_stage2_derived_config
}

open_firewalld_ports() {
  local port changed=false
  for port in $(stage2_host_ports); do
    if firewall-cmd --query-port="${port}/tcp" >/dev/null 2>&1; then
      continue
    fi
    if [[ "$DRY_RUN" == "true" || "$CHECK_ONLY" == "true" ]]; then
      log_warn "dry-run/check-only: firewall port would be opened: ${port}/tcp"
      continue
    fi
    firewall-cmd --permanent --add-port="${port}/tcp" >/dev/null
    changed=true
    log_info "opened firewalld port: ${port}/tcp"
  done
  [[ "$changed" == "true" ]] && firewall-cmd --reload >/dev/null
}

open_ufw_ports() {
  local port
  for port in $(stage2_host_ports); do
    if [[ "$DRY_RUN" == "true" || "$CHECK_ONLY" == "true" ]]; then
      log_warn "dry-run/check-only: ufw port would be allowed: ${port}/tcp"
      continue
    fi
    ufw allow "${port}/tcp" >/dev/null
    log_info "allowed ufw port: ${port}/tcp"
  done
}

open_iptables_ports() {
  local port changed=false
  for port in $(stage2_host_ports); do
    if iptables -C INPUT -p tcp --dport "$port" -j ACCEPT >/dev/null 2>&1; then
      continue
    fi
    if [[ "$DRY_RUN" == "true" || "$CHECK_ONLY" == "true" ]]; then
      log_warn "dry-run/check-only: iptables port would be allowed: ${port}/tcp"
      continue
    fi
    iptables -I INPUT -p tcp --dport "$port" -j ACCEPT
    changed=true
    log_info "allowed iptables port: ${port}/tcp"
  done

  if [[ "$changed" == "true" ]]; then
    if command -v service >/dev/null 2>&1 && service iptables save >/dev/null 2>&1; then
      log_info "saved iptables rules through service iptables save"
    elif command -v iptables-save >/dev/null 2>&1 && [[ -d /etc/sysconfig ]]; then
      iptables-save > /etc/sysconfig/iptables
      log_info "saved iptables rules to /etc/sysconfig/iptables"
    else
      log_warn "iptables rules were changed but persistence method was not found"
    fi
  fi
}

check_firewall_ports() {
  as_bool "$AUTO_OPEN_FIREWALL" || {
    log_warn "firewall auto-open disabled"
    return
  }

  if [[ "$DRY_RUN" == "true" || "$CHECK_ONLY" == "true" ]]; then
    log_info "checking firewall ports without applying changes"
  elif [[ "$(id -u)" != "0" ]]; then
    fail "firewall auto-open requires root; run as root or set AUTO_OPEN_FIREWALL=false"
  fi

  if command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active firewalld >/dev/null 2>&1; then
    open_firewalld_ports
    return
  fi

  if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -qi "Status: active"; then
    open_ufw_ports
    return
  fi

  if command -v iptables >/dev/null 2>&1 && iptables -L INPUT >/dev/null 2>&1; then
    open_iptables_ports
    return
  fi

  log_warn "no active firewalld/ufw detected; skip firewall opening"
}
