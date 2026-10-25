package com.wendao.utils;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.wendao.model.req.OpenApiOrderReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class OpenApiSignUtil {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSignUtil.class);

    @Value("${paypro.openapi.secret:default_secret_key}")
    private String secretKey;

    public String generateSign(Map<String, Object> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null && !"".equals(value) && !"sign".equals(key)) {
                // 对BigDecimal进行特殊处理，保持与前端一致的格式
                String valueStr;
                if (value instanceof BigDecimal) {
                    valueStr = ((BigDecimal) value).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
                } else {
                    valueStr = value.toString();
                }
                sb.append(key).append('=').append(valueStr).append('&');
            }
        }
        sb.append("key=").append(secretKey);

        String signStr = sb.toString();

        return SecureUtil.md5(signStr).toUpperCase();
    }

    public boolean verifySign(OpenApiOrderReq req) {
        Map<String, Object> params = new TreeMap<>();
        params.put("orderNo", req.getOrderNo());
        params.put("amount", req.getAmount());
        params.put("payType", req.getPayType());
        params.put("nickName", req.getNickName());
        params.put("description", req.getDescription());
        params.put("email", req.getEmail());
        params.put("notifyUrl", req.getNotifyUrl());
        params.put("userId", req.getUserId());
        params.put("productId", req.getProductId());
        params.put("timestamp", req.getTimestamp());
        params.put("expireSeconds", req.getExpireSeconds());

        String calculatedSign = generateSign(params);
        boolean isValid = req.getSign() != null && MessageDigest.isEqual(
                calculatedSign.getBytes(StandardCharsets.UTF_8),
                req.getSign().toUpperCase().getBytes(StandardCharsets.UTF_8));

        if (!isValid) {
            log.warn("OpenAPI 签名验证失败, orderNo={}", req.getOrderNo());
        }

        return isValid;
    }

    public boolean verifyTimestamp(Long timestamp) {
        if (timestamp == null) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        long diff = Math.abs(currentTime - timestamp);
        long maxDiff = 5 * 60 * 1000;
        
        if (diff > maxDiff) {
            log.warn("时间戳验证失败 - 当前: {}, 请求: {}, 差值: {}ms", currentTime, timestamp, diff);
            return false;
        }
        return true;
    }

    public static void main(String[] args) {

        System.out.println(DigestUtil.md5Hex("amount=10.00&description=1&email=test@example.com&nickName=1"));
    }
}
