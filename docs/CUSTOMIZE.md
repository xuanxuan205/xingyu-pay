# 二次开发指南

这个项目从一开始就是按“给别人改成自己的”来拆的：

- 后端只提供接口和模板渲染，页面与静态资源放独立目录，改 UI 不用重打 JAR；
- 品牌名、标题、收款人、支持邮箱全部走 `.env`，不写死在代码里；
- 商品、金额、支付方式由 `config/application-prod.yml` 驱动；
- 二维码是外部目录里的普通图片，换收款码不用重新编译。

下面是改每一类东西的准确位置。

---

## 0. 先分清“能改”和“别碰”

| 随便改 | 改了要自己负责 |
| --- | --- |
| 配色、字体、圆角、间距 | 审核 token 的签发与校验 |
| 页面文案、区块顺序 | “GET 不改订单状态”这条约定 |
| 品牌名、标题、邮箱 | “金额由服务端锁定”这条约定 |
| 商品、金额、二维码 | CSP / 安全响应头 |
| 支付方式增减 | `QrCodeResolver` 的路径越界保护 |
| 邮件正文 | `.env` 不进 Git 的纪律 |

右边一列的改动会直接变成资金风险，改之前请先读
[SECURITY.md](SECURITY.md) 和 [TROUBLESHOOTING.md](TROUBLESHOOTING.md)。

---

## 1. 文件地图

| 想改什么 | 改哪里 | 生效方式 |
| --- | --- | --- |
| 站点标题 | `.env` → `SITE_TITLE` / `INDEX_TITLE` | 重启后端 |
| 收款人昵称 | `.env` → `PAYEE_NAME` | 重启后端 |
| 邮件里的品牌名 | `.env` → `EMAIL_BRAND_NAME` | 重启后端 |
| 支持邮箱 | `.env` → `SUPPORT_MAIL` | 重启后端 |
| 站点域名 | `.env` → `SITE_URL` | 重启后端 |
| 配色 / 字体 / 圆角 | `frontend/static/css/common.css` 的 `:root` | 刷新页面 |
| 页面结构 | `frontend/templates/*.html` | 刷新页面 |
| 图标 | `frontend/static/assets/images/*` | 刷新页面 |
| 商品与金额 | `config/application-prod.yml` → `paypro.products` | 重启后端 |
| 支付方式 | `config/application-prod.yml` → `paypro.payMethods` | 重启后端 |
| 收款二维码 | `QR_DIR/{alipay,wechat}/...` | 立即 |
| 邮件正文 | `frontend/templates/order-fail.html` 等 | 刷新/重发 |
| 订单流程 | `src/main/java/com/wendao/**` | 重新打包 |

模板缓存是关的（`spring.thymeleaf.cache: false`），所以改前端不需要重启。

---

## 2. 改品牌

只动 `.env`，不用碰代码：

```bash
SITE_TITLE=星屿支付          # 邮件标题、【】里的名字
INDEX_TITLE=星屿支付         # 首页大标题
PAYEE_NAME=Your Name         # 收款人昵称
EMAIL_BRAND_NAME=星屿支付    # 邮件发件人显示名；留空回退到 PAYEE_NAME
SUPPORT_MAIL=you@example.com # 页面上的支持邮箱
SITE_URL=https://pay.example.com
```

发件人显示名的取值顺序是：

```text
EMAIL_BRAND_NAME → PAYEE_NAME → "星屿支付"
```

改完 `systemctl restart paypro`（`EnvironmentFile` 只在启动时读一次）。

---

## 3. 改配色与排版

所有视觉都挂在 `frontend/static/css/common.css` 顶部的 CSS 变量上，
页面里没有散落的硬编码颜色：

```css
:root {
    --bg-color: #f3f6f1;        /* 页面底色 */
    --surface-color: #fffefa;   /* 卡片底色 */
    --ink: #0b1f1a;             /* 深色主调 */
    --primary-color: #0b6b5b;   /* 主色（按钮、链接） */
    --primary-hover: #075448;
    --secondary-color: #c08a2e; /* 点缀色（黄铜金） */
    --danger-color: #b94039;
    --radius-lg: 28px;          /* 卡片圆角 */
    --radius-md: 18px;
    --font-display: "Iowan Old Style", "Noto Serif SC", serif;
    --font-sans: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}
```

换一套品牌最省事的做法：只改 `--primary-color`、`--ink`、`--secondary-color`
三个值，全站立刻变。

注意 `--primary-color` 最好保持足够对比度（在 `--surface-color` 上 ≥ 4.5:1），
否则按钮文字会看不清。

---

## 4. 改图标

`frontend/static/assets/images/` 下这些文件是一组的，**要一起换**：

| 文件 | 用途 |
| --- | --- |
| `brand-mark.svg` | README 顶部与页面品牌标记 |
| `favicon.svg` / `favicon.ico` | 浏览器标签 |
| `favicon-16.png` / `favicon-32.png` | 旧浏览器兜底 |
| `apple-touch-icon.png` / `icon-180.png` | iOS 主屏 |
| `icon-96/192/256/384/512.png` | Android / PWA |
| `maskable-512.png` | Android 自适应图标（留 20% 安全边距） |
| `og-cover.png` | 社交分享封面，1200×630 |

做法：准备一张 1024×1024 的方形 PNG，用任意 favicon 生成器
（如 realfavicongenerator）导出同名文件覆盖，保持文件名不变，
`site.webmanifest` 和页面里的 `<link>` 就不用动。

`og-cover.png` 必须是 **1200×630**，否则微信/Twitter 卡片会被裁切。

改完记得同步 `frontend/static/site.webmanifest` 里的 `name` / `short_name` /
`theme_color`。

---

## 5. 改商品与金额

`config/application-prod.yml`：

```yaml
paypro:
  qrCodeNum: 2          # 每个金额用几张二维码（1.png / 2.png ...）
  products:
    - id: 1
      product-name: "支持 5 元"
      money: "5.00"     # 必须两位小数
      description: "固定金额 5 元"
      type: "CODE"
      extend: ""
      download-url: ""
```

规则：

1. `money` 必须和二维码目录名**完全一致**（`5.00` 不是 `5`）；
2. 二维码放 `QR_DIR/wechat/5.00/1.png`、`2.png`；
3. 支付宝用 `QR_DIR/alipay/custom.png`，通过 `/assets/qr/alipay/custom.png` 访问；
4. 金额由服务端下发，前端**不能**传金额；`decrement.enabled` 保持 `false`，
   否则会按 0.01 递减去找 `4.99/1.png` 这类目录。

改完重启后端。二维码文件本身替换是立即生效的（不缓存）。

---

## 6. 改支付方式

```yaml
paypro:
  payMethods:
    - id: 'alipay'
      name: '支付宝支付'
      description: '金额已锁定，拉起支付宝付款'
      icon: '🔵'
      status: true
      allow-night: false
      use-local-qr-code: false   # 用支付宝转账页，不用本地图
    - id: 'wechat'
      name: '微信支付'
      description: '扫描对应金额二维码付款'
      icon: '🟢'
      status: true
      allow-night: true
      use-local-qr-code: true    # 用 QR_DIR/wechat/{金额}/n.png
```

- 想临时关掉某个渠道：`status: false`，页面自动不显示，不用改代码；
- `allow-night` 控制夜间是否可用；
- 新增渠道：加一条配置，然后按 `id` 在 `templates/payment.html` 里加对应分支，
  二维码目录按 `QR_DIR/{id}/{金额}/` 组织。

---

## 7. 改邮件

邮件正文走的是**和页面同一套 Thymeleaf 模板**（`TemplateEngine` 默认解析器），
所以：

```text
sendTemplateMail(..., "order-fail", order)
   → frontend/templates/order-fail.html
```

这是最容易踩的坑：**你改 `order-fail.html` 的样式，同时改掉了邮件正文**。
如果想让邮件和页面长得不一样，正确做法是新建一个只给邮件用的模板
（例如 `mail-order-fail.html`），在调用处换名字，别去动页面那份。

模板里可用的变量：

```text
title        邮件标题
brandName    品牌名
brandInitial 品牌名首字（用于圆形头像）
site         站点地址
supportMail  支持邮箱
year         当前年份
+ 订单对象的所有字段（money / description / nickName / createTime ...）
```

---

## 8. 改业务流程

后端入口：

| 文件 | 职责 |
| --- | --- |
| `controller/OrderController.java` | 下单、列表、审核、统计、OpenAPI |
| `controller/SiteAssetController.java` | `/site.webmanifest` |
| `config/SecurityHeadersFilter.java` | 安全响应头 |
| `config/SecurityStartupValidator.java` | 生产启动密钥校验 |
| `config/PayProConfig.java` | 配置绑定 + `/assets/qr/**` 映射 |
| `common/utils/QrCodeResolver.java` | 固定金额二维码定位 |
| `common/utils/EmailUtils.java` | 邮件发送 |
| `service/**` | 订单状态机 |

加新接口时请保持这几条：

1. **GET 不改状态。** 审核通过/驳回必须 POST，并带一次性 token。
   邮件安全扫描器会替你点开所有 GET 链接。
2. **金额只信服务端。** 前端传上来的金额一律忽略，用 `products` 里的。
3. **token 一次性。** 用完 `redisTemplate.delete(...)`，别留可重放的凭证。
4. **路径不许越界。** 任何拼路径的地方都参考 `QrCodeResolver` 的
   `normalize()` + `startsWith(root)` 双检。
5. **管理接口和开放接口的密钥必须不同**（启动时会校验）。

---

## 9. 构建与打包

后端：

```bash
mvn -s settings.xml clean test package
# 产物：target/xingyu-pay-backend.jar   （finalName 固定，版本号变了文件名不变）
```

只需 Java 8 编译；用 JDK 21 构建也可以（`lombok.version` 已固定 1.18.36）。

前端（**单独打包，不要塞进 JAR**）：

```bash
cd frontend && zip -r ../xingyu-pay-frontend.zip templates static
```

部署时是两个独立动作：

```text
后端：xingyu-pay-backend.jar → /opt/xingyu-pay/backend/
前端：templates/ + static/   → /opt/xingyu-pay/frontend/
```

换包前后各跑一次自检，见 [BAOTA_DEPLOY.md](BAOTA_DEPLOY.md) 第 22 节。

---

## 10. 升级上游改动

1. 先读 [CHANGELOG.md](../CHANGELOG.md) 对应版本；
2. 对比 `.env.example`，看有没有新增必填项（例如 `JAR_SHA256`）；
3. 数据库结构变化看 `deploy/database.sql` 与 `src/main/resources/sql/pay.sql`；
4. 合并冲突时优先保留自己的：品牌配置、`common.css` 变量、`products`；
   优先采用上游的：`SecurityHeadersFilter`、`SecurityStartupValidator`、
   `QrCodeResolver`、审核流程。

---

## 11. 二次开发后的署名

MIT 许可允许你闭源分发、商用、改名，只要求**保留版权声明与许可全文**。
具体做法：

- 保留 `LICENSE` 文件；
- 在 `NOTICE` 里保留上游声明；
- 页脚可以换成自己的品牌，但不要把仓库里的 `LICENSE` 删掉。

如果你改了名字继续开源，欢迎在 README 里注明基于 Xingyu Pay。
