# 后端配置手册

配置以 `application.yml` 为公共基线，`application-local.yml` 和 `application-docker.yml` 只覆盖基础设施地址。默认 profile 为 `local`；Compose 固定使用 `docker`。敏感值只通过环境变量或未纳入版本控制的 `.env` 注入。

## 1. Profile 和端口

| 项目 | local | docker |
|---|---|---|
| HTTP | 8080（Spring Boot 默认） | 容器 8080，宿主 `${APP_PORT:-8080}` |
| PostgreSQL | `localhost:5432/storyweaver` | `postgres:5432/storyweaver` |
| Redis | `localhost:6379` | `redis:6379` |
| 日志 | 默认控制台格式 | Logstash JSON 控制台格式 |

JPA 使用 `ddl-auto=validate`、UTC JDBC 时区、关闭 Open Session in View；Hikari 最大 20、最小空闲 2、连接超时 10 秒。Flyway 启动时执行 `classpath:db/migration`。

当前工作区的未提交 `.env` 将宿主端口映射为 app 18080、Prometheus 19090、Grafana 13080，以避开本机端口条件；容器内仍分别为 8080、9090、3000。全新环境默认值仍以 `.env.example` 为准。

## 2. 环境变量

### 2.1 基础设施和容器

| 变量 | 默认/示例 | 说明 |
|---|---|---|
| `APP_PORT` | `8080` | 仅 Compose 宿主映射；应用容器内始终 8080 |
| `DB_URL` | local 或 docker profile 默认值 | JDBC URL |
| `DB_USERNAME` | `storyweaver` | 数据库用户 |
| `DB_PASSWORD` | 本地默认 `storyweaver` | 生产必须替换 |
| `POSTGRES_DB/USER/PASSWORD/PORT` | 见 `.env.example` | Compose PostgreSQL |
| `REDIS_HOST/PORT` | profile 默认值 | 应用 Redis 连接 |
| `PROMETHEUS_PORT` | `9090` | Compose 宿主映射 |
| `GRAFANA_PORT` | `3000` | Compose 宿主映射 |
| `GRAFANA_ADMIN_USER/PASSWORD` | `admin`/占位值 | 生产必须替换密码 |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75.0` | 容器 JVM 参数 |

### 2.2 安全和 DeepSeek

| 变量 | 要求 | 说明 |
|---|---|---|
| `JWT_SECRET` | 至少 32 个高熵字节 | HS256 签名；更改后旧 Token 全部失效 |
| `DEEPSEEK_API_KEY` | 真实调用必填 | 空值时服务仍启动，Agent 调用明确失败 |
| `DEEPSEEK_BASE_URL` | 默认 `https://api.deepseek.com` | 兼容测试代理或企业网关 |
| `DEEPSEEK_USER_ID_SECRET` | 独立高熵 HMAC 密钥 | 对用户 ID 做不可逆稳定伪名，不应复用 API Key |

JWT issuer 为 `storyweaver`，有效期 8 小时。用户角色为 `USER/ADMIN`，公开注册默认 `USER`；角色进入 JWT claim 并映射为 Spring Security authority。仓库 `.env.example` 只能保存占位值；真实 `.env` 已被忽略，禁止提交、打印或复制到文档/日志。生产应使用 Secret Manager 或编排平台 Secret，而不是 Compose 明文环境变量。

### 2.3 Embedding 和检索

| 变量 | 默认 | 说明 |
|---|---|---|
| `EMBEDDING_ENABLED` | `true` | 是否初始化本地 ONNX Embedding |
| `EMBEDDING_MODELS_DIR` | `./models` | Compose 只读挂载目录 |
| `EMBEDDING_MODEL_URI` | `file:/models/model.onnx` | BGE ONNX 模型 |
| `EMBEDDING_TOKENIZER_URI` | `file:/models/tokenizer.json` | Tokenizer |
| `EMBEDDING_DIMENSIONS` | `512` | 必须与数据库 `vector(512)` 和模型一致 |
| `EMBEDDING_CACHE_DIRECTORY` | `/tmp/storyweaver-onnx-cache` | 容器缓存目录 |

世界书默认 Token 预算 4,000。评测选定的生产检索配置是 `worldbook-mode: VECTOR_ONLY`、candidate pool 10、无额外 final K 截断；`BASELINE`、常量隔离、关键词、向量和 RRF Hybrid 均可由 `WorldbookRetrievalOptions` 显式复现。故事事件默认 topK 10，综合权重为语义 0.50、参与者 0.20、地点 0.10、章节距离 0.10、重要度 0.10。模型缺失或加载失败不会阻止启动：世界书仍保留常量/关键词上下文，事件保留结构化检索，并在响应中报告原因。选型证据见根目录 `docs/rag-evaluation-optimization.md`。

### 2.4 Workflow

| 变量 | 默认 | 说明 |
|---|---|---|
| `WORKFLOW_STALE_RUN_TIMEOUT` | `15m` | 心跳超过此值视为可恢复 |
| `WORKFLOW_HEARTBEAT_INTERVAL` | `15s` | 活跃运行心跳 |
| `WORKFLOW_RECOVERY_INTERVAL` | `30s` | Recovery Worker 扫描间隔 |
| `WORKFLOW_CONTEXT_TTL` | `30m` | Context Packet 过期时间 |
| `WORKFLOW_EVENT_STREAM_TIMEOUT` | `5m` | 单次 SSE 连接时长 |
| `WORKFLOW_EVENT_POLL_INTERVAL` | `250ms` | 持久化事件轮询间隔 |
| `WORKFLOW_MAX_ACTIVE_RUNS_PER_PROJECT` | `1` | 应用层限制；数据库仍有部分唯一索引兜底 |

Spring 虚拟线程已开启；服务优雅关闭，单阶段最多等待 30 秒。修改恢复和超时值时必须同时评估最长 DeepSeek 请求、负载、数据库连接池和部署平台 termination grace period。

### 2.5 默认预算

| 变量 | 默认 |
|---|---:|
| `BUDGET_TASK_TOKEN_LIMIT` | 40,000 |
| `BUDGET_USER_DAILY_COST_LIMIT` | 100.00000000 |
| `BUDGET_PROJECT_COST_LIMIT` | 1,000.00000000 |
| `BUDGET_WRITER_OUTPUT_TOKEN_LIMIT` | 12,000 |
| `BUDGET_PLANNER_REASONING_TOKEN_LIMIT` | 6,000 |

这些值只用于项目预算首次创建。已存在项目通过预算 API 和乐观锁维护，不会因环境变量变化而被静默覆盖。金额币种取命中的 `pricing_rule`；仓库不内置可能过期的模型价格。

## 3. 可观测配置

Actuator 暴露 `health,info,metrics,prometheus`，Trace 采样率当前为 1.0。Compose 将 `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` 固定为 `http://tempo:4318/v1/traces`；Prometheus 抓取应用，Grafana 自动配置 Prometheus 和 Tempo 数据源。

100% 采样适合本地验收，不应直接照搬到高流量生产。指标标签禁止使用用户、项目、章节、请求正文等高基数或敏感值。应用普通日志不得记录 JWT、DeepSeek Key、完整 Prompt 或章节正文。

## 4. 启动前配置检查

```powershell
Copy-Item .env.example .env
# 只编辑 .env，不编辑或提交 .env.example 中的占位语义
docker compose config --quiet
.\mvnw.cmd clean verify
```

真实 DeepSeek 模式至少检查 `JWT_SECRET`、`DEEPSEEK_API_KEY`、`DEEPSEEK_USER_ID_SECRET`；Docker 全栈还应替换 PostgreSQL、Grafana 密码。任何维度变更都必须新增 Flyway 迁移并重建既有向量，不能只改 `EMBEDDING_DIMENSIONS`。
