#!/bin/sh
# 星屿支付 · 生产 .env 上线前检查
set -eu

APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE=${1:-"$APP_DIR/.env"}
FAIL=0

if [ ! -f "$ENV_FILE" ]; then
  echo "ENV_CHECK=FAIL"
  echo "缺少文件：$ENV_FILE"
  exit 1
fi

value_of() {
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | sed "s/^'//;s/'$//"
}

check_value() {
  name=$1
  value=$(value_of "$name")
  case "$value" in
    ""|CHANGE_ME*|change-me*|replace-with*|*'？'*|*'?'*)
      echo "FAIL $name 仍是空值、占位符或带问号的未完成值"
      FAIL=1
      ;;
    *)
      echo "OK   $name"
      ;;
  esac
}

check_value DB_PASSWORD
check_value ADMIN_TOKEN
check_value OPENAPI_SECRET
check_value MAIL_PASSWORD

ADMIN_TOKEN=$(value_of ADMIN_TOKEN)
OPENAPI_SECRET=$(value_of OPENAPI_SECRET)
if [ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" = "$OPENAPI_SECRET" ]; then
  echo "FAIL ADMIN_TOKEN 与 OPENAPI_SECRET 不能相同"
  FAIL=1
else
  echo "OK   ADMIN_TOKEN / OPENAPI_SECRET 已分离"
fi

FRONTEND_DIR=$(value_of FRONTEND_DIR)
if [ -n "$FRONTEND_DIR" ] && [ ! -d "$FRONTEND_DIR" ]; then
  echo "FAIL FRONTEND_DIR 目录不存在：$FRONTEND_DIR"
  FAIL=1
else
  echo "OK   FRONTEND_DIR=$FRONTEND_DIR"
fi

QR_DIR=$(value_of QR_DIR)
if [ -n "$QR_DIR" ]; then
  echo "INFO QR_DIR=$QR_DIR"
fi

if [ -x "$APP_DIR/check-qr.sh" ] || [ -f "$APP_DIR/check-qr.sh" ]; then
  if ! /bin/sh "$APP_DIR/check-qr.sh" "$ENV_FILE" "$APP_DIR/config/application-prod.yml"; then
    FAIL=1
  fi
else
  echo "WARN 缺少 check-qr.sh，跳过二维码检查"
fi

MAIL_HOST=$(value_of MAIL_HOST)
MAIL_SSL_TRUST=$(value_of MAIL_SSL_TRUST)
if [ "$MAIL_HOST" = "smtp.qq.com" ] && [ "$MAIL_SSL_TRUST" != "smtp.qq.com" ]; then
  echo "FAIL QQ 邮箱的 MAIL_SSL_TRUST 必须是 smtp.qq.com"
  FAIL=1
else
  echo "OK   MAIL_HOST=$MAIL_HOST / MAIL_SSL_TRUST=$MAIL_SSL_TRUST"
fi

ALIPAY_USER_ID=$(value_of ALIPAY_USER_ID)
case "$ALIPAY_USER_ID" in
  2088*) echo "OK   ALIPAY_USER_ID 格式看起来正确" ;;
  *) echo "WARN ALIPAY_USER_ID 通常以 2088 开头，请确认不是支付宝应用 ID" ;;
esac

ALIPAY_CUSTOM_QR_URL=$(value_of ALIPAY_CUSTOM_QR_URL)
if [ -n "$ALIPAY_CUSTOM_QR_URL" ]; then
  case "$ALIPAY_CUSTOM_QR_URL" in
    https://*) echo "OK   ALIPAY_CUSTOM_QR_URL 是 HTTPS 链接" ;;
    *) echo "WARN ALIPAY_CUSTOM_QR_URL 不是 HTTPS 链接" ;;
  esac
fi

MODE=$(stat -c '%a' "$ENV_FILE" 2>/dev/null || echo unknown)
if [ "$MODE" != "600" ]; then
  echo "WARN .env 权限是 $MODE，建议执行：chmod 600 $ENV_FILE"
else
  echo "OK   .env 权限 600"
fi

if [ "$FAIL" -ne 0 ]; then
  echo "ENV_CHECK=FAIL"
  exit 1
fi

echo "ENV_CHECK=PASS"
