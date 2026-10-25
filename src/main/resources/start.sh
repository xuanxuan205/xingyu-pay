#!/bin/sh
set -eu

LANG=zh_CN.UTF-8
APP_DIR=/app/appsystems
LOG_DIR=/app/applogs
DUMP_DIR=$LOG_DIR/dump
SERVER_PORT=${SERVER_PORT:-8889}
PROFILES=${SPRING_PROFILES_ACTIVE:-${SPRING_PROFILES:-prod}}

mkdir -p "$LOG_DIR" "$DUMP_DIR" "$APP_DIR/config"

if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  JAVA="${JAVA_HOME}/bin/java"
else
  JAVA=java
fi

# The original option list contained -XX:+UseFastAccessorMethods (removed in
# Java 8) and an unauthenticated JMX port. Keep the runtime options portable.
JAVA_MEM_OPTS="${JVM_HEAP_SIZE:--Xms256m -Xmx512m}"
JAVA_OPTIONS="$JAVA_MEM_OPTS -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=$DUMP_DIR/start-$(date +%Y%m%d-%H%M%S).hprof \
  -Djava.net.preferIPv4Stack=true -Dfile.encoding=UTF-8"

JAR=$(ls "$APP_DIR"/*.jar 2>/dev/null | head -n 1)
if [ -z "$JAR" ]; then
  echo "No application jar found in $APP_DIR" >&2
  exit 1
fi

CLASSPATH="$APP_DIR/config/:$JAR"

export LANG
if [ -n "${JAVA_HOME:-}" ]; then
  export JAVA_HOME
fi
echo "Starting $JAR with profile $PROFILES on port $SERVER_PORT"
exec $JAVA $JAVA_OPTIONS -classpath "$CLASSPATH" \
  org.springframework.boot.loader.JarLauncher \
  --server.port="$SERVER_PORT" --spring.profiles.active="$PROFILES"
