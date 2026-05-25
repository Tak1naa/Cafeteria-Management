package com.canteen.service.impl;

import com.canteen.dto.AiDecisionRequest;
import com.canteen.dto.AiDecisionResponse;
import com.canteen.entity.DecisionRecord;
import com.canteen.repository.DecisionRecordRepository;
import com.canteen.service.AiDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiDecisionServiceImpl implements AiDecisionService {

    @Autowired(required = false)
    private DecisionRecordRepository decisionRecordRepository;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

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

        List<Integer> allocation = calculateWeightedAllocation(request);
        saveDecisionRecord(request, allocation, startTime);

        AiDecisionResponse response = new AiDecisionResponse();
        response.setAllocation(allocation);
        response.setSource("ai_service");
        response.setDecisionMode("AI");
        response.setSchemaVersion("v1");

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("AI 决策完成: allocation={}, 耗时={}ms", allocation, elapsedTime);

        return response;
    }

    private List<Integer> calculateWeightedAllocation(AiDecisionRequest request) {
        int newArrivals = request.getNewArrivals();
        List<Integer> queueLengths = request.getQueueLengths();
        int windowCount = request.getWindowCount();

        List<Integer> allocation = new ArrayList<>(windowCount);

        if (newArrivals == 0) {
            for (int i = 0; i < windowCount; i++) {
                allocation.add(0);
            }
            return allocation;
        }

        double[] weights = new double[windowCount];
        double totalWeight = 0;
        for (int i = 0; i < windowCount; i++) {
            weights[i] = 1.0 / (queueLengths.get(i) + 1);
            totalWeight += weights[i];
        }

        int[] allocArray = new int[windowCount];
        int remaining = newArrivals;

        for (int i = 0; i < windowCount && remaining > 0; i++) {
            int assigned = (int) Math.round(weights[i] / totalWeight * newArrivals);
            assigned = Math.min(assigned, remaining);
            allocArray[i] = assigned;
            remaining -= assigned;
        }

        if (remaining > 0) {
            allocArray[0] += remaining;
        }

        for (int i = 0; i < windowCount; i++) {
            allocation.add(allocArray[i]);
        }

        return allocation;
    }

    private void saveDecisionRecord(AiDecisionRequest request,
                                    List<Integer> allocation,
                                    long startTime) {
        if (decisionRecordRepository == null) {
            return;
        }
        try {
            DecisionRecord record = new DecisionRecord();
            record.setSimTime(request.getSimTime());
            record.setNewArrivals(request.getNewArrivals());
            record.setAllocation(allocation);
            record.setDecisionMode("AI");
            record.setSource("ai_service");
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
