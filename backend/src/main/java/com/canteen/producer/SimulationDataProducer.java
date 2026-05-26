package com.canteen.producer;

import com.canteen.config.RabbitMQConfig;
import com.canteen.dto.SimulationDataRequest;
import com.canteen.service.SimulationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SimulationDataProducer {

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private SimulationService simulationService;

    public void sendSimulationData(SimulationDataRequest request) {
        if (rabbitTemplate == null) {
            log.debug("RabbitMQ 未启用，跳过入队: simTime={}", request.getSimTime());
            return;
        }

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SIMULATION_EXCHANGE,
                    RabbitMQConfig.SIMULATION_DATA_ROUTING_KEY,
                    request,
                    message -> {
                        message.getMessageProperties().setExpiration("30000");
                        message.getMessageProperties().setPriority(5);
                        message.getMessageProperties().setHeader("retry-count", 0);
                        return message;
                    }
            );
            log.debug("仿真数据已发送到消息队列: simTime={}", request.getSimTime());
        } catch (Exception e) {
            log.error("发送消息到队列失败，降级为同步处理", e);
            simulationService.processSimulationData(request);
        }
    }
}
