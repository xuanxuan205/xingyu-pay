# 更新日志

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-10-25

第一个可用版本：固定金额收款、邮件审核、前后端分离、宝塔可部署。

### 新增

- 固定金额收款：金额由服务端 `products` 下发并按金额匹配 `qr/` 目录二维码，
  支付页不接受用户输入金额。
- 支付宝金额锁定转账页（`/openAlipay`），微信使用本地金额二维码。
- 审核页 `/review?id=&t=`：一次性 token，GET 只读，状态变更必须 POST。
- 旧邮件链接兼容 `/order/pass`：校验旧 token 后换发新 token 并 302，
  **不在 GET 中修改订单**。
- OpenAPI（签名校验）与管理端接口（`ADMIN_TOKEN`，与 `OPENAPI_SECRET` 分离）。
- PWA：`site.webmanifest` + 全套图标，独立控制器带正确 MIME。
- 宝塔面板部署教程、故障排查手册、部署守卫脚本。
- `deploy/start.sh` 支持可选 `JAR_SHA256`：哈希不符 `exit 78`，阻止旧包静默启动。
- `deploy/restart-clean.sh`：stop → 清残留 java → 确认端口 → start → 探测。
- `deploy/dedupe-service.sh`：只保留一个 systemd 单元，重复单元自动 disable 并可回退。
- 本地演示 profile（H2 + 本地 Redis/SMTP 替身），`mvn test` 可离线跑。

### 变更

- 全站视觉重构：深墨绿 / 象牙白 / 黄铜金；桌面与移动端自适应，
  支持 `prefers-reduced-motion`。
- 布局紧凑化：图标、字号、卡片、留白整体缩小；移动端隐藏首页装饰预览卡。
- 前后端彻底分离：模板与静态资源由 `FRONTEND_DIR` 运行时读取，改页面不用重打 JAR。
- 只保留支付宝与微信两种支付方式，移除当面付与自定义金额输入。

### 安全

- 响应头：CSP、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、
  `Referrer-Policy`、`Permissions-Policy`、COOP。
- 生产启动校验：`ADMIN_TOKEN` / `OPENAPI_SECRET` 必须 ≥32 位且互不相同，
  `DB_PASSWORD` 不能是占位符，不满足直接拒绝启动。
- 订单与 OpenAPI 参数校验；审核/管理接口 `no-store` 且 `noindex`。
- 敏感文件、密钥、JAR、日志不进仓库；`.env` 权限 600。

### 修复

- 新 JAR 已上传但接口仍 404：旧进程持有旧 inode / 重复 systemd 单元抢端口，
  现已在排障手册中给出判定与处置。
- `/site.webmanifest` 被通用视图映射吞掉导致 404。

---

## 版本说明

- `MAJOR`：破坏兼容（`.env` 必填项变化、数据库结构不兼容、接口签名变化）
- `MINOR`：向下兼容的功能新增
- `PATCH`：向下兼容的问题修复

升级前请先读本文件对应版本段落，再读
[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) 的「部署检查清单」。
