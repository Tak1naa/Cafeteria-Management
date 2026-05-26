package com.canteen.controller;

import com.canteen.dto.*;
import com.canteen.producer.SimulationDataProducer;
import com.canteen.service.AiDecisionService;
import com.canteen.service.RealtimeStateStore;
import com.canteen.service.SimulationService;
import com.canteen.websocket.SimulationWebSocket;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 与 C++ 仿真引擎对接的接口
 * 提供两个接口：
 * 1. 状态上报（POST /api/simulation/data）
 * 2. AI 决策（POST /api/ai/decision）
 */
@Slf4j
@RestController
public class CppInterfaceController {

    @Autowired
    private SimulationDataProducer simulationDataProducer;

    @Autowired
    private AiDecisionService aiDecisionService;

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private RealtimeStateStore realtimeStateStore;

    @Value("${business.rate-limit.capacity:1000}")
    private int rateLimitCapacity;

    // 限流器：每秒处理 capacity 个请求
    private RateLimiter rateLimiter;

    /**
     * 初始化限流器
     * @PostConstruct 在 Bean 初始化后执行
     */
    @PostConstruct
    public void init() {
        this.rateLimiter = RateLimiter.create(rateLimitCapacity);
        log.info("限流器已初始化: {} 请求/秒", rateLimitCapacity);
    }

    /**
     * 状态上报接口
     * C++ 仿真引擎每隔一段时间调用此接口上报当前状态
     *
     * 优化策略：
     * 1. 限流保护（防止突发流量）
     * 2. 快速验证（只做必要校验）
     * 3. 异步处理（通过 MQ 处理耗时的数据库写入）
     * 4. 快速返回（保证 1500ms 内响应）
     */
    @PostMapping("/api/simulation/data")
    public ResponseEntity<SimulationDataResponse> receiveSimulationData(
            @Valid @RequestBody SimulationDataRequest request) {

        long startTime = System.currentTimeMillis();

        // 1. 限流检查（令牌桶算法）
        if (!rateLimiter.tryAcquire()) {
            log.warn("请求被限流: simTime={}", request.getSimTime());
            return ResponseEntity.status(429).body(createErrorResponse("rate_limit_exceeded"));
        }

        // 2. 仿真运行状态检查
        if (!realtimeStateStore.isRunning()) {
            return ResponseEntity.status(503).body(createErrorResponse("simulation_stopped"));
        }

        // 2. 快速校验：windowCount 必须等于 queueLengths 的长度
        if (request.getWindowCount() != request.getQueueLengths().size()) {
            log.warn("参数校验失败: windowCount={}, queueLengths.size={}",
                    request.getWindowCount(), request.getQueueLengths().size());
            return ResponseEntity.badRequest().body(
                    createErrorResponse("windowCount must equal queueLengths length")
            );
        }

        // 3. 异步处理（发送到 MQ，主线程立即返回）
        simulationDataProducer.sendSimulationData(request);

        // 4. 可选：更新实时缓存（快速内存操作）
        simulationService.updateRealtimeCache(request);

        // 5. 构造成功响应
        SimulationDataResponse response = new SimulationDataResponse();
        response.setAccepted(true);
        response.setSchemaVersion("v1");
        response.setReceivedAt(LocalDateTime.now());

        long elapsedTime = System.currentTimeMillis() - startTime;
        if (elapsedTime > 100) {
            log.warn("状态上报接口耗时过长: {}ms", elapsedTime);
        } else {
            log.debug("状态上报处理完成: simTime={}, 耗时={}ms",
                    request.getSimTime(), elapsedTime);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * AI 决策接口
     * C++ 请求分配策略，Java 返回 AI 计算结果
     *
     * 此接口需要同步返回结果，不能异步
     */
    @PostMapping("/api/ai/decision")
    public ResponseEntity<AiDecisionResponse> provideAiDecision(
            @Valid @RequestBody AiDecisionRequest request) {

        long startTime = System.currentTimeMillis();

        // 1. 限流检查（令牌桶算法）
        if (!rateLimiter.tryAcquire()) {
            log.warn("请求被限流: simTime={}", request.getSimTime());
            AiDecisionResponse rateLimitResponse = createAiErrorResponse(
                    "rate_limit_exceeded", "请求频率过高，请稍后重试");
            return ResponseEntity.status(429).body(rateLimitResponse);
        }

        // 2. 仿真运行状态检查
        if (!realtimeStateStore.isRunning()) {
            AiDecisionResponse stoppedResp = createAiErrorResponse(
                    "simulation_stopped", "仿真已停止");
            return ResponseEntity.status(503).body(stoppedResp);
        }

        // 3. 参数校验
        if (request.getWindowCount() != request.getQueueLengths().size()) {
            log.warn("AI 决策参数校验失败: windowCount={}, queueLengths.size={}",
                    request.getWindowCount(), request.getQueueLengths().size());
            return ResponseEntity.badRequest().body(
                    createAiErrorResponse("validation_failed", "windowCount must equal queueLengths length")
            );
        }

        if (request.getNewArrivals() < 0) {
            return ResponseEntity.badRequest().body(
                    createAiErrorResponse("validation_failed", "newArrivals must be >= 0")
            );
        }

        // 校验队列长度不能为负
        for (int i = 0; i < request.getQueueLengths().size(); i++) {
            if (request.getQueueLengths().get(i) < 0) {
                return ResponseEntity.badRequest().body(
                        createAiErrorResponse("validation_failed",
                                "queueLengths[" + i + "] must be >= 0")
                );
            }
        }

        // 3. 执行决策（带缓存优化）
        AiDecisionResponse response = aiDecisionService.makeDecisionWithCache(request);

        realtimeStateStore.updateDecision(request, response);
        SimulationWebSocket.broadcastSimulationData(
                java.util.Map.of(
                        "type", "decision",
                        "request", request,
                        "response", response
                )
        );

        // 4. 验证分配结果合法性
        if (!isValidAllocation(response, request)) {
            log.error("AI 决策返回的 allocation 不合法，使用降级策略");
            response = createFallbackAllocation(request);
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("AI 决策接口完成: allocation={}, 耗时={}ms",
                response.getAllocation(), elapsedTime);

        return ResponseEntity.ok(response);
    }

    /**
     * 健康检查接口（供 C++ 检测服务是否可用）
     */
    @GetMapping("/health")
    public ResponseEntity<Object> health() {
        return ResponseEntity.ok().body(
                java.util.Map.of("status", "UP", "timestamp", LocalDateTime.now())
        );
    }

    /**
     * 创建错误响应
     */
    private SimulationDataResponse createErrorResponse(String error) {
        SimulationDataResponse response = new SimulationDataResponse();
        response.setAccepted(false);
        response.setError(error);
        response.setSchemaVersion("v1");
        return response;
    }

    /**
     * 创建 AI 错误响应
     */
    private AiDecisionResponse createAiErrorResponse(String error, String message) {
        AiDecisionResponse response = new AiDecisionResponse();
        response.setError(error);
        response.setMessage(message);
        response.setSchemaVersion("v1");
        return response;
    }

    /**
     * 验证 allocation 是否符合要求
     */
    private boolean isValidAllocation(AiDecisionResponse response, AiDecisionRequest request) {
        if (response == null || response.getAllocation() == null) {
            return false;
        }

        List<Integer> allocation = response.getAllocation();

        // 长度必须等于窗口数
        if (allocation.size() != request.getWindowCount()) {
            return false;
        }

        // 所有元素必须非负
        for (Integer val : allocation) {
            if (val < 0) {
                return false;
            }
        }

        // 总和必须等于新到达人数
        int sum = allocation.stream().mapToInt(Integer::intValue).sum();
        return sum == request.getNewArrivals();
    }

    /**
     * 降级策略：平均分配
     */
    private AiDecisionResponse createFallbackAllocation(AiDecisionRequest request) {
        List<Integer> allocation = new java.util.ArrayList<>();
        int newArrivals = request.getNewArrivals();
        int windowCount = request.getWindowCount();

        // 平均分配
        int base = newArrivals / windowCount;
        int remainder = newArrivals % windowCount;

        for (int i = 0; i < windowCount; i++) {
            allocation.add(base + (i < remainder ? 1 : 0));
        }

        AiDecisionResponse response = new AiDecisionResponse();
        response.setAllocation(allocation);
        response.setSource("fallback");
        response.setDecisionMode("RULE_BASED");
        response.setSchemaVersion("v1");

        return response;
    }
}