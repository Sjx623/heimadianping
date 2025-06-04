package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录拦截器
 */

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从request中获取session
        HttpSession session = request.getSession();
        //从session中获取用户
        Object user = session.getAttribute("user");
        //判断用户是否存在
        if (user == null) {
            //不存在就返回错误（拦截）E
            response.setStatus(401);  //设置状态码401代表未授权
            return false;
        }
        //存在，保存用户信息到threadLocal
        UserHolder.saveUser((UserDTO) user);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
