#!/usr/bin/env bash
# 以管理员身份把 docs/manuals 下的示例说明书写入知识库 (文件名即商品型号, 如 LT-1001.md)
# 用法: ./scripts/load-sample-docs.sh [网关地址] [管理员密码]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
ADMIN_PASSWORD="${2:-admin123}"
DIR="$(cd "$(dirname "$0")/.." && pwd)/docs/manuals"

TOKEN=$(curl -sS -X POST "$BASE/api/users/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "登录失败, 请检查网关地址和管理员密码"; exit 1; }

for f in "$DIR"/*.md; do
  model="$(basename "$f" .md)"
  echo "上传 $model ..."
  curl -sS -X POST "$BASE/api/ai/knowledge/documents" \
    -H "Authorization: Bearer $TOKEN" \
    -F "modelNo=$model" -F "file=@$f"
  echo
done
