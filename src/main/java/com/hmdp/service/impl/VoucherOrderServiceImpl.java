package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    //下面的查询查询秒杀优惠券信息，要使用秒杀优惠券的service
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    //注入，用于生成订单的id
    @Resource
    private RedisIdWorker redisIdWorker;


    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询秒杀优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始,获取秒杀开始时间和结束时间
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        //未开始，即秒杀时间在当前时间的后面，返回异常结果
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        //判断秒杀是否结束，失效时间在当前时间之前
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        //查询的库存值
        Integer stock = voucher.getStock();
        //开始，判断库存是否充足，stock是一个整型
        if(stock < 1) {
            //不充足，返回异常结果
            return Result.fail("库存不充足");
        }
        
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional //涉及1.秒杀优惠券的扣减(秒杀优惠券表)，2.优惠券订单的新增(优惠券订单表)，两表要设计事务进行回滚
    public  Result createVoucherOrder(Long voucherId) {
        /**
         * 一人一单业务
         * 库存充足时，判断优惠券id和用户id是否已经在数据库存在
         * 存在就不能再下单
         * 不存在才能下单
         */
        //获取用户id，使用用户登录拦截器获取
        Long userId = UserHolder.getUser().getId();

//        //查询订单数量
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //查询订单数量，添加了悲观锁，避免多个线程查询
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0) {
            //用户已经购买过了
            return Result.fail("用户已经购买过了");
        }

        //,库存充足，订单数量<=0，扣减库存
        /**
         *  MyBatis-Plus 更新数据库的操作
         *  success返回值代表更新是否成功
         */
        boolean success = seckillVoucherService.update()
                //更新数据库的sql语句
                .setSql("stock = stock - 1")
                //设置更新的条件。
                // 这里添加了乐观锁，即对数据库中stock的判断，是否与上面查询到的stock值一致，一致才能操作数据库
//                .eq("voucher_id", voucherId).eq("stock",stock).update();
                //改进乐观锁的问题，判断时库存大于0就操作数据库，解决线程失败的问题,gt是大于的意思
                .eq("voucher_id", voucherId).gt("stock" , 0).update();
        //success为false也是库存不足，返回异常结果
        if(!success) {
            return Result.fail("库存不足");
        }
        //创建订单,即向数据库的优惠券订单表新增数据
        VoucherOrder voucherOrder = new VoucherOrder();
        /**
         * 准备获取订单中的各种id
         */
        //填入信息，订单id，用户id，优惠券id
        //全局id生成器,订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        voucherOrder.setId(userId);
        //代金券id，参数传递过来的
        voucherOrder.setId(voucherId);

        //订单写入数据库
        save(voucherOrder);

        //返回订单id
        return Result.ok(orderId);

    }
}
