package com.wendao.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Adds browser security headers without changing page markup.
 *
 * The pages use Vue's in-browser template compiler and a small number of CDN
 * assets, so the CSP must allow inline/eval scripts. All network destinations
 * are still limited to the site itself and the known CDN hosts.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "base-uri 'self'",
            "object-src 'none'",
            "frame-ancestors 'none'",
            "form-action 'self'",
            "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.bootcdn.net https://cdnjs.cloudflare.com https://gw.alipayobjects.com",
            "style-src 'self' 'unsafe-inline' https://gw.alipayobjects.com",
            "img-src 'self' data: https:",
            "font-src 'self' data:",
            "connect-src 'self'",
            "media-src 'self'",
            "worker-src 'self'",
            "manifest-src 'self'"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Content-Security-Policy", CSP);

        String path = request.getRequestURI();
        if (isSensitivePath(path)) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSensitivePath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/review")
                || path.startsWith("/api/openapi")
                || path.startsWith("/order/admin")
                || path.startsWith("/order/statistic")
                || path.startsWith("/order/pass")
                || path.startsWith("/order/back")
                || path.startsWith("/order/del")
                || path.startsWith("/order/edit")
                || path.startsWith("/order/state");
    }
}
