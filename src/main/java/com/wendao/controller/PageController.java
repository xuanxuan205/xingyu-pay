package com.wendao.controller;

import com.wendao.common.utils.StringUtils;
import com.wendao.entity.Order;
import com.wendao.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 */
@Controller
public class PageController {

    private static final Logger log= LoggerFactory.getLogger(PageController.class);

    /**
     * Only expose real view names. Without this whitelist, /{page} turns every
     * unknown URL into a Thymeleaf lookup and can also be used for template
     * name probing.
     */
    private static final Set<String> PAGES = new HashSet<>(Arrays.asList(
            "index", "home", "home1", "recharge", "payment", "history", "help",
            "error", "openAlipay", "order-del", "order-edit", "order-fail",
            "order-success", "payment-review", "review", "statistic", "success"
    ));

    @Autowired
    private OrderService orderService;

    @RequestMapping("/")
    public String index(Model model){
        return "index";
    }

    @RequestMapping("/{page}")
    public String showPage(@PathVariable String page,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           Model model){

        String view = page.endsWith(".html")
                ? page.substring(0, page.length() - ".html".length())
                : page;

        if("order-success".equals(view)){
            return "index";
        }
        if(!PAGES.contains(view)){
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return "error";
        }
        if ("openAlipay".equals(view)) {
            String id = request.getParameter("id");
            if (StringUtils.isBlank(id) || !id.matches("[0-9a-fA-F]{32}")) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                return "error";
            }
            Order order;
            try {
                order = orderService.getOrderById(id);
            } catch (Exception e) {
                log.warn("openAlipay order lookup failed for {}", id, e);
                response.setStatus(HttpStatus.NOT_FOUND.value());
                return "error";
            }
            if (order == null || order.getMoney() == null) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                return "error";
            }
            // 金额和支付标识只从数据库取，URL 上的 money/payNum 一律忽略。
            model.addAttribute("payMoney",
                    order.getMoney().setScale(2, RoundingMode.HALF_UP).toPlainString());
            model.addAttribute("payNum", order.getPayNum());
            model.addAttribute("orderId", order.getId());
        }
        return view;
    }

    /**
     * 图标
     */
    @GetMapping({"favicon.ico", "favicon"})
    @ResponseBody
    public void favicon(HttpServletResponse response) {
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader("Location", "/assets/images/favicon.ico");
    }
}
