package com.sky.controller.user;

import com.sky.dto.FlashSaleSubmitDTO;
import com.sky.result.Result;
import com.sky.service.FlashSaleActivityService;
import com.sky.vo.FlashSaleSubmitVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("userFlashSaleController")
@RequestMapping("/user/flash-sale")
@Api(tags = "用户端限量套餐抢购接口")
public class FlashSaleController {

    @Autowired
    private FlashSaleActivityService flashSaleActivityService;

    @PostMapping("/submit")
    @ApiOperation("提交限量套餐抢购请求")
    public Result<FlashSaleSubmitVO> submit(@RequestBody FlashSaleSubmitDTO dto) {
        FlashSaleSubmitVO vo = flashSaleActivityService.submit(dto);
        return Result.success(vo);
    }
}
