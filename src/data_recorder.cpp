// data_recorder.cpp
#include "simulation/data_recorder.h"
#include <spdlog/spdlog.h>
#include <algorithm>
#include <cmath>
//构造函数
DataRecorder::DataRecorder(const std::string& csvPath) : csvPath_(csvPath) {
    csvFile_ = fopen(csvPath_.c_str(), "w");
    if (!csvFile_) {
        spdlog::error("无法创建 CSV 文件: {}", csvPath_);
        return;
    }
    writeHeader();
    spdlog::info("DataRecorder 初始化完成，CSV: {}", csvPath_);
}

DataRecorder::~DataRecorder() {
    if (csvFile_) fclose(csvFile_);
}
//写入CSV表头
void DataRecorder::writeHeader() {
    // 列名：时间、队列、窗口数、空座位、等座人数、累计到达、累计服务、累计入座、累计离座、本步新到、本步离开、
    //       累计等待总时间、平均等待、最大等待、P50、P90、P99
    fprintf(csvFile_,
        "simTime,queueLengths,windowCount,availableSeats,waitingForSeat,"
        "totalArrived,totalServed,totalSeated,totalFinishedDining,newArrivals,exitCount,"
        "totalWaitSec,avgWaitSec,maxWaitSec,p50WaitSec,p90WaitSec,p99WaitSec\n");
}
//初始化p50,p90,p99
void DataRecorder::computePercentiles(int& p50, int& p90, int& p99) {
    if (waitTimes_.empty()) {
        p50 = p90 = p99 = 0;
        return;
    }
    std::vector<int> sorted = waitTimes_;
    std::sort(sorted.begin(), sorted.end());
    auto percentile = [&](double p) -> int {
        double pos = p * (sorted.size() - 1);
        size_t idx = static_cast<size_t>(std::floor(pos));
        double frac = pos - idx;
        if (idx + 1 < sorted.size()) {
            return static_cast<int>(sorted[idx] * (1 - frac) + sorted[idx + 1] * frac + 0.5);
        } else {
            return sorted[idx];
        }
    };
    p50 = percentile(0.5);
    p90 = percentile(0.9);
    p99 = percentile(0.99);
}
//写入CSV一行
void DataRecorder::writeRow(const StepData& data, long long totalWait, double avgWait,
                            int maxWait, int p50, int p90, int p99) {
    // 将 queueLengths 转为字符串，如 "[3,2,1]"
    std::string qlStr = "[";
    for (size_t i = 0; i < data.queueLengths.size(); ++i) {
        qlStr += std::to_string(data.queueLengths[i]);
        if (i != data.queueLengths.size() - 1) qlStr += ",";
    }
    qlStr += "]";

    fprintf(csvFile_,
        "%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%lld,%.2f,%d,%d,%d,%d\n",
        data.simTime, qlStr.c_str(), data.windowCount,
        data.availableSeats, data.waitingForSeat,
        data.totalArrived, data.totalServed, data.totalSeated,
        data.totalFinishedDining, data.newArrivals, data.exitCount,
        totalWait, avgWait, maxWait, p50, p90, p99);
    fflush(csvFile_);
}

void DataRecorder::recordStep(const StepData& data) {
    // 1. 记录新到达学生的到达时间
    for (int id : data.arrivedStudentIds) {
        arrivalTimeMap_[id] = data.simTime;
    }

    // 2. 处理开始服务的学生：计算等待时间
    for (int id : data.servedStudentIds) {
        auto it = arrivalTimeMap_.find(id);
        if (it != arrivalTimeMap_.end()) {
            int wait = data.simTime - it->second;
            waitTimes_.push_back(wait);
            totalWaitTime_ += wait;
            if (wait > maxWaitTime_) maxWaitTime_ = wait;
            arrivalTimeMap_.erase(it);
        } else {
            spdlog::warn("学生 {} 开始服务但未找到到达记录", id);
        }
    }

    // 3. 计算当前分位数
    int p50 = 0, p90 = 0, p99 = 0;
    computePercentiles(p50, p90, p99);

    // 4. 计算平均等待时间
    double avgWait = waitTimes_.empty() ? 0.0 : static_cast<double>(totalWaitTime_) / waitTimes_.size();

    // 5. 写入 CSV 一行
    writeRow(data, totalWaitTime_, avgWait, maxWaitTime_, p50, p90, p99);
}

void DataRecorder::finalize() {
    // 输出最终统计报告
    spdlog::info("========== 最终等待时间统计 ==========");
    spdlog::info("总等待人数: {}", waitTimes_.size());
    spdlog::info("总等待时间(秒): {}", totalWaitTime_);
    spdlog::info("平均等待时间(秒): {:.2f}", waitTimes_.empty() ? 0 : totalWaitTime_ / (double)waitTimes_.size());
    spdlog::info("最大等待时间(秒): {}", maxWaitTime_);
    int p50, p90, p99;
    computePercentiles(p50, p90, p99);
    spdlog::info("P50: {}秒, P90: {}秒, P99: {}秒", p50, p90, p99);

    // 可选：将最终统计写入单独文件
    FILE* report = fopen("build/final_stats.txt", "w");
    if (report) {
        fprintf(report, "total_wait_count=%zu\n", waitTimes_.size());
        fprintf(report, "total_wait_seconds=%lld\n", totalWaitTime_);
        fprintf(report, "avg_wait_seconds=%.2f\n", waitTimes_.empty() ? 0 : totalWaitTime_ / (double)waitTimes_.size());
        fprintf(report, "max_wait_seconds=%d\n", maxWaitTime_);
        fprintf(report, "p50_wait_seconds=%d\n", p50);
        fprintf(report, "p90_wait_seconds=%d\n", p90);
        fprintf(report, "p99_wait_seconds=%d\n", p99);
        fclose(report);
    }
    spdlog::info("最终统计已写入 build/final_stats.txt");
}