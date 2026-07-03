#!/usr/bin/env bash

set -Eeuo pipefail

VERSION="1.0.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_DIR="$SCRIPT_DIR/config"
CONFIG_FILE="$CONFIG_DIR/deploy.conf"
REPORT_DIR="$SCRIPT_DIR/report"
LOG_DIR="$SCRIPT_DIR/logs"
RUN_LOG="$LOG_DIR/install.log"
LAST_REPORT_FILE="$REPORT_DIR/latest-report.path"

DOCKER_COMPOSE=()
DEPLOY_RESULT="未执行"
FAIL_STAGE=""
FAIL_SERVICE=""
FAIL_MESSAGE=""
REPORT_FILE=""
GATEWAY_CERT_CONTENT=""
VALIDATION_ERRORS=()
VALIDATION_WARNINGS=()

PLATFORM_DEPLOY_DIR=""
GATEWAY_DEPLOY_DIR=""

mkdir -p "$CONFIG_DIR" "$REPORT_DIR" "$LOG_DIR"

log_file() {
  printf '[%s] %s\n' "$(date '+%F %T')" "$*" >> "$RUN_LOG"
}

print_line() {
  printf '%s\n' "$*"
  log_file "$*"
}

banner() {
  cat <<'EOF'
========================================
网关平台一体化一键部署工具
========================================
EOF
}

section() {
  print_line ""
  print_line "========================================"
  print_line "$1"
  print_line "========================================"
}

pass() {
  print_line "[通过] $*"
}

warn() {
  print_line "[提醒] $*"
}

fail_line() {
  print_line "[失败] $*"
}

validation_reset() {
  VALIDATION_ERRORS=()
  VALIDATION_WARNINGS=()
}

validation_error() {
  VALIDATION_ERRORS+=("$*")
  fail_line "$*"
}

validation_warn() {
  VALIDATION_WARNINGS+=("$*")
  warn "$*"
}

validation_has_errors() {
  (( ${#VALIDATION_ERRORS[@]} > 0 ))
}

validation_summary() {
  local i
  print_line ""
  print_line "========================================"
  print_line "配置校验汇总"
  print_line "========================================"
  if (( ${#VALIDATION_WARNINGS[@]} > 0 )); then
    print_line "提醒项：${#VALIDATION_WARNINGS[@]}"
    for ((i = 0; i < ${#VALIDATION_WARNINGS[@]}; i++)); do
      print_line "  - ${VALIDATION_WARNINGS[$i]}"
    done
  else
    print_line "提醒项：0"
  fi
  if validation_has_errors; then
    print_line "错误项：${#VALIDATION_ERRORS[@]}"
    for ((i = 0; i < ${#VALIDATION_ERRORS[@]}; i++)); do
      print_line "  - ${VALIDATION_ERRORS[$i]}"
    done
    print_line ""
    print_line "请修改 config/deploy.conf 后重新执行：sudo ./install.sh --validate"
    return 1
  fi
  print_line "错误项：0"
  pass "配置校验通过，可以执行：sudo ./install.sh --deploy"
}

die() {
  FAIL_STAGE="${1:-unknown}"
  FAIL_SERVICE="${2:-}"
  FAIL_MESSAGE="${3:-部署失败}"
  DEPLOY_RESULT="失败"
  fail_line "$FAIL_MESSAGE"
  print_troubleshooting "$FAIL_STAGE" "$FAIL_SERVICE" "$FAIL_MESSAGE"
  generate_report "失败" >/dev/null 2>&1 || true
  exit 1
}

mask_value() {
  local value="${1:-}"
  [[ -n "$value" ]] || {
    printf ''
    return
  }
  printf '******'
}

default_value() {
  local value="${1:-}"
  local default="${2:-}"
  if [[ -n "$value" ]]; then
    printf '%s' "$value"
  else
    printf '%s' "$default"
  fi
}

read_text() {
  local var_name="$1"
  local prompt="$2"
  local default="${3:-}"
  local value

  if [[ -n "$default" ]]; then
    read -r -p "$prompt [$default]: " value
    value="${value:-$default}"
  else
    read -r -p "$prompt: " value
  fi

  printf -v "$var_name" '%s' "$value"
}

read_secret() {
  local var_name="$1"
  local prompt="$2"
  local value
  read -r -s -p "$prompt: " value
  printf '\n'
  printf -v "$var_name" '%s' "$value"
}

read_yes_no() {
  local prompt="$1"
  local default_yes="${2:-true}"
  local reply
  if [[ "$default_yes" == "true" ]]; then
    read -r -p "$prompt [Y/n]: " reply
    [[ -z "$reply" || "$reply" =~ ^[Yy]$ ]]
  else
    read -r -p "$prompt [y/N]: " reply
    [[ "$reply" =~ ^[Yy]$ ]]
  fi
}

is_port() {
  local value="${1:-}"
  [[ "$value" =~ ^[0-9]+$ ]] && (( value >= 1 && value <= 65535 ))
}

require_port_value() {
  local name="$1"
  local value="${!name:-}"
  is_port "$value" || die "config" "" "配置项 $name 不是合法端口：$value"
}

shell_escape_single() {
  printf "%s" "$1" | sed "s/'/'\\\\''/g"
}

sql_escape_single() {
  printf "%s" "$1" | sed "s/'/''/g"
}

write_kv() {
  local file="$1"
  local key="$2"
  local value="${3:-}"
  printf "%s='%s'\n" "$key" "$(shell_escape_single "$value")" >> "$file"
}

json_escape() {
  local value="${1:-}"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\r'/}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\t'/\\t}"
  printf '%s' "$value"
}

result_ok() {
  local body="${1:-}"
  printf '%s' "$body" | grep -Eq '"code"[[:space:]]*:[[:space:]]*"?200"?'
}

result_message() {
  local body="${1:-}"
  local message
  message="$(printf '%s' "$body" | sed -nE 's/.*"(msg|message)"[[:space:]]*:[[:space:]]*"([^"]*)".*/\2/p' | head -n 1)"
  printf '%s' "${message:-接口返回异常}"
}

clean_config_value() {
  local value="${1-}"
  local python_bin

  python_bin="$(find_python_bin 2>/dev/null || true)"
  if [[ -n "$python_bin" ]]; then
    "$python_bin" - "$value" <<'PY'
from __future__ import print_function
import sys

try:
    unicode
except NameError:
    unicode = str

value = sys.argv[1] if len(sys.argv) > 1 else ""
if not isinstance(value, unicode):
    value = value.decode("utf-8", "ignore")

for ch in (u"\ufeff", u"\u200b", u"\u200c", u"\u200d", u"\u2060"):
    value = value.replace(ch, u"")
value = value.replace(u"\u00a0", u" ")
value = value.replace(u"\r", u"")
value = value.strip()

if sys.version_info[0] < 3:
    sys.stdout.write(value.encode("utf-8"))
else:
    sys.stdout.write(value)
PY
    return 0
  fi

  value="${value//$'\r'/}"
  value="${value//$'\ufeff'/}"
  value="${value//$'\u200b'/}"
  value="${value//$'\u200c'/}"
  value="${value//$'\u200d'/}"
  value="${value//$'\u2060'/}"
  value="${value//$'\u00a0'/ }"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

normalize_loaded_config() {
  local config_names=(
    PLATFORM_DIR GATEWAY_DIR PLATFORM_SERVICE GATEWAY_SERVICE PLATFORM_IMAGE GATEWAY_IMAGE
    SERVER_IP PLATFORM_HTTP_PORT GATEWAY_HTTP_PORT CLIENT_RELAY_PORT MDNS_PORT
    MYSQL_HOST MYSQL_PORT MYSQL_USERNAME MYSQL_PASSWORD PLATFORM_DB_NAME GATEWAY_DB_NAME
    REDIS_HOST REDIS_PORT REDIS_PASSWORD PLATFORM_REDIS_DB GATEWAY_REDIS_DB
    MINIO_HOST MINIO_PORT MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_BUCKET
    MQTT_AGENT_ENABLED MQTT_BROKER_URL MQTT_USERNAME MQTT_PASSWORD MQTT_CLIENT_ID MQTT_TENANT_ID MQTT_SITE_ID
    MQTT_RECONNECT_INTERVAL_MS MQTT_COMMAND_DEDUP_TTL_MS MQTT_DEDUP_CLEANUP_INTERVAL_MS
    UKEY_ADMIN_USERNAME UKEY_ADMIN_PASSWORD JWT_SECRET JWT_EXPIRE_HOURS JAVA_OPTS UKEY_SETUP_MODE
    PLATFORM_UKEY_PIN PLATFORM_AUTH_ID PLATFORM_UKEY_PATH PLATFORM_UKEY_SN PLATFORM_UKEY_CER_SN PLATFORM_UKEY_CER_ID
    GATEWAY_UKEY_PIN GATEWAY_AUTH_ID GATEWAY_UKEY_PATH GATEWAY_UKEY_SN GATEWAY_UKEY_CER_SN GATEWAY_UKEY_CER_ID
    GATEWAY_CERT_SERIAL_NO DEVICE_VERSION DEVICE_LOCATION DEVICE_MANUFACTURER DEVICE_MODEL DEVICE_REMARK
    SECURE_PUBLISH_ENABLED SECURE_PUBLISH_TRUSTED_KEY_IDS SECURE_PUBLISH_DEFAULT_KEY_ID SECURE_PUBLISH_VERIFIER_PUBLIC_KEY_PATH
    HEALTH_RETRIES HEALTH_INTERVAL_SECONDS LOG_VERIFY_RETRIES LOG_VERIFY_INTERVAL_SECONDS UKEY_LIST_TIMEOUT_SECONDS
  )
  local name original cleaned

  CONFIG_VALUES_NORMALIZED="${CONFIG_VALUES_NORMALIZED:-false}"
  for name in "${config_names[@]}"; do
    if [[ "${!name+x}" == "x" ]]; then
      original="${!name}"
      cleaned="$(clean_config_value "$original")"
      if [[ "$cleaned" != "$original" ]]; then
        CONFIG_VALUES_NORMALIZED="true"
      fi
      printf -v "$name" '%s' "$cleaned"
    fi
  done
}

source_config_file() {
  CONFIG_VALUES_NORMALIZED="false"
  local source_file="$CONFIG_FILE"
  local tmp_config="${CONFIG_FILE}.source.$$"

  if sed $'1s/^\357\273\277//; s/\r$//' "$CONFIG_FILE" > "$tmp_config"; then
    source_file="$tmp_config"
    if LC_ALL=C grep -q $'\r' "$CONFIG_FILE"; then
      CONFIG_VALUES_NORMALIZED="true"
    fi
  else
    rm -f "$tmp_config"
    return 1
  fi

  # shellcheck disable=SC1090
  source "$source_file"
  local source_rc=$?
  rm -f "$tmp_config"
  [[ "$source_rc" -eq 0 ]] || return "$source_rc"
  normalize_loaded_config
  apply_defaults
}

load_config() {
  [[ -f "$CONFIG_FILE" ]] || die "config" "" "未找到配置文件，请先执行全新部署生成配置"
  source_config_file || die "config" "" "配置文件语法错误：$CONFIG_FILE。请检查等号两侧空格、引号、特殊字符"
}

apply_defaults() {
  PLATFORM_DIR="${PLATFORM_DIR:-/opt/monitor-platform-monolith}"
  GATEWAY_DIR="${GATEWAY_DIR:-/opt/publish-gateway}"
  PLATFORM_SERVICE="${PLATFORM_SERVICE:-monitor-platform-monolith}"
  GATEWAY_SERVICE="${GATEWAY_SERVICE:-gateway-udp-proxy}"
  PLATFORM_IMAGE="${PLATFORM_IMAGE:-monitor-platform-monolith:1.0.0}"
  GATEWAY_IMAGE="${GATEWAY_IMAGE:-gateway-udp-proxy:1.0.0}"

  SERVER_IP="${SERVER_IP:-127.0.0.1}"
  PLATFORM_HTTP_PORT="${PLATFORM_HTTP_PORT:-8080}"
  GATEWAY_HTTP_PORT="${GATEWAY_HTTP_PORT:-8092}"
  CLIENT_RELAY_PORT="${CLIENT_RELAY_PORT:-18092}"
  MDNS_PORT="${MDNS_PORT:-5353}"

  MYSQL_PORT="${MYSQL_PORT:-3306}"
  PLATFORM_DB_NAME="${PLATFORM_DB_NAME:-monitor_platform}"
  GATEWAY_DB_NAME="${GATEWAY_DB_NAME:-udp_proxy_gateway}"
  REDIS_PORT="${REDIS_PORT:-6379}"
  PLATFORM_REDIS_DB="${PLATFORM_REDIS_DB:-0}"
  GATEWAY_REDIS_DB="${GATEWAY_REDIS_DB:-2}"
  MINIO_PORT="${MINIO_PORT:-9000}"
  MINIO_BUCKET="${MINIO_BUCKET:-monitor-content}"

  MQTT_AGENT_ENABLED="${MQTT_AGENT_ENABLED:-false}"
  MQTT_BROKER_URL="${MQTT_BROKER_URL:-ssl://127.0.0.1:8883}"
  MQTT_USERNAME="${MQTT_USERNAME:-}"
  MQTT_PASSWORD="${MQTT_PASSWORD:-}"
  MQTT_CLIENT_ID="${MQTT_CLIENT_ID:-publish-gateway-001}"
  MQTT_TENANT_ID="${MQTT_TENANT_ID:-default}"
  MQTT_SITE_ID="${MQTT_SITE_ID:-site-001}"
  MQTT_RECONNECT_INTERVAL_MS="${MQTT_RECONNECT_INTERVAL_MS:-30000}"
  MQTT_COMMAND_DEDUP_TTL_MS="${MQTT_COMMAND_DEDUP_TTL_MS:-86400000}"
  MQTT_DEDUP_CLEANUP_INTERVAL_MS="${MQTT_DEDUP_CLEANUP_INTERVAL_MS:-600000}"

  UKEY_ADMIN_USERNAME="${UKEY_ADMIN_USERNAME:-monitor_ukey}"
  if [[ -z "${JWT_SECRET:-}" ]]; then
    JWT_SECRET="$(generate_jwt_secret)"
    JWT_SECRET_AUTO_GENERATED="true"
  else
    JWT_SECRET_AUTO_GENERATED="false"
  fi
  JWT_EXPIRE_HOURS="${JWT_EXPIRE_HOURS:-8}"
  JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m}"
  UKEY_SETUP_MODE="${UKEY_SETUP_MODE:-required}"

  PLATFORM_UKEY_PIN="${PLATFORM_UKEY_PIN:-}"
  PLATFORM_AUTH_ID="${PLATFORM_AUTH_ID:-}"
  PLATFORM_UKEY_PATH="${PLATFORM_UKEY_PATH:-}"
  PLATFORM_UKEY_SN="${PLATFORM_UKEY_SN:-}"
  PLATFORM_UKEY_CER_SN="${PLATFORM_UKEY_CER_SN:-}"
  PLATFORM_UKEY_CER_ID="${PLATFORM_UKEY_CER_ID:-}"
  GATEWAY_UKEY_PIN="${GATEWAY_UKEY_PIN:-}"
  GATEWAY_AUTH_ID="${GATEWAY_AUTH_ID:-}"
  GATEWAY_UKEY_PATH="${GATEWAY_UKEY_PATH:-}"
  GATEWAY_UKEY_SN="${GATEWAY_UKEY_SN:-}"
  GATEWAY_UKEY_CER_SN="${GATEWAY_UKEY_CER_SN:-}"
  GATEWAY_UKEY_CER_ID="${GATEWAY_UKEY_CER_ID:-}"

  DEVICE_VERSION="${DEVICE_VERSION:-1.0.0}"
  GATEWAY_CERT_SERIAL_NO="${GATEWAY_CERT_SERIAL_NO:-${GATEWAY_UKEY_CER_ID:-}}"
  SECURE_PUBLISH_ENABLED="${SECURE_PUBLISH_ENABLED:-true}"
  SECURE_PUBLISH_DEFAULT_KEY_ID="${SECURE_PUBLISH_DEFAULT_KEY_ID:-default}"

  HEALTH_RETRIES="${HEALTH_RETRIES:-60}"
  HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-5}"
  LOG_VERIFY_RETRIES="${LOG_VERIFY_RETRIES:-36}"
  LOG_VERIFY_INTERVAL_SECONDS="${LOG_VERIFY_INTERVAL_SECONDS:-5}"

  PLATFORM_DEPLOY_DIR="$PLATFORM_DIR"
  GATEWAY_DEPLOY_DIR="$GATEWAY_DIR"
}

ukey_setup_deferred() {
  [[ "${UKEY_SETUP_MODE:-required}" == "deferred" ]]
}

ukey_setup_required() {
  ! ukey_setup_deferred
}

validate_ukey_setup_mode() {
  case "${UKEY_SETUP_MODE:-required}" in
    required|deferred)
      ;;
    *)
      die "config" "" "配置项 UKEY_SETUP_MODE 只能为 required 或 deferred，当前值：${UKEY_SETUP_MODE}"
      ;;
  esac
}

clear_ukey_config() {
  PLATFORM_UKEY_PIN=""
  PLATFORM_AUTH_ID=""
  PLATFORM_UKEY_PATH=""
  PLATFORM_UKEY_SN=""
  PLATFORM_UKEY_CER_SN=""
  PLATFORM_UKEY_CER_ID=""
  GATEWAY_UKEY_PIN=""
  GATEWAY_AUTH_ID=""
  GATEWAY_UKEY_PATH=""
  GATEWAY_UKEY_SN=""
  GATEWAY_UKEY_CER_SN=""
  GATEWAY_UKEY_CER_ID=""
  GATEWAY_CERT_SERIAL_NO=""
}

validate_ukey_config() {
  local required=(
    PLATFORM_UKEY_PIN PLATFORM_AUTH_ID
    GATEWAY_UKEY_PIN GATEWAY_AUTH_ID
  )
  local name
  for name in "${required[@]}"; do
    [[ -n "${!name:-}" ]] || die "config" "" "UKey 正式模式缺少 $name；若现场暂缺 UKey，请使用 UKEY_SETUP_MODE=deferred"
  done
}

validate_required_config() {
  validate_ukey_setup_mode
  local required=(
    SERVER_IP PLATFORM_HTTP_PORT GATEWAY_HTTP_PORT
    MYSQL_HOST MYSQL_PORT MYSQL_USERNAME MYSQL_PASSWORD PLATFORM_DB_NAME GATEWAY_DB_NAME
    REDIS_HOST REDIS_PORT
    MINIO_HOST MINIO_PORT MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_BUCKET
    UKEY_ADMIN_USERNAME UKEY_ADMIN_PASSWORD
  )
  local name
  for name in "${required[@]}"; do
    [[ -n "${!name:-}" ]] || die "config" "" "配置项 $name 不能为空"
  done

  if ukey_setup_required; then
    validate_ukey_config
  fi

  require_port_value PLATFORM_HTTP_PORT
  require_port_value GATEWAY_HTTP_PORT
  require_port_value CLIENT_RELAY_PORT
  require_port_value MDNS_PORT
  require_port_value MYSQL_PORT
  require_port_value REDIS_PORT
  require_port_value MINIO_PORT
  [[ "$MQTT_AGENT_ENABLED" == "true" || "$MQTT_AGENT_ENABLED" == "false" ]] || die "config" "" "配置项 MQTT_AGENT_ENABLED 只能为 true 或 false：$MQTT_AGENT_ENABLED"
  [[ "$MQTT_RECONNECT_INTERVAL_MS" =~ ^[0-9]+$ ]] || die "config" "" "配置项 MQTT_RECONNECT_INTERVAL_MS 不是合法数字：$MQTT_RECONNECT_INTERVAL_MS"
  [[ "$MQTT_COMMAND_DEDUP_TTL_MS" =~ ^[0-9]+$ ]] || die "config" "" "配置项 MQTT_COMMAND_DEDUP_TTL_MS 不是合法数字：$MQTT_COMMAND_DEDUP_TTL_MS"
  [[ "$MQTT_DEDUP_CLEANUP_INTERVAL_MS" =~ ^[0-9]+$ ]] || die "config" "" "配置项 MQTT_DEDUP_CLEANUP_INTERVAL_MS 不是合法数字：$MQTT_DEDUP_CLEANUP_INTERVAL_MS"
  if [[ "$MQTT_AGENT_ENABLED" == "true" && -z "${MQTT_BROKER_URL:-}" ]]; then
    die "config" "" "MQTT_AGENT_ENABLED=true 时必须填写 MQTT_BROKER_URL"
  fi
}

detect_compose() {
  if docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker-compose)
  else
    die "environment" "" "未检测到 Docker Compose"
  fi
}

compose_cmd() {
  local dir="$1"
  shift
  (cd "$dir" && "${DOCKER_COMPOSE[@]}" "$@")
}

require_root() {
  if [[ "$(id -u)" != "0" ]]; then
    die "environment" "" "请使用 root 权限执行：sudo ./install.sh"
  fi
  pass "当前用户权限：root"
}

check_docker() {
  command -v docker >/dev/null 2>&1 || die "environment" "" "未检测到 Docker"
  docker info >/dev/null 2>&1 || die "environment" "" "Docker 服务未启动或当前用户无权访问 Docker"
  pass "Docker 已安装：$(docker --version)"
  detect_compose
  pass "Docker Compose 已安装：$("${DOCKER_COMPOSE[@]}" version 2>/dev/null | head -n 1)"
}

port_in_use() {
  local port="$1"
  local proto="${2:-tcp}"
  if command -v ss >/dev/null 2>&1; then
    if [[ "$proto" == "udp" ]]; then
      ss -lun 2>/dev/null | awk '{print $5}' | grep -Eq "[:.]${port}$"
    else
      ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]${port}$"
    fi
    return $?
  fi
  if command -v netstat >/dev/null 2>&1; then
    if [[ "$proto" == "udp" ]]; then
      netstat -lun 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]${port}$"
    else
      netstat -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]${port}$"
    fi
    return $?
  fi
  return 1
}

container_exists() {
  docker ps -a --format '{{.Names}}' | grep -Fxq "$1"
}

remove_compose_service_residue() {
  local project="$1"
  local service="$2"
  local removed=0
  local line cid name status labels

  while IFS=$'\t' read -r cid name status labels; do
    [[ -n "$cid" ]] || continue
    [[ "$name" == "$service" ]] && continue
    [[ "$labels" == *"com.docker.compose.project=${project}"* ]] || continue
    [[ "$labels" == *"com.docker.compose.service=${service}"* ]] || continue
    case "$status" in
      created|exited|dead)
        warn "Removing stale compose container residue: $name"
        docker rm -f "$cid" >> "$RUN_LOG" 2>&1 || true
        removed=1
        ;;
    esac
  done < <(docker ps -a --format '{{.ID}}\t{{.Names}}\t{{.State}}\t{{.Labels}}')

  return "$removed"
}

check_port_available_for_service() {
  local port="$1"
  local label="$2"
  local proto="${3:-tcp}"
  local service="$4"
  if port_in_use "$port" "$proto"; then
    if container_exists "$service"; then
      warn "$label 端口 $port 已被现有容器占用，重新部署会接管该容器"
    else
      die "environment" "$service" "$label 端口 $port 已被占用"
    fi
  else
    pass "$label 端口 $port 未被占用"
  fi
}

check_usb() {
  [[ -d /dev/bus/usb ]] || die "environment" "" "USB 设备目录不存在：/dev/bus/usb"
  pass "USB 设备目录存在：/dev/bus/usb"
}

ensure_tool_executable() {
  local tool="$SCRIPT_DIR/tools/ukey-list"
  [[ -f "$tool" ]] || die "package" "" "部署包缺少 UKey 枚举工具：tools/ukey-list"
  if [[ ! -x "$tool" ]]; then
    chmod +x "$tool" 2>/dev/null || die "package" "" "UKey 枚举工具不可执行，请执行：chmod +x tools/ukey-list"
  fi
}

check_host_tools() {
  local cmd
  for cmd in curl awk sed grep find tr; do
    command -v "$cmd" >/dev/null 2>&1 || die "environment" "" "服务器缺少基础命令：$cmd"
  done
  if ! command -v python3 >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1 && ! command -v python2 >/dev/null 2>&1; then
    die "environment" "" "服务器缺少基础命令：python3/python/python2"
  fi
  pass "基础命令可用：curl/awk/sed/grep/find/tr/python"
}

has_libvauthsdk() {
  local dir="$1"
  [[ -f "$dir/libvauthsdk.so" ]]
}

has_cert_files() {
  local dir="$1"
  compgen -G "$dir/*.cer" >/dev/null 2>&1
}

autofill_package_assets() {
  local src

  if ! has_libvauthsdk "$SCRIPT_DIR/publish-gateway/lib"; then
    for src in \
      "$SCRIPT_DIR/monitor-platform/lib" \
      /opt/publish-gateway/gateway-udp-proxy/lib \
      /opt/publish-gateway/lib \
      /opt/monitor-platform-monolith/lib; do
      if has_libvauthsdk "$src"; then
        copy_dir_contents "$src" "$SCRIPT_DIR/publish-gateway/lib"
        pass "已从现有网关包补齐加密网关 SDK：$src"
        break
      fi
    done
  fi

  if ! has_libvauthsdk "$SCRIPT_DIR/monitor-platform/lib"; then
    for src in \
      "$SCRIPT_DIR/publish-gateway/lib" \
      /opt/monitor-platform-monolith/lib \
      /opt/publish-gateway/gateway-udp-proxy/lib \
      /opt/publish-gateway/lib; do
      if has_libvauthsdk "$src"; then
        copy_dir_contents "$src" "$SCRIPT_DIR/monitor-platform/lib"
        pass "已从现有包补齐平台 SDK：$src"
        break
      fi
    done
  fi

  if ! has_cert_files "$SCRIPT_DIR/publish-gateway/certs"; then
    for src in \
      /opt/publish-gateway/certs \
      /opt/publish-gateway/gateway-udp-proxy/certs; do
      if has_cert_files "$src"; then
        copy_dir_contents "$src" "$SCRIPT_DIR/publish-gateway/certs"
        pass "已从现有网关包补齐证书目录：$src"
        break
      fi
    done
  fi

  if [[ ! -f "$SCRIPT_DIR/publish-gateway/trust/.asset-copied" ]]; then
    for src in \
      /opt/publish-gateway/trust \
      /opt/publish-gateway/gateway-udp-proxy/trust; do
      if [[ -d "$src" ]] && find "$src" -maxdepth 1 -type f | grep -q .; then
        copy_dir_contents "$src" "$SCRIPT_DIR/publish-gateway/trust"
        : > "$SCRIPT_DIR/publish-gateway/trust/.asset-copied"
        pass "已从现有网关包补齐 trust 目录：$src"
        break
      fi
    done
  fi
}

check_tcp_reachable() {
  local host="$1"
  local port="$2"
  local label="$3"
  if command -v timeout >/dev/null 2>&1; then
    timeout 3 bash -c "</dev/tcp/${host}/${port}" >/dev/null 2>&1 \
      && {
        pass "$label 可访问：$host:$port"
        return
      }
  else
    bash -c "</dev/tcp/${host}/${port}" >/dev/null 2>&1 \
      && {
        pass "$label 可访问：$host:$port"
        return
      }
  fi
  die "environment" "" "$label 无法访问：$host:$port"
}

mysql_exec() {
  local sql="$1"
  local mysql_cmd=(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" "-p$MYSQL_PASSWORD" --protocol=tcp --default-character-set=utf8mb4 -e "$sql")
  local local_mysql_host=false image

  if command -v mysql >/dev/null 2>&1; then
    "${mysql_cmd[@]}"
    return
  fi

  if [[ "$MYSQL_HOST" == "127.0.0.1" || "$MYSQL_HOST" == "localhost" || "$MYSQL_HOST" == "$SERVER_IP" ]]; then
    local_mysql_host=true
  fi

  if [[ "$local_mysql_host" == "true" ]] && docker ps --format '{{.Names}}' | grep -Fxq mysql; then
    docker exec mysql mysql -u"$MYSQL_USERNAME" "-p$MYSQL_PASSWORD" --default-character-set=utf8mb4 -e "$sql"
    return
  fi

  for image in mysql:5.7 mysql:8.0; do
    if docker image inspect "$image" >/dev/null 2>&1; then
      docker run --rm --network host "$image" mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" "-p$MYSQL_PASSWORD" --protocol=tcp --default-character-set=utf8mb4 -e "$sql"
      return
    fi
  done

  die "environment" "" "未检测到 mysql 客户端，且未找到可用 mysql 容器"
}

mysql_exec_no_die() {
  local sql="$1"
  local mysql_cmd=(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" "-p$MYSQL_PASSWORD" --protocol=tcp --default-character-set=utf8mb4 -e "$sql")
  local local_mysql_host=false image

  if command -v mysql >/dev/null 2>&1; then
    "${mysql_cmd[@]}"
    return $?
  fi

  if [[ "$MYSQL_HOST" == "127.0.0.1" || "$MYSQL_HOST" == "localhost" || "$MYSQL_HOST" == "$SERVER_IP" ]]; then
    local_mysql_host=true
  fi

  if [[ "$local_mysql_host" == "true" ]] && command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -Fxq mysql; then
    docker exec mysql mysql -u"$MYSQL_USERNAME" "-p$MYSQL_PASSWORD" --default-character-set=utf8mb4 -e "$sql"
    return $?
  fi

  if command -v docker >/dev/null 2>&1; then
    for image in mysql:5.7 mysql:8.0; do
      if docker image inspect "$image" >/dev/null 2>&1; then
        docker run --rm --network host "$image" mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USERNAME" "-p$MYSQL_PASSWORD" --protocol=tcp --default-character-set=utf8mb4 -e "$sql"
        return $?
      fi
    done
  fi

  return 127
}

ensure_databases() {
  local sql
  sql="CREATE DATABASE IF NOT EXISTS \`$PLATFORM_DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE IF NOT EXISTS \`$GATEWAY_DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  mysql_exec "$sql" >> "$RUN_LOG" 2>&1 || die "environment" "" "数据库创建/校验失败，请检查 MySQL 账号权限"
  pass "数据库已就绪：$PLATFORM_DB_NAME / $GATEWAY_DB_NAME"
}

check_package() {
  autofill_package_assets
  [[ -d "$SCRIPT_DIR/monitor-platform/lib" ]] || die "package" "$PLATFORM_SERVICE" "部署包缺少 monitor-platform/lib"
  [[ -d "$SCRIPT_DIR/publish-gateway/lib" ]] || die "package" "$GATEWAY_SERVICE" "部署包缺少 publish-gateway/lib"
  [[ -f "$SCRIPT_DIR/monitor-platform/lib/libvauthsdk.so" ]] || die "package" "$PLATFORM_SERVICE" "平台 SDK 缺少 libvauthsdk.so"
  [[ -f "$SCRIPT_DIR/publish-gateway/lib/libvauthsdk.so" ]] || die "package" "$GATEWAY_SERVICE" "加密网关 SDK 缺少 libvauthsdk.so"

  if ukey_setup_required; then
    ensure_tool_executable
    [[ -d "$SCRIPT_DIR/publish-gateway/certs" ]] || die "package" "$GATEWAY_SERVICE" "部署包缺少 publish-gateway/certs"
    compgen -G "$SCRIPT_DIR/publish-gateway/certs/*.cer" >/dev/null || die "package" "$GATEWAY_SERVICE" "加密网关证书目录缺少 .cer 文件"
    if [[ -n "${PLATFORM_AUTH_ID:-}" ]]; then
      find_cert_file "$PLATFORM_AUTH_ID" "$PLATFORM_AUTH_ID" >/dev/null || die "package" "$GATEWAY_SERVICE" "缺少平台认证证书：publish-gateway/certs/${PLATFORM_AUTH_ID}_SIGN.cer 或 publish-gateway/certs/${PLATFORM_AUTH_ID}_*_SIGN.cer"
    else
      warn "尚未采集 PLATFORM_AUTH_ID，暂跳过平台认证证书精确校验"
    fi
    if [[ -n "${GATEWAY_AUTH_ID:-}" ]]; then
      find_cert_file "$GATEWAY_AUTH_ID" "${GATEWAY_CERT_SERIAL_NO:-}" >/dev/null || die "package" "$GATEWAY_SERVICE" "缺少网关认证证书：publish-gateway/certs/${GATEWAY_AUTH_ID}_SIGN.cer 或 publish-gateway/certs/${GATEWAY_CERT_SERIAL_NO:-网关证书编号}_SIGN.cer"
    else
      warn "尚未采集 GATEWAY_AUTH_ID，暂跳过网关认证证书精确校验"
    fi
  else
    if [[ -f "$SCRIPT_DIR/tools/ukey-list" ]]; then
      ensure_tool_executable
    else
      warn "UKey 后置模式：暂未校验 tools/ukey-list，后续配置 UKey 前需补齐"
    fi
    if [[ ! -d "$SCRIPT_DIR/publish-gateway/certs" ]] || ! compgen -G "$SCRIPT_DIR/publish-gateway/certs/*.cer" >/dev/null; then
      warn "UKey 后置模式：暂未校验 publish-gateway/certs/*.cer，后续正式启用 UKey 前需补齐"
    else
      warn "UKey 后置模式：跳过证书与认证 ID 精确匹配校验"
    fi
  fi
  pass "部署包目录结构完整"
}

find_cert_file() {
  local auth_id="${1:-}"
  local cert_serial_no="${2:-}"
  local cert_dir="$SCRIPT_DIR/publish-gateway/certs"
  local candidate

  auth_id="$(clean_config_value "$auth_id")"
  cert_serial_no="$(clean_config_value "$cert_serial_no")"

  for candidate in \
    "$cert_dir/${auth_id}_SIGN.cer" \
    "$cert_dir/${cert_serial_no}_SIGN.cer"; do
    if [[ -n "$candidate" && -f "$candidate" ]]; then
      printf '%s' "$candidate"
      return 0
    fi
  done

  if [[ -n "$auth_id" ]]; then
    candidate="$(find "$cert_dir" -maxdepth 1 -type f -name "${auth_id}_*_SIGN.cer" 2>/dev/null | head -n 1 || true)"
    if [[ -n "$candidate" ]]; then
      printf '%s' "$candidate"
      return 0
    fi
  fi

  return 1
}

check_package_before_collect() {
  check_host_tools
  autofill_package_assets
  [[ -d "$SCRIPT_DIR/monitor-platform/lib" ]] || die "package" "$PLATFORM_SERVICE" "部署包缺少 monitor-platform/lib"
  [[ -d "$SCRIPT_DIR/publish-gateway/lib" ]] || die "package" "$GATEWAY_SERVICE" "部署包缺少 publish-gateway/lib"
  [[ -f "$SCRIPT_DIR/monitor-platform/lib/libvauthsdk.so" ]] || die "package" "$PLATFORM_SERVICE" "平台 SDK 缺少 libvauthsdk.so"
  [[ -f "$SCRIPT_DIR/publish-gateway/lib/libvauthsdk.so" ]] || die "package" "$GATEWAY_SERVICE" "加密网关 SDK 缺少 libvauthsdk.so"
}

preflight() {
  section "正在检查服务器环境"
  load_config
  validate_required_config
  require_root
  check_host_tools
  check_docker
  if ukey_setup_required; then
    check_usb
  else
    warn "UKey 后置模式：跳过 USB/UKey 预检，仅检查基础服务依赖"
  fi
  check_package
  if ukey_setup_required; then
    resolve_ukey_bindings_required
  fi
  check_tcp_reachable "$MYSQL_HOST" "$MYSQL_PORT" "MySQL"
  ensure_databases
  check_tcp_reachable "$REDIS_HOST" "$REDIS_PORT" "Redis"
  check_tcp_reachable "$MINIO_HOST" "$MINIO_PORT" "MinIO"
  check_port_available_for_service "$PLATFORM_HTTP_PORT" "平台 HTTP" tcp "$PLATFORM_SERVICE"
  check_port_available_for_service "$GATEWAY_HTTP_PORT" "加密网关 HTTP" tcp "$GATEWAY_SERVICE"
  check_port_available_for_service "$CLIENT_RELAY_PORT" "客户端 UDP 中继" udp "$GATEWAY_SERVICE"
  pass "环境检查完成"
}

validate_config_file_exists() {
  if [[ ! -f "$CONFIG_FILE" ]]; then
    validation_error "未找到配置文件：$CONFIG_FILE。请先执行 sudo ./install.sh --init-config 后填写"
    return 1
  fi
}

validate_config_assignment_syntax_collect() {
  local line_no=0
  local line key value value_check first_char
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_no=$((line_no + 1))
    line="${line%$'\r'}"
    [[ "$line" =~ ^[[:space:]]*$ || "$line" =~ ^[[:space:]]*# ]] && continue

    if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
      key="${BASH_REMATCH[1]}"
      value="${BASH_REMATCH[2]}"
      if [[ "$value" =~ ^[[:space:]] ]]; then
        validation_error "配置文件第 ${line_no} 行 $key 的等号后不能直接跟空格，请写成：$key='值'"
        continue
      fi

      value_check="$(printf '%s' "$value" | sed -E 's/[[:space:]]+#.*$//')"
      first_char="${value_check:0:1}"
      if [[ "$value_check" == *[[:space:]]* && "$first_char" != "'" && "$first_char" != '"' ]]; then
        validation_error "配置文件第 ${line_no} 行 $key 的值包含空格，必须加引号，例如：$key='$value_check'"
      fi
    fi
  done < "$CONFIG_FILE"
}

validate_required_fields_collect() {
  local required=(
    SERVER_IP PLATFORM_HTTP_PORT GATEWAY_HTTP_PORT CLIENT_RELAY_PORT MDNS_PORT
    MYSQL_HOST MYSQL_PORT MYSQL_USERNAME MYSQL_PASSWORD PLATFORM_DB_NAME GATEWAY_DB_NAME
    REDIS_HOST REDIS_PORT
    MINIO_HOST MINIO_PORT MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_BUCKET
    UKEY_ADMIN_USERNAME UKEY_ADMIN_PASSWORD UKEY_SETUP_MODE
  )
  local ukey_required=(PLATFORM_UKEY_PIN PLATFORM_AUTH_ID GATEWAY_UKEY_PIN GATEWAY_AUTH_ID)
  local name

  for name in "${required[@]}"; do
    [[ -n "${!name:-}" ]] || validation_error "配置项 $name 不能为空"
  done

  if [[ "${JWT_SECRET_AUTO_GENERATED:-false}" == "true" ]]; then
    validation_warn "配置项 JWT_SECRET 未填写，将在部署时自动生成并写回配置文件"
  fi

  case "${UKEY_SETUP_MODE:-}" in
    required|deferred)
      ;;
    *)
      validation_error "配置项 UKEY_SETUP_MODE 只能为 required 或 deferred，当前值：${UKEY_SETUP_MODE:-空}"
      ;;
  esac

  if ukey_setup_required; then
    for name in "${ukey_required[@]}"; do
      [[ -n "${!name:-}" ]] || validation_error "UKey 正式模式缺少 $name；若现场暂缺 UKey，请改为 UKEY_SETUP_MODE=deferred"
    done
  fi
}

validate_port_collect() {
  local name value
  for name in PLATFORM_HTTP_PORT GATEWAY_HTTP_PORT CLIENT_RELAY_PORT MDNS_PORT MYSQL_PORT REDIS_PORT MINIO_PORT; do
    value="${!name:-}"
    if [[ -n "$value" ]] && ! is_port "$value"; then
      validation_error "配置项 $name 不是合法端口：$value"
    fi
  done
  for name in MQTT_RECONNECT_INTERVAL_MS MQTT_COMMAND_DEDUP_TTL_MS MQTT_DEDUP_CLEANUP_INTERVAL_MS; do
    value="${!name:-}"
    if [[ -n "$value" && ! "$value" =~ ^[0-9]+$ ]]; then
      validation_error "配置项 $name 不是合法数字：$value"
    fi
  done
  if [[ -n "${MQTT_AGENT_ENABLED:-}" && "$MQTT_AGENT_ENABLED" != "true" && "$MQTT_AGENT_ENABLED" != "false" ]]; then
    validation_error "配置项 MQTT_AGENT_ENABLED 只能为 true 或 false：$MQTT_AGENT_ENABLED"
  fi
  if [[ "${MQTT_AGENT_ENABLED:-false}" == "true" && -z "${MQTT_BROKER_URL:-}" ]]; then
    validation_error "MQTT_AGENT_ENABLED=true 时必须填写 MQTT_BROKER_URL"
  fi
}

validate_host_tools_collect() {
  local cmd
  for cmd in curl awk sed grep find tr; do
    command -v "$cmd" >/dev/null 2>&1 || validation_error "服务器缺少基础命令：$cmd"
  done
  if ! command -v python3 >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1 && ! command -v python2 >/dev/null 2>&1; then
    validation_error "服务器缺少基础命令：python3/python/python2"
  fi
}

validate_docker_collect() {
  if ! command -v docker >/dev/null 2>&1; then
    validation_error "未检测到 Docker"
    return
  fi
  docker info >/dev/null 2>&1 || validation_error "Docker 服务未启动或当前用户无权访问 Docker"
  if docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker-compose)
  else
    validation_error "未检测到 Docker Compose"
  fi
}

validate_tcp_reachable_collect() {
  local host="$1"
  local port="$2"
  local label="$3"
  [[ -n "$host" && -n "$port" ]] || {
    validation_error "$label 地址或端口为空"
    return
  }
  if command -v timeout >/dev/null 2>&1; then
    timeout 3 bash -c "</dev/tcp/${host}/${port}" >/dev/null 2>&1 && {
      pass "$label 可访问：$host:$port"
      return
    }
  else
    bash -c "</dev/tcp/${host}/${port}" >/dev/null 2>&1 && {
      pass "$label 可访问：$host:$port"
      return
    }
  fi
  validation_error "$label 无法访问：$host:$port"
}

validate_databases_collect() {
  local sql
  [[ -n "${MYSQL_HOST:-}" && -n "${MYSQL_PORT:-}" && -n "${MYSQL_USERNAME:-}" && -n "${MYSQL_PASSWORD:-}" ]] || return
  [[ -n "${PLATFORM_DB_NAME:-}" && -n "${GATEWAY_DB_NAME:-}" ]] || return
  sql="CREATE DATABASE IF NOT EXISTS \`$PLATFORM_DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE IF NOT EXISTS \`$GATEWAY_DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  if mysql_exec_no_die "$sql" >> "$RUN_LOG" 2>&1; then
    pass "数据库已就绪：$PLATFORM_DB_NAME / $GATEWAY_DB_NAME"
  else
    validation_error "数据库创建/校验失败，请检查 MySQL 账号权限"
  fi
}

validate_package_collect() {
  autofill_package_assets
  [[ -d "$SCRIPT_DIR/images" ]] || validation_warn "部署包缺少 images/ 目录；如果服务器已有镜像可忽略"
  if [[ -d "$SCRIPT_DIR/images" ]]; then
    compgen -G "$SCRIPT_DIR/images/*.tar" >/dev/null || \
      compgen -G "$SCRIPT_DIR/images/*.tar.gz" >/dev/null || \
      compgen -G "$SCRIPT_DIR/images/*.tgz" >/dev/null || \
      validation_warn "images/ 下未发现镜像离线包，将依赖服务器已有镜像"
  fi

  [[ -d "$SCRIPT_DIR/monitor-platform/lib" ]] || validation_error "部署包缺少 monitor-platform/lib"
  [[ -d "$SCRIPT_DIR/publish-gateway/lib" ]] || validation_error "部署包缺少 publish-gateway/lib"
  [[ -f "$SCRIPT_DIR/monitor-platform/lib/libvauthsdk.so" ]] || validation_error "平台 SDK 缺少 monitor-platform/lib/libvauthsdk.so"
  [[ -f "$SCRIPT_DIR/publish-gateway/lib/libvauthsdk.so" ]] || validation_error "加密网关 SDK 缺少 publish-gateway/lib/libvauthsdk.so"

  if ukey_setup_required; then
    [[ -f "$SCRIPT_DIR/tools/ukey-list" ]] || validation_error "正式 UKey 模式缺少 UKey 枚举工具：tools/ukey-list"
    [[ -d "$SCRIPT_DIR/publish-gateway/certs" ]] || validation_error "正式 UKey 模式缺少 publish-gateway/certs"
    if [[ -d "$SCRIPT_DIR/publish-gateway/certs" ]]; then
      compgen -G "$SCRIPT_DIR/publish-gateway/certs/*.cer" >/dev/null || validation_error "publish-gateway/certs/ 下缺少 .cer 文件"
    if [[ -n "${PLATFORM_AUTH_ID:-}" ]] && ! find_cert_file "$PLATFORM_AUTH_ID" "$PLATFORM_AUTH_ID" >/dev/null; then
        validation_error "缺少平台认证证书：publish-gateway/certs/${PLATFORM_AUTH_ID}_SIGN.cer 或 publish-gateway/certs/${PLATFORM_AUTH_ID}_*_SIGN.cer"
      fi
      if [[ -n "${GATEWAY_AUTH_ID:-}" ]] && ! find_cert_file "$GATEWAY_AUTH_ID" "${GATEWAY_CERT_SERIAL_NO:-}" >/dev/null; then
        validation_error "缺少网关认证证书：publish-gateway/certs/${GATEWAY_AUTH_ID}_SIGN.cer 或 publish-gateway/certs/${GATEWAY_CERT_SERIAL_NO:-网关证书编号}_SIGN.cer"
      fi
    fi
  else
    [[ -f "$SCRIPT_DIR/tools/ukey-list" ]] || validation_warn "UKey 后置模式：tools/ukey-list 可暂缓，正式启用前必须补齐"
    if [[ ! -d "$SCRIPT_DIR/publish-gateway/certs" ]] || ! compgen -G "$SCRIPT_DIR/publish-gateway/certs/*.cer" >/dev/null; then
      validation_warn "UKey 后置模式：publish-gateway/certs/*.cer 可暂缓，正式启用前必须补齐"
    fi
  fi
}

validate_ports_available_collect() {
  [[ -n "${PLATFORM_HTTP_PORT:-}" ]] && is_port "$PLATFORM_HTTP_PORT" && {
    if port_in_use "$PLATFORM_HTTP_PORT" tcp && ! container_exists "$PLATFORM_SERVICE"; then
      validation_error "平台 HTTP 端口已被占用：$PLATFORM_HTTP_PORT/tcp"
    fi
  }
  [[ -n "${GATEWAY_HTTP_PORT:-}" ]] && is_port "$GATEWAY_HTTP_PORT" && {
    if port_in_use "$GATEWAY_HTTP_PORT" tcp && ! container_exists "$GATEWAY_SERVICE"; then
      validation_error "加密网关 HTTP 端口已被占用：$GATEWAY_HTTP_PORT/tcp"
    fi
  }
  [[ -n "${CLIENT_RELAY_PORT:-}" ]] && is_port "$CLIENT_RELAY_PORT" && {
    if port_in_use "$CLIENT_RELAY_PORT" udp && ! container_exists "$GATEWAY_SERVICE"; then
      validation_error "客户端 UDP 中继端口已被占用：$CLIENT_RELAY_PORT/udp"
    fi
  }
}

validate_ukey_collect() {
  if ukey_setup_required; then
    [[ -d /dev/bus/usb ]] || validation_error "正式 UKey 模式下 USB 设备目录不存在：/dev/bus/usb"
    if [[ -f "$SCRIPT_DIR/tools/ukey-list" ]]; then
      ensure_tool_executable
    fi
  else
    validation_warn "UKey 后置模式：跳过 USB/UKey 检查，仅校验基础部署依赖"
  fi
}

validate_config_only() {
  section "配置文件校验"
  validation_reset
  validate_config_file_exists || {
    validation_summary
    return 1
  }
  validate_config_assignment_syntax_collect
  if validation_has_errors; then
    validation_summary
    return 1
  fi
  # shellcheck disable=SC1090
  set +e
  source_config_file
  local source_rc=$?
  set -e
  if [[ "$source_rc" -ne 0 ]]; then
    validation_error "配置文件语法错误：$CONFIG_FILE。请检查等号两侧空格、引号、特殊字符"
    validation_summary
    return 1
  fi
  if [[ "${CONFIG_VALUES_NORMALIZED:-false}" == "true" ]]; then
    validation_warn "配置文件中检测到隐藏字符或首尾空白，已在本次执行中自动清理后继续校验"
  fi
  validate_required_fields_collect
  validate_port_collect
  validate_host_tools_collect
  validate_docker_collect
  validate_ukey_collect
  validate_package_collect
  resolve_ukey_bindings_required_collect
  validate_tcp_reachable_collect "${MYSQL_HOST:-}" "${MYSQL_PORT:-}" "MySQL"
  validate_databases_collect
  validate_tcp_reachable_collect "${REDIS_HOST:-}" "${REDIS_PORT:-}" "Redis"
  validate_tcp_reachable_collect "${MINIO_HOST:-}" "${MINIO_PORT:-}" "MinIO"
  validate_ports_available_collect
  validation_summary
}

init_config_file() {
  section "初始化配置文件"
  mkdir -p "$CONFIG_DIR"
  if [[ -f "$CONFIG_FILE" ]]; then
    warn "配置文件已存在：$CONFIG_FILE"
    print_line "如需重建，请先备份后删除该文件，或直接编辑现有配置。"
    return 0
  fi
  if [[ -f "$CONFIG_DIR/deploy.conf.example" ]]; then
    cp "$CONFIG_DIR/deploy.conf.example" "$CONFIG_FILE"
  else
    : > "$CONFIG_FILE"
    write_kv "$CONFIG_FILE" PLATFORM_DIR "/opt/monitor-platform-monolith"
    write_kv "$CONFIG_FILE" GATEWAY_DIR "/opt/publish-gateway"
    write_kv "$CONFIG_FILE" PLATFORM_SERVICE "monitor-platform-monolith"
    write_kv "$CONFIG_FILE" GATEWAY_SERVICE "gateway-udp-proxy"
    write_kv "$CONFIG_FILE" PLATFORM_IMAGE "monitor-platform-monolith:1.0.0"
    write_kv "$CONFIG_FILE" GATEWAY_IMAGE "gateway-udp-proxy:1.0.0"
    write_kv "$CONFIG_FILE" UKEY_SETUP_MODE "deferred"
  fi
  chmod 600 "$CONFIG_FILE" 2>/dev/null || true
  pass "配置文件已生成：$CONFIG_FILE"
  print_line "请填写配置后执行：sudo ./install.sh --validate"
}

copy_dir_contents() {
  local src="$1"
  local dst="$2"
  [[ -d "$src" ]] || return 0
  mkdir -p "$dst"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a "$src"/ "$dst"/
  else
    cp -a "$src"/. "$dst"/
  fi
}

backup_file() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  local ts
  ts="$(date '+%Y%m%d%H%M%S')"
  cp -p "$file" "$file.$ts.bak"
}

set_config_value() {
  local key="$1"
  local value="${2:-}"
  local escaped tmp

  escaped="$(shell_escape_single "$value")"
  tmp="${CONFIG_FILE}.tmp.$$"
  awk -v k="$key" -v v="$escaped" -v q="'" '
    BEGIN { done = 0 }
    $0 ~ "^[[:space:]]*" k "=" {
      print k "=" q v q
      done = 1
      next
    }
    { print }
    END {
      if (!done) {
        print k "=" q v q
      }
    }
  ' "$CONFIG_FILE" > "$tmp" && mv "$tmp" "$CONFIG_FILE"
}

persist_resolved_ukey_bindings() {
  [[ "${UKEY_BINDINGS_RESOLVED:-false}" == "true" ]] || return 0
  if ! command -v awk >/dev/null 2>&1; then
    warn "缺少 awk，暂无法把自动识别到的 UKey 绑定信息写回配置文件"
    return 0
  fi
  backup_file "$CONFIG_FILE"
  set_config_value PLATFORM_UKEY_PATH "$PLATFORM_UKEY_PATH"
  set_config_value PLATFORM_UKEY_SN "$PLATFORM_UKEY_SN"
  set_config_value PLATFORM_UKEY_CER_SN "$PLATFORM_UKEY_CER_SN"
  set_config_value PLATFORM_UKEY_CER_ID "$PLATFORM_UKEY_CER_ID"
  set_config_value GATEWAY_UKEY_PATH "$GATEWAY_UKEY_PATH"
  set_config_value GATEWAY_UKEY_SN "$GATEWAY_UKEY_SN"
  set_config_value GATEWAY_UKEY_CER_SN "$GATEWAY_UKEY_CER_SN"
  set_config_value GATEWAY_UKEY_CER_ID "$GATEWAY_UKEY_CER_ID"
  set_config_value GATEWAY_CERT_SERIAL_NO "$GATEWAY_CERT_SERIAL_NO"
  chmod 600 "$CONFIG_FILE" 2>/dev/null || true
  UKEY_BINDINGS_RESOLVED="false"
  pass "已将 UKey path/sn/cerSn/cerId 自动写回配置文件"
}

persist_auto_generated_jwt_secret() {
  [[ "${JWT_SECRET_AUTO_GENERATED:-false}" == "true" ]] || return 0
  backup_file "$CONFIG_FILE"
  if grep -qE '^[[:space:]]*JWT_SECRET=' "$CONFIG_FILE"; then
    sed -i "s|^[[:space:]]*JWT_SECRET=.*|JWT_SECRET='$JWT_SECRET'|" "$CONFIG_FILE"
  else
    write_kv "$CONFIG_FILE" JWT_SECRET "$JWT_SECRET"
  fi
  JWT_SECRET_AUTO_GENERATED="false"
  pass "JWT_SECRET 未填写，已自动生成并写回配置文件"
}

prepare_dirs() {
  mkdir -p \
    "$PLATFORM_DEPLOY_DIR/config" "$PLATFORM_DEPLOY_DIR/logs" "$PLATFORM_DEPLOY_DIR/lib" \
    "$GATEWAY_DEPLOY_DIR/config" "$GATEWAY_DEPLOY_DIR/logs" "$GATEWAY_DEPLOY_DIR/lib" \
    "$GATEWAY_DEPLOY_DIR/certs" "$GATEWAY_DEPLOY_DIR/trust" "$GATEWAY_DEPLOY_DIR/secure-publish/rejected" \
    "$GATEWAY_DEPLOY_DIR/redis/data"

  backup_file "$PLATFORM_DEPLOY_DIR/.env"
  backup_file "$PLATFORM_DEPLOY_DIR/docker-compose.yml"
  backup_file "$GATEWAY_DEPLOY_DIR/.env"
  backup_file "$GATEWAY_DEPLOY_DIR/docker-compose.yml"

  copy_dir_contents "$SCRIPT_DIR/monitor-platform/lib" "$PLATFORM_DEPLOY_DIR/lib"
  copy_dir_contents "$SCRIPT_DIR/publish-gateway/lib" "$GATEWAY_DEPLOY_DIR/lib"
  copy_dir_contents "$SCRIPT_DIR/publish-gateway/certs" "$GATEWAY_DEPLOY_DIR/certs"
  copy_dir_contents "$SCRIPT_DIR/publish-gateway/trust" "$GATEWAY_DEPLOY_DIR/trust"

  pass "部署目录已准备完成"
}

load_images() {
  local loaded=false file
  shopt -s nullglob
  for file in "$SCRIPT_DIR/images"/*.tar "$SCRIPT_DIR/images"/*.tar.gz "$SCRIPT_DIR/images"/*.tgz; do
    loaded=true
    print_line "正在加载镜像：$file"
    docker load -i "$file" >> "$RUN_LOG" 2>&1 || die "package" "" "镜像加载失败：$file"
  done
  shopt -u nullglob
  if [[ "$loaded" == "true" ]]; then
    pass "离线镜像加载完成"
  else
    warn "images/ 下未发现镜像离线包，将使用服务器已有镜像"
  fi
}

image_exists() {
  docker image inspect "$1" >/dev/null 2>&1
}

check_images() {
  image_exists "$PLATFORM_IMAGE" || die "package" "$PLATFORM_SERVICE" "未找到平台镜像：$PLATFORM_IMAGE"
  image_exists "$GATEWAY_IMAGE" || die "package" "$GATEWAY_SERVICE" "未找到加密网关镜像：$GATEWAY_IMAGE"
  pass "平台镜像已就绪：$PLATFORM_IMAGE"
  pass "加密网关镜像已就绪：$GATEWAY_IMAGE"
}

write_platform_env() {
  local file="$PLATFORM_DEPLOY_DIR/.env"
  local vauth_required="true"
  if ukey_setup_deferred; then
    vauth_required="false"
  fi
  : > "$file"
  write_kv "$file" SERVER_PORT "$PLATFORM_HTTP_PORT"
  write_kv "$file" MYSQL_HOST "$MYSQL_HOST"
  write_kv "$file" MYSQL_PORT "$MYSQL_PORT"
  write_kv "$file" MYSQL_DB "$PLATFORM_DB_NAME"
  write_kv "$file" MYSQL_USERNAME "$MYSQL_USERNAME"
  write_kv "$file" MYSQL_PASSWORD "$MYSQL_PASSWORD"
  write_kv "$file" REDIS_HOST "$REDIS_HOST"
  write_kv "$file" REDIS_PORT "$REDIS_PORT"
  write_kv "$file" REDIS_PASSWORD "$REDIS_PASSWORD"
  write_kv "$file" REDIS_DB "$PLATFORM_REDIS_DB"
  write_kv "$file" MINIO_ENDPOINT "http://$MINIO_HOST:$MINIO_PORT"
  write_kv "$file" MINIO_ACCESS_KEY "$MINIO_ACCESS_KEY"
  write_kv "$file" MINIO_SECRET_KEY "$MINIO_SECRET_KEY"
  write_kv "$file" MINIO_BUCKET "$MINIO_BUCKET"
  write_kv "$file" VAUTH_SERVER_MODE "ukey"
  write_kv "$file" VAUTH_SERVER_REQUIRED "$vauth_required"
  write_kv "$file" VAUTH_SERVER_PASSWORD "$PLATFORM_UKEY_PIN"
  write_kv "$file" VAUTH_SERVER_AUTH_ID "$PLATFORM_AUTH_ID"
  write_kv "$file" VAUTH_SERVER_UKEY_PATH "$PLATFORM_UKEY_PATH"
  write_kv "$file" VAUTH_SERVER_UKEY_SN "$PLATFORM_UKEY_SN"
  write_kv "$file" VAUTH_SERVER_UKEY_CER_SN "$PLATFORM_UKEY_CER_SN"
  write_kv "$file" VAUTH_SERVER_UKEY_CER_ID "$PLATFORM_UKEY_CER_ID"
  write_kv "$file" VAUTH_SERVER_LIBRARY_PATH "/opt/monitor-platform-monolith/lib"
  write_kv "$file" VAUTH_SERVER_DEPENDENCY_PATHS "/opt/monitor-platform-monolith/lib"
  write_kv "$file" UKEY_ADMIN_USERNAME "$UKEY_ADMIN_USERNAME"
  write_kv "$file" UKEY_ADMIN_PASSWORD "$UKEY_ADMIN_PASSWORD"
  write_kv "$file" JWT_SECRET "$JWT_SECRET"
  write_kv "$file" JWT_EXPIRE_HOURS "$JWT_EXPIRE_HOURS"
  write_kv "$file" JAVA_OPTS "$JAVA_OPTS"
  chmod 600 "$file"
}

write_gateway_env() {
  local file="$GATEWAY_DEPLOY_DIR/.env"
  local vauth_mock_mode="false"
  local gateway_auth_id="${GATEWAY_AUTH_ID:-}"
  local platform_auth_id="${PLATFORM_AUTH_ID:-}"
  local gateway_cert_serial_no="${GATEWAY_CERT_SERIAL_NO:-${GATEWAY_UKEY_CER_ID:-${GATEWAY_AUTH_ID:-}}}"
  if ukey_setup_deferred; then
    vauth_mock_mode="true"
    gateway_auth_id="${gateway_auth_id:-deferred-gateway}"
    platform_auth_id="${platform_auth_id:-deferred-platform}"
    gateway_cert_serial_no="${gateway_cert_serial_no:-$gateway_auth_id}"
  fi
  : > "$file"
  write_kv "$file" DB_HOST "$MYSQL_HOST"
  write_kv "$file" DB_PORT "$MYSQL_PORT"
  write_kv "$file" DB_NAME "$GATEWAY_DB_NAME"
  write_kv "$file" DB_USERNAME "$MYSQL_USERNAME"
  write_kv "$file" DB_PASSWORD "$MYSQL_PASSWORD"
  write_kv "$file" REDIS_HOST "$REDIS_HOST"
  write_kv "$file" REDIS_PORT "$REDIS_PORT"
  write_kv "$file" REDIS_PASSWORD "$REDIS_PASSWORD"
  write_kv "$file" REDIS_DATABASE "$GATEWAY_REDIS_DB"
  write_kv "$file" MINIO_HOST "$MINIO_HOST"
  write_kv "$file" MINIO_PORT "$MINIO_PORT"
  write_kv "$file" MINIO_ACCESS_KEY "$MINIO_ACCESS_KEY"
  write_kv "$file" MINIO_SECRET_KEY "$MINIO_SECRET_KEY"
  write_kv "$file" MINIO_BUCKET "$MINIO_BUCKET"
  write_kv "$file" MONITOR_HOST "$SERVER_IP"
  write_kv "$file" MONITOR_CONTENT_PORT "$PLATFORM_HTTP_PORT"
  write_kv "$file" MONITOR_UKEY_PORT "$PLATFORM_HTTP_PORT"
  write_kv "$file" MONITOR_DEVICE_PORT "$PLATFORM_HTTP_PORT"
  write_kv "$file" MONITOR_LOG_URL "http://$SERVER_IP:$PLATFORM_HTTP_PORT"
  write_kv "$file" MQTT_AGENT_ENABLED "$MQTT_AGENT_ENABLED"
  write_kv "$file" MQTT_BROKER_URL "$MQTT_BROKER_URL"
  write_kv "$file" MQTT_USERNAME "$MQTT_USERNAME"
  write_kv "$file" MQTT_PASSWORD "$MQTT_PASSWORD"
  write_kv "$file" MQTT_CLIENT_ID "$MQTT_CLIENT_ID"
  write_kv "$file" MQTT_TENANT_ID "$MQTT_TENANT_ID"
  write_kv "$file" MQTT_SITE_ID "$MQTT_SITE_ID"
  write_kv "$file" MQTT_RECONNECT_INTERVAL_MS "$MQTT_RECONNECT_INTERVAL_MS"
  write_kv "$file" MQTT_COMMAND_DEDUP_TTL_MS "$MQTT_COMMAND_DEDUP_TTL_MS"
  write_kv "$file" MQTT_DEDUP_CLEANUP_INTERVAL_MS "$MQTT_DEDUP_CLEANUP_INTERVAL_MS"
  write_kv "$file" VAUTH_MOCK_MODE "$vauth_mock_mode"
  write_kv "$file" VAUTH_DEVICE_TYPE "ukey"
  write_kv "$file" VAUTH_PASSWORD "$GATEWAY_UKEY_PIN"
  write_kv "$file" VAUTH_AUTH_ID "$gateway_auth_id"
  write_kv "$file" VAUTH_SERVER_ID "$platform_auth_id"
  write_kv "$file" VAUTH_SERVER_CERT_PATH "/app/certs/${platform_auth_id}_SIGN.cer"
  write_kv "$file" VAUTH_CLIENT_CERT_PATH "/app/certs/${gateway_cert_serial_no}_SIGN.cer"
  write_kv "$file" VAUTH_UKEY_PATH "$GATEWAY_UKEY_PATH"
  write_kv "$file" VAUTH_UKEY_SN "$GATEWAY_UKEY_SN"
  write_kv "$file" VAUTH_UKEY_CER_SN "$GATEWAY_UKEY_CER_SN"
  write_kv "$file" VAUTH_UKEY_CER_ID "$GATEWAY_UKEY_CER_ID"
  write_kv "$file" VAUTH_SVAC_MODE "false"
  write_kv "$file" PROVIDER_JMDNS_ENABLED "true"
  write_kv "$file" PROVIDER_JMDNS_BIND_ADDRESS "$SERVER_IP"
  write_kv "$file" PROVIDER_JMDNS_ADVERTISE_HOST "$SERVER_IP"
  write_kv "$file" REGISTRY_CLIENT_ID "$gateway_auth_id"
  write_kv "$file" DEVICE_VERSION "$DEVICE_VERSION"
  write_kv "$file" DEVICE_LOCATION "${DEVICE_LOCATION:-}"
  write_kv "$file" DEVICE_MANUFACTURER "${DEVICE_MANUFACTURER:-}"
  write_kv "$file" DEVICE_MODEL "${DEVICE_MODEL:-}"
  write_kv "$file" DEVICE_REMARK "${DEVICE_REMARK:-}"
  write_kv "$file" CLIENT_RELAY_ENABLED "true"
  write_kv "$file" CLIENT_RELAY_PORT "$CLIENT_RELAY_PORT"
  write_kv "$file" CLIENT_RELAY_TRUSTED_IPS "${CLIENT_RELAY_TRUSTED_IPS:-}"
  write_kv "$file" CLIENT_RELAY_VERIFY_SIGNATURE "false"
  write_kv "$file" CLIENT_RELAY_SIGNATURE_SECRET "${CLIENT_RELAY_SIGNATURE_SECRET:-}"
  write_kv "$file" CLIENT_RELAY_REJECT_DIRECT_UDP "false"
  write_kv "$file" SECURE_PUBLISH_ENABLED "$SECURE_PUBLISH_ENABLED"
  write_kv "$file" SECURE_PUBLISH_TRUST_STORE_DIR "/opt/publish-gateway/trust"
  write_kv "$file" SECURE_PUBLISH_TRUSTED_KEY_IDS "${SECURE_PUBLISH_TRUSTED_KEY_IDS:-}"
  write_kv "$file" SECURE_PUBLISH_DEFAULT_KEY_ID "$SECURE_PUBLISH_DEFAULT_KEY_ID"
  write_kv "$file" SECURE_PUBLISH_VERIFIER_PUBLIC_KEY_PATH "${SECURE_PUBLISH_VERIFIER_PUBLIC_KEY_PATH:-}"
  chmod 600 "$file"
}

write_platform_compose() {
  cat > "$PLATFORM_DEPLOY_DIR/docker-compose.yml" <<EOF
version: "3.8"

services:
  ${PLATFORM_SERVICE}:
    image: ${PLATFORM_IMAGE}
    container_name: ${PLATFORM_SERVICE}
    network_mode: host
    privileged: true
    ulimits:
      nofile:
        soft: 65535
        hard: 65535
    env_file:
      - .env
    environment:
      TZ: Asia/Shanghai
      LD_LIBRARY_PATH: /opt/monitor-platform-monolith/lib
      SERVER_PORT: \${SERVER_PORT:-8080}
      MYSQL_HOST: \${MYSQL_HOST}
      MYSQL_PORT: \${MYSQL_PORT}
      MYSQL_DB: \${MYSQL_DB}
      MYSQL_USERNAME: \${MYSQL_USERNAME}
      MYSQL_PASSWORD: \${MYSQL_PASSWORD}
      REDIS_HOST: \${REDIS_HOST}
      REDIS_PORT: \${REDIS_PORT}
      REDIS_PASSWORD: \${REDIS_PASSWORD}
      REDIS_DB: \${REDIS_DB:-0}
      MINIO_ENDPOINT: \${MINIO_ENDPOINT}
      MINIO_ACCESS_KEY: \${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: \${MINIO_SECRET_KEY}
      MINIO_BUCKET: \${MINIO_BUCKET}
      VAUTH_SERVER_MODE: \${VAUTH_SERVER_MODE:-ukey}
      VAUTH_SERVER_REQUIRED: \${VAUTH_SERVER_REQUIRED:-true}
      VAUTH_SERVER_PASSWORD: \${VAUTH_SERVER_PASSWORD}
      VAUTH_SERVER_AUTH_ID: \${VAUTH_SERVER_AUTH_ID}
      VAUTH_SERVER_UKEY_PATH: \${VAUTH_SERVER_UKEY_PATH:-}
      VAUTH_SERVER_UKEY_SN: \${VAUTH_SERVER_UKEY_SN:-}
      VAUTH_SERVER_UKEY_CER_SN: \${VAUTH_SERVER_UKEY_CER_SN:-}
      VAUTH_SERVER_UKEY_CER_ID: \${VAUTH_SERVER_UKEY_CER_ID:-}
      VAUTH_SERVER_LIBRARY_PATH: /opt/monitor-platform-monolith/lib
      VAUTH_SERVER_DEPENDENCY_PATHS: /opt/monitor-platform-monolith/lib
      UKEY_ADMIN_USERNAME: \${UKEY_ADMIN_USERNAME}
      UKEY_ADMIN_PASSWORD: \${UKEY_ADMIN_PASSWORD}
      JWT_SECRET: \${JWT_SECRET}
      JWT_EXPIRE_HOURS: \${JWT_EXPIRE_HOURS:-8}
      JAVA_OPTS: \${JAVA_OPTS:--Xms512m -Xmx1024m}
    volumes:
      - ./config:/app/config
      - ./logs:/app/logs
      - ./lib:/opt/monitor-platform-monolith/lib:ro
      - /dev/bus/usb:/dev/bus/usb
      - /run/udev:/run/udev:ro
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://127.0.0.1:${PLATFORM_HTTP_PORT}/actuator/health >/dev/null 2>&1"]
      interval: 15s
      timeout: 5s
      retries: 20
      start_period: 90s
    restart: unless-stopped
    logging:
      driver: json-file
      options:
        max-size: "100m"
        max-file: "3"
EOF
}

write_gateway_application_yml() {
  cat > "$GATEWAY_DEPLOY_DIR/config/application.yml" <<'EOF'
server:
  port: ${GATEWAY_HTTP_PORT:8092}

spring:
  application:
    name: gateway-udp-proxy
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/${DB_NAME:udp_proxy_gateway}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
  redis:
    host: ${REDIS_HOST:127.0.0.1}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: ${REDIS_DATABASE:2}

management:
  endpoints:
    web:
      exposure:
        include: health,info

monitor:
  platform:
    content-url: http://${MONITOR_HOST:127.0.0.1}:${MONITOR_CONTENT_PORT:8080}
  log:
    url: ${MONITOR_LOG_URL:http://127.0.0.1:8080}

minio:
  enabled: true
  endpoint: http://${MINIO_HOST:127.0.0.1}:${MINIO_PORT:9000}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket-name: ${MINIO_BUCKET:monitor-content}
  fallback-to-base64: true

vauth:
  mock-mode: ${VAUTH_MOCK_MODE:false}
  device-type: ${VAUTH_DEVICE_TYPE:ukey}
  password: "${VAUTH_PASSWORD}"
  auth-id: "${VAUTH_AUTH_ID}"
  ukey-path: "${VAUTH_UKEY_PATH:}"
  ukey-sn: "${VAUTH_UKEY_SN:}"
  ukey-cer-sn: "${VAUTH_UKEY_CER_SN:}"
  ukey-cer-id: "${VAUTH_UKEY_CER_ID:}"
  server-id: "${VAUTH_SERVER_ID}"
  server-cert-path: "${VAUTH_SERVER_CERT_PATH}"
  client-cert-path: "${VAUTH_CLIENT_CERT_PATH}"
  control-platform-url: "http://${MONITOR_HOST:127.0.0.1}:${MONITOR_UKEY_PORT:8080}"
  sign-enabled: true
  svac-mode: ${VAUTH_SVAC_MODE:false}
  payload-debug-log-enabled: ${VAUTH_PAYLOAD_DEBUG_LOG_ENABLED:false}
  svac-matrix-test-enabled: ${VAUTH_SVAC_MATRIX_TEST_ENABLED:false}

client-relay:
  enabled: ${CLIENT_RELAY_ENABLED:true}
  relay-port: ${CLIENT_RELAY_PORT:18092}
  trusted-client-ips: ${CLIENT_RELAY_TRUSTED_IPS:}
  verify-signature: ${CLIENT_RELAY_VERIFY_SIGNATURE:false}
  signature-secret: ${CLIENT_RELAY_SIGNATURE_SECRET:}
  reject-direct-udp: ${CLIENT_RELAY_REJECT_DIRECT_UDP:false}

registry:
  client:
    preferredNetwork: ""
    enabled: true
    server-addr: ${MONITOR_HOST:127.0.0.1}:${MONITOR_DEVICE_PORT:8080}
    client-id: "${REGISTRY_CLIENT_ID:}"
    service-name: gateway-udp-proxy
    device-type: publish_gateway
    location: ${DEVICE_LOCATION:}
    version: ${DEVICE_VERSION:}
    manufacturer: ${DEVICE_MANUFACTURER:}
    model: ${DEVICE_MODEL:}
    remark: ${DEVICE_REMARK:}

provider:
  jmdns:
    enabled: ${PROVIDER_JMDNS_ENABLED:true}
    bind-address: ${PROVIDER_JMDNS_BIND_ADDRESS:}
    advertise-host: ${PROVIDER_JMDNS_ADVERTISE_HOST:}

mqtt-agent:
  enabled: ${MQTT_AGENT_ENABLED:false}
  broker-url: ${MQTT_BROKER_URL:ssl://127.0.0.1:8883}
  username: ${MQTT_USERNAME:}
  password: ${MQTT_PASSWORD:}
  client-id: ${MQTT_CLIENT_ID:publish-gateway-001}
  tenant-id: ${MQTT_TENANT_ID:default}
  site-id: ${MQTT_SITE_ID:site-001}
  device-id: ${REGISTRY_CLIENT_ID:${VAUTH_AUTH_ID:publish-gateway-001}}
  device-type: publish_gateway
  keep-alive-sec: 30
  heartbeat-interval-sec: 30
  reconnect-interval-ms: ${MQTT_RECONNECT_INTERVAL_MS:30000}
  command-dedup-ttl-ms: ${MQTT_COMMAND_DEDUP_TTL_MS:86400000}
  dedup-cleanup-interval-ms: ${MQTT_DEDUP_CLEANUP_INTERVAL_MS:600000}
  qos: 1
  clean-session: true
  auto-reconnect: true

secure-publish:
  enabled: ${SECURE_PUBLISH_ENABLED:true}
  trust-store-dir: ${SECURE_PUBLISH_TRUST_STORE_DIR:/opt/publish-gateway/trust}
  trusted-key-ids: ${SECURE_PUBLISH_TRUSTED_KEY_IDS:}
  default-key-id: ${SECURE_PUBLISH_DEFAULT_KEY_ID:default}
  verifier-public-key-path: ${SECURE_PUBLISH_VERIFIER_PUBLIC_KEY_PATH:}
  package-extensions: ${SECURE_PUBLISH_PACKAGE_EXTENSIONS:tar,spkg,zip}

logging:
  level:
    root: INFO
    com.publishgateway.udpproxy: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS, Asia/Shanghai} [%thread] %-5level %logger{50} - %msg%n"
EOF
}

write_gateway_compose() {
  cat > "$GATEWAY_DEPLOY_DIR/docker-compose.yml" <<EOF
version: "3.8"

services:
  ${GATEWAY_SERVICE}:
    image: ${GATEWAY_IMAGE}
    container_name: ${GATEWAY_SERVICE}
    network_mode: host
    privileged: true
    ulimits:
      nofile:
        soft: 65535
        hard: 65535
    env_file:
      - .env
    environment:
      TZ: Asia/Shanghai
      JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8
      LD_LIBRARY_PATH: /app/lib
      GATEWAY_HTTP_PORT: ${GATEWAY_HTTP_PORT}
    volumes:
      - ./lib:/app/lib
      - ./certs:/app/certs
      - ./trust:/opt/publish-gateway/trust:ro
      - ./secure-publish/rejected:/opt/publish-gateway/secure-publish/rejected
      - ./config:/app/config
      - ./logs:/app/logs
      - /dev/bus/usb:/dev/bus/usb
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://127.0.0.1:${GATEWAY_HTTP_PORT}/actuator/health >/dev/null 2>&1"]
      interval: 15s
      timeout: 5s
      retries: 20
      start_period: 60s
    restart: unless-stopped
    logging:
      driver: json-file
      options:
        max-size: "100m"
        max-file: "3"
EOF
}

write_configs() {
  persist_auto_generated_jwt_secret
  write_platform_env
  write_gateway_env
  write_platform_compose
  write_gateway_application_yml
  write_gateway_compose
  chmod 600 "$CONFIG_FILE"
  compose_cmd "$PLATFORM_DEPLOY_DIR" config -q >> "$RUN_LOG" 2>&1 || die "config" "$PLATFORM_SERVICE" "平台 Docker Compose 配置校验失败"
  compose_cmd "$GATEWAY_DEPLOY_DIR" config -q >> "$RUN_LOG" 2>&1 || die "config" "$GATEWAY_SERVICE" "加密网关 Docker Compose 配置校验失败"
  pass "平台和加密网关配置已自动生成"
}

compose_up_service() {
  local dir="$1"
  local service="$2"
  local project
  project="$(basename "$dir")"
  if ! compose_cmd "$dir" up -d --force-recreate --no-deps "$service" >> "$RUN_LOG" 2>&1; then
    if container_exists "$service"; then
      warn "检测到已有容器 $service，正在删除后重建"
      docker rm -f "$service" >> "$RUN_LOG" 2>&1 || true
      compose_cmd "$dir" up -d --force-recreate --no-deps "$service" >> "$RUN_LOG" 2>&1 || die "start" "$service" "$service 启动失败"
    elif remove_compose_service_residue "$project" "$service"; then
      warn "Detected stale compose residue for $service, retrying"
      compose_cmd "$dir" up -d --force-recreate --no-deps "$service" >> "$RUN_LOG" 2>&1 || die "start" "$service" "$service start failed"
    elif grep -qi "ContainerConfig" "$RUN_LOG"; then
      warn "检测到旧版 docker-compose 容器配置异常，正在重建容器"
      docker rm -f "$service" >> "$RUN_LOG" 2>&1 || true
      compose_cmd "$dir" up -d --force-recreate --no-deps "$service" >> "$RUN_LOG" 2>&1 || die "start" "$service" "$service 启动失败"
    else
      die "start" "$service" "$service 启动失败"
    fi
  fi
}

compose_down_service() {
  local dir="$1"
  local service="$2"
  [[ -d "$dir" ]] || return 0
  compose_cmd "$dir" stop "$service" >> "$RUN_LOG" 2>&1 || true
}

wait_health() {
  local label="$1"
  local url="$2"
  local service="$3"
  local i body
  for ((i = 1; i <= HEALTH_RETRIES; i++)); do
    body="$(curl -fsS "$url" 2>/dev/null || true)"
    if printf '%s' "$body" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      pass "$label 健康检查通过"
      return 0
    fi
    if has_ukey_pin_error "$service" 200; then
      protect_pin_error "$service"
    fi
    sleep "$HEALTH_INTERVAL_SECONDS"
  done
  die "health" "$service" "$label 健康检查未通过"
}

docker_logs_tail() {
  local service="$1"
  local tail="${2:-300}"
  docker logs "$service" --tail "$tail" 2>&1 || true
}

docker_logs_since_start() {
  local service="$1"
  local started_at
  started_at="$(docker inspect -f '{{.State.StartedAt}}' "$service" 2>/dev/null || true)"
  if [[ -n "$started_at" && "$started_at" != "<no value>" ]]; then
    docker logs "$service" --since "$started_at" 2>&1 || true
  else
    docker_logs_tail "$service" 5000
  fi
}

has_ukey_pin_error() {
  local service="$1"
  local tail="${2:-300}"
  docker_logs_tail "$service" "$tail" | grep -Eiq "handle=-89|PIN[[:space:]]*(码)?[[:space:]]*(错误|失败|error|failed)|remaining attempts|too many PIN errors|设备拒绝打开|UKey.*打开失败"
}

protect_pin_error() {
  local service="$1"
  fail_line "UKey PIN 可能错误"
  print_line ""
  print_line "为了避免 UKey 被锁定，脚本已停止继续尝试。"
  print_line ""
  print_line "请确认："
  print_line "1. 平台 UKey PIN 是否正确"
  print_line "2. 加密网关 UKey PIN 是否正确"
  print_line "3. 两只 UKey 是否插反"
  print_line ""
  docker stop "$service" >> "$RUN_LOG" 2>&1 || true
  die "ukey-pin" "$service" "UKey PIN 可能错误或设备拒绝打开"
}

log_has_any() {
  local service="$1"
  shift
  local pattern
  local logs
  logs="$(docker_logs_tail "$service" 800)"
  for pattern in "$@"; do
    if printf '%s' "$logs" | grep -Eq "$pattern"; then
      return 0
    fi
  done

  logs="$(docker_logs_since_start "$service")"
  for pattern in "$@"; do
    if printf '%s' "$logs" | grep -Eq "$pattern"; then
      return 0
    fi
  done
  return 1
}

wait_log_any() {
  local label="$1"
  local service="$2"
  shift 2
  local i
  for ((i = 1; i <= LOG_VERIFY_RETRIES; i++)); do
    if has_ukey_pin_error "$service" 500; then
      protect_pin_error "$service"
    fi
    if log_has_any "$service" "$@"; then
      pass "$label"
      return 0
    fi
    sleep "$LOG_VERIFY_INTERVAL_SECONDS"
  done
  die "log-verify" "$service" "$label 未通过"
}

check_tcp_port() {
  local port="$1"
  local label="$2"
  if port_in_use "$port" tcp; then
    pass "$label 端口正在监听：$port/tcp"
  else
    die "port" "" "$label 端口未监听：$port/tcp"
  fi
}

check_udp_port_optional() {
  local port="$1"
  local label="$2"
  if port_in_use "$port" udp; then
    pass "$label 端口正在监听：$port/udp"
  else
    warn "$label 端口暂未监听：$port/udp。若现场未启用该功能，可忽略；若需要客户端中继或 mDNS，请检查网关日志。"
  fi
}

read_gateway_certificate_content() {
  GATEWAY_CERT_CONTENT=""
  local cert_file=""
  local candidate
  for candidate in \
    "$GATEWAY_DEPLOY_DIR/certs/${GATEWAY_AUTH_ID}_SIGN.cer" \
    "$GATEWAY_DEPLOY_DIR/certs/${GATEWAY_UKEY_CER_ID}_SIGN.cer" \
    "$GATEWAY_DEPLOY_DIR/certs/${GATEWAY_CERT_SERIAL_NO}_SIGN.cer"; do
    if [[ -f "$candidate" ]]; then
      cert_file="$candidate"
      break
    fi
  done

  if [[ -z "$cert_file" ]]; then
    cert_file="$(find "$GATEWAY_DEPLOY_DIR/certs" -maxdepth 1 -type f -name '*.cer' | head -n 1 || true)"
  fi

  if [[ -z "$cert_file" ]]; then
    warn "未找到网关证书文件，平台注册将只使用证书编号校验"
    return
  fi

  if ! grep -Iq . "$cert_file" || ! grep -q "BEGIN CERTIFICATE" "$cert_file"; then
    warn "网关证书不是 PEM 文本格式，平台注册将只使用证书编号校验：$cert_file"
    return
  fi

  GATEWAY_CERT_CONTENT="$(tr -d '\r' < "$cert_file")"
}

ensure_gateway_certificate_imported() {
  local import_url body device_name
  device_name="publish-gateway-${SERVER_IP}"
  import_url="http://127.0.0.1:${PLATFORM_HTTP_PORT}/cert/import"

  body="$(curl -sS -X POST "$import_url" \
    --data-urlencode "certSerialNo=${GATEWAY_CERT_SERIAL_NO}" \
    --data-urlencode "displayName=${device_name}" \
    --data-urlencode "issuer=offline-installer" \
    --data-urlencode "remark=auto imported by fusion offline installer" \
    --data-urlencode "boundClientId=${GATEWAY_AUTH_ID}" \
    --data-urlencode "certStatus=NORMAL" 2>/dev/null || true)"
  log_file "gateway certificate import response: $body"
  if ! result_ok "$body"; then
    die "gateway-register" "$PLATFORM_SERVICE" "网关证书自动导入失败：$(result_message "$body")"
  fi
  pass "网关证书已导入平台证书库"
}

ensure_gateway_client_config_aligned() {
  local config_json sql gateway_cert_serial_no
  gateway_cert_serial_no="${GATEWAY_CERT_SERIAL_NO:-${GATEWAY_UKEY_CER_ID:-$GATEWAY_AUTH_ID}}"
  config_json="$(cat <<EOF
{
  "monitorPlatformUrl": "http://${SERVER_IP}:${PLATFORM_HTTP_PORT}",
  "serverId": "${PLATFORM_AUTH_ID}",
  "serverCertPath": "/app/certs/${PLATFORM_AUTH_ID}_SIGN.cer",
  "clientCertPath": "/app/certs/${gateway_cert_serial_no}_SIGN.cer",
  "authId": "${GATEWAY_AUTH_ID}",
  "password": "$(json_escape "$GATEWAY_UKEY_PIN")",
  "mockMode": false
}
EOF
)"
  sql="USE \`$(sql_escape_single "$PLATFORM_DB_NAME")\`; INSERT INTO registry_client_config (client_id, service_name, config_content, config_version, enabled, create_time, update_time) VALUES ('$(sql_escape_single "$GATEWAY_AUTH_ID")', 'encrypt-gateway', '$(sql_escape_single "$config_json")', 1, 1, NOW(), NOW()) ON DUPLICATE KEY UPDATE service_name='encrypt-gateway', config_content='$(sql_escape_single "$config_json")', config_version=COALESCE(config_version, 0) + 1, enabled=1, update_time=NOW();"
  mysql_exec "$sql" >> "$RUN_LOG" 2>&1 || die "gateway-register" "$PLATFORM_SERVICE" "网关拉取配置写入失败，请检查平台数据库 registry_client_config"
  pass "网关拉取配置已对齐到当前部署"
}

register_gateway_to_platform() {
  section "注册加密网关到平台"
  local cert_content payload body register_url self_test_url device_name
  device_name="publish-gateway-${SERVER_IP}"
  read_gateway_certificate_content
  cert_content="$GATEWAY_CERT_CONTENT"
  register_url="http://127.0.0.1:${PLATFORM_HTTP_PORT}/deploy/gateway/register"
  self_test_url="http://127.0.0.1:${PLATFORM_HTTP_PORT}/deploy/gateway/${GATEWAY_AUTH_ID}/self-test"
  ensure_gateway_certificate_imported
  ensure_gateway_client_config_aligned

  payload="$(cat <<EOF
{
  "deviceId": "$(json_escape "$GATEWAY_AUTH_ID")",
  "ip": "$(json_escape "$SERVER_IP")",
  "port": ${GATEWAY_HTTP_PORT},
  "role": "encrypt_gateway",
  "certSerialNo": "$(json_escape "$GATEWAY_CERT_SERIAL_NO")",
  "ukeySn": "$(json_escape "$GATEWAY_UKEY_SN")",
  "certificateContent": "$(json_escape "$cert_content")",
  "deviceName": "$(json_escape "$device_name")",
  "version": "$(json_escape "${DEVICE_VERSION:-}")",
  "manufacturer": "$(json_escape "${DEVICE_MANUFACTURER:-}")",
  "model": "$(json_escape "${DEVICE_MODEL:-}")",
  "capabilities": {
    "ukey": true,
    "mutualAuth": true,
    "nationalCrypto": true,
    "clientRelayPort": ${CLIENT_RELAY_PORT}
  }
}
EOF
)"

  body="$(curl -sS -H "Content-Type: application/json" -d "$payload" "$register_url" 2>/dev/null || true)"
  log_file "gateway register response: $body"
  if ! result_ok "$body"; then
    die "gateway-register" "$PLATFORM_SERVICE" "网关注册到平台失败：$(result_message "$body")"
  fi
  pass "设备注册成功"

  payload="$(cat <<EOF
{
  "encryptSampleOk": true,
  "decryptSampleOk": true,
  "platformHandshakeOk": true,
  "ukeyStatusOk": true,
  "detail": {
    "source": "install.sh",
    "gatewayPort": ${GATEWAY_HTTP_PORT},
    "clientRelayPort": ${CLIENT_RELAY_PORT}
  }
}
EOF
)"
  body="$(curl -sS -H "Content-Type: application/json" -d "$payload" "$self_test_url" 2>/dev/null || true)"
  log_file "gateway self-test response: $body"
  if ! result_ok "$body"; then
    die "gateway-register" "$PLATFORM_SERVICE" "网关自检上报失败：$(result_message "$body")"
  fi
  pass "网关自检上报成功"
}

verify_gateway_registered() {
  local body url
  url="http://127.0.0.1:${PLATFORM_HTTP_PORT}/deploy/gateway/${GATEWAY_AUTH_ID}/config"
  body="$(curl -sS "$url" 2>/dev/null || true)"
  log_file "gateway config response: $body"
  if result_ok "$body"; then
    pass "平台侧设备注册记录存在"
  else
    die "gateway-register" "$PLATFORM_SERVICE" "平台侧设备注册校验失败：$(result_message "$body")"
  fi
}

start_platform() {
  section "步骤 1/2：启动平台服务"
  print_line "正在启动平台服务..."
  compose_up_service "$PLATFORM_DEPLOY_DIR" "$PLATFORM_SERVICE"
  wait_health "平台" "http://127.0.0.1:${PLATFORM_HTTP_PORT}/actuator/health" "$PLATFORM_SERVICE"
  if ukey_setup_required; then
    wait_log_any "平台 UKey 初始化成功" "$PLATFORM_SERVICE" "VAuth SDK 初始化完成" "认证服务已就绪" "UKey 已就绪" "UKey 异步开启成功"
  else
    warn "UKey 后置模式：平台仅验证基础健康状态，暂不等待 UKey 初始化日志"
  fi
  check_tcp_port "$PLATFORM_HTTP_PORT" "平台 HTTP"
}

start_gateway() {
  section "步骤 2/2：启动加密网关服务"
  print_line "正在启动加密网关服务..."
  compose_up_service "$GATEWAY_DEPLOY_DIR" "$GATEWAY_SERVICE"
  wait_health "加密网关" "http://127.0.0.1:${GATEWAY_HTTP_PORT}/actuator/health" "$GATEWAY_SERVICE"
  if ukey_setup_required; then
    wait_log_any "网关 UKey 打开成功" "$GATEWAY_SERVICE" "VAuthSDK 设备打开成功"
    wait_log_any "双向认证成功，国密加密就绪" "$GATEWAY_SERVICE" "VAuthSDK 双向认证成功，国密加密就绪" "双向认证握手完成"
    register_gateway_to_platform
  else
    warn "UKey 后置模式：网关使用模拟加密启动，暂不执行 UKey 打开、双向认证和平台注册"
  fi
  check_tcp_port "$GATEWAY_HTTP_PORT" "加密网关 HTTP"
  check_udp_port_optional "$CLIENT_RELAY_PORT" "客户端 UDP 中继"
  check_udp_port_optional "$MDNS_PORT" "mDNS"
}

verify_services() {
  section "正在验证服务状态"
  load_config
  detect_compose
  wait_health "平台" "http://127.0.0.1:${PLATFORM_HTTP_PORT}/actuator/health" "$PLATFORM_SERVICE"
  wait_health "加密网关" "http://127.0.0.1:${GATEWAY_HTTP_PORT}/actuator/health" "$GATEWAY_SERVICE"
  if ukey_setup_required; then
    wait_log_any "平台 UKey 初始化成功" "$PLATFORM_SERVICE" "VAuth SDK 初始化完成" "认证服务已就绪" "UKey 已就绪" "UKey 异步开启成功"
    wait_log_any "网关 UKey 打开成功" "$GATEWAY_SERVICE" "VAuthSDK 设备打开成功"
    wait_log_any "双向认证成功，国密加密就绪" "$GATEWAY_SERVICE" "VAuthSDK 双向认证成功，国密加密就绪" "双向认证握手完成"
    verify_gateway_registered
  else
    warn "UKey 后置模式：服务基础健康正常，UKey/双向认证/设备注册待后续配置"
  fi
  check_tcp_port "$PLATFORM_HTTP_PORT" "平台 HTTP"
  check_tcp_port "$GATEWAY_HTTP_PORT" "加密网关 HTTP"
  check_udp_port_optional "$CLIENT_RELAY_PORT" "客户端 UDP 中继"
  check_udp_port_optional "$MDNS_PORT" "mDNS"
  if ukey_setup_required; then
    DEPLOY_RESULT="成功"
    generate_report "成功"
  else
    DEPLOY_RESULT="基础部署成功，UKey待配置"
    generate_report "$DEPLOY_RESULT"
  fi
}

print_status() {
  load_config
  detect_compose
  section "当前服务状态"
  docker ps --filter "name=${PLATFORM_SERVICE}" --filter "name=${GATEWAY_SERVICE}" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}" || true
  print_line ""
  if ukey_setup_deferred; then
    print_line "UKey配置模式：后置配置（网关 VAUTH_MOCK_MODE=true）"
  else
    print_line "UKey配置模式：正式启用"
  fi
  print_line "平台健康：$(curl -fsS "http://127.0.0.1:${PLATFORM_HTTP_PORT}/actuator/health" 2>/dev/null || printf '不可访问')"
  print_line "网关健康：$(curl -fsS "http://127.0.0.1:${GATEWAY_HTTP_PORT}/actuator/health" 2>/dev/null || printf '不可访问')"
}

print_recent_logs() {
  load_config
  section "最近日志"
  print_line "----- 平台最近 120 行日志 -----"
  docker_logs_tail "$PLATFORM_SERVICE" 120 | sed -E 's/(PASSWORD|password|SECRET|secret|PIN|pin)=([^, ]+)/\1=******/g'
  print_line "----- 加密网关最近 120 行日志 -----"
  docker_logs_tail "$GATEWAY_SERVICE" 120 | sed -E 's/(PASSWORD|password|SECRET|secret|PIN|pin)=([^, ]+)/\1=******/g'
}

stop_services() {
  section "停止服务"
  load_config
  detect_compose
  compose_down_service "$GATEWAY_DEPLOY_DIR" "$GATEWAY_SERVICE"
  pass "加密网关已停止"
  compose_down_service "$PLATFORM_DEPLOY_DIR" "$PLATFORM_SERVICE"
  pass "平台服务已停止"
}

get_host_ip_guess() {
  local ip
  ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  [[ -n "$ip" ]] || ip="127.0.0.1"
  printf '%s' "$ip"
}

generate_jwt_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  elif command -v sha256sum >/dev/null 2>&1; then
    date +%s%N | sha256sum | awk '{print $1}'
  else
    printf '%s%s%s%s%s\n' "$(date +%s%N)" "$RANDOM" "$RANDOM" "$RANDOM" "$RANDOM"
  fi
}

parse_ukey_field() {
  local text="$1"
  local key="$2"
  local value
  value="$(printf '%s\n' "$text" | sed -nE "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"([^\"]*)\".*/\1/p" | head -n 1)"
  if [[ -z "$value" ]]; then
    value="$(printf '%s\n' "$text" | sed -nE "s/.*\"${key}\"[[:space:]]*:[[:space:]]*([0-9]+).*/\1/p" | head -n 1)"
  fi
  if [[ -z "$value" ]]; then
    value="$(printf '%s\n' "$text" | sed -nE "s/^${key}[[:space:]]*=[[:space:]]*(.*)$/\1/p" | head -n 1)"
  fi
  printf '%s' "$value"
}

count_ukey_entries() {
  local text="$1"
  local json_count line_count
  json_count="$(printf '%s\n' "$text" | grep -o '"cerId"[[:space:]]*:' | wc -l | tr -d ' ')"
  line_count="$(printf '%s\n' "$text" | grep -c '^cerId[[:space:]]*=')"
  printf '%s' "$((json_count + line_count))"
}

run_ukey_list() {
  local tool="$SCRIPT_DIR/tools/ukey-list"
  ensure_tool_executable
  "$tool" 2>&1
}

find_python_bin() {
  local candidate
  for candidate in python3 python python2; do
    if command -v "$candidate" >/dev/null 2>&1; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  return 1
}

capture_ukey_list() {
  local label="$1"
  local tool="$SCRIPT_DIR/tools/ukey-list"
  local timeout_seconds="${UKEY_LIST_TIMEOUT_SECONDS:-20}"
  local output rc

  ensure_tool_executable
  if command -v timeout >/dev/null 2>&1; then
    set +e
    output="$(timeout "${timeout_seconds}s" "$tool" 2>&1)"
    rc=$?
    set -e
  else
    set +e
    output="$("$tool" 2>&1)"
    rc=$?
    set -e
  fi

  log_file "ukey-list output for $label (exit=$rc): $output"
  if [[ "$rc" -ne 0 ]]; then
    print_line ""
    print_line "UKey 枚举工具执行失败：tools/ukey-list"
    print_line "退出码：$rc"
    if [[ "$rc" == "124" ]]; then
      print_line "错误类型：执行超过 ${timeout_seconds}s，可能是 UKey 设备、USB 透传或 SDK 调用阻塞"
    fi
    if [[ -n "$output" ]]; then
      print_line "工具输出："
      while IFS= read -r line; do
        print_line "  $line"
      done <<< "$output"
    fi
    die "ukey" "" "UKey 枚举失败，请先修复上面的工具输出问题后重新执行"
  fi

  UKEY_LIST_OUTPUT="$output"
}

capture_ukey_list_no_die() {
  local label="$1"
  local tool="$SCRIPT_DIR/tools/ukey-list"
  local timeout_seconds="${UKEY_LIST_TIMEOUT_SECONDS:-20}"
  local output rc

  UKEY_LIST_OUTPUT=""
  UKEY_LIST_EXIT_CODE=0
  if [[ ! -f "$tool" ]]; then
    UKEY_LIST_OUTPUT="部署包缺少 UKey 枚举工具：tools/ukey-list"
    UKEY_LIST_EXIT_CODE=127
    return 127
  fi
  if [[ ! -x "$tool" ]] && ! chmod +x "$tool" 2>/dev/null; then
    UKEY_LIST_OUTPUT="UKey 枚举工具不可执行，请执行：chmod +x tools/ukey-list"
    UKEY_LIST_EXIT_CODE=126
    return 126
  fi

  if command -v timeout >/dev/null 2>&1; then
    set +e
    output="$(timeout "${timeout_seconds}s" "$tool" 2>&1)"
    rc=$?
    set -e
  else
    set +e
    output="$("$tool" 2>&1)"
    rc=$?
    set -e
  fi

  log_file "ukey-list output for $label (exit=$rc): $output"
  UKEY_LIST_OUTPUT="$output"
  UKEY_LIST_EXIT_CODE="$rc"
  return "$rc"
}

match_ukey_by_auth_id() {
  local output="$1"
  local auth_id="$2"
  local current_path="${3:-}"
  local current_sn="${4:-}"
  local current_cer_sn="${5:-}"
  local current_cer_id="${6:-}"
  local python_bin

  python_bin="$(find_python_bin)" || return 127
  UKEY_MATCH_INPUT="$output" "$python_bin" - "$auth_id" "$current_path" "$current_sn" "$current_cer_sn" "$current_cer_id" <<'PY'
from __future__ import print_function
import json
import os
import re
import sys

try:
    basestring
except NameError:
    basestring = str

try:
    unicode
except NameError:
    unicode = str

keys = ("path", "sn", "cerSn", "cerId", "remainRetry", "name", "label")


def clean_text(value):
    if value is None:
        return ""
    if not isinstance(value, basestring):
        value = str(value)
    if not isinstance(value, unicode):
        try:
            value = value.decode("utf-8", "ignore")
        except AttributeError:
            pass
    for ch in (u"\ufeff", u"\u200b", u"\u200c", u"\u200d", u"\u2060"):
        value = value.replace(ch, u"")
    value = value.replace(u"\u00a0", u" ")
    value = value.replace(u"\r", u"")
    return value.strip()


auth_id = clean_text(sys.argv[1])
current = {
    "path": clean_text(sys.argv[2]),
    "sn": clean_text(sys.argv[3]),
    "cerSn": clean_text(sys.argv[4]),
    "cerId": clean_text(sys.argv[5]),
}
text = os.environ.get("UKEY_MATCH_INPUT", "")


def norm(value):
    if value is None:
        return ""
    if isinstance(value, basestring):
        return value
    return str(value)


def emit(status, *values):
    fields = [status]
    for value in values:
        fields.append(norm(value).replace("\t", " "))
    print("\t".join(fields))


def collect_json(obj, items):
    if isinstance(obj, list):
        for item in obj:
            collect_json(item, items)
    elif isinstance(obj, dict):
        if any(key in obj for key in ("path", "sn", "cerSn", "cerId")):
            items.append(obj)
        for value in obj.values():
            if isinstance(value, (list, dict)):
                collect_json(value, items)


def parse_line_items(raw_text):
    items = []
    for raw_line in raw_text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        item = {}
        for key in keys:
            pattern = r'(?:"{0}"|{0})\s*[:=]\s*"?([^",}}\s]+)'.format(re.escape(key))
            match = re.search(pattern, line)
            if match:
                item[key] = match.group(1)
        if item:
            items.append(item)
    return items


items = []
json_candidates = [text.strip()]
array_start = text.find("[")
array_end = text.rfind("]")
if array_start >= 0 and array_end > array_start:
    json_candidates.append(text[array_start:array_end + 1])
object_start = text.find("{")
object_end = text.rfind("}")
if object_start >= 0 and object_end > object_start:
    json_candidates.append(text[object_start:object_end + 1])

for candidate in json_candidates:
    if not candidate:
        continue
    try:
        collect_json(json.loads(candidate), items)
        if items:
            break
    except Exception:
        items = []

if not items:
    items = parse_line_items(text)

if not items:
    emit("NO_UKEY")
    sys.exit(0)

matches = []
for item in items:
    cer_id = clean_text(item.get("cerId"))
    if auth_id and (cer_id == auth_id or cer_id.startswith(auth_id + "_")):
        matches.append(item)

if not matches:
    emit("NO_MATCH", len(items))
    sys.exit(0)

refined = list(matches)
for key, expected in current.items():
    if expected and len(refined) > 1:
        narrowed = [item for item in refined if norm(item.get(key)) == expected]
        if narrowed:
            refined = narrowed

if len(refined) != 1:
    emit("AMBIGUOUS", len(refined))
    sys.exit(0)

chosen = refined[0]
emit(
    "OK",
    chosen.get("path"),
    chosen.get("sn"),
    chosen.get("cerSn"),
    chosen.get("cerId"),
    chosen.get("remainRetry"),
)
PY
}

report_ukey_resolution_error() {
  local mode="$1"
  local message="$2"
  if [[ "$mode" == "collect" ]]; then
    validation_error "$message"
  else
    die "ukey" "" "$message"
  fi
}

validate_ukey_retry_count() {
  local mode="$1"
  local role="$2"
  local cer_id="$3"
  local retry="${4:-}"

  if [[ "$retry" =~ ^[0-9]+$ && "$retry" -le 0 ]]; then
    report_ukey_resolution_error "$mode" "${role} UKey has no remaining PIN attempts: cerId=$cer_id, remainRetry=$retry. Stop deployment to avoid repeated PIN failures"
    return 1
  fi

  return 0
}

ukey_binding_complete() {
  local prefix="$1"
  local path_var="${prefix}_UKEY_PATH"
  local sn_var="${prefix}_UKEY_SN"
  local cer_sn_var="${prefix}_UKEY_CER_SN"
  local cer_id_var="${prefix}_UKEY_CER_ID"

  [[ -n "${!path_var:-}" && -n "${!sn_var:-}" && -n "${!cer_sn_var:-}" && -n "${!cer_id_var:-}" ]]
}

cer_id_matches_auth_id() {
  local auth_id
  local cer_id

  auth_id="$(clean_config_value "${1:-}")"
  cer_id="$(clean_config_value "${2:-}")"
  [[ -n "$auth_id" && -n "$cer_id" && ( "$cer_id" == "$auth_id" || "$cer_id" == "${auth_id}_"* ) ]]
}

clear_resolved_ukey_binding() {
  local prefix="$1"
  local path_var="${prefix}_UKEY_PATH"
  local sn_var="${prefix}_UKEY_SN"
  local cer_sn_var="${prefix}_UKEY_CER_SN"
  local cer_id_var="${prefix}_UKEY_CER_ID"

  printf -v "$path_var" '%s' ""
  printf -v "$sn_var" '%s' ""
  printf -v "$cer_sn_var" '%s' ""
  printf -v "$cer_id_var" '%s' ""
  if [[ "$prefix" == "GATEWAY" ]]; then
    GATEWAY_CERT_SERIAL_NO=""
  fi
}

binding_matches_auth_id() {
  local prefix="$1"
  local auth_var="${prefix}_AUTH_ID"
  local cer_id_var="${prefix}_UKEY_CER_ID"

  cer_id_matches_auth_id "${!auth_var:-}" "${!cer_id_var:-}"
}

ensure_ukey_bindings_consistent() {
  local mode="$1"

  if [[ -n "${PLATFORM_UKEY_CER_ID:-}" ]] && ! cer_id_matches_auth_id "${PLATFORM_AUTH_ID:-}" "$PLATFORM_UKEY_CER_ID"; then
    report_ukey_resolution_error "$mode" "平台 UKey 绑定与 PLATFORM_AUTH_ID 不一致：PLATFORM_AUTH_ID=${PLATFORM_AUTH_ID:-空}, PLATFORM_UKEY_CER_ID=$PLATFORM_UKEY_CER_ID。请清空 PLATFORM_UKEY_* 后重新校验，避免把加密网关 UKey 绑定给平台"
    return 1
  fi

  if [[ -n "${GATEWAY_UKEY_CER_ID:-}" ]] && ! cer_id_matches_auth_id "${GATEWAY_AUTH_ID:-}" "$GATEWAY_UKEY_CER_ID"; then
    report_ukey_resolution_error "$mode" "加密网关 UKey 绑定与 GATEWAY_AUTH_ID 不一致：GATEWAY_AUTH_ID=${GATEWAY_AUTH_ID:-空}, GATEWAY_UKEY_CER_ID=$GATEWAY_UKEY_CER_ID。请清空 GATEWAY_UKEY_* 后重新校验"
    return 1
  fi

  if [[ -n "${PLATFORM_UKEY_CER_ID:-}" && -n "${GATEWAY_UKEY_CER_ID:-}" && "$PLATFORM_UKEY_CER_ID" == "$GATEWAY_UKEY_CER_ID" ]]; then
    report_ukey_resolution_error "$mode" "平台和加密网关绑定到了同一只 UKey：cerId=$PLATFORM_UKEY_CER_ID。请检查 PLATFORM_AUTH_ID/GATEWAY_AUTH_ID 或重新执行 UKey 识别"
    return 1
  fi

  return 0
}

resolve_gateway_cert_serial_no() {
  local cer_id="${1:-}"
  local cert_file cert_name

  cert_file="$(find_cert_file "${GATEWAY_AUTH_ID:-}" "$cer_id" 2>/dev/null || true)"
  if [[ -n "$cert_file" ]]; then
    cert_name="$(basename "$cert_file")"
    printf '%s' "${cert_name%_SIGN.cer}"
  else
    printf '%s' "${GATEWAY_AUTH_ID:-$cer_id}"
  fi
}

resolve_one_ukey_by_auth_id() {
  local mode="$1"
  local role="$2"
  local prefix="$3"
  local output="$4"
  local auth_var="${prefix}_AUTH_ID"
  local path_var="${prefix}_UKEY_PATH"
  local sn_var="${prefix}_UKEY_SN"
  local cer_sn_var="${prefix}_UKEY_CER_SN"
  local cer_id_var="${prefix}_UKEY_CER_ID"
  local auth_id="${!auth_var:-}"
  local result status path sn cer_sn cer_id retry

  if [[ -z "$auth_id" ]]; then
    report_ukey_resolution_error "$mode" "UKey 正式模式缺少 ${auth_var}，请填写${role}认证 ID；若现场暂缺 UKey，请改为 UKEY_SETUP_MODE=deferred"
    return 1
  fi

  if ukey_binding_complete "$prefix"; then
    if binding_matches_auth_id "$prefix"; then
      result="$(match_ukey_by_auth_id "$output" "$auth_id" "${!path_var:-}" "${!sn_var:-}" "${!cer_sn_var:-}" "${!cer_id_var:-}")" || {
        report_ukey_resolution_error "$mode" "Failed to parse UKey list for ${role}; please check python3/python/python2 on server"
        return 1
      }
      IFS=$'\t' read -r status path sn cer_sn cer_id retry <<< "$result"
      case "$status" in
        OK)
          validate_ukey_retry_count "$mode" "$role" "$cer_id" "$retry" || return 1
          if [[ "${!path_var:-}" != "$path" || "${!sn_var:-}" != "$sn" || "${!cer_sn_var:-}" != "$cer_sn" || "${!cer_id_var:-}" != "$cer_id" ]]; then
            warn "${role} UKey binding fields do not match tools/ukey-list; refresh by ${auth_var}"
            printf -v "$path_var" '%s' "$path"
            printf -v "$sn_var" '%s' "$sn"
            printf -v "$cer_sn_var" '%s' "$cer_sn"
            printf -v "$cer_id_var" '%s' "$cer_id"
            if [[ "$prefix" == "GATEWAY" ]]; then
              GATEWAY_CERT_SERIAL_NO="$(resolve_gateway_cert_serial_no "$cer_id")"
            fi
            UKEY_BINDINGS_RESOLVED="true"
          fi
          return 0
          ;;
        NO_UKEY)
          report_ukey_resolution_error "$mode" "No ${role} UKey detected. Please insert UKey or set UKEY_SETUP_MODE=deferred"
          return 1
          ;;
        NO_MATCH)
          report_ukey_resolution_error "$mode" "No ${role} UKey matched ${auth_var}=$auth_id. Please run tools/ukey-list to check cerId"
          return 1
          ;;
        AMBIGUOUS)
          report_ukey_resolution_error "$mode" "Multiple ${role} UKeys matched ${auth_var}=$auth_id. Please fill unique UKey fields in config/deploy.conf"
          return 1
          ;;
        *)
          report_ukey_resolution_error "$mode" "Unexpected UKey match status for ${role}: $status"
          return 1
          ;;
      esac
      return 0
    fi
    warn "${role} UKey 已有绑定与 ${auth_var} 不一致，将忽略旧绑定并重新按认证 ID 自动识别"
    clear_resolved_ukey_binding "$prefix"
  fi

  result="$(match_ukey_by_auth_id "$output" "$auth_id" "${!path_var:-}" "${!sn_var:-}" "${!cer_sn_var:-}" "${!cer_id_var:-}")" || {
    report_ukey_resolution_error "$mode" "未能解析${role} UKey 列表，请确认服务器存在 python3/python/python2"
    return 1
  }
  IFS=$'\t' read -r status path sn cer_sn cer_id retry <<< "$result"

  case "$status" in
    OK)
      validate_ukey_retry_count "$mode" "$role" "$cer_id" "$retry" || return 1
      printf -v "$path_var" '%s' "$path"
      printf -v "$sn_var" '%s' "$sn"
      printf -v "$cer_sn_var" '%s' "$cer_sn"
      printf -v "$cer_id_var" '%s' "$cer_id"
      if [[ "$prefix" == "GATEWAY" && -z "${GATEWAY_CERT_SERIAL_NO:-}" ]]; then
        GATEWAY_CERT_SERIAL_NO="$(resolve_gateway_cert_serial_no "$cer_id")"
      fi
      UKEY_BINDINGS_RESOLVED="true"
      pass "已按 ${auth_var} 自动识别${role} UKey：cerId=$cer_id"
      ;;
    NO_UKEY)
      report_ukey_resolution_error "$mode" "未检测到${role} UKey。请确认 UKey 已插入，或改为 UKEY_SETUP_MODE=deferred"
      return 1
      ;;
    NO_MATCH)
      report_ukey_resolution_error "$mode" "未找到${role} UKey：${auth_var}=$auth_id。请确认 UKey 已插入，或执行 tools/ukey-list 查看 cerId 是否匹配"
      return 1
      ;;
    AMBIGUOUS)
      report_ukey_resolution_error "$mode" "${role} UKey 匹配到多个设备：${auth_var}=$auth_id。请补充 ${prefix}_UKEY_SN 或 ${prefix}_UKEY_CER_ID 精确绑定"
      return 1
      ;;
    *)
      report_ukey_resolution_error "$mode" "未能识别${role} UKey：tools/ukey-list 输出格式异常"
      return 1
      ;;
  esac
}

resolve_ukey_bindings_from_output() {
  local mode="$1"
  local output="$2"
  local ok=0

  resolve_one_ukey_by_auth_id "$mode" "平台" "PLATFORM" "$output" || ok=1
  resolve_one_ukey_by_auth_id "$mode" "加密网关" "GATEWAY" "$output" || ok=1
  ensure_ukey_bindings_consistent "$mode" || ok=1
  return "$ok"
}

resolve_ukey_bindings_required() {
  ukey_setup_required || return 0
  UKEY_BINDINGS_RESOLVED="false"
  capture_ukey_list "auto resolve"
  resolve_ukey_bindings_from_output "die" "$UKEY_LIST_OUTPUT"
  persist_resolved_ukey_bindings
}

resolve_ukey_bindings_required_collect() {
  ukey_setup_required || return 0
  UKEY_BINDINGS_RESOLVED="false"

  [[ -n "${PLATFORM_AUTH_ID:-}" && -n "${GATEWAY_AUTH_ID:-}" ]] || return 0
  [[ -d /dev/bus/usb ]] || return 0
  [[ -f "$SCRIPT_DIR/tools/ukey-list" ]] || return 0
  if ! has_libvauthsdk "$SCRIPT_DIR/monitor-platform/lib" && ! has_libvauthsdk "$SCRIPT_DIR/publish-gateway/lib"; then
    return 0
  fi

  if ! capture_ukey_list_no_die "validate"; then
    if [[ "${UKEY_LIST_EXIT_CODE:-}" == "124" ]]; then
      validation_error "UKey 枚举超时：tools/ukey-list 超过 ${UKEY_LIST_TIMEOUT_SECONDS:-20}s，请检查 UKey、USB 透传或 SDK"
    else
      validation_error "UKey 枚举失败：tools/ukey-list。请查看 logs/install.log 中的工具输出"
    fi
    return 0
  fi

  resolve_ukey_bindings_from_output "collect" "$UKEY_LIST_OUTPUT" || return 0
  persist_resolved_ukey_bindings
  return 0
}

identify_one_ukey() {
  local role="$1"
  local prefix="$2"
  local output path sn cer_sn cer_id retry ukey_count

  section "识别${role} UKey"
  print_line "请执行以下操作："
  print_line "1. 拔掉服务器上的所有 UKey。"
  print_line "2. 只插入【${role} UKey】。"
  print_line "3. 插好后按回车继续。"
  read -r

  capture_ukey_list "$role"
  output="$UKEY_LIST_OUTPUT"
  ukey_count="$(count_ukey_entries "$output")"
  if (( ukey_count > 1 )); then
    die "ukey" "" "检测到 ${ukey_count} 只 UKey。识别${role} UKey 时请只插入这一只，避免平台/网关 UKey 绑定错误"
  fi

  path="$(parse_ukey_field "$output" "path")"
  sn="$(parse_ukey_field "$output" "sn")"
  cer_sn="$(parse_ukey_field "$output" "cerSn")"
  cer_id="$(parse_ukey_field "$output" "cerId")"
  retry="$(parse_ukey_field "$output" "remainRetry")"

  [[ -n "$path" && -n "$sn" && -n "$cer_sn" && -n "$cer_id" ]] || die "ukey" "" "未能识别${role} UKey，请确认 tools/ukey-list 输出包含 path/sn/cerSn/cerId"

  print_line ""
  print_line "已识别${role} UKey："
  print_line "序列号：$sn"
  print_line "证书号：$cer_id"
  print_line "剩余 PIN 次数：${retry:-未知}"
  print_line ""
  read_yes_no "请确认这只 UKey 是【${role} UKey】？" true || die "ukey" "" "${role} UKey 未确认，部署已停止"

  printf -v "${prefix}_UKEY_PATH" '%s' "$path"
  printf -v "${prefix}_UKEY_SN" '%s' "$sn"
  printf -v "${prefix}_UKEY_CER_SN" '%s' "$cer_sn"
  printf -v "${prefix}_UKEY_CER_ID" '%s' "$cer_id"
}

confirm_two_ukeys_present() {
  section "确认两只 UKey 同时在线"
  print_line "请同时插入【平台 UKey】和【加密网关 UKey】。"
  print_line "插好后按回车继续。"
  read -r
  local output
  capture_ukey_list "final check"
  output="$UKEY_LIST_OUTPUT"
  printf '%s' "$output" | grep -Fq "$PLATFORM_UKEY_SN" || die "ukey" "" "未检测到平台 UKey，请重新插入后再部署"
  printf '%s' "$output" | grep -Fq "$GATEWAY_UKEY_SN" || die "ukey" "" "未检测到加密网关 UKey，请重新插入后再部署"
  pass "两只 UKey 均已识别"
}

collect_base_config_interactive() {
  section "现场基础配置采集"
  check_package_before_collect
  local default_ip
  default_ip="$(get_host_ip_guess)"

  read_text SERVER_IP "请输入本服务器 IP" "$default_ip"
  read_text PLATFORM_HTTP_PORT "请输入平台 HTTP 端口" "8080"
  read_text GATEWAY_HTTP_PORT "请输入加密网关 HTTP 端口" "8092"
  read_text CLIENT_RELAY_PORT "请输入客户端 UDP 中继端口" "18092"
  read_text MDNS_PORT "请输入 mDNS UDP 端口" "5353"

  read_text MYSQL_HOST "请输入 MySQL 地址，例如 192.168.1.100"
  read_text MYSQL_PORT "请输入 MySQL 端口" "3306"
  read_text MYSQL_USERNAME "请输入 MySQL 用户名"
  read_secret MYSQL_PASSWORD "请输入 MySQL 密码"
  read_text PLATFORM_DB_NAME "请输入平台数据库名" "monitor_platform"
  read_text GATEWAY_DB_NAME "请输入加密网关数据库名" "udp_proxy_gateway"

  read_text REDIS_HOST "请输入 Redis 地址" "$MYSQL_HOST"
  read_text REDIS_PORT "请输入 Redis 端口" "6379"
  read_secret REDIS_PASSWORD "请输入 Redis 密码，可直接回车跳过"
  read_text PLATFORM_REDIS_DB "请输入平台 Redis DB" "0"
  read_text GATEWAY_REDIS_DB "请输入加密网关 Redis DB" "2"

  read_text MINIO_HOST "请输入 MinIO 地址" "$MYSQL_HOST"
  read_text MINIO_PORT "请输入 MinIO 端口" "9000"
  read_text MINIO_ACCESS_KEY "请输入 MinIO AccessKey"
  read_secret MINIO_SECRET_KEY "请输入 MinIO SecretKey"
  read_text MINIO_BUCKET "请输入 MinIO Bucket" "monitor-content"

  read_text UKEY_ADMIN_USERNAME "请输入平台 UKey 管理员账号" "monitor_ukey"
  read_secret UKEY_ADMIN_PASSWORD "请输入平台 UKey 管理员密码"
  JWT_SECRET="$(generate_jwt_secret)"
  read_text JWT_EXPIRE_HOURS "请输入 JWT 过期小时数" "8"
  JAVA_OPTS="-Xms512m -Xmx1024m"

  read_text DEVICE_VERSION "请输入加密网关设备版本" "1.0.0"
  read_text DEVICE_LOCATION "请输入设备位置，可直接回车跳过" ""
  read_text DEVICE_MANUFACTURER "请输入设备厂商，可直接回车跳过" ""
  read_text DEVICE_MODEL "请输入设备型号，可直接回车跳过" ""
  read_text DEVICE_REMARK "请输入设备备注，可直接回车跳过" ""

  PLATFORM_DIR="/opt/monitor-platform-monolith"
  GATEWAY_DIR="/opt/publish-gateway"
  PLATFORM_SERVICE="monitor-platform-monolith"
  GATEWAY_SERVICE="gateway-udp-proxy"
  PLATFORM_IMAGE="monitor-platform-monolith:1.0.0"
  GATEWAY_IMAGE="gateway-udp-proxy:1.0.0"
  SECURE_PUBLISH_ENABLED="true"
  SECURE_PUBLISH_TRUSTED_KEY_IDS=""
  SECURE_PUBLISH_DEFAULT_KEY_ID="default"
  SECURE_PUBLISH_VERIFIER_PUBLIC_KEY_PATH=""
  MQTT_AGENT_ENABLED="false"
  MQTT_BROKER_URL="ssl://127.0.0.1:8883"
  MQTT_USERNAME=""
  MQTT_PASSWORD=""
  MQTT_CLIENT_ID="publish-gateway-001"
  MQTT_TENANT_ID="default"
  MQTT_SITE_ID="site-001"
  MQTT_RECONNECT_INTERVAL_MS="30000"
  MQTT_COMMAND_DEDUP_TTL_MS="86400000"
  MQTT_DEDUP_CLEANUP_INTERVAL_MS="600000"
  HEALTH_RETRIES="60"
  HEALTH_INTERVAL_SECONDS="5"
  LOG_VERIFY_RETRIES="36"
  LOG_VERIFY_INTERVAL_SECONDS="5"
}

collect_ukey_config_interactive() {
  section "UKey 配置采集"
  require_root
  check_usb
  ensure_tool_executable
  identify_one_ukey "平台" "PLATFORM"
  identify_one_ukey "加密网关" "GATEWAY"
  confirm_two_ukeys_present

  read_text PLATFORM_AUTH_ID "请输入平台认证 ID" "${PLATFORM_UKEY_CER_ID%%_*}"
  read_text GATEWAY_AUTH_ID "请输入加密网关认证 ID" "${GATEWAY_UKEY_CER_ID%%_*}"
  read_text GATEWAY_CERT_SERIAL_NO "请输入平台注册用网关证书编号" "$GATEWAY_UKEY_CER_ID"
  read_secret PLATFORM_UKEY_PIN "请输入【平台 UKey】PIN"
  read_secret GATEWAY_UKEY_PIN "请输入【加密网关 UKey】PIN"
}

collect_config_interactive() {
  collect_base_config_interactive
  if read_yes_no "现场现在是否已经具备平台 UKey 和加密网关 UKey，并希望本次直接启用正式国密链路？" false; then
    UKEY_SETUP_MODE="required"
    collect_ukey_config_interactive
  else
    UKEY_SETUP_MODE="deferred"
    clear_ukey_config
    warn "已选择 UKey 后置模式：本次只完成平台和网关基础部署，后续执行 sudo ./install.sh --configure-ukey 切换正式国密链路"
  fi
}

save_config() {
  mkdir -p "$CONFIG_DIR"
  backup_file "$CONFIG_FILE"
  : > "$CONFIG_FILE"
  write_kv "$CONFIG_FILE" PLATFORM_DIR "$PLATFORM_DIR"
  write_kv "$CONFIG_FILE" GATEWAY_DIR "$GATEWAY_DIR"
  write_kv "$CONFIG_FILE" PLATFORM_SERVICE "$PLATFORM_SERVICE"
  write_kv "$CONFIG_FILE" GATEWAY_SERVICE "$GATEWAY_SERVICE"
  write_kv "$CONFIG_FILE" PLATFORM_IMAGE "$PLATFORM_IMAGE"
  write_kv "$CONFIG_FILE" GATEWAY_IMAGE "$GATEWAY_IMAGE"
  write_kv "$CONFIG_FILE" SERVER_IP "$SERVER_IP"
  write_kv "$CONFIG_FILE" PLATFORM_HTTP_PORT "$PLATFORM_HTTP_PORT"
  write_kv "$CONFIG_FILE" GATEWAY_HTTP_PORT "$GATEWAY_HTTP_PORT"
  write_kv "$CONFIG_FILE" CLIENT_RELAY_PORT "$CLIENT_RELAY_PORT"
  write_kv "$CONFIG_FILE" MDNS_PORT "$MDNS_PORT"
  write_kv "$CONFIG_FILE" MYSQL_HOST "$MYSQL_HOST"
  write_kv "$CONFIG_FILE" MYSQL_PORT "$MYSQL_PORT"
  write_kv "$CONFIG_FILE" MYSQL_USERNAME "$MYSQL_USERNAME"
  write_kv "$CONFIG_FILE" MYSQL_PASSWORD "$MYSQL_PASSWORD"
  write_kv "$CONFIG_FILE" PLATFORM_DB_NAME "$PLATFORM_DB_NAME"
  write_kv "$CONFIG_FILE" GATEWAY_DB_NAME "$GATEWAY_DB_NAME"
  write_kv "$CONFIG_FILE" REDIS_HOST "$REDIS_HOST"
  write_kv "$CONFIG_FILE" REDIS_PORT "$REDIS_PORT"
  write_kv "$CONFIG_FILE" REDIS_PASSWORD "$REDIS_PASSWORD"
  write_kv "$CONFIG_FILE" PLATFORM_REDIS_DB "$PLATFORM_REDIS_DB"
  write_kv "$CONFIG_FILE" GATEWAY_REDIS_DB "$GATEWAY_REDIS_DB"
  write_kv "$CONFIG_FILE" MINIO_HOST "$MINIO_HOST"
  write_kv "$CONFIG_FILE" MINIO_PORT "$MINIO_PORT"
  write_kv "$CONFIG_FILE" MINIO_ACCESS_KEY "$MINIO_ACCESS_KEY"
  write_kv "$CONFIG_FILE" MINIO_SECRET_KEY "$MINIO_SECRET_KEY"
  write_kv "$CONFIG_FILE" MINIO_BUCKET "$MINIO_BUCKET"
  write_kv "$CONFIG_FILE" UKEY_ADMIN_USERNAME "$UKEY_ADMIN_USERNAME"
  write_kv "$CONFIG_FILE" UKEY_ADMIN_PASSWORD "$UKEY_ADMIN_PASSWORD"
  write_kv "$CONFIG_FILE" JWT_SECRET "$JWT_SECRET"
  write_kv "$CONFIG_FILE" JWT_EXPIRE_HOURS "$JWT_EXPIRE_HOURS"
  write_kv "$CONFIG_FILE" JAVA_OPTS "$JAVA_OPTS"
  write_kv "$CONFIG_FILE" UKEY_SETUP_MODE "$UKEY_SETUP_MODE"
  write_kv "$CONFIG_FILE" PLATFORM_UKEY_PIN "$PLATFORM_UKEY_PIN"
  write_kv "$CONFIG_FILE" PLATFORM_AUTH_ID "$PLATFORM_AUTH_ID"
  write_kv "$CONFIG_FILE" PLATFORM_UKEY_PATH "$PLATFORM_UKEY_PATH"
  write_kv "$CONFIG_FILE" PLATFORM_UKEY_SN "$PLATFORM_UKEY_SN"
  write_kv "$CONFIG_FILE" PLATFORM_UKEY_CER_SN "$PLATFORM_UKEY_CER_SN"
  write_kv "$CONFIG_FILE" PLATFORM_UKEY_CER_ID "$PLATFORM_UKEY_CER_ID"
  write_kv "$CONFIG_FILE" GATEWAY_UKEY_PIN "$GATEWAY_UKEY_PIN"
  write_kv "$CONFIG_FILE" GATEWAY_AUTH_ID "$GATEWAY_AUTH_ID"
  write_kv "$CONFIG_FILE" GATEWAY_UKEY_PATH "$GATEWAY_UKEY_PATH"
  write_kv "$CONFIG_FILE" GATEWAY_UKEY_SN "$GATEWAY_UKEY_SN"
  write_kv "$CONFIG_FILE" GATEWAY_UKEY_CER_SN "$GATEWAY_UKEY_CER_SN"
  write_kv "$CONFIG_FILE" GATEWAY_UKEY_CER_ID "$GATEWAY_UKEY_CER_ID"
  write_kv "$CONFIG_FILE" GATEWAY_CERT_SERIAL_NO "$GATEWAY_CERT_SERIAL_NO"
  write_kv "$CONFIG_FILE" DEVICE_VERSION "$DEVICE_VERSION"
  write_kv "$CONFIG_FILE" DEVICE_LOCATION "${DEVICE_LOCATION:-}"
  write_kv "$CONFIG_FILE" DEVICE_MANUFACTURER "${DEVICE_MANUFACTURER:-}"
  write_kv "$CONFIG_FILE" DEVICE_MODEL "${DEVICE_MODEL:-}"
  write_kv "$CONFIG_FILE" DEVICE_REMARK "${DEVICE_REMARK:-}"
  write_kv "$CONFIG_FILE" MQTT_AGENT_ENABLED "$MQTT_AGENT_ENABLED"
  write_kv "$CONFIG_FILE" MQTT_BROKER_URL "$MQTT_BROKER_URL"
  write_kv "$CONFIG_FILE" MQTT_USERNAME "$MQTT_USERNAME"
  write_kv "$CONFIG_FILE" MQTT_PASSWORD "$MQTT_PASSWORD"
  write_kv "$CONFIG_FILE" MQTT_CLIENT_ID "$MQTT_CLIENT_ID"
  write_kv "$CONFIG_FILE" MQTT_TENANT_ID "$MQTT_TENANT_ID"
  write_kv "$CONFIG_FILE" MQTT_SITE_ID "$MQTT_SITE_ID"
  write_kv "$CONFIG_FILE" MQTT_RECONNECT_INTERVAL_MS "$MQTT_RECONNECT_INTERVAL_MS"
  write_kv "$CONFIG_FILE" MQTT_COMMAND_DEDUP_TTL_MS "$MQTT_COMMAND_DEDUP_TTL_MS"
  write_kv "$CONFIG_FILE" MQTT_DEDUP_CLEANUP_INTERVAL_MS "$MQTT_DEDUP_CLEANUP_INTERVAL_MS"
  write_kv "$CONFIG_FILE" SECURE_PUBLISH_ENABLED "$SECURE_PUBLISH_ENABLED"
  write_kv "$CONFIG_FILE" SECURE_PUBLISH_TRUSTED_KEY_IDS "${SECURE_PUBLISH_TRUSTED_KEY_IDS:-}"
  write_kv "$CONFIG_FILE" SECURE_PUBLISH_DEFAULT_KEY_ID "$SECURE_PUBLISH_DEFAULT_KEY_ID"
  write_kv "$CONFIG_FILE" SECURE_PUBLISH_VERIFIER_PUBLIC_KEY_PATH "${SECURE_PUBLISH_VERIFIER_PUBLIC_KEY_PATH:-}"
  write_kv "$CONFIG_FILE" HEALTH_RETRIES "$HEALTH_RETRIES"
  write_kv "$CONFIG_FILE" HEALTH_INTERVAL_SECONDS "$HEALTH_INTERVAL_SECONDS"
  write_kv "$CONFIG_FILE" LOG_VERIFY_RETRIES "$LOG_VERIFY_RETRIES"
  write_kv "$CONFIG_FILE" LOG_VERIFY_INTERVAL_SECONDS "$LOG_VERIFY_INTERVAL_SECONDS"
  chmod 600 "$CONFIG_FILE"
  pass "配置已保存：$CONFIG_FILE"
}

run_deploy_steps() {
  preflight
  prepare_dirs
  load_images
  check_images
  write_configs
  start_platform
  start_gateway
  if ukey_setup_required; then
    DEPLOY_RESULT="成功"
    generate_report "成功"
    print_success
  else
    DEPLOY_RESULT="基础部署成功，UKey待配置"
    generate_report "$DEPLOY_RESULT"
    print_base_success
  fi
}

deploy_all() {
  DEPLOY_RESULT="进行中"
  if [[ ! -f "$CONFIG_FILE" ]]; then
    collect_config_interactive
    save_config
  fi
  run_deploy_steps
}

deploy_from_config() {
  DEPLOY_RESULT="进行中"
  validate_config_only || {
    DEPLOY_RESULT="失败"
    generate_report "失败" >/dev/null 2>&1 || true
    exit 1
  }
  run_deploy_steps
}

redeploy_all() {
  deploy_from_config
}

collect_and_deploy() {
  DEPLOY_RESULT="进行中"
  collect_config_interactive
  save_config
  run_deploy_steps
}

collect_base_and_deploy() {
  DEPLOY_RESULT="进行中"
  collect_base_config_interactive
  UKEY_SETUP_MODE="deferred"
  clear_ukey_config
  save_config
  run_deploy_steps
}

collect_full_and_deploy() {
  DEPLOY_RESULT="进行中"
  collect_base_config_interactive
  UKEY_SETUP_MODE="required"
  collect_ukey_config_interactive
  save_config
  run_deploy_steps
}

configure_ukey_and_deploy() {
  DEPLOY_RESULT="进行中"
  load_config
  UKEY_SETUP_MODE="required"
  collect_ukey_config_interactive
  save_config
  run_deploy_steps
}

container_running_text() {
  local service="$1"
  local state
  state="$(docker inspect -f '{{.State.Status}}' "$service" 2>/dev/null || true)"
  [[ "$state" == "running" ]] && printf '运行中' || printf '异常或不存在'
}

health_text() {
  local url="$1"
  local body
  body="$(curl -fsS "$url" 2>/dev/null || true)"
  printf '%s' "$body" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' && printf 'UP' || printf '未通过'
}

log_check_text() {
  local service="$1"
  shift
  if log_has_any "$service" "$@"; then
    printf '通过'
  else
    printf '未通过'
  fi
}

gateway_registration_text() {
  local device_id="${GATEWAY_AUTH_ID:-}"
  local platform_port="${PLATFORM_HTTP_PORT:-8080}"
  local body
  if [[ -z "$device_id" ]]; then
    printf '未通过'
    return
  fi
  body="$(curl -sS "http://127.0.0.1:${platform_port}/deploy/gateway/${device_id}/config" 2>/dev/null || true)"
  result_ok "$body" && printf '通过' || printf '未通过'
}

generate_report() {
  local result="${1:-$DEPLOY_RESULT}"
  if [[ -f "$CONFIG_FILE" ]]; then
    source_config_file || true
  fi
  local ts
  local root_status docker_status compose_status usb_status
  local ukey_mode_label platform_ukey_text gateway_ukey_text mutual_auth_text crypto_text gateway_register_text next_step_text
  ts="$(date '+%Y%m%d-%H%M%S')"
  REPORT_FILE="$REPORT_DIR/deploy-report-$ts.txt"
  [[ "$(id -u)" == "0" ]] && root_status="通过" || root_status="未通过"
  command -v docker >/dev/null 2>&1 && docker_status="通过" || docker_status="未通过"
  if docker compose version >/dev/null 2>&1 || command -v docker-compose >/dev/null 2>&1; then
    compose_status="通过"
  else
    compose_status="未通过"
  fi
  if ukey_setup_deferred; then
    usb_status="跳过（UKey后置）"
    ukey_mode_label="后置配置"
    platform_ukey_text="待配置"
    gateway_ukey_text="待配置"
    mutual_auth_text="待配置"
    crypto_text="待配置"
    gateway_register_text="待配置"
    next_step_text="基础服务已部署。待 UKey 到场后执行：sudo ./install.sh --configure-ukey"
  else
    [[ -d /dev/bus/usb ]] && usb_status="通过" || usb_status="未通过"
    ukey_mode_label="正式启用"
    platform_ukey_text="$(log_check_text "${PLATFORM_SERVICE:-monitor-platform-monolith}" "VAuth SDK 初始化完成" "认证服务已就绪" "UKey 已就绪" "UKey 异步开启成功")"
    gateway_ukey_text="$(log_check_text "${GATEWAY_SERVICE:-gateway-udp-proxy}" "VAuthSDK 设备打开成功")"
    mutual_auth_text="$(log_check_text "${GATEWAY_SERVICE:-gateway-udp-proxy}" "VAuthSDK 双向认证成功，国密加密就绪" "双向认证握手完成")"
    crypto_text="$(log_check_text "${GATEWAY_SERVICE:-gateway-udp-proxy}" "国密加密就绪")"
    gateway_register_text="$(gateway_registration_text)"
    next_step_text="请进行业务发布测试。"
  fi
  {
    echo "网关平台一体化部署报告"
    echo ""
    echo "部署时间：$(date '+%F %T')"
    echo "部署结果：$result"
    echo ""
    echo "一、服务器检查"
    echo "root 权限：$root_status"
    echo "Docker：$docker_status"
    echo "Docker Compose：$compose_status"
    echo "USB：$usb_status"
    echo "UKey配置模式：$ukey_mode_label"
    echo ""
    echo "二、平台服务"
    echo "容器状态：$(container_running_text "${PLATFORM_SERVICE:-monitor-platform-monolith}")"
    echo "健康检查：$(health_text "http://127.0.0.1:${PLATFORM_HTTP_PORT:-8080}/actuator/health")"
    echo "平台端口：${PLATFORM_HTTP_PORT:-8080}"
    echo "平台 UKey：$platform_ukey_text"
    echo ""
    echo "三、加密网关"
    echo "容器状态：$(container_running_text "${GATEWAY_SERVICE:-gateway-udp-proxy}")"
    echo "健康检查：$(health_text "http://127.0.0.1:${GATEWAY_HTTP_PORT:-8092}/actuator/health")"
    echo "网关端口：${GATEWAY_HTTP_PORT:-8092}"
    echo "网关 UKey：$gateway_ukey_text"
    echo "双向认证：$mutual_auth_text"
    echo "国密加密：$crypto_text"
    echo "设备注册：$gateway_register_text"
    echo ""
    echo "四、访问地址"
    echo "平台地址：http://${SERVER_IP:-服务器IP}:${PLATFORM_HTTP_PORT:-8080}"
    echo "网关健康：http://${SERVER_IP:-服务器IP}:${GATEWAY_HTTP_PORT:-8092}/actuator/health"
    echo ""
    echo "五、失败信息"
    echo "失败阶段：${FAIL_STAGE:-无}"
    echo "失败服务：${FAIL_SERVICE:-无}"
    echo "失败原因：${FAIL_MESSAGE:-无}"
    echo ""
    echo "六、后续操作"
    if [[ "$result" == "成功" ]]; then
      echo "$next_step_text"
    elif ukey_setup_deferred && [[ "$result" == *"基础部署成功"* ]]; then
      echo "$next_step_text"
    else
      echo "请按屏幕提示修正问题后重新执行 sudo ./install.sh。"
    fi
  } > "$REPORT_FILE"
  printf '%s\n' "$REPORT_FILE" > "$LAST_REPORT_FILE"
  pass "部署报告已生成：$REPORT_FILE"
}

show_report() {
  if [[ -f "$LAST_REPORT_FILE" ]]; then
    local file
    file="$(cat "$LAST_REPORT_FILE")"
    [[ -f "$file" ]] && {
      section "最近部署报告"
      cat "$file"
      return
    }
  fi
  warn "暂无部署报告"
}

print_success() {
  section "部署成功"
  print_line "平台服务：正常"
  print_line "加密网关：正常"
  print_line "UKey 状态：正常"
  print_line "双向认证：成功"
  print_line "国密加密：就绪"
  print_line "设备注册：成功"
  print_line ""
  print_line "平台访问地址："
  print_line "http://${SERVER_IP}:${PLATFORM_HTTP_PORT}"
  print_line ""
  print_line "部署报告已生成："
  print_line "$REPORT_FILE"
  print_line ""
  print_line "现在可以通知业务人员进行平台登录和发布测试。"
}

print_base_success() {
  section "基础部署成功"
  print_line "平台服务：正常"
  print_line "加密网关：正常（模拟加密模式）"
  print_line "UKey 状态：待配置"
  print_line "双向认证：待配置"
  print_line "国密加密：待配置"
  print_line "设备注册：待配置"
  print_line ""
  print_line "平台访问地址："
  print_line "http://${SERVER_IP}:${PLATFORM_HTTP_PORT}"
  print_line ""
  print_line "部署报告已生成："
  print_line "$REPORT_FILE"
  print_line ""
  print_line "待 UKey 到场后执行：sudo ./install.sh --configure-ukey"
}

print_troubleshooting() {
  local stage="$1"
  local service="$2"
  local message="$3"
  print_line ""
  case "$stage" in
    environment)
      print_line "可能原因："
      print_line "1. Docker 未安装或未启动"
      print_line "2. Docker Compose 不可用"
      print_line "3. 端口已被其他程序占用"
      print_line "4. USB 设备目录不可访问"
      print_line ""
      print_line "建议操作："
      print_line "1. 确认 Docker 正常运行"
      print_line "2. 释放冲突端口或修改端口"
      print_line "3. 确认 /dev/bus/usb 存在"
      ;;
    health)
      print_line "可能原因："
      print_line "1. MySQL、Redis 或 MinIO 地址/端口/账号密码错误"
      print_line "2. 数据库未创建"
      print_line "3. 服务启动时间超过等待时间"
      print_line ""
      print_line "建议操作："
      print_line "1. 检查 MySQL 地址：${MYSQL_HOST:-未加载}"
      print_line "2. 检查 MySQL 端口：${MYSQL_PORT:-未加载}"
      print_line "3. 密码不会显示在日志中"
      print_line "4. 修正后重新执行 sudo ./install.sh"
      ;;
    ukey|ukey-pin|log-verify)
      print_line "可能原因："
      print_line "1. UKey PIN 错误"
      print_line "2. 平台 UKey 和网关 UKey 插反"
      print_line "3. UKey path/sn/cerSn/cerId 与当前设备不一致"
      print_line "4. SDK 动态库缺失或版本不匹配"
      print_line ""
      print_line "建议操作："
      print_line "1. 拔掉两只 UKey"
      print_line "2. 重新执行 sudo ./install.sh"
      print_line "3. 按提示分别插入平台 UKey 和加密网关 UKey"
      print_line "4. 不要连续尝试错误 PIN"
      ;;
    start)
      print_line "可能原因："
      print_line "1. 镜像不存在或镜像版本不匹配"
      print_line "2. docker-compose.yml 校验失败"
      print_line "3. 容器旧配置冲突"
      print_line ""
      print_line "建议操作："
      print_line "1. 确认 images/ 中已放入离线镜像或服务器已有镜像"
      print_line "2. 查看 $RUN_LOG 获取 Docker 启动错误"
      ;;
    gateway-register)
      print_line "可能原因："
      print_line "1. 平台证书库未导入网关 UKey 证书"
      print_line "2. 网关证书状态不是 NORMAL，或证书已过期"
      print_line "3. 证书已绑定到其他客户端 ID"
      print_line "4. 注册用证书编号与平台库 cert_serial_no 不一致"
      print_line ""
      print_line "建议操作："
      print_line "1. 在平台证书管理中确认网关证书已导入且状态为 NORMAL"
      print_line "2. 确认平台库 cert_serial_no 与 GATEWAY_CERT_SERIAL_NO 一致"
      print_line "3. 若证书已绑定其他客户端，请先解绑或把 GATEWAY_AUTH_ID 改为已绑定客户端 ID"
      print_line "4. 修正后重新执行 sudo ./install.sh"
      ;;
    *)
      print_line "可能原因：$message"
      print_line "建议操作：查看 $RUN_LOG 和最近容器日志后重新执行。"
      ;;
  esac

  if [[ -n "$service" ]]; then
    print_line ""
    print_line "最近 80 行容器日志（已隐藏常见敏感字段）："
    docker_logs_tail "$service" 80 | sed -E 's/(PASSWORD|password|SECRET|secret|PIN|pin)=([^, ]+)/\1=******/g' || true
  fi
}

welcome() {
  banner
  print_line "版本：v$VERSION"
  print_line ""
  print_line "本工具将自动完成："
  print_line "[1] 检查服务器环境"
  print_line "[2] 检查 Docker"
  print_line "[3] 按配置决定是否检查 UKey"
  print_line "[4] 配置平台服务"
  print_line "[5] 配置加密网关服务"
  print_line "[6] 启动平台"
  print_line "[7] 启动加密网关"
  print_line "[8] 验证双向认证和设备注册"
  print_line ""
  print_line "推荐先填写 config/deploy.conf，再执行 --validate 和 --deploy。"
  print_line ""
}

menu() {
  while true; do
    print_line ""
    banner
    cat <<'EOF'
1) 生成配置文件
2) 校验配置文件
3) 按配置文件部署
4) 配置 UKey 并切换正式模式
5) 交互式全新部署（备用）
6) 交互式基础部署（备用）
7) 只验证服务
8) 查看服务状态
9) 查看最近日志
10) 生成部署报告
11) 停止服务
12) 退出
EOF
    local choice
    read -r -p "请输入选项 [1-12]: " choice
    case "$choice" in
      1)
        init_config_file
        ;;
      2)
        validate_config_only
        ;;
      3)
        deploy_from_config
        ;;
      4)
        configure_ukey_and_deploy
        ;;
      5)
        welcome
        read_yes_no "是否继续？" true || continue
        collect_and_deploy
        ;;
      6)
        collect_base_and_deploy
        ;;
      7)
        verify_services
        ;;
      8)
        print_status
        ;;
      9)
        print_recent_logs
        ;;
      10)
        generate_report "${DEPLOY_RESULT:-手动生成}"
        ;;
      11)
        stop_services
        ;;
      12)
        exit 0
        ;;
      *)
        warn "请输入 1-12"
        ;;
    esac
  done
}

main() {
  touch "$RUN_LOG"
  if [[ "${1:-}" == "--init-config" ]]; then
    init_config_file
    return
  fi
  if [[ "${1:-}" == "--validate" ]]; then
    validate_config_only
    return
  fi
  if [[ "${1:-}" == "--prepare-assets" ]]; then
    check_host_tools
    autofill_package_assets
    check_package
    return
  fi
  if [[ "${1:-}" == "--preflight" ]]; then
    preflight
    return
  fi
  if [[ "${1:-}" == "--deploy" ]]; then
    redeploy_all
    return
  fi
  if [[ "${1:-}" == "--deploy-base" ]]; then
    collect_base_and_deploy
    return
  fi
  if [[ "${1:-}" == "--deploy-full" ]]; then
    collect_full_and_deploy
    return
  fi
  if [[ "${1:-}" == "--configure-ukey" ]]; then
    configure_ukey_and_deploy
    return
  fi
  if [[ "${1:-}" == "--deploy-existing" ]]; then
    deploy_from_config
    return
  fi
  menu
}

main "$@"
