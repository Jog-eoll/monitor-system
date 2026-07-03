#!/usr/bin/env bash

set -euo pipefail

PACKAGE_DIR="${1:-.}"
ENV_FILE="${PACKAGE_DIR}/deploy.env"
CSV_FILE="${PACKAGE_DIR}/devices.csv"

if [[ ! -f "$ENV_FILE" && -f "${PACKAGE_DIR}/deploy.env.example" ]]; then
  ENV_FILE="${PACKAGE_DIR}/deploy.env.example"
fi

if [[ ! -f "$CSV_FILE" && -f "${PACKAGE_DIR}/devices.csv.example" ]]; then
  CSV_FILE="${PACKAGE_DIR}/devices.csv.example"
fi

ERRORS=0
WARNINGS=0

declare -A ENV
declare -A DEVICE_IDS
declare -A DEVICE_TYPE
declare -A DEVICE_TYPE_COUNT
declare -A DEVICE_IP
declare -A DEVICE_PARENT

fail() {
  echo "[ERROR] $*" >&2
  ERRORS=$((ERRORS + 1))
}

warn() {
  echo "[WARN] $*" >&2
  WARNINGS=$((WARNINGS + 1))
}

info() {
  echo "[INFO] $*"
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
  if [[ ! -f "$ENV_FILE" ]]; then
    fail "deploy.env not found: $ENV_FILE"
    return
  fi

  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local line
    line="$(trim "$raw_line")"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ "$line" != *=* ]]; then
      fail "invalid env line: $line"
      continue
    fi

    local key value
    key="$(trim "${line%%=*}")"
    value="$(trim "${line#*=}")"
    value="$(strip_quotes "$value")"

    if [[ ! "$key" =~ ^[A-Z0-9_]+$ ]]; then
      fail "invalid env key: $key"
      continue
    fi
    ENV["$key"]="$value"
  done < "$ENV_FILE"
}

env_value() {
  local key="$1"
  printf '%s' "${ENV[$key]:-}"
}

require_env() {
  local key="$1"
  if [[ -z "$(env_value "$key")" ]]; then
    fail "missing required env: $key"
  fi
}

is_port() {
  local value="$1"
  [[ "$value" =~ ^[0-9]+$ ]] && (( value >= 1 && value <= 65535 ))
}

is_ipv4() {
  local value="$1"
  [[ "$value" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || return 1
  local IFS=.
  local -a parts
  read -r -a parts <<< "$value"
  local part
  for part in "${parts[@]}"; do
    (( part >= 0 && part <= 255 )) || return 1
  done
}

is_common_port() {
  case "$1" in
    22|80|3306|6379) return 0 ;;
    *) return 1 ;;
  esac
}

validate_password() {
  local key="$1"
  local value
  value="$(env_value "$key")"
  [[ -z "$value" ]] && return

  if (( ${#value} < 8 )); then
    fail "$key must be at least 8 characters"
  fi
  if [[ ! "$value" =~ [A-Za-z] ]]; then
    fail "$key must contain a letter"
  fi
  if [[ ! "$value" =~ [0-9] ]]; then
    fail "$key must contain a digit"
  fi
  if [[ ! "$value" =~ [^A-Za-z0-9] ]]; then
    fail "$key must contain a special character"
  fi
  if [[ "$value" =~ (YOUR_|_HERE|CHANGE_ME|TODO|123456|admin123456|root123456|password|PASSWORD) ]]; then
    fail "$key looks like a placeholder or weak password"
  fi
}

validate_username() {
  local key="$1"
  local value
  value="$(env_value "$key")"
  [[ -z "$value" ]] && return

  local lower
  lower="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
  case "$lower" in
    root|admin|guest|test|user|administrator|superuser|default|anonymous)
      fail "$key uses forbidden username: $value"
      ;;
  esac
}

validate_env() {
  local required=(
    DEPLOY_SCENARIO PLATFORM_CODE PLATFORM_HOST PLATFORM_HTTP_PORT
    MYSQL_HOST MYSQL_PORT MYSQL_DATABASE MYSQL_USERNAME MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
    REDIS_HOST REDIS_PORT REDIS_PASSWORD
    NACOS_HOST NACOS_PORT NACOS_NAMESPACE
    MINIO_ENDPOINT MINIO_ROOT_USER MINIO_ROOT_PASSWORD MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_BUCKET
    UKEY_ADMIN_USERNAME UKEY_ADMIN_PASSWORD
    VAUTH_SERVER_MODE VAUTH_SERVER_PASSWORD VAUTH_SERVER_AUTH_ID
    CERT_DIR IMAGE_DIR SDK_LIB_DIR
  )

  local key
  for key in "${required[@]}"; do
    require_env "$key"
  done

  case "$(env_value DEPLOY_SCENARIO)" in
    full_platform|lite_platform|one_to_one|one_to_many) ;;
    *) fail "DEPLOY_SCENARIO must be one of: full_platform, lite_platform, one_to_one, one_to_many" ;;
  esac

  case "$(env_value PLATFORM_CODE)" in
    monitor_platform|monitor_platform_monolith) ;;
    *) fail "PLATFORM_CODE must be monitor_platform or monitor_platform_monolith" ;;
  esac

  if ! is_ipv4 "$(env_value PLATFORM_HOST)"; then
    fail "PLATFORM_HOST must be an IPv4 address"
  fi

  local port_keys=(
    PLATFORM_HTTP_PORT PLATFORM_HTTPS_PORT PLATFORM_API_GATEWAY_PORT MONOLITH_PORT
    MYSQL_PORT REDIS_PORT NACOS_PORT SENTINEL_PORT
  )
  for key in "${port_keys[@]}"; do
    local value
    value="$(env_value "$key")"
    [[ -z "$value" ]] && continue
    if ! is_port "$value"; then
      fail "$key must be a valid port: $value"
      continue
    fi
    if [[ "$(env_value FORBID_COMMON_HOST_PORTS)" == "true" ]] && is_common_port "$value"; then
      fail "$key uses forbidden common host port: $value"
    fi
  done

  validate_username MYSQL_USERNAME
  validate_username MINIO_ROOT_USER
  validate_username UKEY_ADMIN_USERNAME

  validate_password MYSQL_PASSWORD
  validate_password MYSQL_ROOT_PASSWORD
  validate_password REDIS_PASSWORD
  validate_password MINIO_ROOT_PASSWORD
  validate_password MINIO_SECRET_KEY
  validate_password UKEY_ADMIN_PASSWORD
  validate_password VAUTH_SERVER_PASSWORD

  local cert_dir sdk_dir image_dir
  cert_dir="${PACKAGE_DIR}/$(env_value CERT_DIR)"
  sdk_dir="${PACKAGE_DIR}/$(env_value SDK_LIB_DIR)"
  image_dir="${PACKAGE_DIR}/$(env_value IMAGE_DIR)"
  [[ -d "$cert_dir" ]] || fail "certificate directory not found: $cert_dir"
  [[ -d "$sdk_dir" ]] || fail "SDK library directory not found: $sdk_dir"
  [[ -d "$image_dir" ]] || warn "image directory not found: $image_dir"
}

allowed_device_type() {
  case "$1" in
    monitor_platform|monitor_platform_monolith|publish_gateway|terminal_encrypt_gateway|publish_server|info_board|content_server|ops_agent|portainer)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

allowed_role() {
  case "$1" in
    control|encrypt|decrypt|source|display|content|ops|"")
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

require_field() {
  local line_no="$1"
  local field_name="$2"
  local value="$3"
  if [[ -z "$value" ]]; then
    fail "devices.csv line $line_no missing $field_name"
  fi
}

cert_exists() {
  local cert_file="$1"
  [[ -z "$cert_file" ]] && return 1
  [[ -f "${PACKAGE_DIR}/$(env_value CERT_DIR)/${cert_file}" ]]
}

validate_csv() {
  if [[ ! -f "$CSV_FILE" ]]; then
    fail "devices.csv not found: $CSV_FILE"
    return
  fi

  local expected_header
  expected_header="deviceId,deviceType,role,ip,httpPort,sshPort,sshUser,certFile,certSerialNo,ukeySn,authId,parentDeviceId,screenDeviceId,screenIp,screenPort,remark"

  local header
  IFS= read -r header < "$CSV_FILE" || true
  header="${header%$'\r'}"
  if [[ "$header" != "$expected_header" ]]; then
    fail "devices.csv header mismatch"
    echo "expected: $expected_header" >&2
    echo "actual:   $header" >&2
    return
  fi

  local line_no=1
  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    line_no=$((line_no + 1))
    raw_line="${raw_line%$'\r'}"
    [[ -z "$(trim "$raw_line")" ]] && continue

    local deviceId deviceType role ip httpPort sshPort sshUser certFile certSerialNo ukeySn authId parentDeviceId screenDeviceId screenIp screenPort remark extra
    IFS=',' read -r deviceId deviceType role ip httpPort sshPort sshUser certFile certSerialNo ukeySn authId parentDeviceId screenDeviceId screenIp screenPort remark extra <<< "$raw_line"

    if [[ -n "${extra:-}" ]]; then
      fail "devices.csv line $line_no has too many columns"
      continue
    fi

    deviceId="$(trim "$deviceId")"
    deviceType="$(trim "$deviceType")"
    role="$(trim "$role")"
    ip="$(trim "$ip")"
    httpPort="$(trim "$httpPort")"
    sshPort="$(trim "$sshPort")"
    certFile="$(trim "$certFile")"
    ukeySn="$(trim "$ukeySn")"
    authId="$(trim "$authId")"
    parentDeviceId="$(trim "$parentDeviceId")"
    screenDeviceId="$(trim "$screenDeviceId")"
    screenIp="$(trim "$screenIp")"
    screenPort="$(trim "$screenPort")"

    require_field "$line_no" deviceId "$deviceId"
    require_field "$line_no" deviceType "$deviceType"
    require_field "$line_no" ip "$ip"

    if [[ -n "$deviceId" ]]; then
      if [[ -n "${DEVICE_IDS[$deviceId]:-}" ]]; then
        fail "duplicate deviceId in devices.csv: $deviceId"
      fi
      DEVICE_IDS["$deviceId"]="1"
      DEVICE_IP["$deviceId"]="$ip"
      DEVICE_TYPE["$deviceId"]="$deviceType"
      DEVICE_PARENT["$deviceId"]="$parentDeviceId"
    fi

    if [[ -n "$deviceType" ]]; then
      if ! allowed_device_type "$deviceType"; then
        fail "devices.csv line $line_no invalid deviceType: $deviceType"
      fi
      DEVICE_TYPE_COUNT["$deviceType"]=$(( ${DEVICE_TYPE_COUNT[$deviceType]:-0} + 1 ))
    fi

    if ! allowed_role "$role"; then
      fail "devices.csv line $line_no invalid role: $role"
    fi

    if ! is_ipv4 "$ip"; then
      fail "devices.csv line $line_no invalid ip: $ip"
    fi

    if [[ -n "$httpPort" ]]; then
      if ! is_port "$httpPort"; then
        fail "devices.csv line $line_no invalid httpPort: $httpPort"
      elif [[ "$(env_value FORBID_COMMON_HOST_PORTS)" == "true" ]] && is_common_port "$httpPort"; then
        fail "devices.csv line $line_no uses forbidden httpPort: $httpPort"
      fi
    fi

    if [[ -n "$sshPort" ]]; then
      if ! is_port "$sshPort"; then
        fail "devices.csv line $line_no invalid sshPort: $sshPort"
      elif [[ "$sshPort" == "22" && "$(env_value ALLOW_SSH_PORT_22)" != "true" ]]; then
        fail "devices.csv line $line_no uses sshPort 22 but ALLOW_SSH_PORT_22 is not true"
      fi
    fi

    case "$deviceType" in
      monitor_platform)
        [[ "$role" == "control" ]] || fail "devices.csv line $line_no monitor_platform role must be control"
        ;;
      monitor_platform_monolith)
        [[ "$role" == "control" ]] || fail "devices.csv line $line_no monitor_platform_monolith role must be control"
        ;;
      publish_gateway)
        [[ "$role" == "encrypt" ]] || fail "devices.csv line $line_no publish_gateway role must be encrypt"
        require_field "$line_no" httpPort "$httpPort"
        require_field "$line_no" certFile "$certFile"
        require_field "$line_no" ukeySn "$ukeySn"
        require_field "$line_no" authId "$authId"
        if [[ -n "$certFile" ]] && ! cert_exists "$certFile"; then
          fail "devices.csv line $line_no certificate not found: ${PACKAGE_DIR}/$(env_value CERT_DIR)/${certFile}"
        fi
        ;;
      terminal_encrypt_gateway)
        [[ "$role" == "decrypt" ]] || fail "devices.csv line $line_no terminal_encrypt_gateway role must be decrypt"
        require_field "$line_no" httpPort "$httpPort"
        require_field "$line_no" certFile "$certFile"
        require_field "$line_no" ukeySn "$ukeySn"
        require_field "$line_no" authId "$authId"
        require_field "$line_no" parentDeviceId "$parentDeviceId"
        require_field "$line_no" screenDeviceId "$screenDeviceId"
        require_field "$line_no" screenIp "$screenIp"
        require_field "$line_no" screenPort "$screenPort"
        if [[ -n "$certFile" ]] && ! cert_exists "$certFile"; then
          fail "devices.csv line $line_no certificate not found: ${PACKAGE_DIR}/$(env_value CERT_DIR)/${certFile}"
        fi
        if [[ -n "$screenIp" ]] && ! is_ipv4 "$screenIp"; then
          fail "devices.csv line $line_no invalid screenIp: $screenIp"
        fi
        if [[ -n "$screenPort" ]] && ! is_port "$screenPort"; then
          fail "devices.csv line $line_no invalid screenPort: $screenPort"
        fi
        ;;
      publish_server)
        [[ "$role" == "source" ]] || fail "devices.csv line $line_no publish_server role must be source"
        ;;
      info_board)
        [[ "$role" == "display" ]] || fail "devices.csv line $line_no info_board role must be display"
        ;;
      content_server)
        [[ "$role" == "content" ]] || fail "devices.csv line $line_no content_server role must be content"
        ;;
    esac
  done < <(tail -n +2 "$CSV_FILE")
}

validate_topology() {
  local scenario
  scenario="$(env_value DEPLOY_SCENARIO)"

  case "$scenario" in
    full_platform)
      (( ${DEVICE_TYPE_COUNT[monitor_platform]:-0} >= 1 )) || fail "full_platform requires monitor_platform in devices.csv"
      ;;
    lite_platform)
      (( ${DEVICE_TYPE_COUNT[monitor_platform_monolith]:-0} >= 1 )) || fail "lite_platform requires monitor_platform_monolith in devices.csv"
      ;;
    one_to_one)
      (( ${DEVICE_TYPE_COUNT[publish_gateway]:-0} == 1 )) || fail "one_to_one requires exactly one publish_gateway"
      (( ${DEVICE_TYPE_COUNT[terminal_encrypt_gateway]:-0} == 1 )) || fail "one_to_one requires exactly one terminal_encrypt_gateway"
      ;;
    one_to_many)
      (( ${DEVICE_TYPE_COUNT[monitor_platform_monolith]:-0} == 1 )) || fail "one_to_many requires exactly one monitor_platform_monolith"
      (( ${DEVICE_TYPE_COUNT[publish_gateway]:-0} == 1 )) || fail "one_to_many requires exactly one publish_gateway"
      (( ${DEVICE_TYPE_COUNT[terminal_encrypt_gateway]:-0} >= 1 )) || fail "one_to_many requires at least one terminal_encrypt_gateway"

      local platform_id="" publish_id="" id
      for id in "${!DEVICE_TYPE[@]}"; do
        [[ "${DEVICE_TYPE[$id]}" == "monitor_platform_monolith" ]] && platform_id="$id"
        [[ "${DEVICE_TYPE[$id]}" == "publish_gateway" ]] && publish_id="$id"
      done

      if [[ -n "$platform_id" && -n "$publish_id" && "${DEVICE_IP[$platform_id]:-}" != "${DEVICE_IP[$publish_id]:-}" ]]; then
        fail "one_to_many requires monitor_platform_monolith and publish_gateway on the same host"
      fi

      local terminal_id parent_id
      for terminal_id in "${!DEVICE_TYPE[@]}"; do
        [[ "${DEVICE_TYPE[$terminal_id]}" == "terminal_encrypt_gateway" ]] || continue
        parent_id="${DEVICE_PARENT[$terminal_id]:-}"
        if [[ -z "$parent_id" ]]; then
          fail "one_to_many terminal gateway $terminal_id missing parentDeviceId"
          continue
        fi
        if [[ -z "${DEVICE_IDS[$parent_id]:-}" ]]; then
          fail "one_to_many terminal gateway $terminal_id parentDeviceId does not exist: $parent_id"
          continue
        fi
        if [[ "${DEVICE_TYPE[$parent_id]:-}" != "publish_gateway" ]]; then
          fail "one_to_many terminal gateway $terminal_id parentDeviceId must reference publish_gateway: $parent_id"
        fi
      done
      ;;
  esac
}

main() {
  info "validating stage 1 deployment inputs in: $PACKAGE_DIR"
  info "env file: $ENV_FILE"
  info "csv file: $CSV_FILE"

  load_env_file
  validate_env
  validate_csv
  validate_topology

  if (( ERRORS > 0 )); then
    echo "[FAIL] validation failed: errors=$ERRORS warnings=$WARNINGS" >&2
    exit 1
  fi

  echo "[OK] validation passed: warnings=$WARNINGS"
}

main "$@"
