package com.wendao.service.impl;

import com.wendao.common.utils.EmailUtils;
import com.wendao.config.PayProConfig;
import com.wendao.entity.Order;
import com.wendao.model.ResponseVO;
import com.wendao.model.req.OrderReq;
import com.wendao.model.resp.AddOrderResp;
import com.wendao.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplQrTest {

    @Mock
    private PayProConfig payProConfig;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private OrderService thisService;

    @Mock
    private EmailUtils emailUtils;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private OrderServiceImpl orderService;

    @TempDir
    Path qrDir;

    @BeforeEach
    void setUp() {
        PayProConfig.PayMethod wechat = new PayProConfig.PayMethod();
        wechat.setId("wechat");
        wechat.setStatus(true);
        wechat.setUseLocalQrCode(true);

        PayProConfig.RateLimit rateLimit = new PayProConfig.RateLimit();
        rateLimit.setIpExpire(1L);

        PayProConfig.Order orderConfig = new PayProConfig.Order();
        orderConfig.setTimeoutMinutes(30L);

        PayProConfig.Token token = new PayProConfig.Token();
        token.setValue("test-token");
        token.setExpire(14L);

        PayProConfig.Email email = new PayProConfig.Email();
        email.setSender("");
        email.setReceiver("");

        when(payProConfig.getPayMethods()).thenReturn(Collections.singletonList(wechat));
        when(payProConfig.getUseLocalQrCode("wechat")).thenReturn(true);
        when(payProConfig.getQrCodeNum()).thenReturn(2);
        when(payProConfig.getQrDir()).thenReturn(qrDir.toString());
        when(payProConfig.getRateLimit()).thenReturn(rateLimit);
        when(payProConfig.getOrder()).thenReturn(orderConfig);
        when(payProConfig.getToken()).thenReturn(token);
        when(payProConfig.getSite()).thenReturn("http://127.0.0.1:8901");
        when(payProConfig.getEmail()).thenReturn(email);
        when(payProConfig.getTitle()).thenReturn("test");
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(thisService.addOrder(any(Order.class))).thenReturn(1);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void usesProductPriceAndExistingQrNumber() throws IOException {
        Path amountDir = qrDir.resolve("wechat").resolve("5.00");
        Files.createDirectories(amountDir);
        Files.createFile(amountDir.resolve("2.png"));
        when(payProConfig.getProductConfigById(1)).thenReturn(product(1, "5.00"));

        OrderReq req = new OrderReq();
        req.setProductId(1L);
        req.setPayType("wechat");
        req.setMoney(new BigDecimal("999.00"));

        ResponseVO<AddOrderResp> response = orderService.addOrder(req, request);

        assertEquals(200, response.getCode());
        AddOrderResp data = response.getData();
        assertEquals(new BigDecimal("5.00"), data.getMoney());
        assertEquals(2, data.getPayQrNum());
        assertTrue(data.getLocalQr());
        assertFalse(data.getCustom());
        assertEquals("/assets/qr/wechat/5.00/2.png", data.getQrUrl());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(thisService).addOrder(captor.capture());
        Order saved = captor.getValue();
        assertEquals(new BigDecimal("5.00"), saved.getMoney());
        assertEquals(new BigDecimal("5.00"), saved.getActualAmount());
        assertEquals("REMARK", saved.getMatchMode());
        assertEquals(2, saved.getPayQrNum());
        assertNotNull(saved.getPayNum());
    }

    @Test
    void rejectsWhenAmountQrIsMissingAndDoesNotCreateOrder() {
        when(payProConfig.getProductConfigById(2)).thenReturn(product(2, "10.00"));

        OrderReq req = new OrderReq();
        req.setProductId(2L);
        req.setPayType("wechat");
        req.setMoney(new BigDecimal("10.00"));

        ResponseVO<AddOrderResp> response = orderService.addOrder(req, request);

        assertEquals(500, response.getCode());
        assertTrue(response.getMsg().contains("未配置"));
        verify(thisService, never()).addOrder(any(Order.class));
    }

    @Test
    void rejectsCustomAmountAndMissingProduct() {
        OrderReq custom = new OrderReq();
        custom.setProductId(1L);
        custom.setPayType("wechat");
        custom.setMoney(new BigDecimal("5.00"));
        custom.setCustom(true);

        assertEquals(500, orderService.addOrder(custom, request).getCode());

        OrderReq noProduct = new OrderReq();
        noProduct.setPayType("wechat");
        noProduct.setMoney(new BigDecimal("5.00"));

        assertEquals(500, orderService.addOrder(noProduct, request).getCode());
        verify(thisService, never()).addOrder(any(Order.class));
    }

    @Test
    void rejectsSecondRequestFromSameIpWithinWindow() {
        Path amountDir = qrDir.resolve("wechat").resolve("5.00");
        assertDoesNotThrow(() -> {
            Files.createDirectories(amountDir);
            Files.createFile(amountDir.resolve("2.png"));
        });
        when(payProConfig.getProductConfigById(1)).thenReturn(product(1, "5.00"));
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        OrderReq req = new OrderReq();
        req.setProductId(1L);
        req.setPayType("wechat");
        req.setMoney(new BigDecimal("5.00"));

        ResponseVO<AddOrderResp> response = orderService.addOrder(req, request);

        assertEquals(500, response.getCode());
        assertTrue(response.getMsg().contains("频繁"));
        verify(thisService, never()).addOrder(any(Order.class));
    }

    private PayProConfig.ProductConfig product(int id, String money) {
        PayProConfig.ProductConfig product = new PayProConfig.ProductConfig();
        product.setId(id);
        product.setMoney(new BigDecimal(money));
        product.setProductName("test");
        product.setType("CODE");
        return product;
    }
}
