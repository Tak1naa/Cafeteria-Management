#include <bits/stdc++.h>
struct StepData {
    int simTime;                     // 仿真秒数
    std::vector<int> queueLengths;   // 各窗口排队人数
    int availableSeats;              // 空座位数
    int waitingForSeat;              // 等座位的人数
    int totalArrived;                // 累计到达人数
    int totalServed;                 // 累计服务人数（从队列进入餐桌的人数）
    int totalSeated;                 // 累计坐上座位的人数
    int totalFinishedDining;         // 累计用餐完毕离开的人数
    int newArrivals;                 // 本步新到达人数
    // 以下为 C 需要额外从 B 获取的字段（用于计算等待时间）：
    std::vector<int> servedIds;      // 本步中开始被服务的学生ID（从队列进入服务台）
    std::vector<int> seatedIds;      // 本步中从服务台进入座位的学生ID（可选）
    std::vector<int> finishedIds;    // 本步中用餐完毕离开的学生ID
    // 更简单的办法：B 每步告知每个学生的状态变化，C 自行记录每个学生的“到达时间”、“开始服务时间”、“服务结束时间”
};