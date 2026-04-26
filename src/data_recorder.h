// data_recorder.h
#pragma once
#include <vector>
#include <string>
#include <unordered_map>
#include <cstdio>

struct StepData {//数据包裹，将B同学每步传来的参数打包成一个结构体。
    int simTime;
    std::vector<int> queueLengths;
    int windowCount;          // 可略，因为 queueLengths.size() 就是
    int availableSeats;
    int waitingForSeat;
    int totalArrived;
    int totalServed;
    int totalSeated;
    int totalFinishedDining;
    int newArrivals;

    // 扩展分析字段（可选，提供默认空值）
    int exitCount = 0;
    std::vector<int> arrivedStudentIds;
    std::vector<int> servedStudentIds;
};

class DataRecorder {
public:
    explicit DataRecorder(const std::string& csvPath);
    ~DataRecorder();

    void recordStep(const StepData& data);
    void finalize();

private:
    std::string csvPath_;
    FILE* csvFile_ = nullptr;

    // 用于等待时间计算
    std::unordered_map<int, int> arrivalTimeMap_;  // studentId -> arrivalTime
    std::vector<int> waitTimes_;                   // 所有已完成的等待时间（秒）
    long long totalWaitTime_ = 0;
    int maxWaitTime_ = 0;

    void writeHeader();
    void writeRow(const StepData& data, long long totalWait, double avgWait,
                  int maxWait, int p50, int p90, int p99);
    void computePercentiles(int& p50, int& p90, int& p99);
};