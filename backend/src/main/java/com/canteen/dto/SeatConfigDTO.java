package com.canteen.dto;

import lombok.Data;

@Data
public class SeatConfigDTO {
    private Integer windowCount;
    private Integer chairCount;
    private Double distanceWeight;
    private Double queueRandomWeight;
    private Double seatRandomWeight;
}