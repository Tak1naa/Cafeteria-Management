#include <iostream>
#include <memory>
#include <string>

#include "simulation/data_recorder.h"
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

        std::unique_ptr<DataRecorder> recorder;
        if (config.stepRecordToFile) {
            recorder = std::make_unique<DataRecorder>(config.stepRecordFilePath);
            engine.setStepRecorder([&recorder](const cafeteria::StepData& data) {
                recorder->recordStep(data);
            });
            std::cout << "Step recorder enabled: " << config.stepRecordFilePath << "\n";
        }

        engine.run();

        if (recorder) {
            recorder->finalize();
        }
        return 0;
    } catch (const std::exception& ex) {
        std::cerr << "Simulation failed: " << ex.what() << "\n";
        return 1;
    }
}
