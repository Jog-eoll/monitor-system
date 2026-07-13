param(
  [string]$DeployDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

function Read-Text {
  param([string]$Path)
  if (-not (Test-Path -LiteralPath $Path)) {
    throw "Missing file: $Path"
  }
  Get-Content -Raw -LiteralPath $Path
}

function Assert-Contains {
  param(
    [string]$Text,
    [string]$Needle,
    [string]$Label
  )
  if ($Text -notlike "*$Needle*") {
    throw "Missing contract [$Label]: $Needle"
  }
}

$stage2Dir = Join-Path $DeployDir 'stage2'
$install = Read-Text (Join-Path $stage2Dir 'install.sh')
$compose = Read-Text (Join-Path $stage2Dir 'lib\compose.sh')
$ctl = Read-Text (Join-Path $stage2Dir 'lib\ctl.sh')
$env = Read-Text (Join-Path $stage2Dir 'lib\env.sh')
$conf = Read-Text (Join-Path $stage2Dir 'deploy.conf.example')
$composeYaml = Read-Text (Join-Path $DeployDir 'docker-compose.yml')
$envExample = Read-Text (Join-Path $DeployDir '.env.example')

Assert-Contains $install '--update-images PATH' 'stage2 install update option'
Assert-Contains $install 'run_update_images' 'stage2 install update dispatcher'
Assert-Contains $compose 'update_image_archives()' 'image package update function'
Assert-Contains $compose 'detect_loaded_monitor_services' 'loaded image to service mapping'
Assert-Contains $compose 'backup_monitor_image_ids' 'pre-update image backup manifest'
Assert-Contains $compose 'compose_cmd up -d --no-deps --force-recreate' 'targeted service recreate'
Assert-Contains $ctl 'update-images <path>' 'generated ctl update command'
Assert-Contains $ctl 'cmd_update_images()' 'generated ctl update implementation'

$requiredVars = @(
  'SPRING_PROFILES_ACTIVE',
  'EMQX_DASHBOARD_PASSWORD',
  'ALARM_AUTO_BLACK_SCREEN_ENABLED',
  'LOG_CLEANUP_CRON',
  'LOG_TIMELINE_DEFAULT_DAYS',
  'MQTT_AUTH_ENABLED',
  'MQTT_DEVICE_DEFAULT_PASSWORD',
  'MQTT_ALLOW_EMPTY_DEVICE_PASSWORD',
  'MQTT_PLATFORM_CLIENT_PREFIX',
  'UKEY_PASSWORD_ENCRYPT_KEY'
)

foreach ($name in $requiredVars) {
  Assert-Contains $env "$name=" "rendered .env variable $name"
  Assert-Contains $conf "$name=" "deploy.conf.example variable $name"
  Assert-Contains $envExample "$name=" ".env.example variable $name"
}

foreach ($name in @(
  'SPRING_PROFILES_ACTIVE',
  'ALARM_AUTO_BLACK_SCREEN_ENABLED',
  'LOG_CLEANUP_CRON',
  'LOG_TIMELINE_DEFAULT_DAYS',
  'MQTT_AUTH_ENABLED',
  'MQTT_PLATFORM_CLIENT_PREFIX',
  'UKEY_PASSWORD_ENCRYPT_KEY'
)) {
  Assert-Contains $composeYaml $name "docker-compose variable $name"
}

Write-Host 'stage2 deploy contract checks passed'
