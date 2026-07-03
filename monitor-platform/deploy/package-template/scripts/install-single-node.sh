#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_DIR="."
INSTALL_DIR_OVERRIDE=""
DRY_RUN=false
SKIP_PORT_CHECK=false

ERRORS=0
WARNINGS=0

declare -A ENV
declare -a DOCKER_COMPOSE

log_info() {
  echo "[INFO] $*"
}

log_warn() {
  echo "[WARN] $*" >&2
  WARNINGS=$((WARNINGS + 1))
}

log_error() {
  echo "[ERROR] $*" >&2
  ERRORS=$((ERRORS + 1))
}

fail() {
  log_error "$*"
  exit 1
}

usage() {
  cat <<'USAGE'
Usage:
  bash scripts/install-single-node.sh [PACKAGE_DIR] [--dry-run] [--skip-port-check] [--install-dir DIR]

Stage 2 MVP deploys a single-node lightweight platform:
  MySQL + Redis + MinIO + monitor-platform-monolith.

It consumes deploy.env and devices.csv from PACKAGE_DIR. Example files are
accepted only in --dry-run mode.
USAGE
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run)
        DRY_RUN=true
        ;;
      --skip-port-check)
        SKIP_PORT_CHECK=true
        ;;
      --install-dir)
        [[ $# -ge 2 ]] || fail "--install-dir requires a value"
        INSTALL_DIR_OVERRIDE="$2"
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      -*)
        fail "unknown option: $1"
        ;;
      *)
        PACKAGE_DIR="$1"
        ;;
    esac
    shift
  done
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

strip_quotes() {
  local value="$1"
  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

load_env_file() {
  local env_file="$1"
  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local line key value
    line="$(trim "${raw_line%$'\r'}")"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" == *=* ]] || fail "invalid env line: $line"

    key="$(trim "${line%%=*}")"
    value="$(trim "${line#*=}")"
    value="$(strip_quotes "$value")"

    [[ "$key" =~ ^[A-Z0-9_]+$ ]] || fail "invalid env key: $key"
    ENV["$key"]="$value"
  done < "$env_file"
}

env_value() {
  local key="$1"
  printf '%s' "${ENV[$key]:-}"
}

env_default() {
  local key="$1"
  local default_value="$2"
  local value
  value="$(env_value "$key")"
  if [[ -n "$value" ]]; then
    printf '%s' "$value"
  else
    printf '%s' "$default_value"
  fi
}

require_env() {
  local key="$1"
  [[ -n "$(env_value "$key")" ]] || fail "missing required env: $key"
}

is_port() {
  local value="$1"
  [[ "$value" =~ ^[0-9]+$ ]] && (( value >= 1 && value <= 65535 ))
}

detect_files() {
  PACKAGE_DIR="$(cd "$PACKAGE_DIR" && pwd)"
  ENV_FILE="${PACKAGE_DIR}/deploy.env"
  CSV_FILE="${PACKAGE_DIR}/devices.csv"

  if [[ ! -f "$ENV_FILE" ]]; then
    if [[ "$DRY_RUN" == "true" && -f "${PACKAGE_DIR}/deploy.env.example" ]]; then
      ENV_FILE="${PACKAGE_DIR}/deploy.env.example"
      log_warn "using deploy.env.example because --dry-run is enabled"
    else
      fail "deploy.env not found: ${PACKAGE_DIR}/deploy.env"
    fi
  fi

  if [[ ! -f "$CSV_FILE" ]]; then
    if [[ "$DRY_RUN" == "true" && -f "${PACKAGE_DIR}/devices.csv.example" ]]; then
      CSV_FILE="${PACKAGE_DIR}/devices.csv.example"
      log_warn "using devices.csv.example because --dry-run is enabled"
    else
      fail "devices.csv not found: ${PACKAGE_DIR}/devices.csv"
    fi
  fi

  COMPOSE_TEMPLATE="${PACKAGE_DIR}/single-node/docker-compose.yml"
  [[ -f "$COMPOSE_TEMPLATE" ]] || fail "single-node compose template not found: $COMPOSE_TEMPLATE"
}

validate_stage1_inputs() {
  local validator="${SCRIPT_DIR}/validate-stage1-input.sh"
  [[ -f "$validator" ]] || fail "stage 1 validator not found: $validator"
  bash "$validator" "$PACKAGE_DIR"
}

validate_stage2_scope() {
  require_env DEPLOY_SCENARIO
  require_env PLATFORM_CODE
  require_env PLATFORM_HTTP_PORT
  require_env MYSQL_PORT
  require_env MYSQL_DATABASE
  require_env MYSQL_USERNAME
  require_env MYSQL_PASSWORD
  require_env MYSQL_ROOT_PASSWORD
  require_env REDIS_PORT
  require_env REDIS_PASSWORD
  require_env MINIO_ENDPOINT
  require_env MINIO_ROOT_USER
  require_env MINIO_ROOT_PASSWORD
  require_env MINIO_ACCESS_KEY
  require_env MINIO_SECRET_KEY
  require_env MINIO_BUCKET

  case "$(env_value DEPLOY_SCENARIO)" in
    lite_platform|one_to_many) ;;
    *)
      fail "stage 2 single-node MVP only supports DEPLOY_SCENARIO=lite_platform or one_to_many"
      ;;
  esac

  [[ "$(env_value PLATFORM_CODE)" == "monitor_platform_monolith" ]] \
    || fail "stage 2 single-node MVP requires PLATFORM_CODE=monitor_platform_monolith"

  is_port "$(env_value PLATFORM_HTTP_PORT)" || fail "invalid PLATFORM_HTTP_PORT"
  is_port "$(env_value MYSQL_PORT)" || fail "invalid MYSQL_PORT"
  is_port "$(env_value REDIS_PORT)" || fail "invalid REDIS_PORT"
}

endpoint_port() {
  local endpoint="$1"
  local host_port port
  host_port="${endpoint#*://}"
  host_port="${host_port%%/*}"
  if [[ "$host_port" == *:* ]]; then
    port="${host_port##*:}"
  else
    port="9000"
  fi
  is_port "$port" || fail "cannot parse MINIO endpoint port: $endpoint"
  printf '%s' "$port"
}

detect_docker_compose() {
  command -v docker >/dev/null 2>&1 || fail "docker command not found"
  docker info >/dev/null 2>&1 || fail "docker daemon is not available"

  if docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker-compose)
  else
    fail "docker compose plugin or docker-compose command not found"
  fi
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
  log_warn "ss/netstat not found; skip host port check for $port"
  return 1
}

check_ports() {
  [[ "$SKIP_PORT_CHECK" == "true" ]] && {
    log_warn "host port check skipped by --skip-port-check"
    return
  }

  local minio_api_port minio_console_port ports port
  minio_api_port="$(endpoint_port "$(env_value MINIO_ENDPOINT)")"
  minio_console_port="$(env_default MINIO_CONSOLE_PORT "19002")"
  is_port "$minio_console_port" || fail "invalid MINIO_CONSOLE_PORT: $minio_console_port"

  ports=(
    "$(env_value PLATFORM_HTTP_PORT)"
    "$(env_value MYSQL_PORT)"
    "$(env_value REDIS_PORT)"
    "$minio_api_port"
    "$minio_console_port"
  )

  for port in "${ports[@]}"; do
    if port_listening "$port"; then
      fail "host port is already in use: $port"
    fi
  done
}

load_image_archives() {
  local image_dir loaded archive
  image_dir="${PACKAGE_DIR}/$(env_default IMAGE_DIR images)"
  [[ -d "$image_dir" ]] || return

  shopt -s nullglob
  loaded=false
  for archive in "$image_dir"/*.tar "$image_dir"/*.tar.gz "$image_dir"/*.tgz; do
    loaded=true
    log_info "loading image archive: $archive"
    docker load -i "$archive"
  done
  shopt -u nullglob

  [[ "$loaded" == "true" ]] || log_info "no docker image archives found under: $image_dir"
}

check_or_pull_image() {
  local image="$1"
  if docker image inspect "$image" >/dev/null 2>&1; then
    log_info "image exists: $image"
    return
  fi

  if [[ "$(env_default PULL_MISSING_IMAGES false)" == "true" ]]; then
    log_warn "image missing, pulling: $image"
    docker pull "$image"
    return
  fi

  fail "docker image not found: $image. Put image tar files under images/ or set PULL_MISSING_IMAGES=true."
}

check_images() {
  [[ "$DRY_RUN" == "true" ]] && {
    log_info "dry-run enabled; skip docker image presence checks"
    return
  }

  load_image_archives
  check_or_pull_image "$(env_default MYSQL_IMAGE mysql:5.7)"
  check_or_pull_image "$(env_default REDIS_IMAGE redis:latest)"
  check_or_pull_image "$(env_default MINIO_IMAGE minio/minio:latest)"
  check_or_pull_image "$(env_default MINIO_MC_IMAGE minio/mc:latest)"
  check_or_pull_image "$(env_default MONOLITH_IMAGE monitor-platform-monolith:1.0.0)"
}

generate_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
    return
  fi
  tr -dc 'A-Za-z0-9' </dev/urandom | head -c 64
}

existing_env_value() {
  local file="$1"
  local key="$2"
  [[ -f "$file" ]] || return
  grep -E "^${key}=" "$file" | tail -n 1 | cut -d= -f2-
}

write_runtime_env() {
  local target_env="$1"
  local minio_api_port minio_console_port jwt_secret

  minio_api_port="$(endpoint_port "$(env_value MINIO_ENDPOINT)")"
  minio_console_port="$(env_default MINIO_CONSOLE_PORT "19002")"
  jwt_secret="$(env_value JWT_SECRET)"
  if [[ -z "$jwt_secret" ]]; then
    jwt_secret="$(existing_env_value "$target_env" JWT_SECRET || true)"
  fi
  if [[ -z "$jwt_secret" ]]; then
    jwt_secret="$(generate_secret)"
  fi

  cat > "$target_env" <<EOF
COMPOSE_PROJECT_NAME=monitor-platform-single-node

MYSQL_IMAGE=$(env_default MYSQL_IMAGE mysql:5.7)
REDIS_IMAGE=$(env_default REDIS_IMAGE redis:latest)
MINIO_IMAGE=$(env_default MINIO_IMAGE minio/minio:latest)
MINIO_MC_IMAGE=$(env_default MINIO_MC_IMAGE minio/mc:latest)
MONOLITH_IMAGE=$(env_default MONOLITH_IMAGE monitor-platform-monolith:1.0.0)

SERVER_PORT=$(env_value PLATFORM_HTTP_PORT)
MYSQL_HOST=127.0.0.1
MYSQL_PORT=$(env_value MYSQL_PORT)
MYSQL_DATABASE=$(env_value MYSQL_DATABASE)
MYSQL_DB=$(env_value MYSQL_DATABASE)
MYSQL_USERNAME=$(env_value MYSQL_USERNAME)
MYSQL_PASSWORD=$(env_value MYSQL_PASSWORD)
MYSQL_ROOT_PASSWORD=$(env_value MYSQL_ROOT_PASSWORD)

REDIS_HOST=127.0.0.1
REDIS_PORT=$(env_value REDIS_PORT)
REDIS_PASSWORD=$(env_value REDIS_PASSWORD)
REDIS_DB=$(env_default REDIS_DB 0)

MINIO_ENDPOINT=$(env_value MINIO_ENDPOINT)
MINIO_API_PORT=${minio_api_port}
MINIO_CONSOLE_PORT=${minio_console_port}
MINIO_ROOT_USER=$(env_value MINIO_ROOT_USER)
MINIO_ROOT_PASSWORD=$(env_value MINIO_ROOT_PASSWORD)
MINIO_ACCESS_KEY=$(env_value MINIO_ACCESS_KEY)
MINIO_SECRET_KEY=$(env_value MINIO_SECRET_KEY)
MINIO_BUCKET=$(env_value MINIO_BUCKET)

UKEY_ADMIN_USERNAME=$(env_value UKEY_ADMIN_USERNAME)
UKEY_ADMIN_PASSWORD=$(env_value UKEY_ADMIN_PASSWORD)

VAUTH_SERVER_MODE=$(env_default VAUTH_SERVER_MODE ukey)
VAUTH_SERVER_REQUIRED=$(env_default VAUTH_SERVER_REQUIRED true)
VAUTH_SERVER_PASSWORD=$(env_value VAUTH_SERVER_PASSWORD)
VAUTH_SERVER_AUTH_ID=$(env_value VAUTH_SERVER_AUTH_ID)
VAUTH_SERVER_UKEY_PATH=$(env_value VAUTH_SERVER_UKEY_PATH)
VAUTH_SERVER_UKEY_SN=$(env_value VAUTH_SERVER_UKEY_SN)
VAUTH_SERVER_UKEY_CER_SN=$(env_value VAUTH_SERVER_UKEY_CER_SN)
VAUTH_SERVER_UKEY_CER_ID=$(env_value VAUTH_SERVER_UKEY_CER_ID)

JWT_SECRET=${jwt_secret}
JWT_EXPIRE_HOURS=$(env_default JWT_EXPIRE_HOURS 8)
JAVA_OPTS=$(env_default JAVA_OPTS "-Xms512m -Xmx1024m")
EOF
  chmod 600 "$target_env"
}

prepare_runtime_dir() {
  local install_dir cert_dir sdk_dir source_csv
  install_dir="${INSTALL_DIR_OVERRIDE:-$(env_default INSTALL_DIR /opt/monitor-platform-single-node)}"
  INSTALL_DIR="$install_dir"

  mkdir -p "$INSTALL_DIR"/{config,logs,lib,certs,data/mysql,data/redis,data/minio}
  cp "$COMPOSE_TEMPLATE" "$INSTALL_DIR/docker-compose.yml"

  sdk_dir="${PACKAGE_DIR}/$(env_default SDK_LIB_DIR sdk/lib)"
  if [[ -d "$sdk_dir" ]]; then
    cp -a "$sdk_dir"/. "$INSTALL_DIR/lib"/
  else
    log_warn "SDK library directory not found: $sdk_dir"
  fi

  cert_dir="${PACKAGE_DIR}/$(env_default CERT_DIR certs)"
  if [[ -d "$cert_dir" ]]; then
    cp -a "$cert_dir"/. "$INSTALL_DIR/certs"/
  else
    log_warn "certificate directory not found: $cert_dir"
  fi

  source_csv="$CSV_FILE"
  cp "$source_csv" "$INSTALL_DIR/devices.csv"
  write_runtime_env "$INSTALL_DIR/.env"
}

validate_compose_config() {
  (
    cd "$INSTALL_DIR"
    "${DOCKER_COMPOSE[@]}" -f docker-compose.yml config -q
  )
}

deploy_stack() {
  if [[ "$DRY_RUN" == "true" ]]; then
    log_info "dry-run enabled; skip docker compose up"
    return
  fi

  (
    cd "$INSTALL_DIR"
    "${DOCKER_COMPOSE[@]}" -f docker-compose.yml up -d monitor-platform-mysql monitor-platform-redis monitor-platform-minio
    "${DOCKER_COMPOSE[@]}" -f docker-compose.yml run --rm monitor-platform-minio-init
    "${DOCKER_COMPOSE[@]}" -f docker-compose.yml up -d monitor-platform-monolith
  )
}

wait_health() {
  [[ "$DRY_RUN" == "true" ]] && return

  local url retries interval i
  url="http://127.0.0.1:$(env_value PLATFORM_HTTP_PORT)/actuator/health"
  retries="$(env_default HEALTH_RETRIES 40)"
  interval="$(env_default HEALTH_INTERVAL_SECONDS 5)"

  log_info "waiting for platform health: $url"
  for ((i = 1; i <= retries; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log_info "platform health check passed"
      return
    fi
    sleep "$interval"
  done

  fail "platform health check failed: $url"
}

print_summary() {
  echo ""
  echo "Stage 2 single-node MVP summary"
  echo "  package dir : $PACKAGE_DIR"
  echo "  install dir : $INSTALL_DIR"
  echo "  platform    : http://$(env_value PLATFORM_HOST):$(env_value PLATFORM_HTTP_PORT)"
  echo "  health      : http://127.0.0.1:$(env_value PLATFORM_HTTP_PORT)/actuator/health"
  echo "  warnings    : $WARNINGS"
  echo ""
}

main() {
  parse_args "$@"
  detect_files
  log_info "package dir: $PACKAGE_DIR"
  log_info "env file: $ENV_FILE"
  log_info "csv file: $CSV_FILE"

  load_env_file "$ENV_FILE"
  validate_stage1_inputs
  validate_stage2_scope
  detect_docker_compose
  check_ports
  check_images
  prepare_runtime_dir
  validate_compose_config
  deploy_stack
  wait_health
  print_summary
}

main "$@"
