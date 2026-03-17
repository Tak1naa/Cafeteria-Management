#pragma once

#include <vector>
#include <random>

namespace cafeteria {

class TableMatrix {
public:
    TableMatrix(int rows, int cols);

    int tick();
    int occupy(int people, std::mt19937& rng, int avgEatTimeSec);
    int availableSeats() const;
    int capacity() const;

private:
    static int sampleEatDuration(std::mt19937& rng, int avgEatTimeSec);

    int rows_;
    int cols_;
    std::vector<int> seatRemainingSeconds_;
};

} // namespace cafeteria
