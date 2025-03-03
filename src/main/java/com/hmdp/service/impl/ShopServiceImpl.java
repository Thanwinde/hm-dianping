package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
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
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id){
        Shop shop = new Shop();
        Map map_ca = redisTemplate.opsForHash().entries("cache:shop:" + id);

        if (map_ca.get("id") != null) {
            if(map_ca.get("id").equals('0')){
                log.info("空数据缓存命中");
                return null;
            }
            BeanUtil.fillBeanWithMap(map_ca,shop,false);
            log.info("Redis店铺缓存命中! {}",shop);
            return shop;
        }else{

            try {

                log.info("成功获取锁！");

                if(tryLock(id)){
                    Thread.sleep(50);
                    return queryWithMutex(id);
                }

                shop = shopMapper.selectById(id);
                Map map = new HashMap();
                if (shop == null) {
                    map.put("id",0);
                    redisTemplate.opsForHash().putAll("cache:shop:" + id,map);
                    log.info("缓存空数据防止穿透");
                    //防止缓存穿透
                    return null;
                }
                map = BeanUtil.beanToMap(shop);
                redisTemplate.opsForHash().putAll("cache:shop:" + id, map);
                redisTemplate.expire("cache:shop:" + id, 30L, TimeUnit.MINUTES);
                log.info("新增店铺缓存 {}",shop);
            } catch (InterruptedException e) {

                throw new RuntimeException(e);

            } finally {
                log.info("成功解锁！");
                unlock(id);
            }



        }
        return shop;
    }

    @Override
    public Result updateShop(Shop shop) {
        //先数据库，再缓存
        shopMapper.updateById(shop);
        redisTemplate.delete("cache:shop:" + shop.getId());
        shop = shopMapper.selectById(shop.getId());
        Map map = BeanUtil.beanToMap(shop);
        redisTemplate.opsForHash().putAll("cache:shop:" + shop.getId(), map);
        redisTemplate.expire("cache:shop:" + shop.getId(), 30L, TimeUnit.MINUTES);
        log.info("新增店铺缓存 {}",shop);
        return Result.ok();
    }

    public boolean tryLock(Long id){
        boolean flag = redisTemplate.opsForValue().setIfAbsent("lock:shop:" + id, 1, 5L, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }
    public void unlock(Long id){
        redisTemplate.delete("lock:shop:" + id);
    }
}
