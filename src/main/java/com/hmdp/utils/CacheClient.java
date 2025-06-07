package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData( value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds( time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough (
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {

        String key = keyPrefix + id;
        //1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2. 判断是否存在
        //isNotBlank()只能判断出有字符串的情况下，而""、null、"\t"都判断不出来
        if (StrUtil.isNotBlank(json)) {
            //3. 存在直接返回(不用查询数据库了)
            return JSONUtil.toBean(json, type);
        }
        // 所以要加一层，判断是否是空值
        if (json != null) {
            return null;
        }

        //4. 不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            //5. 数据库也不存在
            // 为了防止缓存穿透问题，将空值写入redis，设置TTL为2分钟 (为了防止空值过多消耗内存)
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误
            return null;
        }
        //6. 存在，写入redis，再返回 (记得要将对象转成JSON格式)
        this.set(key, r, time, timeUnit);
        //7.返回
        return r;
    }


    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //用逻辑过期解决缓存击穿问题
    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {

        String key = keyPrefix + id;
        //1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2. 判断是否存在
        if (StrUtil.isBlank(json)) {
            //3. 不存在直接返回null
            // 假定存在，但是依旧使用旧数据或空值
            return null;
        }

        //命中
        //需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回店铺信息
            return r;
        }
        //已过期，需要缓存重建
        //3.缓存重建: 1.获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //3.2 判断是否获取锁成功
        if (isLock) {
            //3.3 获取锁成功，开启独立线程，实现缓存重建
            //判断是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                //未过期，直接返回店铺信息
                return r;
            }
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //缓存重建
                    //查询数据库
                    R apply = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key, apply, time,  timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //3.4 获取锁失败，返回旧的商铺信息
        return r;
    }

    // 尝试获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}















