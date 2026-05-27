package com.canteen.service.impl;

import com.canteen.dto.AiDecisionRequest;
import com.canteen.dto.AiDecisionResponse;
import com.canteen.entity.DecisionRecord;
import com.canteen.repository.DecisionRecordRepository;
import com.canteen.service.AiDecisionService;
import com.canteen.service.DeepSeekClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiDecisionServiceImpl implements AiDecisionService {

    @Autowired(required = false)
    private DecisionRecordRepository decisionRecordRepository;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private DeepSeekClient deepSeekClient;

    @Override
    public AiDecisionResponse makeDecisionWithCache(AiDecisionRequest request) {
        String cacheKey = generateCacheKey(request);

        if (redisTemplate != null) {
            try {
                AiDecisionResponse cached = (AiDecisionResponse) redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    log.debug("命中决策缓存: key={}", cacheKey);
                    return cached;
                }
            } catch (Exception e) {
                log.trace("Redis 决策缓存读取跳过: {}", e.getMessage());
            }
        }

        AiDecisionResponse response = makeDecision(request);

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, response, 10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.trace("Redis 决策缓存写入跳过: {}", e.getMessage());
            }
        }

        return response;
    }

    @Override
    public AiDecisionResponse makeDecision(AiDecisionRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("执行 AI 决策: newArrivals={}, queueLengths={}",
                request.getNewArrivals(), request.getQueueLengths());

        // 优先使用 DeepSeek API
        if (deepSeekClient != null) {
            try {
                Optional<AiDecisionResponse> deepSeekResult = deepSeekClient.requestDecision(request);
                if (deepSeekResult.isPresent()) {
                    AiDecisionResponse response = deepSeekResult.get();
                    saveDecisionRecord(request, response.getAllocation(), startTime,
                            response.getSource(), response.getDecisionMode());
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("DeepSeek 决策完成: allocation={}, 耗时={}ms", response.getAllocation(), elapsed);
                    return response;
                }
            } catch (Exception e) {
                log.warn("DeepSeek 决策异常，回退到规则引擎: {}", e.getMessage());
            }
        }

        // 回退：加权最短队列算法
        List<Integer> allocation = calculateWeightedAllocation(request);
        saveDecisionRecord(request, allocation, startTime, "ai_service", "RULE_BASED");

        AiDecisionResponse response = new AiDecisionResponse();
        response.setAllocation(allocation);
        response.setSource("ai_service");
        response.setDecisionMode("RULE_BASED");
        response.setSchemaVersion("v1");

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("规则引擎决策完成: allocation={}, 耗时={}ms", allocation, elapsedTime);

        return response;
    }

    private List<Integer> calculateWeightedAllocation(AiDecisionRequest request) {
        int newArrivals = request.getNewArrivals();
        List<Integer> queueLengths = request.getQueueLengths();
        int windowCount = request.getWindowCount();

        List<Integer> allocation = new ArrayList<>(windowCount);
        for (int i = 0; i < windowCount; i++) {
            allocation.add(0);
        }

        if (newArrivals == 0) {
            return allocation;
        }

        // 计算权重：队列越短权重越高
        double[] weights = new double[windowCount];
        double totalWeight = 0;
        for (int i = 0; i < windowCount; i++) {
            weights[i] = 1.0 / (Math.max(queueLengths.get(i), 0) + 1);
            totalWeight += weights[i];
        }

        // 按权重比例分配（先取 floor，确保不超分）
        int allocated = 0;
        int[] floorAlloc = new int[windowCount];
        for (int i = 0; i < windowCount; i++) {
            floorAlloc[i] = (int) (weights[i] / totalWeight * newArrivals);
            allocated += floorAlloc[i];
        }

        // 余数按"队列最短优先"逐个分配
        int remaining = newArrivals - allocated;
        for (int r = 0; r < remaining; r++) {
            int bestIdx = 0;
            int bestLen = Integer.MAX_VALUE;
            for (int i = 0; i < windowCount; i++) {
                int projectedLen = queueLengths.get(i) + floorAlloc[i];
                if (projectedLen < bestLen) {
                    bestLen = projectedLen;
                    bestIdx = i;
                }
            }
            floorAlloc[bestIdx]++;
        }

        for (int i = 0; i < windowCount; i++) {
            allocation.set(i, floorAlloc[i]);
        }

        return allocation;
    }

    private void saveDecisionRecord(AiDecisionRequest request,
                                    List<Integer> allocation,
                                    long startTime,
                                    String source,
                                    String decisionMode) {
        if (decisionRecordRepository == null) {
            return;
        }
        try {
            DecisionRecord record = new DecisionRecord();
            record.setSimTime(request.getSimTime());
            record.setNewArrivals(request.getNewArrivals());
            record.setAllocation(allocation);
            record.setDecisionMode(decisionMode);
            record.setSource(source);
            record.setQueueLengthsBefore(request.getQueueLengths());
            record.setResponseTimeMs((int) (System.currentTimeMillis() - startTime));
            decisionRecordRepository.save(record);
        } catch (Exception e) {
            log.warn("决策记录落库失败（联调可忽略）: {}", e.getMessage());
        }
    }

    private String generateCacheKey(AiDecisionRequest request) {
        return String.format("decision:%d:%d:%s",
                request.getSimTime(),
                request.getNewArrivals(),
                request.getQueueLengths().hashCode());
    }
}
