package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class SimpleRedisLock {
    @Autowired
    private RedisTemplate redisTemplate;

    static final String uuid = UUID.randomUUID().toString() + "-";

    public boolean tryLock(String name, Long expireTime) {
        long threadId = Thread.currentThread().getId();
        //threadId区分线程，uuid区分JVM
        String key = "lock:" + name ;
        Boolean b = redisTemplate.opsForValue().setIfAbsent(key, uuid + threadId, expireTime, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(b);
    }

    public void unlock(String name) {
        String key = "lock:" + name;
        long threadId = Thread.currentThread().getId();
        String id = (String) redisTemplate.opsForValue().get(key);
        if (id == uuid + threadId) {
            redisTemplate.delete(key);
        }

    }

}
