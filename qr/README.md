# 二维码目录

不要把真实收款码提交到 Git。

微信固定金额二维码：

```text
qr/wechat/1.00/1.png
qr/wechat/1.00/2.png
qr/wechat/5.00/1.png
qr/wechat/5.00/2.png
qr/wechat/10.00/1.png
qr/wechat/10.00/2.png
```

金额必须与 `config/application-prod.yml` 的 `products[].money` 一致。
每个金额至少放一张，最多按 `paypro.qrCodeNum` 配置。

支付宝不需要本地二维码；`openAlipay` 页面会根据 `ALIPAY_USER_ID` 拉起付款页。
