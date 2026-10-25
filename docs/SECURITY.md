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

## 当前模型

- `GET /review` 只读。
- `POST /order/pass` / `POST /order/back` 需要一次性 token。
- token 存在 Redis：`review:token:<orderId>`，30 分钟。
- 通过时必须输入实际到账金额。
- 金额按两位小数比较。
- 订单状态只允许 `0`（待支付）或 `4`（已扫码）。
- 公开记录只返回成功订单，且脱敏。

## 仍然存在的风险

- 没有支付宝/微信官方异步回调或主动查单，到账仍靠人工审核。
- 没有管理员账号体系，仍是单一 `ADMIN_TOKEN`。
- 没有 CSRF token，当前依赖一次性审核 token。
- `payNum` 只有 4 位，同金额订单可能碰撞。
- OpenAPI 仍是 MD5 签名，没有 nonce/IP 白名单。
- Redis 默认没有密码，生产应设置 `requirepass`。
- 生产 JAR 仍包含 H2、Springfox、devtools，建议后续裁剪。

## 报告问题

不要在公开 issue 里贴真实 token、数据库密码、邮箱授权码、收款码或订单数据。
