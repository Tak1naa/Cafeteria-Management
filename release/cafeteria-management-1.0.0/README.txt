食堂仿真系统 — 发布包
========================

前置条件:
  - JDK 21+
  - MySQL 8.4 (数据库 canteen_sim, 用户 canteen/canteen123)
  - Redis (localhost:6379)
  - RabbitMQ (localhost:5672)

快速启动:
  1. 确保 MySQL/Redis/RabbitMQ 运行中
  2. (可选) export DEEPSEEK_API_KEY=sk-xxx
  3. (可选) export DEEPSEEK_API_ENABLED=true
  4. ./start.sh

访问:
  管理端: http://localhost:8081/
  用户端: http://localhost:8081/app.html

配置:
  编辑 config/default_config.json 调整仿真参数
  或通过用户端"仿真控制"面板实时调节

详情见 docs/ 目录
