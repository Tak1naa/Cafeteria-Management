package com.canteen.consumer;

import com.canteen.config.RabbitMQConfig;
import com.canteen.dto.SimulationDataRequest;
import com.canteen.service.SimulationService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;

/**
 * 仿真数据消费者
 * 从队列中取出数据并进行处理
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "integration.rabbit.enabled", havingValue = "true")
public class SimulationDataConsumer {

    private static final int MAX_RETRY_COUNT = 3;

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 消费仿真数据
     *
     * @param request 仿真数据请求
     * @param channel RabbitMQ 通道（用于手动确认消息）
     * @param message 原始消息（用于获取消息属性）
     *
     * concurrency = "3-10"：并发消费数量，最少 3 个，最多 10 个
     */
    @RabbitListener(queues = RabbitMQConfig.SIMULATION_DATA_QUEUE, concurrency = "3-10")
    public void consumeSimulationData(SimulationDataRequest request,
                                      Channel channel,
                                      Message message) throws IOException {
        long startTime = System.currentTimeMillis();
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.debug("开始处理仿真数据: simTime={}, 队列长度={}",
                    request.getSimTime(), request.getQueueLengths().size());

            // 调用 Service 处理业务逻辑（写入数据库等）
            simulationService.processSimulationData(request);

            long processTime = System.currentTimeMillis() - startTime;
            log.debug("仿真数据处理完成，耗时: {}ms", processTime);

            // 事务提交成功后再 ACK，避免数据丢失
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            channel.basicAck(deliveryTag, false);
                            log.debug("事务提交成功，消息已确认: tag={}", deliveryTag);
                        } catch (Exception e) {
                            log.warn("ACK 失败(channel已关闭): tag={}", deliveryTag);
                        }
                    }
                });
            } else {
                try {
                    channel.basicAck(deliveryTag, false);
                } catch (Exception e) {
                    log.warn("ACK 失败(channel已关闭): tag={}", deliveryTag);
                }
            }

        } catch (Exception e) {
            log.error("处理仿真数据失败: simTime={}", request.getSimTime(), e);

            int retryCount = getRetryCount(message);

            if (retryCount < MAX_RETRY_COUNT) {
                // 重新发布消息（带递增的重试计数），原消息不重新入队
                try {
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.SIMULATION_EXCHANGE,
                            RabbitMQConfig.SIMULATION_DATA_ROUTING_KEY,
                            request,
                            msg -> {
                                msg.getMessageProperties().setHeader("retry-count", retryCount + 1);
                                msg.getMessageProperties().setExpiration("30000");
                                return msg;
                            }
                    );
                } catch (Exception pubEx) {
                    log.error("重试消息发布失败，消息将丢失: simTime={}", request.getSimTime(), pubEx);
                }
                // 拒绝原消息，不重新入队（因为已经发布了新消息）
                try {
                    channel.basicReject(deliveryTag, false);
                } catch (Exception ignored) {}
                log.warn("消息处理失败，已发布重试: retryCount={}", retryCount + 1);
            } else {
                // 重试次数超过上限，拒绝消息（进入死信队列或丢弃）
                try {
                    channel.basicReject(deliveryTag, false);
                } catch (Exception ignored) {}
                log.error("消息处理失败超过{}次，已拒绝: simTime={}", MAX_RETRY_COUNT, request.getSimTime());
            }
        }
    }

    /**
     * 从消息头获取重试次数
     */
    private int getRetryCount(Message message) {
        Object retryCount = message.getMessageProperties().getHeader("retry-count");
        if (retryCount instanceof Integer i) {
            return i;
        }
        if (retryCount instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
