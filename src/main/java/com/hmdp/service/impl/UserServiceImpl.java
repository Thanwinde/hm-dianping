package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号码不正确！");
        }
        String code = RandomUtil.randomNumbers(6);
        //session.setAttribute("code",code);
        redisTemplate.opsForValue().set("login:code:" + phone, code,2, TimeUnit.MINUTES);
        log.info("发送验证码 {}",code);
        return Result.ok();
    }

    @Override
    public Result Login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号码不正确！");
        }
        String code = loginForm.getCode();
        String aim = redisTemplate.opsForValue().get("login:code:" + phone).toString();
        if(code.isEmpty()||!code.equals(aim)){
            return Result.fail("验证码不正确！");
        }
        User user = query().eq("phone", phone).one();
        if(user==null){
            user = createNewUserWithPhone(phone);
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        String token = UUID.randomUUID().toString();
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO);
        //session.setAttribute("user",userDTO);
        redisTemplate.opsForHash().putAll("login:token:" + token, stringObjectMap);
        redisTemplate.expire("login:token:" + token, 30L, TimeUnit.MINUTES);
        log.info("用户登录 {}",userDTO);
        return Result.ok(token);
    }

    private User createNewUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        log.info("创建用户 {}",user);
        save(user);
        return user;
    }
}
