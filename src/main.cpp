#include <fstream>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>

#include "simulation/simulation_engine.h"

int main(int argc, char* argv[]) {
   
    try {
        const std::string configPath = argc > 1 ? argv[1] : "config/default_config.json";
        const auto config = cafeteria::SimulationEngine::loadConfig(configPath);

        std::cout << "Cafeteria simulation start, config=" << configPath << "\n";
        if (config.randomSeed >= 0) {
            std::cout << "Deterministic random seed: " << config.randomSeed << "\n";
        }
        cafeteria::SimulationEngine engine(config);

        if (config.stepRecordToFile) {
            auto stepOutput = std::make_shared<std::ofstream>(config.stepRecordFilePath, std::ios::out | std::ios::trunc);
            if (!stepOutput->is_open()) {
                std::cerr << "Warning: failed to open step record file: " << config.stepRecordFilePath << "\n";
            } else {
                (*stepOutput) << "simTime,queueLengths,availableSeats,waitingForSeat,totalQueueWaitSec,avgQueueWaitSec,maxQueueWaitSec,p50QueueWaitSec,p90QueueWaitSec,p99QueueWaitSec,totalServed,totalArrived,newArrivals\n";

                engine.setStepRecorder([stepOutput](const cafeteria::StepData& data) {
                    std::ostringstream queueStream;
                    for (std::size_t i = 0; i < data.queueLengths.size(); ++i) {
                        if (i > 0) {
                            queueStream << "|";
                        }
                        queueStream << data.queueLengths[i];
                    }

                    (*stepOutput) << data.simTime << ","
                                  << '"' << queueStream.str() << '"' << ","
                                  << data.availableSeats << ","
                                  << data.waitingForSeat << ","
                                  << data.totalQueueWaitSec << ","
                                  << data.avgQueueWaitSec << ","
                                  << data.maxQueueWaitSec << ","
                                  << data.p50QueueWaitSec << ","
                                  << data.p90QueueWaitSec << ","
                                  << data.p99QueueWaitSec << ","
                                  << data.totalServed << ","
                                  << data.totalArrived << ","
                                  << data.newArrivals << "\n";
                });

                std::cout << "Step recorder enabled: " << config.stepRecordFilePath << "\n";
            }
        }

        engine.run();
        return 0;
    } catch (const std::exception& ex) {
        std::cerr << "Simulation failed: " << ex.what() << "\n";
        return 1;
    }
}
