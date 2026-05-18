package com.canteen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class CppSeatClientImpl implements CppSeatClient {

    private final WebClient webClient;
    private final String cppSeatConfigUrl;

    public CppSeatClientImpl(WebClient.Builder webClientBuilder,
                             @Value("${cpp.service.url:http://localhost:8080}") String cppBaseUrl) {
        this.webClient = webClientBuilder.baseUrl(cppBaseUrl).build();
        this.cppSeatConfigUrl = "/api/seats/config";
        log.info("初始化 C++ 客户端，基础 URL: {}", cppBaseUrl);
    }

    @Override
    public void sendConfig(SeatConfigDTO config) {
        log.info("调用 C++ 后端接口：{}，请求参数：{}", cppSeatConfigUrl, config);

        try {
            webClient.post()
                    .uri(cppSeatConfigUrl)
                    .bodyValue(config)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();  // 同步调用，阻塞等待结果
            log.info("C++ 后端调用成功");
        } catch (Exception e) {
            log.error("调用 C++ 后端失败", e);
            throw new RuntimeException("转发配置到 C++ 服务失败: " + e.getMessage(), e);
        }
    }
}