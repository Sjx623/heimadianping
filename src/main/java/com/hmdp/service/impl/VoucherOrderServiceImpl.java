
package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            //秒杀没开始
            return Result.fail("秒杀尚未开始!");
        }
        //判断秒杀是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            //秒杀已结束
            return Result.fail("秒杀已结束!");
        }
        //判断库存是否充足
        if(voucher.getStock() < 1){
            return Result.fail("库存不足!");
        }

        Long userId = UserHolder.getUser().getId(); //获取用户id
        //创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
//        boolean isLock = lock.tryLock(1200);
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
            //获取锁失败，返回错误或者重试
            return Result.fail("不允许重复下单!");
        }
        //获取锁成功，创建订单
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();  //获取代理对象
            //用this调用没有事务功能
            //用代理对象调用，而不是this
            //所以这个方法就会交给spring管理，就可以让这个方法的事务生效
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId(); //获取用户id

        //查询订单
        //查询同一用户id下同一优惠券id的订单数量
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //判断是否存在
        if (count > 0) {
            //用户已经购买过了
            return Result.fail("用户已经购买过一次!");
        }

        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)  //乐观锁
                .update();
        if(!success){
            //扣减库存失败
            return Result.fail("库存不足!");
        }

        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();

        long orderId = redisIdWorker.nextId("order"); //生成全局唯一订单id
        voucherOrder.setId(orderId);  //订单id
        voucherOrder.setUserId(userId);  //用户id
        voucherOrder.setVoucherId(voucherId);  //优惠券id
        save(voucherOrder);  //保存订单写入数据库

        //返回订单id
        return Result.ok(orderId);
    }
}
