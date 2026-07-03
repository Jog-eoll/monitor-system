#!/usr/bin/env bash

# ========== publish-gateway 顶层入口脚本 ==========
# 用于调用 stage2/install.sh 进行部署

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STAGE2_SCRIPT="$SCRIPT_DIR/stage2/install.sh"

# 检查 stage2 脚本是否存在
if [[ ! -f "$STAGE2_SCRIPT" ]]; then
  echo "[ERROR] 安装脚本不存在: $STAGE2_SCRIPT"
  echo "请确保安装包完整解压"
  exit 1
fi

# 传递所有参数给 stage2 脚本
bash "$STAGE2_SCRIPT" "$@"
