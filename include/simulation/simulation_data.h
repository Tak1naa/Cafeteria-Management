#pragma once

#include <vector>

namespace cafeteria {

struct StepData {
    int simTime = 0;
    std::vector<int> queueLengths;
    int availableSeats = 0;
    int waitingForSeat = 0;
    long long totalQueueWaitSec = 0;
    double avgQueueWaitSec = 0.0;
    int maxQueueWaitSec = 0;
    double p50QueueWaitSec = 0.0;
    double p90QueueWaitSec = 0.0;
    double p99QueueWaitSec = 0.0;
    int totalServed = 0;
    int totalArrived = 0;
    int newArrivals = 0;
};

} // namespace cafeteria
