#!/bin/sh
set -eu

if [ -z "${MYSQL_USER:-}" ] || [ "$MYSQL_USER" = "root" ]; then
  exit 0
fi

sql_escape() {
  printf '%s' "$1" | sed "s/\\\\/\\\\\\\\/g; s/'/''/g"
}

APP_USER="$(sql_escape "$MYSQL_USER")"
APP_PASSWORD="$(sql_escape "${MYSQL_PASSWORD:-}")"

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE DATABASE IF NOT EXISTS info_publish_client DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '${APP_USER}'@'%' IDENTIFIED BY '${APP_PASSWORD}';
GRANT ALL PRIVILEGES ON monitor_platform.* TO '${APP_USER}'@'%';
GRANT ALL PRIVILEGES ON nacos_config.* TO '${APP_USER}'@'%';
GRANT ALL PRIVILEGES ON info_publish_client.* TO '${APP_USER}'@'%';
FLUSH PRIVILEGES;
EOSQL
