package com.wendao.utils;

import com.wendao.model.req.OpenApiOrderReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiSignUtilTest {

    private OpenApiSignUtil signUtil;

    @BeforeEach
    void setUp() {
        signUtil = new OpenApiSignUtil();
        ReflectionTestUtils.setField(signUtil, "secretKey", "test-secret");
    }

    @Test
    void verifiesCorrectSignatureAndRejectsTampering() {
        OpenApiOrderReq req = new OpenApiOrderReq();
        req.setOrderNo("order-1");
        req.setAmount(new BigDecimal("5.00"));
        req.setPayType("wechat");
        req.setTimestamp(System.currentTimeMillis());

        Map<String, Object> params = new TreeMap<>();
        params.put("orderNo", req.getOrderNo());
        params.put("amount", req.getAmount());
        params.put("payType", req.getPayType());
        params.put("timestamp", req.getTimestamp());
        req.setSign(signUtil.generateSign(params));

        assertTrue(signUtil.verifyTimestamp(req.getTimestamp()));
        assertTrue(signUtil.verifySign(req));

        req.setAmount(new BigDecimal("0.01"));
        assertFalse(signUtil.verifySign(req));
    }
}
