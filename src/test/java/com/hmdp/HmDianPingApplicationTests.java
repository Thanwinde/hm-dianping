package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisTemplate redisTemplate;

    private static final Long BEGIN_STAMP = 1641092645L;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    public void nextId() {
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSeconds - BEGIN_STAMP;

        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = redisTemplate.opsForValue().increment("icr:" + "prefix" + ":" + date, timeStamp);
        System.out.println(timeStamp << 32 | count);
    }
    @Test
    public void test() {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable runnable = () -> {
            for (int i = 0; i < 300; i++) {
                nextId();
            }
            latch.countDown();
        };
        for(int i = 0; i < 300; i++){
            es.submit(runnable);
        }
    }
}
