package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RefreshTokenInterceptor;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {  //使用工具类中的方法校验
            //不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //符合，生成验证码
        String code = RandomUtil.randomNumbers(6);  //使用hutool生成6位随机数字验证码

        //保存验证码到redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code ,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES)  ;

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
        Object cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());  //从redis中获取验证码
        String code = loginForm.getCode();  //从前端获取验证码

        //判断session和前端发送的代码是否一致
        if (cacheCode == null || !cacheCode.equals(code)) {
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
        //保存用户信息到redis中
        //随机生成token，作为登陆令牌
        String token = UUID.randomUUID().toString(true);
        //将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((filldName, fieldValue) -> fieldValue.toString()));

        //存到redis中
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //设置token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);  //设置30min

        //还有一个功能，就是当用户不访问超过有效期时就失效，而不是只要到了30min就失效
        //所以为了知道用户有没有访问，我们就要在拦截器中设置这个功能

        //返回token
        return Result.ok(token);
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
