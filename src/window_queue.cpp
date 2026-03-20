#include "simulation/window_queue.h"

#include <algorithm>

namespace cafeteria {

WindowQueue::WindowQueue()
    : serving_(false),
      currentServiceRemaining_(0),
      totalServed_(0),
      totalStartedService_(0),
      totalQueueWaitSec_(0),
    maxQueueWaitSec_(0),
    queueWaitSamples_() {}

void WindowQueue::enqueue(int people, std::mt19937& rng, int avgServiceTimeSec, int currentSimTimeSec) {
    for (int i = 0; i < people; ++i) {
        WaitingCustomer customer;
        customer.serviceDurationSec = sampleServiceDuration(rng, avgServiceTimeSec);
        customer.enqueueTimeSec = currentSimTimeSec;
        waitingCustomers_.push(customer);
    }
}

int WindowQueue::tick(int currentSimTimeSec) {
    if (!serving_ && !waitingCustomers_.empty()) {
        const WaitingCustomer nextCustomer = waitingCustomers_.front();
        waitingCustomers_.pop();

        currentServiceRemaining_ = nextCustomer.serviceDurationSec;
        const int waitSec = std::max(0, currentSimTimeSec - nextCustomer.enqueueTimeSec);
        totalQueueWaitSec_ += waitSec;
        maxQueueWaitSec_ = std::max(maxQueueWaitSec_, waitSec);
        queueWaitSamples_.push_back(waitSec);
        ++totalStartedService_;
        serving_ = true;
    }

    int completed = 0;
    if (serving_) {
        --currentServiceRemaining_;
        if (currentServiceRemaining_ <= 0) {
            serving_ = false;
            currentServiceRemaining_ = 0;
            ++completed;
            ++totalServed_;
        }
    }

    return completed;
}

int WindowQueue::queueLength() const {
    return static_cast<int>(waitingCustomers_.size()) + (serving_ ? 1 : 0);
}

int WindowQueue::totalServed() const {
    return totalServed_;
}

int WindowQueue::totalStartedService() const {
    return totalStartedService_;
}

long long WindowQueue::totalQueueWaitSec() const {
    return totalQueueWaitSec_;
}

int WindowQueue::maxQueueWaitSec() const {
    return maxQueueWaitSec_;
}

double WindowQueue::averageQueueWaitSec() const {
    if (totalStartedService_ <= 0) {
        return 0.0;
    }
    return static_cast<double>(totalQueueWaitSec_) / static_cast<double>(totalStartedService_);
}

const std::vector<int>& WindowQueue::queueWaitSamples() const {
    return queueWaitSamples_;
}

int WindowQueue::sampleServiceDuration(std::mt19937& rng, int avgServiceTimeSec) {
    const int safeAvg = std::max(1, avgServiceTimeSec);
    std::poisson_distribution<int> dist(safeAvg);
    return std::max(1, dist(rng));
}

} // namespace cafeteria
