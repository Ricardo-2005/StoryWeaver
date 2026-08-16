# 数据库基线

PostgreSQL 是系统记录源，Flyway 是唯一结构管理机制；Hibernate 使用 `ddl-auto=validate` 并关闭 Open Session in View。

## 已实施迁移

| 版本 | 内容 |
|---|---|
| V0 | PostgreSQL 基线校验 |
| V1 | `app_user`、`novel_project`、`project_snapshot` |
| V2 | `canon_asset`、`canon_asset_version` |
| V3 | `outline_node`、`chapter`、`chapter_version` |
| V4 | `character`、`character_state` |
| V4.1 | `skill_definition`、`skill_binding` |
| V4.2 | `usage_record` |
| V5 | pgvector 扩展、`worldbook`、`worldbook_entry`、`story_event` |
| V6 | `workflow_run`、`context_packet`、`workflow_step`、`workflow_event` |
| V7 | `story_fact`、`item_ownership`、`character_knowledge`、`review_issue`，工作流审查/提交状态 |
| V8 | `pricing_rule`、`project_budget`、`mcp_audit_log`、Usage 成本快照及 MCP 候选事实来源 |
| V9 | 导入、伏笔、影响报告、滚动大纲、批量章节、剧情门、分支与模型尝试 |
| V10 | 创建项目题材、受众、视角、篇幅和故事构想偏好 |
| V11 | `global_skill`、不可变版本、原子规则、熔炼任务与项目绑定 |
| V12 | 从本地写作 `SKILL.md` 熔炼的内置中文网文基础 Skill |
| V13 | TXT/手写来源快照、段落证据、熔炼步骤与 8 类验证测试 |
| V14 | Skill 动态模板上下文：素材标签、题材和来源项目 |
| V15 | 用户 `USER/ADMIN` 角色约束 |
| V16 | 单个 TXT 书籍建项：私有临时源、Import Job、章节 Preview、分析 Candidate 与 Project/Chapter 来源证据 |
| V17 | 可恢复全项目重建：Reconstruction Job、Chapter/Chunk、Evidence Candidate、章节重建元数据、步骤和 Usage 归属 |
| V18 | Candidate 策略/撤回、人物与事实时间生命周期、检索资格、世界书版本、滚动大纲快照、依赖与失效 |
| V19 | 伏笔来源 Candidate 关联、既有候选台账回填与可逆取消 |

`worldbook_entry.embedding` 与 `story_event.embedding` 均为 `vector(512)`。条目保存作用域、可见性、关键词、常驻/向量开关、优先级及 Embedding 状态；事件保存参与者、知情者、地点、故事时间、行动、结果、重要度和证据。两类表均带项目外键、必要索引和乐观锁版本。

Phase 4 使用精确余弦距离查询，没有近似向量索引。Phase 5 的数据库约束保证用户级幂等键唯一，并通过部分唯一索引保证同一项目最多一个执行中的 Writer；`workflow_event.event_id` 是 SSE 重放游标，`context_packet.expires_at` 用于过期检测。

Phase 6 将候选事实与接受事实分离；道具按“项目 + itemKey”保持单一当前持有关系，人物知识按“项目 + 人物 + factKey”保持单一状态。Reviewer 与 Java Validator 问题持久化到 `review_issue`，未解决的 BLOCKER 由服务和事务层双重阻止提交。

Phase 7 把每次 LLM 请求匹配到当时有效的 `pricing_rule`，并在 `usage_record` 保存规则 ID、规则版本、估算/实际成本和币种，避免规则更新后重算历史账单。仓库不内置可能过期的模型价格；运维通过数据库按生效区间维护规则，没有匹配规则的请求明确计入 `unpricedRequests`。`project_budget` 保存单任务、用户每日、项目累计、Writer 输出和 Planner 推理上限。

MCP 写入的 StoryFact 使用 `source=MCP`、调用者和可选幂等键，数据库约束保证它只能是无 Workflow/Chapter 绑定的候选来源；MCP 不存在直接写入 `ACCEPTED` 的路径。每次 Tool、Resource 或 Prompt 执行都写入 `mcp_audit_log`，失败审计使用独立事务保存。

V16 的 TXT 源只保存服务器生成的相对 storage key，默认 24 小时 TTL；正式 Project、Chapter 和 ChapterVersion 保存来源哈希、编码、解析器版本与字符 offset，临时源清理不删除正式导入结果。`book_import_job` 状态与 owner 隔离独立于既有的项目内 `story_import`。

V17 将重建进度落在 Job、Chunk 和 Step 表，模型输出只进入 `project_reconstruction_candidate`。V18 为候选加入建议动作、目标实体、撤回/取代和当前检索资格，并为人物状态、关系、知识、物品、事实、世界书和事件加入生命周期或章节有效区间。旧值退出当前检索，不等于删除历史证据。

V19 为正式伏笔增加 `source_candidate_id`，将既有未应用的拆书伏笔 Candidate 回填到台账并建立一对一来源关联。已登记候选变为 `APPLIED`；取消正式条目时，关联 Candidate 恢复为 `CANDIDATE`。

Testcontainers 在空 PostgreSQL/pgvector 数据库上顺序执行全部 22 条迁移并验证当前版本 V19，再由 Hibernate 校验实体映射。正式提交使用悲观锁定 WorkflowRun 的单个数据库事务；事务失败后章节版本、当前版本号、事实和状态写入全部回滚，再以独立事务把运行标记为 `ROLLED_BACK`。

## 表归属（67 张业务表）

| 模块 | 表 | 关键语义 |
|---|---|---|
| Auth/Project | `app_user`, `novel_project`, `project_snapshot` | 账户唯一性、USER/ADMIN 角色、owner、项目版本与聚合快照 |
| Canon | `canon_asset`, `canon_asset_version` | 当前指针 + 不可变版本、确认版本 |
| Outline/Chapter | `outline_node`, `chapter`, `chapter_version` | 大纲父子关系、项目内章节号、不可变正文版本 |
| Character | `character`, `character_state` | 人物卡和一对一当前状态，分别乐观锁 |
| Skill | `skill_definition`, `skill_binding` | 规则内容和 BASE/PROJECT/CHAPTER 绑定 |
| LLM/Usage | `usage_record` | 请求、Token、缓存、重试、耗时、价格/成本快照 |
| Worldbook/Memory | `worldbook`, `worldbook_entry`, `story_event` | 默认世界书、作用域/可见性、512 维向量、结构化故事记忆 |
| Workflow | `workflow_run`, `context_packet`, `workflow_step`, `workflow_event` | 状态机、规范上下文、步骤尝试、SSE 可重放事件 |
| Consistency | `story_fact`, `item_ownership`, `character_knowledge`, `review_issue` | 候选/接受事实、当前道具归属、人物知识、Java/LLM 问题 |
| Cost/MCP | `pricing_rule`, `project_budget`, `mcp_audit_log` | 生效区间价格、项目限制、协议调用审计 |
| Long-form Production | `story_import` 等 13 张 V1.5 表 | 导入、滚动生产、剧情门、分支、伏笔、影响与模型尝试 |
| Global Skill | `global_skill`, `global_skill_version`, `global_skill_atomic_rule`, `skill_forge_run`, `project_skill_binding` | 跨项目契约、不可变版本、证据规则、动态熔炼上下文与基础绑定 |
| Skill Evidence/Test | `skill_source`, `skill_source_paragraph`, `skill_forge_step`, `skill_test_case`, `skill_test_run`, `skill_test_result` | 私有原文快照、稳定段落证据、状态事件和验证结果 |
| TXT Book Import | `book_import_source`, `book_import_job`, `book_import_chapter`, `book_analysis_candidate` | 20 MiB 私有临时源、Preview 后建项、来源证据和旧版可选分析 Candidate |
| Reconstruction | `book_reconstruction_job`, `book_analysis_chunk`, `project_reconstruction_candidate`, `chapter_reconstruction_metadata`, `book_reconstruction_step` | 可恢复分层分析、真实进度/Usage、Evidence Candidate 和低风险章节元数据 |
| Evolution/Lifecycle | `character_state_timeline`, `character_relationship_timeline`, `item_ownership_timeline`, `worldbook_entry_version`, `rolling_outline_snapshot`, `asset_dependency`, `asset_invalidation` | 章节有效区间、历史版本、当前检索资格、滚动大纲刷新和依赖失效 |

`flyway_schema_history` 由 Flyway 自己维护，不计入上述 67 张业务表。业务表数由迁移中的 `CREATE TABLE` 语句复核，不包含扩展已有表的 V18 字段和索引。

## 约束和索引原则

- 用户名/邮箱规范化后唯一；项目和所有业务聚合均保存 `project_id`。
- 大纲位置、项目章节号、章节版本号、正典版本号具有业务唯一性，避免并发生成重复序号。
- 项目内活跃 Workflow 使用部分唯一索引；用户幂等键、SSE `(run_id,event_id)` 可稳定重放。
- `item_ownership(project_id,item_key)` 和 `character_knowledge(project_id,character_id,fact_key)` 表示单一当前状态。
- 当前检索还要求生命周期/有效区间和 `retrieval_eligible=true`；未来章节、已撤回、已取代、合并、归档或清除的数据不得作为当前状态召回。
- MCP `requestKey` 使用来源限定唯一索引，防止重试重复创建候选事实。
- 列表/恢复/检索热路径按 `project_id`、状态、更新时间/章节号建立 B-tree 索引。
- 当前向量查询为精确余弦距离，没有 HNSW/IVFFlat；扩大数据量前需基准验证再加索引。

## 并发、时间与数据保留

JPA 业务聚合普遍使用 `version`；API `expectedVersion` 在进入写操作时校验，底层并发冲突统一映射 409。Workflow 审批额外对运行行加悲观锁，避免两个审批同时提交。默认数据库隔离级别为 PostgreSQL `READ COMMITTED`，原子性依靠短事务、行锁、唯一约束和状态前置条件共同保证。

时间戳按 UTC 持久化并以 ISO-8601 返回。当前没有自动清理 `workflow_event`、`mcp_audit_log`、`usage_record`、旧 Context Packet 或历史版本；这是为了 MVP 可追溯性，不等于无限保留适合生产。上线前应按法规、审计和容量制定归档/删除策略，并确保不会破坏 SSE 恢复窗口、账单或版本追溯。

## 迁移纪律

已应用迁移不可原地修改。结构、约束、索引、默认值和 Embedding 维度变化都必须新增版本化 SQL，并在生产数据副本演练。应用账号生产环境宜使用最小权限；迁移账号与运行账号应分离。仓库当前尚未实现备份/PITR/恢复自动化，Compose volume 只能提供容器重建后的本机持久化，不能替代备份。
