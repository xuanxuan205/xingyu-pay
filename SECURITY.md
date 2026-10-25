# 安全说明

## 已修复

- 未认证订单列表
- 审核链接 GET 即执行
- 审核 token 放在 URL 且长期有效
- `openAlipay` 金额来自 URL
- 审核通过不校验到账金额
- 审核通过不校验订单状态
- `/order/edit` 整对象绑定
- 管理统计 token 放在 URL
- 信任客户端可伪造的 `X-Forwarded-For`
- SQL 明文日志
- 空管理员 token 可能通过比较
- OpenAPI 订单号、邮箱、回调地址、有效期缺少边界校验
- 到账金额超过两位小数仍可能被四舍五入

## 当前模型

- `GET /review` 只读。
- `POST /order/pass` / `POST /order/back` 需要一次性 token。
- token 存在 Redis：`review:token:<orderId>`，30 分钟。
- 通过时必须输入实际到账金额，且最多两位小数。
- 金额按两位小数比较。
- 订单状态只允许 `0`（待支付）或 `4`（已扫码）。
- 公开记录只返回成功订单，且脱敏。
- 管理接口使用 `X-Admin-Token` 请求头，空值或短于 32 字符直接拒绝。
- 生产启动时校验 `ADMIN_TOKEN`、`OPENAPI_SECRET`、`DB_PASSWORD`。
- 全站响应头包含 CSP、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、Referrer-Policy、Permissions-Policy。
- 敏感路径增加 `Cache-Control: no-store` 和 `noindex`。
- OpenAPI 回调拒绝本机、内网、链路本地地址。

## 仍然存在的风险

- 没有支付宝/微信官方异步回调或主动查单，到账仍靠人工审核。
- 没有管理员账号体系，仍是单一 `ADMIN_TOKEN`。
- 没有 CSRF token，当前依赖一次性审核 token。
- `payNum` 只有 4 位，同金额订单可能碰撞。
- OpenAPI 仍是 MD5 签名，没有 nonce 和 IP 白名单。
- 生产 JAR 仍包含 H2、Springfox、devtools，建议后续裁剪。
- 前端使用 Vue 浏览器编译，CSP 必须允许 `unsafe-eval`。

## 生产要求

- `.env` 权限 `600`，属主为运行用户。
- MySQL、Redis 只监听 `127.0.0.1`。
- 只开放 80/443，不开放 8889/3306/6379。
- 使用 HTTPS，TLS 1.2/1.3。
- 定期备份数据库、`config/`、`.env`、`qr/`。
- 不要把真实密钥、订单数据、邮箱或收款码贴到公开 issue。

## 报告问题

不要在公开 issue 里贴真实 token、数据库密码、邮箱授权码、收款码或订单数据。请提供复现步骤、受影响版本和脱敏后的日志。
