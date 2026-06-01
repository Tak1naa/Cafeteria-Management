#pragma once

#include <cstdio>
#include <string>
#include <unordered_map>
#include <vector>

#include "simulation/simulation_data.h"

class DataRecorder {
public:
    explicit DataRecorder(const std::string& csvPath);
    ~DataRecorder();

    void recordStep(const cafeteria::StepData& data);
    void finalize();

private:
    struct WaitSample {
        int arrivalTime;
        int startServiceTime;
    };

    void writeHeader();
    void writeRow(const cafeteria::StepData& data);
    void computePercentiles(int& p50, int& p90, int& p99);

    std::string csvPath_;
    FILE* csvFile_ = nullptr;

    std::unordered_map<int, WaitSample> studentWaitMap_;
    std::vector<int> waitTimes_;
    long long totalWaitTime_ = 0;
    int maxWaitTime_ = 0;
    int nextStudentId_ = 0;
};
