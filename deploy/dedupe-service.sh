#!/bin/sh
# 只保留一个守护者：停用重复的 systemd 单元，让 KEEP 独占端口
#
# 典型场景：宝塔「网站 → Java项目」会给同一个 JAR 生成 spring_<名称>.service，
# 于是一个 JAR 有两个 unit、两个 java 进程抢同一个 8889：
#   先抢到端口的那个用“它启动那一刻”的 class 应答；
#   另一个每次启动都 Port already in use 退出，被 Restart=always 拉起，长期停留
#   在 activating (auto-restart)。
# 表现就是：磁盘上 JAR 换了新的，接口却还是旧行为（例如 /review 404）。
#
# 用法：sh dedupe-service.sh
# 反向：KEEP=spring_paypro DROP=paypro sh dedupe-service.sh
set -u

KEEP="${KEEP:-paypro}"
DROP="${DROP:-spring_paypro}"
PORT="${SERVER_PORT:-8889}"
JAR_NAME="${JAR_NAME:-xingyu-pay-backend.jar}"

say() { printf '%s\n' "$*"; }

say "== 1. 当前状态 =="
for u in "$KEEP" "$DROP"; do
  systemctl cat "$u" >/dev/null 2>&1 || { say "FAIL 找不到 unit: $u"; exit 1; }
  say "$u : active=$(systemctl is-active "$u" 2>/dev/null) enabled=$(systemctl is-enabled "$u" 2>/dev/null)"
done
say "$PORT : $(ss -lntp 2>/dev/null | grep ":$PORT" || echo 空闲)"
say "进程 : $(pgrep -fc "$JAR_NAME" 2>/dev/null || echo 0)"

say "== 2. 停用 $DROP =="
systemctl stop "$DROP" || say "WARN stop 返回非 0"
systemctl disable "$DROP" 2>&1 | sed 's/^/  /'
sleep 3
if systemctl is-active --quiet "$DROP"; then
  say "FAIL $DROP 仍在运行"
  exit 1
fi
say "OK   $DROP 已停止并禁用（要永久生效，请在宝塔面板删除对应 Java 项目）"

say "== 3. 复位并重启 $KEEP =="
systemctl reset-failed "$KEEP" 2>/dev/null || true
systemctl restart "$KEEP"
i=0
while [ "$i" -lt 40 ]; do
  i=$((i + 1))
  sleep 1
  if systemctl is-active --quiet "$KEEP" && ss -lntp 2>/dev/null | grep -q ":$PORT"; then
    break
  fi
done

COUNT=$(pgrep -fc "$JAR_NAME" 2>/dev/null || echo 0)
say "状态 : $(systemctl is-active "$KEEP")"
say "进程数: $COUNT"

if [ "$COUNT" != "1" ]; then
  say "自动回退：重新启用 $DROP"
  systemctl enable --now "$DROP" >/dev/null 2>&1 || true
  say "RESULT=FAIL_PROCESS_COUNT_$COUNT"
  exit 1
fi

say "== 4. 探测 =="
FAILED=""
probe() {
  code=$(curl -s -o /dev/null -w '%{http_code}' -m 8 "$1" || echo 000)
  say "  $2 -> $code"
  [ "$code" = "200" ] || FAILED="$FAILED $2:$code"
}
probe "http://127.0.0.1:$PORT/home.html" "home"
probe "http://127.0.0.1:$PORT/review?id=00000000000000000000000000000000&t=00000000000000000000000000000000" "review"
probe "http://127.0.0.1:$PORT/site.webmanifest" "manifest"

if [ -n "$FAILED" ]; then
  say "RESULT=FAIL:$FAILED"
  exit 1
fi

say "NRestarts=$(systemctl show -p NRestarts --value "$KEEP")"
say "RESULT=OK"
