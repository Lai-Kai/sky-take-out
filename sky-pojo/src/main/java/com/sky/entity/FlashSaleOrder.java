package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("flash_sale_order")
public class FlashSaleOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final Integer PROCESSING = 0;
    public static final Integer CREATED = 1;
    public static final Integer FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long setmealId;

    private Long userId;

    private Long orderId;

    private String orderNumber;

    private Integer status;

    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
