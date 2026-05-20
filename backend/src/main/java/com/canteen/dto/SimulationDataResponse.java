package com.canteen.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 状态上报响应
 * C++ 期望的返回格式
 */
@Data
public class SimulationDataResponse {
    private Boolean accepted;      // 是否接受
    private String schemaVersion;  // 协议版本
    private LocalDateTime receivedAt;  // 接收时间

    // 错误时使用
    private String error;      // 错误码
    private String message;    // 错误消息
}