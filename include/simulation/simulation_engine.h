#pragma once

#include <functional>
#include <random>
#include <string>
#include <vector>

#include "simulation/decision_client.h"
#include "simulation/person_generator.h"
#include "simulation/simulation_data.h"
#include "simulation/simulation_types.h"
#include "simulation/table_matrix.h"
#include "simulation/window_queue.h"

namespace cafeteria {

class SimulationEngine {
public:
    explicit SimulationEngine(SimulationConfig config);

    static SimulationConfig loadConfig(const std::string& configPath);
    void setStepRecorder(std::function<void(const StepData&)> recorder);
    void run();

private:
    void step();
    SimulationState currentState(int newArrivals) const;
    std::vector<int> ruleBasedAllocation(int newArrivals) const;
    bool validateAllocation(const std::vector<int>& allocation, int newArrivals) const;
    void applyAllocation(const std::vector<int>& allocation, int simTimeSec);

    SimulationConfig config_;
    PersonGenerator generator_;
    std::vector<WindowQueue> windows_;
    TableMatrix tables_;
    DecisionClient decisionClient_;
    mutable std::mt19937 rng_;

    int tick_;
    int waitingForSeat_;
    int totalArrived_;
    int totalServed_;
    int totalSeated_;
    int totalFinishedDining_;
    std::function<void(const StepData&)> stepRecorder_;
};

} // namespace cafeteria
