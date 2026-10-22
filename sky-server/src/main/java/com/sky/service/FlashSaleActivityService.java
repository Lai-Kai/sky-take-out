package com.sky.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sky.dto.FlashSaleCreateDTO;
import com.sky.dto.FlashSaleSubmitDTO;
import com.sky.entity.FlashSaleActivity;
import com.sky.vo.FlashSaleSubmitVO;

public interface FlashSaleActivityService extends IService<FlashSaleActivity> {

    Long createActivity(FlashSaleCreateDTO dto);

    void warmUpStock(Long activityId);

    FlashSaleSubmitVO submit(FlashSaleSubmitDTO dto);
}
