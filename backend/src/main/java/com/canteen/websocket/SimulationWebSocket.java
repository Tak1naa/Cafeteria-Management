package com.canteen.websocket;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 服务端
 * 用于实时向前端推送仿真数据
 *
 * @ServerEndpoint 定义 WebSocket 的访问路径
 */
@Slf4j
@Component
@ServerEndpoint("/ws/simulation")
public class SimulationWebSocket {

    // 存储所有在线的 WebSocket 会话（线程安全的集合）
    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();

    /**
     * 连接建立时调用
     */
    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        log.info("WebSocket 连接建立，当前连接数: {}", sessions.size());
    }

    /**
     * 连接关闭时调用
     */
    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        log.info("WebSocket 连接关闭，当前连接数: {}", sessions.size());
    }

    /**
     * 接收客户端消息时调用
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        if (message == null || message.isBlank()) {
            log.debug("收到空 WebSocket 消息，已忽略");
            return;
        }
        if (message.length() > 4096) {
            log.warn("收到过长 WebSocket 消息 ({} 字节)，已关闭连接", message.length());
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.TOO_BIG, "消息过长"));
            } catch (IOException ignored) {
            }
            return;
        }
        log.debug("收到 WebSocket 消息: {}", message);
    }

    /**
     * 发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 错误", error);
        sessions.remove(session);
    }

    /**
     * 向所有客户端广播消息
     * 用于推送实时仿真数据
     */
    public static void broadcast(String message) {
        if (sessions.isEmpty()) {
            return;
        }

        for (Session session : sessions) {
            if (session.isOpen()) {
                // 异步发送，不阻塞主线程
                session.getAsyncRemote().sendText(message);
            }
        }
    }

    /**
     * 广播仿真数据（重载方法）
     */
    public static void broadcastSimulationData(Object data) {
        String json = JSON.toJSONString(data);
        broadcast(json);
        log.debug("已广播仿真数据到 {} 个客户端", sessions.size());
    }
}