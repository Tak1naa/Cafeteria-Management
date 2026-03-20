#pragma once

#include <optional>
#include <string>
#include <vector>

#include "simulation/simulation_types.h"

namespace cafeteria {

class DecisionClient {
public:
    explicit DecisionClient(const SimulationConfig& config);

    DecisionResult decide(
        const SimulationState& state,
        int newArrivals,
        const std::vector<int>& fallbackAllocation,
        int simTimeSec);

    bool sendRealtimeState(const SimulationState& state);

private:
    std::optional<std::string> postJson(const std::string& url, const std::string& payload) const;
    std::string buildStateJson(const SimulationState& state) const;
    std::string buildDecisionRequestJson(const SimulationState& state, int newArrivals) const;
    std::optional<std::vector<int>> parseAllocation(const std::string& responseBody, std::size_t expectedWindowCount) const;

    struct CachedDecision {
        int expireAtSimTimeSec = -1;
        int arrivals = -1;
        std::vector<int> allocation;
    };

    SimulationConfig config_;
    mutable CachedDecision cache_;
};

} // namespace cafeteria
