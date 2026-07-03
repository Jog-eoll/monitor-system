#!/bin/bash

# =============================================================================
# 监控平台一键部署脚本
# Monitor Platform One-Click Deployment Script
# =============================================================================
# 版本: 2.0.0
# 作者: System Admin
# 日期: 2026-05-08
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ "${1:-}" = "--legacy" ]; then
    shift
elif [ "${1:-}" = "-c" ] || [ "${1:-}" = "--init-config" ]; then
    CONFIG_TARGET="$SCRIPT_DIR/deploy.conf"
    CONFIG_SOURCE="$SCRIPT_DIR/stage2/deploy.conf.example"
    if [ -f "$CONFIG_TARGET" ]; then
        echo "[WARN] deploy.conf already exists: $CONFIG_TARGET"
        echo "[WARN] not overwriting existing config"
    else
        cp "$CONFIG_SOURCE" "$CONFIG_TARGET"
        echo "[OK] config template generated: $CONFIG_TARGET"
        echo "[INFO] edit deploy.conf, then run: bash install.sh --check"
    fi
    exit 0
else
    case "${1:-}" in
        --stage2)
            shift
            exec bash "$SCRIPT_DIR/stage2/install.sh" "$@"
            ;;
        ""|--check|--dry-run|--yes|--auto-install-docker|--no-auto-firewall|--report|--config|-h|--help)
            exec bash "$SCRIPT_DIR/stage2/install.sh" "$@"
            ;;
        *)
            exec bash "$SCRIPT_DIR/stage2/install.sh" "$@"
            ;;
    esac
fi

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 配置文件路径
CONFIG_FILE="./deploy.conf"
GATEWAY_CONFIG_FILE="./gateway.conf"

# 默认配置（使用容器内服务）
# 安全提示：所有密码必须通过环境变量或 deploy.conf 配置文件注入，禁止在此硬编码
DEFAULT_MYSQL_HOST="db"
DEFAULT_MYSQL_PORT="3306"
DEFAULT_MYSQL_DB="monitor_platform"
DEFAULT_MYSQL_USER="root"
DEFAULT_MYSQL_PASSWORD=""  # 必须通过环境变量 MYSQL_PASSWORD 或 deploy.conf 配置
DEFAULT_REDIS_HOST="redis"
DEFAULT_REDIS_PORT="6379"
DEFAULT_REDIS_PASSWORD=""  # 必须通过环境变量 REDIS_PASSWORD 或 deploy.conf 配置
DEFAULT_NACOS_HOST="nacos"
DEFAULT_NACOS_PORT="8848"
DEFAULT_NACOS_NAMESPACE="5c87647a-8a17-4adb-94ce-04047ea26d96"
DEFAULT_SENTINEL_HOST="192.168.1.31"
DEFAULT_SENTINEL_PORT="8858"
DEFAULT_MINIO_ENDPOINT="http://minio:9000"
DEFAULT_MINIO_ACCESS_KEY="admin"
DEFAULT_MINIO_SECRET_KEY=""  # 必须通过环境变量 MINIO_SECRET_KEY 或 deploy.conf 配置
DEFAULT_MINIO_BUCKET="monitor-platform"
DEFAULT_CONTENT_MYSQL_IP_PORT="127.0.0.1:23306"
DEFAULT_CONTENT_REDIS_HOST="127.0.0.1"
DEFAULT_CONTENT_REDIS_PORT="26379"
DEFAULT_CONTENT_NACOS_IP_PORT="127.0.0.1:18848"
DEFAULT_CONTENT_MINIO_ENDPOINT="http://127.0.0.1:19000"
DEFAULT_AUDIT_MODE="local"
DEFAULT_LOCAL_AUDIT_IMAGE_URL="http://192.168.1.28:8080/api/audit/image"
DEFAULT_LOCAL_AUDIT_TEXT_URL="http://192.168.1.28:8080/api/audit/text"
DEFAULT_LOCAL_AUDIT_VIDEO_URL="http://192.168.1.28:8080/api/audit/video"
DEFAULT_LOCAL_AUDIT_VIDEO_FRAME_INTERVAL="0.5"
DEFAULT_LOCAL_AUDIT_VIDEO_MAX_FRAMES="200"
DEFAULT_LOCAL_AUDIT_VIDEO_SAVE_FRAMES="false"
DEFAULT_MYSQL_ROOT_PASSWORD=""  # 必须通过 deploy.conf 配置，留空则等于 MYSQL_PASSWORD
DEFAULT_DISPATCH_MODE="http"
DEFAULT_MQTT_BROKER_URL="ssl://127.0.0.1:8883"
DEFAULT_MQTT_USERNAME=""
DEFAULT_MQTT_PASSWORD=""
DEFAULT_MQTT_TENANT_ID="default"
DEFAULT_MQTT_SITE_ID="site-001"
DEFAULT_MQTT_PLATFORM_CLIENT_ID="monitor-platform-001"
DEFAULT_MQTT_RECONNECT_INTERVAL_MS="30000"

# 服务端口配置
GATEWAY_PORT=8060
DEVICE_PORT=8062
UKEY_PORT=8063
ALARM_PORT=8064
CONTENT_PORT=8065
RULE_PORT=8066
FORWARD_PORT=8067
ROLE_PORT=8068
REGISTRY_PORT=8069
NGINX_PORT=80

# =============================================================================
# 打印Banner
# =============================================================================
print_banner() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║                                                                ║"
    echo "║              监控平台一键部署脚本 (Monitor Platform)             ║"
    echo "║                        Version 2.0.0                           ║"
    echo "║                                                                ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo ""
}

# =============================================================================
# 加载配置文件
# =============================================================================
load_config() {
    if [ -f "$CONFIG_FILE" ]; then
        log_info "加载配置文件: $CONFIG_FILE"
        source "$CONFIG_FILE"
    else
        log_warn "配置文件不存在，使用默认配置"
        # 使用默认配置
        MYSQL_HOST=$DEFAULT_MYSQL_HOST
        MYSQL_PORT=$DEFAULT_MYSQL_PORT
        MYSQL_DB=$DEFAULT_MYSQL_DB
        MYSQL_USER=$DEFAULT_MYSQL_USER
        MYSQL_PASSWORD=$DEFAULT_MYSQL_PASSWORD
        REDIS_HOST=$DEFAULT_REDIS_HOST
        REDIS_PORT=$DEFAULT_REDIS_PORT
        REDIS_PASSWORD=$DEFAULT_REDIS_PASSWORD
        NACOS_HOST=$DEFAULT_NACOS_HOST
        NACOS_PORT=$DEFAULT_NACOS_PORT
        NACOS_NAMESPACE=$DEFAULT_NACOS_NAMESPACE
        SENTINEL_HOST=$DEFAULT_SENTINEL_HOST
        SENTINEL_PORT=$DEFAULT_SENTINEL_PORT
        MINIO_ENDPOINT=$DEFAULT_MINIO_ENDPOINT
        MINIO_ACCESS_KEY=$DEFAULT_MINIO_ACCESS_KEY
        MINIO_SECRET_KEY=$DEFAULT_MINIO_SECRET_KEY
        MINIO_BUCKET=$DEFAULT_MINIO_BUCKET
        AUDIT_MODE=$DEFAULT_AUDIT_MODE
        LOCAL_AUDIT_IMAGE_URL=$DEFAULT_LOCAL_AUDIT_IMAGE_URL
        LOCAL_AUDIT_TEXT_URL=$DEFAULT_LOCAL_AUDIT_TEXT_URL
        LOCAL_AUDIT_VIDEO_URL=$DEFAULT_LOCAL_AUDIT_VIDEO_URL
        LOCAL_AUDIT_VIDEO_FRAME_INTERVAL=$DEFAULT_LOCAL_AUDIT_VIDEO_FRAME_INTERVAL
        LOCAL_AUDIT_VIDEO_MAX_FRAMES=$DEFAULT_LOCAL_AUDIT_VIDEO_MAX_FRAMES
        LOCAL_AUDIT_VIDEO_SAVE_FRAMES=$DEFAULT_LOCAL_AUDIT_VIDEO_SAVE_FRAMES
        MYSQL_ROOT_PASSWORD=$DEFAULT_MYSQL_ROOT_PASSWORD
        DISPATCH_MODE=$DEFAULT_DISPATCH_MODE
        MQTT_BROKER_URL=$DEFAULT_MQTT_BROKER_URL
        MQTT_USERNAME=$DEFAULT_MQTT_USERNAME
        MQTT_PASSWORD=$DEFAULT_MQTT_PASSWORD
        MQTT_TENANT_ID=$DEFAULT_MQTT_TENANT_ID
        MQTT_SITE_ID=$DEFAULT_MQTT_SITE_ID
        MQTT_PLATFORM_CLIENT_ID=$DEFAULT_MQTT_PLATFORM_CLIENT_ID
        MQTT_RECONNECT_INTERVAL_MS=$DEFAULT_MQTT_RECONNECT_INTERVAL_MS
    fi

    CONTENT_MYSQL_IP_PORT=${CONTENT_MYSQL_IP_PORT:-$DEFAULT_CONTENT_MYSQL_IP_PORT}
    CONTENT_REDIS_HOST=${CONTENT_REDIS_HOST:-$DEFAULT_CONTENT_REDIS_HOST}
    CONTENT_REDIS_PORT=${CONTENT_REDIS_PORT:-$DEFAULT_CONTENT_REDIS_PORT}
    CONTENT_NACOS_IP_PORT=${CONTENT_NACOS_IP_PORT:-$DEFAULT_CONTENT_NACOS_IP_PORT}
    CONTENT_MINIO_ENDPOINT=${CONTENT_MINIO_ENDPOINT:-$DEFAULT_CONTENT_MINIO_ENDPOINT}
    CONTENT_MONITOR_RULE_URL=${CONTENT_MONITOR_RULE_URL:-http://127.0.0.1:${RULE_PORT}}
    CONTENT_MONITOR_ALARM_URL=${CONTENT_MONITOR_ALARM_URL:-http://127.0.0.1:${ALARM_PORT}}
    CONTENT_MONITOR_DEVICE_URL=${CONTENT_MONITOR_DEVICE_URL:-http://127.0.0.1:${DEVICE_PORT}}
    CONTENT_MONITOR_FORWARD_URL=${CONTENT_MONITOR_FORWARD_URL:-http://127.0.0.1:${FORWARD_PORT}}
    DISPATCH_MODE=${DISPATCH_MODE:-$DEFAULT_DISPATCH_MODE}
    MQTT_BROKER_URL=${MQTT_BROKER_URL:-$DEFAULT_MQTT_BROKER_URL}
    MQTT_USERNAME=${MQTT_USERNAME:-$DEFAULT_MQTT_USERNAME}
    MQTT_PASSWORD=${MQTT_PASSWORD:-$DEFAULT_MQTT_PASSWORD}
    MQTT_TENANT_ID=${MQTT_TENANT_ID:-$DEFAULT_MQTT_TENANT_ID}
    MQTT_SITE_ID=${MQTT_SITE_ID:-$DEFAULT_MQTT_SITE_ID}
    MQTT_PLATFORM_CLIENT_ID=${MQTT_PLATFORM_CLIENT_ID:-$DEFAULT_MQTT_PLATFORM_CLIENT_ID}
    MQTT_RECONNECT_INTERVAL_MS=${MQTT_RECONNECT_INTERVAL_MS:-$DEFAULT_MQTT_RECONNECT_INTERVAL_MS}
}

# =============================================================================
# 安全校验：禁止使用默认账号名和占位密码
# =============================================================================
validate_security_config() {
    log_info "校验安全配置..."
    local has_error=false

    # 禁用用户名列表
    local forbidden_users=("root" "admin" "guest" "test" "user" "administrator" "superuser" "default" "anonymous")

    # 占位符密码特征列表（含 YOUR_ 或 _HERE 说明未替换）
    local placeholder_pattern="YOUR_|_HERE"

    # 检查函数：用户名是否是默认账号
    check_forbidden_username() {
        local field_name="$1"
        local value="$2"
        local lower_value=$(echo "$value" | tr '[:upper:]' '[:lower:]')
        for forbidden in "${forbidden_users[@]}"; do
            if [ "$lower_value" = "$forbidden" ]; then
                log_error "安全错误: ${field_name}='${value}' 是系统禁用的默认账号名，请修改为其他用户名"
                has_error=true
                return
            fi
        done
    }

    # 检查函数：密码是否仍是占位符
    check_placeholder() {
        local field_name="$1"
        local value="$2"
        if echo "$value" | grep -qE "$placeholder_pattern"; then
            log_error "安全错误: ${field_name} 未替换占位符，请在 deploy.conf 中设置真实密码"
            has_error=true
        fi
    }

    # 检查函数：必填字段不能为空
    check_required() {
        local field_name="$1"
        local value="$2"
        if [ -z "$value" ]; then
            log_error "安全错误: ${field_name} 不能为空，请在 deploy.conf 中填写该字段"
            has_error=true
        fi
    }

    # === MySQL ===
    check_forbidden_username "MYSQL_USER" "$MYSQL_USER"
    check_placeholder "MYSQL_PASSWORD" "$MYSQL_PASSWORD"
    check_placeholder "MYSQL_ROOT_PASSWORD" "${MYSQL_ROOT_PASSWORD:-$MYSQL_PASSWORD}"

    # === Redis ===
    check_placeholder "REDIS_PASSWORD" "$REDIS_PASSWORD"

    # === MinIO ===
    check_forbidden_username "MINIO_ROOT_USER" "$MINIO_ROOT_USER"
    check_placeholder "MINIO_ROOT_PASSWORD" "$MINIO_ROOT_PASSWORD"
    check_required "MINIO_ACCESS_KEY" "$MINIO_ACCESS_KEY"
    check_placeholder "MINIO_ACCESS_KEY" "$MINIO_ACCESS_KEY"
    check_required "MINIO_SECRET_KEY" "$MINIO_SECRET_KEY"
    check_placeholder "MINIO_SECRET_KEY" "$MINIO_SECRET_KEY"

    # === UKey 管理员 ===
    check_forbidden_username "UKEY_ADMIN_USERNAME" "$UKEY_ADMIN_USERNAME"
    check_placeholder "UKEY_ADMIN_PASSWORD" "$UKEY_ADMIN_PASSWORD"

    # === VAuth 证书 ===
    check_placeholder "VAUTH_SERVER_PASSWORD" "$VAUTH_SERVER_PASSWORD"
    check_placeholder "VAUTH_SERVER_AUTH_ID" "$VAUTH_SERVER_AUTH_ID"

    if [ "$has_error" = true ]; then
        log_error "安全配置校验失败，请修改 deploy.conf 后重新执行部署"
        exit 1
    fi

    log_success "安全配置校验通过"
}

# =============================================================================
# 生成配置文件模板
# =============================================================================
generate_config_template() {
    cat > "$CONFIG_FILE" << 'EOF'
# =============================================================================
# 监控平台部署配置文件
# 安全警告：以下密码为占位符，部署前必须修改为强密码！
# =============================================================================

# MySQL 配置（使用容器内服务）
MYSQL_HOST=db
MYSQL_PORT=3306
MYSQL_DB=monitor_platform
MYSQL_USER=root
MYSQL_PASSWORD=YOUR_MYSQL_PASSWORD_HERE
# MySQL容器ROOT密码（留空则与MYSQL_PASSWORD一致）
MYSQL_ROOT_PASSWORD=YOUR_MYSQL_PASSWORD_HERE

# Redis 配置（使用容器内服务）
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=YOUR_REDIS_PASSWORD_HERE

# Nacos 配置
NACOS_HOST=nacos
NACOS_PORT=8848
NACOS_NAMESPACE=5c87647a-8a17-4adb-94ce-04047ea26d96

# Sentinel 配置
SENTINEL_HOST=192.168.1.31
SENTINEL_PORT=8858

# MinIO 配置
# 注意: 两套账号用途不同
#   MINIO_ROOT_USER/PASSWORD  = MinIO容器的ROOT管理员账号（只在本机启动MinIO容器时使用）
#   MINIO_ACCESS_KEY/SECRET_KEY = 应用连接MinIO的AK/SK（必填，跟javaSDK调用所用）
MINIO_ENDPOINT=http://minio:9000
MINIO_ROOT_USER=YOUR_MINIO_ROOT_USER_HERE
MINIO_ROOT_PASSWORD=YOUR_MINIO_ROOT_PASSWORD_HERE
MINIO_ACCESS_KEY=YOUR_MINIO_ACCESS_KEY_HERE
MINIO_SECRET_KEY=YOUR_MINIO_SECRET_KEY_HERE
MINIO_BUCKET=monitor-platform

# monitor-content uses host network, so it must access local services through host ports.
CONTENT_MYSQL_IP_PORT=127.0.0.1:23306
CONTENT_REDIS_HOST=127.0.0.1
CONTENT_REDIS_PORT=26379
CONTENT_NACOS_IP_PORT=127.0.0.1:18848
CONTENT_MINIO_ENDPOINT=http://127.0.0.1:19000
CONTENT_MONITOR_RULE_URL=http://127.0.0.1:8066
CONTENT_MONITOR_ALARM_URL=http://127.0.0.1:8064
CONTENT_MONITOR_DEVICE_URL=http://127.0.0.1:8062
CONTENT_MONITOR_FORWARD_URL=http://127.0.0.1:8067

# MQTT 下发配置（默认保持 HTTP；公网 Broker 和网关 Agent 准备好后改为 mqtt 或 dual）
DISPATCH_MODE=http
MQTT_BROKER_URL=ssl://127.0.0.1:8883
MQTT_USERNAME=
MQTT_PASSWORD=
MQTT_TENANT_ID=default
MQTT_SITE_ID=site-001
MQTT_PLATFORM_CLIENT_ID=monitor-platform-001
MQTT_RECONNECT_INTERVAL_MS=30000

# UKey 管理员账号（安全要求：禁用root/admin等默认账号，必须符合复杂度要求，>=8位+字母+数字+特殊字符）
UKEY_ADMIN_USERNAME=YOUR_UKEY_ADMIN_USERNAME_HERE
UKEY_ADMIN_PASSWORD=YOUR_UKEY_ADMIN_PASSWORD_HERE

# VAuth 服务端证书配置（安全敬告：必须填写，禁止使用默认值）
VAUTH_SERVER_PASSWORD=YOUR_VAUTH_SERVER_PASSWORD_HERE
VAUTH_SERVER_AUTH_ID=YOUR_VAUTH_SERVER_AUTH_ID_HERE

# AI内容审核模式 (qwen=公网千问API / local=内网本地模型)
AUDIT_MODE=local
# 本地审核模型地址（仅 AUDIT_MODE=local 时需要）
LOCAL_AUDIT_IMAGE_URL=http://192.168.1.28:8080/api/audit/image
LOCAL_AUDIT_TEXT_URL=http://192.168.1.28:8080/api/audit/text
LOCAL_AUDIT_VIDEO_URL=http://192.168.1.28:8080/api/audit/video
LOCAL_AUDIT_VIDEO_FRAME_INTERVAL=0.5
LOCAL_AUDIT_VIDEO_MAX_FRAMES=200
LOCAL_AUDIT_VIDEO_SAVE_FRAMES=false
EOF
    log_success "配置文件模板已生成: $CONFIG_FILE"
}

# =============================================================================
# 检查系统环境
# =============================================================================
check_system() {
    log_info "检查系统环境..."
    
    # 检查操作系统
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        OS="Linux"
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        OS="MacOS"
    else
        log_error "不支持的操作系统: $OSTYPE"
        exit 1
    fi
    log_info "操作系统: $OS"
    
    # 检查 Docker
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装，请先安装 Docker"
        exit 1
    fi
    log_success "Docker 已安装: $(docker --version)"
    
    # 检查 Docker Compose
    if command -v docker-compose &> /dev/null; then
        DOCKER_COMPOSE="docker-compose"
        log_success "Docker Compose 已安装: $(docker-compose --version)"
    elif docker compose version &> /dev/null; then
        DOCKER_COMPOSE="docker compose"
        log_success "Docker Compose (Plugin) 已安装: $(docker compose version)"
    else
        log_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi
    
    # 检查 Java（可选，用于调试）
    if ! command -v java &> /dev/null; then
        log_warn "Java 未安装，不影响镜像部署"
    else
        log_success "Java 已安装: $(java -version 2>&1 | head -1)"
    fi
}

# =============================================================================
# 检查端口占用
# =============================================================================
check_ports() {
    log_info "检查端口占用情况..."
    
    local ports=($GATEWAY_PORT $DEVICE_PORT $UKEY_PORT $ALARM_PORT $CONTENT_PORT $RULE_PORT $FORWARD_PORT $ROLE_PORT $REGISTRY_PORT $NGINX_PORT)
    local port_in_use=false
    
    for port in "${ports[@]}"; do
        if command -v netstat &> /dev/null; then
            if netstat -tuln 2>/dev/null | grep -q ":$port "; then
                log_warn "端口 $port 已被占用"
                port_in_use=true
            fi
        elif command -v ss &> /dev/null; then
            if ss -tuln 2>/dev/null | grep -q ":$port "; then
                log_warn "端口 $port 已被占用"
                port_in_use=true
            fi
        fi
    done
    
    if [ "$port_in_use" = true ]; then
        log_warn "部分端口已被占用，可能会导致部署失败"
        read -p "是否继续部署? (y/n): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        log_success "所有端口可用"
    fi
}

# =============================================================================
# 检查镜像
# =============================================================================
check_images() {
    log_info "检查镜像是否存在..."
    
    # 公共镜像（可从 Docker Hub 在线拉取）
    local public_images=(
        "nacos/nacos-server:v2.2.0"
        "mysql:5.7"
        "redis:latest"
        "minio/minio:latest"
        "nginx:alpine"
    )

    # 私有业务镜像（必须通过 tar 包加载）
    local private_images=(
        "monitor-platform-device:latest"
        "monitor-platform-ukey:latest"
        "monitor-platform-alarm:latest"
        "monitor-platform-rule:latest"
        "monitor-platform-content:latest"
        "monitor-platform-forward:latest"
        "monitor-platform-role:latest"
        "monitor-platform-registry-server:latest"
        "monitor-platform-gateway:latest"
    )

    local missing_public=()
    local missing_private=()
    # 尝试获取所有镜像
    local image_list=$(docker images --format "{{.Repository}}:{{.Tag}}" 2>/dev/null || echo "")
    
    for image in "${public_images[@]}"; do
        if [[ "$image_list" != *"$image"* ]]; then
            missing_public+=("$image")
        fi
    done

    for image in "${private_images[@]}"; do
        if [[ "$image_list" != *"$image"* ]]; then
            missing_private+=("$image")
        fi
    done

    # 处理缺失的公共镜像：优先从 tar 加载，否则在线拉取
    if [ ${#missing_public[@]} -gt 0 ]; then
        log_warn "以下公共镜像未找到："
        for image in "${missing_public[@]}"; do
            echo "  - $image"
        done

        # 尝试从 tar 包加载公共镜像
        local found_tar=false
        if [ -d "./tar" ]; then
            for file in ./tar/*.tar ./tar/*.tar.gz ./tar/*.tgz; do
                if [ -f "$file" ]; then
                    found_tar=true
                    break
                fi
            done
        fi

        if [ "$found_tar" = true ]; then
            log_info "检测到 tar 包，将在后续统一加载"
        else
            log_info "未找到 tar 包，尝试在线拉取公共镜像..."
            local pull_failed=false
            for image in "${missing_public[@]}"; do
                log_info "正在拉取: $image"
                if docker pull "$image"; then
                    log_success "拉取成功: $image"
                else
                    log_error "拉取失败: $image"
                    pull_failed=true
                fi
            done
            if [ "$pull_failed" = true ]; then
                log_error "部分公共镜像拉取失败，请检查网络或手动将 tar 包放入 ./tar 目录"
                exit 1
            fi
        fi
    fi

    # 处理缺失的私有镜像：必须通过 tar 包加载
    if [ ${#missing_private[@]} -gt 0 ]; then
        log_warn "以下业务镜像未找到："
        for image in "${missing_private[@]}"; do
            echo "  - $image"
        done
        log_info "正在查找镜像 tar 包..."
        
        # 查找 tar 包
        local tar_files=()
        # 首先在 ./tar 目录中查找
        if [ -d "./tar" ]; then
            for file in ./tar/*.tar ./tar/*.tar.gz ./tar/*.tgz; do
                if [ -f "$file" ]; then
                    tar_files+=("$file")
                fi
            done
        fi
        
        # 如果 ./tar 目录中没有，再在当前目录查找
        if [ ${#tar_files[@]} -eq 0 ]; then
            for file in *.tar *.tar.gz *.tgz; do
                if [ -f "$file" ]; then
                    tar_files+=("$file")
                fi
            done
        fi
        
        if [ ${#tar_files[@]} -gt 0 ]; then
            log_success "找到 ${#tar_files[@]} 个 tar 包文件:"
            for file in "${tar_files[@]}"; do
                echo "  - $file"
            done
            
            echo ""
            read -p "是否从 tar 包加载镜像? (y/n): " -n 1 -r
            echo
            
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                load_images_from_tar "${tar_files[@]}"
                # 再次检查
                check_images
            else
                log_warn "请手动加载镜像"
                exit 1
            fi
        else
            log_error "未找到镜像 tar 包，请将镜像 tar 包放置在 ./tar 目录或当前目录"
            exit 1
        fi
    else
        if [ ${#missing_public[@]} -eq 0 ]; then
            log_success "所有镜像已就绪"
        fi
    fi
}

# =============================================================================
# 从 tar 包加载镜像
# =============================================================================
load_images_from_tar() {
    local tar_files=($@)
    log_info "开始从 tar 包加载镜像..."
    
    local success_count=0
    local fail_count=0
    
    for tar_file in "${tar_files[@]}"; do
        log_info "正在加载: $tar_file"
        
        if docker load -i "$tar_file"; then
            log_success "加载成功: $tar_file"
            success_count=$((success_count + 1))
        else
            log_error "加载失败: $tar_file"
            fail_count=$((fail_count + 1))
        fi
        
        echo ""
    done
    
    echo ""
    log_success "镜像加载完成"
    echo "  成功: $success_count"
    echo "  失败: $fail_count"
    
    if [ $fail_count -gt 0 ]; then
        log_warn "部分镜像加载失败，请检查 tar 包"
    fi
}

# =============================================================================
# 检查外部服务连接
# =============================================================================
check_external_services() {
    log_info "检查外部服务连接..."
    
    # 检查 MySQL
    log_info "检查 MySQL 连接 ($MYSQL_HOST:$MYSQL_PORT)..."
    if command -v mysql &> /dev/null; then
        if mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1" &> /dev/null; then
            log_success "MySQL 连接正常"
        else
            log_warn "无法连接到 MySQL，请检查配置"
        fi
    else
        log_warn "未安装 MySQL 客户端，跳过连接检查"
    fi
    
    # 检查 Redis
    log_info "检查 Redis 连接 ($REDIS_HOST:$REDIS_PORT)..."
    if command -v redis-cli &> /dev/null; then
        if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -a "$REDIS_PASSWORD" ping &> /dev/null | grep -q "PONG"; then
            log_success "Redis 连接正常"
        else
            log_warn "无法连接到 Redis，请检查配置"
        fi
    else
        log_warn "未安装 Redis 客户端，跳过连接检查"
    fi
    
    # 检查 Nacos
    log_info "检查 Nacos 连接 ($NACOS_HOST:$NACOS_PORT)..."
    if curl -s "http://$NACOS_HOST:$NACOS_PORT/nacos" > /dev/null 2>&1; then
        log_success "Nacos 连接正常"
    else
        log_warn "无法连接到 Nacos，请检查配置"
    fi
}

# =============================================================================


# =============================================================================
# 加载发布网关配置
# =============================================================================
load_gateway_config() {
    if [ -f "$GATEWAY_CONFIG_FILE" ]; then
        log_info "加载发布网关配置文件: $GATEWAY_CONFIG_FILE"
        source "$GATEWAY_CONFIG_FILE"
    else
        log_warn "未找到 gateway.conf，将自动复用管控平台配置并使用默认值"
    fi

    # 复用管控平台的 MySQL 配置（可被 gateway.conf 覆盖）
    GATEWAY_DB_HOST=${GATEWAY_DB_HOST:-$MYSQL_HOST}
    GATEWAY_DB_PORT=${GATEWAY_DB_PORT:-$MYSQL_PORT}
    GATEWAY_DB_USER=${GATEWAY_DB_USER:-$MYSQL_USER}
    GATEWAY_DB_PASSWORD=${GATEWAY_DB_PASSWORD:-$MYSQL_PASSWORD}

    # 复用管控平台的 MinIO 账号密码（endpoint 需用宿主机IP，同机默认 127.0.0.1）
    GATEWAY_MINIO_HOST=${GATEWAY_MINIO_HOST:-127.0.0.1}
    GATEWAY_MINIO_PORT=${GATEWAY_MINIO_PORT:-9003}
    GATEWAY_MINIO_ACCESS_KEY=${GATEWAY_MINIO_ACCESS_KEY:-$MINIO_ACCESS_KEY}
    GATEWAY_MINIO_SECRET_KEY=${GATEWAY_MINIO_SECRET_KEY:-$MINIO_SECRET_KEY}
    GATEWAY_MINIO_BUCKET=${GATEWAY_MINIO_BUCKET:-$MINIO_BUCKET}

    # 发布网关独有配置
    GATEWAY_MONITOR_HOST=${GATEWAY_MONITOR_HOST:-127.0.0.1}
    GATEWAY_MONITOR_CONTENT_PORT=${GATEWAY_MONITOR_CONTENT_PORT:-8065}
    GATEWAY_MONITOR_UKEY_PORT=${GATEWAY_MONITOR_UKEY_PORT:-8063}
    GATEWAY_DEPLOY_DIR=${GATEWAY_DEPLOY_DIR:-/opt/publish-gateway}
    GATEWAY_IMAGE_TAR=${GATEWAY_IMAGE_TAR:-gateway-udp-proxy.tar}
}

# =============================================================================
# 部署发布网关
# =============================================================================
deploy_publish_gateway() {
    log_info "======= 开始部署发布网关 ======="

    # 1. 创建目录结构
    log_info "创建部署目录: $GATEWAY_DEPLOY_DIR"
    mkdir -p "$GATEWAY_DEPLOY_DIR"/{lib,certs,config,logs,tar}

    # 2. 生成 config/application.yml（含 ${VAR:default} 占位，永不需要再改）
    log_info "生成 config/application.yml..."
    cat > "$GATEWAY_DEPLOY_DIR/config/application.yml" << 'APPYML'
server:
  port: 8092
spring:
  application:
    name: gateway-udp-proxy
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/udp_proxy_gateway?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
monitor:
  platform:
    content-url: http://${MONITOR_HOST:127.0.0.1}:${MONITOR_CONTENT_PORT:8065}
minio:
  enabled: true
  endpoint: http://${MINIO_HOST:127.0.0.1}:${MINIO_PORT:9003}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket-name: ${MINIO_BUCKET:monitor-content}
  fallback-to-base64: true
vauth:
  device-type: ${VAUTH_DEVICE_TYPE:ukey}
  password: "${VAUTH_PASSWORD}"
  auth-id: "${VAUTH_AUTH_ID:44030000003330000305}"
  ukey-path: ""
  server-id: "${VAUTH_SERVER_ID:44010100003330003024}"
  server-cert-path: "certs/${VAUTH_SERVER_ID:44010100003330003024}_SIGN.cer"
  client-cert-path: "certs/${VAUTH_AUTH_ID:44030000003330000305}_SIGN.cer"
  control-platform-url: "http://${MONITOR_HOST:127.0.0.1}:${MONITOR_UKEY_PORT:8063}"
  sign-enabled: true
gateway:
  transcode:
    enabled: false
logging:
  level:
    root: INFO
    com.publishgateway.udpproxy: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS, Asia/Shanghai} [%thread] %-5level %logger{50} - %msg%n"
APPYML

    # 3. 生成 .env（真实参数，Spring Boot 容器启动时注入）
    log_info "生成 .env..."
    cat > "$GATEWAY_DEPLOY_DIR/.env" << EOF
DB_HOST=${GATEWAY_DB_HOST}
DB_PORT=${GATEWAY_DB_PORT}
DB_USERNAME=${GATEWAY_DB_USER}
DB_PASSWORD=${GATEWAY_DB_PASSWORD}
MONITOR_HOST=${GATEWAY_MONITOR_HOST}
MONITOR_CONTENT_PORT=${GATEWAY_MONITOR_CONTENT_PORT}
MONITOR_UKEY_PORT=${GATEWAY_MONITOR_UKEY_PORT}
MINIO_HOST=${GATEWAY_MINIO_HOST}
MINIO_PORT=${GATEWAY_MINIO_PORT}
MINIO_ACCESS_KEY=${GATEWAY_MINIO_ACCESS_KEY}
MINIO_SECRET_KEY=${GATEWAY_MINIO_SECRET_KEY}
MINIO_BUCKET=${GATEWAY_MINIO_BUCKET}
VAUTH_AUTH_ID=${GATEWAY_VAUTH_AUTH_ID:-44030000003330000305}
VAUTH_SERVER_ID=${GATEWAY_VAUTH_SERVER_ID:-44010100003330003024}
VAUTH_PASSWORD=${GATEWAY_VAUTH_PASSWORD}
EOF

    # 4. 生成 docker-compose.yml
    log_info "生成 docker-compose.yml..."
    cat > "$GATEWAY_DEPLOY_DIR/docker-compose.yml" << 'DCYML'
version: '3.8'
services:
  publish-gateway:
    image: gateway-udp-proxy:latest
    container_name: gateway-udp-proxy
    network_mode: host
    privileged: true
    env_file:
      - .env
    environment:
      - TZ=Asia/Shanghai
      - JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8
      - LD_LIBRARY_PATH=/app/lib
    volumes:
      - /opt/publish-gateway/lib:/app/lib
      - /opt/publish-gateway/certs:/app/certs
      - /opt/publish-gateway/config:/app/config
      - /dev/bus/usb:/dev/bus/usb
      - /opt/publish-gateway/logs:/app/logs
    restart: unless-stopped
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "3"
DCYML

    # 5. 加载镜像
    local tar_path="$GATEWAY_DEPLOY_DIR/tar/$GATEWAY_IMAGE_TAR"
    if [ -f "$tar_path" ]; then
        log_info "加载镜像: $tar_path"
        docker load -i "$tar_path"
    else
        log_warn "未找到镜像包: $tar_path"
        log_warn "请手动将 gateway-udp-proxy.tar 放入 $GATEWAY_DEPLOY_DIR/tar/ 后执行："
        log_warn "  docker load -i $tar_path"
        log_warn "  cd $GATEWAY_DEPLOY_DIR && $DOCKER_COMPOSE up -d"
        return
    fi

    # 6. 启动容器
    log_info "启动发布网关容器..."
    (cd "$GATEWAY_DEPLOY_DIR" && $DOCKER_COMPOSE up -d)

    if [ $? -eq 0 ]; then
        log_success "发布网关部署成功！"
        echo ""
        echo "  容器名:     gateway-udp-proxy"
        echo "  HTTP接口:   http://localhost:8092"
        echo "  部署目录:   $GATEWAY_DEPLOY_DIR"
        echo ""
        echo "  注意：请将以下文件手动上传到对应目录（脚本无法自动复制）："
        echo "    SDK动态库(.so) → $GATEWAY_DEPLOY_DIR/lib/"
        echo "    证书文件(.cer) → $GATEWAY_DEPLOY_DIR/certs/"
    else
        log_error "发布网关启动失败，请查看日志："
        echo "  docker logs gateway-udp-proxy"
    fi
}

# =============================================================================
# 询问是否部署发布网关
# =============================================================================
ask_deploy_publish_gateway() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║               发布网关（publish-gateway）                       ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo ""
    echo "  发布网关可以与管控平台部署在同一台服务器，也可以单独部署到其他环境。"
    echo ""
    read -p "是否在本机同时部署发布网关? (y/n): " -n 1 -r
    echo ""

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        load_gateway_config
        deploy_publish_gateway
    else
        echo ""
        log_info "跳过发布网关部署。如需单独部署，请参考以下步骤："
        echo ""
        echo "  1. 在目标服务器创建目录：mkdir -p /opt/publish-gateway/{lib,certs,config,logs,tar}"
        echo "  2. 上传 gateway-udp-proxy.tar 到 /opt/publish-gateway/tar/"
        echo "  3. 上传 SDK动态库(.so) 到 /opt/publish-gateway/lib/"
        echo "  4. 上传 证书文件(.cer) 到 /opt/publish-gateway/certs/"
        echo "  5. 编写 gateway.conf 填入该环境的参数，与 install.sh 并列放置"
        echo "  6. 执行 ./install.sh 并选择部署发布网关"
        echo ""
    fi
}

# =============================================================================


# =============================================================================
# 生成环境变量文件
# =============================================================================
generate_env_file() {
    log_info "生成环境变量文件..."
    
    cat > .env << EOF
# 数据库配置
MYSQL_IP_PORT=${MYSQL_HOST}:${MYSQL_PORT}
MYSQL_USERNAME=${MYSQL_USER}
MYSQL_PASSWORD=${MYSQL_PASSWORD}
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-${MYSQL_PASSWORD}}

# Redis 配置
REDIS_HOST=${REDIS_HOST}
REDIS_PORT=${REDIS_PORT}
REDIS_PASSWORD=${REDIS_PASSWORD}

# Nacos 配置
NACOS_IP_PORT=${NACOS_HOST}:${NACOS_PORT}
NACOS_NAMESPACE=${NACOS_NAMESPACE}

# Sentinel 配置
SENTINEL_IP_PORT=${SENTINEL_HOST}:${SENTINEL_PORT}
SENTINEL_PORT=${SENTINEL_PORT}

# MinIO 配置
MINIO_ENDPOINT=${MINIO_ENDPOINT}
MINIO_ROOT_USER=${MINIO_ROOT_USER}
MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD}
MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}
MINIO_SECRET_KEY=${MINIO_SECRET_KEY}
MINIO_BUCKET_NAME=${MINIO_BUCKET}

# monitor-content uses host network, so it must access local services through host ports.
CONTENT_MYSQL_IP_PORT=${CONTENT_MYSQL_IP_PORT}
CONTENT_REDIS_HOST=${CONTENT_REDIS_HOST}
CONTENT_REDIS_PORT=${CONTENT_REDIS_PORT}
CONTENT_NACOS_IP_PORT=${CONTENT_NACOS_IP_PORT}
CONTENT_MINIO_ENDPOINT=${CONTENT_MINIO_ENDPOINT}
CONTENT_MONITOR_RULE_URL=${CONTENT_MONITOR_RULE_URL}
CONTENT_MONITOR_ALARM_URL=${CONTENT_MONITOR_ALARM_URL}
CONTENT_MONITOR_DEVICE_URL=${CONTENT_MONITOR_DEVICE_URL}
CONTENT_MONITOR_FORWARD_URL=${CONTENT_MONITOR_FORWARD_URL}

# UKey 管理员账号
UKEY_ADMIN_USERNAME=${UKEY_ADMIN_USERNAME}
UKEY_ADMIN_PASSWORD=${UKEY_ADMIN_PASSWORD}

# VAuth 服务端证书
VAUTH_SERVER_PASSWORD=${VAUTH_SERVER_PASSWORD}
VAUTH_SERVER_AUTH_ID=${VAUTH_SERVER_AUTH_ID}

# 服务间调用配置
MONITOR_DEVICE_URL=http://monitor-device:${DEVICE_PORT}
MONITOR_ALARM_URL=http://monitor-alarm:${ALARM_PORT}
MONITOR_CONTENT_URL=http://host.docker.internal:${CONTENT_PORT}
MONITOR_RULE_URL=http://monitor-rule:${RULE_PORT}
MONITOR_FORWARD_URL=http://monitor-forward:${FORWARD_PORT}
MONITOR_ROLE_URL=http://monitor-role:${ROLE_PORT}

# MQTT 下发配置
DISPATCH_MODE=${DISPATCH_MODE:-${DEFAULT_DISPATCH_MODE}}
MQTT_BROKER_URL=${MQTT_BROKER_URL:-${DEFAULT_MQTT_BROKER_URL}}
MQTT_USERNAME=${MQTT_USERNAME:-${DEFAULT_MQTT_USERNAME}}
MQTT_PASSWORD=${MQTT_PASSWORD:-${DEFAULT_MQTT_PASSWORD}}
MQTT_TENANT_ID=${MQTT_TENANT_ID:-${DEFAULT_MQTT_TENANT_ID}}
MQTT_SITE_ID=${MQTT_SITE_ID:-${DEFAULT_MQTT_SITE_ID}}
MQTT_PLATFORM_CLIENT_ID=${MQTT_PLATFORM_CLIENT_ID:-${DEFAULT_MQTT_PLATFORM_CLIENT_ID}}
MQTT_RECONNECT_INTERVAL_MS=${MQTT_RECONNECT_INTERVAL_MS:-${DEFAULT_MQTT_RECONNECT_INTERVAL_MS}}

# 远程升级下载地址。设备在内网，必须填写设备可访问的公网平台根地址，例如 http://公网IP 或 http://域名。
UPGRADE_DOWNLOAD_BASE_URL=${UPGRADE_DOWNLOAD_BASE_URL:-http://${SERVER_IP}}

# AI检测模式配置 (qwen=公网千问 / local=内网本地模型)
AUDIT_MODE=${AUDIT_MODE:-${DEFAULT_AUDIT_MODE}}
LOCAL_AUDIT_IMAGE_URL=${LOCAL_AUDIT_IMAGE_URL:-${DEFAULT_LOCAL_AUDIT_IMAGE_URL}}
LOCAL_AUDIT_TEXT_URL=${LOCAL_AUDIT_TEXT_URL:-${DEFAULT_LOCAL_AUDIT_TEXT_URL}}
LOCAL_AUDIT_VIDEO_URL=${LOCAL_AUDIT_VIDEO_URL:-${DEFAULT_LOCAL_AUDIT_VIDEO_URL}}
LOCAL_AUDIT_VIDEO_FRAME_INTERVAL=${LOCAL_AUDIT_VIDEO_FRAME_INTERVAL:-${DEFAULT_LOCAL_AUDIT_VIDEO_FRAME_INTERVAL}}
LOCAL_AUDIT_VIDEO_MAX_FRAMES=${LOCAL_AUDIT_VIDEO_MAX_FRAMES:-${DEFAULT_LOCAL_AUDIT_VIDEO_MAX_FRAMES}}
LOCAL_AUDIT_VIDEO_SAVE_FRAMES=${LOCAL_AUDIT_VIDEO_SAVE_FRAMES:-${DEFAULT_LOCAL_AUDIT_VIDEO_SAVE_FRAMES}}
EOF
    
    log_success "环境变量文件已生成: .env"
}

# =============================================================================
# 生成 Nacos application.properties（不写死，由 install.sh 渲染后挂载进容器）
# =============================================================================
generate_nacos_config() {
    log_info "生成 Nacos 配置文件..."
    mkdir -p ./nacos/conf

    cat > ./nacos/conf/application.properties << EOF
server.contextPath=/nacos
server.port=8848

spring.datasource.platform=mysql

db.num=1
db.url.0=jdbc:mysql://db:3306/nacos_config?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true
db.user=${MYSQL_USER}
db.password=${MYSQL_PASSWORD}

nacos.cmdb.dumpTaskInterval=3600
nacos.cmdb.eventTaskInterval=10
nacos.cmdb.labelTaskInterval=300
nacos.cmdb.loadDataAtStart=false

management.metrics.export.elastic.enabled=false
management.metrics.export.influx.enabled=false

server.tomcat.accesslog.enabled=true
server.tomcat.accesslog.pattern=%h %l %u %t "%r" %s %b %D %{User-Agent}i

nacos.security.ignore.urls=/,/**/*.css,/**/*.js,/**/*.html,/**/*.map,/**/*.svg,/**/*.png,/**/*.ico,/console-fe/public/**,/v1/auth/login,/v1/console/health/**,/v1/cs/**,/v1/ns/**,/v1/cmdb/**,/actuator/**,/v1/console/server/**

nacos.naming.distro.taskDispatchThreadCount=1
nacos.naming.distro.taskDispatchPeriod=200
nacos.naming.distro.batchSyncKeyCount=1000
nacos.naming.distro.initDataRatio=0.9
nacos.naming.distro.syncRetryDelay=5000
nacos.naming.data.warmup=true
nacos.naming.expireInstance=true
EOF

    log_success "Nacos 配置文件已生成: ./nacos/conf/application.properties"
}

# =============================================================================
# 更新 docker-compose.yml
# =============================================================================
update_docker_compose() {
    log_info "更新 Docker Compose 配置..."
    
    # 备份原文件
    if [ -f "docker-compose.yml" ]; then
        cp docker-compose.yml "docker-compose.yml.backup.$(date +%Y%m%d%H%M%S)"
    fi
    
    log_success "Docker Compose 配置已更新"
}

# =============================================================================
# 部署服务
# =============================================================================
deploy_services() {
    log_info "开始部署服务..."
    
    # 确保 UKey SDK 目录存在
    if [ ! -d "./sdk/lib" ]; then
        mkdir -p ./sdk/lib
        log_warn "已创建 ./sdk/lib 目录，请确认 SDK 动态库(.so)文件已放入"
    fi
    
    # 校验 SDK 文件是否存在
    local so_count=$(find ./sdk/lib -name "*.so" 2>/dev/null | wc -l)
    if [ "$so_count" -eq 0 ]; then
        log_warn "警告: ./sdk/lib 下未找到 .so 文件，UKey 服务可能无法正常启动"
        log_warn "请将 VAuth SDK 动态库文件放入 ./sdk/lib/ 目录"
    else
        log_info "检测到 ${so_count} 个 SDK 动态库文件"
    fi
    
    # 停止旧服务
    log_info "停止旧服务..."
    $DOCKER_COMPOSE down --remove-orphans 2>/dev/null || true
    
    # 直接启动服务（使用预提供的镜像）
    log_info "启动服务..."
    $DOCKER_COMPOSE up -d
    
    if [ $? -eq 0 ]; then
        log_success "服务部署成功"
    else
        log_error "服务部署失败"
        exit 1
    fi
}

# =============================================================================
# 检查服务健康状态
# =============================================================================
check_health() {
    log_info "检查服务健康状态..."
    
    local services=("monitor-gateway" "monitor-device" "monitor-ukey" "monitor-alarm" "monitor-content" "monitor-rule" "monitor-forward" "monitor-role" "monitor-registry-server")
    local max_retries=30
    local retry_interval=5
    
    for service in "${services[@]}"; do
        log_info "检查 $service 健康状态..."
        local retries=0
        local healthy=false
        
        while [ $retries -lt $max_retries ]; do
            if docker ps | grep -q "$service"; then
                if [ "$service" == "monitor-gateway" ]; then
                    # 网关服务特殊检查
                    if curl -s "http://localhost:${GATEWAY_PORT}/actuator/health" > /dev/null 2>&1; then
                        healthy=true
                        break
                    fi
                else
                    healthy=true
                    break
                fi
            fi
            
            retries=$((retries + 1))
            sleep $retry_interval
        done
        
        if [ "$healthy" = true ]; then
            log_success "$service 运行正常"
        else
            log_warn "$service 可能未正常启动，请检查日志"
        fi
    done
    
    # 检查 Nginx
    if docker ps | grep -q "monitor-nginx"; then
        log_success "Nginx 运行正常"
    else
        log_warn "Nginx 可能未正常启动"
    fi
}

# =============================================================================
# 打印部署信息
# =============================================================================
print_deployment_info() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║                     部署完成信息                               ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo ""
    echo "服务访问地址:"
    echo "  - API 网关:     http://localhost:${GATEWAY_PORT}"
    echo "  - Nginx 代理:   http://localhost:${NGINX_PORT}"
    echo ""
    echo "各服务端口:"
    echo "  - Gateway:      ${GATEWAY_PORT}"
    echo "  - Device:       ${DEVICE_PORT}"
    echo "  - UKey:         ${UKEY_PORT}"
    echo "  - Alarm:        ${ALARM_PORT}"
    echo "  - Content:      ${CONTENT_PORT}"
    echo "  - Rule:         ${RULE_PORT}"
    echo "  - Forward:      ${FORWARD_PORT}"
    echo "  - Role:         ${ROLE_PORT}"
    echo "  - Registry:     ${REGISTRY_PORT}"
    echo ""
    echo "外部依赖服务:"
    echo "  - MySQL:        ${MYSQL_HOST}:${MYSQL_PORT}"
    echo "  - Redis:        ${REDIS_HOST}:${REDIS_PORT}"
    echo "  - Nacos:        http://${NACOS_HOST}:${NACOS_PORT}"
    echo "  - MinIO:        ${MINIO_ENDPOINT}"
    echo ""
    echo "常用命令:"
    echo "  - 查看日志:     docker-compose logs -f [service-name]"
    echo "  - 停止服务:     docker-compose down"
    echo "  - 重启服务:     docker-compose restart [service-name]"
    echo "  - 查看状态:     docker-compose ps"
    echo ""
    echo "日志文件位置:"
    echo "  - Nginx 日志:   ./nginx/logs/"
    echo ""
    log_success "部署完成！"
}

# =============================================================================
# 显示帮助信息
# =============================================================================
show_help() {
    echo "监控平台一键部署脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help          显示帮助信息"
    echo "  -c, --config        生成配置文件模板"
    echo "  -d, --deploy        仅部署服务"
    echo "  -r, --reload        重新加载配置并重启服务"
    echo "  --check             仅检查环境"
    echo "  --clean             清理所有容器和镜像"
    echo "  --load-images       仅加载镜像"
    echo ""
    echo "示例:"
    echo "  $0                  完整部署"
    echo "  $0 -c               生成配置文件模板"
    echo "  $0 -d               仅部署服务"
    echo "  $0 -r               重新加载配置并重启服务"
    echo "  $0 --check          检查部署环境"
    echo "  $0 --clean          清理所有容器和镜像"
    echo "  $0 --load-images    仅加载镜像"
}

# =============================================================================
# 清理环境
# =============================================================================
clean_environment() {
    log_warn "即将清理所有容器和镜像..."
    read -p "确认继续? (y/n): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "停止所有服务..."
        $DOCKER_COMPOSE down --remove-orphans --volumes 2>/dev/null || true
        
        log_info "删除相关镜像..."
        docker images | grep "monitor-platform" | awk '{print $3}' | xargs -r docker rmi -f 2>/dev/null || true
        
        log_info "清理未使用的资源..."
        docker system prune -f
        
        log_success "清理完成"
    else
        log_info "取消清理操作"
    fi
}

# =============================================================================
# 主函数
# =============================================================================
main() {
    print_banner
    
    # 解析命令行参数
    case "${1:-}" in
        -h|--help)
            show_help
            exit 0
            ;;
        -c|--config)
            generate_config_template
            exit 0
            ;;
        --check)
            load_config
            check_system
            check_ports
            check_external_services
            exit 0
            ;;
        --clean)
            clean_environment
            exit 0
            ;;
        --load-images)
            load_config
            check_system
            check_images
            exit 0
            ;;
        -d|--deploy)
            load_config
            validate_security_config
            check_system
            check_ports
            check_external_services
            check_images
            deploy_services
            check_health
            print_deployment_info
            exit 0
            ;;
        -r|--reload)
            log_info "重新加载配置并重启服务..."
            load_config
            generate_nacos_config
            generate_env_file
            log_info "停止所有服务..."
            $DOCKER_COMPOSE down --remove-orphans 2>/dev/null || true
            log_info "重新启动服务..."
            $DOCKER_COMPOSE up -d
            check_health
            print_deployment_info
            exit 0
            ;;
        "")
            # 完整部署流程
            load_config
            validate_security_config
            check_system
            check_ports
            check_external_services
            check_images
            generate_nacos_config
            generate_env_file
            deploy_services
            check_health
            print_deployment_info
            ask_deploy_publish_gateway
            ;;
        *)
            log_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"
