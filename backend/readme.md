# Cafeteria Management — Java Backend

Spring Boot 4.0.5 · Java 21 · MySQL · Redis · RabbitMQ · DeepSeek API

## Architecture

```
C++ Simulation Engine
    │
    ├── POST /api/simulation/data ──→ CppInterfaceController
    │                                       │
    │                              ┌────────┴────────┐
    │                              ▼                  ▼
    │                    RabbitMQ (async)    RealtimeStateStore (memory)
    │                         │                    │
    │                    Consumer                Redis (cache)
    │                         │                    │
    │                    MySQL (persist)     WebSocket → Frontend
    │
    └── POST /api/ai/decision ──→ AiDecisionServiceImpl
                                       │
                              ┌────────┴────────┐
                              ▼                  ▼
                        DeepSeek API        Rule-based Fallback
                        (deepseek-chat)     (weighted shortest queue)
```

## Module Structure

```
com.canteen/
├── config/          Security, RabbitMQ, Async, Web, SimulationAutoStarter
├── controller/      CppInterfaceController, FrontendController
├── dto/             SimulationDataRequest/Response, AiDecisionRequest/Response
├── entity/          SimulationSnapshot, DecisionRecord
├── repository/      JPA repositories
├── service/         SimulationService, AiDecisionService, DeepSeekClient,
│                    RealtimeStateStore, SimulationLauncher
├── producer/        SimulationDataProducer (RabbitMQ)
├── consumer/        SimulationDataConsumer (RabbitMQ)
└── websocket/       SimulationWebSocket
```

## Key Services

| Service | Role |
|---------|------|
| `SimulationLauncher` | ProcessBuilder wrapper — auto-starts C++ on boot, stop/restart via API |
| `RealtimeStateStore` | AtomicReference-based in-memory state, sub-microsecond reads |
| `DeepSeekClient` | Calls DeepSeek `/v1/chat/completions`, validates allocation, retry+timeout |
| `AiDecisionServiceImpl` | DeepSeek-first with rule-based fallback, Redis caching |
| `SimulationDataProducer` | RabbitMQ async dispatch, synchronous fallback on failure |
| `SimulationDataConsumer` | 3-retry + DLQ, transactional ACK |

## Configuration Profiles

- **mysql** (active): MySQL + Redis + RabbitMQ
- **dev**: H2 in-memory (for quick local testing without infra)

Switch via `application.properties`: `spring.profiles.active`

## Quick Start

```bash
# Ensure infrastructure
systemctl start mysqld rabbitmq-server
redis-server --port 6379 --daemonize yes

# Set DeepSeek API key
export DEEPSEEK_API_KEY=sk-xxx
export DEEPSEEK_API_ENABLED=true

# One-click start (Java + C++ auto-launch)
cd backend
mvn spring-boot:run
```

## Access

| Page | URL |
|------|-----|
| Admin Console | http://localhost:8081/ |
| Client (Live View) | http://localhost:8081/app.html |
| Swagger API Docs | http://localhost:8081/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672/ (guest/guest) |
