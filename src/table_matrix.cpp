#include "simulation/table_matrix.h"

#include <algorithm>

namespace cafeteria {

TableMatrix::TableMatrix(int rows, int cols)
    : rows_(std::max(1, rows)), cols_(std::max(1, cols)), seatRemainingSeconds_(static_cast<std::size_t>(std::max(1, rows) * std::max(1, cols)), 0) {}

int TableMatrix::tick() {
    int finishedDining = 0;
    for (int& remaining : seatRemainingSeconds_) {
        if (remaining > 0) {
            --remaining;
            if (remaining <= 0) {
                remaining = 0;
                ++finishedDining;
            }
        }
    }
    return finishedDining;
}

int TableMatrix::occupy(int people, std::mt19937& rng, int avgEatTimeSec) {
    if (people <= 0) {
        return 0;
    }

    int seated = 0;
    for (int& remaining : seatRemainingSeconds_) {
        if (remaining == 0) {
            remaining = sampleEatDuration(rng, avgEatTimeSec);
            ++seated;
            if (seated >= people) {
                break;
            }
        }
    }

    return seated;
}

int TableMatrix::availableSeats() const {
    int available = 0;
    for (const int remaining : seatRemainingSeconds_) {
        if (remaining == 0) {
            ++available;
        }
    }
    return available;
}

int TableMatrix::capacity() const {
    return rows_ * cols_;
}

int TableMatrix::sampleEatDuration(std::mt19937& rng, int avgEatTimeSec) {
    const int safeAvg = std::max(1, avgEatTimeSec);
    std::poisson_distribution<int> dist(safeAvg);
    return std::max(1, dist(rng));
}

} // namespace cafeteria
