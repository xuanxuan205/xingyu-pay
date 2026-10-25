#!/bin/sh
set -eu

APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PID_FILE="$APP_DIR/paypro.pid"
LOG_FILE="$APP_DIR/logs/console.log"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Xingyu Pay is already running: $(cat "$PID_FILE")"
  exit 0
fi

mkdir -p "$APP_DIR/logs"
nohup "$APP_DIR/start.sh" >> "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"
echo "Xingyu Pay started: PID $(cat "$PID_FILE"), log $LOG_FILE"

