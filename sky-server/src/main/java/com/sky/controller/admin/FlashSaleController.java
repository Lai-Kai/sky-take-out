package com.sky.controller.admin;

import com.sky.dto.FlashSaleCreateDTO;
import com.sky.result.Result;
import com.sky.service.FlashSaleActivityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("adminFlashSaleController")
@RequestMapping("/admin/flash-sale")
@Api(tags = "限量套餐抢购管理接口")
public class FlashSaleController {

    @Autowired
    private FlashSaleActivityService flashSaleActivityService;

    @PostMapping
    @ApiOperation("创建限量套餐抢购活动")
    public Result<Long> create(@RequestBody FlashSaleCreateDTO dto) {
        Long activityId = flashSaleActivityService.createActivity(dto);
        return Result.success(activityId);
    }

    @PostMapping("/{id}/warm-up")
    @ApiOperation("预热抢购库存到Redis")
    public Result warmUp(@PathVariable Long id) {
        flashSaleActivityService.warmUpStock(id);
        return Result.success();
    }
}
