# Cafeteria B 模块最终交付一页式说明

更新日期：2026-03-17

## 1) 交付范围（开发者 B）

- 仿真核心：到达生成、窗口队列、餐桌占用释放、时间步推进
- 策略能力：规则分配 + AI 决策调用 + 异常降级 + 缓存
- 协议联调：`/api/simulation/data` 与 `/api/ai/decision`（v1）
- 数据输出：逐步 CSV + 场景对比报告 + 契约回归产物
- 统计指标：队列等待 `total/avg/max/p50/p90/p99`

## 2) 一键验收命令（交付前必跑）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_full_validation.ps1
```

通过标准：

- `overallStatus = passed`
- `failedCount = 0`

## 3) 关键产物路径

- 全量验收汇总：`build/validation/validation_summary_*.json` / `*.md`
- API 契约产物：`build/api_contract/*`
- AI 契约产物：`build/ai_contract/*`
- 场景对比报告：`build/reports/scenario_comparison_*.md` / `*.json`
- 逐步数据（CSV）：`build/step_data.csv`（或场景脚本导出的快照）

## 4) A / C 对接要点

给 A（后端/AI）：

- 按 `docs/api_contract_v1.md` 固定字段联调
- `allocation` 长度必须等于 `windowCount`，元素非负且总和等于 `newArrivals`

给 C（记录/分析）：

- 消费 CSV 列：`simTime,queueLengths,availableSeats,waitingForSeat,totalQueueWaitSec,avgQueueWaitSec,maxQueueWaitSec,p50QueueWaitSec,p90QueueWaitSec,p99QueueWaitSec,totalServed,totalArrived,newArrivals`
- 对比分析优先读取 `build/reports/scenario_comparison_*.json`

## 5) 环境说明（Windows）

- 默认可走 WinHTTP 回退，无需强依赖 libcurl
- 若本机有 libcurl，可按 CMake/编译参数启用

## 6) 最终交付建议

- 先执行一键全量验收，确认汇总 `passed`
- 打包 `docs/` 与 `build/validation` 最近一轮报告给老师/组员
- A、C 变更接口字段前，先重新跑契约脚本并附通过记录
