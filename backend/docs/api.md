# StoryWeaver 后端 API 契约

本文档对应仓库当前 Phase 0–8、V1.5 与全局 Skill 更新。Spring MVC 运行时暴露 **128 条业务 REST 路由**；`InfrastructureIT` 会从 `RequestMappingHandlerMapping` 读取实际路由并与固定清单做精确比较，新增、删除或改动路径都会使 CI 失败。除此之外还有 `/mcp` 和 Actuator 端点。

## 1. 通用约定

| 项目 | 约定 |
|---|---|
| Base URL | 默认 `http://localhost:8080`；当前工作区 `http://localhost:18080` |
| 请求/响应 | JSON、UTF-8；SSE 和 MCP 除外 |
| 鉴权 | `Authorization: Bearer <accessToken>` |
| 无需 JWT | `/api/auth/register`、`/api/auth/login`、`/actuator/**` |
| ID | UUID 字符串 |
| 时间 | ISO-8601 UTC 时间 |
| 并发更新 | 请求体提交资源当前 `expectedVersion`，冲突返回 409 |
| 项目隔离 | 只允许项目 owner；跨用户资源按不存在处理 |
| 删除语义 | 当前版本不提供物理删除 API；项目/人物使用 `archived`，正典使用状态迁移 |

成功状态通常为 200；创建资源为 201；启动异步 Workflow 为 202。所有列表当前均返回 JSON 数组，尚未实现分页。

### 1.1 注册、登录和错误示例

```http
POST /api/auth/register
Content-Type: application/json

{"username":"author","email":"author@example.com","password":"change-me-123"}
```

注册和登录返回：

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresAt": "2026-08-03T12:00:00Z",
  "user": {"id":"<uuid>","username":"author","email":"author@example.com","role":"USER","createdAt":"..."}
}
```

`role` 为 `USER` 或 `ADMIN`，并作为同名 JWT claim 映射为 Spring Security 的 `ROLE_USER` / `ROLE_ADMIN` authority。公开注册始终创建 `USER`。

错误统一使用 `application/problem+json`：

```json
{
  "type": "urn:storyweaver:error:validation_failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/projects",
  "code": "validation_failed",
  "errors": {"name":"不能为空"}
}
```

常见状态：400 请求/校验错误，401 未登录或 JWT 无效，403 被安全层拒绝，404 路由或本用户可见范围内的资源不存在，409 乐观锁、幂等、状态机、预算、BLOCKER 或数据约束冲突。代码不会把堆栈、SQL 或密钥返回给客户端。

## 2. REST 路由总表（128）

### 2.1 Auth、项目和快照（9）

| 方法与路径 | 状态 | 请求/说明 |
|---|---:|---|
| `POST /api/auth/register` | 201 | `username` 3–50，合法 `email`，`password` 8–72 |
| `POST /api/auth/login` | 200 | `identifier` 可为用户名或邮箱；`password` |
| `GET /api/me` | 200 | 当前用户资料 |
| `POST /api/projects` | 201 | `name/genre/targetAudience/narrativePerspective/lengthType/premise/worldRules`；其余项目设置可选 |
| `GET /api/projects` | 200 | 当前用户的项目列表 |
| `GET /api/projects/{projectId}` | 200 | 项目详情 |
| `PUT /api/projects/{projectId}` | 200 | 全量字段、`archived`、`expectedVersion` |
| `DELETE /api/projects/{projectId}?expectedVersion={version}` | 204 | 仅允许所有者永久删除已归档且版本匹配的项目；关联项目数据由数据库级联删除 |
| `POST /api/projects/{projectId}/snapshots` | 201 | `{ "expectedVersion": 0 }`；快照包含项目及各模块贡献内容 |

项目字段上限：`name` 80、`genre` 80、`customGenre` 20、`premise` 10—500、`description` 300、`authorIntent` 3,000、`currentFocus` 2,000 字符；`worldRules` 是每项最多 500 字符的数组。`targetAudience` 为 `MALE/FEMALE/GENERAL`，`narrativePerspective` 为 `FIRST_PERSON/THIRD_PERSON`，`lengthType` 为 `SHORT_NOVEL/LONG_NOVEL`。响应包含全部项目偏好及 `id/version/createdAt/updatedAt`。

### 2.2 正典资产（5）

| 方法与路径 | 状态 | 请求/说明 |
|---|---:|---|
| `POST /api/projects/{projectId}/assets` | 201 | `assetType/name/content/changeSummary` |
| `GET /api/projects/{projectId}/assets` | 200 | 项目全部正典资产 |
| `PUT /api/assets/{assetId}` | 200 | `name/content/changeSummary/expectedVersion`，生成不可变历史版本 |
| `POST /api/assets/{assetId}/confirm` | 200 | `{ "expectedVersion": n }`，确认当前版本 |
| `POST /api/assets/{assetId}/deprecate` | 200 | `{ "expectedVersion": n }` |

`assetType` 必须匹配 `[A-Za-z][A-Za-z0-9_-]*` 且最多 40 字符；`name` 120，`content` 200,000，`changeSummary` 500。状态：`DRAFT/CANDIDATE/CONFIRMED/CONFLICTED/DEPRECATED`。响应同时给出 `currentVersionNo/confirmedVersionNo/currentVersion/version`。

### 2.3 人物与人物状态（6）

| 方法与路径 | 状态 | 请求/说明 |
|---|---:|---|
| `POST /api/projects/{projectId}/characters` | 201 | 人物卡，可带初始 `state` |
| `GET /api/projects/{projectId}/characters` | 200 | 人物列表 |
| `GET /api/characters/{characterId}` | 200 | 人物卡及当前状态 |
| `PUT /api/characters/{characterId}` | 200 | 人物字段、`archived/expectedVersion` |
| `GET /api/characters/{characterId}/state` | 200 | 当前状态 |
| `PUT /api/characters/{characterId}/state` | 200 | 状态字段及状态自己的 `expectedVersion` |

人物字段：`name` 必填；`aliases/role/description/personality/background/goals/appearance/notes` 可选。状态字段：`lifeStatus/currentLocation/physicalCondition/emotionalState/abilities/inventoryNotes/notes`；`lifeStatus` 为 `UNKNOWN/ALIVE/DEAD`。

### 2.4 大纲、章节和章节版本（11）

| 方法与路径 | 状态 | 请求/说明 |
|---|---:|---|
| `POST /api/projects/{projectId}/outlines` | 201 | `parentId/nodeType/title/summary/objective/sequenceNo` |
| `GET /api/projects/{projectId}/outlines` | 200 | 按项目读取大纲节点 |
| `GET /api/outlines/{outlineId}` | 200 | 单节点 |
| `PUT /api/outlines/{outlineId}` | 200 | 可编辑字段及 `expectedVersion` |
| `POST /api/projects/{projectId}/chapters` | 201 | `chapterNo/title/outlineNodeId/outline` |
| `GET /api/projects/{projectId}/chapters` | 200 | 章节列表 |
| `GET /api/chapters/{chapterId}` | 200 | 章节及当前正式版本 |
| `PUT /api/chapters/{chapterId}/outline` | 200 | `outlineNodeId/title/outline/expectedVersion` |
| `POST /api/chapters/{chapterId}/versions` | 201 | 手工提交 `title/content/summary/changeSummary/expectedVersion` |
| `GET /api/chapters/{chapterId}/versions` | 200 | 不可变版本列表 |
| `POST /api/chapters/{chapterId}/restore/{versionNo}` | 201 | `changeSummary/expectedVersion`；恢复会创建新版本，不覆盖历史 |

大纲类型：`MASTER/VOLUME/ARC/CHAPTER`。章节状态：`DRAFT/GENERATING/REVIEW_REQUIRED/WAITING_APPROVAL/CONFIRMED/ARCHIVED`。`chapterNo` 为正整数；正文最多 500,000，摘要/大纲最多 50,000 字符。

### 2.5 Skill（4）

| 方法与路径 | 状态 | 请求/说明 |
|---|---:|---|
| `POST /api/projects/{projectId}/skills` | 201 | `name/description/rules/enabled/scope/chapterId` |
| `GET /api/projects/{projectId}/skills` | 200 | Skill 列表 |
| `PUT /api/skills/{skillId}` | 200 | 全量字段及 `expectedVersion` |
| `POST /api/projects/{projectId}/skills/compose` | 200 | `{ "chapterId": "<可选 uuid>" }` |

`rules` 是非空字符串键值对象；作用域为 `BASE/PROJECT/CHAPTER`，CHAPTER 必须对应本项目章节。合成响应包含 `resolved`、逐键 `effectiveRules` 和同一优先级无法自动选择的 `conflicts`；优先级为 CHAPTER > PROJECT > BASE。

### 2.6 DeepSeek Agents（5）

| 方法与路径 | 状态 | 响应 |
|---|---:|---|
| `POST /api/projects/{projectId}/ai/planner` | 200 | 结构化 `ChapterPlan` |
| `POST /api/projects/{projectId}/ai/writer` | 200 SSE | `delta/usage/done/error` 事件 |
| `POST /api/projects/{projectId}/ai/extractor` | 200 | 结构化 `ExtractionResult` |
| `POST /api/projects/{projectId}/ai/reviewer` | 200 | 结构化 `ReviewResult` |
| `GET /api/ai/model-config` | 200 | 四类 Agent 当前模型及参数（不含 API Key） |

四个 POST 请求均使用：

```json
{"instruction":"本次任务，最多 20000 字符","context":"已构建上下文，最多 400000 字符"}
```

Planner、Extractor、Reviewer 对模型 JSON 执行结构校验和一次 JSON 修复；Writer 流式透传。Adapter 记录 token、attempt、duration 和请求 ID，使用匿名 HMAC `user_id`，不会把内部用户 UUID 直接发给模型。网络错误和 429/5xx 可重试，认证/请求错误不重试。未设置 `DEEPSEEK_API_KEY` 时调用返回明确的未配置错误；其余非模型 API 可正常运行。

Writer SSE 示例：

```text
event: delta
data: {"text":"第一段"}

event: usage
data: {"promptTokens":100,"completionTokens":50,...}

event: done
data: {"requestId":"..."}
```

### 2.7 世界书和故事事件（8）

| 方法与路径 | 状态 | 请求/说明 |
|---|---:|---|
| `POST /api/projects/{projectId}/worldbook-entries` | 201 | 创建世界书条目 |
| `GET /api/projects/{projectId}/worldbook-entries` | 200 | 条目列表 |
| `PUT /api/worldbook-entries/{entryId}` | 200 | 全量更新及 `expectedVersion` |
| `POST /api/projects/{projectId}/worldbook/preview` | 200 | 激活预览、原因、分数、Token 裁剪和降级原因 |
| `POST /api/projects/{projectId}/story-events` | 201 | 创建故事事件 |
| `GET /api/projects/{projectId}/story-events` | 200 | 事件列表 |
| `PUT /api/story-events/{eventId}` | 200 | 全量更新及 `expectedVersion` |
| `POST /api/projects/{projectId}/story-events/search` | 200 | 结构化 + 语义综合检索 |

世界书创建/更新字段：`title/content/active/constantEnabled/vectorEnabled/keywords/priority/scopeType/scopeRefId/visibilityType/visibilityRefId`。作用域：`PROJECT/CHAPTER/CHARACTER`；可见性：`ALL/AUTHOR_ONLY/CHARACTER_ONLY`。Preview 请求字段为 `query`，可选 `chapterId/viewpointCharacterId/tokenBudget/topK`；默认预算 4,000、topK 8。

事件字段：`chapterId/participantIds/knownByIds/location/storyTime/action/result/importance/evidenceParagraph`；`importance` 为 0–1。检索请求为 `query/participantIds/location/chapterNo/topK`，响应逐项返回语义、参与者、地点、章节距离分数及命中原因。Embedding 状态为 `NOT_REQUESTED/AVAILABLE/UNAVAILABLE`；模型缺失时响应显式给出 `embeddingAvailable=false/degradedReason`，并继续结构化检索。

### 2.8 Workflow、一致性查询（11）

| 方法与路径 | 状态 | 请求/说明 |
|---|---:|---|
| `POST /api/chapters/{chapterId}/workflows` | 202 | Header `Idempotency-Key`；`viewpointCharacterId/instruction` |
| `GET /api/workflows/{runId}` | 200 | 状态、上下文、步骤、草稿、候选、审查、版本 |
| `GET /api/workflows/{runId}/events` | 200 SSE | `Last-Event-ID` 或 `afterEventId` 重放 |
| `POST /api/workflows/{runId}/cancel` | 200 | 请求取消 |
| `POST /api/workflows/{runId}/approve` | 200 | 人工审批和原子提交 |
| `POST /api/workflows/{runId}/request-revision` | 200 | `{ "revisedDraft":"..." }` |
| `POST /api/workflows/{runId}/reextract` | 200 | 同上；重新提取、校验、审查 |
| `GET /api/projects/{projectId}/story-facts?status=ACCEPTED` | 200 | 可选 `status` |
| `GET /api/projects/{projectId}/item-ownership` | 200 | 当前道具归属 |
| `GET /api/characters/{characterId}/knowledge` | 200 | 人物知识边界 |

正常生成路径：

```text
CREATED → PREFLIGHT → CONTEXT_READY → PLANNING → PLAN_READY → WRITING
→ TEXT_READY → EXTRACTING → VALIDATING → REVIEWING → WAITING_APPROVAL
```

终态还包括 `COMPLETED/BLOCKED/FAILED/CANCELLED/ROLLED_BACK`，修订态为 `REVISION_REQUIRED`。同一项目最多一个活跃生成；相同用户 + `Idempotency-Key` 返回原运行。事件先写 PostgreSQL 再发送，SSE 重连不会依赖进程内缓冲。

审批请求：

```json
{
  "expectedVersion": 14,
  "changeSummary": "确认第一章",
  "acceptedFactIndexes": [0],
  "characterStateChanges": [],
  "itemChanges": [],
  "timelineEvents": [],
  "knowledgeChanges": []
}
```

每项状态变更必须带正文证据；人物状态变更还要带人物状态的 `expectedVersion`。审批会再次运行确定性校验；任何未解决 `BLOCKER`、过期 Context 或版本冲突均返回 409。成功时在一个事务内提交 ChapterVersion、接受/拒绝事实、事件、人物状态、道具归属、人物知识和 Workflow 状态；失败全部回滚并标记 `ROLLED_BACK`。

### 2.9 用量、预算和 MCP 审计（6）

| 方法与路径 | 状态 | 说明 |
|---|---:|---|
| `GET /api/projects/{projectId}/costs` | 200 | 估算/实际成本、请求数、未计价数 |
| `GET /api/projects/{projectId}/budget` | 200 | 项目预算；首次读取会得到默认值 |
| `PUT /api/projects/{projectId}/budget` | 200 | 五项限制及 `expectedVersion` |
| `GET /api/pricing-rules` | 200 | 当前调用者可读的全局价格规则；API 不提供写入 |
| `GET /api/projects/{projectId}/usage` | 200 | LLM 请求 token、缓存、重试、延迟和成本快照 |
| `GET /api/projects/{projectId}/mcp-audit` | 200 | MCP Tool/Resource/Prompt 调用审计 |

预算字段：`taskTokenLimit`、`userDailyCostLimit`、`projectCostLimit`、`writerOutputTokenLimit`、`plannerReasoningTokenLimit`。货币金额使用十进制定点数。价格必须由运维直接维护 `pricing_rule`；历史 `usage_record` 固化命中的规则版本和金额，没有匹配价格的调用进入 `unpricedRequests`，不会伪造为零成本。

### 2.10 全局 Skill 与文本熔炉（26）

全局 Skill 不依赖当前小说项目。`/api/skills` 管理私有/内置的版本化行为契约；只有 `VALIDATED` 版本可作为项目基础 Skill 绑定。

| 方法与路径 | 状态 | 说明 |
|---|---:|---|
| `GET /api/skills` | 200 | 当前用户可见的内置和私有全局 Skill |
| `POST /api/skills` | 201 | 创建契约草稿；slug 仅允许小写字母、数字和连字符 |
| `GET /api/skills/{skillId}` | 200 | Skill 当前契约与状态 |
| `GET /api/skills/{skillId}/versions` | 200 | 不可变版本列表 |
| `POST /api/skills/{skillId}/versions` | 201 | 新建草稿版本 |
| `POST /api/skills/{skillId}/validate` | 200 | 验证完整 Skill Contract |
| `GET /api/skills/{skillId}/tests` | 200 | 8 类测试用例及最新运行结果 |
| `GET /api/skills/{skillId}/export` | 200 ZIP | 导出 `SKILL.md/references/tests/LICENSE`；不包含原始 TXT/粘贴原文 |
| `DELETE /api/skills/{skillId}` | 204 | 归档私有 Skill |
| `POST /api/skill-forge/runs` | 201 | 确认素材权利后创建私有文本熔炼任务；提交 `materialTag`、`skillType`、`focus`、`materialDescription`、可选 `genre/sourceProjectId` 动态上下文 |
| `GET /api/skill-forge/runs/{runId}` | 200 | 熔炼状态、设置与候选契约 |
| `GET /api/skill-forge/runs/{runId}/events` | 200 | 可持久化步骤事件（当前为列表，不是 SSE） |
| `POST /api/skill-forge/runs/{runId}/sources/text` | 201 | 添加 200–50,000 字手写/粘贴文本 |
| `POST /api/skill-forge/runs/{runId}/sources/txt` | 201 | 多文件上传；最多 20 个、单个 10 MiB、合计 20 MiB，UTF-8/GB18030 |
| `GET /api/skill-forge/runs/{runId}/sources` | 200 | 来源元数据、编码、哈希和段落数，不返回完整原文 |
| `DELETE /api/skill-forge/runs/{runId}/sources/{sourceId}` | 204 | 熔炼开始前删除来源 |
| `POST /api/skill-forge/runs/{runId}/start` | 200 | 预处理、六维抽取与交叉验证，进入逐条审阅 |
| `GET /api/skill-forge/runs/{runId}/rules` | 200 | 候选原子规则及来源段落、摘录哈希和授权摘录 |
| `PATCH /api/skill-forge/runs/{runId}/rules/{ruleId}` | 200 | `ACCEPT/EDIT/DELETE`；编辑内容标记为用户修改 |
| `POST /api/skill-forge/runs/{runId}/resolve-conflicts` | 200 | 对冲突规则保留、编辑或删除 |
| `POST /api/skill-forge/runs/{runId}/generate-contract` | 200 | 仅用已接受规则生成契约和测试集；契约含动态熔炼上下文与按 Skill 类型区分的输出结构 |
| `POST /api/skill-forge/runs/{runId}/validate` | 200 | 运行 3 典型 + 冲突/边界/越界/过拟合/诚实边界测试并发布验证版本 |
| `POST /api/skill-forge/runs/{runId}/cancel` | 204 | 取消未结束任务 |
| `GET /api/projects/{projectId}/skill-bindings` | 200 | 项目全局 Skill 绑定 |
| `POST/DELETE /api/projects/{projectId}/skill-bindings/foundation` | 200/204 | 替换或解除基础 Skill |

TXT 原始字节、规范化文本与段落证据只对所有者可见并保存在数据库中；Skill Contract 和默认导出包只保存来源 ID/哈希，不嵌入原文。UTF-16、错误扩展名、无效编码、超限文件和未确认权利均返回 400 Problem Details。

### 2.11 书稿导入与 Git 导出（11）

| 方法与路径 | 状态 | 说明 |
|---|---:|---|
| `POST /api/projects/{projectId}/imports` | 201 | 上传 TXT/Markdown/DOCX/ZIP，创建导入任务 |
| `GET /api/projects/{projectId}/imports` | 200 | 项目导入任务列表 |
| `GET /api/imports/{importId}` | 200 | 导入状态、章节切分和候选详情 |
| `PUT /api/imports/{importId}/chapters` | 200 | 修订服务端章节切分结果 |
| `POST /api/imports/{importId}/extract` | 200 | 从确认切分中抽取候选资料 |
| `POST /api/imports/{importId}/retry` | 200 | 重试失败的导入阶段 |
| `POST /api/imports/{importId}/cancel` | 200 | 取消未完成导入 |
| `POST /api/imports/{importId}/complete` | 200 | 完成导入并提交已确认内容 |
| `POST /api/imports/{importId}/candidates/decide` | 200 | 接受或拒绝导入候选 |
| `POST /api/imports/{importId}/aliases/merge` | 200 | 合并人物别名候选 |
| `GET /api/projects/{projectId}/exports/git` | 200 ZIP | 鉴权导出 Git 目录结构 ZIP |

导入文件不是上传后立即成为正典。章节切分、候选资料和别名必须经过对应状态与决定接口；失败、取消或未确认候选不会被当作正式事实。

### 2.12 伏笔与影响报告（7）

| 方法与路径 | 状态 | 说明 |
|---|---:|---|
| `POST /api/projects/{projectId}/foreshadows` | 201 | 创建伏笔记录 |
| `GET /api/projects/{projectId}/foreshadows` | 200 | 查询项目伏笔台账 |
| `PUT /api/foreshadows/{id}` | 200 | 以 `expectedVersion` 更新伏笔 |
| `POST /api/foreshadows/{id}/transition` | 200 | 执行伏笔状态迁移 |
| `POST /api/chapters/{chapterId}/impact-reports` | 201 | 为章节生成影响报告 |
| `GET /api/chapters/{chapterId}/impact-reports` | 200 | 查询章节影响报告列表 |
| `GET /api/impact-reports/{id}` | 200 | 查询单份影响报告 |

### 2.13 滚动大纲（3）

| 方法与路径 | 状态 | 说明 |
|---|---:|---|
| `GET /api/projects/{projectId}/rolling-outline` | 200 | 获取当前滚动大纲 |
| `PUT /api/projects/{projectId}/rolling-outline` | 200 | 创建或更新滚动大纲与窗口 |
| `POST /api/projects/{projectId}/rolling-outline/advance` | 200 | 在章节推进后移动规划窗口 |

### 2.14 章节批次与剧情门（9）

| 方法与路径 | 状态 | 说明 |
|---|---:|---|
| `POST /api/projects/{projectId}/chapter-batches` | 201 | 创建 1—3 章串行生产批次 |
| `GET /api/projects/{projectId}/chapter-batches` | 200 | 项目批次列表 |
| `GET /api/chapter-batches/{id}` | 200 | 批次、条目和当前进度 |
| `POST /api/chapter-batches/{id}/pause` | 200 | 暂停可暂停的批次 |
| `POST /api/chapter-batches/{id}/resume` | 200 | 恢复暂停批次 |
| `POST /api/chapter-batches/{id}/cancel` | 200 | 取消未完成批次 |
| `GET /api/chapter-batches/{id}/gates` | 200 | 查询批次重大剧情门 |
| `POST /api/story-gates/{id}/approve` | 200 | 批准剧情门并允许继续 |
| `POST /api/story-gates/{id}/reject` | 200 | 拒绝剧情门并阻止自动推进 |

章节批次不会绕过单章工作流审批。剧情门用于在重大转折处增加额外人工决定；未决或被拒绝的门不能被批次静默跳过。

### 2.15 章节分支（5）

| 方法与路径 | 状态 | 说明 |
|---|---:|---|
| `POST /api/chapters/{chapterId}/branches` | 201 | 创建章节替代叙事分支 |
| `GET /api/chapters/{chapterId}/branches` | 200 | 查询章节分支列表 |
| `GET /api/chapter-branches/{id}` | 200 | 查询分支及其版本 |
| `POST /api/chapter-branches/{id}/versions` | 201 | 追加分支不可变版本 |
| `POST /api/chapter-branches/{id}/promote-impact` | 200 | 将选定分支影响提升为后续依据 |

### 2.16 模型尝试与模型健康（2）

| 方法与路径 | 状态 | 说明 |
|---|---:|---|
| `GET /api/workflows/{runId}/model-attempts` | 200 | 查询工作流各模型尝试、结果与耗时 |
| `GET /api/ai/model-health` | 200 | 查询模型与相关能力健康状态 |

## 3. MCP Streamable HTTP

```http
POST /mcp
Authorization: Bearer <JWT>
Content-Type: application/json
Accept: application/json, text/event-stream
MCP-Protocol-Version: 2025-11-25
```

传输为 stateless Streamable HTTP。已验证方法：`initialize`、`tools/list`、`tools/call`、`resources/templates/list`、`resources/read`、`prompts/list`、`prompts/get`。

### 3.1 Tools（6）

| Tool | 参数 | 权限/效果 |
|---|---|---|
| `get_character_state` | `characterId` | 只读人物状态 |
| `get_character_knowledge` | `characterId` | 只读人物知识 |
| `get_worldbook_entries` | `projectId` | 只读世界书 |
| `get_recent_story_events` | `projectId, limit?` | 只读最近事件 |
| `get_item_owner` | `projectId, itemKey` | 只读道具当前归属 |
| `save_candidate_fact` | `projectId, content, evidence`；可选 `factKey/paragraphKey/requestKey` | 只创建 `source=MCP,status=CANDIDATE` |

唯一写 Tool 要求非空证据；`requestKey` 可幂等。MCP 没有接受事实、提交章节、改变人物状态或修改正典的能力。协议/业务失败通过 MCP `isError` 返回，同时审计成功或失败结果。

### 3.2 Resource templates（5）和 Prompts（3）

- `story://projects/{projectId}/author-intent`
- `story://projects/{projectId}/current-outline`
- `story://projects/{projectId}/recent-summary`
- `story://characters/{characterId}/card`
- `story://characters/{characterId}/knowledge`

Prompts：`plan-next-chapter(projectId, chapterId)`、`review-chapter(projectId, chapterId)`、`query-story-state(projectId)`。全部复用 JWT 当前用户和 REST 相同的项目所有权校验。

## 4. Actuator 与健康检查

| 路径 | 用途 |
|---|---|
| `GET /actuator/health` | 聚合健康状态 |
| `GET /actuator/health/liveness` | 容器存活探针 |
| `GET /actuator/health/readiness` | 容器就绪探针 |
| `GET /actuator/info` | 应用名、当前 Phase 8 |
| `GET /actuator/metrics` | Micrometer 指标目录 |
| `GET /actuator/prometheus` | Prometheus 抓取文本 |

这些端点当前公开，详细健康信息默认仅向已授权调用者展示。公网部署必须由网关/防火墙限制 Actuator 网络可达范围。

## 5. 契约边界与未实现项

当前没有 OpenAPI/Swagger UI、分页、Refresh Token、注销/吊销列表、PricingRule 写 API、管理后台、Webhook 或 GraphQL。任何客户端都应以本文件和 `InfrastructureIT` 的 128 路由契约为准，不能依赖不存在的设计稿接口。
