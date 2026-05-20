package com.canteen.repository;

import com.canteen.entity.SimulationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 仿真快照数据访问接口
 * JpaRepository 提供了基本的 CRUD 方法
 */
@Repository
public interface SimulationSnapshotRepository extends JpaRepository<SimulationSnapshot, Long> {

    /**
     * 获取最新的一条记录
     * 按 simTime 降序排序，取第一条
     */
    Optional<SimulationSnapshot> findFirstByOrderBySimTimeDesc();

    /**
     * 获取指定时间范围内的快照
     */
    List<SimulationSnapshot> findBySimTimeBetween(Integer startTime, Integer endTime);

    /**
     * 获取指定日期的所有快照
     */
    @Query("SELECT s FROM SimulationSnapshot s WHERE DATE(s.createdAt) = :date")
    List<SimulationSnapshot> findByDate(@Param("date") LocalDateTime date);

    /**
     * 获取队列长度历史（用于趋势图）
     * nativeQuery = true 表示使用原生 SQL
     */
    @Query(value = "SELECT sim_time, queue_lengths FROM simulation_snapshot " +
            "WHERE DATE(created_at) = :date ORDER BY sim_time", nativeQuery = true)
    List<Object[]> findQueueHistoryByDate(@Param("date") String date);
}