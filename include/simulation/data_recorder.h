// data_recorder.h
#pragma once
#include <vector>
#include <string>
#include <unordered_map>
#include <cstdio>

struct StepData {//结构体,接收仿真引擎每步产生的数据，计算每个学生的排队等待时间，输出CSV文件和最终统计报告。
    int simTime;//仿真时间
    std::vector<int> queueLengths;//每个串钩排队人数
    int windowCount;        //窗口数
    int availableSeats;//空座位数
    int waitingForSeat;//等待作为的人数
    int totalArrived;//总到达数
    int totalServed;//总服务数
    int totalSeated;//总座位数
    int totalFinishedDining;
    int newArrivals;//新到达人数

    // 扩展分析字段（可选，提供默认空值）
    int exitCount = 0;
    std::vector<int> arrivedStudentIds;//到达学生ID
    std::vector<int> servedStudentIds;//服务学生ID
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