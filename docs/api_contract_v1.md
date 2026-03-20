# B-A 接口契约（v1）

本文档定义仿真引擎（B）与后端/AI（A）联调时的固定 HTTP JSON 协议。

## 通用约定

- Base URL：`http://<host>:<port>`
- Content-Type：`application/json`
- 字符编码：UTF-8
- 超时（B 侧当前默认）：1500ms
- 协议版本字段：`schemaVersion`，当前固定值 `v1`

---

## 1) 状态上报接口

### 请求

- Method：`POST`
- Path：`/api/simulation/data`

### 请求体（JSON）

必填字段：

- `schemaVersion`: string，固定 `"v1"`
- `simTime`: int，仿真秒级时间
- `queueLengths`: int[]，长度必须 > 0
- `windowCount`: int，必须等于 `queueLengths.length`
- `availableSeats`: int，>= 0
- `waitingForSeat`: int，>= 0
- `totalArrived`: int，>= 0
- `totalServed`: int，>= 0
- `totalSeated`: int，>= 0
- `totalFinishedDining`: int，>= 0
- `newArrivals`: int，>= 0

示例：

```json
{
  "schemaVersion": "v1",
  "simTime": 42,
  "queueLengths": [3, 2, 4, 1],
  "windowCount": 4,
  "availableSeats": 26,
  "waitingForSeat": 1,
  "totalArrived": 155,
  "totalServed": 120,
  "totalSeated": 118,
  "totalFinishedDining": 92,
  "newArrivals": 5
}
```

### 响应体（JSON）

成功：

```json
{
  "accepted": true,
  "schemaVersion": "v1",
  "receivedAt": "2026-03-17T10:22:33.1234567+08:00"
}
```

失败（400）：

```json
{
  "error": "validation_failed",
  "schemaVersion": "v1",
  "message": "windowCount must equal queueLengths length"
}
```

---

## 2) AI 决策接口

### 请求

- Method：`POST`
- Path：`/api/ai/decision`

### 请求体（JSON）

必填字段：

- `schemaVersion`: string，固定 `"v1"`
- `simTime`: int
- `queueLengths`: int[]，长度必须 > 0
- `windowCount`: int，必须等于 `queueLengths.length`
- `availableSeats`: int，>= 0
- `waitingForSeat`: int，>= 0
- `newArrivals`: int，>= 0

### 响应体（JSON）

成功响应必填：

- `allocation`: int[]，长度等于 `windowCount`
- 每个元素是非负整数
- `sum(allocation) == newArrivals`

建议附带字段（可选）：

- `source`: string（如 `"ai_service"`）
- `decisionMode`: string（如 `"AI"`）
- `schemaVersion`: string（建议回传 `"v1"`）

示例：

```json
{
  "allocation": [2, 1, 1, 1],
  "source": "ai_service",
  "decisionMode": "AI",
  "schemaVersion": "v1"
}
```

失败（400）：

```json
{
  "error": "validation_failed",
  "schemaVersion": "v1",
  "message": "newArrivals must be >= 0"
}
```

---

## 3) 降级策略（B 侧）

触发任一条件时，B 自动降级为规则分配（最短队列优先）：

- HTTP 超时/连接失败
- 响应非 2xx
- 响应 JSON 解析失败
- `allocation` 不合法（长度不匹配、含负数、总和不等于 `newArrivals`）

---

## 4) 联调检查清单

- A 侧先通过 `GET /health`
- 先跑 `scripts/run_ai_contract_test.ps1` 验证“仿真链路 + AI响应”
- 再跑 `scripts/test_api_contract.ps1` 验证“协议正反用例”
