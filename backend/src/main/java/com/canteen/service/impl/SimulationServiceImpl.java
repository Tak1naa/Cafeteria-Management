package com.canteen.service.impl;

import com.canteen.dto.SimulationDataDTO;
import com.canteen.dto.SimulationDataRequest;
import com.canteen.entity.SimulationSnapshot;
import com.canteen.repository.SimulationSnapshotRepository;
import com.canteen.service.RealtimeStateStore;
import com.canteen.service.SimulationService;
import com.canteen.websocket.SimulationWebSocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SimulationServiceImpl implements SimulationService {

    @Autowired
    private SimulationSnapshotRepository snapshotRepository;

    @Autowired
    private RealtimeStateStore realtimeStateStore;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void processSimulationData(SimulationDataRequest request) {
        log.debug("开始处理仿真数据: simTime={}", request.getSimTime());

        SimulationSnapshot snapshot = new SimulationSnapshot();
        snapshot.setSimTime(request.getSimTime());
        snapshot.setQueueLengths(request.getQueueLengths());
        snapshot.setWindowCount(request.getWindowCount());
        snapshot.setAvailableSeats(request.getAvailableSeats());
        snapshot.setWaitingForSeat(request.getWaitingForSeat());
        snapshot.setTotalArrived(request.getTotalArrived());
        snapshot.setTotalServed(request.getTotalServed());
        snapshot.setTotalSeated(request.getTotalSeated());
        snapshot.setTotalFinishedDining(request.getTotalFinishedDining());
        snapshot.setNewArrivals(request.getNewArrivals());
        snapshotRepository.save(snapshot);
        evictRealtimeCache();
    }

    @Override
    public void updateRealtimeCache(SimulationDataRequest request) {
        realtimeStateStore.updateSimulation(request);

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("realtime:status", request, 5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.trace("Redis 缓存更新跳过: {}", e.getMessage());
            }
        }

        SimulationDataDTO dto = realtimeStateStore.getLatestSimulation();
        SimulationWebSocket.broadcastSimulationData(dto);
        log.trace("实时缓存已更新: simTime={}", request.getSimTime());
    }

    @Override
    @Cacheable(value = "realtimeStatus", unless = "#result == null")
    public SimulationDataDTO getLatestSimulationData() {
        SimulationDataDTO inMemory = realtimeStateStore.getLatestSimulation();
        if (inMemory != null) {
            return inMemory;
        }

        try {
            return snapshotRepository.findFirstByOrderBySimTimeDesc()
                    .map(this::toDto)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("数据库查询最新仿真数据失败: {}", e.getMessage());
            return null;
        }
    }

    @CacheEvict(value = "realtimeStatus", allEntries = true)
    public void evictRealtimeCache() {
        log.trace("实时状态缓存已清除");
    }

    private SimulationDataDTO toDto(SimulationSnapshot snapshot) {
        SimulationDataDTO dto = new SimulationDataDTO();
        dto.setId(snapshot.getId());
        dto.setSimTime(snapshot.getSimTime());
        dto.setQueueLengths(snapshot.getQueueLengths());
        dto.setWindowCount(snapshot.getWindowCount());
        dto.setAvailableSeats(snapshot.getAvailableSeats());
        dto.setWaitingForSeat(snapshot.getWaitingForSeat());
        dto.setTotalArrived(snapshot.getTotalArrived());
        dto.setTotalServed(snapshot.getTotalServed());
        dto.setTotalSeated(snapshot.getTotalSeated());
        dto.setTotalFinishedDining(snapshot.getTotalFinishedDining());
        dto.setNewArrivals(snapshot.getNewArrivals());
        return dto;
    }
}
