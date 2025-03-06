# TWind 的黑马点评随笔

​	目前是把黑马点评的技术部分完全做完了，不能说吃得饱饱，也算个半饱吧。

​	黑马点评严格来说不算项目，因为它给的前端过于垃圾，内容又重在Redis，所以称之为Redis练习貌似跟贴切。

​	尽管如此，这个项目仍然非常适合新手入门，亮点尤其在带你一步步解决抢优惠卷带来的一系列线程问题展开，从表层到深入，非常不错。

​	但是，对于异步编程，集群，分布式等内容建议还是移步spring cloud，这个是专业的，黑马点评更多注重于Redis的运用(后面的关于签到，位置，推文这些建议还是别看了，这已经脱离了初衷)

​	后面应该会写一些关于Redis消息队列的文章

----

## 坑合集

	### 坑1

首当其冲的是黑马的构思前端，登录时无法选中阅读协议！！！

解决办法是到nginx里面的前端代码手动修改，把**login.html**里面的

> ```java
> login(){
> if( ! this.radio){
>  this.$message.error("请先确认阅读用户协议！");
>  return
> }
> ```

惊叹号去掉！

### 坑2

紧随其后的是redis序列化器

你大可直接用黑马视频用的自带的stringRedisTemplate，但是你这样做的话就得在使用Map互转Bean时尽享那一串的构思修复代码

这里建议直接一步到位，用**Jackson2JsonRedisSerializer**作为所有的序列化反序列化器

>```java
>@Bean
>public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory){
>log.info("开始创建redis模板对象...");
>
>ObjectMapper objectMapper = new ObjectMapper();
>objectMapper.registerModule(new JavaTimeModule());  // 注册 JavaTimeModule 处理 LocalDateTime
>objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);  // 禁用时间戳格式
>
>Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer =
>       new Jackson2JsonRedisSerializer<>(Object.class);
>jackson2JsonRedisSerializer.setObjectMapper(objectMapper);
>
>RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
>redisTemplate.setConnectionFactory(redisConnectionFactory);
>redisTemplate.setKeySerializer(new StringRedisSerializer());
>redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);
>redisTemplate.setHashValueSerializer(jackson2JsonRedisSerializer);
>return redisTemplate;
>}
>```

**redisTemplate.setKeySerializer(new StringRedisSerializer());**这里不用jackson的原因是用了key会乱码（虽然不影响就是了）

注意！要用的话要用一套（dubug了好久），redis默认的是string类的序列化器！远古java版本的LocalDateTime有兼容性问题！

### 坑3

你可能在传参数到Lua脚本时发现Lua接受到的始终为**nil**，这是因为我们用的是:

>```java
>@Autowired
>private RedisTemplat redisTemplate;
>```

序列化出了问题:**Lua脚本不知道传进去的数据是什么类型！**（Redis是知道的但它没给Lua说），得手动指定:

>```java
>@Autowired
>private RedisTemplate<String,String> redisTemplate;
>```

这样就完美解决

### 坑4

严格来说不算坑，是自己学艺不精......

同时看到了一篇错误的CSDN博客......

就是

> ```java
> stringRedisTemplate.opsForStream().read
> ```

**它对应的是消费者组操作中的XREADGROUP ，是既可以访问消息队列也可以访问pending-list队列的！**

**取决于其ID**

~~所以stringRedisTemplate.opsForStream().pending的意义在哪呢~~

----

 ## 总结

完结撒花 :tada: :tada: :tada:

接下来就是写几篇博客巩固一下......
