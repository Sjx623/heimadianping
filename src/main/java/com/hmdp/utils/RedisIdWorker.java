package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于redis的全局唯一id生成器
 */

@Component
public class RedisIdWorker {

    //  开始时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    // 序列号位数
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate  stringRedisTemplate;

    // 生成id
    public long nextId(String keyPrefix) {
        // 生成时间戳
        LocalDateTime now = LocalDateTime.now();   // 获取当前时间
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);   // 获取当前时间的毫秒数
        long timestamp = nowSecond - BEGIN_TIMESTAMP;  // 计算时间戳

        // 生成序列号
        //获取当前日期（精确到天）
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 拼接并返回
        //  拼接方法：因为一共64位，先把时间戳左移32位(位运算)，再将序列号与64位的数字进行或运算，就可以将其拼接进去
        return timestamp << COUNT_BITS | count;
    }

}
