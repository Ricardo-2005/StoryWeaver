# 后端运行与运维手册

## 1. 本地运行

前置：JDK 21+、Docker Engine/Compose。仓库包含 Maven Wrapper，不要求全局 Maven。

```powershell
Copy-Item .env.example .env
# 在 .env 中替换密码和密钥；不要提交该文件
.\scripts\download-embedding-model.ps1
docker compose up -d --build
docker compose ps
```

验收地址：

| 服务 | 地址 |
|---|---|
| Backend | 默认 `http://localhost:8080`；当前工作区 `http://localhost:18080` |
| Readiness | 当前工作区 `http://localhost:18080/actuator/health/readiness` |
| Prometheus | 默认 9090；当前工作区 `http://localhost:19090` |
| Grafana | 默认 3000；当前工作区 `http://localhost:13080` |
| MCP | 当前工作区 `http://localhost:18080/mcp` |

不用 Compose 运行应用时，可先启动 `postgres` 和 `redis`，再执行：

```powershell
$env:SPRING_PROFILES_ACTIVE='local'
.\mvnw.cmd spring-boot:run
```

## 2. 启停和状态检查

```powershell
docker compose ps
docker compose logs --tail 200 app
docker compose logs --tail 100 postgres redis prometheus grafana tempo
docker compose restart app
docker compose down
```

`docker compose down` 保留命名卷；`docker compose down -v` 会删除数据库、Redis、Prometheus、Grafana 和 Tempo 数据，属于不可恢复操作，不应在生产或有价值的本地数据上使用。

应用优雅关闭等待最多 30 秒。部署平台应先撤出流量，再发送终止信号，并给至少相同的 grace period；活跃 Workflow 通过心跳和 Recovery Worker 恢复。

## 3. 健康、指标和追踪

Compose 应用健康检查使用 readiness，Prometheus 等待应用健康后启动，Grafana 等待 Prometheus ready 后启动。排障顺序：

1. `/actuator/health/readiness` 是否 UP；
2. `docker compose ps` 中 PostgreSQL/Redis/app 是否 healthy；
3. Prometheus Targets 中 app 是否 UP；
4. Grafana 数据源 health；
5. Tempo 日志和 Trace 查询。

关键自定义指标包括：

- `storyweaver_llm_requests_total`
- `storyweaver_llm_latency_seconds`
- `storyweaver_llm_input_tokens_total`
- `storyweaver_llm_output_tokens_total`
- `storyweaver_llm_cache_hit_tokens_total`
- `storyweaver_llm_cost_total`
- `storyweaver_sse_connections`

Grafana 已预配置 StoryWeaver 面板、Prometheus 和 Tempo。当前 Trace 100% 采样只适合本地；生产需降低采样率、配置留存和访问控制。

## 4. 数据库与迁移

- Flyway 是唯一 Schema 修改入口；禁止使用 Hibernate 自动建表或手工修改后不补迁移。
- 发布前在数据库备份副本上执行迁移演练，并运行 `flyway.validate`（集成测试已自动执行）。
- 当前 18 条迁移最终版本为 15；应用启动后 Hibernate 再验证实体映射。
- PricingRule 由运维 SQL 管理，必须指定模型、生效区间、币种和规则版本；更新价格不能覆盖历史记录。
- Embedding 维度固定 512；换模型/维度需要迁移和重建任务。

仓库提供持久卷但尚未提供自动备份、恢复脚本、PITR 或恢复演练。生产上线前必须补齐这些能力；有卷不等于有备份。

## 5. Demo 与评测

```powershell
.\scripts\seed-phase8-demo.ps1
.\scripts\demo-phase8.ps1
.\scripts\run-phase8-evaluation.ps1
```

种子脚本只调用公开 API，回执写入 `target/` 且不保存 Token/密码。Demo 默认只读且不调用 DeepSeek；只有 `-StartLiveWorkflow` 才消耗真实额度。实时工作流停在 `WAITING_APPROVAL`，不会自动提交正典。

## 6. 常见故障

### 6.1 宿主端口无法绑定

先检查占用和 Windows 排除端口范围：

```powershell
Get-NetTCPConnection -LocalPort 8080,9090 -ErrorAction SilentlyContinue
netsh interface ipv4 show excludedportrange protocol=tcp
```

容器内标准端口保持 8080/9090/3000，宿主映射可以通过 `APP_PORT`、`PROMETHEUS_PORT`、`GRAFANA_PORT` 调整。当前 Windows 环境的 9090 落在保留端口段，因此使用 18080/19090/13080。修改后应通过根目录启动脚本启动前端，让 `BACKEND_UPSTREAM` 自动指向新的 app 宿主端口。

### 6.2 应用启动失败

- PostgreSQL/Redis 未 ready：检查容器 health 和凭据；
- Flyway checksum/validate 失败：不要改写已发布迁移，新增迁移修复；
- JWT secret 太短/错误：使用至少 32 个随机字节；
- Embedding 文件缺失：应用应降级启动，检查日志和 Preview 的 `degradedReason`；
- DeepSeek Key 为空：只有 Agent/Workflow 模型阶段失败，基础 CRUD/MCP 只读能力仍可用。

### 6.3 Workflow 卡住

读取 `GET /api/workflows/{runId}` 的 `status/steps/failureCode/heartbeatAt`，再检查 Usage、预算、DeepSeek 错误和应用日志。恢复 Worker 只恢复符合超时条件的可恢复状态；`WAITING_APPROVAL/BLOCKED/FAILED/CANCELLED/ROLLED_BACK/COMPLETED` 需要用户动作或新运行，不会自动越过人工门禁。

## 7. 生产前必须补齐

TLS/反向代理、Actuator 网络隔离、Secret Manager、数据库自动备份与恢复演练、日志脱敏/集中留存、Grafana 权限、告警规则、镜像扫描/SBOM、容量与故障压测、多实例协调和灾备目前不在仓库已实现范围。发布评审必须逐项落地，不能把本地 Compose 当作生产平台。
