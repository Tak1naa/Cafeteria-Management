package com.canteen.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * C++ 请求 AI 决策的数据
 * 对应接口文档：POST /api/ai/decision
 */
@Data
public class AiDecisionRequest {

    @NotBlank(message = "schemaVersion 不能为空")
    private String schemaVersion;

    @NotNull(message = "simTime 不能为空")
    private Integer simTime;

    @NotNull(message = "queueLengths 不能为空")
    @Size(min = 1, message = "queueLengths 长度必须大于 0")
    private List<Integer> queueLengths;

    @NotNull(message = "windowCount 不能为空")
    @Positive(message = "windowCount 必须大于 0")
    private Integer windowCount;

    @NotNull(message = "availableSeats 不能为空")
    @Min(value = 0, message = "availableSeats 不能小于 0")
    private Integer availableSeats;

    @NotNull(message = "waitingForSeat 不能为空")
    @Min(value = 0, message = "waitingForSeat 不能小于 0")
    private Integer waitingForSeat;

    @NotNull(message = "newArrivals 不能为空")
    @Min(value = 0, message = "newArrivals 不能小于 0")
    private Integer newArrivals;
}