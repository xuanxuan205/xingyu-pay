# 星屿支付 / Xingyu Pay

一个面向个人站点的固定金额收款系统。前端与后端分离，支持支付宝金额锁定付款页、微信按金额二维码收款、邮件审核、后台统计和 OpenAPI 下单。

> 本项目是在 PayPro 代码基础上做过安全加固的版本。开源前请先阅读 `NOTICE` 和 `LICENSE`，确认你有权重新分发上游代码。

## 功能

- 固定金额商品，不接受前端自行输入金额
- 支付宝：服务端锁定金额，拉起支付宝付款页
- 微信：`qr/wechat/{金额}/1.png`、`2.png` 对应金额二维码
- 下单后邮件通知管理员审核
- 审核页一次性 token，30 分钟有效
- 审核通过必须输入实际到账金额，金额不一致直接拒绝
- 审核通过/驳回只允许 POST
- 公开支持记录脱敏
- 管理端统计使用 `X-Admin-Token` 请求头
- 前后端分开部署，改页面不用重新打包 JAR

## 安全设计

- 不信任 URL 里的金额、支付标识、订单 ID 之外的支付参数
- `openAlipay` 金额只从数据库读取
- `GET /review` 只展示，不修改订单
- `POST /order/pass` 校验状态机和到账金额
- `POST /order/back` 只允许待支付/已扫码状态
- 公开列表只返回成功订单，隐藏邮箱、附言、支付标识
- 管理员 token 使用常量时间比较
- 只信任 Nginx 写入的 `X-Real-IP`
- 关闭 MyBatis SQL 明文日志

## 目录

```text
src/                    后端源码
config/                 生产/本地配置
frontend/templates/     Thymeleaf 页面
frontend/static/        CSS / JS / 图标
deploy/                 systemd、Nginx、脚本、建表 SQL
docs/                   部署、安全、开源检查
qr/README.md            二维码目录说明
```

## 环境要求

- JDK 8
- Maven 3.6+
- MySQL 5.7+ / 8.0
- Redis 6+
- Nginx（生产）

## 本地构建

```bash
mvn clean package -DskipTests
```

运行：

```bash
export FRONTEND_DIR="$PWD/frontend"
java -jar target/xingyu-pay-backend.jar   --spring.profiles.active=prod   --spring.config.additional-location=file:./config/
```

本地 H2 演示：

```bash
java -jar target/xingyu-pay-backend.jar   --spring.profiles.active=local   --spring.config.additional-location=file:./config/   --spring.datasource.url='jdbc:h2:mem:pay;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1'
```

## 配置

复制 `.env.example` 为 `.env`，填写自己的数据库、Redis、邮箱、管理员 token：

```bash
cp .env.example .env
chmod 600 .env
```

必改项：

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

`ADMIN_TOKEN` 和 `OPENAPI_SECRET` 请各自生成 64 位随机十六进制，不要相同：

```bash
openssl rand -hex 32
```

## 二维码

不要把真实收款码提交到 Git。

```text
qr/wechat/1.00/1.png
qr/wechat/1.00/2.png
qr/wechat/5.00/1.png
qr/wechat/5.00/2.png
...
```

金额必须与 `config/application-prod.yml` 里的 `products[].money` 一致。

## 邮件审核流程

1. 用户下单，后端生成订单和一次性审核 token。
2. 管理员收到邮件，邮件里只有“打开审核页”链接。
3. 打开 `review?id=...&t=...`，页面只展示订单。
4. 管理员核对支付宝/微信实际到账金额。
5. 输入到账金额并通过；金额不一致会被拒绝。
6. token 使用一次后删除。

## 部署

见 `docs/DEPLOY.md`。

## Docker

```bash
cp .env.example .env
docker compose up -d --build
```

## 开源前检查

```text
docs/OPEN_SOURCE_CHECK.md
```

仓库不包含 `.env`、真实二维码、日志、数据库、JAR、旧版回滚包。

## 许可证

见 `LICENSE` 和 `NOTICE`。当前快照没有替仓库所有者选择开源许可证；请确认上游 PayPro 的授权后，再替换成 MIT、Apache-2.0 或其他许可证。
