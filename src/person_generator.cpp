#include "simulation/person_generator.h"

#include <algorithm>

namespace cafeteria {

PersonGenerator::PersonGenerator(double lambdaPerTick, unsigned int seed)
    : rng_(seed), distribution_(std::max(0.0, lambdaPerTick)) {}

int PersonGenerator::generateArrivals() {
    return distribution_(rng_);
}

} // namespace cafeteria
