package com.canteen.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 * 定义队列、交换机、绑定关系及死信队列
 */
@Configuration
@ConditionalOnProperty(name = "integration.rabbit.enabled", havingValue = "true")
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    // 队列名称（常量）
    public static final String SIMULATION_DATA_QUEUE = "simulation.data.queue";
    public static final String AI_DECISION_QUEUE = "ai.decision.queue";
    public static final String NOTIFICATION_QUEUE = "notification.queue";

    // 死信队列
    public static final String SIMULATION_DATA_DLQ = "simulation.data.dlq";
    public static final String DEAD_LETTER_EXCHANGE = "simulation.dead.letter";

    // 交换机名称
    public static final String SIMULATION_EXCHANGE = "simulation.exchange";
    public static final String AI_EXCHANGE = "ai.exchange";

    // 路由键
    public static final String SIMULATION_DATA_ROUTING_KEY = "simulation.data.process";
    public static final String AI_DECISION_ROUTING_KEY = "ai.decision.process";
    public static final String SIMULATION_DATA_DLQ_ROUTING_KEY = "simulation.data.dlq";

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue simulationDataDlq() {
        return QueueBuilder.durable(SIMULATION_DATA_DLQ).build();
    }

    @Bean
    public Binding simulationDataDlqBinding() {
        return BindingBuilder.bind(simulationDataDlq())
                .to(deadLetterExchange())
                .with(SIMULATION_DATA_DLQ_ROUTING_KEY);
    }

    /**
     * 定义队列（持久化、不自动删除）
     * durable=true：服务器重启后队列仍然存在
     * 配置死信交换机，消息被拒绝后自动路由到 DLQ
     */
    @Bean
    public Queue simulationDataQueue() {
        return QueueBuilder.durable(SIMULATION_DATA_QUEUE)
                .withArgument("x-max-length", 10000)           // 队列最大长度
                .withArgument("x-overflow", "reject-publish")  // 溢出策略：拒绝新消息
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SIMULATION_DATA_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue aiDecisionQueue() {
        return QueueBuilder.durable(AI_DECISION_QUEUE).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    /**
     * 定义交换机（Topic Exchange）
     * Topic 类型支持通配符匹配路由键
     */
    @Bean
    public TopicExchange simulationExchange() {
        return ExchangeBuilder.topicExchange(SIMULATION_EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange aiExchange() {
        return ExchangeBuilder.topicExchange(AI_EXCHANGE).durable(true).build();
    }

    /**
     * 绑定队列到交换机
     */
    @Bean
    public Binding simulationDataBinding() {
        return BindingBuilder.bind(simulationDataQueue())
                .to(simulationExchange())
                .with(SIMULATION_DATA_ROUTING_KEY);
    }

    @Bean
    public Binding aiDecisionBinding() {
        return BindingBuilder.bind(aiDecisionQueue())
                .to(aiExchange())
                .with(AI_DECISION_ROUTING_KEY);
    }

    /**
     * 消息转换器：将 Java 对象自动转换为 JSON
     *
     */
    @Bean
    public MessageConverter messageConverter() {
        // 可选：配置序列化特性
        // converter.setCreateMessageIds(true);
        return new JacksonJsonMessageConverter();
    }

    /**
     * 配置 RabbitTemplate
     * 设置消息转换器和发布确认回调
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());

        // 设置发布确认回调（确保消息发送成功）
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("RabbitMQ 消息发送失败: {}", cause);
            }
        });

        // 设置消息返回回调（当消息无法路由时触发）
        template.setReturnsCallback(returned -> {
            log.error("RabbitMQ 消息无法路由: exchange={}, routingKey={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText());
        });

        return template;
    }
}