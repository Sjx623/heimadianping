package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = "cache:shopType:list";
        //1. 从redis查询商铺分类缓存
        String shopTypesJSON = stringRedisTemplate.opsForValue().get(key);  // 返回多个JSON格式的商铺分类
        //2. 判断是否存在
        //将json转成对象
        if (shopTypesJSON != null && !shopTypesJSON.isEmpty()) {
            List<ShopType> shopTypes = JSONUtil.toList(shopTypesJSON, ShopType.class);
            return Result.ok(shopTypes);
        }
        //4. 不存在，根据id查询数据库
        List<ShopType> shopTypes = this.list();
        if (shopTypes.isEmpty()) {
            //5. 数据库也不存在，返回错误
            return Result.fail("店铺类型不存在");
        }
        //6. 存在，写入redis，再返回 (记得要将对象转成JSON格式)
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypes));
        //7.返回
        return Result.ok(shopTypes);
    }
}
