# 故障排查

这里的每一条都来自真实线上事故，按“现象 → 判定 → 处置”写。

约定：

```text
后端目录：/opt/xingyu-pay/backend      （JAR、config/、qr/、.env、start.sh）
前端目录：/opt/xingyu-pay/frontend     （templates/、static/）
服务名  ：paypro
端口    ：127.0.0.1:8889
```

---

## 0. 先跑一遍自检

```bash
cd /opt/xingyu-pay/backend

systemctl is-active paypro
pgrep -fc xingyu-pay-backend.jar
ss -lntp | grep ':8889'
sha256sum xingyu-pay-backend.jar

curl -s -o /dev/null -w 'home=%{http_code}\n'     http://127.0.0.1:8889/home.html
curl -s -o /dev/null -w 'review=%{http_code}\n'   'http://127.0.0.1:8889/review?id=00000000000000000000000000000000&t=00000000000000000000000000000000'
curl -s -o /dev/null -w 'manifest=%{http_code}\n' http://127.0.0.1:8889/site.webmanifest
curl -s -D - -o /dev/null http://127.0.0.1:8889/home.html | grep -Ei 'content-security-policy|x-frame-options'
```

健康的样子：

```text
active
1                       <- 进程数必须是 1
127.0.0.1:8889 只有一个 java
home=200  review=200  manifest=200
X-Frame-Options: DENY / Content-Security-Policy: ...
```

任何一项不对，往下看对应章节。

---

## 1. 新功能接口 404，但 `sha256sum` 明明是新的

### 现象

```text
$ sha256sum xingyu-pay-backend.jar
7007f7c2...b0dd7af  xingyu-pay-backend.jar     <- 是对的

$ unzip -l xingyu-pay-backend.jar | grep -c 'SiteAssetController'
2                                              <- 类在包里

$ curl -s -o /dev/null -w '%{http_code}' 'http://127.0.0.1:8889/review?id=...&t=...'
404                                            <- 但接口 404
```

而且 404 的响应头长这样：

```text
HTTP/1.1 404
Content-Type: text/html;charset=UTF-8
Content-Language: en-US
Vary: Accept-Encoding
Transfer-Encoding: chunked
```

这是 **Spring Boot 自己的错误页**，不是 Nginx 的 404。说明请求已经进到 Java 进程，
只是进程里没有这条路由。

### 原因 A：进程没换血（最常见）

Java 启动时打开了 JAR 文件。你用 `mv` / 覆盖上传替换 JAR 后，**旧进程手里还是旧
inode**，`sha256sum` 看的是新文件，进程跑的仍是旧 class。`systemctl restart` 如果
没能把旧进程杀掉，就会出现这种“文件新、行为旧”。

判定：

```bash
for p in $(pgrep -f xingyu-pay-backend.jar); do
  echo "PID $p start=$(ps -o lstart= -p $p)"
done
stat -c 'jar_mtime=%y' xingyu-pay-backend.jar
ss -lntp | grep ':8889'
```

**进程的 start 时间早于 jar_mtime，就是它。**

处置：

```bash
systemctl stop paypro
sleep 3
pkill -f xingyu-pay-backend.jar
sleep 2
ss -lntp | grep ':8889' || echo PORT_FREE
systemctl start paypro
sleep 15
pgrep -fc xingyu-pay-backend.jar      # 必须是 1
```

或直接 `sh deploy/restart-clean.sh`（做的是同一件事，外加探测）。

### 原因 B：同一个 JAR 有两个 systemd 单元

```bash
$ systemctl list-units --type=service --all | grep -iE 'pay|java'
paypro.service         loaded activating auto-restart
spring_paypro.service  loaded active     running
```

宝塔「网站 → Java项目」会给 JAR 生成 `spring_<名称>.service`。如果它和手写的
`paypro.service` 同时存在，就是两个 java 抢同一个 8889：

- 先抢到端口的那个在应答，它用的是**它启动那一刻**的 class；
- 另一个每次启动都 `Port 8889 was already in use` 退出，被 `Restart=always` 拉起，
  于是长期停在 `activating (auto-restart)`，`NRestarts` 一直涨。

判定：

```bash
systemctl show -p ActiveState -p SubState -p MainPID -p NRestarts paypro
systemctl is-enabled paypro spring_paypro
ps -eo pid,ppid,lstart,args | grep '[x]ingyu-pay-backend.jar'
ss -lntp | grep ':8889'
```

处置（保留 `paypro.service`，它 `User=www` + `EnvironmentFile=.env`）：

```bash
sh deploy/dedupe-service.sh
```

等价的四条：

```bash
systemctl stop spring_paypro
systemctl disable spring_paypro
systemctl reset-failed paypro
systemctl restart paypro
```

> `systemctl disable` 只是删掉 `multi-user.target.wants` 里的软链，unit 文件还在
> `/usr/lib/systemd/system/`。**必须在宝塔面板「网站 → Java项目」里把那个项目删掉**，
> 否则面板重启或在面板里点一次「启动」，它又回来了。

宝塔生成的单元长这样，注意它杀不干净 Java：

```ini
Type=forking
ExecStart=/bin/bash /var/tmp/springboot/vhost/scripts/paypro.sh
ExecStop=/usr/bin/pkill -9 "/bin/bash /var/tmp/springboot/vhost/scripts/paypro.sh"
PIDFile=/var/tmp/springboot/vhost/pids/paypro.pid
Restart=always
```

`ExecStop` 只杀那个 bash 脚本，不认它 fork 出来的 java，所以换包后必须
`pkill -f xingyu-pay-backend.jar` 才能真松端口。

---

## 2. `systemctl status paypro` 显示 `activating (auto-restart)`

`activating` 不是“正在启动”，是**崩溃—重启循环**。看两处：

```bash
systemctl show -p NRestarts --value paypro        # 数字很大 = 一直在重启
journalctl -u paypro -n 80 --no-pager
```

日志里出现这些就走对应章节：

| 日志 | 含义 | 去处 |
| --- | --- | --- |
| `Port 8889 was already in use` | 有另一个进程占着端口 | 第 1 节 原因 B |
| `Failed to load environment files` | `.env` 路径不对或读不到 | 见下 |
| `ADMIN_TOKEN must be a random value with at least 32 characters` | `.env` 还是占位符 | 见下 |
| `ADMIN_TOKEN and OPENAPI_SECRET must be different` | 两个密钥写成了同一个 | 见下 |
| `DB_PASSWORD must be set to a non-placeholder value` | 数据库密码是 `change-me` | 见下 |

`.env` 的三条硬性要求（`prod` 下启动即校验，不满足直接退出）：

```bash
grep -E '^(ADMIN_TOKEN|OPENAPI_SECRET|DB_PASSWORD)=' /opt/xingyu-pay/backend/.env
# 两个 token 必须 >= 32 位、不能是 change-me、且互不相同
openssl rand -hex 32   # 生成用
chmod 600 .env
```

`.env` 被 `EnvironmentFile=` 读取，写法限制：**不要写 `export`、不要有行内注释、
值里有空格要加引号**。

---

## 3. `/site.webmanifest` 404 或 PWA 图标不出来

这个文件由后端 `SiteAssetController` 直接读取前端目录，所以两个前提：

```bash
# 1) 文件真的在
ls -l /opt/xingyu-pay/frontend/static/site.webmanifest

# 2) 后端知道前端目录在哪
grep -E '^FRONTEND_DIR=' /opt/xingyu-pay/backend/.env
```

`FRONTEND_DIR` 写错 → manifest 404；写对 → 200 且 `Content-Type: application/manifest+json`。

```bash
curl -sI http://127.0.0.1:8889/site.webmanifest | head -3
```

改了 `.env` 要 `systemctl restart paypro` 才生效（`EnvironmentFile` 在启动时读一次）。

---

## 4. 前端改了，页面没变

模板和静态资源是运行时从 `FRONTEND_DIR` 读的（`spring.thymeleaf.prefix=file:...`、
`spring.resources.static-locations=file:...`），所以**改前端不用重新打 JAR**，也不用
重启后端。但：

1. 只上传 `templates/` 和 `static/` 两个目录，不要把 `.env`、JAR、日志放进站点根目录；
2. 浏览器强刷 `Ctrl + F5`（CSS 带版本号时也要刷）；
3. 确认 Nginx 的 `root` 指向的是同一个前端目录。

```bash
ls -l /opt/xingyu-pay/frontend/templates/review.html
curl -s http://127.0.0.1:8889/review?id=x | head -5   # 直接看后端渲染结果
```

---

## 5. 审核邮件里的旧链接打开没反应

旧版邮件里的链接是：

```text
/order/pass?id=...&token=...&myToken=...
```

新版审核页是：

```text
/review?id=...&t=...
```

`/order/pass` 保留了兼容入口：GET 只做校验 + 换发新的 `review` token 然后 302 到
`/review`，**绝不在 GET 里改订单状态**。token 过期会显示“审核链接无效或已过期”。

如果打开后停在“操作未完成”，说明 token 已经过期（默认 30 分钟）或订单已处理，
重新发起一笔即可，不需要改代码。

---

## 6. 支付宝提示“对方账户未注册或未激活”

这是收款账号本身的问题，不是程序问题。检查 `.env`：

```bash
grep -E '^(ALIPAY_USER_ID|ALIPAY_CUSTOM_QR_URL|PAYEE_NAME)=' /opt/xingyu-pay/backend/.env
```

- `ALIPAY_USER_ID` 必须是 **2088 开头**的支付宝用户 ID，不能填开放平台的应用 ID
  （`2021...` 那种）。搞错了就会报“账户未注册”。
- 用手机支付宝「我的 → 头像 → 支付宝账号」或扫码登录后查看 user_id 核对。
- 页面只会拉起 `alipay.com` 的转账页，金额由服务端锁定，用户改不了。

改完 `.env` 记得 `systemctl restart paypro`。

---

## 7. 服务器上 `curl https://你的域名` 报证书错误

```text
curl: (60) Certificate key usage inadequate for attempted operation.
```

这是服务器自己的 CA 包太旧，**浏览器访问不受影响**。要么临时 `curl -k`，要么更新：

```bash
yum update -y ca-certificates && update-ca-trust
# Debian/Ubuntu: apt-get update && apt-get install -y ca-certificates
```

---

## 8. 部署检查清单

上线前逐条打勾：

- [ ] `.env` 权限 `600`，属主 `www`，不在站点根目录
- [ ] `ADMIN_TOKEN` / `OPENAPI_SECRET` 各 64 位随机、互不相同、已轮换过
- [ ] `DB_PASSWORD` 不是默认值；MySQL 只监听需要的最小范围
- [ ] Redis 绑 `127.0.0.1` 且有密码（或至少不对外）
- [ ] 后端只监听 `127.0.0.1:8889`，公网只开 `80/443`
- [ ] `pgrep -fc xingyu-pay-backend.jar` 结果恒为 `1`
- [ ] `systemctl is-active paypro` 是 `active`，不是 `activating`
- [ ] `NRestarts` 不再增长
- [ ] `home / review / site.webmanifest` 三个都 200
- [ ] `X-Frame-Options: DENY`、`Content-Security-Policy` 存在
- [ ] 站点根目录没有 `.env`、`.jar`、`.sql`、`.log`、`.git`
- [ ] 宝塔「Java项目」里没有重复托管同一个 JAR 的项目
- [ ] 数据库有备份，且备份不在 Web 目录

---

## 9. 不要做的事

| 不要 | 原因 |
| --- | --- |
| 把 JAR 放进网站根目录 | 可被直接下载，泄露全部代码与配置默认值 |
| 用 `git push` 带上 `.env` | 密钥一旦进历史就很难彻底清除，必须全部轮换 |
| 同时开 systemd 和宝塔 Java 项目 | 两个进程抢端口，症状是“时好时坏” |
| 只 `systemctl restart` 就认为换了新包 | 旧进程可能还活着，见第 1 节 |
| 用 GET 请求直接改订单状态 | 邮件安全扫描器会替你点“通过” |
| 在生产用 `local` profile | 它用 H2 + 本地假 Redis/SMTP，只用于演示 |
