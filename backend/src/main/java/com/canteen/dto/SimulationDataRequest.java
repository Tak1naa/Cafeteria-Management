package com.canteen.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * C++ 仿真引擎上报的状态数据
 * 对应接口文档：POST /api/simulation/data
 */
@Data
public class SimulationDataRequest {

    @NotBlank(message = "schemaVersion 不能为空")
    private String schemaVersion;  // 协议版本，固定 "v1"

    @NotNull(message = "simTime 不能为空")
    private Integer simTime;  // 仿真时间（秒）

    @NotNull(message = "queueLengths 不能为空")
    @Size(min = 1, message = "queueLengths 长度必须大于 0")
    private List<Integer> queueLengths;  // 各队列长度数组

    @NotNull(message = "windowCount 不能为空")
    @Positive(message = "windowCount 必须大于 0")
    private Integer windowCount;  // 窗口数量

    @NotNull(message = "availableSeats 不能为空")
    @Min(value = 0, message = "availableSeats 不能小于 0")
    private Integer availableSeats;  // 可用座位数

    @NotNull(message = "waitingForSeat 不能为空")
    @Min(value = 0, message = "waitingForSeat 不能小于 0")
    private Integer waitingForSeat;  // 等待座位人数

    @NotNull(message = "totalArrived 不能为空")
    @Min(value = 0, message = "totalArrived 不能小于 0")
    private Integer totalArrived;  // 总到达人数

    @NotNull(message = "totalServed 不能为空")
    @Min(value = 0, message = "totalServed 不能小于 0")
    private Integer totalServed;  // 总服务人数

    @NotNull(message = "totalSeated 不能为空")
    @Min(value = 0, message = "totalSeated 不能小于 0")
    private Integer totalSeated;  // 总就座人数

    @NotNull(message = "totalFinishedDining 不能为空")
    @Min(value = 0, message = "totalFinishedDining 不能小于 0")
    private Integer totalFinishedDining;  // 完成用餐总人数

    @NotNull(message = "newArrivals 不能为空")
    @Min(value = 0, message = "newArrivals 不能小于 0")
    private Integer newArrivals;  // 新到达人数
}