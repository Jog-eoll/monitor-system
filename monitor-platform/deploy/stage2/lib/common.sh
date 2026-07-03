#!/usr/bin/env bash

log_info() {
  echo "[INFO] $*"
}

log_success() {
  echo "[OK] $*"
}

log_warn() {
  echo "[WARN] $*" >&2
}

log_error() {
  echo "[ERROR] $*" >&2
}

fail() {
  log_error "$*"
  exit 1
}

as_bool() {
  case "${1:-}" in
    true|TRUE|1|yes|YES|y|Y) return 0 ;;
    *) return 1 ;;
  esac
}

require_command() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 || fail "command not found: $command_name"
}

is_port() {
  local value="$1"
  [[ "$value" =~ ^[0-9]+$ ]] && (( value >= 1 && value <= 65535 ))
}

dedupe_ports() {
  local seen="" port
  for port in "$@"; do
    [[ -z "$port" ]] && continue
    if [[ " $seen " != *" $port "* ]]; then
      printf '%s\n' "$port"
      seen="$seen $port"
    fi
  done
}
