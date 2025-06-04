package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    // 添加拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",  // 验证码接口
                        "/user/login",  //登录接口
                        "/blog/hot",    //热门博客接口
                        "/shop/**", // 店铺接口
                        "/shop-type/**", // 店铺类型接口
                        "/upload/**",   // 文件上传接口
                        "/voucher/**" ,  // 优惠券接口
                        "/user/info"   //
                );
    }
}
