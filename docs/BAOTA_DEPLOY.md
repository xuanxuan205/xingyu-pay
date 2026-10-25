# 宝塔面板部署教程

本文以宝塔 Linux 面板为例，部署：

- 前端：`/www/wwwroot/pay.example.com`
- 后端：`/www/wwwroot/xingyu-pay-backend`
- 域名：`pay.example.com`
- 后端监听：`127.0.0.1:8889`
- 对外入口：Nginx `443` 反向代理到 `8889`

> 将示例域名、目录名和密码替换成自己的值。不要把 `.env`、JAR、数据库备份放到站点根目录。

## 1. 最终目录

```text
/www/wwwroot/pay.example.com/
├── templates/
│   ├── home.html
│   ├── recharge.html
│   └── ...
└── static/
    ├── css/
    ├── assets/
    └── site.webmanifest

/www/wwwroot/xingyu-pay-backend/
├── xingyu-pay-backend.jar
├── config/
│   └── application-prod.yml
├── qr/
│   ├── alipay/
│   └── wechat/
├── logs/
├── start.sh
├── stop.sh
├── check-env.sh
├── check-qr.sh
└── .env
```

前端目录会被 Spring Boot 以 `FRONTEND_DIR` 读取；Nginx 只负责 HTTPS、反向代理和请求头。

## 2. 域名和 DNS

在域名服务商添加 A 记录：

```text
主机记录：pay
记录类型：A
记录值：你的服务器公网 IPv4
```

等待解析生效：

```bash
ping -c 3 pay.example.com
```

## 3. 宝塔安装软件

在宝塔「软件商店」安装：

- Nginx 1.22+
- MySQL 5.7 或 8.0
- Redis 6+
- Java 管理器 / JDK 1.8
- 可选：Maven 3.6+，也可以在自己的电脑构建后上传 JAR

确认版本：

```bash
nginx -v
mysql --version
redis-server --version
/www/server/java/jdk1.8.0_371/bin/java -version
```

## 4. 创建站点

宝塔「网站」→「添加站点」：

```text
域名：pay.example.com
根目录：/www/wwwroot/pay.example.com
PHP：纯静态
数据库：不在这里创建
```

创建后先不要放任何源码到根目录，后面只上传 `templates/` 和 `static/`。

## 5. 创建数据库和用户

宝塔「数据库」→「添加数据库」：

```text
数据库名：pay
用户名：pay
密码：使用随机强密码
字符集：utf8mb4
```

导入表结构：

```bash
mysql -u pay -p pay < /www/wwwroot/xingyu-pay-backend/deploy/database.sql
```

如果 `database.sql` 不在后端目录，先从仓库的 `deploy/database.sql` 上传。

检查：

```bash
mysql -u pay -p -e "USE pay; SHOW TABLES;"
```

## 6. 安装并加固 Redis

宝塔「软件商店」→ Redis → 设置：

```text
端口：6379
绑定：127.0.0.1
密码：设置一个随机强密码
```

命令行确认只监听本机：

```bash
ss -lntp | grep 6379
```

期望看到 `127.0.0.1:6379`，不要看到 `0.0.0.0:6379`。

## 7. 上传前端

把仓库的 `frontend/` 上传到站点根目录，最终结构必须是：

```text
/www/wwwroot/pay.example.com/templates/home.html
/www/wwwroot/pay.example.com/static/css/common.css
/www/wwwroot/pay.example.com/static/site.webmanifest
```

不要上传：

```text
.env
target/
*.jar
logs/
data/
qr/
```

设置权限：

```bash
chown -R www:www /www/wwwroot/pay.example.com
find /www/wwwroot/pay.example.com -type d -exec chmod 755 {} \;
find /www/wwwroot/pay.example.com -type f -exec chmod 644 {} \;
```

## 8. 上传后端

创建后端目录：

```bash
mkdir -p /www/wwwroot/xingyu-pay-backend/{config,qr,logs}
```

上传以下文件：

```text
xingyu-pay-backend.jar
config/application-prod.yml
deploy/start.sh
deploy/stop.sh
deploy/check-env.sh
deploy/check-qr.sh
qr/                        # 真实二维码按金额目录放好
```

构建 JAR：

```bash
mvn clean package -DskipTests
```

构建产物：

```text
target/xingyu-pay-backend.jar
```

设置权限：

```bash
chown -R www:www /www/wwwroot/xingyu-pay-backend
chmod 755 /www/wwwroot/xingyu-pay-backend/*.sh
chmod 600 /www/wwwroot/xingyu-pay-backend/.env
```

## 9. 配置 `.env`

复制模板：

```bash
cp /www/wwwroot/xingyu-pay-backend/deploy/baota/env.example \
   /www/wwwroot/xingyu-pay-backend/.env
chmod 600 /www/wwwroot/xingyu-pay-backend/.env
```

最少修改这些值：

```text
SITE_URL=https://pay.example.com
FRONTEND_DIR=/www/wwwroot/pay.example.com
QR_DIR=/www/wwwroot/xingyu-pay-backend/qr
DB_PASSWORD=你的数据库密码
REDIS_PASSWORD=你的Redis密码
ADMIN_TOKEN=64位随机十六进制
OPENAPI_SECRET=另一组64位随机十六进制
MAIL_USERNAME=你的发件邮箱
MAIL_PASSWORD=邮箱SMTP授权码
MAIL_FROM=你的发件邮箱
MAIL_TO=接收审核通知的邮箱
SUPPORT_MAIL=页面展示的支持邮箱
ALIPAY_USER_ID=你的支付宝用户ID
```

生成两组不同的随机密钥：

```bash
openssl rand -hex 32
openssl rand -hex 32
```

`ADMIN_TOKEN` 和 `OPENAPI_SECRET` 不能相同。生产环境启动时会检查，占位符或短于 32 字符会直接拒绝启动。

如果值里有 `&`、`?`、`#`，用单引号包住：

```text
DB_URL='jdbc:mysql://127.0.0.1:3306/pay?useSSL=false&characterEncoding=utf-8&serverTimezone=Asia/Shanghai'
```

## 10. 二维码目录

每个商品金额必须有对应目录：

```text
qr/wechat/5.00/1.png
qr/wechat/5.00/2.png
qr/wechat/10.00/1.png
qr/wechat/29.00/1.png
```

金额必须与 `config/application-prod.yml` 里的 `products[].money` 完全一致。

检查：

```bash
/www/wwwroot/xingyu-pay-backend/check-qr.sh
```

## 11. 启动后端

```bash
cd /www/wwwroot/xingyu-pay-backend
./check-env.sh
./start.sh
```

观察日志：

```bash
tail -f logs/console.log
```

确认端口：

```bash
ss -lntp | grep 8889
```

期望：

```text
127.0.0.1:8889
```

## 12. systemd 服务

复制示例：

```bash
cp /www/wwwroot/xingyu-pay-backend/deploy/baota/paypro.service.example \
   /etc/systemd/system/paypro.service
systemctl daemon-reload
systemctl enable --now paypro
systemctl status paypro --no-pager
```

查看日志：

```bash
journalctl -u paypro -f
```

如果 8889 已被宝塔「Java 项目」占用，先停掉重复项目，只保留一个后端进程。

## 13. 配置 Nginx

宝塔「网站」→ `pay.example.com` →「设置」→「配置文件」，替换为：

```nginx
# 完整示例见仓库：
# deploy/baota/nginx-site.conf.example
```

最少确认这些行：

```nginx
server_name pay.example.com;

location / {
    proxy_pass http://127.0.0.1:8889;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

测试并重载：

```bash
nginx -t && nginx -s reload
```

## 14. 申请 SSL

宝塔「网站」→「SSL」：

1. 选择 Let's Encrypt。
2. 勾选域名 `pay.example.com`。
3. 申请并部署。
4. 开启「强制 HTTPS」。

确认证书路径与 Nginx 配置一致：

```bash
ls -l /www/server/panel/vhost/cert/pay.example.com/
```

## 15. 防火墙

只对外开放：

```text
80/tcp
443/tcp
```

不要对外开放：

```text
8889    后端
3306    MySQL
6379    Redis
```

宝塔「安全」和云服务器安全组都要检查。

## 16. 上线验证

本机后端：

```bash
curl -I http://127.0.0.1:8889/home.html
```

期望：

```text
HTTP/1.1 200
```

域名：

```bash
curl -I https://pay.example.com/home.html
curl -I https://pay.example.com/recharge.html
curl -I https://pay.example.com/history.html
```

公开记录接口：

```bash
curl -s -X POST https://pay.example.com/order/publicList \
  -H 'Content-Type: application/json' \
  -d '{"pageIndex":1,"pageSize":5}'
```

安全头：

```bash
curl -I https://pay.example.com/home.html | grep -Ei \
'content-security-policy|x-frame-options|x-content-type-options|referrer-policy|permissions-policy'
```

敏感文件应为 404：

```bash
curl -o /dev/null -w '%{http_code}\n' https://pay.example.com/.env
curl -o /dev/null -w '%{http_code}\n' https://pay.example.com/xingyu-pay-backend.jar
```

## 17. 更新和回滚

更新前端：

```bash
cd /www/wwwroot/pay.example.com
tar -czf /www/backup/pay-frontend-$(date +%F-%H%M).tar.gz templates static
# 上传新的 templates/ static/
chown -R www:www templates static
```

前端不需要重启后端。

更新后端：

```bash
cd /www/wwwroot/xingyu-pay-backend
cp xingyu-pay-backend.jar xingyu-pay-backend.jar.bak-$(date +%F-%H%M)
# 上传新的 JAR
systemctl restart paypro
systemctl status paypro --no-pager
```

回滚：

```bash
cd /www/wwwroot/xingyu-pay-backend
cp xingyu-pay-backend.jar.bak-YYYYMMDD-HHMM xingyu-pay-backend.jar
systemctl restart paypro
```

## 18. 常见问题

### 502 Bad Gateway

```bash
systemctl status paypro --no-pager
journalctl -u paypro -n 100 --no-pager
ss -lntp | grep 8889
```

常见原因：JAR 路径错、`.env` 权限错、Java 路径错、8889 未监听。

### 页面 404

确认：

```bash
ls -l /www/wwwroot/pay.example.com/templates/home.html
ls -l /www/wwwroot/pay.example.com/static/css/common.css
grep '^FRONTEND_DIR=' /www/wwwroot/xingyu-pay-backend/.env
```

`FRONTEND_DIR` 必须指向包含 `templates/` 和 `static/` 的目录。

### 二维码 404

```bash
ls -l /www/wwwroot/xingyu-pay-backend/qr/wechat/5.00/
grep '^QR_DIR=' /www/wwwroot/xingyu-pay-backend/.env
```

金额目录名必须是两位小数，例如 `5.00`，不是 `5`。

### 邮件发不出去

- QQ 邮箱使用 SMTP 授权码，不是登录密码。
- `MAIL_SSL_TRUST` 必须是 `smtp.qq.com`。
- 465 端口通常设置 `MAIL_SSL_ENABLE=true`、`MAIL_STARTTLS=false`。

### 启动直接失败

生产环境会检查密钥。查看：

```bash
journalctl -u paypro -n 80 --no-pager | grep -i -E 'ADMIN_TOKEN|OPENAPI_SECRET|DB_PASSWORD|IllegalState'
```

## 19. 安全清单

- `.env` 权限 `600`，属主 `www:www`
- `ADMIN_TOKEN`、`OPENAPI_SECRET` 各 64 位且不相同
- MySQL、Redis 只监听 `127.0.0.1`
- 只开放 80/443
- 使用 HTTPS，TLS 1.2/1.3
- 不把 JAR、日志、数据库、`.env` 放进站点根目录
- 不在公开 issue 里贴订单、邮箱、收款码或 token
- 定期备份 MySQL 和后端 `qr/`、`config/`、`.env`

## 20. 许可证

本项目使用 MIT License。部署和二次分发前，请同时阅读仓库根目录的 `LICENSE` 与 `NOTICE`。

## 21. 只能有一个守护者（最容易踩的坑）

同一个 JAR 只能由一个 systemd 单元托管。宝塔「网站 → Java项目」会为 JAR 生成
`spring_<名称>.service`，如果它和手写的 `paypro.service` 同时存在，就是两个 java
进程抢同一个 `127.0.0.1:8889`：

```text
paypro.service         loaded activating auto-restart     <- 抢不到端口，反复重启
spring_paypro.service  loaded active     running          <- 抢到了，在用旧 class 应答
```

症状非常有迷惑性：**磁盘上 JAR 是新包、`sha256sum` 也对，新接口却 404**。因为
应答的是那个“启动时打开的是旧 JAR”的进程，或者另一个进程根本没绑定成功。

先确认有没有两个：

```bash
systemctl list-units --type=service --all | grep -iE 'pay|java'
pgrep -fc xingyu-pay-backend.jar        # 必须是 1
ss -lntp | grep ':8889'                 # 必须只有一个 java
```

只保留 `paypro.service`（它 `User=www`、读 `.env`）：

```bash
sh deploy/dedupe-service.sh
```

然后**去宝塔面板「网站 → Java项目」把那个项目删掉**。只 `systemctl disable` 不够，
unit 文件还在 `/usr/lib/systemd/system/`，面板点一次「启动」就复活。

宝塔生成的单元里 `ExecStop` 只 `pkill` 那个 bash 脚本，不认它 fork 出来的 java，
所以换包后必须补一刀：

```bash
pkill -f xingyu-pay-backend.jar
```

## 22. 部署后自检（必做）

```bash
cd /www/wwwroot/xingyu-pay-backend

echo "state=$(systemctl is-active paypro)"
echo "count=$(pgrep -fc xingyu-pay-backend.jar)"
ss -lntp | grep ':8889'
sha256sum xingyu-pay-backend.jar

curl -s -o /dev/null -w 'home=%{http_code}\n'     http://127.0.0.1:8889/home.html
curl -s -o /dev/null -w 'review=%{http_code}\n'   'http://127.0.0.1:8889/review?id=00000000000000000000000000000000&t=00000000000000000000000000000000'
curl -s -o /dev/null -w 'manifest=%{http_code}\n' http://127.0.0.1:8889/site.webmanifest
curl -s -D - -o /dev/null http://127.0.0.1:8889/home.html | grep -Ei 'content-security-policy|x-frame-options|x-content-type-options'
```

期望：

```text
state=active
count=1
127.0.0.1:8889 只有一个 java
home=200  review=200  manifest=200
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'; ...
```

换包前后各跑一次。换完新包不放心就再跑 `sh deploy/restart-clean.sh`，它会
stop → `pkill` 残留 → 确认端口释放 → start → 把上面这些探测重跑一遍，最后一行是
`RESULT=OK` 才算过。

更细的排障见 [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md)。
