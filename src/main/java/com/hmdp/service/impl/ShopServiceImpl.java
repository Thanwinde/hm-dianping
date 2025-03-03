package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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

    @Autowired
    private CacheUtil cacheUtil;

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheUtil.queryWithMutex("cache:shop:",id,Shop.class,this::getById);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
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


}
