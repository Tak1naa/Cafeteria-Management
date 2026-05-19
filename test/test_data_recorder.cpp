#include "simulation/data_recorder.h"
#include <iostream>
#include <vector>

int main() {
    DataRecorder recorder("test_output.csv");

    // 第0步
    StepData step0;
    step0.simTime = 0;
    step0.queueLengths = {0, 0};
    step0.windowCount = 2;
    step0.availableSeats = 50;
    step0.waitingForSeat = 0;
    step0.totalArrived = 2;
    step0.totalServed = 0;
    step0.totalSeated = 0;
    step0.totalFinishedDining = 0;
    step0.newArrivals = 2;
    step0.exitCount = 0;
    step0.arrivedStudentIds = {1, 2};
    step0.servedStudentIds = {};
    recorder.recordStep(step0);

    // 第5步
    StepData step5;
    step5.simTime = 5;
    step5.queueLengths = {1, 0};
    step5.windowCount = 2;
    step5.availableSeats = 50;
    step5.waitingForSeat = 0;
    step5.totalArrived = 3;
    step5.totalServed = 1;
    step5.totalSeated = 1;
    step5.totalFinishedDining = 0;
    step5.newArrivals = 1;
    step5.exitCount = 0;
    step5.arrivedStudentIds = {3};
    step5.servedStudentIds = {1};
    recorder.recordStep(step5);

    // 第8步
    StepData step8;
    step8.simTime = 8;
    step8.queueLengths = {1, 0};
    step8.windowCount = 2;
    step8.availableSeats = 50;
    step8.waitingForSeat = 0;
    step8.totalArrived = 3;
    step8.totalServed = 2;
    step8.totalSeated = 2;
    step8.totalFinishedDining = 0;
    step8.newArrivals = 0;
    step8.exitCount = 0;
    step8.arrivedStudentIds = {};
    step8.servedStudentIds = {2};
    recorder.recordStep(step8);

    // 第12步
    StepData step12;
    step12.simTime = 12;
    step12.queueLengths = {1, 0};
    step12.windowCount = 2;
    step12.availableSeats = 50;
    step12.waitingForSeat = 0;
    step12.totalArrived = 4;
    step12.totalServed = 3;
    step12.totalSeated = 3;
    step12.totalFinishedDining = 0;
    step12.newArrivals = 1;
    step12.exitCount = 0;
    step12.arrivedStudentIds = {4};
    step12.servedStudentIds = {3};
    recorder.recordStep(step12);

    recorder.finalize();
    return 0;
}