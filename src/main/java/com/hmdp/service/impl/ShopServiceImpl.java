package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(
//                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //用逻辑过期解决缓存击穿问题
        Shop shop = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //返回
        return Result.ok(shop);
    }

    //用逻辑过期解决缓存击穿问题
//    public Shop queryWithLogicalExpire(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        //1. 从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2. 判断是否存在
//        if (StrUtil.isBlank(shopJson)) {
//            //3. 不存在直接返回null
//            // 假定存在，但是依旧使用旧数据或空值
//            return null;
//        }
//
//        //命中
//        //需要先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//
//        //判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            //未过期，直接返回店铺信息
//            return shop;
//        }
//        //已过期，需要缓存重建
//        //3.缓存重建: 1.获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        //3.2 判断是否获取锁成功
//         if (isLock) {
//             //3.3 获取锁成功，开启独立线程，实现缓存重建
//             //判断是否过期
//             if (expireTime.isAfter(LocalDateTime.now())) {
//                 //未过期，直接返回店铺信息
//                 return shop;
//             }
//             CACHE_REBUILD_EXECUTOR.submit(() -> {
//                 try {
//                     //缓存重建
//                     this.saveShop2Redis(id, 20L);
//                 } catch (Exception e) {
//                     throw new RuntimeException(e);
//                 }finally {
//                     //释放锁
//                     unLock(lockKey);
//                 }
//             });
//         }
//        //3.4 获取锁失败，返回旧的商铺信息
//        return shop;
//    }

    //用互斥锁解决缓存击穿
//    private Shop queryWithMutex(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        //1. 从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2. 判断是否存在
//        //isNotBlank()只能判断出有字符串的情况下，而""、null、"\t"都判断不出来
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3. 存在直接返回(不用查询数据库了)
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 所以要加一层，判断是否是空值
//        if (shopJson != null) {
//            return null;
//        }
//
//        //4.开始实现缓存重建
//        //4.1. 获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            //4.2. 判断是否获取成功
//            if (!isLock) {
//                //4.3. 失败，则休眠并重试
//                Thread.sleep(50);
//                queryWithMutex(id);
//            }
//            //4.4. 成功，根据id查询数据库
//            shop = getById(id);
//            //模拟重建延时
//            Thread.sleep(200);
//
//            if (shop == null) {
//                //5. 数据库也不存在
//                // 为了防止缓存穿透问题，将空值写入redis，设置TTL为2分钟 (为了防止空值过多消耗内存)
//                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                // 返回错误
//                return null;
//            }
//            //6. 存在，写入redis，再返回 (记得要将对象转成JSON格式)
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //7. 释放锁
//            unLock(lockKey);
//        }
//
//        //8.返回
//        return shop;
//    }

    //原来做的解决缓存穿透的查询代码
//    public Shop queryWithPassThrough(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        //1. 从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2. 判断是否存在
//        //isNotBlank()只能判断出有字符串的情况下，而""、null、"\t"都判断不出来
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3. 存在直接返回(不用查询数据库了)
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 所以要加一层，判断是否是空值
//        if (shopJson != null) {
//            return null;
//        }
//
//        //4. 不存在，根据id查询数据库
//        Shop shop = getById(id);
//        if (shop == null) {
//            //5. 数据库也不存在
//            // 为了防止缓存穿透问题，将空值写入redis，设置TTL为2分钟 (为了防止空值过多消耗内存)
//            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            // 返回错误
//            return null;
//        }
//        //6. 存在，写入redis，再返回 (记得要将对象转成JSON格式)
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //7.返回
//        return shop;
//    }

    // 尝试获取锁
//    private boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    // 释放锁
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }
//
//    //  逻辑过期解决缓存击穿 封装店铺信息和逻辑过期时间到RedisData
//    public void saveShop2Redis(Long id,  Long expireSeconds) {
//        //查新店铺数据
//        Shop shop = getById(id);
//        //封装逻辑过期
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //写入redis
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    @Override
    @Transactional  // 开启事务
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {   //对id进行判断
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
