#!/bin/sh
# 星屿支付 · 固定金额二维码上线前检查
set -eu

APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE=${1:-"$APP_DIR/.env"}
CONFIG_FILE=${2:-"$APP_DIR/config/application-prod.yml"}
FAIL=0

value_of() {
  sed -n "s/^$1=//p" "$ENV_FILE" 2>/dev/null | tail -n 1 | sed "s/^'//;s/'$//"
}

if [ ! -f "$ENV_FILE" ]; then
  echo "QR_CHECK=FAIL"
  echo "缺少环境变量文件：$ENV_FILE"
  exit 1
fi
if [ ! -f "$CONFIG_FILE" ]; then
  echo "QR_CHECK=FAIL"
  echo "缺少生产配置：$CONFIG_FILE"
  exit 1
fi

QR_DIR=$(value_of QR_DIR)
if [ -z "$QR_DIR" ]; then
  QR_DIR="$APP_DIR/qr"
fi

if [ ! -d "$QR_DIR" ]; then
  echo "QR_CHECK=FAIL"
  echo "二维码目录不存在：$QR_DIR"
  exit 1
fi

AMOUNTS=$(awk '
  /^  products:[[:space:]]*$/ { in_products=1; next }
  in_products && /^  [A-Za-z]/ { in_products=0 }
  in_products && /^[[:space:]]+money:[[:space:]]*/ {
    line=$0
    sub(/^[^:]*:[[:space:]]*/, "", line)
    gsub(/"/, "", line)
    print line
  }
' "$CONFIG_FILE" | sed '/^[[:space:]]*$/d')

if [ -z "$AMOUNTS" ]; then
  echo "QR_CHECK=FAIL"
  echo "未能从 $CONFIG_FILE 解析出商品金额"
  exit 1
fi

echo "QR_DIR=$QR_DIR"
COUNT=0
for AMOUNT in $AMOUNTS; do
  COUNT=$((COUNT + 1))
  FOUND=0
  for NUM in 1 2; do
    if [ -f "$QR_DIR/wechat/$AMOUNT/$NUM.png" ]; then
      FOUND=1
      echo "OK   wechat/$AMOUNT/$NUM.png"
    fi
  done
  if [ "$FOUND" -ne 1 ]; then
    echo "FAIL wechat/$AMOUNT/ 缺少 1.png 或 2.png"
    FAIL=1
  fi
done

if [ "$COUNT" -eq 0 ]; then
  echo "QR_CHECK=FAIL"
  echo "商品金额数量为 0"
  exit 1
fi

if [ "$FAIL" -ne 0 ]; then
  echo "QR_CHECK=FAIL"
  echo "补齐二维码后重试：$QR_DIR/wechat/{金额}/1.png"
  exit 1
fi

echo "QR_CHECK=PASS amounts=$COUNT"
