package com.canteen.service;

import com.canteen.dto.AiDecisionRequest;
import com.canteen.dto.AiDecisionResponse;
import com.canteen.dto.SimulationDataDTO;
import com.canteen.dto.SimulationDataRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 联调阶段内存态：不依赖 Redis 即可向前端提供最新仿真与决策数据。
 */
@Component
public class RealtimeStateStore {

    private final AtomicReference<SimulationDataDTO> latestSimulation = new AtomicReference<>();
    private final AtomicReference<AiDecisionResponse> latestDecision = new AtomicReference<>();
    private final AtomicReference<AiDecisionRequest> latestDecisionRequest = new AtomicReference<>();
    private final AtomicReference<Instant> lastSimulationAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastDecisionAt = new AtomicReference<>();

    public void updateSimulation(SimulationDataRequest request) {
        SimulationDataDTO dto = toDto(request);
        latestSimulation.set(dto);
        lastSimulationAt.set(Instant.now());
    }

    public void updateDecision(AiDecisionRequest request, AiDecisionResponse response) {
        latestDecisionRequest.set(request);
        latestDecision.set(response);
        lastDecisionAt.set(Instant.now());
    }

    public SimulationDataDTO getLatestSimulation() {
        return latestSimulation.get();
    }

    public AiDecisionResponse getLatestDecision() {
        return latestDecision.get();
    }

    public AiDecisionRequest getLatestDecisionRequest() {
        return latestDecisionRequest.get();
    }

    public Instant getLastSimulationAt() {
        return lastSimulationAt.get();
    }

    public Instant getLastDecisionAt() {
        return lastDecisionAt.get();
    }

    private SimulationDataDTO toDto(SimulationDataRequest request) {
        SimulationDataDTO dto = new SimulationDataDTO();
        dto.setSimTime(request.getSimTime());
        dto.setQueueLengths(request.getQueueLengths());
        dto.setWindowCount(request.getWindowCount());
        dto.setAvailableSeats(request.getAvailableSeats());
        dto.setWaitingForSeat(request.getWaitingForSeat());
        dto.setTotalArrived(request.getTotalArrived());
        dto.setTotalServed(request.getTotalServed());
        dto.setTotalSeated(request.getTotalSeated());
        dto.setTotalFinishedDining(request.getTotalFinishedDining());
        dto.setNewArrivals(request.getNewArrivals());
        return dto;
    }
}
