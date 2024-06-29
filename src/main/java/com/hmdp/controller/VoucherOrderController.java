package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *优惠券下单
 * 这里是前端提交优惠券id
 */
@RestController
@RequestMapping("/voucher-order")   //处理对/voucher-order路径的POST请求
public class VoucherOrderController {
    //注入优惠券下单的功能模块
    @Resource
    IVoucherOrderService voucherOrderService;
    //标记seckillVoucher方法，处理对/voucher-order/seckill/{id}路径的POST请求
    @PostMapping("seckill/{id}")
    //@PathVariable("id") 用于将路径变量{id}绑定到方法的参数voucherId上
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        //调用voucherOrderService.seckillVoucher的方法,给这个方法传入voucherId参数
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
