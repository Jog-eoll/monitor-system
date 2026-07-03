#!/usr/bin/env bash

set -euo pipefail

STAGE2_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$STAGE2_DIR/.." && pwd)"
CONFIG_FILE="${CONFIG_FILE:-$DEPLOY_DIR/deploy.conf}"
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
DRY_RUN=false
CHECK_ONLY=false
NON_INTERACTIVE=false
REPORT_ONLY=false
CURRENT_STAGE="initializing"
REPORT_WRITTEN=false

# shellcheck disable=SC1091
source "$STAGE2_DIR/lib/common.sh"
# shellcheck disable=SC1091
source "$STAGE2_DIR/lib/env.sh"
# shellcheck disable=SC1091
source "$STAGE2_DIR/lib/preflight.sh"
# shellcheck disable=SC1091
source "$STAGE2_DIR/lib/compose.sh"
# shellcheck disable=SC1091
source "$STAGE2_DIR/lib/health.sh"
# shellcheck disable=SC1091
source "$STAGE2_DIR/lib/report.sh"
# shellcheck disable=SC1091
source "$STAGE2_DIR/lib/account.sh"
# shellcheck disable=SC1091
source "$STAGE2_DIR/lib/ctl.sh"

usage() {
  cat <<'USAGE'
Usage:
  bash stage2/install.sh [--config FILE] [--check] [--dry-run] [--yes] [--report]

Options:
  --config FILE          Use a deploy.conf-compatible config file.
  --check                Run preflight checks and render .env only.
  --dry-run              Validate and render without changing firewall or starting containers.
  --yes                  Non-interactive mode; fail on port conflicts.
  --auto-install-docker  Allow automatic Docker installation when missing.
  --no-auto-firewall     Do not open firewall ports automatically.
  --report               Generate a deployment report without starting containers.
  --init-config          Top-level install.sh option: generate deploy.conf from stage2/deploy.conf.example.
  --legacy               Top-level install.sh option: run the old deployment script.
USAGE
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --config)
        [[ $# -ge 2 ]] || fail "--config requires a value"
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
      --report)
        REPORT_ONLY=true
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        fail "unknown option: $1"
        ;;
    esac
    shift
  done
}

write_report_once() {
  local result="$1"
  local stage="${2:-$CURRENT_STAGE}"
  [[ "$REPORT_WRITTEN" == "true" ]] && return
  REPORT_WRITTEN=true
  write_stage2_report "$result" "$stage"
}

on_exit() {
  local rc=$?
  if (( rc != 0 )) && [[ "$REPORT_WRITTEN" != "true" ]]; then
    write_stage2_report "failed" "$CURRENT_STAGE" || true
  fi
}

main() {
  trap on_exit EXIT
  parse_args "$@"
  log_info "stage2 deploy dir: $DEPLOY_DIR"
  log_info "stage2 config: $CONFIG_FILE"

  CURRENT_STAGE="load config"
  load_stage2_config
  CURRENT_STAGE="auto generate passwords"
  auto_generate_passwords
  if [[ "$CHECK_ONLY" == "true" || "$DRY_RUN" == "true" || "$REPORT_ONLY" == "true" ]]; then
    AUTO_OPEN_FIREWALL=false
  fi
  CURRENT_STAGE="validate config"
  validate_stage2_config
  CURRENT_STAGE="check OS resources"
  check_os_resources
  CURRENT_STAGE="check Docker Compose"
  check_docker_compose
  CURRENT_STAGE="resolve port conflicts"
  resolve_port_conflicts
  CURRENT_STAGE="check firewall ports"
  check_firewall_ports
  CURRENT_STAGE="check delivery files"
  check_stage2_delivery_files
  CURRENT_STAGE="render env"
  write_stage2_env
  CURRENT_STAGE="validate compose"
  validate_compose

  if [[ "$REPORT_ONLY" == "true" ]]; then
    write_report_once "report-only" "$CURRENT_STAGE"
    exit 0
  fi

  if [[ "$CHECK_ONLY" == "true" || "$DRY_RUN" == "true" ]]; then
    write_report_once "checked" "$CURRENT_STAGE"
    log_success "stage2 check completed"
    exit 0
  fi

  CURRENT_STAGE="prepare runtime dirs"
  prepare_stage2_runtime_dirs
  CURRENT_STAGE="generate EMQX certs"
  generate_emqx_certs
  CURRENT_STAGE="check images"
  check_images_stage2
  CURRENT_STAGE="start infra services"
  start_infra_services
  CURRENT_STAGE="check infra health"
  check_infra_health
  CURRENT_STAGE="run MinIO init"
  run_minio_init
  CURRENT_STAGE="check MinIO init"
  check_minio_init
  CURRENT_STAGE="start business services"
  start_business_services
  CURRENT_STAGE="check actuator health"
  check_actuator_health
  CURRENT_STAGE="check Nacos registrations"
  check_nacos_registrations

  CURRENT_STAGE="generate account file"
  write_account_file "$DEPLOY_DIR"
  CURRENT_STAGE="generate ctl.sh"
  write_ctl_script "$DEPLOY_DIR"

  write_report_once "success" "$CURRENT_STAGE"
  log_success "stage2 single-node deployment completed"
}

main "$@"
