package com.canteen.client;

import com.canteen.dto.SeatConfigDTO;

public interface CppSeatClient {
    void sendConfig(SeatConfigDTO config);
}