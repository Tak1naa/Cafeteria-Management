package com.canteen;  // 注意包名和主类一致

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulation_config")  // 表名建议用复数或下划线命名
@Data
public class SimulationConfig {  // 类名用更语义化的名字，不用SQLcon

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "windows")  // 可以省略，如果字段名和列名一致
    private Integer windows;

    @Column(name = "seats")
    private Integer seats;

    @Column(name = "avg_cook_time")
    private Double avgCookTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 可以添加@PrePersist自动设置创建时间
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}