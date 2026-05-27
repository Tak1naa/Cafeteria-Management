package com.canteen.service;

import com.canteen.dto.AiDecisionRequest;
import com.canteen.dto.AiDecisionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class DeepSeekClient {

    private final WebClient webClient;
    private final String model;
    private final Duration timeout;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(
            @Value("${deepseek.api.key}") String apiKey,
            @Value("${deepseek.api.base-url}") String baseUrl,
            @Value("${deepseek.api.model}") String model,
            @Value("${deepseek.api.timeout}") long timeoutMs,
            @Value("${deepseek.api.enabled:false}") boolean enabled) {
        this.model = model;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.enabled = enabled && apiKey != null && !apiKey.isBlank();
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        if (!this.enabled) {
            log.info("DeepSeek API 未启用（enabled={}, keyPresent={}）",
                    enabled, apiKey != null && !apiKey.isBlank());
        }
    }

    public Optional<AiDecisionResponse> requestDecision(AiDecisionRequest request) {
        if (!enabled) {
            log.debug("DeepSeek API 未启用，跳过");
            return Optional.empty();
        }

        try {
            String prompt = buildPrompt(request);
            String rawJson = callChatCompletion(prompt);
            AiDecisionResponse response = parseAllocation(rawJson, request);
            log.info("DeepSeek 决策成功: allocation={}", response.getAllocation());
            return Optional.of(response);
        } catch (Exception e) {
            log.warn("DeepSeek API 调用失败，将回退到规则引擎: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(AiDecisionRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 食堂排队分配任务\n\n");
        sb.append("你需要将 **").append(req.getNewArrivals()).append(" 位新到达顾客** ");
        sb.append("分配到 ").append(req.getWindowCount()).append(" 个取餐窗口。\n\n");

        sb.append("### 当前各窗口队列长度\n");
        var queues = req.getQueueLengths();
        for (int i = 0; i < queues.size(); i++) {
            sb.append("- 窗口").append(i + 1).append("：").append(queues.get(i)).append(" 人\n");
        }

        sb.append("\n### 等待时间统计\n");
        if (req.getAvgQueueWaitSec() != null) {
            sb.append("- 平均等待：").append(String.format("%.1f", req.getAvgQueueWaitSec())).append(" 秒\n");
            sb.append("- 最大等待：").append(req.getMaxQueueWaitSec()).append(" 秒\n");
            sb.append("- P50：").append(String.format("%.1f", req.getP50QueueWaitSec())).append(" 秒\n");
            sb.append("- P90：").append(String.format("%.1f", req.getP90QueueWaitSec())).append(" 秒\n");
        } else {
            sb.append("（暂无历史数据）\n");
        }

        sb.append("\n### 座位状态\n");
        sb.append("- 可用座位：").append(req.getAvailableSeats()).append("\n");
        sb.append("- 等座人数：").append(req.getWaitingForSeat()).append("\n");

        sb.append("\n### 优化目标（按优先级）\n");
        sb.append("1. 优先分配到**队列较短**的窗口，减少个体等待时间\n");
        sb.append("2. 平衡各窗口负载，避免某个窗口堆积而其他窗口空闲\n");
        sb.append("3. 若某些窗口历史等待时间明显偏高，应减少对其分配\n");
        sb.append("4. 考虑座位紧张程度：若等座人多，优先填满短队列让顾客尽快入座\n");

        sb.append("\n返回 JSON，allocation 数组长度 = ").append(req.getWindowCount());
        sb.append("，元素之和 = ").append(req.getNewArrivals()).append("。");
        sb.append("只返回 JSON，不要解释。");

        return sb.toString();
    }

    private String callChatCompletion(String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "你是高校食堂智能调度系统。你的任务是将新到达的顾客分配到各取餐窗口，" +
                                "以最小化整体等待时间、平衡窗口负载、提升就餐体验。" +
                                "你只能返回 JSON，格式：{\"allocation\": [n1, n2, ..., nk]}" +
                                "其中 k=窗口数，所有 ni >= 0，sum(ni) = 新到达人数。" +
                                "不得返回任何其他文字、解释或 markdown。"),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.3,
                "max_tokens", 300,
                "response_format", Map.of("type", "json_object"));

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(1, Duration.ofSeconds(1))
                        .maxAttempts(2))
                .block()
                .getChoices().get(0)
                .getMessage()
                .getContent();
    }

    private AiDecisionResponse parseAllocation(String rawJson, AiDecisionRequest request) {
        try {
            AllocationPayload payload = objectMapper.readValue(rawJson, AllocationPayload.class);
            List<Integer> allocation = payload.getAllocation();

            if (allocation == null || allocation.size() != request.getWindowCount()) {
                throw new IllegalStateException(
                        String.format("分配数组长度(%d) != 窗口数(%d)",
                                allocation == null ? 0 : allocation.size(),
                                request.getWindowCount()));
            }

            int sum = allocation.stream().mapToInt(Integer::intValue).sum();
            if (sum != request.getNewArrivals()) {
                throw new IllegalStateException(
                        String.format("分配总和(%d) != 新到达人数(%d)", sum, request.getNewArrivals()));
            }

            for (int v : allocation) {
                if (v < 0) {
                    throw new IllegalStateException("分配值不能为负数: " + v);
                }
            }

            AiDecisionResponse response = new AiDecisionResponse();
            response.setAllocation(allocation);
            response.setSource("deepseek");
            response.setDecisionMode("AI");
            response.setSchemaVersion("v1");
            return response;
        } catch (Exception e) {
            throw new RuntimeException("解析 DeepSeek 响应失败: " + e.getMessage(), e);
        }
    }

    @Data
    private static class AllocationPayload {
        private List<Integer> allocation;
    }

    @Data
    private static class ChatCompletionResponse {
        private List<Choice> choices;

        @Data
        public static class Choice {
            private Message message;
        }

        @Data
        public static class Message {
            private String content;
        }
    }
}
