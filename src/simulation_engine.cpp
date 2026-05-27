#include "simulation/simulation_engine.h"

#include <algorithm>
#include <fstream>
#include <iostream>
#include <regex>
#include <sstream>
#include <utility>
#include <vector>

#if __has_include(<nlohmann/json.hpp>)
#include <nlohmann/json.hpp>
#define CAFETERIA_HAS_NLOHMANN_JSON 1
#endif

namespace cafeteria {

namespace {

int parseIntWithDefault(const std::string& content, const std::string& key, int fallback) {
    const std::regex pattern("\"" + key + "\"\\s*:\\s*(-?\\d+)");
    std::smatch match;
    if (std::regex_search(content, match, pattern) && match.size() >= 2) {
        try {
            return std::stoi(match[1].str());
        } catch (...) {
            return fallback;
        }
    }
    return fallback;
}

double parseDoubleWithDefault(const std::string& content, const std::string& key, double fallback) {
    const std::regex pattern("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    std::smatch match;
    if (std::regex_search(content, match, pattern) && match.size() >= 2) {
        try {
            return std::stod(match[1].str());
        } catch (...) {
            return fallback;
        }
    }
    return fallback;
}

bool parseBoolWithDefault(const std::string& content, const std::string& key, bool fallback) {
    const std::regex pattern("\"" + key + "\"\\s*:\\s*(true|false)", std::regex_constants::icase);
    std::smatch match;
    if (std::regex_search(content, match, pattern) && match.size() >= 2) {
        const std::string token = match[1].str();
        return token == "true" || token == "TRUE" || token == "True";
    }
    return fallback;
}

std::string parseStringWithDefault(const std::string& content, const std::string& key, const std::string& fallback) {
    const std::regex pattern("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
    std::smatch match;
    if (std::regex_search(content, match, pattern) && match.size() >= 2) {
        return match[1].str();
    }
    return fallback;
}

std::string decisionModeText(DecisionMode mode) {
    return mode == DecisionMode::Ai ? "AI" : "RULE";
}

double calculatePercentile(const std::vector<int>& samples, double percentile) {
    if (samples.empty()) {
        return 0.0;
    }

    std::vector<int> sorted = samples;
    std::sort(sorted.begin(), sorted.end());

    const double rank = (percentile / 100.0) * static_cast<double>(sorted.size() - 1);
    const std::size_t lower = static_cast<std::size_t>(rank);
    const std::size_t upper = std::min(lower + 1, sorted.size() - 1);
    const double weight = rank - static_cast<double>(lower);

    return static_cast<double>(sorted[lower]) +
           weight * static_cast<double>(sorted[upper] - sorted[lower]);
}

} // namespace

SimulationEngine::SimulationEngine(SimulationConfig config)
    : config_(std::move(config)),
      generator_(
          config_.arrivalRatePerTick,
          config_.randomSeed >= 0 ? static_cast<unsigned int>(config_.randomSeed) : std::random_device{}()),
      windows_(static_cast<std::size_t>(std::max(1, config_.windowCount))),
      tables_(config_.tableRows, config_.tableCols),
      decisionClient_(config_),
      rng_(
          config_.randomSeed >= 0 ? (static_cast<unsigned int>(config_.randomSeed) + 1u) : std::random_device{}()),
      tick_(0),
      waitingForSeat_(0),
      totalArrived_(0),
      totalServed_(0),
      totalSeated_(0),
    totalFinishedDining_(0),
    stepRecorder_(nullptr) {
    // 应用初始状态
    if (!config_.initialQueueLengths.empty()) {
        for (std::size_t i = 0; i < config_.initialQueueLengths.size() && i < windows_.size(); ++i) {
            if (config_.initialQueueLengths[i] > 0) {
                windows_[i].prepopulate(config_.initialQueueLengths[i], rng_, config_.avgServiceTimeSec);
            }
        }
    }
    if (config_.initialOccupiedSeats > 0) {
        tables_.preoccupy(config_.initialOccupiedSeats, rng_, config_.avgEatTimeSec);
    }
}

void SimulationEngine::setStepRecorder(std::function<void(const StepData&)> recorder) {
    stepRecorder_ = std::move(recorder);
}

SimulationConfig SimulationEngine::loadConfig(const std::string& configPath) {
    SimulationConfig config;

    std::ifstream input(configPath);
    if (!input.is_open()) {
        return config;
    }

    std::stringstream buffer;
    buffer << input.rdbuf();
    const std::string content = buffer.str();

#ifdef CAFETERIA_HAS_NLOHMANN_JSON
    try {
        const auto json = nlohmann::json::parse(content);
        config.windowCount = json.value("windowCount", config.windowCount);
        config.tableRows = json.value("tableRows", config.tableRows);
        config.tableCols = json.value("tableCols", config.tableCols);
        config.arrivalRatePerTick = json.value("arrivalRatePerTick", config.arrivalRatePerTick);
        config.avgServiceTimeSec = json.value("avgServiceTimeSec", config.avgServiceTimeSec);
        config.avgEatTimeSec = json.value("avgEatTimeSec", config.avgEatTimeSec);
        config.tickSeconds = json.value("tickSeconds", config.tickSeconds);
        config.totalTicks = json.value("totalTicks", config.totalTicks);
        config.reportEveryTicks = json.value("reportEveryTicks", config.reportEveryTicks);
        config.aiCacheSeconds = json.value("aiCacheSeconds", config.aiCacheSeconds);
        config.aiEnabled = json.value("aiEnabled", config.aiEnabled);
        config.backendBaseUrl = json.value("backendBaseUrl", config.backendBaseUrl);
        config.apiKey = json.value("apiKey", config.apiKey);
        config.randomSeed = json.value("randomSeed", config.randomSeed);
        config.stepRecordToFile = json.value("stepRecordToFile", config.stepRecordToFile);
        config.stepRecordFilePath = json.value("stepRecordFilePath", config.stepRecordFilePath);
        if (json.contains("initialQueueLengths") && json["initialQueueLengths"].is_array()) {
            config.initialQueueLengths = json["initialQueueLengths"].get<std::vector<int>>();
        }
        config.initialOccupiedSeats = json.value("initialOccupiedSeats", config.initialOccupiedSeats);
        return config;
    } catch (...) {
    }
#endif

    config.windowCount = parseIntWithDefault(content, "windowCount", config.windowCount);
    config.tableRows = parseIntWithDefault(content, "tableRows", config.tableRows);
    config.tableCols = parseIntWithDefault(content, "tableCols", config.tableCols);
    config.arrivalRatePerTick = parseDoubleWithDefault(content, "arrivalRatePerTick", config.arrivalRatePerTick);
    config.avgServiceTimeSec = parseIntWithDefault(content, "avgServiceTimeSec", config.avgServiceTimeSec);
    config.avgEatTimeSec = parseIntWithDefault(content, "avgEatTimeSec", config.avgEatTimeSec);
    config.tickSeconds = parseIntWithDefault(content, "tickSeconds", config.tickSeconds);
    config.totalTicks = parseIntWithDefault(content, "totalTicks", config.totalTicks);
    config.reportEveryTicks = parseIntWithDefault(content, "reportEveryTicks", config.reportEveryTicks);
    config.aiCacheSeconds = parseIntWithDefault(content, "aiCacheSeconds", config.aiCacheSeconds);
    config.aiEnabled = parseBoolWithDefault(content, "aiEnabled", config.aiEnabled);
    config.backendBaseUrl = parseStringWithDefault(content, "backendBaseUrl", config.backendBaseUrl);
    config.apiKey = parseStringWithDefault(content, "apiKey", config.apiKey);
    config.randomSeed = parseIntWithDefault(content, "randomSeed", config.randomSeed);
    config.stepRecordToFile = parseBoolWithDefault(content, "stepRecordToFile", config.stepRecordToFile);
    config.stepRecordFilePath = parseStringWithDefault(content, "stepRecordFilePath", config.stepRecordFilePath);
    config.initialOccupiedSeats = parseIntWithDefault(content, "initialOccupiedSeats", config.initialOccupiedSeats);
    return config;
}

void SimulationEngine::run() {
    for (int i = 0; i < std::max(1, config_.totalTicks); ++i) {
        step();
    }

    const auto finalState = currentState(0);
    std::cout << "\n=== Simulation Summary ===\n";
    std::cout << "Total arrived: " << totalArrived_ << "\n";
    std::cout << "Total served: " << totalServed_ << "\n";
    std::cout << "Total seated: " << totalSeated_ << "\n";
    std::cout << "Total finished dining: " << totalFinishedDining_ << "\n";
    std::cout << "Total queue wait (sec): " << finalState.totalQueueWaitSec << "\n";
    std::cout << "Average queue wait (sec): " << finalState.avgQueueWaitSec << "\n";
    std::cout << "Max queue wait (sec): " << finalState.maxQueueWaitSec << "\n";
    std::cout << "P50 queue wait (sec): " << finalState.p50QueueWaitSec << "\n";
    std::cout << "P90 queue wait (sec): " << finalState.p90QueueWaitSec << "\n";
    std::cout << "P99 queue wait (sec): " << finalState.p99QueueWaitSec << "\n";
    std::cout << "Waiting for seat at end: " << waitingForSeat_ << "\n";
}

void SimulationEngine::step() {
    const int simTimeSec = tick_ * std::max(1, config_.tickSeconds);
    const int newArrivals = generator_.generateArrivals();
    const auto fallback = ruleBasedAllocation(newArrivals);

    const auto beforeState = currentState(newArrivals);
    DecisionResult decision = decisionClient_.decide(beforeState, newArrivals, fallback, simTimeSec);
    if (!validateAllocation(decision.allocation, newArrivals)) {
        decision.allocation = fallback;
        decision.mode = DecisionMode::RuleFallback;
        decision.reason = "invalid_allocation_sanitized";
        decision.fromCache = false;
    }

    applyAllocation(decision.allocation, simTimeSec);
    totalArrived_ += newArrivals;

    int servedThisTick = 0;
    for (auto& window : windows_) {
        servedThisTick += window.tick(simTimeSec);
    }

    waitingForSeat_ += servedThisTick;
    totalServed_ += servedThisTick;

    const int finishedDining = tables_.tick();
    totalFinishedDining_ += finishedDining;

    const int newlySeated = tables_.occupy(waitingForSeat_, rng_, config_.avgEatTimeSec);
    waitingForSeat_ -= newlySeated;
    totalSeated_ += newlySeated;

    const auto state = currentState(newArrivals);
    if (stepRecorder_) {
        StepData data;
        data.simTime = state.simTime;
        data.queueLengths = state.queueLengths;
        data.availableSeats = state.availableSeats;
        data.waitingForSeat = state.waitingForSeat;
        data.totalQueueWaitSec = state.totalQueueWaitSec;
        data.avgQueueWaitSec = state.avgQueueWaitSec;
        data.maxQueueWaitSec = state.maxQueueWaitSec;
        data.p50QueueWaitSec = state.p50QueueWaitSec;
        data.p90QueueWaitSec = state.p90QueueWaitSec;
        data.p99QueueWaitSec = state.p99QueueWaitSec;
        data.totalServed = state.totalServed;
        data.totalArrived = state.totalArrived;
        data.newArrivals = state.newArrivals;
        stepRecorder_(data);
    }

    if (tick_ % std::max(1, config_.reportEveryTicks) == 0) {
        decisionClient_.sendRealtimeState(state);

        std::cout << "t=" << state.simTime
                  << " arrivals=" << newArrivals
                  << " served=" << servedThisTick
                  << " seatsFree=" << state.availableSeats
                  << " waitingSeat=" << state.waitingForSeat
                  << " avgQueueWait=" << state.avgQueueWaitSec
                  << " maxQueueWait=" << state.maxQueueWaitSec
                  << " p90QueueWait=" << state.p90QueueWaitSec
                  << " mode=" << decisionModeText(decision.mode)
                  << " reason=" << decision.reason
                  << (decision.fromCache ? "(cache)" : "")
                  << "\n";
    }

    ++tick_;
}

SimulationState SimulationEngine::currentState(int newArrivals) const {
    SimulationState state;
    state.simTime = tick_ * std::max(1, config_.tickSeconds);
    state.queueLengths.reserve(windows_.size());
    long long totalQueueWaitSec = 0;
    int maxQueueWaitSec = 0;
    int totalStartedService = 0;
    std::vector<int> allQueueWaitSamples;
    for (const auto& window : windows_) {
        state.queueLengths.push_back(window.queueLength());
        totalQueueWaitSec += window.totalQueueWaitSec();
        maxQueueWaitSec = std::max(maxQueueWaitSec, window.maxQueueWaitSec());
        totalStartedService += window.totalStartedService();

        const auto& samples = window.queueWaitSamples();
        allQueueWaitSamples.insert(allQueueWaitSamples.end(), samples.begin(), samples.end());
    }
    state.availableSeats = tables_.availableSeats();
    state.waitingForSeat = waitingForSeat_;
    state.totalQueueWaitSec = totalQueueWaitSec;
    state.maxQueueWaitSec = maxQueueWaitSec;
    state.avgQueueWaitSec = totalStartedService > 0
                                ? static_cast<double>(totalQueueWaitSec) / static_cast<double>(totalStartedService)
                                : 0.0;
    state.p50QueueWaitSec = calculatePercentile(allQueueWaitSamples, 50.0);
    state.p90QueueWaitSec = calculatePercentile(allQueueWaitSamples, 90.0);
    state.p99QueueWaitSec = calculatePercentile(allQueueWaitSamples, 99.0);
    state.totalArrived = totalArrived_;
    state.totalServed = totalServed_;
    state.totalSeated = totalSeated_;
    state.totalFinishedDining = totalFinishedDining_;
    state.newArrivals = newArrivals;
    return state;
}

std::vector<int> SimulationEngine::ruleBasedAllocation(int newArrivals) const {
    std::vector<int> allocation(windows_.size(), 0);
    if (newArrivals <= 0) {
        return allocation;
    }

    std::vector<int> projectedLengths;
    projectedLengths.reserve(windows_.size());
    for (const auto& window : windows_) {
        projectedLengths.push_back(window.queueLength());
    }

    for (int i = 0; i < newArrivals; ++i) {
        auto bestIt = std::min_element(projectedLengths.begin(), projectedLengths.end());
        const std::size_t index = static_cast<std::size_t>(std::distance(projectedLengths.begin(), bestIt));
        ++allocation[index];
        ++projectedLengths[index];
    }

    return allocation;
}

bool SimulationEngine::validateAllocation(const std::vector<int>& allocation, int newArrivals) const {
    if (allocation.size() != windows_.size()) {
        return false;
    }
    int total = 0;
    for (const int value : allocation) {
        if (value < 0) {
            return false;
        }
        total += value;
    }
    return total == newArrivals;
}

void SimulationEngine::applyAllocation(const std::vector<int>& allocation, int simTimeSec) {
    for (std::size_t i = 0; i < windows_.size() && i < allocation.size(); ++i) {
        windows_[i].enqueue(allocation[i], rng_, config_.avgServiceTimeSec, simTimeSec);
    }
}

} // namespace cafeteria
