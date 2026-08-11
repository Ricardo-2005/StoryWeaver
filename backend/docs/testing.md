# 后端测试与验收说明

标准验收命令是：

```powershell
.\mvnw.cmd clean verify
```

它依次执行编译、单元/架构测试、打包、Testcontainers 集成测试、Spotless 校验和 Failsafe verify。当前 Surefire 报告 13 次单元/架构测试执行；源码中有 27 个显式 `@Test` 方法，其中 8 个是普通单元测试方法、19 个是集成测试方法。执行次数与注解数不完全相同，是因为 5 条 ArchUnit 规则由架构测试类独立生成测试执行。集成测试使用真实 PostgreSQL 18 + pgvector 和 Redis 8.2 容器，空库执行全部 18 条 Flyway 迁移并验证当前版本为 V15；不依赖开发者现有数据库。

## 1. 测试分层

| 层级 | 文件/数量 | 验证内容 |
|---|---|---|
| Domain/Service 单测 | 8 个显式 `@Test` 方法 | 正典状态、Skill 合成、Workflow 状态机、DeepSeek 参数与 HMAC、确定性 Validator、Phase 8 评测 |
| ArchUnit | 5 个架构规则 | Controller 不直连 Repository、shared 依赖方向、模块无环等边界；计入 Surefire 的 13 次单元/架构执行 |
| Infrastructure IT | 2 个方法 | PG/Redis 版本、Flyway、Hibernate、Actuator、ProblemDetail、精确 128 路由契约 |
| Phase 1–4 IT | 6 个方法 | 身份/所有权/版本、内容模型、DeepSeek/SSE、世界书、事件、上下文与降级 |
| Phase 5–7 IT | 8 个方法 | Workflow、恢复、原子提交、一致性、MCP、预算、费用、指标、Trace |
| Phase 8 Demo IT | 1 个方法 | 连续 10 章工作流、原子提交与演示链路 |
| Skill Forge IT | 2 个方法 | 默认模板、动态必填参数、Skill 熔炼持久化与兼容行为 |

当前源码共有 **27 个显式 `@Test` 方法（8 个普通单元方法 + 19 个集成方法）**；加上 5 条 ArchUnit 规则，完整 Maven 验收会报告 **32 次测试执行（13 次单元/架构 + 19 次集成）**。新增或删除测试、路由、迁移时，本文、接口清单与 CI 验收记录必须同步更新。

## 2. Phase 可追溯矩阵

| Phase | 核心自动证据 |
|---|---|
| 0 | `InfrastructureIT`、`ModuleArchitectureTest`、`docker compose config`、Docker image build |
| 1 | `Phase1ApiIT`：注册登录、JWT、项目/正典版本、跨用户隐藏、乐观锁 |
| 2 | `Phase2ApiIT`：人物状态、大纲、章节版本/恢复、三级 Skill 冲突与隔离 |
| 3 | `Phase3ApiIT`：Planner/Extractor/Reviewer JSON 修复、Writer SSE、Usage、匿名 user_id、重试策略、认证错误不重试 |
| 4 | `Phase4ApiIT`：CONSTANT/KEYWORD/VECTOR、Token 裁剪、作用域、可见性、事件排序、Embedding 降级、隔离 |
| 5 | `Phase5ApiIT`：端到端草稿、SSE 重放、幂等、同项目并发、取消、超时恢复 |
| 6 | `Phase6ApiIT`：状态传播、BLOCKER、修订重提取、审批、原子提交、故障注入回滚 |
| 7 | `Phase7ApiIT`：MCP 未认证拒绝、6 Tools、5 Resources、3 Prompts、候选写安全/幂等/隔离/审计、预算读写、Pricing/Usage/Cost、指标、Tracer |
| 8 | `Phase8EvaluationTest` + `Phase8DemoIT`：固定集指标、Context 对照、微基准、连续十章 |
| 9–13 | 项目向导、全局 Skill、导入导出、分支/门禁、滚动大纲等能力由对应 API IT 与 128 路由契约共同覆盖 |
| 14–15 | `SkillForgeServiceIT` + `InfrastructureIT`：28 个动态模板、参数契约、管理员角色迁移、空库升级到 V15 |

## 3. 接口契约测试

`InfrastructureIT.exposesTheDocumentedRestApiSurfaceWithoutUndocumentedBusinessRoutes` 从 Spring 运行时读取所有 `com.storyweaver.*Controller` 映射，要求与 128 条 `METHOD path` 清单完全一致。它可以发现：

- Controller 写了但文档/契约未登记的业务路由；
- 方法、路径参数名或父路径被意外改动；
- Controller 未被扫描，导致路由实际不存在；
- 同一路由重构时出现漏删或重复暴露。

它不替代每个输入组合的行为测试。行为由各 Phase 集成测试覆盖关键正向路径、鉴权、所有权、状态冲突、降级和回滚；新业务分支仍必须增加对应断言。

## 4. 真实组件与测试替身

| 组件 | 测试方式 |
|---|---|
| PostgreSQL/pgvector | 真实 Testcontainer，Flyway 从空库迁移 |
| Redis | 真实 Testcontainer |
| HTTP/Security/JPA/事务 | `RANDOM_PORT` 完整 Spring Boot 进程内服务 |
| DeepSeek HTTP | WireMock：重试、错误、SSE、用量和结构化返回；不会消耗真实 Key |
| Phase 5/6/8 Agent | 确定性 Spring 测试 Stub；状态机、数据库、事务仍是生产实现 |
| 本地 Embedding | Phase 4 同时覆盖可用替身和加载失败降级；未把真实大模型下载纳入 CI |
| Prometheus/Tracer | 应用端注册、指标文本和 Trace Bean；完整 Tempo/Grafana 链路由 Docker 验收 |

因此 `clean verify` 能证明后端逻辑和协议在确定性环境中工作，但不能证明真实 DeepSeek 的当日可用性、账号余额、开放域生成质量或生产性能容量。

## 5. Phase 8 评测口径

```powershell
.\scripts\run-phase8-evaluation.ps1 -Clean
```

固定集含 120 个冲突正例和 120 个安全对照，直接调用生产 Java Validators；报告 Precision/Recall/F1、BLOCKER 漏检、证据定位、Context Token 对照和本机微基准。结果写入 `target/phase8-results/phase8-results.json`，版本化参考快照在 `eval/results/phase8-results.json`。

这些是固定规则边界回归，不是自然语言泛化基准，也不是并发压测。性能数字只适合检测明显回退，机器/JVM 信息必须与结果一起解释。

## 6. CI

GitHub Actions 在 push main、PR 和手工触发时：

1. 使用 Temurin Java 21 运行 `clean verify`；
2. 无论成功失败上传 Phase 8 结果证据；
3. 执行 `docker compose config --quiet`；
4. 构建后端镜像。

CI 没有启动完整 Grafana/Tempo UI、没有真实调用 DeepSeek、没有漏洞扫描/SBOM、没有跨平台矩阵和生产压测。这些属于发布加固项，不能从绿色 CI 推断为已完成。

## 7. 常用局部命令

```powershell
# 只跑单元测试
.\mvnw.cmd test

# 只跑一个集成测试（仍会执行单元测试）
.\mvnw.cmd "-Dit.test=Phase7ApiIT" verify

# 格式化后再验证
.\mvnw.cmd spotless:apply
.\mvnw.cmd clean verify

# 校验 Compose 和构建镜像
docker compose config --quiet
docker build -t storyweaver-backend:audit .
```

测试要求 Docker Engine 可用；如 Testcontainers 报连接失败，先运行 `docker info`，不要跳过集成测试后声称完整验收通过。
