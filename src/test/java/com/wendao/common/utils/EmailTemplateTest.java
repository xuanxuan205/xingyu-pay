package com.wendao.common.utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateTest {

    private static SpringTemplateEngine templateEngine;

    @BeforeAll
    static void setUpTemplateEngine() {
        String configured = System.getProperty("frontend.dir");
        Path templateDir = null;
        if (configured != null && !configured.trim().isEmpty()) {
            templateDir = Paths.get(configured).toAbsolutePath().normalize();
        } else {
            Path projectDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path[] candidates = {
                    projectDir.resolve("frontend/templates").normalize(),
                    projectDir.resolve("../frontend/templates").normalize(),
                    projectDir.resolve("../rebrand/frontend/templates").normalize(),
                    projectDir.resolve("../security/frontend/templates").normalize(),
                    projectDir.resolve("deploy/security/frontend/templates").normalize(),
                    projectDir.resolve("deploy/rebrand/frontend/templates").normalize()
            };
            for (Path candidate : candidates) {
                if (Files.isDirectory(candidate)) {
                    templateDir = candidate;
                    break;
                }
            }
        }
        assertTrue(templateDir != null && Files.isDirectory(templateDir),
                "email template directory not found: " + templateDir);

        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix(templateDir.toString() + File.separator);
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.LEGACYHTML5);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    @Test
    void successTemplateUsesDynamicBrandAndReceiptVariables() {
        Context context = baseContext();
        context.setVariable("money", "5.00");
        context.setVariable("payType", "微信支付");
        context.setVariable("description", "测试订单");
        context.setVariable("time", "2026-10-25 02:30:00");
        context.setVariable("payNum", "9527");
        context.setVariable("id", "ORDER-TEST-001");

        String html = templateEngine.process("order-success", context);

        assertRendered(html, "测试品牌");
        assertTrue(html.contains("5.00"));
        assertTrue(html.contains("9527"));
        assertTrue(html.contains("ORDER-TEST-001"));
        assertTrue(html.contains("mailto:support@example.com"));
    }

    @Test
    void failureTemplateUsesDynamicBrandAndOrderVariables() {
        Context context = baseContext();
        context.setVariable("money", "8.88");
        context.setVariable("nickName", "测试用户");
        context.setVariable("payType", "支付宝支付");
        context.setVariable("time", "2026-10-25 02:31:00");
        context.setVariable("info", "未查询到匹配备注");

        String html = templateEngine.process("order-fail", context);

        assertRendered(html, "测试品牌");
        assertTrue(html.contains("8.88"));
        assertTrue(html.contains("测试用户"));
        assertTrue(html.contains("未查询到匹配备注"));
    }

    @Test
    void reviewTemplateUsesSingleReviewUrl() {
        Context context = baseContext();
        context.setVariable("money", "12.34");
        context.setVariable("payType", "微信支付");
        context.setVariable("nickName", "审核用户");
        context.setVariable("payNum", "2468");
        context.setVariable("email", "user@test.cn");
        context.setVariable("time", "2026-10-25 02:32:00");
        context.setVariable("description", "审核订单");
        context.setVariable("device", "Mozilla/5.0 Test Browser");
        context.setVariable("reviewUrl", "https://pay.example.com/review?id=1&t=abc123");

        String html = templateEngine.process("payment-review", context);

        assertRendered(html, "测试品牌");
        assertTrue(html.contains("https://pay.example.com/review?id=1"));
        assertTrue(html.contains("t=abc123"));
        assertFalse(html.contains("/order/pass?"));
        assertFalse(html.contains("/order/back?"));
        assertFalse(html.contains("myToken="));
        assertTrue(html.contains("Mozilla/5.0 Test Browser"));
    }

    private static Context baseContext() {
        Context context = new Context(Locale.CHINA);
        context.setVariable("brandName", "测试品牌");
        context.setVariable("brandInitial", "测");
        context.setVariable("site", "https://pay.example.com");
        context.setVariable("supportMail", "support@example.com");
        context.setVariable("year", 2026);
        return context;
    }

    private static void assertRendered(String html, String brandName) {
        assertTrue(html.contains(brandName));
        assertFalse(html.contains("星屿支付"), "email template contains a hard-coded brand name");
        assertFalse(html.contains("th:text"));
        assertFalse(html.contains("th:href"));
        assertFalse(html.contains("[[${"));
    }
}
