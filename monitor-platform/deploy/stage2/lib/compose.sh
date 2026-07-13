#!/usr/bin/env bash

public_images=(
  "nacos/nacos-server:v2.2.0"
  "mysql:5.7"
  "redis:latest"
  "minio/minio:latest"
  "minio/mc:latest"
  "nginx:alpine"
  "emqx/emqx:5.4.0"
)

private_images=(
  "monitor-platform-device:latest"
  "monitor-platform-ukey:latest"
  "monitor-platform-alarm:latest"
  "monitor-platform-rule:latest"
  "monitor-platform-content:latest"
  "monitor-platform-forward:latest"
  "monitor-platform-role:latest"
  "monitor-platform-registry-server:latest"
  "monitor-platform-gateway:latest"
  "monitor-platform-websocket:latest"
  "monitor-platform-log:latest"
)

private_image_services=(
  "monitor-platform-device:latest monitor-device"
  "monitor-platform-ukey:latest monitor-ukey"
  "monitor-platform-alarm:latest monitor-alarm"
  "monitor-platform-rule:latest monitor-rule"
  "monitor-platform-content:latest monitor-content"
  "monitor-platform-forward:latest monitor-forward"
  "monitor-platform-role:latest monitor-role"
  "monitor-platform-registry-server:latest monitor-registry-server"
  "monitor-platform-gateway:latest monitor-gateway"
  "monitor-platform-websocket:latest monitor-websocket"
  "monitor-platform-log:latest monitor-log"
)

infra_services=(db redis minio nacos emqx)
business_services=(
  monitor-device monitor-ukey monitor-rule monitor-websocket monitor-log
  monitor-forward monitor-role monitor-registry-server monitor-content monitor-alarm
)
edge_services=(monitor-gateway nginx)
UPDATED_MONITOR_SERVICES=()

compose_cmd() {
  (cd "$DEPLOY_DIR" && "${DOCKER_COMPOSE[@]}" -f "$COMPOSE_FILE" "$@")
}

prepare_stage2_runtime_dirs() {
  mkdir -p \
    "$DEPLOY_DIR/mysql/data" \
    "$DEPLOY_DIR/mysql/conf" \
    "$DEPLOY_DIR/mysql/init" \
    "$DEPLOY_DIR/nacos/logs" \
    "$DEPLOY_DIR/minio/data1" \
    "$DEPLOY_DIR/minio/data2" \
    "$DEPLOY_DIR/minio/config" \
    "$DEPLOY_DIR/nginx/conf" \
    "$DEPLOY_DIR/nginx/logs" \
    "$DEPLOY_DIR/nginx/certs" \
    "$DEPLOY_DIR/sdk/lib" \
    "$DEPLOY_DIR/emqx/data" \
    "$DEPLOY_DIR/emqx/log" \
    "$DEPLOY_DIR/emqx/etc/certs"
  # EMQX container runs as UID 1000; grant write access to data/log dirs
  chown -R 1000:1000 "$DEPLOY_DIR/emqx/data" "$DEPLOY_DIR/emqx/log" 2>/dev/null || true
  chmod -R 775 "$DEPLOY_DIR/emqx/data" "$DEPLOY_DIR/emqx/log" 2>/dev/null || true
}

generate_emqx_certs() {
  local cert_dir="$DEPLOY_DIR/emqx/etc/certs"
  if [[ -f "$cert_dir/cert.pem" && -f "$cert_dir/key.pem" && -f "$cert_dir/cacert.pem" ]]; then
    log_info "EMQX TLS certificates already exist"
    return
  fi
  if ! command -v openssl >/dev/null 2>&1; then
    log_warn "openssl not found; EMQX MQTTS will not work until certificates are placed in $cert_dir"
    return
  fi
  log_info "generating self-signed TLS certificates for EMQX"
  openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout "$cert_dir/key.pem" \
    -out "$cert_dir/cert.pem" \
    -days 3650 \
    -subj "/CN=emqx-monitor-platform/O=Monitor/C=CN" \
    2>/dev/null
  cp "$cert_dir/cert.pem" "$cert_dir/cacert.pem"
  chmod 600 "$cert_dir/key.pem"
  log_success "EMQX self-signed certificates generated in $cert_dir"
}

has_stage2_image_archive() {
  local dir file
  shopt -s nullglob
  for dir in "$DEPLOY_DIR/tar" "$DEPLOY_DIR/images"; do
    [[ -d "$dir" ]] || continue
    for file in "$dir"/*.tar "$dir"/*.tar.gz "$dir"/*.tgz; do
      shopt -u nullglob
      return 0
    done
  done
  shopt -u nullglob
  return 1
}

require_delivery_file() {
  local path="$1"
  local label="$2"
  [[ -f "$path" ]] || fail "missing delivery file: $label ($path)"
}

check_stage2_delivery_files() {
  log_info "checking stage2 delivery files"
  require_delivery_file "$COMPOSE_FILE" "docker-compose.yml"
  require_delivery_file "$DEPLOY_DIR/mysql/init/00-create-app-user.sh" "MySQL app user init script"
  require_delivery_file "$DEPLOY_DIR/mysql/init/monitor_platform_init.sql" "monitor platform init SQL"
  require_delivery_file "$DEPLOY_DIR/mysql/init/nacos.sql" "Nacos init SQL"
  require_delivery_file "$DEPLOY_DIR/mysql/init/02-info-publish-client.sql" "info publish client init SQL"
  require_delivery_file "$DEPLOY_DIR/nacos/conf/application.properties" "Nacos application.properties"
  require_delivery_file "$DEPLOY_DIR/nginx/conf/nginx.conf" "Nginx config"
  require_delivery_file "$DEPLOY_DIR/nginx/www/index.html" "front-end static index.html"

  require_delivery_file "$DEPLOY_DIR/emqx/etc/emqx.conf" "EMQX main config"
  require_delivery_file "$DEPLOY_DIR/emqx/etc/acl.conf" "EMQX ACL config"

  require_delivery_file "$DEPLOY_DIR/sdk/lib/libvauthsdk.so" "UKey SDK libvauthsdk.so"
  if [[ "$UKEY_SETUP_MODE" == "deferred" ]]; then
    log_warn "UKEY_SETUP_MODE=deferred: SDK is present, but onsite UKey/PIN/AuthId will be configured later"
  fi

  local image missing_private=() missing_public=()
  for image in "${private_images[@]}"; do
    image_exists "$image" || missing_private+=("$image")
  done
  for image in "${public_images[@]}"; do
    image_exists "$image" || missing_public+=("$image")
  done

  if (( ${#missing_private[@]} > 0 )); then
    if has_stage2_image_archive; then
      log_info "private images are not loaded yet; archives under tar/ or images/ will be loaded during deployment"
    else
      fail "private images are missing and no image archive exists under tar/ or images/: ${missing_private[*]}"
    fi
  fi

  if (( ${#missing_public[@]} > 0 )) && ! as_bool "$PULL_PUBLIC_IMAGES"; then
    if has_stage2_image_archive; then
      log_info "public images are not loaded yet; archives under tar/ or images/ will be loaded during deployment"
    else
      fail "public images are missing, PULL_PUBLIC_IMAGES=false, and no image archive exists under tar/ or images/: ${missing_public[*]}"
    fi
  fi

  log_success "stage2 delivery files checked"
}

load_image_archives() {
  local dirs=("$DEPLOY_DIR/tar" "$DEPLOY_DIR/images")
  local file loaded=false dir
  shopt -s nullglob
  for dir in "${dirs[@]}"; do
    [[ -d "$dir" ]] || continue
    for file in "$dir"/*.tar "$dir"/*.tar.gz "$dir"/*.tgz; do
      loaded=true
      log_info "loading image archive: $file"
      docker load -i "$file"
    done
  done
  shopt -u nullglob
  [[ "$loaded" == "true" ]] || log_info "no image archive found under tar/ or images/"
}

image_id() {
  docker image inspect -f '{{.Id}}' "$1" 2>/dev/null || true
}

backup_monitor_image_ids() {
  local backup_dir="$DEPLOY_DIR/backups/image-updates/$(date +%Y%m%d-%H%M%S)"
  local manifest="$backup_dir/image-ids-before.txt"
  local image
  mkdir -p "$backup_dir"
  for image in "${private_images[@]}"; do
    printf '%s %s\n' "$image" "$(image_id "$image")" >> "$manifest"
  done
  log_info "pre-update image manifest saved: $manifest" >&2
  printf '%s\n' "$manifest"
}

archive_listing_has_path() {
  local archive="$1"
  local pattern="$2"
  tar -tf "$archive" 2>/dev/null | grep -Eq "$pattern"
}

collect_image_archives_from_dir() {
  local root="$1"
  local file
  while IFS= read -r file; do
    if archive_listing_has_path "$file" '^manifest\.json$'; then
      printf '%s\n' "$file"
    fi
  done < <(find "$root" -type f \( -name '*.tar' -o -name '*.tar.gz' -o -name '*.tgz' \) | sort)
}

extract_image_package_if_needed() {
  local source_path="$1"
  local work_dir="$2"

  if [[ -d "$source_path" ]]; then
    printf '%s\n' "$source_path"
    return
  fi

  [[ -f "$source_path" ]] || fail "image update package not found: $source_path"

  if archive_listing_has_path "$source_path" '^manifest\.json$'; then
    printf '%s\n' "$source_path"
    return
  fi

  if archive_listing_has_path "$source_path" '(^|/)package/(images|tar)/[^/]+\.(tar|tar\.gz|tgz)$|(^|/)(images|tar)/[^/]+\.(tar|tar\.gz|tgz)$'; then
    mkdir -p "$work_dir"
    tar -xf "$source_path" -C "$work_dir"
    printf '%s\n' "$work_dir"
    return
  fi

  # Last resort: let docker load report whether this is a valid image archive.
  printf '%s\n' "$source_path"
}

resolve_update_image_archives() {
  local source_path="$1"
  local work_dir="$2"
  local resolved
  resolved="$(extract_image_package_if_needed "$source_path" "$work_dir")"

  if [[ -d "$resolved" ]]; then
    collect_image_archives_from_dir "$resolved"
  else
    printf '%s\n' "$resolved"
  fi
}

detect_loaded_monitor_services() {
  local before_manifest="$1"
  local pair image service before after
  UPDATED_MONITOR_SERVICES=()

  for pair in "${private_image_services[@]}"; do
    image="${pair%% *}"
    service="${pair#* }"
    before="$(awk -v img="$image" '$1 == img {print $2}' "$before_manifest" 2>/dev/null || true)"
    after="$(image_id "$image")"
    if [[ -n "$after" && "$before" != "$after" ]]; then
      UPDATED_MONITOR_SERVICES+=("$service")
      log_info "image changed: $image -> service $service"
    fi
  done
}

update_image_archives() {
  local source_path="$1"
  local update_root="$DEPLOY_DIR/update-packages"
  local work_dir="$update_root/extracted-$(date +%Y%m%d-%H%M%S)"
  local manifest archive loaded=false
  local archives=()

  [[ -n "$source_path" ]] || fail "missing image update package path"
  [[ -e "$source_path" ]] || fail "image update package path does not exist: $source_path"

  mapfile -t archives < <(resolve_update_image_archives "$source_path" "$work_dir")
  (( ${#archives[@]} > 0 )) || fail "no image archive found in update package: $source_path"

  if [[ "$DRY_RUN" == "true" ]]; then
    log_info "dry-run: image archives that would be loaded:"
    printf '  %s\n' "${archives[@]}"
    return
  fi

  manifest="$(backup_monitor_image_ids)"
  for archive in "${archives[@]}"; do
    [[ -f "$archive" ]] || continue
    loaded=true
    log_info "loading update image archive: $archive"
    docker load -i "$archive"
  done
  [[ "$loaded" == "true" ]] || fail "no readable image archive found in update package: $source_path"

  detect_loaded_monitor_services "$manifest"
  if (( ${#UPDATED_MONITOR_SERVICES[@]} == 0 )); then
    log_warn "no monitor-platform service image changed after loading package"
    return
  fi

  log_info "recreating updated services: ${UPDATED_MONITOR_SERVICES[*]}"
  compose_cmd up -d --no-deps --force-recreate "${UPDATED_MONITOR_SERVICES[@]}"
}

image_exists() {
  docker image inspect "$1" >/dev/null 2>&1
}

ensure_image() {
  local image="$1"
  local public="$2"
  image_exists "$image" && return

  if [[ "$public" == "true" ]] && as_bool "$PULL_PUBLIC_IMAGES"; then
    log_warn "pulling missing public image: $image"
    docker pull "$image"
    return
  fi

  fail "required image not found: $image"
}

check_images_stage2() {
  [[ "$DRY_RUN" == "true" ]] && {
    log_info "dry-run: skip image load and image presence checks"
    return
  }

  load_image_archives
  local image
  for image in "${public_images[@]}"; do
    ensure_image "$image" true
  done
  for image in "${private_images[@]}"; do
    ensure_image "$image" false
  done
  log_success "all required images are ready"
}

validate_compose() {
  log_info "validating docker compose config"
  compose_cmd config -q
}

start_infra_services() {
  log_info "starting infra services: ${infra_services[*]}"
  compose_cmd up -d "${infra_services[@]}"
}

run_minio_init() {
  log_info "initializing MinIO bucket and application user"
  compose_cmd rm -f -s minio-init >/dev/null 2>&1 || true
  compose_cmd up -d minio-init
}

start_business_services() {
  log_info "starting business services: ${business_services[*]}"
  compose_cmd up -d "${business_services[@]}"
  log_info "starting edge services: ${edge_services[*]}"
  compose_cmd up -d "${edge_services[@]}"
}
