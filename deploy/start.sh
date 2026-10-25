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

