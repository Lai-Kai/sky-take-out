package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class FlashSaleSubmitDTO implements Serializable {

    private Long activityId;

    private Long addressBookId;
}
