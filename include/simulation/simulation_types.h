#pragma once

#include <string>
#include <vector>

namespace cafeteria {

enum class DecisionMode {
    Ai,
    RuleFallback
};

struct SimulationConfig {
    int windowCount = 4;
    int tableRows = 5;
    int tableCols = 10;
    double arrivalRatePerTick = 3.0;
    int avgServiceTimeSec = 8;
    int avgEatTimeSec = 15;
    int tickSeconds = 1;
    int totalTicks = 300;
    int reportEveryTicks = 1;
    int aiCacheSeconds = 5;
    bool aiEnabled = true;
    std::string backendBaseUrl = "http://127.0.0.1:8080";
    int randomSeed = -1;
    bool stepRecordToFile = false;
    std::string stepRecordFilePath = "build/step_data.csv";
};

struct SimulationState {
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
    int totalArrived = 0;
    int totalServed = 0;
    int totalSeated = 0;
    int totalFinishedDining = 0;
    int newArrivals = 0;
};

struct DecisionResult {
    std::vector<int> allocation;
    DecisionMode mode = DecisionMode::RuleFallback;
    bool fromCache = false;
    std::string reason;
};

} // namespace cafeteria
