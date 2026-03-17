# Cafeteria-Management
2026 大二夏季学期软件实训项目

## 当前开发进展（开发者B）

已完成一个可运行的 C++ 仿真引擎 MVP，覆盖开发者 B 的核心职责主线：

- 仿真核心类结构：`SimulationEngine` / `WindowQueue` / `TableMatrix` / `PersonGenerator`
- 人员到达模拟：使用泊松分布（`<random>`）生成每步到达人数
- 多窗口排队：每个窗口独立队列与服务过程推进
- 时间驱动引擎：固定时间步推进（默认 1 秒）
- 餐桌矩阵管理：二维容量映射、占用与释放
- 规则分配策略：按最短队列分配新到人员（AI 失败时降级策略）
- 排队等待统计：总等待秒数、平均等待秒数、最大等待秒数
- AI 交互骨架：
	- 向 `/api/simulation/data` 上报实时状态
	- 向 `/api/ai/decision` 请求分配策略
	- 本地缓存决策（默认 5 秒有效）
	- 请求失败自动降级规则策略
- 步数据落盘：可按配置将每个仿真步导出到 CSV（供 C 侧直接消费）

## 目录结构

- `include/simulation/`：头文件（核心类与数据结构）
- `src/`：实现文件与主程序入口
- `config/default_config.json`：默认仿真参数

## B 与 C 的接口（当前已提供）

- 公共数据结构：`include/simulation/simulation_data.h` 中的 `StepData`
- B 侧回调注入：`SimulationEngine::setStepRecorder(...)`
- 行为：每个仿真步都会回调一次，便于 C 侧记录模块直接接入

## 开发联调看板（B 需要告知 A / C）

正式协议文档：`docs/api_contract_v1.md`

### 给 A（后端 / AI）的要求

- 决策接口：`POST /api/ai/decision`
- 返回 JSON 必须包含 `allocation` 数组，长度必须等于 `windowCount`
- `allocation` 所有元素必须为非负整数，且总和必须等于请求中的 `newArrivals`
- 返回不合法或超时（当前 B 侧超时 1500ms）时，B 会自动降级到规则分配
- 联调阶段请固定使用同一场景和随机种子（建议 `config/scenarios/baseline_seeded.json`）
- 涉及策略优化对比时，请同时查看 B 侧对比报告（`build/reports/scenario_comparison_*.md`）
- 每次接口字段或校验逻辑变更前，请先跑 `scripts/run_ai_contract_test.ps1` 并提供通过记录

### 给 C（记录 / 分析）的要求

- B 已支持逐步 CSV 落盘，配置项为：`stepRecordToFile`、`stepRecordFilePath`
- 当前 CSV 表头：`simTime,queueLengths,availableSeats,waitingForSeat,totalQueueWaitSec,avgQueueWaitSec,maxQueueWaitSec,p50QueueWaitSec,p90QueueWaitSec,p99QueueWaitSec,totalServed,totalArrived,newArrivals`
- 其中 `queueLengths` 用 `|` 分隔各窗口队列长度（如 `"3|2|4|1"`）
- 如需新增字段（例如 `totalSeated`、`totalFinishedDining`），请一次性给字段清单，B 统一补齐
- 基准数据建议使用 `scripts/run_baseline.ps1` 产物（`build/baseline/` 下的日志与CSV快照）
- 对比分析建议直接消费 `scripts/compare_scenarios.ps1` 产出的 JSON 摘要（`build/reports/scenario_comparison_*.json`）
- 若要分析 AI 决策有效性，请优先使用 `build/ai_contract/` 下的契约回归产物

## 逐步数据导出配置

`config/default_config.json` 已包含：

```json
"randomSeed": 20260317,
"stepRecordToFile": true,
"stepRecordFilePath": "build/step_data.csv"
```

说明：`randomSeed >= 0` 时启用固定随机序列；`-1` 表示每次运行随机。

## 基准场景一键运行

```powershell
.\scripts\run_baseline.ps1
```

可选参数：

```powershell
.\scripts\run_baseline.ps1 -ConfigPath "config/scenarios/stress_peak_seeded.json"
```

## 场景对比报告一键生成

```powershell
.\scripts\compare_scenarios.ps1
```

可选参数：

```powershell
.\scripts\compare_scenarios.ps1 -BaselineConfig "config/scenarios/baseline_seeded.json" -StressConfig "config/scenarios/stress_peak_seeded.json"
```

输出目录：`build/reports/`
- Markdown 报告：`scenario_comparison_*.md`
- JSON 摘要：`scenario_comparison_*.json`
- 原始运行日志与CSV：`build/reports/runs/`

## AI 接口契约回归（Mock）

```powershell
.\scripts\run_ai_contract_test.ps1
```

可选参数：

```powershell
.\scripts\run_ai_contract_test.ps1 -ConfigPath "config/scenarios/ai_mock_seeded.json" -Port 18080
```

该脚本会自动：
- 启动本地 Mock AI 服务（`scripts/mock_ai_server.ps1`）
- 运行开启 AI 的仿真并校验返回契约（Windows 默认使用 WinHTTP 回退，无需额外安装 libcurl）
- 输出结果到 `build/ai_contract/`（日志、配置快照、summary JSON、step CSV）

## API 协议正反用例测试（独立）

```powershell
.\scripts\test_api_contract.ps1
```

可选参数：

```powershell
.\scripts\test_api_contract.ps1 -Port 18080
```

输出目录：`build/api_contract/`
- summary：`api_contract_summary_*.json`
- mock 服务日志：`mock_api_server_*.log`

## 一键全量验收（交付前推荐）

```powershell
.\scripts\run_full_validation.ps1
```

该脚本会串行执行：
- 可执行文件编译
- API 协议正反用例测试
- AI 接口契约回归（Mock）
- 基线/压力场景对比

输出目录：`build/validation/`
- 汇总 JSON：`validation_summary_*.json`
- 汇总 Markdown：`validation_summary_*.md`

验收条目请见：`docs/acceptance_checklist.md`
最终交付一页式说明：`docs/final_delivery_onepager.md`

## 本地构建运行

```bash
cmake -S . -B build
cmake --build build
./build/cafeteria_sim config/default_config.json
```

Windows PowerShell 可执行：

```powershell
cmake -S . -B build
cmake --build build
.\build\cafeteria_sim.exe config\default_config.json
```

如果本机暂时没有 `cmake`，也可直接使用 `g++`：

```powershell
if (!(Test-Path build)) { New-Item -ItemType Directory -Path build | Out-Null }
g++ -std=c++17 -Iinclude src/main.cpp src/person_generator.cpp src/window_queue.cpp src/table_matrix.cpp src/decision_client.cpp src/simulation_engine.cpp -lwinhttp -o build/cafeteria_sim.exe
.\build\cafeteria_sim.exe config\default_config.json
```

## 说明

- Windows 下默认支持 WinHTTP 回退，未安装 libcurl 也可进行 HTTP 联调。
- 若系统中可用 libcurl，CMake 会自动优先链接 libcurl 进行 HTTP 通信。
