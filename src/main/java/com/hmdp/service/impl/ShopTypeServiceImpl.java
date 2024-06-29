package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopType() {

        //从redis查询店铺信息
        //索引从0开始。负数表示从列表的末尾开始计数。如-1表示最后一个元素
        List<String> shopType = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        //判断缓存是否命中
        if(StrUtil.isNotBlank(JSONUtil.toJsonStr(shopType))) {
            //命中直接返回店铺信息
            return Result.ok(shopType);
        }
        //未命中，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //判断数据库中有无店铺信息
        if(typeList == null) {
            //无，返回失败信息
            return Result.fail("分类不存在");
        }
        //有，将店铺信息写进redis缓存中
        stringRedisTemplate.opsForList().rightPush(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList));
        //返回店铺信息
        return Result.ok(typeList);
    }
}
