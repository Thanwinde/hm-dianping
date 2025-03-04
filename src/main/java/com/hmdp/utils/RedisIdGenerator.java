package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdGenerator {

    @Autowired
    private RedisTemplate redisTemplate;

    private static final Long BEGIN_STAMP = 1641092645L;

    public long nextId(String prefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSeconds - BEGIN_STAMP;

        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = redisTemplate.opsForValue().increment("icr:" + prefix + ":" + date, timeStamp);
        return timeStamp << 32 | count;
    }
}
