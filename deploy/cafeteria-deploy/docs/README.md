# Cafeteria-Management

2026 软件实训项目 — 智能食堂仿真系统

## 架构

```
C++ 仿真引擎 ←HTTP JSON→ Java Spring Boot ←WebSocket/HTTP→ 前端 (HTML/CSS/JS)
                                │
                          ┌─────┼─────┐
                        MySQL  Redis  RabbitMQ  DeepSeek API
```

## 快速启动

```bash
# 1. 确保基础设施运行
systemctl start mysqld rabbitmq-server
redis-server --port 6379 --daemonize yes

# 2. 编译 C++ 仿真引擎
cd build && cmake .. && make -j$(nproc)

# 3. 设置 DeepSeek API（可选，不设置则使用规则引擎）
export DEEPSEEK_API_KEY=sk-xxx
export DEEPSEEK_API_ENABLED=true

# 4. 一键启动后端（自动拉起 C++）
cd backend
mvn spring-boot:run
```

## 访问

| 页面 | 地址 |
|------|------|
| 管理端 | http://localhost:8081/ |
| 用户端（实时 + 平面图 + 控制） | http://localhost:8081/app.html |
| Swagger API | http://localhost:8081/swagger-ui.html |
| RabbitMQ 管理 | http://localhost:15672/ (guest/guest) |

## 核心功能

- **C++ 仿真引擎**：泊松到达 → 4 窗口排队 → 5×10 座位就餐 → 离开
- **AI 智能决策**：DeepSeek API + 4 层容灾（缓存 → DeepSeek → Java 规则 → C++ 规则）
- **实时可视化**：食堂平面图（窗口数显 + 座位占用 + 数据流指示器）、WebSocket 推送
- **仿真控制**：前端可调参数、启停/重置、初始状态配置
- **全栈数据**：MySQL 持久化、Redis 缓存、RabbitMQ 异步消息

## 目录结构

```
├── config/             仿真配置文件
│   └── default_config.json
├── include/simulation/ C++ 头文件
├── src/                C++ 实现
├── backend/            Java Spring Boot 后端
│   └── src/main/java/com/canteen/
├── docs/               项目文档
│   ├── api_contract_v1.md          API 接口文档
│   ├── acceptance_checklist.md     验收清单
│   └── security_robustness_fixes_20260525.md  安全审计
├── scripts/            辅助脚本 (PowerShell)
└── build/              CMake 构建输出
```

## 文档

- [API 接口文档](docs/api_contract_v1.md)
- [后端架构说明](backend/readme.md)
- [验收清单](docs/acceptance_checklist.md)
- [安全审计报告](docs/security_robustness_fixes_20260525.md)
