#include <iostream>
#include <vector>

#include "simulation/data_recorder.h"

int main() {
    DataRecorder recorder("test_output.csv");

    cafeteria::StepData step;
    step.simTime = 0;
    step.queueLengths = {0, 0};
    step.windowCount = 2;
    step.availableSeats = 50;
    step.waitingForSeat = 0;
    step.totalArrived = 2;
    step.totalServed = 0;
    step.totalSeated = 0;
    step.totalFinishedDining = 0;
    step.newArrivals = 2;
    recorder.recordStep(step);

    step.simTime = 5;
    step.queueLengths = {1, 0};
    step.waitingForSeat = 0;
    step.totalArrived = 3;
    step.totalServed = 1;
    step.totalSeated = 1;
    step.newArrivals = 1;
    recorder.recordStep(step);

    step.simTime = 8;
    step.queueLengths = {1, 0};
    step.totalServed = 2;
    step.totalSeated = 2;
    step.newArrivals = 0;
    recorder.recordStep(step);

    step.simTime = 12;
    step.queueLengths = {1, 0};
    step.totalArrived = 4;
    step.totalServed = 3;
    step.totalSeated = 3;
    step.newArrivals = 1;
    recorder.recordStep(step);

    recorder.finalize();
    std::cout << "test_data_recorder: all steps recorded.\n";
    return 0;
}
