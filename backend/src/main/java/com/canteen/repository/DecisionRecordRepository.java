package com.canteen.repository;

import com.canteen.entity.DecisionRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionRecordRepository extends JpaRepository<DecisionRecord, Long> {

    /**
     * 分页查询决策记录
     * Pageable 包含页码、每页大小、排序信息
     */
    List<DecisionRecord> findAllByOrderBySimTimeDesc(Pageable pageable);

    /**
     * 统计指定时间范围内的决策次数
     */
    long countBySimTimeBetween(Integer startTime, Integer endTime);
}