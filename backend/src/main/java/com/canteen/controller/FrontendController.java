package com.canteen.controller;

import com.canteen.dto.AiDecisionRequest;
import com.canteen.dto.AiDecisionResponse;
import com.canteen.dto.SimulationDataDTO;
import com.canteen.service.RealtimeStateStore;
import com.canteen.service.SimulationLauncher;
import com.canteen.service.SimulationService;
import com.canteen.websocket.SimulationWebSocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Autowired
    private SimulationLauncher simulationLauncher;

    @GetMapping("/realtime/status")
    public SimulationDataDTO getRealtimeStatus() {
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
        status.put("status", realtimeStateStore.isRunning() ? "running" : "stopped");
        status.put("timestamp", Instant.now().toString());
        status.put("version", "1.0.0");
        status.put("lastSimulationAt", realtimeStateStore.getLastSimulationAt());
        status.put("lastDecisionAt", realtimeStateStore.getLastDecisionAt());
        status.put("hasSimulationData", realtimeStateStore.getLatestSimulation() != null);
        status.put("hasDecisionData", realtimeStateStore.getLatestDecision() != null);
        status.put("cppRunning", simulationLauncher.isRunning());
        return status;
    }

    @PostMapping("/simulation/start")
    public Map<String, Object> startSimulation(@RequestBody(required = false) Map<String, Object> body) {
        realtimeStateStore.reset();
        realtimeStateStore.setRunning(true);
        simulationLauncher.restart(body);
        Map<String, Object> result = new HashMap<>();
        result.put("action", "start");
        result.put("status", "running");
        result.put("message", "仿真已启动" + (body != null && !body.isEmpty() ? "（含自定义参数）" : ""));
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    @PostMapping("/simulation/stop")
    public Map<String, Object> stopSimulation() {
        realtimeStateStore.setRunning(false);
        realtimeStateStore.reset();
        simulationLauncher.stop();
        Map<String, Object> result = new HashMap<>();
        result.put("action", "stop");
        result.put("status", "stopped");
        result.put("message", "仿真已停止，C++ 引擎已终止");
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    @PostMapping("/simulation/reset")
    public Map<String, Object> resetSimulation(@RequestBody(required = false) Map<String, Object> body) {
        realtimeStateStore.reset();
        simulationService.resetAllData();

        Map<String, Object> resetMsg = new HashMap<>();
        resetMsg.put("type", "reset");
        resetMsg.put("timestamp", Instant.now().toString());
        SimulationWebSocket.broadcastSimulationData(resetMsg);

        realtimeStateStore.setRunning(true);
        simulationLauncher.restart(body);
        Map<String, Object> result = new HashMap<>();
        result.put("action", "reset");
        result.put("status", "reset_complete");
        result.put("message", "仿真数据已清除，已重新启动" + (body != null && !body.isEmpty() ? "（含自定义参数）" : ""));
        result.put("timestamp", Instant.now().toString());
        return result;
    }
}
