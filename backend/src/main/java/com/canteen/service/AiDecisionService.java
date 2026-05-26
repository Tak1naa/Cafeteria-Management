package com.canteen.service;

import com.canteen.dto.AiDecisionRequest;
import com.canteen.dto.AiDecisionResponse;

/**
 * AI 决策服务接口
 */
public interface AiDecisionService {

    /**
     * 执行 AI 决策
     * @param request AI 决策请求
     * @return 分配决策结果
     */
    AiDecisionResponse makeDecision(AiDecisionRequest request);

    /**
     * 带缓存的决策（快速响应）
     * @param request AI 决策请求
     * @return 分配决策结果
     */
    AiDecisionResponse makeDecisionWithCache(AiDecisionRequest request);
}