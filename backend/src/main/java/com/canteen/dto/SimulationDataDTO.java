package com.canteen.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.util.List;

@Data
public class SimulationDataDTO {

    // 数据库主键
    private Long id;

    // 仿真时间（秒）
    private Integer simTime;

    // 队列长度数组
    private List<Integer> queueLengths;

    // 窗口数量
    private Integer windowCount;

    // 可用座位数
    private Integer availableSeats;

    // 等待座位人数
    private Integer waitingForSeat;

    // 总到达人数
    private Integer totalArrived;

    // 总服务人数
    private Integer totalServed;

    // 总就座人数
    private Integer totalSeated;

    // 完成用餐总人数
    private Integer totalFinishedDining;

    // 新到达人数
    private Integer newArrivals;

    // 记录创建时间
    private String createdAt;

    /**
     * 平均队列长度（动态计算）
     */
    public Double getAvgQueueLength() {
        if (queueLengths == null || queueLengths.isEmpty()) {
            return 0.0;
        }
        return queueLengths.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    /**
     * 座位利用率（动态计算）
     * 利用率 = 已就座人数 / (空座位 + 已就座人数) × 100
     */
    public Double getUtilizationRate() {
        if (totalSeated == null || totalSeated == 0) {
            return 0.0;
        }
        int occupied = totalSeated;
        int empty = availableSeats != null ? availableSeats : 0;
        int totalSeats = occupied + empty;
        if (totalSeats == 0) {
            return 0.0;
        }
        return (double) occupied / totalSeats * 100;
    }

    // Not a database field — prevent double serialization
    @JsonIgnore
    public boolean isEmpty() {
        return simTime == null;
    }
}
