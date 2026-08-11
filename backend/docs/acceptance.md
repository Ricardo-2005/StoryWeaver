# Phase 0–8 后端实现审计

本报告以当前代码、运行时路由、Flyway、Testcontainers 测试、Compose 和评测产物为证据，不仅依据设计文档中的目标描述。

## 1. 结论

Phase 0–8、V1.5 与全局 Skill 更新的仓库内后端范围已经实现，并具备可重复自动验收。当前实际规模：22 个顶层业务/共享模块、23 个 REST Controller、128 条业务 REST 路由、1 个 stateless MCP 端点、6 Tools、5 Resource templates、3 Prompts、51 张业务表、18 条 Flyway 迁移、27 个显式 `@Test` 方法；连同 5 条 ArchUnit 规则，完整 Maven 验收共执行 32 项测试。

“已实现”指设计稿在本仓库定义的 MVP 范围能够编译、启动并通过确定性测试，不代表真实 DeepSeek SLA、开放域生成质量、生产并发容量、高可用、备份恢复和安全合规已经完成。

## 2. Phase 验收矩阵

| Phase | 主要实现 | 自动证据 | 结论 |
|---|---|---|---|
| 0 | Spring Boot/Maven、模块脚手架、Docker、PG/pgvector、Redis、Flyway、Actuator、Prometheus、Grafana、CI | InfrastructureIT、ArchUnit、Compose config、镜像构建 | 已完成 |
| 1 | Auth/JWT、Project、Snapshot、Canon、版本/隔离/乐观锁 | Phase1ApiIT | 已完成 |
| 2 | Character/State、Outline、Chapter immutable versions、Skill composition | Phase2ApiIT + 单测 | 已完成 |
| 3 | DeepSeek Adapter、四 Agent、Writer SSE、重试、并发、Usage | Phase3ApiIT + WireMock + RequestFactory 单测 | 已完成 |
| 4 | Worldbook 三激活、ONNX/pgvector、Token 裁剪、StoryEvent 检索和降级 | Phase4ApiIT | 已完成 |
| 5 | Workflow 状态机、上下文、编排、持久化 SSE、恢复/取消/幂等/单 Writer | Phase5ApiIT + 状态机单测 | 已完成 |
| 6 | 一致性规则、候选事实、Reviewer、修改重审、人工门禁、原子提交/回滚 | Phase6ApiIT + Validator 单测 | 已完成 |
| 7 | MCP、安全候选写、审计、价格/预算/成本、Prometheus、OTel/Tempo/Grafana | Phase7ApiIT + Docker 配置 | 已完成 |
| 8 | 固定评测、Context Baseline、微基准、20 章 Demo、连续 10 章回归 | Phase8EvaluationTest + Phase8DemoIT | 已完成 |

## 3. 接口审计

- 128 条 REST 路由已从运行中的 Spring `RequestMappingHandlerMapping` 提取，并由精确集合测试锁定；不是仅靠扫描源码计数。
- 除 Auth 和 Actuator 外全部需要 JWT；`/mcp` 未认证请求自动返回 401。
- Phase 1–8 关键 CRUD、所有权隔离、乐观锁、SSE、Workflow、预算、成本、MCP 均有 HTTP 级集成测试。
- Phase 7 审计实际调用了全部 6 Tools、读取全部 5 Resource templates、获取全部 3 Prompts；写 Tool 缺证据会失败，跨项目调用会失败并留下审计。
- Actuator 当前报告 `phase: 1.5`；测试固定 PostgreSQL 18、Redis 8.2、18 migrations/current v15、health/prometheus/info 和 ProblemDetail。

REST 路由及字段约束见 `docs/api.md`。路由契约能发现 API 面漂移，但无法穷举每个字段的所有非法组合；新增分支仍需补行为测试。

## 4. 数据和事务审计

- PostgreSQL 是记录源，Redis 不承载不可恢复的正典数据。
- 51 张业务表均由 Flyway 创建；Hibernate 只 validate。
- 项目级复合外键和 Application Service 双重隔离跨项目引用。
- 可编辑聚合使用 `version/expectedVersion`；章节、正典保留不可变历史版本。
- Workflow 审批以悲观锁 + 单事务提交章节和故事状态；故障注入测试证明中途失败不产生半提交。
- MCP 只能写带证据 `CANDIDATE`，数据库约束和服务层均禁止直接产生 `ACCEPTED`。

## 5. 可观测与基础设施审计

- Compose 定义 PostgreSQL、Redis、app、Tempo、Prometheus、Grafana，关键服务有 healthcheck 和持久卷。
- Prometheus 文本中已验证 LLM 请求、延迟、输入/输出/cache token、成本、SSE 连接指标。
- OTel Tracer 在应用中注册，Compose 把 OTLP Trace 发往 Tempo，Grafana 预配置数据源/面板。
- GitHub Actions 使用 Java 21 跑完整 verify、上传 Phase 8 证据、验证 Compose、构建镜像。

## 6. 评测证据解释

当前固定集结果：120 个规则冲突正例 + 120 个安全对照，Precision/Recall/F1 均 100%，BLOCKER 漏检 0%；固定《龙族》技术 Demo 规模的 Context Token 为 4,880 → 2,265，节省 53.59%；连续工作流 10/10 完成。

这些数字只说明当前确定性规则固定集和 Demo 数据未回退。它们不代表模型对任意小说文本的 100% 识别率，也不等于生产性能或真实模型可用性。

## 7. 已知限制和后续风险

| 级别 | 限制 | 当前处理 |
|---|---|---|
| 发布前 | 无 TLS、Secret Manager、数据库自动备份/恢复演练 | 明确不属于当前仓库；生产上线前必须补齐 |
| 发布前 | Actuator 当前公开、Trace 100% 采样 | 由网关隔离并降低生产采样率 |
| 发布前 | 未做容量/故障/安全扫描和 SBOM | 增加专门发布流水线 |
| 产品 | 无 Refresh Token、Token 吊销、分页、OpenAPI 和管理员业务后台 | 客户端按现有 128 路由契约接入；后续版本设计 |
| 检索 | pgvector 精确检索、无 HNSW/重建任务 | 当前数据规模可用；扩大前压测与演进 |
| 外部依赖 | CI 不调用真实 DeepSeek、不加载真实 BGE 大模型 | WireMock/Stub 验证协议与降级；上线前做受控冒烟 |
| 计价 | PricingRule 没有管理 API且仓库无默认价格 | 运维 SQL 管理，未匹配调用明确显示 unpriced |

## 8. 复验命令

```powershell
.\mvnw.cmd clean verify
docker compose config --quiet
docker build -t storyweaver-backend:audit .
.\scripts\run-phase8-evaluation.ps1
```

只有四项均成功，并结合目标环境的真实 DeepSeek/Embedding/监控冒烟，才能把本报告用于一次具体发布签字。
