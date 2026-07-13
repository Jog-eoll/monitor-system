#!/usr/bin/env bash
set -euo pipefail

# ──────────────────────────────────────────────────────────
# build-package.sh  —  assemble one-click installation package
#
# Run on the build server (where Docker images are available).
# Usage:
#   bash build-package.sh [--version VERSION] [--output DIR] [--skip-images]
# ──────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="${VERSION:-1.0.0}"
OUTPUT_DIR="${OUTPUT_DIR:-$SCRIPT_DIR/dist}"
SKIP_IMAGES=false

# ── Parse arguments ──
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)  VERSION="$2"; shift ;;
    --output)   OUTPUT_DIR="$2"; shift ;;
    --skip-images) SKIP_IMAGES=true ;;
    -h|--help)
      echo "Usage: bash build-package.sh [--version VERSION] [--output DIR] [--skip-images]"
      exit 0
      ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

PACKAGE_NAME="monitor-platform-v${VERSION}"
PACKAGE_DIR="$OUTPUT_DIR/$PACKAGE_NAME"
PKG_ROOT="$PACKAGE_DIR/package"

echo "═══════════════════════════════════════"
echo "  Building: $PACKAGE_NAME"
echo "  Output:   $OUTPUT_DIR"
echo "  Skip images: $SKIP_IMAGES"
echo "═══════════════════════════════════════"

# ── Clean previous build ──
if [[ -d "$PACKAGE_DIR" ]]; then
  echo "[1/6] Cleaning previous build..."
  rm -rf "$PACKAGE_DIR"
else
  echo "[1/6] No previous build to clean"
fi

# ── Create directory structure ──
echo "[2/6] Creating package directory structure..."
mkdir -p \
  "$PKG_ROOT/mysql/init" \
  "$PKG_ROOT/mysql/conf" \
  "$PKG_ROOT/nacos/conf" \
  "$PKG_ROOT/nginx/conf" \
  "$PKG_ROOT/nginx/www" \
  "$PKG_ROOT/nginx/certs" \
  "$PKG_ROOT/emqx/etc" \
  "$PKG_ROOT/emqx/etc/certs" \
  "$PKG_ROOT/sdk/lib" \
  "$PKG_ROOT/stage2/lib" \
  "$PKG_ROOT/images"

# ── Copy deployment resources ──
echo "[3/6] Copying deployment resources..."

# docker-compose.yml
cp "$SCRIPT_DIR/docker-compose.yml" "$PKG_ROOT/"

# MySQL init scripts
for f in "$SCRIPT_DIR/mysql/init/"*; do
  [[ -f "$f" ]] && cp "$f" "$PKG_ROOT/mysql/init/"
done
# MySQL conf (if present)
for f in "$SCRIPT_DIR/mysql/conf/"*; do
  [[ -f "$f" ]] && cp "$f" "$PKG_ROOT/mysql/conf/"
done

# Nacos config
cp "$SCRIPT_DIR/nacos/conf/application.properties" "$PKG_ROOT/nacos/conf/" 2>/dev/null || true

# Nginx config and static files (full www/ tree for volume mount)
cp "$SCRIPT_DIR/nginx/conf/nginx.conf" "$PKG_ROOT/nginx/conf/" 2>/dev/null || true
if [[ -d "$SCRIPT_DIR/nginx/www" ]]; then
  cp -r "$SCRIPT_DIR/nginx/www/"* "$PKG_ROOT/nginx/www/" 2>/dev/null || true
fi
# Nginx certs (if present)
for f in "$SCRIPT_DIR/nginx/certs/"*; do
  [[ -f "$f" ]] && cp "$f" "$PKG_ROOT/nginx/certs/"
done 2>/dev/null || true

# EMQX config
cp "$SCRIPT_DIR/emqx/etc/emqx.conf" "$PKG_ROOT/emqx/etc/"
cp "$SCRIPT_DIR/emqx/etc/acl.conf" "$PKG_ROOT/emqx/etc/"
# EMQX certs (copy if pre-generated, otherwise installer generates at deploy time)
for f in "$SCRIPT_DIR/emqx/etc/certs/"*; do
  [[ -f "$f" ]] && cp "$f" "$PKG_ROOT/emqx/etc/certs/"
done 2>/dev/null || true

# SDK (if present)
for f in "$SCRIPT_DIR/sdk/lib/"*; do
  [[ -f "$f" ]] && cp "$f" "$PKG_ROOT/sdk/lib/"
done 2>/dev/null || true

# Stage2 installer
cp "$SCRIPT_DIR/stage2/install.sh" "$PKG_ROOT/stage2/"
cp "$SCRIPT_DIR/stage2/deploy.conf.example" "$PKG_ROOT/stage2/"
for f in "$SCRIPT_DIR/stage2/lib/"*.sh; do
  [[ -f "$f" ]] && cp "$f" "$PKG_ROOT/stage2/lib/"
done

# Top-level install.sh (dual-mode entry)
if [[ -f "$SCRIPT_DIR/install.sh" ]]; then
  cp "$SCRIPT_DIR/install.sh" "$PKG_ROOT/"
fi

# ── Export Docker images ──
echo "[4/6] Exporting Docker images..."

PUBLIC_IMAGES=(
  "nacos/nacos-server:v2.2.0"
  "mysql:5.7"
  "redis:latest"
  "minio/minio:latest"
  "minio/mc:latest"
  "nginx:alpine"
  "emqx/emqx:5.4.0"
)

PRIVATE_IMAGES=(
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

if [[ "$SKIP_IMAGES" == "true" ]]; then
  echo "  Skipped (--skip-images)"
else
  export_image() {
    local image="$1"
    local safe_name
    safe_name="$(echo "$image" | tr '/:' '__')"
    local tar_file="$PKG_ROOT/images/${safe_name}.tar"
    if docker image inspect "$image" >/dev/null 2>&1; then
      echo "  Saving: $image -> images/${safe_name}.tar"
      docker save "$image" -o "$tar_file"
    else
      echo "  WARN: image not found locally: $image (skipped)"
    fi
  }

  for img in "${PUBLIC_IMAGES[@]}"; do
    export_image "$img"
  done
  for img in "${PRIVATE_IMAGES[@]}"; do
    export_image "$img"
  done
fi

# ── Generate README ──
echo "[5/6] Generating README..."
cat > "$PKG_ROOT/README.md" <<EOF
# ${PACKAGE_NAME} — 微服务平台一键安装包

## 快速开始

\`\`\`bash
# 1. 解压安装包
tar -xzf ${PACKAGE_NAME}.tar.gz
cd ${PACKAGE_NAME}/package

# 2. 生成部署配置
cp stage2/deploy.conf.example deploy.conf
vi deploy.conf   # 修改所有密码和配置项

# 3. 执行部署
bash install.sh --stage2
# 或直接:
bash stage2/install.sh
\`\`\`

## 目录结构

\`\`\`
package/
├── docker-compose.yml       # 容器编排文件
├── install.sh               # 顶层入口脚本（双模式）
├── stage2/                  # 第二阶段安装器
│   ├── install.sh
│   ├── deploy.conf.example
│   └── lib/                 # 模块库（8 个）
├── mysql/init/              # 数据库初始化脚本
├── nacos/conf/              # Nacos 配置
├── nginx/                   # Nginx 配置 + 前端静态文件
├── emqx/etc/                # EMQX MQTT 配置
├── sdk/lib/                 # UKey SDK 动态库
├── images/                  # Docker 镜像归档
└── README.md
\`\`\`

## 服务端口

| 服务            | 默认端口       |
|----------------|---------------|
| Nginx HTTP     | 80            |
| Nginx HTTPS    | 443           |
| MySQL          | 23306         |
| Redis          | 26379         |
| Nacos          | 18848         |
| MinIO API      | 19000         |
| EMQX MQTT      | 1883          |
| EMQX MQTTS     | 8883          |
| EMQX Dashboard | 18083         |

## MQTT 下发模式

在 deploy.conf 中设置 \`DISPATCH_MODE\`:
- \`http\` — 传统 HTTP 直连（局域网默认）
- \`mqtt\` — MQTT 通道（公网部署）
- \`dual\` — 双通道灰度发布

## 部署后管理

\`\`\`bash
bash ctl.sh status    # 查看服务状态
bash ctl.sh logs      # 查看日志
bash ctl.sh health    # 健康概览
bash ctl.sh report    # 生成报告
bash ctl.sh update-images /tmp/monitor-platform-v${VERSION}.tar.gz  # 更新业务镜像
\`\`\`

## 版本: ${VERSION}
## 构建时间: $(date '+%Y-%m-%d %H:%M:%S')
EOF

# ── Create tarball ──
echo "[6/6] Creating tarball..."
mkdir -p "$OUTPUT_DIR"
(cd "$OUTPUT_DIR" && tar -czf "${PACKAGE_NAME}.tar.gz" "$PACKAGE_NAME/")

TARBALL="$OUTPUT_DIR/${PACKAGE_NAME}.tar.gz"
SIZE="$(du -sh "$TARBALL" | awk '{print $1}')"

echo
echo "═══════════════════════════════════════"
echo "  Build completed!"
echo "  Package: $TARBALL"
echo "  Size:    $SIZE"
echo "═══════════════════════════════════════"
