package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SessionInterceptor implements HandlerInterceptor {

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        log.info("进行拦截处理 {}", token);
        if(token.isEmpty()) {
            return false;
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries("login:token:" + token);
        redisTemplate.expire("login:token" + token,30L, TimeUnit.MINUTES);
        UserDTO userDTO = new UserDTO();
        BeanUtil.fillBeanWithMap(entries,userDTO,false);

        if (entries == null) {
            return false;
        }
        log.info("保存线程用户: {}", entries.toString());
        UserHolder.saveUser(userDTO);
        return true;
    }

}
