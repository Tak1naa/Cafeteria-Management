#include "simulation/data_recorder.h"

#include <algorithm>
#include <cmath>
#include <iostream>

DataRecorder::DataRecorder(const std::string& csvPath) : csvPath_(csvPath) {
    csvFile_ = fopen(csvPath_.c_str(), "w");
    if (!csvFile_) {
        std::cerr << "DataRecorder: failed to create CSV file: " << csvPath_ << "\n";
        return;
    }
    writeHeader();
    std::cout << "DataRecorder initialized, CSV: " << csvPath_ << "\n";
}

DataRecorder::~DataRecorder() {
    if (csvFile_) {
        fclose(csvFile_);
    }
}

void DataRecorder::writeHeader() {
    if (!csvFile_) return;
    fprintf(csvFile_,
        "simTime,queueLengths,windowCount,availableSeats,waitingForSeat,"
        "totalArrived,totalServed,totalSeated,totalFinishedDining,newArrivals,"
        "totalQueueWaitSec,avgQueueWaitSec,maxQueueWaitSec,"
        "p50QueueWaitSec,p90QueueWaitSec,p99QueueWaitSec\n");
}

void DataRecorder::writeRow(const cafeteria::StepData& data) {
    if (!csvFile_) return;
    std::string qlStr = "[";
    for (size_t i = 0; i < data.queueLengths.size(); ++i) {
        qlStr += std::to_string(data.queueLengths[i]);
        if (i + 1 < data.queueLengths.size()) {
            qlStr += ",";
        }
    }
    qlStr += "]";

    fprintf(csvFile_,
        "%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,"
        "%lld,%.2f,%d,%.2f,%.2f,%.2f\n",
        data.simTime,
        qlStr.c_str(),
        data.windowCount,
        data.availableSeats,
        data.waitingForSeat,
        data.totalArrived,
        data.totalServed,
        data.totalSeated,
        data.totalFinishedDining,
        data.newArrivals,
        data.totalQueueWaitSec,
        data.avgQueueWaitSec,
        data.maxQueueWaitSec,
        data.p50QueueWaitSec,
        data.p90QueueWaitSec,
        data.p99QueueWaitSec);
    fflush(csvFile_);
}

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
        }
        return sorted[idx];
    };
    p50 = percentile(0.5);
    p90 = percentile(0.9);
    p99 = percentile(0.99);
}

void DataRecorder::recordStep(const cafeteria::StepData& data) {
    writeRow(data);
}

void DataRecorder::finalize() {
    if (!csvFile_) {
        return;
    }
    std::cout << "DataRecorder: CSV written to " << csvPath_ << "\n";
}
