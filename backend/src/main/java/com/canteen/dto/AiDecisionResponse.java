package com.canteen.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * AI 决策响应
 * Java 返回给 C++ 的分配方案
 */
@Data
public class AiDecisionResponse {

    @NotNull(message = "allocation 不能为空")
    private List<Integer> allocation;  // 分配数组，长度等于 windowCount

    private String source;        // 决策来源，"ai_service"
    private String decisionMode;  // 决策模式，"AI" 或 "RULE_BASED"
    private String schemaVersion; // 协议版本

    // 错误时使用
    private String error;
    private String message;
}