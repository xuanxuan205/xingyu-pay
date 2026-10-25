package com.wendao.common.utils;

import com.wendao.config.PayProConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailUtilsTest {

    @Test
    void senderDisplayNameSubjectAndBrandVariablesAreDynamic() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        PayProConfig config = new PayProConfig();
        config.setName("Your Name");
        config.setEmailBrandName("星屿支付");
        config.setTitle("星屿支付");
        config.setSite("https://pay.example.com");
        config.setSupportMail("support@example.cn");

        EmailUtils emailUtils = new EmailUtils();
        ReflectionTestUtils.setField(emailUtils, "mailSender", mailSender);
        ReflectionTestUtils.setField(emailUtils, "templateEngine", templateEngine);
        ReflectionTestUtils.setField(emailUtils, "payProConfig", config);

        emailUtils.sendTemplateMail(
                "noreply@example.cn",
                "user@example.cn",
                "【星屿支付】支付成功通知",
                "order-success",
                new Object());

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sent = messageCaptor.getValue();

        assertEquals("星屿支付", ((InternetAddress) sent.getFrom()[0]).getPersonal());
        assertEquals("【星屿支付】支付成功通知", sent.getSubject());
    }
}
