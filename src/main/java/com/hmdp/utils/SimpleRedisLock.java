package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SimpleRedisLock {
    @Autowired
    private RedisTemplate redisTemplate;

    public boolean tryLock(String name, Long expireTime) {
        long threadId = Thread.currentThread().getId();
        String key = "lock:" + name;
        Boolean b = redisTemplate.opsForValue().setIfAbsent(key, threadId, expireTime, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(b);
    }

    public void unlock(String name) {
        String key = "lock:" + name;
        redisTemplate.delete(key);
    }

}
