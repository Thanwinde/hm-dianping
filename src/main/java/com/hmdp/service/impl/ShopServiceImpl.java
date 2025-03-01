package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
        Shop shop = shopMapper.selectById(id);
        Map map_ca = redisTemplate.opsForHash().entries("cache:shop:" + id);
        if (map_ca != null) {
            BeanUtil.fillBeanWithMap(map_ca,shop,false);
            log.info("Redis店铺缓存命中! {}",shop);
            return Result.ok(shop);
        }else{
            shop = shopMapper.selectById(id);
            if (shop == null) {
                return Result.fail("店铺不存在！");
            }
            Map map = BeanUtil.beanToMap(shop);
            redisTemplate.opsForHash().putAll("cache:shop:" + id, map);
            log.info("新增店铺缓存 {}",shop);

        }
        return Result.ok(shop);
    }
}
