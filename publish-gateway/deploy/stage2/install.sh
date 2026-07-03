#!/usr/bin/env bash

set -euo pipefail

# ========== publish-gateway 一键部署脚本 ==========
# 适用于非融合架构的加密网关独立部署

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_FILE="${CONFIG_FILE:-$SCRIPT_DIR/deploy.conf}"
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
DRY_RUN=false
CHECK_ONLY=false
NON_INTERACTIVE=false
CURRENT_STAGE="initializing"
REPORT_WRITTEN=false

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ========== 日志函数 ==========
log_info() {
  echo -e "${CYAN}[INFO]${NC} $*"
}

log_success() {
  echo -e "${GREEN}[SUCCESS]${NC} $*"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $*"
}

fail() {
  log_error "$*"
  exit 1
}

# ========== 使用说明 ==========
usage() {
  cat <<'USAGE'
Usage:
  bash install.sh [--config FILE] [--check] [--dry-run] [--yes]

Options:
  --config FILE          使用指定的配置文件
  --check                仅运行预检查和生成 .env
  --dry-run              验证配置但不启动容器
  --yes                  非交互模式，端口冲突时直接失败
  --auto-install-docker  允许自动安装 Docker（不推荐）
  --no-auto-firewall     不自动开放防火墙端口
  -h, --help             显示帮助信息

USAGE
}

# ========== 参数解析 ==========
parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --config)
        [[ $# -ge 2 ]] || fail "--config 需要一个参数值"
        CONFIG_FILE="$2"
        shift
        ;;
      --check)
        CHECK_ONLY=true
        ;;
      --dry-run)
        DRY_RUN=true
        ;;
      --yes)
        NON_INTERACTIVE=true
        ;;
      --auto-install-docker)
        AUTO_INSTALL_DOCKER=true
        ;;
      --no-auto-firewall)
        AUTO_OPEN_FIREWALL=false
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        fail "未知选项: $1"
        ;;
    esac
    shift
  done
}

# ========== 加载配置文件 ==========
load_config() {
  if [[ ! -f "$CONFIG_FILE" ]]; then
    fail "配置文件不存在: $CONFIG_FILE
请执行: cp stage2/deploy.conf.example deploy.conf"
  fi

  log_info "加载配置文件: $CONFIG_FILE"
  # shellcheck disable=SC1090
  source "$CONFIG_FILE"

  # 设置默认值
  AUTO_OPEN_FIREWALL="${AUTO_OPEN_FIREWALL:-true}"
  AUTO_INSTALL_DOCKER="${AUTO_INSTALL_DOCKER:-false}"
  PULL_PUBLIC_IMAGES="${PULL_PUBLIC_IMAGES:-false}"
}

# ========== 自动生成密码 ==========
auto_generate_passwords() {
  log_info "检查并自动生成密码..."

  local passwords=(
    "DB_PASSWORD"
    "DB_ROOT_PASSWORD"
    "REDIS_PASSWORD"
    "MINIO_SECRET_KEY"
  )

  for var in "${passwords[@]}"; do
    local value="${!var:-}"
    if [[ -z "$value" ]] || [[ "$value" == Replace_* ]]; then
      local new_pass
      new_pass=$(openssl rand -base64 16 | tr -dc 'a-zA-Z0-9!@#$%^&*' | head -c 16)
      eval "$var='$new_pass'"
      log_info "  自动生成 $var"
    fi
  done
}

# ========== 校验配置 ==========
validate_config() {
  log_info "校验配置参数..."

  # 检查必需的配置项
  local required_vars=(
    "DB_HOST"
    "DB_PORT"
    "DB_USERNAME"
    "DB_PASSWORD"
    "MONITOR_HOST"
    "VAUTH_AUTH_ID"
    "VAUTH_SERVER_ID"
    "VAUTH_PASSWORD"
  )

  for var in "${required_vars[@]}"; do
    if [[ -z "${!var:-}" ]]; then
      fail "必需的配置项 $var 未设置"
    fi
  done

  # 校验 IP 地址格式
  local ip_vars=("DB_HOST" "REDIS_HOST" "MONITOR_HOST")
  for var in "${ip_vars[@]}"; do
    local ip="${!var:-}"
    if [[ -n "$ip" ]] && [[ "$ip" != "127.0.0.1" ]] && [[ "$ip" != "localhost" ]]; then
      if ! [[ "$ip" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
        log_warn "$var 的值 '$ip' 可能不是有效的 IP 地址"
      fi
    fi
  done

  log_success "配置校验通过"
}

# ========== 检查系统资源 ==========
check_os_resources() {
  log_info "检查系统资源..."

  # 检查 CPU
  local cpu_cores
  cpu_cores=$(nproc 2>/dev/null || echo "0")
  local min_cpu="${MIN_CPU_CORES:-2}"
  if (( cpu_cores < min_cpu )); then
    log_warn "CPU 核数不足: 当前 $cpu_cores, 最低要求 $min_cpu"
  else
    log_info "  CPU: $cpu_cores 核 (满足要求)"
  fi

  # 检查内存
  local total_mem_mb
  total_mem_mb=$(free -m | awk '/^Mem:/{print $2}')
  local min_mem="${MIN_MEMORY_MB:-4096}"
  if (( total_mem_mb < min_mem )); then
    log_warn "内存不足: 当前 ${total_mem_mb}MB, 最低要求 ${min_mem}MB"
  else
    log_info "  内存: ${total_mem_mb}MB (满足要求)"
  fi

  # 检查磁盘
  local disk_avail_gb
  disk_avail_gb=$(df -BG "$DEPLOY_DIR" | awk 'NR==2{print $4}' | tr -d 'G')
  local min_disk="${MIN_DISK_GB:-30}"
  if (( disk_avail_gb < min_disk )); then
    log_warn "磁盘空间不足: 当前 ${disk_avail_gb}GB, 最低要求 ${min_disk}GB"
  else
    log_info "  磁盘: ${disk_avail_gb}GB 可用 (满足要求)"
  fi

  log_success "系统资源检查完成"
}

# ========== 检查 Docker ==========
check_docker() {
  log_info "检查 Docker 环境..."

  # 检查 Docker 是否安装
  if ! command -v docker &>/dev/null; then
    if [[ "$AUTO_INSTALL_DOCKER" == "true" ]]; then
      log_info "Docker 未安装，尝试自动安装..."
      install_docker
    else
      fail "Docker 未安装，请先安装 Docker 20.10+"
    fi
  fi

  # 检查 Docker 版本
  local docker_version
  docker_version=$(docker version --format '{{.Server.Version}}' 2>/dev/null || echo "unknown")
  log_info "  Docker 版本: $docker_version"

  # 检查 Docker Compose
  if docker compose version &>/dev/null; then
    COMPOSE_CMD="docker compose"
    local compose_version
    compose_version=$(docker compose version --short 2>/dev/null || echo "unknown")
    log_info "  Docker Compose 版本: $compose_version (v2)"
  elif command -v docker-compose &>/dev/null; then
    COMPOSE_CMD="docker-compose"
    local compose_version
    compose_version=$(docker-compose version --short 2>/dev/null || echo "unknown")
    log_info "  Docker Compose 版本: $compose_version (v1)"
  else
    fail "Docker Compose 未安装"
  fi

  # 检查 Docker 是否运行
  if ! docker info &>/dev/null; then
    fail "Docker 未运行，请启动 Docker 服务"
  fi

  log_success "Docker 环境检查通过"
}

# ========== 安装 Docker ==========
install_docker() {
  log_info "正在安装 Docker..."

  if [[ -f /etc/os-release ]]; then
    # shellcheck disable=SC1091
    source /etc/os-release
    case "$ID" in
      ubuntu|debian)
        curl -fsSL https://get.docker.com | sh
        ;;
      centos|rhel|rocky|alma)
        yum install -y yum-utils
        yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
        yum install -y docker-ce docker-ce-cli containerd.io
        ;;
      *)
        fail "不支持的操作系统: $ID"
        ;;
    esac
  else
    fail "无法检测操作系统类型"
  fi

  systemctl enable docker
  systemctl start docker

  log_success "Docker 安装完成"
}

# ========== 检查端口冲突 ==========
check_port_conflicts() {
  log_info "检查端口冲突..."

  local ports=(
    "${GATEWAY_HTTP_PORT:-8092}"
    "${CLIENT_RELAY_PORT:-18092}"
  )

  # 如果使用本地 Redis
  if [[ "${REDIS_ENABLED:-true}" == "true" ]] && [[ "${REDIS_HOST:-127.0.0.1}" == "127.0.0.1" ]]; then
    ports+=("${REDIS_PORT:-6379}")
  fi

  local conflicts=0
  for port in "${ports[@]}"; do
    if ss -ltn | grep -q ":${port} "; then
      log_warn "端口 $port 已被占用"
      ((conflicts++))
    fi
  done

  if (( conflicts > 0 )); then
    if [[ "$NON_INTERACTIVE" == "true" ]]; then
      fail "存在 $conflicts 个端口冲突，请先释放端口或修改配置"
    else
      log_warn "存在 $conflicts 个端口冲突，请手动释放端口或修改配置"
      read -rp "是否继续部署？(y/N): " confirm
      if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
        exit 1
      fi
    fi
  else
    log_success "端口检查通过"
  fi
}

# ========== 开放防火墙端口 ==========
open_firewall_ports() {
  if [[ "$AUTO_OPEN_FIREWALL" != "true" ]]; then
    log_info "跳过防火墙配置（AUTO_OPEN_FIREWALL=false）"
    return
  fi

  log_info "配置防火墙端口..."

  local ports=(
    "${GATEWAY_HTTP_PORT:-8092}"
    "${CLIENT_RELAY_PORT:-18092}"
  )

  # 检测防火墙类型
  if command -v firewall-cmd &>/dev/null; then
    # firewalld
    for port in "${ports[@]}"; do
      firewall-cmd --permanent --add-port="${port}/tcp" 2>/dev/null || true
      firewall-cmd --permanent --add-port="${port}/udp" 2>/dev/null || true
    done
    firewall-cmd --reload 2>/dev/null || true
    log_success "防火墙端口已开放 (firewalld)"
  elif command -v ufw &>/dev/null; then
    # ufw
    for port in "${ports[@]}"; do
      ufw allow "${port}/tcp" 2>/dev/null || true
      ufw allow "${port}/udp" 2>/dev/null || true
    done
    log_success "防火墙端口已开放 (ufw)"
  elif command -v iptables &>/dev/null; then
    # iptables
    for port in "${ports[@]}"; do
      iptables -A INPUT -p tcp --dport "${port}" -j ACCEPT 2>/dev/null || true
      iptables -A INPUT -p udp --dport "${port}" -j ACCEPT 2>/dev/null || true
    done
    log_success "防火墙端口已开放 (iptables)"
  else
    log_warn "未检测到防火墙工具，请手动开放端口: ${ports[*]}"
  fi
}

# ========== 检查交付文件 ==========
check_delivery_files() {
  log_info "检查交付文件..."

  local required_files=(
    "$DEPLOY_DIR/docker-compose.yml"
    "$DEPLOY_DIR/application.yml"
    "$DEPLOY_DIR/lib/libvauthsdk.so"
  )

  local missing=0
  for file in "${required_files[@]}"; do
    if [[ ! -f "$file" ]]; then
      log_error "缺少文件: $file"
      ((missing++))
    fi
  done

  # 检查 Docker 镜像
  local image="gateway-udp-proxy:1.0.0"
  if ! docker image inspect "$image" &>/dev/null; then
    if [[ -f "$DEPLOY_DIR/images/publish-gateway.tar" ]]; then
      log_info "加载 Docker 镜像..."
      docker load -i "$DEPLOY_DIR/images/publish-gateway.tar"
    elif [[ "$PULL_PUBLIC_IMAGES" == "true" ]]; then
      log_warn "镜像 $image 不存在，将尝试从网络拉取"
    else
      log_error "缺少 Docker 镜像: $image"
      ((missing++))
    fi
  fi

  if (( missing > 0 )); then
    fail "缺少 $missing 个必需文件，请检查安装包完整性"
  fi

  log_success "交付文件检查通过"
}

# ========== 生成环境变量文件 ==========
write_env_file() {
  log_info "生成 .env 文件..."

  cat > "$ENV_FILE" <<EOF
# ========== publish-gateway 环境变量 ==========
# 自动生成于 $(date '+%Y-%m-%d %H:%M:%S')

# MySQL 配置
DB_HOST='${DB_HOST}'
DB_PORT='${DB_PORT}'
DB_NAME='${DB_NAME}'
DB_USERNAME='${DB_USERNAME}'
DB_PASSWORD='${DB_PASSWORD}'
DB_ROOT_PASSWORD='${DB_ROOT_PASSWORD}'

# Redis 配置
REDIS_HOST='${REDIS_HOST:-127.0.0.1}'
REDIS_PORT='${REDIS_PORT:-6379}'
REDIS_PASSWORD='${REDIS_PASSWORD}'
REDIS_DATABASE='${REDIS_DATABASE:-2}'

# MinIO 配置
MINIO_HOST='${MINIO_HOST:-127.0.0.1}'
MINIO_PORT='${MINIO_PORT:-9000}'
MINIO_ACCESS_KEY='${MINIO_ACCESS_KEY:-admin}'
MINIO_SECRET_KEY='${MINIO_SECRET_KEY}'
MINIO_BUCKET='${MINIO_BUCKET:-monitor-content}'

# 管控平台配置
MONITOR_HOST='${MONITOR_HOST}'
MONITOR_CONTENT_PORT='${MONITOR_CONTENT_PORT:-8080}'
MONITOR_UKEY_PORT='${MONITOR_UKEY_PORT:-8080}'
MONITOR_DEVICE_PORT='${MONITOR_DEVICE_PORT:-8080}'
MONITOR_LOG_URL='${MONITOR_LOG_URL}'

# VAuth 国密配置
VAUTH_MOCK_MODE='${VAUTH_MOCK_MODE:-false}'
VAUTH_DEVICE_TYPE='${VAUTH_DEVICE_TYPE:-ukey}'
VAUTH_PASSWORD='${VAUTH_PASSWORD}'
VAUTH_AUTH_ID='${VAUTH_AUTH_ID}'
VAUTH_SERVER_ID='${VAUTH_SERVER_ID}'
VAUTH_UKEY_PATH='${VAUTH_UKEY_PATH:-}'
VAUTH_UKEY_SN='${VAUTH_UKEY_SN:-}'
VAUTH_UKEY_CER_SN='${VAUTH_UKEY_CER_SN:-}'
VAUTH_UKEY_CER_ID='${VAUTH_UKEY_CER_ID:-}'
VAUTH_SVAC_MODE='${VAUTH_SVAC_MODE:-false}'

# JmDNS 配置
PROVIDER_JMDNS_ENABLED='${PROVIDER_JMDNS_ENABLED:-true}'
PROVIDER_JMDNS_BIND_ADDRESS='${PROVIDER_JMDNS_BIND_ADDRESS}'
PROVIDER_JMDNS_ADVERTISE_HOST='${PROVIDER_JMDNS_ADVERTISE_HOST}'

# 设备注册配置
REGISTRY_CLIENT_ID='${REGISTRY_CLIENT_ID}'
DEVICE_VERSION='${DEVICE_VERSION:-1.0.0}'
DEVICE_LOCATION='${DEVICE_LOCATION:-}'
DEVICE_MANUFACTURER='${DEVICE_MANUFACTURER:-}'
DEVICE_MODEL='${DEVICE_MODEL:-}'
DEVICE_REMARK='${DEVICE_REMARK:-publish encrypt gateway}'

# 客户端中继配置
CLIENT_RELAY_ENABLED='${CLIENT_RELAY_ENABLED:-true}'
CLIENT_RELAY_PORT='${CLIENT_RELAY_PORT:-18092}'
CLIENT_RELAY_TRUSTED_IPS='${CLIENT_RELAY_TRUSTED_IPS:-}'
CLIENT_RELAY_VERIFY_SIGNATURE='${CLIENT_RELAY_VERIFY_SIGNATURE:-false}'
CLIENT_RELAY_SIGNATURE_SECRET='${CLIENT_RELAY_SIGNATURE_SECRET:-}'
CLIENT_RELAY_REJECT_DIRECT_UDP='${CLIENT_RELAY_REJECT_DIRECT_UDP:-false}'

# 安全包验签配置
SECURE_PUBLISH_ENABLED='${SECURE_PUBLISH_ENABLED:-true}'
SECURE_PUBLISH_TRUST_STORE_DIR='${SECURE_PUBLISH_TRUST_STORE_DIR:-/opt/publish-gateway/trust}'
SECURE_PUBLISH_TRUSTED_KEY_IDS='${SECURE_PUBLISH_TRUSTED_KEY_IDS:-}'
SECURE_PUBLISH_DEFAULT_KEY_ID='${SECURE_PUBLISH_DEFAULT_KEY_ID:-default}'
SECURE_PUBLISH_VERIFIER_PUBLIC_KEY_PATH='${SECURE_PUBLISH_VERIFIER_PUBLIC_KEY_PATH:-}'

# MQTT Agent 配置
MQTT_AGENT_ENABLED=${MQTT_AGENT_ENABLED:-false}
MQTT_BROKER_URL=${MQTT_BROKER_URL:-ssl://127.0.0.1:8883}
MQTT_USERNAME=${MQTT_USERNAME:-publish-gateway-001}
MQTT_PASSWORD=${MQTT_PASSWORD:-}
MQTT_CLIENT_ID=${MQTT_CLIENT_ID:-publish-gateway-001}
MQTT_TENANT_ID=${MQTT_TENANT_ID:-default}
MQTT_SITE_ID=${MQTT_SITE_ID:-site-001}

# System network change configuration
SYSTEM_NETWORK_CHANGE_ENABLED=${SYSTEM_NETWORK_CHANGE_ENABLED:-false}
SYSTEM_NETWORK_CHANGE_ALLOWED_INTERFACES=${SYSTEM_NETWORK_CHANGE_ALLOWED_INTERFACES:-}
SYSTEM_NETWORK_CHANGE_ROLLBACK_DIR=${SYSTEM_NETWORK_CHANGE_ROLLBACK_DIR:-/tmp}
SYSTEM_NETWORK_CHANGE_COMMAND_TIMEOUT_MS=${SYSTEM_NETWORK_CHANGE_COMMAND_TIMEOUT_MS:-10000}
EOF

  chmod 600 "$ENV_FILE"
  log_success ".env 文件已生成"
}

# ========== 验证 Compose 文件 ==========
validate_compose() {
  log_info "验证 docker-compose.yml..."

  if [[ ! -f "$COMPOSE_FILE" ]]; then
    fail "docker-compose.yml 不存在: $COMPOSE_FILE"
  fi

  # 验证语法
  if ! $COMPOSE_CMD -f "$COMPOSE_FILE" config --quiet 2>/dev/null; then
    fail "docker-compose.yml 语法错误"
  fi

  log_success "docker-compose.yml 验证通过"
}

# ========== 准备运行目录 ==========
prepare_runtime_dirs() {
  log_info "准备运行目录..."

  local dirs=(
    "$DEPLOY_DIR/logs"
    "$DEPLOY_DIR/certs"
    "$DEPLOY_DIR/trust"
    "$DEPLOY_DIR/redis/data"
  )

  for dir in "${dirs[@]}"; do
    mkdir -p "$dir"
  done

  # 设置目录权限
  chmod 755 "$DEPLOY_DIR/logs"
  chmod 700 "$DEPLOY_DIR/certs"
  chmod 700 "$DEPLOY_DIR/trust"

  log_success "运行目录准备完成"
}

# ========== 启动服务 ==========
start_services() {
  log_info "启动服务..."

  # 启动 MySQL 数据库
  log_info "  启动 MySQL..."
  $COMPOSE_CMD -f "$COMPOSE_FILE" up -d publish-gateway-mysql
  sleep 10  # 等待 MySQL 初始化

  # 如果使用本地 Redis
  if [[ "${REDIS_ENABLED:-true}" == "true" ]] && [[ "${REDIS_HOST:-127.0.0.1}" == "127.0.0.1" ]]; then
    log_info "  启动 Redis..."
    $COMPOSE_CMD -f "$COMPOSE_FILE" up -d publish-gateway-redis
    sleep 5
  fi

  # 启动加密网关
  log_info "  启动加密网关..."
  $COMPOSE_CMD -f "$COMPOSE_FILE" up -d publish-gateway

  log_success "服务启动完成"
}

# ========== 健康检查 ==========
check_health() {
  log_info "执行健康检查..."

  # 检查 MySQL 容器状态
  log_info "  检查 MySQL..."
  local mysql_retries=0
  local mysql_max_retries=30
  while (( mysql_retries < mysql_max_retries )); do
    if docker inspect --format='{{.State.Status}}' publish-gateway-mysql 2>/dev/null | grep -q "running"; then
      log_success "  MySQL 容器运行正常"
      break
    fi
    ((mysql_retries++))
    log_info "  等待 MySQL 启动... ($mysql_retries/$mysql_max_retries)"
    sleep 5
  done

  # 检查 Redis 容器状态
  if [[ "${REDIS_ENABLED:-true}" == "true" ]] && [[ "${REDIS_HOST:-127.0.0.1}" == "127.0.0.1" ]]; then
    log_info "  检查 Redis..."
    if docker inspect --format='{{.State.Status}}' publish-gateway-redis 2>/dev/null | grep -q "running"; then
      log_success "  Redis 容器运行正常"
    else
      log_warn "  Redis 容器未运行"
    fi
  fi

  # 检查加密网关容器状态
  log_info "  检查加密网关..."
  local max_retries=30
  local retry_interval=5
  local retries=0

  while (( retries < max_retries )); do
    if docker inspect --format='{{.State.Status}}' gateway-udp-proxy 2>/dev/null | grep -q "running"; then
      # 容器运行中，检查应用是否就绪
      if curl -sf "http://127.0.0.1:${GATEWAY_HTTP_PORT:-8092}/actuator/health" &>/dev/null || \
         curl -sf "http://127.0.0.1:${GATEWAY_HTTP_PORT:-8092}/udp-proxy/rules/running" &>/dev/null; then
        log_success "加密网关健康检查通过"
        return 0
      fi
    fi

    ((retries++))
    log_info "  等待服务启动... ($retries/$max_retries)"
    sleep "$retry_interval"
  done

  log_error "健康检查超时，请查看日志: docker logs gateway-udp-proxy"
  return 1
}

# ========== 生成账户文件 ==========
write_account_file() {
  log_info "生成账户文件..."

  local account_file="$DEPLOY_DIR/account.txt"

  cat > "$account_file" <<EOF
================================================================
  publish-gateway 部署账户信息
  生成时间: $(date '+%Y-%m-%d %H:%M:%S')
================================================================

一、服务访问地址

  加密网关 HTTP:    http://$(hostname -I | awk '{print $1}'):${GATEWAY_HTTP_PORT:-8092}
  客户端中继端口:   $(hostname -I | awk '{print $1}'):${CLIENT_RELAY_PORT:-18092}

二、数据库信息

  MySQL 地址:       ${DB_HOST}:${DB_PORT}
  MySQL 数据库:     ${DB_NAME}
  MySQL 用户名:     ${DB_USERNAME}
  MySQL 密码:       ${DB_PASSWORD}

三、Redis 信息

  Redis 地址:       ${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6379}
  Redis 密码:       ${REDIS_PASSWORD}

四、MinIO 信息

  MinIO 地址:       http://${MINIO_HOST:-127.0.0.1}:${MINIO_PORT:-9000}
  MinIO Access Key: ${MINIO_ACCESS_KEY:-admin}
  MinIO Secret Key: ${MINIO_SECRET_KEY}
  MinIO Bucket:     ${MINIO_BUCKET:-monitor-content}

五、VAuth 国密配置

  认证 ID:          ${VAUTH_AUTH_ID}
  服务端 ID:        ${VAUTH_SERVER_ID}
  设备类型:         ${VAUTH_DEVICE_TYPE:-ukey}

六、常用命令

  查看状态:         docker ps | grep gateway-udp-proxy
  查看日志:         docker logs -f gateway-udp-proxy
  重启服务:         docker restart gateway-udp-proxy
  停止服务:         docker stop gateway-udp-proxy

================================================================
  请妥善保管此文件，切勿泄露密码信息！
================================================================
EOF

  chmod 600 "$account_file"
  log_success "账户文件已生成: $account_file"
}

# ========== 退出处理 ==========
on_exit() {
  local rc=$?
  if (( rc != 0 )) && [[ "$REPORT_WRITTEN" != "true" ]]; then
    log_error "部署失败（阶段: $CURRENT_STAGE）"
  fi
}

# ========== 主函数 ==========
main() {
  trap on_exit EXIT
  parse_args "$@"

  echo ""
  echo "================================================"
  echo "  publish-gateway 一键部署脚本"
  echo "================================================"
  echo ""

  log_info "部署目录: $DEPLOY_DIR"
  log_info "配置文件: $CONFIG_FILE"

  CURRENT_STAGE="加载配置"
  load_config

  CURRENT_STAGE="自动生成密码"
  auto_generate_passwords

  CURRENT_STAGE="校验配置"
  validate_config

  CURRENT_STAGE="检查系统资源"
  check_os_resources

  CURRENT_STAGE="检查 Docker"
  check_docker

  CURRENT_STAGE="检查端口冲突"
  check_port_conflicts

  CURRENT_STAGE="检查交付文件"
  check_delivery_files

  CURRENT_STAGE="生成环境变量"
  write_env_file

  CURRENT_STAGE="验证 Compose"
  validate_compose

  if [[ "$CHECK_ONLY" == "true" || "$DRY_RUN" == "true" ]]; then
    log_success "预检查完成"
    exit 0
  fi

  CURRENT_STAGE="配置防火墙"
  open_firewall_ports

  CURRENT_STAGE="准备运行目录"
  prepare_runtime_dirs

  CURRENT_STAGE="启动服务"
  start_services

  CURRENT_STAGE="健康检查"
  check_health

  CURRENT_STAGE="生成账户文件"
  write_account_file

  echo ""
  echo "================================================"
  log_success "publish-gateway 部署完成！"
  echo "================================================"
  echo ""
  log_info "请查看 account.txt 获取访问信息"
  log_info "查看日志: docker logs -f gateway-udp-proxy"
  echo ""
}

main "$@"
