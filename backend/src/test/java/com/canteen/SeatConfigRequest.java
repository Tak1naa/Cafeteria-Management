package com.canteen;

import lombok.Data;
import jakarta.validation.constraints.Min;

@Data
public class SeatConfigRequest {
    // 使用包装类型
    @Min(1)
    private Integer windowCount;

    @Min(1)
    private Integer chairCount;

    @Min(0)
    private Double distanceWeight;

    @Min(0)
    private Double queueRandomWeight;

    @Min(0)
    private Double seatRandomWeight;
}