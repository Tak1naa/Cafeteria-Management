package com.canteen.service;

import com.canteen.dto.AiDecisionRequest;
import com.canteen.dto.AiDecisionResponse;
import com.canteen.dto.SimulationDataDTO;
import com.canteen.dto.SimulationDataRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 联调阶段内存态：不依赖 Redis 即可向前端提供最新仿真与决策数据。
 * 使用不可变快照保证原子读写。
 */
@Component
public class RealtimeStateStore {

    private static class SimulationSnapshot {
        final SimulationDataDTO data;
        final Instant timestamp;

        SimulationSnapshot(SimulationDataDTO data, Instant timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    private static class DecisionSnapshot {
        final AiDecisionRequest request;
        final AiDecisionResponse response;
        final Instant timestamp;

        DecisionSnapshot(AiDecisionRequest request, AiDecisionResponse response, Instant timestamp) {
            this.request = request;
            this.response = response;
            this.timestamp = timestamp;
        }
    }

    private final AtomicReference<SimulationSnapshot> latestSimulation = new AtomicReference<>();
    private final AtomicReference<DecisionSnapshot> latestDecision = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public boolean isRunning() {
        return running.get();
    }

    public void setRunning(boolean value) {
        running.set(value);
    }

    public void reset() {
        latestSimulation.set(null);
        latestDecision.set(null);
    }

    public void updateSimulation(SimulationDataRequest request) {
        SimulationDataDTO dto = toDto(request);
        latestSimulation.set(new SimulationSnapshot(dto, Instant.now()));
    }

    public void updateDecision(AiDecisionRequest request, AiDecisionResponse response) {
        latestDecision.set(new DecisionSnapshot(request, response, Instant.now()));
    }

    public SimulationDataDTO getLatestSimulation() {
        SimulationSnapshot snap = latestSimulation.get();
        return snap != null ? snap.data : null;
    }

    public AiDecisionResponse getLatestDecision() {
        DecisionSnapshot snap = latestDecision.get();
        return snap != null ? snap.response : null;
    }

    public AiDecisionRequest getLatestDecisionRequest() {
        DecisionSnapshot snap = latestDecision.get();
        return snap != null ? snap.request : null;
    }

    public Instant getLastSimulationAt() {
        SimulationSnapshot snap = latestSimulation.get();
        return snap != null ? snap.timestamp : null;
    }

    public Instant getLastDecisionAt() {
        DecisionSnapshot snap = latestDecision.get();
        return snap != null ? snap.timestamp : null;
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
