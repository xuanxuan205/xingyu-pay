package com.wendao.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;


/**
 */
public class IpInfoUtils {

    private static final Logger log = LoggerFactory.getLogger(IpInfoUtils.class);

    /**
     * 获取客户端IP地址
     * @param request 请求
     * @return
     */
    public static String getIpAddr(HttpServletRequest request) {
        // 只信任 Nginx 覆盖写入的 X-Real-IP；绝不使用客户端可伪造的 X-Forwarded-For。
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.trim().isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }

        return ip == null ? "" : ip.trim();
    }
}
