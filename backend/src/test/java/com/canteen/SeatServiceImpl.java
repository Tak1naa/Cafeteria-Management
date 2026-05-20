package com.canteen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class SeatServiceImpl implements SeatService {

    private final CppSeatClient cppSeatClient;

    public SeatServiceImpl(CppSeatClient cppSeatClient) {
        this.cppSeatClient = cppSeatClient;
    }


    @Override
    public void processConfig(SeatConfigRequest request) {
        // 默认值处理
        int windowCount = request.getWindowCount() != null ? request.getWindowCount() : 10;
        int chairCount = request.getChairCount() != null ? request.getChairCount() : 50;

        double distanceWeight = request.getDistanceWeight() != null ? request.getDistanceWeight() : 0.6;
        double queueRandomWeight = request.getQueueRandomWeight() != null ? request.getQueueRandomWeight() : 0.2;
        double seatRandomWeight = request.getSeatRandomWeight() != null ? request.getSeatRandomWeight() : 0.2;

        // 每个权重的取值范围限定为 [0, 1]
        distanceWeight = clampWeight(distanceWeight, "distanceWeight");
        queueRandomWeight = clampWeight(queueRandomWeight, "queueRandomWeight");
        seatRandomWeight = clampWeight(seatRandomWeight, "seatRandomWeight");


        // 2. 可选：将完整配置持久化到数据库（省略具体代码）
        // seatConfigRepository.save(...)

        // 构建需要发送给 C++ 后端的完整对象
        SeatConfigDTO fullConfig = new SeatConfigDTO();
        fullConfig.setWindowCount(windowCount);
        fullConfig.setChairCount(chairCount);
        fullConfig.setDistanceWeight(distanceWeight);
        fullConfig.setQueueRandomWeight(queueRandomWeight);
        fullConfig.setSeatRandomWeight(seatRandomWeight);

        // 调用 C++ 客户端接口发送配置 加入时间戳
        cppSeatClient.sendConfig(fullConfig);

        log.info("配置处理完成，已发送至 C++ 后端:  {} @ {}", fullConfig, LocalDateTime.now());
    }

    //验证权重范围
    private double clampWeight(double value, String name) {
        if (value < 0) {
            log.warn("权重 {} 原始值为 {}，小于0，已设置为0", name, value);
            return 0;
        }
        if (value > 1) {
            log.warn("权重 {} 原始值为 {}，大于1，已设置为1", name, value);
            return 1;
        }
        return value;
    }
}