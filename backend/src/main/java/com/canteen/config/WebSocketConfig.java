package com.canteen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置类
 * 用于实时向前端推送数据
 */
@Configuration
public class WebSocketConfig {

    /**
     * 自动注册使用 @ServerEndpoint 注解的类
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}