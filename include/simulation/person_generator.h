#pragma once

#include <random>

namespace cafeteria {

class PersonGenerator {
public:
    explicit PersonGenerator(double lambdaPerTick, unsigned int seed = std::random_device{}());
    int generateArrivals();

private:
    std::mt19937 rng_;
    std::poisson_distribution<int> distribution_;
};

} // namespace cafeteria
