package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
    //Result是返回值，seckillVoucher是方法名，(Long voucherId)是参数
    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);
}
