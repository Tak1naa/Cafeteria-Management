# API 接口文档 v2

仿真引擎（C++）与后端（Java Spring Boot）之间的 HTTP JSON 协议，以及前端 API。

## 通用约定

- Base URL：`http://localhost:8081`
- Content-Type：`application/json`
- 字符编码：UTF-8
- 无需认证（开发模式）
- 协议版本字段：`schemaVersion`，固定值 `"v1"`

---

## 1. 健康检查

```
GET /health
```

响应：
```json
{"status": "UP", "timestamp": "2026-05-26T18:00:00"}
```

---

## 2. C++ → Java 接口

### 2.1 状态上报

```
POST /api/simulation/data
```

请求体：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `schemaVersion` | string | 是 | `"v1"` |
| `simTime` | int | 是 | 仿真时间（秒） |
| `queueLengths` | int[] | 是 | 各窗口队列长度 |
| `windowCount` | int | 是 | 窗口数量，必须等于 `queueLengths.length` |
| `availableSeats` | int | 是 | 可用座位数 |
| `waitingForSeat` | int | 是 | 等座人数 |
| `totalArrived` | int | 是 | 累计到达 |
| `totalServed` | int | 是 | 累计服务 |
| `totalSeated` | int | 是 | 累计就座 |
| `totalFinishedDining` | int | 是 | 累计完成用餐 |
| `newArrivals` | int | 是 | 本步新到达 |

成功响应 (200)：
```json
{"accepted": true, "schemaVersion": "v1", "receivedAt": "2026-05-26T18:00:00"}
```

仿真停止时 (503)：
```json
{"accepted": false, "error": "simulation_stopped", "schemaVersion": "v1"}
```

### 2.2 AI 决策

```
POST /api/ai/decision
```

请求体：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `schemaVersion` | string | 是 | `"v1"` |
| `simTime` | int | 是 | 仿真时间 |
| `queueLengths` | int[] | 是 | 各窗口队列 |
| `windowCount` | int | 是 | 等于 `queueLengths.length` |
| `availableSeats` | int | 是 | 可用座位 |
| `waitingForSeat` | int | 是 | 等座人数 |
| `newArrivals` | int | 是 | 需分配的新到达人数 |

成功响应 (200)：
```json
{
  "allocation": [2, 1, 1, 1],
  "source": "deepseek",
  "decisionMode": "AI",
  "schemaVersion": "v1"
}
```

`source` 可能值：`"deepseek"`（AI 成功）、`"ai_service"`（规则回退）  
`decisionMode` 可能值：`"AI"`、`"RULE_BASED"`  
约束：`allocation` 长度 = `windowCount`，所有值 ≥ 0，`sum(allocation) == newArrivals`

仿真停止时 (503)：
```json
{"error": "simulation_stopped", "message": "仿真已停止", "schemaVersion": "v1"}
```

---

## 3. 前端查询接口

### 3.1 实时状态（轮询）

```
GET /api/frontend/realtime/status
```

返回 `SimulationDataDTO`：
```json
{
  "simTime": 42,
  "queueLengths": [3, 2, 4, 1],
  "windowCount": 4,
  "availableSeats": 26,
  "waitingForSeat": 1,
  "totalArrived": 155,
  "totalServed": 120,
  "totalSeated": 118,
  "totalFinishedDining": 92,
  "newArrivals": 5,
  "avgQueueLength": 2.5,
  "utilizationRate": 48.0
}
```

### 3.2 最新 AI 决策

```
GET /api/frontend/last-decision
```

```json
{
  "request": {...},
  "response": {"allocation": [2,1,1,1], "source": "deepseek", ...},
  "receivedAt": "2026-05-26T18:00:00"
}
```

### 3.3 系统状态

```
GET /api/frontend/status
```

```json
{
  "status": "running",
  "version": "1.0.0",
  "cppRunning": true,
  "hasSimulationData": true,
  "hasDecisionData": true,
  "lastSimulationAt": "2026-05-26T18:00:00",
  "lastDecisionAt": "2026-05-26T18:00:00"
}
```

---

## 4. 仿真控制接口

### 4.1 启动

```
POST /api/frontend/simulation/start
Content-Type: application/json

{
  "initialQueueLengths": [2, 0, 3, 1],
  "initialOccupiedSeats": 10,
  "arrivalRatePerTick": 1.2,
  "avgServiceTimeSec": 3,
  "avgEatTimeSec": 45,
  "totalTicks": 180
}
```

所有字段可选，不传则使用 `default_config.json` 的默认值。

响应：
```json
{"action": "start", "status": "running", "message": "仿真已启动（含自定义参数）"}
```

### 4.2 停止

```
POST /api/frontend/simulation/stop
```

杀死 C++ 进程，清空状态。

### 4.3 重置

```
POST /api/frontend/simulation/reset
```

清空数据库 + 内存 + 缓存 + WebSocket 通知，重启 C++。

---

## 5. WebSocket

```
ws://localhost:8081/ws/simulation
```

推送消息类型：

**仿真数据**（每 tick）：
```json
{"simTime": 1, "queueLengths": [3,2,4,1], "availableSeats": 30, ...}
```

**AI 决策**：
```json
{"type": "decision", "request": {...}, "response": {...}}
```

**重置通知**：
```json
{"type": "reset", "timestamp": "2026-05-26T18:00:00"}
```

---

## 6. C++ 容灾层级

触发任一条件时，C++ 自动降级：

| 层级 | 条件 | 行为 |
|------|------|------|
| 1. 缓存 | `aiCacheSeconds` 内有相同请求 | 复用上次结果 |
| 2. DeepSeek | Java 调用 DeepSeek API 成功 | 使用 AI 分配 |
| 3. Java 规则 | DeepSeek 失败/超时 | Java 加权最短队列 |
| 4. C++ 规则 | Java HTTP 失败/503 | C++ 本地最短队列优先 |
