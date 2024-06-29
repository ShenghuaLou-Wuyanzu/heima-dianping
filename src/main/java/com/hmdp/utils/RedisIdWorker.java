package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * id生成器工具类
 */

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 开始时间戳
     * 定义为常量
     */
    private static final long BEGIN_TIMESTAMP = 1640995200;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    /**
     * 生成分布式ID
     * @param keyPrefix
     * @return
     */
    public long nextId(String keyPrefix){
        //获取当前时间的秒数
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        //计算时间戳
        long timestamp = nowSecond - BEGIN_TIMESTAMP;   //这里的时间戳是32位的

        // 生成序列号
        // 以当天的时间戳为key，防止一直自增下去导致超时，这样每天的极限都是 2^{31}

        //设置获取到的当天时间的格式
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //redis实现自增长得到序列号
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //时间戳左移，按位或运算序列号，拼接并返回，得到全局唯一ID
        return timestamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        //给localDataTime赋值，作为开始时间
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        //计算开始时间的秒数
        long second = time.toEpochSecond(ZoneOffset.UTC);
        //输出到控制台，记录到BEGIN_TIMESTAMP开始时间戳
        System.out.println("second = " + second);
    }
}
