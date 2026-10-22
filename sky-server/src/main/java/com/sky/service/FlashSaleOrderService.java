package com.sky.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sky.dto.FlashSaleOrderMessage;
import com.sky.entity.FlashSaleOrder;

public interface FlashSaleOrderService extends IService<FlashSaleOrder> {

    void createOrder(FlashSaleOrderMessage message);
}
