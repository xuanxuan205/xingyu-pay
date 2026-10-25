#!/bin/sh
set -eu

APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$APP_DIR"

if [ -f "$APP_DIR/.env" ]; then
  set -a
  . "$APP_DIR/.env"
  set +a
fi

JAR="$APP_DIR/xingyu-pay-backend.jar"
LOG_DIR="$APP_DIR/logs"
PROFILE=${SPRING_PROFILES_ACTIVE:-prod}
ADDRESS=${SERVER_ADDRESS:-127.0.0.1}
PORT=${SERVER_PORT:-8889}
HEAP=${JVM_HEAP_SIZE:--Xms256m -Xmx512m}
FRONTEND_DIR=${FRONTEND_DIR:-$APP_DIR/frontend}

mkdir -p "$LOG_DIR"

# 可选：.env 里设置 JAR_SHA256 时，启动前校验 JAR 内容。
# 磁盘上文件名对、内容是旧包，是线上最常见的事故，这里直接拒绝启动。
if [ -n "${JAR_SHA256:-}" ]; then
  if [ ! -f "$JAR" ]; then
    echo "start.sh: JAR 不存在: $JAR" >&2
    exit 78
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256=$(sha256sum "$JAR" | awk '{print $1}' | tr 'A-Z' 'a-z')
  else
    ACTUAL_SHA256=$(openssl dgst -sha256 "$JAR" | awk '{print $NF}' | tr 'A-Z' 'a-z')
  fi
  EXPECTED_SHA256=$(printf '%s' "$JAR_SHA256" | tr 'A-Z' 'a-z')
  if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
    echo "start.sh: JAR 校验失败，拒绝启动（很可能上传的是旧包）" >&2
    echo "  expected: $EXPECTED_SHA256" >&2
    echo "  actual  : $ACTUAL_SHA256" >&2
    exit 78
  fi
  echo "start.sh: JAR 校验通过 $ACTUAL_SHA256"
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

export FRONTEND_DIR

exec "$JAVA" $HEAP \
  -Djava.net.preferIPv4Stack=true \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=Asia/Shanghai \
  -jar "$JAR" \
  --server.address="$ADDRESS" \
  --server.port="$PORT" \
  --spring.profiles.active="$PROFILE" \
  --spring.config.additional-location="file:$APP_DIR/config/"

