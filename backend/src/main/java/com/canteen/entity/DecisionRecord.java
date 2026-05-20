package com.canteen.entity;

import com.canteen.converter.JsonToListConverter;
import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 决策记录表
 * 存储每次 AI 决策的详细信息，用于分析和复盘
 */
@Data
@Entity
@Table(name = "decision_record")
public class DecisionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sim_time")
    private Integer simTime;

    @Column(name = "new_arrivals")
    private Integer newArrivals;

    @Convert(converter = JsonToListConverter.class)
    @Column(columnDefinition = "JSON")
    private List<Integer> allocation;  // 分配结果

    @Column(name = "decision_mode")
    private String decisionMode;  // AI 或 RULE_BASED

    private String source;  // 决策来源

    @Convert(converter = JsonToListConverter.class)
    @Column(name = "queue_lengths_before", columnDefinition = "JSON")
    private List<Integer> queueLengthsBefore;  // 决策前的队列长度

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;  // 响应时间（毫秒）

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}