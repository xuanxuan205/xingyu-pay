# 部署

## 1. 准备

- JDK 8
- MySQL 5.7+
- Redis 6+
- Nginx

## 2. 建库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pay DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p pay < deploy/database.sql
```

## 3. 配置

```bash
cp .env.example .env
chmod 600 .env
```

修改 `.env`：

```text
DB_PASSWORD
ADMIN_TOKEN
OPENAPI_SECRET
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM
MAIL_TO
SUPPORT_MAIL
ALIPAY_USER_ID
```

## 4. 构建

```bash
mvn clean package -DskipTests
```

## 5. 目录

```text
/opt/xingyu-pay/backend/    JAR、config、.env、qr、logs
/opt/xingyu-pay/frontend/   templates、static
```

```bash
mkdir -p /opt/xingyu-pay/backend /opt/xingyu-pay/frontend
cp target/xingyu-pay-backend.jar /opt/xingyu-pay/backend/
cp -a config /opt/xingyu-pay/backend/
cp -a frontend/templates frontend/static /opt/xingyu-pay/frontend/
cp -a deploy/*.sh deploy/*.service.example /opt/xingyu-pay/backend/
```

## 6. 启动

```bash
cd /opt/xingyu-pay/backend
chmod +x start.sh stop.sh check-env.sh check-qr.sh
./check-env.sh
./start.sh
```

## 7. systemd

```bash
cp deploy/paypro.service.example /etc/systemd/system/paypro.service
systemctl daemon-reload
systemctl enable --now paypro
systemctl status paypro --no-pager
```

## 8. Nginx

复制 `deploy/nginx-proxy.conf`，把 `server_name` 和证书路径改成自己的域名：

```nginx
server_name pay.example.com;
ssl_certificate /etc/nginx/ssl/pay.example.com/fullchain.pem;
ssl_certificate_key /etc/nginx/ssl/pay.example.com/privkey.pem;
```

```bash
nginx -t && nginx -s reload
```

## 9. 验证

```bash
curl -s -o /dev/null -w 'home=%{http_code}\n' http://127.0.0.1:8889/home.html
curl -s -o /dev/null -w 'list=%{http_code}\n' -X POST http://127.0.0.1:8889/order/list
curl -s -o /dev/null -w 'pass=%{http_code}\n' http://127.0.0.1:8889/order/pass?id=x
curl -s -o /dev/null -w 'stat=%{http_code}\n' -X POST 'http://127.0.0.1:8889/order/statistic?type=1'
```

期望：

```text
home=200
list=404
pass=405
stat=401
```

## 10. 宝塔

- 站点根目录指向 `/opt/xingyu-pay/frontend`，或反向代理到 `127.0.0.1:8889`
- SSL 证书用宝塔申请
- 不要再建第二个 Java 项目抢 8889 端口
- 继续用 systemd 服务 `paypro`
