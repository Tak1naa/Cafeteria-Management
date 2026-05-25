package com.canteen.service;

import com.canteen.dto.SimulationDataRequest;
import com.canteen.dto.SimulationDataDTO;

/**
 * 仿真数据服务接口
 * 定义业务方法，具体的实现可以替换
 */
public interface SimulationService {

    /**
     * 处理仿真数据
     * @param request 仿真数据请求
     */
    void processSimulationData(SimulationDataRequest request);

    /**
     * 快速更新实时缓存（内存操作，不写数据库）
     * @param request 仿真数据请求
     */
    void updateRealtimeCache(SimulationDataRequest request);

    /**
     * 获取最新的仿真数据
     * @return 最新的仿真数据
     */
    SimulationDataDTO getLatestSimulationData();
}