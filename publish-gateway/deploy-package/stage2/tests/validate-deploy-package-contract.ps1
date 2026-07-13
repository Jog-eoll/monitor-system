param(
  [string]$PackageRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

function Read-Text([string]$Path) {
  return Get-Content -LiteralPath $Path -Raw -Encoding UTF8
}

function Assert-Contains([string]$Text, [string]$Needle, [string]$Message) {
  if (-not $Text.Contains($Needle)) {
    throw $Message
  }
}

function Assert-Line([string]$Text, [string]$Pattern, [string]$Message) {
  if ($Text -notmatch $Pattern) {
    throw $Message
  }
}

$install = Read-Text (Join-Path $PackageRoot 'stage2/install.sh')
$compose = Read-Text (Join-Path $PackageRoot 'docker-compose.yml')
$config = Read-Text (Join-Path $PackageRoot 'stage2/deploy.conf.example')
$app = Read-Text (Join-Path $PackageRoot 'application.yml')

Assert-Contains $install 'CONFIG_FILE="${CONFIG_FILE:-$DEPLOY_DIR/deploy.conf}"' `
  'stage2/install.sh must default to package-root deploy.conf.'

Assert-Contains $install 'images/publish-gateway.tar' `
  'stage2/install.sh must load the packaged publish-gateway image archive.'
Assert-Contains $install 'images/redis-7-alpine.tar.gz' `
  'stage2/install.sh must load the packaged redis image archive for offline installs.'

foreach ($name in @(
  'CLIENT_RELAY_ENABLED',
  'SECURE_PUBLISH_ENABLED',
  'VAUTH_PAYLOAD_DEBUG_LOG_ENABLED',
  'VAUTH_SVAC_MATRIX_TEST_ENABLED',
  'MQTT_RECONNECT_INTERVAL_MS',
  'MQTT_COMMAND_DEDUP_TTL_MS',
  'MQTT_DEDUP_CLEANUP_INTERVAL_MS',
  'MQTT_HTTP_FORWARD_TIMEOUT_MS',
  'CONTROL_DELIVERY_MQTT_FINAL_REPLY_POLL_INTERVAL_MS',
  'CONTROL_DELIVERY_MQTT_FINAL_REPLY_TIMEOUT_MS',
  'SECURE_DELIVERY_TERMINAL_GW_URL',
  'SECURE_DELIVERY_DOWNLOAD_TIMEOUT_MS',
  'SECURE_DELIVERY_DOWNLOAD_MAX_FILE_SIZE_MB',
  'SECURE_DELIVERY_ENVELOPE_ENABLED',
  'SECURE_DELIVERY_LARGE_FILE_REF_ENABLED'
)) {
  Assert-Line $install "(?m)^$name=" "stage2/install.sh must emit $name into .env."
  Assert-Line $config "(?m)^$name=" "deploy.conf.example must document $name."
}

foreach ($line in @(
  '- MQTT_AGENT_ENABLED=${MQTT_AGENT_ENABLED:-true}',
  '- SECURE_PUBLISH_ENABLED=${SECURE_PUBLISH_ENABLED:-false}',
  '- ./lib:/app/lib',
  '- ./certs:/app/certs',
  '- ./trust:/opt/publish-gateway/trust:ro',
  '- ./secure-publish/rejected:/opt/publish-gateway/secure-publish/rejected'
)) {
  Assert-Line $compose "(?m)^\s+$([regex]::Escape($line))\s*$" "docker-compose.yml must contain active line: $line"
}

foreach ($needle in @(
  'terminal-gateway-url: ${SECURE_DELIVERY_TERMINAL_GW_URL:http://127.0.0.1:8093}',
  'timeout-ms: ${SECURE_DELIVERY_DOWNLOAD_TIMEOUT_MS:900000}',
  'max-file-size-mb: ${SECURE_DELIVERY_DOWNLOAD_MAX_FILE_SIZE_MB:2048}',
  'enabled: ${SECURE_DELIVERY_ENVELOPE_ENABLED:false}',
  'payload-debug-log-enabled: ${VAUTH_PAYLOAD_DEBUG_LOG_ENABLED:false}',
  'svac-matrix-test-enabled: ${VAUTH_SVAC_MATRIX_TEST_ENABLED:false}',
  'truststore-path: ${MQTT_TRUSTSTORE_PATH:}',
  'registration-enabled: ${MQTT_REGISTER_ENABLED:true}',
  'timeout-ms: ${MQTT_HTTP_FORWARD_TIMEOUT_MS:15000}'
)) {
  Assert-Contains $app $needle "application.yml is missing synchronized setting: $needle"
}

Write-Host '[OK] publish-gateway deploy-package contract is valid.'
