package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逻辑过期时间
 * 存入Redis的数据
 */
@Data
public class RedisData {
    //逻辑过期时间属性
    private LocalDateTime expireTime;
    //存数据的属性
    private Object data;
}
