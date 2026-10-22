package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.FlashSaleActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FlashSaleActivityMapper extends BaseMapper<FlashSaleActivity> {

    @Update("update flash_sale_activity set stock = stock - 1, update_time = now() where id = #{id} and stock > 0")
    int deductStock(Long id);
}
