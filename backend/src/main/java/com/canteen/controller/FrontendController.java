package com.canteen.controller;

import com.canteen.dto.AiDecisionRequest;
import com.canteen.dto.AiDecisionResponse;
import com.canteen.dto.SimulationDataDTO;
import com.canteen.service.RealtimeStateStore;
import com.canteen.service.SimulationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/frontend")
public class FrontendController {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private RealtimeStateStore realtimeStateStore;

    @GetMapping("/realtime/status")
    public SimulationDataDTO getRealtimeStatus() {
        log.debug("前端请求实时状态");
        return simulationService.getLatestSimulationData();
    }

    @GetMapping("/last-decision")
    public Map<String, Object> getLastDecision() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("request", realtimeStateStore.getLatestDecisionRequest());
        payload.put("response", realtimeStateStore.getLatestDecision());
        payload.put("receivedAt", realtimeStateStore.getLastDecisionAt());
        return payload;
    }

    @GetMapping("/status")
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "running");
        status.put("timestamp", Instant.now().toString());
        status.put("version", "1.0.0");
        status.put("lastSimulationAt", realtimeStateStore.getLastSimulationAt());
        status.put("lastDecisionAt", realtimeStateStore.getLastDecisionAt());
        status.put("hasSimulationData", realtimeStateStore.getLatestSimulation() != null);
        status.put("hasDecisionData", realtimeStateStore.getLatestDecision() != null);
        return status;
    }
}
