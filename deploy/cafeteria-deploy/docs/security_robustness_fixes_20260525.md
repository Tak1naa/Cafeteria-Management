# Java 后端安全性与健壮性修复报告

**日期**: 2026-05-25  
**分支**: `feature/takina-backend`  
**审查范围**: `backend/src/main/java/` 全部 28 个 Java 源文件 + 配置文件 + pom.xml

---

## 修复汇总

### 安全性（6 项）

| # | 文件 | 严重程度 | 问题 | 修复 |
|---|------|---------|------|------|
| 1 | [application-mysql.properties](../backend/src/main/resources/application-mysql.properties) | **严重** | 数据库密码硬编码 `Eat@3390`，已被 git 跟踪 | 改用 `${DB_PASSWORD:}` 环境变量，用户名改用 `${DB_USER:root}` |
| 2 | [SecurityConfig.java](../backend/src/main/java/com/canteen/config/SecurityConfig.java) | **严重** | 所有请求完全放行：CSRF 关闭 + `anyRequest().permitAll()` | 添加 API Key 过滤器：`/api/**` 需 `X-API-Key` header 校验；`/health`、前端页面、WebSocket、H2 控制台保持开放；禁用 Session（无状态） |
| 3 | [WebConfig.java](../backend/src/main/java/com/canteen/config/WebConfig.java) | **高** | `allowedOriginPatterns("*")` + `allowCredentials(true)` 危险组合，任意源可携带凭据 | 限制 CORS 为 `http://localhost:*` 和 `http://127.0.0.1:*` |
| 4 | [application.properties](../backend/src/main/resources/application.properties) | **中** | 无请求体大小限制，可被大 JSON 攻击 | 添加 `server.tomcat.max-http-form-post-size=2MB` |
| 5 | [application.properties](../backend/src/main/resources/application.properties) | **中** | H2 控制台暴露 + Actuator 健康详情公开 | 添加 `security.api-key` 配置项及文档注释 |
| 6 | [application-mysql.properties](../backend/src/main/resources/application-mysql.properties) | **信息** | 缺少环境变量使用说明 | 添加注释说明启动方式 |

### 健壮性（11 项）

| # | 文件 | 严重程度 | 问题 | 修复 |
|---|------|---------|------|------|
| 7 | [AiDecisionServiceImpl.java](../backend/src/main/java/com/canteen/service/impl/AiDecisionServiceImpl.java) | **高** | 加权分配算法：`Math.round()` 独立取整导致先处理的窗口占优；余数全给窗口 0 不合理 | 重写为 `floor` + 余数按"分配后队列最短优先"逐个分配 |
| 8 | [SimulationDataProducer.java](../backend/src/main/java/com/canteen/producer/SimulationDataProducer.java) | **高** | 未设置 `retry-count` header | 初始化消息时设置 `retry-count=0` |
| 9 | [SimulationDataConsumer.java](../backend/src/main/java/com/canteen/consumer/SimulationDataConsumer.java) | **高** | 重试计数永远为 0，消息无限重试；`@Transactional` + 手动 ACK 顺序不当导致可能丢数据 | 重发消息时递增 `retry-count`；使用 `TransactionSynchronizationManager` 在事务提交成功后 ACK |
| 10 | [RabbitMQConfig.java](../backend/src/main/java/com/canteen/config/RabbitMQConfig.java) | **中** | 使用 `System.out/err` 而非日志框架；无死信队列 | 改用 SLF4J；添加 Dead Letter Exchange + Dead Letter Queue |
| 11 | [CppInterfaceController.java](../backend/src/main/java/com/canteen/controller/CppInterfaceController.java) | **高** | 429 限流时返回 `body(null)`，C++ 端解析崩溃 | 返回带 `error` 和 `message` 的 `AiDecisionResponse` |
| 12 | [CppInterfaceController.java](../backend/src/main/java/com/canteen/controller/CppInterfaceController.java) | **中** | 未校验负数 queueLengths | 添加负值遍历校验，返回明确错误信息 |
| 13 | [CacheConfig.java](../backend/src/main/java/com/canteen/config/CacheConfig.java) | **中** | 与 RedisConfig 的 CacheManager 同时存在，`@Primary` 指向内存版 | 添加 `@ConditionalOnProperty`，Redis 启用时此配置自动停用 |
| 14 | [RealtimeStateStore.java](../backend/src/main/java/com/canteen/service/RealtimeStateStore.java) | **中** | 3 个独立 `AtomicReference` 非原子更新，读者可能读到不一致的快照 | 改用不可变内部类 `SimulationSnapshot` / `DecisionSnapshot` 包裹，单一 `AtomicReference` 原子更新 |
| 15 | [SimulationDataDTO.java](../backend/src/main/java/com/canteen/dto/SimulationDataDTO.java) | **中** | 座位利用率公式无物理意义；`avgQueueLength`/`utilizationRate` 字段与动态 getter 冲突 | 修正公式：`利用率 = 已就座 / (空位 + 已就座) × 100`；移除无用字段 |
| 16 | [SimulationServiceImpl.java](../backend/src/main/java/com/canteen/service/impl/SimulationServiceImpl.java) | **中** | `@Transactional` 方法内 try-catch 吞异常，数据库写入失败静默丢失 | 移除 try-catch，让异常正常传播触发回滚 |
| 17 | [JsonToListConverter.java](../backend/src/main/java/com/canteen/converter/JsonToListConverter.java) | **低** | 读写 `null` 崩溃；`convertToEntityAttribute` 使用 raw `List.class` 类型不安全；`RuntimeException` 包装 | `null`/空串保护；`TypeReference<List<Integer>>` 类型安全反序列化；SLF4J 日志 |

### 工程（4 项）

| # | 文件 | 严重程度 | 问题 | 修复 |
|---|------|---------|------|------|
| 18 | [pom.xml](../backend/pom.xml) | **低** | 重复声明 `maven-compiler-plugin`（Maven 警告） | 合并为一个 plugin 声明，executions 统一管理 |
| 19 | [pom.xml](../backend/pom.xml) | **低** | `spring-boot-starter-validation` 依赖重复 | 移除重复项 |
| 20 | [pom.xml](../backend/pom.xml) | **低** | Lombok 1.18.44 | 升级到 1.18.46 |
| 21 | [SimulationWebSocket.java](../backend/src/main/java/com/canteen/websocket/SimulationWebSocket.java) | **低** | 无消息校验 | 空消息忽略；超长消息（>4KB）断开连接 |

---

## 编译与测试

```
mvn clean compile  →  BUILD SUCCESS (28 source files, 0 errors)
mvn test           →  Tests run: 2, Failures: 0, Errors: 0, BUILD SUCCESS
```

- JDK: Temurin 21.0.11+10
- Maven: 3.9.12

---

## C++ 端适配说明

由于 [SecurityConfig.java](../backend/src/main/java/com/canteen/config/SecurityConfig.java) 新增了 API Key 校验，C++ 仿真引擎 `decision_client.cpp` 在调用 `/api/**` 接口时需要携带 HTTP Header：

```
X-API-Key: changeme-dev-key
```

默认 key 通过 `security.api-key` 配置，启动时可通过环境变量 `SECURITY_API_KEY` 覆盖。

C++ 端修改位置：`DecisionClient::postJson()` 方法中的 headers 添加 `"X-API-Key: <key>"`。

---

## 联调前置条件

1. 启动时设置数据库密码：`DB_PASSWORD=xxx ./mvnw spring-boot:run`
2. C++ 端同步添加 `X-API-Key` header
3. Redis / RabbitMQ 默认禁用（`integration.redis.enabled=false`），需要时切换 profile 并配置连接信息
