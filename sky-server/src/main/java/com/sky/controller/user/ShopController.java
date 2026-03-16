package com.sky.controller.user;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Api("商户信息管理接口")
@Slf4j
public class ShopController {
    public static final String SHOP_STATUS_KEY = "shop_status";

    @Autowired
    private RedisTemplate redisTemplate;

    @ApiOperation("获得商户状态")
    @GetMapping("/status")
    public Result<Integer> getStatus(){
        log.info("获得商铺状态");
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        return Result.success(status);
    }
}
