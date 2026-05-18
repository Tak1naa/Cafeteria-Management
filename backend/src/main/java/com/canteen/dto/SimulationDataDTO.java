package com.canteen.dto;

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

    // 计算得出的字段
    private Double avgQueueLength;   //平均队列长度
    private Double utilizationRate;  // 座位利用率

    /**
     * 辅助方法：计算平均队列长度
     */
    public Double getAvgQueueLength() {
        if (queueLengths == null || queueLengths.isEmpty()) {
            return 0.0;
        }
        return queueLengths.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    /**
     * 辅助方法：计算座位利用率
     * 假设总座位数 = availableSeats + waitingForSeat（已就座+等待）
     */
    public Double getUtilizationRate() {
        int totalSeats = availableSeats + waitingForSeat;
        if (totalSeats == 0) return 0.0;
        return (double) totalSeated / totalSeats * 100;
    }
}