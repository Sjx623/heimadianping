package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {  //使用工具类中的方法校验
            //不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //符合，生成验证码
        String code = RandomUtil.randomNumbers(6);  //使用hutool生成6位随机数字验证码
        //保存验证码到session
        session.setAttribute("code", code);
        //发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {  //使用工具类中的方法校验
            //不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //校验验证码
        Object cacheCode = session.getAttribute("code");  //从session中获取验证码
        String code = loginForm.getCode();  //从前端获取验证码
        //判断session和前端发送的代码是否一致
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            //不一致就报错
            return Result.fail("验证码错误");
        }
        //一致，根据手机号查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        //判断用户是否存在
        if (user == null) {
            //不存在就创建新用户，存入到数据库
            user = createUserWithPhone(loginForm.getPhone());
        }
        //保存用户信息到session
        session.setAttribute("user", user);

        return Result.ok(); //不需要返回登陆凭证，因为使用session会将信息自动写入到cookie中，下次登陆就直接带着信息登录
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));  //生成随机昵称
        //保存用户
        save(user); //mybatisPlus
        return user;
    }
}
