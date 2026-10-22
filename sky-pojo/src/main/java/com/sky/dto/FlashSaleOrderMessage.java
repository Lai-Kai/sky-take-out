package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleOrderMessage implements Serializable {

    private Long activityId;

    private Long userId;

    private Long addressBookId;

    private Long requestTimeMillis;
}
