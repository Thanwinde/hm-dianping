package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
@Component
@Slf4j
public class CacheUtil {
    @Autowired
    private RedisTemplate redisTemplate;


    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback) {
        R r;
        String key = keyPrefix + id;
        String json = (String) redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {

            log.info("Redis缓存命中! {}", json);
            return JSONUtil.toBean(json, type);

        }
        if (json != null) {

            log.info("Redis空缓存命中!");
            return null;

        }
        try {

            log.info("成功获取锁！");

            if (tryLock(id)) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix,id,type,dbFallback);
            }

            Thread.sleep(100);//模拟延迟
            r = dbFallback.apply(id);

            if(r == null) {
                redisTemplate.opsForValue().set(key,"",1L, TimeUnit.MINUTES);
                log.info("未找到对象，添加空缓存!");
                return null;
            }

            json = JSONUtil.toJsonStr(r);
            redisTemplate.opsForValue().set(key, json, 30L, TimeUnit.MINUTES);
            log.info("新增缓存: {}", json);

        } catch (InterruptedException e) {

            throw new RuntimeException(e);

        } finally {
            log.info("成功解锁！");
            unlock(id);
        }
        return r;
    }


    public <ID,R> void update(String keyPrefix, ID id, R content, Class<R> type, Function<ID, R> dbFallback,Consumer<R> dbUpdate){
        dbUpdate.accept(content);
        R r = dbFallback.apply(id);
        String key = keyPrefix + id;
        redisTemplate.delete(key);
        String json = JSONUtil.toJsonStr(r);
        redisTemplate.opsForValue().set(key,json,30L, TimeUnit.MINUTES);
        log.info("新增缓存 {}",content);
    }
    private <ID>boolean tryLock(ID id) {
        String lockKey = "lock:shop:" + id;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", 10, TimeUnit.SECONDS);
        return success != null && success;  // 如果成功获取锁，返回 true
    }

    private <ID>void unlock(ID id) {
        String lockKey = "lock:shop:" + id;
        // 解锁时，确保只有持有锁的线程才能释放锁
        redisTemplate.delete(lockKey);
    }

}
