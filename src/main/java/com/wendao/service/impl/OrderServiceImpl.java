package com.wendao.service.impl;

import cn.hutool.http.HttpUtil;
import com.wendao.config.PayProConfig;
import com.wendao.entity.Order;
import com.wendao.exception.ApiException;
import com.wendao.mapper.OrderMapper;
import com.wendao.service.ProductService;
import com.wendao.enums.OrderStatesEnum;
import com.wendao.model.req.GetOrderListReq;
import com.wendao.model.req.OpenApiOrderReq;
import com.wendao.model.resp.AddOrderResp;
import com.wendao.model.resp.CountResp;
import com.wendao.model.resp.OpenApiOrderResp;
import com.wendao.service.OrderService;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Snowflake;
import com.wendao.entity.Product;
import com.wendao.model.ResponseVO;
import com.wendao.common.utils.*;
import com.wendao.model.req.OrderReq;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wendao.utils.OpenApiSignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    OrderService thisService;

    @Autowired
    PayProConfig payProConfig;

    @Autowired
    private EmailUtils emailUtils;

    @Autowired
    ProductService productService;

    @Autowired
    Snowflake snowflake;

    @Autowired
    OpenApiSignUtil openApiSignUtil;

    @Autowired
    private ResourceLoader resourceLoader;

    @Override
    public Order getOrderById(String id) {
        Order byId = orderMapper.selectById(id);
        if (byId == null) {
            return null;
        }
        byId.setTime(StringUtils.getTimeStamp(byId.getCreateTime()));
        return byId;
    }

    @Override
    public int addOrder(Order pay) {
        pay.setId(UUID.randomUUID().toString().replace("-", ""));
        pay.setCreateTime(new Date());
        pay.setState(OrderStatesEnum.WAIT_PAY.getState());
        if (StringUtils.isBlank(pay.getOrderSource())) {
            pay.setOrderSource("PRODUCT");
        }
        if (pay.getExpireTime() == null) {
            pay.setExpireTime(DateUtil.offsetMinute(new Date(),
                    payProConfig.getOrder().getTimeoutMinutes().intValue()));
        }
        // 如果未设置匹配模式，默认为备注模式
        if (pay.getMatchMode() == null) {
            pay.setMatchMode("REMARK");
        }
        if (pay.getActualAmount() == null) {
            pay.setActualAmount(pay.getMoney());
        }
        orderMapper.insert(pay);
        return 1;
    }

    @Override
    public int updateOrder(Order pay) {
        if (pay == null || StringUtils.isBlank(pay.getId())) {
            return 0;
        }
        pay.setUpdateTime(new Date());
        orderMapper.updateById(pay);
        return 1;
    }

    @Override
    public int changeOrderState(String id, Integer state) {

        Order pay = getOrderById(id);
        if (pay == null) {
            return 0;
        }
        pay.setState(state);
        pay.setUpdateTime(new Date());
        orderMapper.updateById(pay);
        return 1;
    }

    @Override
    public int delOrder(String id) {
        orderMapper.deleteById(id);
        return 1;
    }

    @Override
    public CountResp statistic(Integer type, String start, String end) {

        CountResp count = new CountResp();
        if (type == -1) {
            // 总
            count.setAmount(orderMapper.countAllMoney());
            count.setWeixin(orderMapper.countAllMoneyByType("wechat"));
            count.setAlipay(orderMapper.countAllMoneyByType("alipay"));
            return count;
        }
        Date startDate = null, endDate = null;
        if (type == 0) {
            // 今天
            startDate = DateUtils.getDayBegin();
            endDate = DateUtils.getDayEnd();
        }
        if (type == 6) {
            // 昨天
            startDate = DateUtils.getBeginDayOfYesterday();
            endDate = DateUtils.getEndDayOfYesterDay();
        } else if (type == 1) {
            // 本周
            startDate = DateUtils.getBeginDayOfWeek();
            endDate = DateUtils.getEndDayOfWeek();
        } else if (type == 2) {
            // 本月
            startDate = DateUtils.getBeginDayOfMonth();
            endDate = DateUtils.getEndDayOfMonth();
        } else if (type == 3) {
            // 本年
            startDate = DateUtils.getBeginDayOfYear();
            endDate = DateUtils.getEndDayOfYear();
        } else if (type == 4) {
            // 上周
            startDate = DateUtils.getBeginDayOfLastWeek();
            endDate = DateUtils.getEndDayOfLastWeek();
        } else if (type == 5) {
            // 上个月
            startDate = DateUtils.getBeginDayOfLastMonth();
            endDate = DateUtils.getEndDayOfLastMonth();
        } else if (type == -2) {
            // 自定义
            startDate = DateUtils.parseStartDate(start);
            endDate = DateUtils.parseEndDate(end);
        }
        count.setAmount(orderMapper.countMoney(startDate, endDate));
        count.setWeixin(orderMapper.countMoneyByType("wechat", startDate, endDate));
        count.setAlipay(orderMapper.countMoneyByType("alipay", startDate, endDate));
        return count;
    }

    @Override
    @Transactional
    public ResponseVO<AddOrderResp> addOrder(OrderReq req, HttpServletRequest request) {

        // 固定金额模式：金额只能来自 YAML 商品配置，浏览器提交的 money 一律忽略。
        if (req.getProductId() == null) {
            return ResponseVO.errorResponse("请选择页面提供的固定金额");
        }
        if (Boolean.TRUE.equals(req.getCustom())) {
            return ResponseVO.errorResponse("不支持自定义金额，请选择页面提供的固定金额");
        }

        String payType = req.getPayType();
        boolean payTypeSupported = payProConfig.getPayMethods() != null
                && payProConfig.getPayMethods().stream()
                .anyMatch(m -> m.isStatus() && m.getId().equals(payType));
        if (!payTypeSupported) {
            return ResponseVO.errorResponse("不支持的支付方式");
        }

        PayProConfig.ProductConfig productConfig =
                payProConfig.getProductConfigById(req.getProductId().intValue());
        if (productConfig == null || productConfig.getMoney() == null) {
            return ResponseVO.errorResponse("商品不存在");
        }
        BigDecimal money = productConfig.getMoney().setScale(2, RoundingMode.HALF_UP);
        if (money.compareTo(BigDecimal.ZERO) <= 0
                || money.compareTo(new BigDecimal("100000.00")) > 0) {
            return ResponseVO.errorResponse("商品金额配置错误");
        }

        // 本地二维码支付方式必须能在 QR_DIR 中找到该金额的实际二维码。
        boolean localQr = Boolean.TRUE.equals(payProConfig.getUseLocalQrCode(payType));
        int qrNum = 1;
        String qrUrl = null;
        if (localQr) {
            List<Integer> available = findAvailableQrNumbers(payType, money);
            if (available.isEmpty()) {
                return ResponseVO.errorResponse("当前金额未配置微信收款二维码，请联系管理员");
            }
            qrNum = available.get(ThreadLocalRandom.current().nextInt(available.size()));
            qrUrl = "/assets/qr/" + payType.toLowerCase(Locale.ROOT) + "/"
                    + QrCodeResolver.formatAmount(money) + "/" + qrNum + ".png";
        }

        String ip = IpInfoUtils.getIpAddr(request);
        String rateKey = "pay:rate:" + ip;
        Long rateMinutes = payProConfig.getRateLimit().getIpExpire() == null
                ? 1L : payProConfig.getRateLimit().getIpExpire();
        Boolean allowed = redisTemplate.opsForValue()
                .setIfAbsent(rateKey, "1", rateMinutes, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(allowed)) {
            return ResponseVO.errorResponse("请求过于频繁，请稍后再试");
        }

        Order entity = new Order();
        BeanUtils.copyProperties(req, entity);
        entity.setMoney(money);
        entity.setProductId(req.getProductId());
        entity.setCustom(false);
        entity.setMatchMode("REMARK");
        entity.setActualAmount(money);
        entity.setDecrementIndex(null);
        entity.setPayQrNum(qrNum);
        entity.setPayNum(StringUtils.getRandomNum());
        entity.setOrderSource("PRODUCT");
        entity.setExpireTime(DateUtil.offsetMinute(new Date(),
                payProConfig.getOrder().getTimeoutMinutes().intValue()));

        try {
            thisService.addOrder(entity);
        } catch (Exception e) {
            log.error(e.toString());
            redisTemplate.delete(rateKey);
            return ResponseVO.errorResponse("创建支持订单失败");
        }

        //给管理员发送审核邮件
        String tokenAdmin = UUID.randomUUID().toString();
        Long tokenDays = payProConfig.getToken().getExpire() == null
                ? 14L : payProConfig.getToken().getExpire();
        redisTemplate.opsForValue().set(entity.getId(), tokenAdmin, tokenDays, TimeUnit.DAYS);

        String reviewToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("review:token:" + entity.getId(),
                reviewToken, 30, TimeUnit.MINUTES);
        entity = getReviewUrl(entity, entity.getId(), reviewToken);
        if(StringUtils.isNotBlank(payProConfig.getEmail().getReceiver())
                && StringUtils.isNotBlank(payProConfig.getEmail().getSender())){
            emailUtils.sendTemplateMail(payProConfig.getEmail().getSender(),
                    payProConfig.getEmail().getReceiver(),
                    "【" + payProConfig.getTitle() + "】待审核处理", "payment-review", entity);
        }

        AddOrderResp addPayResp = new AddOrderResp();
        addPayResp.setId(entity.getId());
        addPayResp.setPayNum(entity.getPayNum());
        addPayResp.setPayType(entity.getPayType());
        addPayResp.setMoney(entity.getMoney());
        addPayResp.setPayQrNum(entity.getPayQrNum());
        addPayResp.setCustom(false);
        addPayResp.setLocalQr(localQr);
        addPayResp.setQrUrl(qrUrl);
        addPayResp.setMatchMode("REMARK");
        addPayResp.setActualAmount(money);
        addPayResp.setFallbackToRemark(false);
        return ResponseVO.successResponse(addPayResp);
    }

    /**
     * 处理充值业务,直接调用游戏的接口。订单状态在游戏的地方修改
     */
    @Override
    @Transactional
    public int pass(String id, BigDecimal receivedAmount) {
        Order pay = orderMapper.selectById(id);
        if (pay == null) {
            return 0;
        }
        // Approval must be idempotent: a repeated email click must not send the
        // same download link or callback twice.
        if (OrderStatesEnum.SUCCESS_PAY.getState().equals(pay.getState())) {
            return 1;
        }
        if (!OrderStatesEnum.WAIT_PAY.getState().equals(pay.getState())
                && !OrderStatesEnum.SCANE_QR.getState().equals(pay.getState())) {
            return -1;
        }
        if (receivedAmount == null || pay.getMoney() == null
                || receivedAmount.setScale(2, RoundingMode.HALF_UP)
                .compareTo(pay.getMoney().setScale(2, RoundingMode.HALF_UP)) != 0) {
            return -2;
        }

        if ("PRODUCT".equals(pay.getOrderSource()) && pay.getProductId() != null) {
            Product product = productService.getProductById(pay.getProductId().intValue());
            if (product != null && "CODE".equals(product.getType())) {
                if (StringUtils.isNotBlank(pay.getEmail())
                        && StringUtils.isNotBlank(payProConfig.getEmail().getSender())) {
                    emailUtils.sendTemplateMail(payProConfig.getEmail().getSender(),
                            pay.getEmail(),
                            "【" + payProConfig.getTitle() + "】支付成功通知",
                            "order-success", pay);
                }
            }
        } else if ("OPENAPI".equals(pay.getOrderSource())) {
            try {
                callbackFaka(pay.getNotifyUrl(), pay.getId(), pay.getMoney(), pay.getPayNum());
            } catch (Exception e) {
                // The money is already received; record success and leave the
                // callback failure in the log for a manual retry.
                log.error("OpenAPI 回调失败, orderId={}, notifyUrl={}", pay.getId(), pay.getNotifyUrl(), e);
            }
        }

        pay.setState(OrderStatesEnum.SUCCESS_PAY.getState());
        pay.setUpdateTime(new Date());
        orderMapper.updateById(pay);
        return 1;
    }

    @Override
    @Transactional
    public int back(String id) {
        Order pay = orderMapper.selectById(id);
        if (pay == null) {
            return 0;
        }
        if (OrderStatesEnum.SUCCESS_PAY.getState().equals(pay.getState())) {
            return -1;
        }
        if (!OrderStatesEnum.WAIT_PAY.getState().equals(pay.getState())
                && !OrderStatesEnum.SCANE_QR.getState().equals(pay.getState())) {
            return -1;
        }
        pay.setState(OrderStatesEnum.FAIL_PAY.getState());
        pay.setUpdateTime(new Date());
        orderMapper.updateById(pay);
        return 1;
    }

    @Override
    public IPage<Order> list(GetOrderListReq req) {

        LambdaQueryWrapper<Order> wrapper = Wrappers.lambdaQuery();
        List<Integer> states = req.getStates();
        if (states == null || states.isEmpty()) {
            states = Arrays.asList(0, 1, 2, 4, 5);
        }
        wrapper.in(Order::getState, states);
        if (StringUtils.isNotBlank(req.getOrderBy())) {
            String orderBy = req.getOrderBy();
            boolean desc = !"asc".equalsIgnoreCase(req.getOrder());
            if ("createTime".equals(orderBy)) {
                if (desc) {
                    wrapper.orderByDesc(Order::getCreateTime);
                } else {
                    wrapper.orderByAsc(Order::getCreateTime);
                }
            }

            if ("money".equals(orderBy)) {
                if (desc) {
                    wrapper.orderByDesc(Order::getMoney);
                } else {
                    wrapper.orderByAsc(Order::getMoney);
                }
            }
        }

        if (StringUtils.isNotBlank(req.getKeyword())) {
            //字符串类型的处理，统一全部like查询
            String keyword = req.getKeyword();
            wrapper.and(w -> w.like(Order::getEmail, keyword)
                    .or().like(Order::getNickName, keyword));
        }
        long pageIndex = req.getPageIndex() == null || req.getPageIndex() < 1 ? 1 : req.getPageIndex();
        long pageSize = req.getPageSize() == null || req.getPageSize() < 1 ? 10 : Math.min(req.getPageSize(), 100);
        Page<Order> page = new Page<>(pageIndex, pageSize);
        Page<Order> payPage = orderMapper.selectPage(page, wrapper);

        for (Order record : payPage.getRecords()) {
            // 屏蔽隐私数据
            if (StringUtils.isNotBlank(record.getId())) {
                record.setId(record.getId().substring(0, Math.min(8, record.getId().length())) + "****");
            }
            record.setEmail("");
            record.setPayNum(null);
            record.setMobile(null);
            record.setCustom(null);
            record.setDevice(null);
            record.setUserId(null);
            record.setNotifyUrl(null);
        }
        return payPage;
    }

    @Override
    public IPage<Order> publicList(GetOrderListReq req) {
        if (req == null) {
            req = new GetOrderListReq();
        }
        // 公开记录只允许成功订单，不允许关键字检索，避免邮箱/昵称盲测。
        req.setStates(Collections.singletonList(OrderStatesEnum.SUCCESS_PAY.getState()));
        req.setKeyword(null);
        int size = req.getPageSize() == null || req.getPageSize() < 1
                ? 10 : Math.min(req.getPageSize(), 20);
        req.setPageSize(size);

        IPage<Order> page = list(req);
        for (Order record : page.getRecords()) {
            if (record.getId() != null && record.getId().length() > 8) {
                record.setId(record.getId().substring(0, 8) + "****");
            }
            record.setDescription(null);
            record.setEmail(null);
            record.setPayNum(null);
            record.setDevice(null);
            record.setMobile(null);
            record.setUserId(null);
            record.setNotifyUrl(null);
            record.setActualAmount(null);
            record.setProductId(null);
            record.setOrderSource(null);
            record.setMatchMode(null);
            record.setPayQrNum(null);
            record.setExpireTime(null);
            record.setUpdateTime(null);
            record.setNickName(null);
        }
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiOrderResp createOpenApiOrder(OpenApiOrderReq req) {
        if (!openApiSignUtil.verifyTimestamp(req.getTimestamp())) {
            throw new ApiException(ApiException.ErrorCode.TIMESTAMP_ERROR, "请求时间戳无效或已过期");
        }

        if (!openApiSignUtil.verifySign(req)) {
            throw new ApiException(ApiException.ErrorCode.SIGN_ERROR, "签名验证失败");
        }

        if (req.getAmount() == null || req.getAmount().compareTo(new BigDecimal("0")) <= 0) {
            throw new ApiException(ApiException.ErrorCode.AMOUNT_ERROR, "金额必须大于0");
        }

        if (req.getAmount().compareTo(new BigDecimal("100000")) > 0) {
            throw new ApiException(ApiException.ErrorCode.AMOUNT_ERROR, "金额超出限制，单笔订单不能超过100000元");
        }
        if (req.getAmount().scale() > 2) {
            throw new ApiException(ApiException.ErrorCode.AMOUNT_ERROR, "金额最多保留两位小数");
        }

        boolean payTypeSupported = payProConfig.getPayMethods() != null
                && payProConfig.getPayMethods().stream()
                .anyMatch(m -> m.isStatus() && m.getId().equals(req.getPayType()));
        if (!payTypeSupported) {
            throw new ApiException(ApiException.ErrorCode.INVALID_PARAM, "不支持的支付方式");
        }

        Order existingOrder = orderMapper.selectById(req.getOrderNo());
        if (existingOrder != null) {
            throw new ApiException(ApiException.ErrorCode.DUPLICATE_ORDER, "订单号已存在");
        }

        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        req.setAmount(amount);

        Order order = new Order();
        order.setId(req.getOrderNo());
        order.setMoney(amount);
        order.setPayType(req.getPayType());
        order.setNickName(req.getNickName());
        order.setEmail(req.getEmail());
        order.setNotifyUrl(req.getNotifyUrl());
        order.setUserId(req.getUserId());
        order.setProductId(req.getProductId());
        order.setOrderSource("OPENAPI");
        order.setState(OrderStatesEnum.WAIT_PAY.getState());
        order.setCreateTime(new Date());
        int expireSeconds = req.getExpireSeconds() != null && req.getExpireSeconds() > 0
                ? req.getExpireSeconds()
                : payProConfig.getOrder().getTimeoutMinutes().intValue() * 60;
        order.setExpireTime(DateUtil.offsetSecond(new Date(), expireSeconds));


        // 固定金额二维码模式：不再减额，实际金额等于订单金额。
        boolean localQr = Boolean.TRUE.equals(payProConfig.getUseLocalQrCode(req.getPayType()));
        int qrNum = 1;
        String qrUrl = "";
        if (localQr) {
            List<Integer> available = findAvailableQrNumbers(req.getPayType(), amount);
            if (available.isEmpty()) {
                throw new ApiException(ApiException.ErrorCode.AMOUNT_ERROR,
                        "当前金额未配置微信收款二维码");
            }
            qrNum = available.get(ThreadLocalRandom.current().nextInt(available.size()));
            qrUrl = payProConfig.getSite() + "/assets/qr/"
                    + req.getPayType().toLowerCase(Locale.ROOT) + "/"
                    + QrCodeResolver.formatAmount(amount) + "/" + qrNum + ".png";
        }

        order.setMatchMode("REMARK");
        order.setActualAmount(amount);
        order.setDecrementIndex(null);
        order.setPayQrNum(qrNum);
        order.setPayNum(StringUtils.getRandomNum());
        order.setCustom(false);

        String formattedAmount = QrCodeResolver.formatAmount(amount);
        String encodedQrUrl = "";
        try {
            encodedQrUrl = java.net.URLEncoder.encode(qrUrl, "UTF-8");
        } catch (Exception ignored) {
            // qrUrl 只包含 ASCII 路径；编码失败时由支付页从金额与编号重建
        }
        Boolean useLocalQrCodeConfig = localQr;
        String returnUrl = payProConfig.getSite() + "/payment.html?" +
                "orderId=" + req.getOrderNo() +
                "&money=" + formattedAmount +
                "&payType=" + req.getPayType() +
                "&payNum=" + (order.getPayNum() != null ? order.getPayNum() : "") +
                "&picName=" + formattedAmount +
                "&payQrNum=" + qrNum +
                "&useLocalQrCode=" + useLocalQrCodeConfig +
                "&localQr=" + localQr +
                "&qrUrl=" + encodedQrUrl +
                "&matchMode=REMARK" +
                "&actualAmount=" + formattedAmount +
                "&fallbackToRemark=false";
        try {
            orderMapper.insert(order);
        } catch (Exception e) {
            log.error("创建OpenApi订单失败: {}", e.getMessage(), e);
            throw new ApiException(ApiException.ErrorCode.SYSTEM_ERROR, "创建订单失败");
        }

        String tokenAdmin = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(order.getId(), tokenAdmin, payProConfig.getToken().getExpire(), TimeUnit.DAYS);

        String reviewToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("review:token:" + order.getId(),
                reviewToken, 30, TimeUnit.MINUTES);
        order = getReviewUrl(order, order.getId(), reviewToken);
        if(StringUtils.isNotBlank(payProConfig.getEmail().getReceiver())
                && StringUtils.isNotBlank(payProConfig.getEmail().getSender())){
            emailUtils.sendTemplateMail(payProConfig.getEmail().getSender(),
                    payProConfig.getEmail().getReceiver(),
                    "【" + payProConfig.getTitle() + "】待审核处理", "payment-review", order);
        }

        return OpenApiOrderResp.builder()
                .orderId(order.getId())
                .orderNo(req.getOrderNo())
                .amount(req.getAmount())
                .payType(req.getPayType())
                .payNum(order.getPayNum())
                .state(order.getState())
                .message("订单创建成功")
                .qrCodeUrl(qrUrl)
                .returnUrl(returnUrl)
                .timestamp(System.currentTimeMillis())
                .matchMode("REMARK")
                .actualAmount(amount)
                .fallbackToRemark(false)
                .build();
    }

    /**
     * 检查指定支付类型和金额的二维码文件是否存在
     *
     * @param payType 支付类型，如 "wechat"、"alipay"，需与文件夹名称匹配
     * @param amount  金额，将格式化为两位小数作为文件夹名
     * @param qrNum   二维码编号，用于构建文件名
     * @return 文件存在返回 true，否则返回 false
     */
    private boolean checkQrFileExists(String payType, BigDecimal amount, int qrNum) {
        // 格式化金额为两位小数，与文件夹名称一致
        String amountStr = String.format("%.2f", amount);
        String relativePath = payType.toLowerCase() + "/" + amountStr + "/" + qrNum + ".png";

        // 优先检查外部目录
        String qrDir = payProConfig.getQrDir();
        if (org.springframework.util.StringUtils.hasText(qrDir)) {
            String externalPath = qrDir.endsWith("/") ? qrDir + relativePath : qrDir + "/" + relativePath;
            try {
                if (resourceLoader.getResource("file:" + externalPath).exists()) {
                    return true;
                }
            } catch (Exception e) {
                // ignore, try classpath fallback
            }
        }

        // classpath 回退（修复原 bug：static/qr -> static/assets/qr）
        String classpathPath = "classpath:static/assets/qr/" + relativePath;
        try {
            Resource resource = resourceLoader.getResource(classpathPath);
            return resource.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 返回当前支付方式与金额在 QR_DIR 中真实存在的二维码编号。
     */
    private List<Integer> findAvailableQrNumbers(String payType, BigDecimal amount) {
        int qrCodeNum = payProConfig.getQrCodeNum() == null || payProConfig.getQrCodeNum() < 1
                ? 1 : payProConfig.getQrCodeNum();
        return QrCodeResolver.findAvailableNumbers(
                payProConfig.getQrDir(), payType, amount, qrCodeNum);
    }


    /**
     * 拼接审核页链接。邮件里只放这一条，页面只展示，不直接执行。
     */
    public Order getReviewUrl(Order pay, String id, String reviewToken) {
        pay.setReviewUrl(payProConfig.getSite() + "/review?id=" + id + "&t=" + reviewToken);
        return pay;
    }

    public void callbackFaka(String notifyUrl, String orderNo, BigDecimal amount, String payNum) {
        if (StringUtils.isBlank(notifyUrl)) {
            return;
        }
        try {
            URI uri = URI.create(notifyUrl);
            if (uri.getHost() == null
                    || (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("notifyUrl must be http(s)");
            }
            InetAddress address = InetAddress.getByName(uri.getHost());
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                throw new IllegalArgumentException("notifyUrl must not target a private address");
            }
        } catch (Exception e) {
            log.warn("拒绝不安全的 OpenAPI 回调地址: {}", notifyUrl);
            return;
        }

        Map<String, Object> params = new TreeMap<>();
        params.put("orderNo", orderNo);
        params.put("amount", amount);
        params.put("payNum", payNum);

        // 生成签名
        String sign = openApiSignUtil.generateSign(params);
        params.put("sign", sign);
        HttpUtil.post(notifyUrl, com.alibaba.fastjson2.JSONObject.toJSONString(params), 5000);
    }
    // ==================== 减额匹配相关方法 ====================

    /**
     * 减额匹配槽位分配结果
     */
    @lombok.Data
    private static class DecrementSlotResult {
        private boolean success;
        private boolean fallbackToRemark;
        private int slotIndex;
        private BigDecimal actualAmount;
    }

    /**
     * 尝试分配减额槽位
     * maxCount = 向下递减次数(不含基准价)
     * 例如 maxCount=2: 槽位0=5.00, 槽位1=4.99, 槽位2=4.98
     *
     * @param payType    支付类型
     * @param baseAmount 基础金额(原始订单金额)
     * @param config     减额配置
     * @return 槽位分配结果
     */
    private DecrementSlotResult tryAllocateDecrementSlot(
            String payType, BigDecimal baseAmount, PayProConfig.Decrement config) {

        BigDecimal step = config.getStep();
        int maxCount = config.getMaxCount();

        // 收集已占用的槽位索引
        Set<Integer> occupiedSlots = new HashSet<>();
        for (int i = 0; i <= maxCount; i++) {
            BigDecimal slotAmount = baseAmount.subtract(step.multiply(new BigDecimal(i)));
            // 实际金额必须 > 0
            if (slotAmount.compareTo(BigDecimal.ZERO) <= 0) {
                occupiedSlots.add(i);
                continue;
            }
            // 检查该金额的QR码文件是否存在(任意编号)
            if (!checkAnyQrFileExists(payType, baseAmount, slotAmount)) {
                log.warn("减额槽位{}金额{}的QR码文件不存在，跳过该槽位", i, String.format("%.2f", slotAmount));
                occupiedSlots.add(i);
                continue;
            }
            // 查询该金额下是否有活跃订单
            List<Order> activeOrders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getActualAmount, slotAmount)
                            .eq(Order::getMatchMode, "DECREMENT")
                            .eq(Order::getPayType, payType)
                            .in(Order::getState, 0, 4));
            if (activeOrders != null && !activeOrders.isEmpty()) {
                occupiedSlots.add(i);
            }
        }

        // 找第一个可用槽位
        for (int i = 0; i <= maxCount; i++) {
            if (!occupiedSlots.contains(i)) {
                BigDecimal actualAmount = baseAmount.subtract(step.multiply(new BigDecimal(i)));
                DecrementSlotResult result = new DecrementSlotResult();
                result.setSuccess(true);
                result.setSlotIndex(i);
                result.setActualAmount(actualAmount);
                result.setFallbackToRemark(false);
                return result;
            }
        }

        // 所有槽位被占用 -- 回退到备注模式
        DecrementSlotResult result = new DecrementSlotResult();
        result.setSuccess(true);
        result.setFallbackToRemark(true);
        // 回退金额 = 基础金额 + step，用不同金额区分
        result.setActualAmount(baseAmount.add(step));
        return result;
    }

    /**
     * 检查指定支付类型和金额下是否有任意QR码文件存在(使用正确的assets/qr路径)
     * 减额二维码放在基础金额目录下: {payType}/{baseAmount}/{amount}/{qrNum}.png
     * 槽位0(金额等于基础金额)保持原有路径: {payType}/{baseAmount}/{qrNum}.png
     */
    private boolean checkAnyQrFileExists(String payType, BigDecimal baseAmount, BigDecimal amount) {
        String baseAmountStr = String.format("%.2f", baseAmount);
        String amountStr = String.format("%.2f", amount);
        int qrCodeNum = payProConfig.getQrCodeNum() == null || payProConfig.getQrCodeNum() < 1
                ? 1 : payProConfig.getQrCodeNum();
        for (int i = 1; i <= qrCodeNum; i++) {
            String relativePath;
            if (amount.compareTo(baseAmount) == 0) {
                // 槽位0: 基础金额目录下直接放二维码
                relativePath = payType.toLowerCase() + "/" + baseAmountStr + "/" + i + ".png";
            } else {
                // 减额槽位: 放在基础金额目录下的子目录
                relativePath = payType.toLowerCase() + "/" + baseAmountStr
                        + "/" + amountStr + "/" + i + ".png";
            }
            String qrDir = payProConfig.getQrDir();
            if (StringUtils.isNotBlank(qrDir)) {
                String externalPath = qrDir.endsWith("/") ? qrDir + relativePath : qrDir + "/" + relativePath;
                try {
                    if (resourceLoader.getResource("file:" + externalPath).exists()) {
                        return true;
                    }
                } catch (Exception e) {
                    // ignore, fall back to classpath
                }
            }
            try {
                Resource resource = resourceLoader.getResource("classpath:static/assets/qr/" + relativePath);
                if (resource.exists()) {
                    return true;
                }
            } catch (Exception e) {
                // ignore, try next
            }
        }
        return false;
    }

    @Override
    public Order getByActualAmount(BigDecimal actualAmount, Date startTime, Date endTime) {
        return orderMapper.getDecrementOrderByAmount(actualAmount, startTime, endTime);
    }

    @Override
    public Set<Integer> getOccupiedDecrementSlots(String payType, BigDecimal baseAmount) {
        PayProConfig.Decrement config = payProConfig.getDecrement();
        if (config == null || !config.isEnabled()) {
            return Collections.emptySet();
        }
        Set<Integer> occupied = new HashSet<>();
        BigDecimal step = config.getStep();
        for (int i = 0; i <= config.getMaxCount(); i++) {
            BigDecimal slotAmount = baseAmount.subtract(step.multiply(new BigDecimal(i)));
            if (slotAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            List<Order> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getActualAmount, slotAmount)
                            .eq(Order::getMatchMode, "DECREMENT")
                            .eq(Order::getPayType, payType)
                            .in(Order::getState, 0, 4));
            if (orders != null && !orders.isEmpty()) {
                occupied.add(i);
            }
        }
        return occupied;
    }

    public static String getDecrementLockKey(String payType, String baseAmount) {
        return "pay:decrement_lock:" + payType + ":" + baseAmount;
    }

}
