package com.wendao.config;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.spring5.view.ThymeleafViewResolver;

import javax.annotation.Resource;
import java.util.Calendar;

@Component
public class Config implements WebMvcConfigurer {

    @Resource
    PayProConfig payConfig;

    /**
     * @description 一些全局变量，放在这里
     * @return void
    */
    @Resource
    private void configureThymeleafStaticVars(ThymeleafViewResolver viewResolver) {
        if(viewResolver != null) {
            viewResolver.addStaticVariable("title",payConfig.getTitle());
            viewResolver.addStaticVariable("indexTitle",payConfig.getIndexTitle());
            viewResolver.addStaticVariable("site",payConfig.getSite());
            viewResolver.addStaticVariable("alipayUserId",payConfig.getAlipayUserId());
            viewResolver.addStaticVariable("alipayCustomQrUrl",payConfig.getAlipayCustomQrUrl());
            viewResolver.addStaticVariable("mobile",payConfig.getMobile());
            viewResolver.addStaticVariable("name",payConfig.getName());
            String brandName = payConfig.getEmailBrandName();
            if (brandName == null || brandName.trim().isEmpty()) {
                brandName = payConfig.getName();
            }
            brandName = brandName == null || brandName.trim().isEmpty()
                    ? "星屿支付" : brandName.trim();
            viewResolver.addStaticVariable("brandName", brandName);
            viewResolver.addStaticVariable("brandInitial", brandName.substring(0, 1));
            viewResolver.addStaticVariable("year", Calendar.getInstance().get(Calendar.YEAR));
            viewResolver.addStaticVariable("supportMail",payConfig.getSupportMail());
            viewResolver.addStaticVariable("qrCodeNum",payConfig.getQrCodeNum());
        }
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public Snowflake snowflake(){
        return new Snowflake(0,0);
    }

}
