package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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
    private IVoucherOrderService proxy;

    private final ExecutorService secKill_order_handler = Executors.newSingleThreadExecutor();
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    private void init(){
        secKill_order_handler.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    List<MapRecord<String,Object,Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1","c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    if(list == null || list.isEmpty()){
                        continue;
                    }
                    MapRecord<String,Object,Object> mapRecord = list.get(0);
                    Map<Object,Object> val = mapRecord.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(val,new VoucherOrder(),false);
                    handleSecKill(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",mapRecord.getId());
                } catch (Exception e) {
                    log.error("订单异常:{}",e);
                    handlePendingList();
                }

            }
        }
    }
    private void handlePendingList(){
        while (true) {
            try {
                List<MapRecord<String,Object,Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1","c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );
                if(list == null || list.isEmpty()){
                    break;
                }
                MapRecord<String,Object,Object> mapRecord = list.get(0);
                Map<Object,Object> val = mapRecord.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(val,new VoucherOrder(),false);
                handleSecKill(voucherOrder);
                stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",mapRecord.getId());
            } catch (Exception e) {
                log.error("订单异常:{}",e);
                throw new RuntimeException(e);
            }
        }
    }
    @Override
    public Result setSeckillVoucher(Long voucherId) {
        SeckillVoucher voucher = cacheUtil.queryWithMutex("secKill:info:" + voucherId,voucherId,SeckillVoucher.class,seckillVoucherService::getById);
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("活动尚未开始!");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("活动已经结束！");
        }
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdGenerator.nextId("order");
        Long sign = redisTemplate.execute(
                script,
                Collections.emptyList(),
                voucherId.toString(),userId.toString(),orderId.toString());

        if(sign != 0){
            return Result.fail(sign == 1 ? "已抢光！" : "已经购买过！");
        }

        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }
    public void handleSecKill(VoucherOrder voucherOrder){
        RLock lock = redissonClient.getLock("lock:order:" + voucherOrder.getUserId());
        boolean locked = lock.tryLock();
        if(!locked){
            return;
        }
        proxy.submitOrder(voucherOrder);
        lock.unlock();
    }
    @Transactional
    public void submitOrder(VoucherOrder voucherOrder){
        save(voucherOrder);
    }
}
