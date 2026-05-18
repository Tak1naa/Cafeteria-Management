package com.canteen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/seats")
public class SeatController {
    private final SeatService seatService;

    // 构造器注入
    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping("/config")
    public ResponseEntity<String> updateConfig(@Valid @RequestBody SeatConfigRequest request) {
        log.info("收到座位配置请求: {} @ {}", request, LocalDateTime.now());

        // 调用 Service 处理业务逻辑
        seatService.processConfig(request);

        return ResponseEntity.ok("座位配置已接收并处理");
    }
}
