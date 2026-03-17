# Cafeteria B 模块验收清单

## 1. 编译与运行

- [ ] 可执行文件可成功构建：`build/cafeteria_sim.exe`
- [ ] 默认配置可运行：`config/default_config.json`
- [ ] 程序能输出逐步日志与最终统计

## 2. 接口契约（A 对接）

- [ ] `POST /api/simulation/data` 请求字段完整（含 `schemaVersion`）
- [ ] `POST /api/ai/decision` 返回字段符合约定（`allocation`）
- [ ] 非法 schema 能被拒绝（状态码 400）
- [ ] AI 不可用时触发本地降级策略

## 3. C 侧消费数据

- [ ] CSV 持续输出于 `build/simulation_output.csv`
- [ ] 含队列与吞吐指标字段
- [ ] 含等待指标字段：`wait_total/avg/max/p50/p90/p99`

## 4. 回归脚本

- [ ] API 契约测试通过：`scripts/test_api_contract.ps1`
- [ ] AI 契约回归通过：`scripts/run_ai_contract_test.ps1`
- [ ] 场景对比通过：`scripts/compare_scenarios.ps1`

## 5. 一键全量验收（建议交付前执行）

执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_full_validation.ps1
```

产物：

- 汇总报告（JSON）：`build/validation/validation_summary_*.json`
- 汇总报告（Markdown）：`build/validation/validation_summary_*.md`
- API 契约日志：`build/api_contract/*`
- AI 契约日志：`build/ai_contract/*`
- 场景对比报告：`build/reports/*`

通过标准：

- 汇总报告 `overallStatus = passed`
- `failedCount = 0`
