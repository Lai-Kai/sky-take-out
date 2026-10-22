package com.sky.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FlashSaleCreateDTO implements Serializable {

    private Long setmealId;

    private String title;

    private BigDecimal price;

    private Integer stock;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;

    private Integer status;
}
