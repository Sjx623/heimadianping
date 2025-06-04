package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 添加拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //真正进行拦截的拦截器
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
                ).order(2);

        //  刷新账号登陆过期的拦截器，捕捉所有请求，但是不予拦截
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);
    }
}
