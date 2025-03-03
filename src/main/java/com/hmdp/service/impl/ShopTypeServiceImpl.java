package com.hmdp.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
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
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private IShopTypeService typeService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        String aim = (String) redisTemplate.opsForValue().get("cache:shopType");
        List<ShopType> typeList = new ArrayList<>();
        if(aim == null){
            typeList = typeService
                    .query().orderByAsc("sort").list();
            aim = JSONUtil.toJsonStr(typeList);
            redisTemplate.opsForValue().set("cache:shopType", aim);
            log.info("新增缓存！ {}",typeList);
        }else{
            typeList = JSONUtil.toList(aim, ShopType.class);
            log.info("缓存命中！ {}",typeList);
        }
        return Result.ok(typeList);
    }
}
