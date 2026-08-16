# Phase 0 后端契约核对

核对日期：2026-08-03。事实来源优先级：实际 Controller/DTO 与 `backend/docs/api.md`，其次为迁移，最后才是设计文档。

## OpenAPI 结论

后端当前明确声明没有 OpenAPI/Swagger UI，仓库也没有受版本控制的 OpenAPI 文件。因此 Phase 0 不伪造生成类型；`src/api/generated` 保留到后端提供正式契约。Phase 1 应依据下列实际 Java DTO 建立最小 API 类型。

## 本任务要求范围内的实际路由

| 方法与路径 | 实际请求/响应摘要 |
|---|---|
| `POST /api/auth/register` | `username/email/password` → 201 `AuthResponse` |
| `POST /api/auth/login` | `identifier/password` → `AuthResponse` |
| `GET /api/me` | Bearer JWT → `UserResponse` |
| `POST /api/projects` | `name/genre/targetAudience/narrativePerspective/lengthType/premise/worldRules`，另有可选高级设置 → 201 `ProjectResponse` |
| `GET /api/projects?includeArchived=false` | 返回 `ProjectResponse[]`，无分页 |
| `GET /api/projects/{projectId}` | 返回 `ProjectResponse` |
| `PUT /api/projects/{projectId}` | 全量字段 + `archived/expectedVersion` |
| `DELETE /api/projects/{projectId}?expectedVersion={version}` | 仅永久删除已归档项目，成功返回 204 |
| `POST /api/projects/{projectId}/snapshots` | `{ expectedVersion }` → 201 `SnapshotResponse` |
| `POST /api/projects/{projectId}/assets` | `assetType/name/content/changeSummary` → 201 `AssetResponse` |
| `GET /api/projects/{projectId}/assets` | 返回 `AssetResponse[]` |
| `PUT /api/assets/{assetId}` | `name/content/changeSummary/expectedVersion` |
| `POST /api/assets/{assetId}/confirm` | `{ expectedVersion }` |
| `POST /api/assets/{assetId}/deprecate` | `{ expectedVersion }` |

`AuthResponse` 实际包含 `accessToken/tokenType/expiresAt/user`，其中 `user.role` 为 `USER` 或 `ADMIN`。后端使用无状态 Bearer JWT；没有 HttpOnly Cookie、Refresh Token、登出或吊销接口。前端只能将 access token 保存在内存中，刷新页面后需要重新登录。

`ProjectResponse` 实际包含 `id/name/genre/customGenre/targetAudience/narrativePerspective/lengthType/premise/description/authorIntent/currentFocus/worldRules/targetWordCount/chapterWordTarget/archived/version/createdAt/updatedAt`。更新必须提交 `expectedVersion`。新建项目要求题材与 10—500 字故事构想；`genre=CUSTOM` 时还要求 `customGenre`。

`AssetResponse` 实际包含 `id/projectId/assetType/name/status/currentVersionNo/confirmedVersionNo/version/createdAt/updatedAt/currentVersion`；状态为 `DRAFT/CANDIDATE/CONFIRMED/CONFLICTED/DEPRECATED`。

## 与设计/任务描述的差异

1. 任务列出的 `POST /api/projects/{projectId}/archive` **未实现**。实际归档方式是 `PUT /api/projects/{projectId}`，携带完整更新 DTO、`archived: true` 和 `expectedVersion`。
2. 后端设计文档的 Projects 列表未写归档路由，实际实现与 `backend/docs/api.md` 一致。
3. 后端设计文档的 API 兼容章节建议 `/api/v1/...`，实际 162 条路由使用 `/api/...`，前端必须以后者为准。
4. 前端设计建议 OpenAPI 3.1 生成类型，但后端明确未提供 OpenAPI/Swagger；Phase 0 不生成虚假 schema。
5. 前端设计优先推荐同站 HttpOnly Cookie，但实际登录返回 Bearer JWT，且没有 Refresh Token。共享客户端已采用仅内存 Token 策略。
6. 前端设计的 `ApiProblem.fieldErrors: Record<string, string[]>` 与后端实际校验响应 `errors: Record<string, string>` 不同；客户端统一规范化为字符串数组。
7. 前端需求点名 422、429 与 5xx；后端当前 Bean Validation 返回 400，其他状态仍由统一 Problem Details 层兼容处理。
8. 前端 Phase 1 设计包含会话列表、固定、归档和搜索，但实际 162 条后端路由没有 conversation/session/chat 持久化接口；后续只能先实现导航框架，不能伪造可持久化会话能力。

## Phase 1 对接状态

Auth、Project、Snapshot 和 Canon Asset 路由已建立集中式 endpoint 模块，并由 TanStack Query 管理服务器缓存。会话/聊天能力仍未实现，因为后端没有对应契约。

Phase 2 继续接入人物卡与人物状态、世界书条目、大纲节点、章节与版本、Skill 与合成预览。实际接口均为 POST/GET/PUT；这些模块没有 DELETE、PATCH、自定义字段、Progression、Mentions、拖动排序、拆分、合并或历史查询接口。

## Phase 3 契约结论

Phase 3 工作台只复用已存在的正典资产接口：

- `GET /api/projects/{projectId}/assets`：工作台最近资产与 Canon Canvas 数据源；
- `PUT /api/assets/{assetId}`：Canvas 保存为新的正典资产版本，提交 `expectedVersion`。

实际 Controller、`backend/docs/api.md` 和迁移中均没有 Conversation、Message、Chat、Writing Block 持久化、会话搜索/归档、消息重试/续写或 Chat SSE 接口。后端已有的 `/api/projects/{projectId}/ai/planner|writer|extractor|reviewer` 属于 DeepSeek/生成能力，其中 Writer SSE 也不是 Chat SSE；本阶段不把它们冒充会话接口，也不调用它们。工作流事件 `/api/workflows/{runId}/events` 同样不属于 Chat SSE。

## Phase 4 契约结论

章节编辑器对接以下实际接口：

- `GET /api/chapters/{chapterId}`：读取章节和当前正式版本；
- `POST /api/chapters/{chapterId}/versions`：提交 `title/content/summary/changeSummary/expectedVersion`，创建不可变正式版本；
- `GET /api/chapters/{chapterId}/versions`：读取正式版本列表；
- `POST /api/chapters/{chapterId}/restore/{versionNo}`：提交 `changeSummary/expectedVersion`，从历史版本创建新版本。

实际 `CreateChapterVersionRequest` 只接受纯文本正文，没有 `contentHtml/editorDocument/paragraphMap/contentHash/baseVersion`，也没有草稿自动保存 API。前端因此把 TipTap JSON、ParagraphKey 和未提交草稿保存在 IndexedDB；正式版本仍以后端 `content` 纯文本为真源。正式保存成功后清理对应本地草稿。前端没有调用 DeepSeek Writer 或 Workflow SSE。

`backend/docs/api.md` 把 restore 成功状态写为 201，但实际 `ChapterController.restore` 直接返回 `ChapterResponse` 且没有 `@ResponseStatus`，因此实际状态为 200。客户端按实际 Controller 实现，不依赖文档中的 201。

## Phase 5 契约结论

工作流页面对接以下实际接口：

- `GET /api/projects/{projectId}/budget`：读取真实 Token 和成本预算；
- `GET /api/ai/model-config`：读取四类 Agent 的实际模型与输出上限；
- `POST /api/projects/{projectId}/skills/compose`：核对章节 Skill 冲突；
- `POST /api/chapters/{chapterId}/workflows`：必须携带 8—128 字符安全格式的 `Idempotency-Key`，提交 `viewpointCharacterId/instruction`，返回 202；
- `GET /api/workflows/{runId}`：读取状态、步骤、Context 聚合、场景计划、候选事实和审查问题；
- `POST /api/workflows/{runId}/cancel`：请求取消活跃工作流。

后端没有独立 Preflight、Context Preview、Context 重建、场景计划确认/编辑或重新规划接口。启动工作流后，`WorkflowOrchestrator` 会连续执行 PREFLIGHT、CONTEXT、PLANNING、WRITING、EXTRACTING、VALIDATING 和 REVIEWING，不会在 Context 或 PLAN_READY 等待前端。

`ContextPacketResponse` 只返回 `id/tokenEstimate/estimatedCost/expiresAt/stale/createdAt`，没有设计稿要求的 `sourceType/sourceId/sourceVersion/activationReason/tokenCount/hardRule/removable/dropReason`，因此前端不能展示真实逐来源明细或逐来源 Token 分配。当前 `WorkflowContextBuilder` 还把 `estimatedCost` 固定为零；前端只标注这是后端返回值，不生成随机费用。

## Phase 6 契约结论

正文流对接实际 `GET /api/workflows/{runId}/events`。该接口返回命名 SSE，持久事件 DTO 为 `eventId/runId/type/step/timestamp/payload`，同时会发送没有 `eventId` 的独立 `heartbeat` 数据。后端支持 `Last-Event-ID` Header 和 `afterEventId` Query，Header 优先；前端使用 Header，避免把鉴权信息或不必要的状态放入 URL。

实际鉴权仍是登录返回的 Bearer JWT，后端没有设计稿建议的 HttpOnly Session/JWT Cookie或短期 SSE Ticket。原生 EventSource 不能携带 Authorization Header，所以客户端用集中式流式 `fetch` 消费 SSE，并复用现有内存 Token、Problem Details 和 401 处理。长期 Token 不写入 URL 或持久存储。

实际 `text.delta` payload 仅为 `{ text }`，没有设计稿描述的 `sequence`。可靠游标来自 V6 迁移中 `workflow_event.event_id BIGSERIAL`，前端据此去重和续传。实际 Writer 完成后才发送一次 `usage.partial`；`analysis.completed/review.completed` 未实现，相关阶段只通过 `workflow.step` 体现。

## Phase 7 契约结论

审查与提交使用 `WorkflowResponse.reviewIssues/candidateFacts/version`，并对接：

- `POST /api/workflows/{runId}/request-revision`：提交 1—500,000 字符的完整 `revisedDraft`，清空旧审查产物后重新执行提取、校验和审查；
- `POST /api/workflows/{runId}/approve`：提交 Workflow `expectedVersion`、最多 500 字符的 `changeSummary`、`acceptedFactIndexes`、人物状态、道具、时间线和人物知识变更。

审批要求 Workflow 为 `WAITING_APPROVAL`、Context Packet 未过期、没有未解决 BLOCKER，且所有引用和版本通过确定性校验。成功时，后端在一个事务中创建 ChapterVersion、接受或拒绝全部候选事实、应用四类一致性变更并将 Workflow 置为 COMPLETED。前端不做乐观更新。

实际 ReviewIssue 没有正文位置或处置接口，候选事实也没有编辑接口。`request-revision` 和 `reextract` 实际是同一完整正文重提取逻辑。Extraction 变更只有字符串描述，缺少构造原子 DTO 所需的实体 ID、版本和证据，因此前端不能自动把它们变成提交提案。

## Phase 8 契约结论

模型与费用页面对接：

- `GET /api/projects/{projectId}/usage`：返回完整 UsageRecord 列表，包含 Agent、模型、模型 requestId、状态、Prompt/Completion/Reasoning/Cache Hit/Cache Miss Token、尝试次数、请求耗时、计价规则版本、估算/实际成本和货币；
- `GET /api/projects/{projectId}/costs`：返回后端汇总的 estimatedCost、actualCost、unpricedRequests 和 requests；
- `GET/PUT /api/projects/{projectId}/budget`：五项硬限制，PUT 使用 Budget `expectedVersion`；
- `GET /api/pricing-rules`：只读价格规则；
- `GET /api/ai/model-config`：实际 Agent 能力矩阵。

Usage 没有 Workflow/Chapter 外键，也没有分页、日期或 Agent 过滤；CostSummary 没有 currency。前端不能可靠生成章节费用、项目级 Workflow 耗时趋势、跨币种费用或当前用户今日费用。模型 requestId 不是应用 Trace ID；Trace ID 仅从 Problem Details `traceId` 或 `X-Trace-ID` 读取。

后端 `BudgetService.checkWorkflow` 会阻止项目实际费用达到上限、用户 UTC 日费用达到上限以及 Token 上限不足的工作流。前端新增可确定的项目费用和模型合同检查，但跨项目用户日费用和服务端配置仍由后端最终预检决定。

## V1.2 全局 Skill 文本熔炉契约

Skill 工坊位于项目路由之外。熔炉支持在同一任务中混用多份 TXT 与一份手写/粘贴文本；前端先按 20 文件、单文件 10 MiB、总计 20 MiB、手写文本 200–50,000 字做即时校验，后端重复执行同一安全校验并负责 UTF-8/GB18030 严格解码。页面按 7 种素材标签 × 4 种 Skill 类型提供 28 组独立模板，可选关联项目以自动注入题材。用户修改过的字段在切换选项时不会被强制覆盖。

前端通过 `src/api/endpoints/globalSkills.ts` 使用 `/api/skill-forge/runs/*` 完成来源提交、启动、证据规则审阅、冲突处理、契约生成和 8 类验证。创建请求必须携带 `materialTag` 和 `skillType`，并携带当前文本框中的 `focus/materialDescription`；可选 `genre/sourceProjectId` 由后端重新校验项目所有权与题材。后端将这些上下文组装进熔炼 Prompt，并按 `FOUNDATION/GENRE/TECHNIQUE/REVIEW` 生成不同 `structuredOutput`。每条规则必须由用户 `ACCEPT/EDIT/DELETE` 后才能进入契约；证据展示来自后端所有权校验后的段落，不从前端上传内容自行拼造。

`GET /api/skills/{skillId}/tests` 返回验证用例和最新结果；`GET /api/skills/{skillId}/export` 返回标准 ZIP，默认且当前固定不包含完整原始 TXT 或粘贴文本。仓库没有 springdoc/Swagger 生成物，实际路由由后端 `InfrastructureIT` 的 162 路由集合锁定。

## TXT 书籍导入与 AI 项目重建契约

创建项目页提供“从零开始 / 导入 TXT 书籍”。后者进入 `/projects/import/txt`，通过 `src/api/endpoints/txtImports.ts` 调用：

- `POST /api/imports/txt` 上传单个 `.txt`；前端执行精确 20 MiB 校验，后端重复校验文件元数据和实际流字节数。
- `POST /api/txt-imports/{importId}/parse` 选择 UTF-8、GB18030 或 GBK；UTF-8 BOM 由后端自动识别。编码不确定时必须允许用户切换并重看 Preview。
- Preview、content、PATCH chapter，以及 reorder/merge/split/whole/fixed-split 只修改 Import Job，不提前创建 Project。
- `POST /api/txt-imports/{importId}/commit` 成功后才返回正式项目；前端随后刷新项目缓存并可进入项目。

这与 Skill 熔炉限制不同：书籍建项是“单个 20 MiB TXT”，Skill 熔炉是“最多 20 个、单个 10 MiB、合计 20 MiB”。Nginx/Spring 的 25 MiB 是 multipart 请求上限，不是用户可上传 25 MiB TXT。

导入完成后，`src/api/endpoints/reconstruction.ts` 使用 `/api/projects/{projectId}/reconstruction/*` 完成 Estimate、启动、状态轮询、Pause/Resume/Cancel/Retry、Candidate 决策/撤回和 Safe Apply。进度由后端按 Chunk 与 VOLUME/ENTITY/GLOBAL/VALIDATING 等真实阶段计算；Chunk 完成时仍处于全书聚合，前端不得提前显示 100%。稳定且重复出现的人物会自动建立人物卡；具备 Evidence 且无歧义的世界事实会归并到正式世界书；可信全书摘要写入滚动大纲；伏笔候选自动登记为正式伏笔的 `CANDIDATE` 状态。伏笔只完成登记，不自动确认已埋设、发展或回收。已登记 Candidate 从上方隐藏；调用 `DELETE /api/foreshadows/{id}` 后删除台账条目并恢复关联 Candidate。单次人物提及、不确定事实和冲突仍显示为待审核。

人物响应扩展 `importance/lifecycleStatus/mergedInto/retrievalEligible`，并对接 `state-at`、`lifecycle`、`merge` 和 `purge`。Purge 是不可恢复的物理删除，界面必须显式确认；普通“退出当前创作”应使用生命周期或归档。
