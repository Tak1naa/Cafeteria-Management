# 系统验收清单

## 基础设施

- [ ] MySQL 运行，数据库 `canteen_sim` 可连接
- [ ] Redis 运行 (port 6379)
- [ ] RabbitMQ 运行 (port 5672)，管理界面可访问 (port 15672)

## 编译与启动

- [ ] Java `mvn compile` 无错误
- [ ] C++ `cmake .. && make` 编译成功
- [ ] `mvn spring-boot:run` 启动后 C++ 自动拉起
- [ ] 前端管理端 `http://localhost:8081/` 可访问
- [ ] 前端用户端 `http://localhost:8081/app.html` 可访问

## API 接口

- [ ] `GET /health` 返回 UP
- [ ] `POST /api/simulation/data` 正常接收数据
- [ ] `POST /api/ai/decision` 返回合法 allocation
- [ ] `GET /api/frontend/realtime/status` 返回实时数据
- [ ] `GET /api/frontend/status` 返回系统状态

## 仿真控制

- [ ] `POST /api/frontend/simulation/stop` — C++ 进程终止，接口返回 503
- [ ] `POST /api/frontend/simulation/start` — C++ 重新拉起，数据恢复
- [ ] `POST /api/frontend/simulation/reset` — 数据库清空，C++ 重启
- [ ] 前端仿真控制面板可调节参数（到达率/服务/用餐/步数）

## 数据流

- [ ] WebSocket 实时推送正常（用户端和管理端同步更新）
- [ ] MySQL `simulation_snapshot` 表有数据写入
- [ ] MySQL `decision_record` 表有 AI 决策记录
- [ ] RabbitMQ 队列消息正常流转（无积压）

## 前端可视化

- [ ] 用户端显示食堂平面图（窗口队列 + 座位 + 流指示器）
- [ ] 座位占用数匹配实际数据
- [ ] 管理端仪表盘指标实时更新
- [ ] 管理端 API 联调区可模拟 C++ 请求

## DeepSeek AI

- [ ] AI 决策返回 `source: "deepseek"`（环境变量已设置时）
- [ ] DeepSeek 不可用时自动回退规则引擎（`source: "ai_service"`, `decisionMode: "RULE_BASED"`）
- [ ] 前端显示 AI 分配结果
