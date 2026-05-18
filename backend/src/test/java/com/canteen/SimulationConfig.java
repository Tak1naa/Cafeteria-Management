package com.canteen;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulation_config")
@Data
public class SimulationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "windows")
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