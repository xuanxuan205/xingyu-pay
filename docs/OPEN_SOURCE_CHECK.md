# 开源前检查

生成时间：2026-10-25
仓库目录：`xingyu-pay`

## 已排除

- 所有 `.env`
- `target/`、`*.jar`、`*.class`
- `logs/`、`data/`、`*.log`、`*.pid`、`*.hprof`
- `qr/` 下的真实收款码，只保留 `qr/README.md`
- 旧版回滚包、基线源码、旧版 JAR
- `deploy/security` 下的验证日志和真实服务器路径
- 上游 README 里的真实演示域名、邮箱和个人姓名

## 敏感字符串扫描

未发现真实域名、数据库密码、邮箱授权码、手机号、支付宝 ID 或旧管理员 token。

## 发布前人工确认

1. 不要把服务器上的 `.env` 复制进仓库。
2. 不要把 `qr/` 里的真实收款码提交。
3. 不要把 `data/`、`logs/`、`*.jar` 提交。
4. 确认上游 PayPro 的许可证，再替换 `LICENSE`。
5. 在 GitHub 仓库设置里开启 Secret Scanning。

## 本地复查命令

```bash
git status --short
git grep -nE 'YOUR_REAL_DOMAIN|YOUR_DB_PASSWORD|YOUR_EMAIL|YOUR_PHONE' || true
find . -name '.env' -o -name '*.jar' -o -name '*.log' -o -name '*.png' | head
```
