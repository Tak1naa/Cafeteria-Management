#pragma once

#include <queue>
#include <random>
#include <vector>

namespace cafeteria {

struct WaitingCustomer {
    int serviceDurationSec = 0;
    int enqueueTimeSec = 0;
};

class WindowQueue {
public:
    WindowQueue();

    void enqueue(int people, std::mt19937& rng, int avgServiceTimeSec, int currentSimTimeSec);
    void prepopulate(int count, std::mt19937& rng, int avgServiceTimeSec);
    int tick(int currentSimTimeSec);
    int queueLength() const;
    int totalServed() const;
    int totalStartedService() const;
    long long totalQueueWaitSec() const;
    int maxQueueWaitSec() const;
    double averageQueueWaitSec() const;
    const std::vector<int>& queueWaitSamples() const;

private:
    static int sampleServiceDuration(std::mt19937& rng, int avgServiceTimeSec);

    std::queue<WaitingCustomer> waitingCustomers_;
    bool serving_;
    int currentServiceRemaining_;
    int totalServed_;
    int totalStartedService_;
    long long totalQueueWaitSec_;
    int maxQueueWaitSec_;
    std::vector<int> queueWaitSamples_;
};

} // namespace cafeteria
