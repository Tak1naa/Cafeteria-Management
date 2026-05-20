package com.canteen.entity;

import com.canteen.converter.JsonToListConverter;
import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 仿真状态快照表
 * 存储每个时刻的仿真数据，用于历史查询和分析
 */
@Data
@Entity
@Table(name = "simulation_snapshot", indexes = {
        @Index(name = "idx_sim_time", columnList = "sim_time")
})
public class SimulationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sim_time", nullable = false)
    private Integer simTime;  // 仿真时间

    @Convert(converter = JsonToListConverter.class)
    @Column(columnDefinition = "JSON")
    private List<Integer> queueLengths;  // 队列长度数组（存为 JSON）

    @Column(name = "window_count")
    private Integer windowCount;

    @Column(name = "available_seats")
    private Integer availableSeats;

    @Column(name = "waiting_for_seat")
    private Integer waitingForSeat;

    @Column(name = "total_arrived")
    private Integer totalArrived;

    @Column(name = "total_served")
    private Integer totalServed;

    @Column(name = "total_seated")
    private Integer totalSeated;

    @Column(name = "total_finished_dining")
    private Integer totalFinishedDining;

    @Column(name = "new_arrivals")
    private Integer newArrivals;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * JPA 生命周期回调：在插入前自动设置创建时间
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}