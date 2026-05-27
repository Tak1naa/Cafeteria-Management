# 食堂仿真系统 — 交付说明

更新日期：2026-05-26

## 系统架构

```
C++ 仿真引擎 ←→ Java Spring Boot ←→ 前端 (HTML/CSS/JS)
                    │
              ┌─────┼─────┐
            MySQL  Redis  RabbitMQ  DeepSeek API
```

## 一键启动

```bash
cd backend
export DEEPSEEK_API_KEY=sk-xxx
export DEEPSEEK_API_ENABLED=true
mvn spring-boot:run
```

Java 启动 → 自动拉起 C++ 仿真 → 数据实时流入

## 访问地址

| 页面 | URL |
|------|-----|
| 管理端（仪表盘 + API 联调） | http://localhost:8081/ |
| 用户端（实况 + 平面图 + 仿真控制） | http://localhost:8081/app.html |
| API 文档 (Swagger) | http://localhost:8081/swagger-ui.html |

## 核心功能

- **C++ 仿真引擎**：泊松到达、4 窗口排队、5×10 座位矩阵、时间步推进
- **AI 决策**：DeepSeek API 优先，4 层容灾（缓存 → DeepSeek → Java 规则 → C++ 规则）
- **实时可视化**：食堂平面图（窗口 + 座位 + 流指示器）、WebSocket 推送
- **仿真控制**：前端可调参数（到达率/服务时间/用餐时间/总步数）、启停/重置
- **数据持久化**：MySQL 落库、Redis 缓存、RabbitMQ 异步消息

## 关键文件

| 路径 | 说明 |
|------|------|
| `config/default_config.json` | 仿真默认配置 |
| `docs/api_contract_v1.md` | 完整 API 接口文档 |
| `docs/security_robustness_fixes_20260525.md` | 安全审计报告 |
| `backend/readme.md` | 后端架构说明 |
