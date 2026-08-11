# StoryWeaver Backend

StoryWeaver（文脉）后端已完成 Phase 0–8、V1.5 长篇生产、全局 Skill 工坊、TXT/手写证据熔炼、动态模板上下文和 USER/ADMIN 角色。本工程采用 Java 21、Spring Boot 4.1.0 和模块化单体架构。

## 已实现范围

- Phase 0：Docker Compose、PostgreSQL/pgvector、Redis、Flyway、Testcontainers、ArchUnit、Actuator、Prometheus、Grafana、GitHub Actions CI。
- Phase 1：注册登录、Bearer JWT、项目、项目快照、正典资产及版本、所有权隔离和乐观锁。
- Phase 2：人物及状态、大纲树、章节及不可变版本、BASE/PROJECT/CHAPTER 三级 Skill 合成与冲突报告。
- Phase 3：DeepSeek V4 Adapter、Planner/Writer/Extractor/Reviewer、Writer SSE、重试与并发保护、匿名 `user_id`、用量审计。
- Phase 4：世界书 CONSTANT/KEYWORD/VECTOR 激活、作用域与可见性、激活解释、Token 预算裁剪、本地 BGE ONNX 向量、故事事件相关性检索及降级运行。
- Phase 5：WorkflowRun 状态机、Preflight、Canonical Context Packet、Planner/Writer/Extractor 编排、持久化 SSE、超时恢复、取消、幂等启动及同项目单 Writer。
- Phase 6：StoryFact、ItemOwnership、CharacterKnowledge、人物/道具/时间线/知识边界 Validators、Reviewer、修改后重新提取、BLOCKER 门禁、审批、原子提交与失败回滚。
- Phase 7：Stateless Streamable HTTP MCP、6 个 Tools、5 个 Resource 模板、3 个 Prompts、候选事实安全写入、MCP 审计、PricingRule、费用与项目预算 API、Prometheus 指标、OpenTelemetry/Tempo Trace 和 Grafana 面板。
- Phase 8：120 组四类冲突固定集、240 次正负样本预测、全文注入 Context Baseline、Validator/Context 本地微基准、20 章 Demo 清单与种子脚本、三分钟 Demo 脚本、十章连续工作流及原子提交回归、实测结果快照。
- V1.5 与 Skill 更新：导入/伏笔/滚动生产/剧情门/分支；全局 Skill 版本、TXT/手写来源、段落证据、28 组动态模板上下文、8 类测试与安全导出。
- 认证更新：V15 增加 `USER/ADMIN`，角色进入认证 DTO 和 JWT authority；公开注册默认 `USER`。

工作流在 Reviewer 后停于 `WAITING_APPROVAL`，只有显式审批才能在单个数据库事务中创建正式章节版本并回写接受的故事状态。MCP 的唯一写工具也只能创建带证据的 `CANDIDATE`，不能接受事实或修改正典。

## Phase 8 实测结果

最近一次已提交结果见 [评测快照](eval/results/phase8-results.json)，数据集和执行口径见 [评测说明](eval/README.md)。

| 指标 | 实测值 | 口径 |
|---|---:|---|
| 固定冲突集 Precision / Recall / F1 | 100% / 100% / 100% | 120 个规则正例 + 120 个安全对照 |
| BLOCKER 漏检率 | 0% | 固定确定性规则集 |
| 证据标记定位准确率 | 100% | evidence 或 historicalEvidence 包含标记 |
| Context 输入 Token | 4,880 → 2,265 | 前 15 章原创短篇 Fixture vs 动态上下文 |
| Context Token 节省 | 53.59% | 固定 20 章《龙族》技术 Demo 规模与本地 TokenEstimator |
| 连续章节工作流 | 10/10 完成 | Testcontainers + 确定性 Agent Stub + 原子审批 |

这些数字不是开放域自然语言或 LLM 泛化成绩，也不是生产并发压测。固定集直接覆盖当前 Java Validator 的规则边界；本地微基准受机器和 JVM 影响，完整环境信息保存在结果文件中。

## 《龙族》主题技术演示

Phase 8 的可重复 Demo 已统一为青铜城调查模板。人物仅使用路明非、楚子航、诺诺、恺撒、昂热和芬格尔；地点/组织使用卡塞尔学院、三峡、青铜城、秘党、学生会和狮心会；世界书覆盖混血种、龙族、言灵、龙文、炼金术、血统与七宗罪。

当前演示剧情是：卡塞尔学院执行青铜城调查任务，行动成员进入青铜城前核对人物知识、完整七宗罪剑匣归属和事件时间线。世界书 Preview 用于展示青铜城水下结构、龙文机关、炼金武器限制、混血种血统规则和角色可见信息。

版权边界：角色和设定名称只用于非商业技术演示与功能测试；仓库不包含原著正文或完整章节，不生成与原著相同的段落，也不要求模型精确模仿作者文风。Demo 中的章号、剧情、正文和摘要均为原创产品测试 Fixture，不是《龙族》原著真实章节。

## 环境与启动

要求 JDK 21+、Docker Engine 和 Docker Compose；无需本机安装 Maven。

```powershell
Set-Location backend
Copy-Item .env.example .env
.\scripts\download-embedding-model.ps1
.\mvnw.cmd clean verify
docker compose up -d --build
```

`.env` 仅用于本机真实配置并已被忽略；`.env.example` 必须始终保留占位值。Embedding 模型下载到同样被忽略的 `models/`，容器只读挂载该目录。模型文件不存在或初始化失败时，服务继续启动，世界书自动降级为 CONSTANT + KEYWORD，故事事件仍可按结构化字段检索。

| 服务 | 地址 |
|---|---|
| Backend / Health | 默认 `http://localhost:8080/actuator/health`；当前工作区 `http://localhost:18080/actuator/health` |
| Prometheus | 默认 9090；当前工作区 http://localhost:19090 |
| Grafana | 默认 3000；当前工作区 http://localhost:13080 |
| MCP | 默认 `http://localhost:8080/mcp`；当前工作区 `http://localhost:18080/mcp` |

Phase 8 评测与 Demo：

```powershell
.\scripts\run-phase8-evaluation.ps1
.\scripts\seed-phase8-demo.ps1
.\scripts\demo-phase8.ps1
# 只有显式添加 -StartLiveWorkflow 才会调用 DeepSeek 并消耗额度
```

Seed 默认只归档当前 Demo 用户下带 `storyweaver-dragon-template-v1` 精确标记的旧模板项目，再创建新的独立项目；不会删除或改写其他用户项目。使用 `-KeepExistingDemo` 可保留此前模板项目。当前正式 API 没有物理删除项目接口，因此安全清理采用既有项目归档语义。

后端文档索引：

- [完整 API 契约](docs/api.md)：128 条 REST 路由、MCP、SSE、错误、字段和枚举；
- [架构文档](docs/architecture.md)：模块边界、事务、Workflow、MCP 和可观测设计；
- [数据库文档](docs/database.md)：18 条迁移、51 张业务表、约束、并发和迁移纪律；
- [配置手册](docs/configuration.md)：Profile、全部环境变量、安全、Embedding、Workflow 和预算；
- [测试说明](docs/testing.md)：29 个测试、Phase 可追溯矩阵、真实组件和测试替身；
- [运维手册](docs/operations.md)：启动、健康、指标、Trace、迁移和排障；
- [实现审计](docs/acceptance.md)：Phase 0–8 验收结论、证据及已知限制；
- [三分钟 Demo](docs/demo.md) 与 [Roadmap](docs/roadmap.md)。

## P1 与产品 Roadmap（未实现）

以下内容不属于当前已完成范围：Prompt Registry/Golden Regression、混合检索与重建任务、MCP 第三方工具市场、生产 Secret Manager、TLS/反向代理、多实例高可用、Kubernetes、短篇/扫榜/封面/浏览器自动化和自动发布。README 和评测报告不会把这些规划描述成已实现功能。
