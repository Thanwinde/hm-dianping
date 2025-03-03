package com.hmdp.config;

import com.hmdp.interceptor.SessionInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

@Slf4j
@Configuration
public class MVCConfig extends WebMvcConfigurationSupport {
    @Autowired
    private SessionInterceptor sessionInterceptor;

    protected void addInterceptors(InterceptorRegistry registry) {
        log.info("拦截器启动");
        registry.addInterceptor( sessionInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns("/shop/**","/voucher/**","/shop-type/**","/upload/**","/blog/hot","/user/code","/user/login")
        ;
    }
}
