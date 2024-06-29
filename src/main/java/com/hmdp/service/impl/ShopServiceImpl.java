package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource private StringRedisTemplate stringRedisTemplate;
    /**
     * 根据id查询商铺的信息
     */
    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 逻辑过期解决缓存击穿
     */
    //这里需要声明一个线程池，因为下面我们需要新建一个现成来完成重构缓存
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id) {
        //设置id的key常量
        String key = CACHE_SHOP_KEY + id;
        //根据key查询店铺缓存信息
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if (StrUtil.isBlank(json)) {
            //缓存未命中,返回空
            return null;
        }
        //Json反序列化为对象,这个对象包含shop和过期时间
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //把data转化为Shop对象
        JSONObject shopJson = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        //获取过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if(LocalDateTime.now().isAfter(expireTime)){
            //未过期
            return shop;
        }
        //缓存过期，尝试获取锁
        boolean flag = getLock(LOCK_SHOP_KEY + id);
        //判断是否获取锁
        if(!flag) {
            //没有获取到锁,返回商铺信息
            return shop;
        }
        //获取到锁，开启独立线程
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                this.saveShop2Redis(id, LOCK_SHOP_TTL);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //释放锁
                unLock(LOCK_SHOP_KEY + id);
            }

        });
        //返回商铺信息
        return shop;
    }


    /**
     * 互斥锁解决缓存击穿
     */
    public Shop queryWithMutex(Long id) { //根据参数id查询，返回值是shop(商铺信息)
        String key = CACHE_SHOP_KEY + id;
        //从redis中查询商铺的缓存,导入商铺缓存的key常量
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中，即判断shopJson是否不是空
        if (StrUtil.isNotBlank(shopJson)) {
            // shopJson不为空，缓存命中，直接返回店铺数据。把Json字符串转为Java对象返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //命中的不是空值
        if (shopJson != null) {
            return null;
        }
        //命中的是空值
        Shop shop = null;
        try {
            //获得互斥锁
            boolean isLock = getLock(LOCK_SHOP_KEY);

            //未获得锁，休眠，再重试
            if(!isLock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //缓存未命中，获取锁成功，查询数据库
            shop = getById(id);
            //模拟重建的延时
            Thread.sleep(200);
            //查询到是空值，避免缓存穿透，将空值(空字符串)写入redis,并且设置空值缓存的时间，返回失败信息
            if(shop == null){
                stringRedisTemplate.opsForValue().set(key,"", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            //查询到商铺，写入Redis缓存。存JSON字符串，查的是Java对象，shop转为Json字符串存入，并设置过期时间
            String jsonStr = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);


        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(key);
        }
        return shop;
    }

    /**
     *获取锁的方法
     */
    private boolean getLock(String key) {
        //setIfAbsent()方法模拟互斥锁，只有第一个请求能拿到该锁,flag用于标记是否拿到锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //使用工具类，避免拆箱
        return BooleanUtil.isTrue(flag);
    }

    /**
     *释放锁的方法
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result upDate(Shop shop) {
        //判断id是否为空，空，直接返回错误信息
        if(shop == null) {
            return Result.fail("商铺id不能为空");
        }
        //修改数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    public void saveShop2Redis(Long id,Long expireSeconds) {
        //根据id,查询商铺
        Shop shop = getById(id);
        //新建RedisData类，用于封装商铺信息和逻辑过期时间
        RedisData redisData = new RedisData();
        //设置商铺信息
        redisData.setData(shop);
        //设置商铺的逻辑过期时间,在当前时间上加
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //把封装的信息转为JSON字符串，存入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

}



