package com.wendao.controller;

import cn.hutool.log.StaticLog;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wendao.common.utils.EmailUtils;
import com.wendao.common.utils.StringUtils;
import com.wendao.config.PayProConfig;
import com.wendao.entity.Order;
import com.wendao.enums.OrderStatesEnum;
import com.wendao.exception.ApiException;
import com.wendao.model.ResponseVO;
import com.wendao.model.req.GetOrderListReq;
import com.wendao.model.req.OpenApiOrderReq;
import com.wendao.model.req.OrderReq;
import com.wendao.model.resp.AddOrderResp;
import com.wendao.model.resp.OpenApiOrderResp;
import com.wendao.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

@Controller
@Api(tags = "开放接口", description = "订单管理")
@CacheConfig(cacheNames = "order")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private EmailUtils emailUtils;

    @Autowired
    private PayProConfig payProConfig;

    private boolean tokenEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private boolean adminTokenValid(String token) {
        return tokenEquals(token, payProConfig.getToken().getValue());
    }

    private String reviewKey(String id) {
        return "review:token:" + id;
    }

    /**
     * 管理端订单列表。必须携带 X-Admin-Token 请求头，不能再用 URL 传 token。
     */
    @PostMapping("/order/admin/list")
    @ApiOperation(value = "管理端获取支付数据列表")
    @ResponseBody
    public ResponseVO<IPage<Order>> getAdminOrderList(
            @RequestBody GetOrderListReq req,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            HttpServletResponse response) {
        if (!adminTokenValid(adminToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return ResponseVO.errorResponse(401, "未授权");
        }
        return ResponseVO.successResponse(orderService.list(req));
    }

    /**
     * 公开支持记录。只返回成功订单的脱敏字段，不支持关键字检索。
     */
    @PostMapping("/order/publicList")
    @ApiOperation(value = "公开支持记录")
    @ResponseBody
    public ResponseVO<IPage<Order>> getPublicOrderList(
            @RequestBody(required = false) GetOrderListReq req) {
        return ResponseVO.successResponse(orderService.publicList(req));
    }

    @GetMapping("/order/state/{id}")
    @ApiOperation(value = "获取支付状态")
    @ResponseBody
    public ResponseVO getOrderState(@PathVariable String id) {
        Order order;
        try {
            order = orderService.getOrderById(id);
        } catch (Exception e) {
            return ResponseVO.errorResponse("获取支付数据失败");
        }
        if (order == null) {
            return ResponseVO.errorResponse("订单不存在");
        }

        if (order.getExpireTime() != null && order.getExpireTime().before(new Date())) {
            if (OrderStatesEnum.WAIT_PAY.getState().equals(order.getState())) {
                order.setState(OrderStatesEnum.EXPIRED.getState());
                orderService.updateOrder(order);
            }
            return ResponseVO.successResponse(OrderStatesEnum.EXPIRED.getState());
        }

        return ResponseVO.successResponse(order.getState());
    }

    @GetMapping("/order/{id:[0-9a-fA-F]{32}}")
    @ApiOperation(value = "获取支付数据")
    @ResponseBody
    public ResponseVO<Object> getPayData(@PathVariable String id,
                                         @RequestParam String token) {
        String cached = redisTemplate.opsForValue().get(id);
        if (!tokenEquals(token, cached)) {
            return ResponseVO.errorResponse("无效的Token或链接");
        }
        Order order;
        try {
            order = orderService.getOrderById(id);
        } catch (Exception e) {
            StaticLog.error(e);
            return ResponseVO.errorResponse("获取支付数据失败");
        }
        if (order == null) {
            return ResponseVO.errorResponse("订单不存在");
        }
        return ResponseVO.successResponse(order);
    }

    @PostMapping("/order/add")
    @ApiOperation(value = "添加支付订单")
    @ResponseBody
    public ResponseVO<AddOrderResp> addOrder(@RequestBody @Validated OrderReq orderReq,
                                             HttpServletRequest request) {
        orderReq.setDevice(request.getHeader("user-agent"));
        return orderService.addOrder(orderReq, request);
    }

    /**
     * 编辑订单。只允许改昵称、附言、邮箱；金额/支付方式/支付标识/状态一律不接受。
     */
    @PostMapping("/order/edit")
    @ApiOperation(value = "编辑支付订单")
    @ResponseBody
    @CacheEvict(key = "#id")
    public ResponseVO editOrder(@RequestParam String id,
                                @RequestParam String token,
                                @RequestParam(required = false) String nickName,
                                @RequestParam(required = false) String description,
                                @RequestParam(required = false) String email) {
        if (!tokenEquals(token, redisTemplate.opsForValue().get(id))) {
            return ResponseVO.errorResponse("无效的Token或链接");
        }
        if (orderService.getOrderById(id) == null) {
            return ResponseVO.errorResponse("订单不存在");
        }
        if (nickName != null && nickName.length() > 255) {
            return ResponseVO.errorResponse("昵称过长");
        }
        if (description != null && description.length() > 255) {
            return ResponseVO.errorResponse("附言过长");
        }
        if (StringUtils.isNotBlank(email) && !EmailUtils.checkEmail(email)) {
            return ResponseVO.errorResponse("邮箱格式不正确");
        }

        Order update = new Order();
        update.setId(id);
        update.setNickName(nickName);
        update.setDescription(description);
        update.setEmail(email);
        orderService.updateOrder(update);
        return ResponseVO.successResponse();
    }

    /**
     * 审核页：只展示，不修改任何状态。GET 可以被邮件安全扫描器安全访问。
     */
    @GetMapping("/review")
    public String review(@RequestParam String id,
                         @RequestParam(value = "t", required = false) String t,
                         Model model) {
        if (!tokenEquals(t, redisTemplate.opsForValue().get(reviewKey(id)))) {
            model.addAttribute("errorMsg", "审核链接无效或已过期");
            return "error";
        }
        Order order = orderService.getOrderById(id);
        if (order == null) {
            model.addAttribute("errorMsg", "订单不存在");
            return "error";
        }
        boolean canReview = OrderStatesEnum.WAIT_PAY.getState().equals(order.getState())
                || OrderStatesEnum.SCANE_QR.getState().equals(order.getState());
        model.addAttribute("order", order);
        model.addAttribute("reviewToken", t);
        model.addAttribute("canReview", canReview);
        return "review";
    }

    /**
     * 审核通过。必须 POST，必须带一次性审核 token，必须输入实际到账金额。
     */
    @PostMapping("/order/pass")
    @ApiOperation(value = "审核通过支付订单")
    @CacheEvict(key = "#id")
    public String pass(@RequestParam String id,
                       @RequestParam(value = "t", required = false) String t,
                       @RequestParam(value = "receivedAmount", required = false) String receivedAmount,
                       Model model) {
        if (!tokenEquals(t, redisTemplate.opsForValue().get(reviewKey(id)))) {
            model.addAttribute("errorMsg", "审核链接无效或已过期");
            return "error";
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(receivedAmount == null ? "" : receivedAmount.trim());
        } catch (Exception e) {
            model.addAttribute("errorMsg", "到账金额格式不正确");
            return "error";
        }

        int result;
        try {
            result = orderService.pass(id, amount);
        } catch (Exception e) {
            log.error("审核通过失败, id={}", id, e);
            model.addAttribute("errorMsg", "处理数据出错");
            return "error";
        }

        if (result == 1) {
            redisTemplate.delete(reviewKey(id));
            return "redirect:/success";
        }
        String msg;
        if (result == -2) {
            msg = "到账金额与订单金额不一致，未通过审核";
        } else if (result == -1) {
            msg = "订单状态不允许审核";
        } else {
            msg = "订单不存在";
        }
        model.addAttribute("errorMsg", msg);
        return "error";
    }

    /**
     * 审核驳回。必须 POST，必须带一次性审核 token。
     */
    @PostMapping("/order/back")
    @ApiOperation(value = "审核驳回支付订单")
    @CacheEvict(key = "#id")
    public String backPay(@RequestParam String id,
                          @RequestParam(value = "t", required = false) String t,
                          Model model) {
        if (!tokenEquals(t, redisTemplate.opsForValue().get(reviewKey(id)))) {
            model.addAttribute("errorMsg", "审核链接无效或已过期");
            return "error";
        }

        int result;
        try {
            result = orderService.back(id);
        } catch (Exception e) {
            log.error("审核驳回失败, id={}", id, e);
            model.addAttribute("errorMsg", "处理数据出错");
            return "error";
        }

        if (result == 1) {
            redisTemplate.delete(reviewKey(id));
            Order order = orderService.getOrderById(id);
            if (order != null
                    && StringUtils.isNotBlank(order.getEmail())
                    && EmailUtils.checkEmail(order.getEmail())) {
                emailUtils.sendTemplateMail(payProConfig.getEmail().getSender(), order.getEmail(),
                        "【" + payProConfig.getTitle() + "】支付失败通知", "order-fail", order);
            }
            return "redirect:/success";
        }
        model.addAttribute("errorMsg", "订单状态不允许驳回");
        return "error";
    }

    @PostMapping("/order/del")
    @ApiOperation(value = "删除支付订单")
    @ResponseBody
    @CacheEvict(key = "#id")
    public ResponseVO<Object> dePay(@RequestParam String id,
                                    @RequestParam String token) {
        if (!tokenEquals(token, redisTemplate.opsForValue().get(id))) {
            return ResponseVO.errorResponse("无效的Token或链接");
        }
        try {
            orderService.delOrder(id);
            redisTemplate.delete(id);
            redisTemplate.delete(reviewKey(id));
        } catch (Exception e) {
            log.error("删除支付订单失败, id={}", id, e);
            return ResponseVO.errorResponse("删除支付订单失败");
        }
        return ResponseVO.successResponse();
    }

    @PostMapping("/order/statistic")
    @ApiOperation(value = "数据统计")
    @ResponseBody
    public ResponseVO<Object> statistic(@RequestParam Integer type,
                                        @RequestParam(required = false) String start,
                                        @RequestParam(required = false) String end,
                                        @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
                                        HttpServletResponse response) {
        if (!adminTokenValid(adminToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return ResponseVO.errorResponse(401, "未授权");
        }
        return ResponseVO.successResponse(orderService.statistic(type, start, end));
    }

    @PostMapping("/api/openapi/add")
    @ApiOperation(value = "创建OpenApi订单", notes = "OpenApi通过此接口创建支付订单")
    @ResponseBody
    public ResponseVO<OpenApiOrderResp> createOpenApiOrder(@RequestBody @Validated OpenApiOrderReq req) {
        try {
            log.info("收到OpenApi订单创建请求 - 订单号: {}, 金额: {}, 支付方式: {}",
                    req.getOrderNo(), req.getAmount(), req.getPayType());

            OpenApiOrderResp resp = orderService.createOpenApiOrder(req);

            log.info("OpenApi订单创建成功 - 订单ID: {}, 订单号: {}", resp.getOrderId(), resp.getOrderNo());
            return new ResponseVO<>(ApiException.ErrorCode.SUCCESS, "success", resp);
        } catch (ApiException e) {
            log.warn("OpenApi订单创建失败 - 错误码: {}, 错误信息: {}", e.getCode(), e.getMessage());
            return new ResponseVO<>(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("OpenApi订单创建系统异常", e);
            return new ResponseVO<>(ApiException.ErrorCode.SYSTEM_ERROR, "系统内部异常");
        }
    }

    @GetMapping("/getPayMethods")
    @ApiOperation(value = "获取支付信息配置", notes = "获取支付信息配置")
    @ResponseBody
    public ResponseVO<PayProConfig.PayMethod> getPayMethods() {
        return ResponseVO.successResponse(payProConfig.getPayMethods());
    }
}
