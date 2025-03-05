package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtil;
import com.hmdp.utils.RedisIdGenerator;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdGenerator redisIdGenerator;

    @Autowired
    private SimpleRedisLock simpleRedisLock;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Autowired
    private CacheUtil cacheUtil;

    static final DefaultRedisScript<Long> script;
    static {
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("secKill.lua"));
        script.setResultType(Long.class);
    }

    @Override
    public Result setSeckillVoucher(Long voucherId) {
        //TODO 添加基于redis的优惠劵缓存
                Long userId = UserHolder.getUser().getId();
                Long sign = redisTemplate.execute(
                script,
                Collections.emptyList(),
                voucherId.toString(),userId.toString());

        if(sign != 0){
            return Result.fail(sign == 1 ? "已抢光！" : "已经购买过！");
        }
        return Result.ok();
    }

    /*@Override
    public Result setSeckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("活动尚未开始!");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("活动已经结束！");
        }
        if(voucher.getStock() < 1){
            return Result.fail("已抢光！");
        }

        Long userId = UserHolder.getUser().getId();

        String key = "VoucherOrder:" +userId;
        //boolean b = simpleRedisLock.tryLock(key, 10L);
        RLock lock = redissonClient.getLock(key);
        boolean b = lock.tryLock();
        if(!b){
            log.info("redis分部锁拦截！");
            return Result.fail("已经购买过！");
        }
        log.info("redis分部锁获得！");
        try {

            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.getResult(voucherId);

        } catch (IllegalStateException e) {

            throw new RuntimeException(e);

        } finally {
            log.info("redis分部锁解锁！");
            //simpleRedisLock.unlock(key);
            lock.unlock();
        }
    }*/

    @Transactional
    public Result getResult(Long voucherId) {

        long userId = UserHolder.getUser().getId();
        int cnt = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(cnt > 0){
            return Result.fail("已经购买过!");
        }

        boolean success = seckillVoucherService
                .update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if(!success){
            return Result.fail("已抢光！");
        }
        long orderId = redisIdGenerator.nextId("order");

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
