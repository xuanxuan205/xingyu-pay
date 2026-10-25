#!/bin/sh
# 彻底重启后端：stop -> 清掉残留 java -> 确认端口释放 -> start -> 探测
# 用法：sh restart-clean.sh
# 为什么需要：只执行 systemctl restart 时，若上一次的 java 进程没有真正退出
# （例如被宝塔 Java 项目的 Type=forking 守护拉起过），新进程会绑定失败并进入
# auto-restart 循环，而旧进程继续用它启动时那一刻的 class 应答，表现为
# “磁盘上的 JAR 是新的、接口却还是 404”。
set -u

SERVICE="${SERVICE:-paypro}"
APP_DIR="${APP_DIR:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)}"
PORT="${SERVER_PORT:-8889}"
JAR_NAME="${JAR_NAME:-xingyu-pay-backend.jar}"
EXPECTED="${JAR_SHA256:-}"

say() { printf '%s\n' "$*"; }

say "== 1. 磁盘上的包 =="
if [ -f "$APP_DIR/$JAR_NAME" ]; then
  say "mtime : $(date -r "$APP_DIR/$JAR_NAME" '+%F %T' 2>/dev/null || echo unknown)"
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL=$(sha256sum "$APP_DIR/$JAR_NAME" | awk '{print $1}')
    say "sha256: $ACTUAL"
    if [ -n "$EXPECTED" ]; then
      [ "$ACTUAL" = "$(printf '%s' "$EXPECTED" | tr 'A-Z' 'a-z')" ] \
        && say "OK   与 JAR_SHA256 一致" \
        || say "WARN 与 JAR_SHA256 不一致，检查是否传错包"
    fi
  fi
else
  say "FAIL 找不到 $APP_DIR/$JAR_NAME"
fi

say "== 2. 重启前的进程 =="
for p in $(pgrep -f "$JAR_NAME" 2>/dev/null); do
  say "PID $p 启动于 $(ps -o lstart= -p "$p" 2>/dev/null | sed 's/^ *//')"
done
ss -lntp 2>/dev/null | grep ":$PORT" || say "端口 $PORT 空闲"

say "== 3. 彻底停止 =="
systemctl stop "$SERVICE" || say "WARN systemctl stop 返回非 0"
sleep 3
pkill -f "$JAR_NAME" 2>/dev/null && say "已清理残留 java" || say "无残留 java"
sleep 2
if ss -lntp 2>/dev/null | grep -q ":$PORT"; then
  say "FAIL 端口 $PORT 仍被占用："
  ss -lntp | grep ":$PORT"
  exit 1
fi
say "OK   端口 $PORT 已释放"

say "== 4. 启动 =="
systemctl start "$SERVICE"
i=0
while [ "$i" -lt 40 ]; do
  i=$((i + 1))
  sleep 1
  if ss -lntp 2>/dev/null | grep -q ":$PORT"; then break; fi
done
systemctl is-active "$SERVICE" || { journalctl -u "$SERVICE" -n 40 --no-pager; exit 1; }

say "== 5. 新进程 =="
for p in $(pgrep -f "$JAR_NAME" 2>/dev/null); do
  say "PID $p 启动于 $(ps -o lstart= -p "$p" 2>/dev/null | sed 's/^ *//')"
  ls -l "/proc/$p/fd" 2>/dev/null | grep -i '\.jar' | sed 's/^/  /'
done

say "== 6. 探测 =="
FAILED=""
probe() {
  code=$(curl -s -o /dev/null -w '%{http_code}' -m 8 "$1" || echo 000)
  say "  $2 -> $code"
  [ "$code" = "200" ] || FAILED="$FAILED $2:$code"
}
probe "http://127.0.0.1:$PORT/home.html" "home"
probe "http://127.0.0.1:$PORT/review?id=00000000000000000000000000000000&t=00000000000000000000000000000000" "review"
probe "http://127.0.0.1:$PORT/site.webmanifest" "manifest"

HDRS=$(curl -s -D - -o /dev/null -m 8 "http://127.0.0.1:$PORT/home.html" || true)
for H in Content-Security-Policy X-Frame-Options X-Content-Type-Options; do
  printf '%s\n' "$HDRS" | grep -qi "^$H:" && say "  OK 响应头 $H" || { say "  MISS 响应头 $H"; FAILED="$FAILED $H"; }
done

if [ -n "$FAILED" ]; then
  say "RESULT=FAIL:$FAILED"
  exit 1
fi
say "RESULT=OK"
