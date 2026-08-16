# StoryWeaver / 文脉
## TXT 导入后 AI 全项目重建、项目演化与 RAG 生命周期 — Codex 更新文档

> 文档版本：V1.2  
> 前置能力：沿用现有“TXT 书籍导入并创建项目”，单文件 `.txt` ≤ 20 MB  
> 更新目标：TXT 导入成功后，用户可选择“AI 自动构建完整项目”，让系统分层读取整本书，并尽可能自动填充左侧项目导航中的内容模块。  
> 核心原则：**原文先可靠导入；AI 负责反向理解与结构重建；高风险结果默认 Candidate；重要结论必须有 Evidence；用户拥有最终确认权。**

> 实施状态（2026-08-13）：本文件主体保留产品设计与验收意图；以下“Codex 实际实现记录”为当前代码事实。已新增 V18 与 V19；V19 将拆书伏笔自动登记到正式台账，并支持取消后恢复 Candidate。未运行 Live Model 或新数据集评分，相关指标保持 `null / Not Run`。

## 0. Codex 实际实现记录（2026-08-13）

### 0.1 已落地

- `ReconstructionCandidatePolicyEngine` 为重建候选计算 `suggestedAction`、目标实体、主体名、人物重要度、检索资格和策略原因；候选支持接受、拒绝、冲突、应用及撤回。
- 人物新增 `PROTAGONIST / MAJOR / SUPPORTING / MINOR / MENTION_ONLY` 重要度，以及 `CANDIDATE / ACTIVE / INACTIVE / DECEASED / MISSING / LEFT_STORY / MERGED / REJECTED / ARCHIVED / PURGED` 生命周期。合并会保留目标，归档/合并/清除会退出当前检索。
- 人物状态、人物关系、人物知识、物品归属和故事事实支持章节有效区间、取代/撤回状态与当前检索资格；`GET /api/characters/{characterId}/state-at` 可按目标章节读取状态。
- Candidate 提供显式撤回 API；撤回或被取代的候选不再进入当前检索。
- StoryEvent、Worldbook 与 Context 构建加入目标章节、有效区间和生命周期过滤；Context 按 P0—P7 分层，结构化当前状态负责“现在什么是真的”，RAG 负责补充相关历史证据。
- 滚动大纲保存输入指纹、刷新状态与失效原因；仅由已确认章节和当前结构化状态刷新，来源变化后旧快照标记为失效。
- 伏笔状态扩展为 `CANDIDATE / PLANTED / DEVELOPING / DUE / PARTIALLY_RESOLVED / RESOLVED / ABANDONED / REJECTED`；到期只产生提醒，已解决项不再作为待回收上下文。
- 人物清除采用准备检查与显式确认；V18 将需要保留历史的关联改为可空/`ON DELETE SET NULL`，清除前移除当前引用并失效相关 Context，随后物理删除人物。
- 重建验证阶段会按全书 Candidate 归并人物：稳定姓名跨候选重复出现才自动创建一张正式人物卡，并汇总背景/状态描述与 Evidence；单次提及、非人名、同名歧义继续留在 Candidate。自动建卡使用事务级 advisory lock 和完成标记，避免启动恢复与定时恢复并发重复建卡。
- 同一验证阶段会将具备 Evidence、且不包含“不确定/冲突/未经证实”等歧义标记的世界事实归并到正式条目 `TXT AI 重建 · 世界设定`；可信 `OUTLINE/PROJECT_OVERVIEW` 写入第一个至最后一个导入章节范围的滚动大纲。两类资产共用事务锁与 `PROJECT_ASSET_MATERIALIZATION` 完成标记；手动保存大纲会清除 AI 所有权哈希，后续重建不覆盖用户版本。
- 新增 `temporal-rag`、`entity-lifecycle`、`rolling-outline`、`foreshadow` 四组原创 Draft 数据集；Ground Truth 尚未复核，未发布指标。

### 0.2 Migration 与真实 API

先新增 `V18__project_evolution_and_rag_lifecycle.sql` 扩展 Candidate 策略、人物生命周期、章节时间区间、当前检索资格、滚动大纲刷新元数据和伏笔生命周期；随后新增 `V19__reconstruction_foreshadow_materialization.sql`，为伏笔建立来源 Candidate 关联并回填既有候选。没有修改 V0—V18。

在 V1.1 重建 API 基础上新增或扩展：

```text
POST /api/projects/{projectId}/reconstruction/candidates/{candidateId}/revoke
GET  /api/characters/{characterId}/state-at?chapterNo={chapterNo}
POST /api/characters/{characterId}/lifecycle
POST /api/characters/{characterId}/merge
POST /api/characters/{characterId}/purge
```

既有世界书、事件、滚动大纲和伏笔 API 继续复用，DTO 与查询语义由 V18 扩展，不复制第二套资产系统。

### 0.3 当前安全边界

- Safe Apply 当前自动应用低风险章节重建元数据；人物、世界书和滚动大纲由独立确定性物化门禁处理。单次人物提及、不确定或冲突世界硬规则、唯一物品、人物知识、伏笔和 Skill 仍需人工审核，不会因模型输出直接成为正式资产。
- 正式 Character 已接通上述保守自动建卡；更细的人物结构字段仍需复核。Worldbook、Outline、Foreshadow 与 Project-local Skill 的“审核后一键写入”尚未全部接通，不能把接受 Candidate 等同于所有正式资产已自动创建。
- 旧数据退出当前检索依赖结构化生命周期、有效区间和 `retrievalEligible`；仓库没有伪造 Embedding 重建完成状态。需要重新向量化的条目必须由真实后续任务处理。
- 本次没有调用真实 DeepSeek 全项目重建，也没有运行高成本 Live Eval。四组新评测夹具为 `DRAFT_NOT_REVIEWED`，所有新指标为 `null / Not Run`。

### 0.4 验证结果

- Backend：`./mvnw clean verify` 通过，35 次单元/架构测试与 20 次集成测试通过，Flyway 当前版本 V19，运行时 162 条业务 REST 路由契约通过。
- Frontend：`pnpm lint`、`pnpm typecheck`、`pnpm test:unit`、`pnpm build` 均通过；21 个测试文件、52 项单元测试通过。
- 根目录：`docker compose config` 通过。
- Chromium TXT Import E2E：上传、Preview、改名、拆分、合并与 Commit 通过；该用例使用 Mock API，不代表真实 DeepSeek 全栈分析已经执行。

---

# 1. 功能目标

当前 TXT 导入：

```text
TXT
→ 编码检测
→ 自动分章
→ 创建 Project
→ 创建 Chapter
```

升级后：

```text
TXT
→ 可靠导入章节
→ AI 分层读取整本书
→ 项目概览
→ 创作工作台
→ 人物
→ 世界书
→ Reverse Outline
→ 章节摘要 / POV / Event / StoryFact
→ 伏笔台账
→ Project Skill
→ 连续写作 Context
→ 项目可直接继续创作
```

本功能不是“总结一本小说”，而是：

```text
Existing Novel
→ Reverse Engineering
→ Structured Story Model
→ Editable StoryWeaver Project
→ Canon / Memory / Retrieval Ready
→ Continue Writing
```

---

# 2. 左侧导航自动填充映射

当前左侧模块：

```text
连续写作
伏笔台账

项目概览
创作工作台
人物
世界书
大纲
章节
Skill
模型与费用
```

AI 分析完成后的映射：

| 模块 | 自动处理 | 写入策略 |
|---|---|---|
| 项目概览 | 书名、简介、题材、基调、核心冲突、主要人物、故事阶段 | Candidate / Suggested Update |
| 创作工作台 | 当前剧情焦点、最近事件、未解决冲突、下一步建议 | 工作状态候选 |
| 人物 | 人物、别名、关系、身份、动机、状态、Progression、知识边界 | Candidate |
| 世界书 | 地点、势力、道具、规则、术语、能力体系、历史事件 | Candidate |
| 大纲 | 总纲、卷纲、Story Arc、章纲、Scene/Beat（深度模式） | Candidate |
| 章节 | 原文直接导入；AI 补摘要、POV、地点、人物、事件、标签 | 原文 Confirmed；AI 元数据 Candidate |
| Skill | 从导入文本提炼 Project-local Skill | DRAFT / Candidate |
| 伏笔台账 | 埋设、推进、回收、未回收伏笔及证据 | Candidate |
| 连续写作 | 根据最后若干章和最终状态生成 Continuation Context | Suggestion |
| 模型与费用 | 显示本次分析真实 Token / Cost / Retry / Duration | 系统真实 Usage |

注意：

**“模型与费用”不能由模型生成小说内容后猜测填充，只能读取真实 Usage / Pricing。**

---

# 3. 用户流程

TXT 项目创建完成后：

```text
TXT 导入完成

《项目名称》
共 812 章
约 286 万字

[直接进入项目]

[✨ AI 自动构建完整项目]
自动分析：
人物 / 世界书 / 大纲 / 章节摘要 / 伏笔 / Skill / 连续写作状态

预计模型调用：xxx
预计 Token：xxx
预计费用：xxx

[开始分析]
```

默认不能自动产生大额模型费用。

必须由用户显式确认。

---

# 4. 分析模式

## QUICK

快速建立：

```text
项目概览
主要人物
主要地点 / 势力
章节摘要
粗粒度大纲
最后剧情状态
```

## STANDARD

默认推荐：

```text
项目概览
主要 / 次要人物
人物关系
人物状态
世界书
卷纲 / Arc / 章纲
章节摘要
Story Event
伏笔
Project Skill
连续写作 Context
```

## DEEP

额外：

```text
Character Knowledge
人物 Progression
复杂关系变化
跨章时间线
唯一物品流转
伏笔生命周期
主题 / 意象
细粒度 StoryFact
更完整 Skill Evidence
一致性基线
```

DEEP 启动前必须展示预计 Token 与预计最大费用。

---

# 5. 禁止整本一次塞给 LLM

禁止：

```text
20 MB TXT
→ 一个 Prompt
→ “分析全部人物、世界观、大纲和伏笔”
```

必须采用：

```text
Hierarchical Book Reconstruction
```

原因：

- 超出或逼近上下文限制；
- 单次输出无法承载整书结构；
- 任一失败会让整次任务失效；
- 无法单章重试；
- 无法保留精确 Evidence；
- 别名、时间线、伏笔必须跨章融合；
- 成本和重试不可控。

---

# 6. 全书 AI 重建 Pipeline

```text
已导入 Chapter 原文
        ↓
A. Deterministic Preprocess
        ↓
B. Chapter / Chunk Analysis
        ↓
C. Volume / Arc Aggregation
        ↓
D. Global Entity Resolution
        ↓
E. Timeline / Relationship / Knowledge Reconstruction
        ↓
F. Project Modules Candidate Generation
        ↓
G. Foreshadow + Skill + Continuation
        ↓
H. Cross-module Consistency Validation
        ↓
I. Candidate Review / Safe Apply
        ↓
Reconstruction READY
```

---

# 7. Phase A：确定性预处理

不调用 LLM。

对每章生成：

```text
chapterId
chapterIndex
volume
title
characterCount
paragraphCount
contentHash
```

并建立：

```text
Source Anchor
```

例如：

```text
chapterId
paragraphKey
startOffset
endOffset
contentHash
```

后续任何重要 Candidate 尽量指向原文 Anchor。

---

# 8. Chunk 策略

短章：

```text
一章 = 一个分析单元
```

长章：

```text
Chapter
→ Chunk 1
→ Chunk 2
→ Chunk 3
```

按 Token Budget + 段落边界切分。

保存：

```text
chunkId
chapterId
sequence
textHash
paragraphRange
tokenEstimate
```

---

# 9. Chapter Analyzer

局部输出：

```json
{
  "chapterId": "...",
  "summary": "...",
  "povCharacters": [],
  "locations": [],
  "charactersMentioned": [],
  "newCharacters": [],
  "characterStateChanges": [],
  "relationshipChanges": [],
  "items": [],
  "worldFacts": [],
  "storyEvents": [],
  "knowledgeChanges": [],
  "foreshadowingCandidates": [],
  "resolvedForeshadowingCandidates": [],
  "timelineClues": [],
  "writingSignals": [],
  "sourceEvidence": []
}
```

要求：

```text
Structured Output
Schema Validation
Evidence Validation
Retry
Failure Artifact
```

局部结果不能直接创建正式 Character / Worldbook。

---

# 10. Global Entity Resolution

章节级会出现：

```text
同一个人物多个名字
称呼
代号
简称
别名
```

必须做：

```text
Local Entity
→ Normalize
→ Alias Candidate
→ Context Similarity
→ Co-occurrence
→ Cross-chapter Evidence
→ MERGE / KEEP_SEPARATE / NEEDS_REVIEW
```

不确定时：

```text
NEEDS_REVIEW
```

禁止为了减少重复而强行 Merge。

---

# 11. 人物自动重建

人物页自动生成：

```text
姓名
别名
身份
所属组织
首次出现
最后出现
重要程度
外貌（仅原文明示）
性格（推断需标记）
动机
能力
弱点
关系
最终状态
Progression
Character Knowledge（DEEP）
Evidence
```

事实类型：

```text
DIRECT_FACT
MODEL_INFERENCE
USER_CONFIRMED
```

例如：

```text
性格：谨慎
AI 推断 · Evidence 6 处
```

不能伪装成原文明示事实。

---

# 12. Character Knowledge

DEEP 模式下重建：

```text
角色知道什么
何时知道
通过哪个事件知道
证据在哪一章
```

结构：

```text
subjectCharacter
factId
learnedAtChapter
sourceEvent
confidence
evidence
```

用于续写时避免：

```text
知道不该知道的信息
忘记已经知道的信息
```

---

# 13. 世界书自动重建

分类：

```text
LOCATION
FACTION
ITEM
RULE
TERM
ABILITY_SYSTEM
HISTORY
RACE
TECHNOLOGY
CUSTOM
```

每条：

```text
name
aliases
category
description
rules
relatedCharacters
relatedEntries
firstMention
lastMention
importance
activationKeywords
evidence
```

世界硬规则必须区分：

```text
HARD_RULE
SOFT_RULE
OBSERVED_PATTERN
SPECULATION
```

只有高证据规则才可以建议升级为 HARD_RULE。

---

# 14. 唯一物品

识别：

```text
唯一武器
钥匙
遗物
信物
重要文书
特殊道具
```

建立：

```text
Item Ownership Timeline
```

例如：

```text
Chapter 10 → A
Chapter 22 → B
Chapter 53 → LOST
```

作为后续 Consistency Baseline。

---

# 15. 项目概览自动填充

自动建议：

```text
项目名称
原文件名
总章节
总字数
卷数
题材
目标读者候选
叙事视角
篇幅
项目简介
一句话故事
核心矛盾
故事背景
主要人物
主要势力
核心规则
作品基调
当前故事阶段
TXT 导入时间
AI 重建版本
```

如果用户已经手填某字段：

```text
不得静默覆盖
```

只能创建：

```text
Suggested Update
```

---

# 16. 创作工作台自动初始化

根据最后 3—10 章生成：

```text
当前剧情焦点
当前主要人物
当前地点
最近重大事件
当前 Arc
未完成目标
未解决冲突
重要未回收伏笔
下一步候选方向
```

“下一步”必须标：

```text
AI Suggestion
```

不能自动创建正式下一章。


---

# 17. Reverse Outline

TXT 已经有正文，因此这里不是“AI 创作大纲”，而是：

```text
正文
→ 反向重建作品结构
```

层级尽量生成：

```text
全书总纲
→ Volume
→ Story Arc
→ Chapter Outline
→ Scene / Beat（DEEP）
```

如果 TXT 未完结：

```text
projectStoryStatus = IMPORTED_IN_PROGRESS
```

不能强行生成不存在的最终结局。

---

# 18. 全书总纲

生成：

```text
故事起点
主角初始状态
核心目标
主要矛盾
重大转折
高潮（若已有）
TXT 结束时的故事状态
未解决线程
```

---

# 19. Arc Candidate

即使原文没有显式 Arc，也可以按剧情阶段反向聚类：

```text
title
chapterRange
goal
conflict
majorCharacters
turningPoints
result
openThreads
```

必须是：

```text
CANDIDATE
```

---

# 20. 章节自动增强

正文：

```text
Confirmed Imported Source
```

AI 只能补充元数据：

```text
summary
chapterGoal
pov
locations
characters
keyEvents
stateChanges
newInformation
foreshadowing
resolvedThreads
endingHook
tags
analysisVersion
```

不改原文。

---

# 21. 伏笔台账

伏笔不能在单章直接确认。

局部只产生：

```text
FORESHADOW_CANDIDATE
```

全局阶段再判断：

```text
PLANTED
DEVELOPING
RESOLVED
PARTIALLY_RESOLVED
UNRESOLVED
ABANDONED_CANDIDATE
```

每条保存：

```text
title
description
plantedAt
developmentChapters
resolvedAt
status
relatedCharacters
relatedWorldbook
confidence
evidence
```

关键原则：

```text
Local Candidate
→ Cross-chapter Evidence
→ Global Resolution
```

避免把氛围细节误判成伏笔。

---

# 22. Skill 自动熔炼

TXT 属于用户主动导入内容。

如果用户确认拥有相应文本使用权，可生成：

```text
PROJECT_LOCAL
DRAFT
```

类型的项目写作 Skill。

复用现有 Skill Forge：

```text
Source
→ Atomic Rule
→ Evidence
→ Conflict
→ Contract
→ Validation
```

不要创建第二套 Skill 引擎。

提取：

```text
句式节奏
段落长度
对话密度
POV 控制
环境描写
动作描写
人物出场
冲突推进
信息释放
章尾
反模式
```

若不是用户本人作品：

只允许提炼高层写作特征。

不得宣称：

```text
精确复刻某位在世作者
```

---

# 23. 连续写作 Context

AI 重建完成后自动形成：

```text
Continuation Context
```

输入：

```text
最后 5—10 章
人物最终状态
Character Knowledge
当前世界状态
当前 Arc
未解决冲突
未解决伏笔
唯一物品
作者意图
Project Skill
```

输出：

```text
Last Stable Story State
Active Characters
Current Locations
Open Conflicts
Unresolved Foreshadowing
Knowledge Boundaries
Unique Items
Hard Constraints
Next Chapter Suggestions
```

用户点击“连续写作”时无需再从全书零开始理解。

---

# 24. Candidate Staging Area

全书分析完成后先展示：

```text
AI 已理解整本书

人物候选：43
世界书候选：127
故事 Arc：9
Story Events：463
伏笔：34
Skill Rules：58

高置信度：612
待审核：47
冲突：8
低置信度：21
```

操作：

```text
[一键应用安全结果]
[逐项审核]
[只看低置信度]
[只看冲突]
```

---

# 25. Safe Apply

可以一键自动应用的低风险数据：

```text
章节摘要
章节 POV
章节标签
明确首次出现
明确人物别名
高置信度地点
高置信度 Story Event
```

默认不能自动 Confirm 的高风险数据：

```text
世界硬规则
隐藏动机
Character Knowledge
唯一物品 Ownership
伏笔
关系推断
Project Skill
```

这些保持 Candidate。

---

# 26. Evidence

每条重要结果尽量可点：

```text
查看依据
```

例如：

```text
人物性格：谨慎
Evidence：
第12章 / 第18段
第27章 / 第4段
第31章 / 第22段
```

Candidate 至少保存：

```text
confidence
evidenceCount
sourceCoverage
inferenceType
sourceAnchors
```

置信度：

```text
HIGH
MEDIUM
LOW
```

---

# 27. 全局一致性复核

候选生成完后必须运行：

```text
Imported Book Consistency Validation
```

检查：

```text
同名不同人
Alias 错 Merge
人物重复
地点重复
世界规则互相冲突
时间线矛盾
唯一物品同时归属
人物最终状态冲突
Character Knowledge 时间错误
伏笔回收早于埋设
Outline Chapter Range 错误
```

重要冲突必须回查：

```text
原始 Chapter Evidence
```

不能只让 Reviewer 阅读之前 AI 生成的摘要，否则错误会层层传播。

---

# 28. Reconstruction Job

建议新增独立：

```text
BookReconstructionJob
```

状态：

```text
CREATED
ESTIMATING
WAITING_USER_CONFIRMATION
QUEUED
PREPROCESSING
CHAPTER_ANALYSIS
VOLUME_AGGREGATION
ENTITY_RESOLUTION
GLOBAL_RECONSTRUCTION
FORESHADOW_ANALYSIS
SKILL_DISTILLATION
VALIDATING
WAITING_REVIEW
APPLYING
COMPLETED
```

异常：

```text
PAUSED
PAUSED_BUDGET
PARTIAL
CANCELLED
FAILED
```

---

# 29. Pause / Resume

20MB 小说可能是长任务。

必须支持：

```text
暂停
恢复
取消
关闭浏览器后后台继续
失败章节单独重试
Backend Restart 后恢复
```

每一步保存：

```text
checkpoint
processedChapter
contentHash
analysisVersion
promptVersion
model
resultHash
```

恢复时不从第 1 章重跑。

---

# 30. 分批处理

章节局部分析可以有限并行：

```text
concurrency = 2—5
```

配置化。

必须有：

```text
Rate Limit
Concurrency Limit
Retry
Exponential Backoff
Budget Limit
```

禁止：

```text
812 章同时发送 812 个模型请求
```

如果当前 StoryWeaver 已有 Job / Workflow 基础设施：

优先复用。

如果确实缺少长批任务能力，可以参考 chunk-oriented processing，但不要为了一个功能引入第二套庞大编排框架。

---

# 31. 模型路由

按职责而不是“全程一个最贵模型”：

```text
Chapter Analyzer
→ Structured Extraction 稳定 / 成本优先

Entity Resolver
→ 推理能力优先

Outline Reconstructor
→ 长程归纳能力优先

Foreshadow Analyzer
→ 语义推理优先

Skill Distiller
→ 分析质量优先

Reviewer
→ 冲突判断优先
```

模型名必须从当前 StoryWeaver 实际配置读取。

不要在新增代码里硬编码旧模型名。

---

# 32. Structured Output

输出 Java 类型：

```text
ChapterAnalysis
CharacterCandidate
WorldbookCandidate
OutlineCandidate
ForeshadowCandidate
SkillCandidate
ReconstructionReview
```

流程：

```text
LLM
→ Structured Output
→ Schema Validation
→ Bean Validation
→ Evidence Validation
→ Domain Validation
→ Candidate
```

合法 JSON 不等于事实正确。

---

# 33. DeepSeek JSON 异常

必须处理：

```text
Empty Content
Invalid JSON
Truncated JSON
Unknown Enum
Missing Required Field
Semantic Conflict
429
Timeout
5xx
```

支持：

```text
Retry
Backoff
Failure Artifact
Partial Result
```

---

# 34. Token / Cost

“模型与费用”显示真实：

```text
Chapter Extraction
Entity Resolution
Outline Reconstruction
Foreshadow Analysis
Skill Distillation
Final Review
```

每阶段：

```text
Input Tokens
Output Tokens
Reasoning Tokens（若真实可用）
Cached Tokens（若真实可用）
Cost
Latency
Retry Count
```

价格必须复用 StoryWeaver 当前 Usage / Pricing。

不能在 Reconstruction 模块维护第二套价格表。

---

# 35. 费用预估

启动前根据：

```text
章节数
Chunk 数
Token Estimate
分析模式
当前模型
Prompt Overhead
预计 Output
缓存策略
```

给：

```text
Estimated Calls
Estimated Input Tokens
Estimated Output Tokens
Estimated Cost Min
Estimated Cost Max
```

UI 必须明确：

```text
预估
```

与实际费用区分。

用户可以设置：

```text
最大分析预算
```

达到预算后：

```text
PAUSED_BUDGET
```

---

# 36. 缓存与增量

缓存 Key 至少考虑：

```text
chapterContentHash
analysisVersion
promptVersion
model
```

完全一致：

允许复用。

如果章节改动：

```text
Chapter Analysis Stale
```

未来支持只重分析受影响：

```text
Character
Story Event
Arc
Foreshadow
```

V1 可以先完整实现首次重建，但数据库必须保留：

```text
contentHash
analysisVersion
```

为增量重建留空间。

---

# 37. 数据模型建议

若现有模型不够，最小新增：

```text
book_reconstruction_job
book_reconstruction_step
book_analysis_checkpoint
chapter_analysis_result
project_reconstruction_candidate
entity_resolution_candidate
```

优先复用现有：

```text
Canon Candidate
StoryFact
Character
Worldbook
Outline
Skill
Review Finding
Usage
SSE
```

避免创建一套与正式项目域平行的模型。

`book_reconstruction_job` 可包含：

```text
id
project_id
owner_id
mode
status
current_step
total_chapters
processed_chapters
failed_chapters
progress
estimated_tokens
estimated_cost
actual_tokens
actual_cost
started_at
paused_at
completed_at
error_code
version
```

---

# 38. API

## Estimate

```http
POST /api/projects/{projectId}/reconstruction/estimate
```

## Start

```http
POST /api/projects/{projectId}/reconstruction
```

Request 示例：

```json
{
  "mode": "STANDARD",
  "includeSkillDistillation": true,
  "includeForeshadowing": true,
  "maxBudget": null
}
```

## Status

```http
GET /api/projects/{projectId}/reconstruction
```

## SSE

如果当前已有 SSE：

```http
GET /api/projects/{projectId}/reconstruction/events
```

事件：

```text
reconstruction.started
reconstruction.phase.changed
reconstruction.chapter.completed
reconstruction.progress
reconstruction.candidate.created
reconstruction.review.required
reconstruction.completed
reconstruction.failed
heartbeat
```

## Control

```http
POST /api/projects/{projectId}/reconstruction/pause
POST /api/projects/{projectId}/reconstruction/resume
POST /api/projects/{projectId}/reconstruction/cancel
```

## Candidate

```http
GET  /api/projects/{projectId}/reconstruction/candidates
POST /api/projects/{projectId}/reconstruction/candidates/{id}/approve
POST /api/projects/{projectId}/reconstruction/candidates/{id}/reject
POST /api/projects/{projectId}/reconstruction/approve-safe
```

## Retry

```http
POST /api/projects/{projectId}/reconstruction/retry-failed
```

---

# 39. 前端

新增：

```text
BookReconstructionDialog.vue
BookReconstructionEstimate.vue
BookReconstructionProgress.vue
BookReconstructionSummary.vue
ReconstructionCandidateReview.vue
ReconstructionConflictPanel.vue
SourceEvidenceViewer.vue
```

TXT 创建成功后：

```text
[直接进入项目]
[AI 自动构建完整项目]
```

项目内如果用户选“稍后”：

项目概览保留：

```text
✨ AI 构建完整项目
```

---

# 40. 进度页

```text
正在理解这本书

《项目名称》

██████████████░░░░░░ 73%

594 / 812 章

当前阶段：
人物与别名融合

已发现：
人物 42
地点 68
势力 11
重要道具 24
故事事件 386
伏笔候选 31

实际费用：
￥X.XX

[暂停] [后台继续]
```

进度必须来自真实 Work Unit。

不能：

```text
setInterval +1%
```

---

# 41. 左侧菜单状态

建议：

```text
灰：未分析
蓝：分析中
黄：待审核
绿：已建立
红：有冲突
```

同时显示文字 / Tooltip，不能只靠颜色。

项目概览：

```text
TXT 项目重建

✓ 章节       812 / 812
✓ 项目概览
! 人物       42（3 项待审核）
! 世界书     116（8 项低置信度）
✓ 大纲
! 伏笔       31（14 个未回收）
! Skill      待确认

[查看全部分析结果]
```

---

# 42. Reconstruction Ready

项目状态：

```text
NOT_ANALYZED
ANALYZING
PARTIAL
REVIEW_REQUIRED
READY
```

`READY` 表示：

```text
基础结构已足够支撑连续写作
```

不代表所有 AI 推断都已变成 Confirmed Canon。

最小 Ready 条件建议：

```text
最后若干章摘要存在
主要人物最终状态存在
当前地点存在
当前 Arc 存在
主要未解决冲突存在
```

---

# 43. 失败降级

如果：

```text
809 / 812 章成功
3 章失败
```

可以：

```text
PARTIAL
```

并提供：

```text
[重试失败章节]
```

但如果：

```text
Global Entity Resolution
```

完全失败：

不能标 READY。

---

# 44. 权限

用户 A 不得读取用户 B：

```text
Reconstruction Job
Candidate
Evidence
Usage
Failure Artifact
```

所有 API 必须 owner 校验。

---

# 45. Evaluation

如果根目录已有 `evals/`，新增：

```text
evals/datasets/import-reconstruction/
```

建议指标：

```text
Character Precision
Character Recall
Alias Merge Accuracy

Worldbook Entity Precision
Required Fact Recall

Chapter → Arc Mapping Accuracy

Foreshadow Precision
Foreshadow Recall
Foreshadow F1

Evidence Traceability Rate

Reconstruction Completion Rate
```

这些指标只有真实 Dataset / Ground Truth / Run 后才能写进 README 或简历。

---

# 46. 测试

后端至少覆盖：

```text
ReconstructionStateMachine
ProgressCalculator
CostEstimator
BudgetPause
Chapter Chunk
Structured Output
Empty JSON
Invalid JSON
Truncated JSON
Entity Alias Merge
同名不同人
Worldbook Merge
Reverse Outline
未完结 TXT
Foreshadow
Evidence
Candidate
Confidence
Pause
Resume
Backend Restart Resume
Cancel
Partial
Retry
Idempotency
Content Hash
Permissions
Usage
Cost
```

前端：

```text
启动估算
QUICK / STANDARD / DEEP
费用确认
真实进度
刷新恢复
Pause
Resume
Cancel
Partial
Retry
Candidate Review
Evidence 查看
Safe Apply
左栏状态
Reconstruction Summary
```

E2E：

```text
TXT Import
→ Create Project
→ Start STANDARD Reconstruction
→ Progress
→ Candidate Review
→ Project Overview / Character / Worldbook / Outline 回显
→ Continuation Ready
```

普通 CI 不应自动进行高成本 Live 全书分析。

Live Test 必须有显式环境开关和预算限制。

---

# 47. Definition of Done

```text
[ ] TXT 创建项目后可以选择 AI 自动构建完整项目
[ ] 默认不自动花费模型费用
[ ] 有 QUICK / STANDARD / DEEP
[ ] 分析前有 Token / Cost Estimate
[ ] 不整本单 Prompt
[ ] Chapter / Chunk 局部分析
[ ] Volume / Arc 聚合
[ ] 人物跨章 Entity Resolution
[ ] 别名 Merge 可审核
[ ] 世界书 Entity Resolution
[ ] Reverse Outline
[ ] 章节正文不改写
[ ] 章节有摘要 / POV / Event 等元数据
[ ] 自动生成伏笔台账
[ ] 伏笔跨章验证
[ ] 自动生成 Project-local Skill Candidate
[ ] Skill 复用现有 Skill Forge
[ ] 自动初始化项目概览
[ ] 自动初始化创作工作台
[ ] 自动初始化连续写作 Context
[ ] 高风险数据默认 Candidate
[ ] Important Candidate 有 Evidence
[ ] 区分 DIRECT_FACT / MODEL_INFERENCE / USER_CONFIRMED
[ ] 支持 Safe Apply
[ ] 支持逐项审核
[ ] 支持 Pause / Resume / Cancel
[ ] 浏览器关闭后后台可继续
[ ] Backend Restart 后可恢复
[ ] 支持失败章节单独 Retry
[ ] 支持 PARTIAL
[ ] 真实 Usage / Cost
[ ] 支持 Budget Limit
[ ] Owner 权限正确
[ ] 有 Reconstruction Report
[ ] 测试覆盖状态机、结构化输出、实体融合、恢复、幂等和权限
```


---

# 48. Codex 实施提示词

把本文件放到 StoryWeaver 仓库根目录，然后在仓库根目录打开 Codex，输入：

```text
阅读仓库根目录中的：

StoryWeaver_TXT导入后AI全项目重建_Codex更新文档_V1.1.md

同时完整读取：

- 根目录、backend、frontend、evals 下的 AGENTS.md；
- 最新 StoryWeaver 前后端设计文档；
- TXT 导入设计文档；
- Skill 熔炼设计文档；
- 当前 Project / Character / Worldbook / Outline / Chapter / Skill / Memory / Canon / Workflow / Review / Usage / SSE / Evaluation 实际代码；
- 当前 Flyway、OpenAPI 和测试。

本次目标：

在现有“TXT ≤20MB 导入并创建项目”之后，实现“AI 自动读取整本书并重建完整 StoryWeaver 项目”。

TXT 项目创建成功后，用户可以主动点击：

“AI 自动构建完整项目”

系统要尽可能自动初始化左侧项目内容模块：

- 项目概览；
- 创作工作台；
- 人物；
- 世界书；
- 大纲；
- 章节 AI 元数据；
- Skill；
- 伏笔台账；
- 连续写作 Context。

“模型与费用”只记录真实 Usage、Token、Cost、Retry 和 Duration，不允许 AI 编造。

核心要求：

1. 禁止把 20MB 全文一次塞给 LLM。
2. 必须采用 Hierarchical Reconstruction：
   Deterministic Preprocess
   → Chapter / Chunk Analysis
   → Volume / Arc Aggregation
   → Global Entity Resolution
   → Timeline / Relationship / Knowledge Reconstruction
   → Project Candidate Generation
   → Foreshadow Analysis
   → Skill Distillation
   → Consistency Validation。
3. TXT 导入的 Chapter 原文不可被 AI 自动改写。
4. AI 生成的重要结构化结果默认 Candidate。
5. 高风险信息：
   - World Hard Rule；
   - Character Knowledge；
   - Hidden Motivation；
   - Unique Item Ownership；
   - Foreshadow；
   - Skill；
   - 模糊关系推断；
   不得未经确认直接成为硬 Canon。
6. 每个重要 Candidate 尽量保存 Source Evidence。
7. 区分：
   DIRECT_FACT
   MODEL_INFERENCE
   USER_CONFIRMED。
8. 人物做跨章节 Entity Resolution。
9. Alias 合并不确定时返回 NEEDS_REVIEW，禁止强 Merge。
10. 世界书也要做 Entity Resolution。
11. 自动生成的是 Reverse Outline，禁止虚构 TXT 之后的未来剧情。
12. 未完结 TXT 标记 IMPORTED_IN_PROGRESS。
13. 伏笔使用 Local Candidate → Global Resolution。
14. Skill 必须复用现有 Skill Forge，不实现第二套 Skill 系统。
15. 连续写作 Context 基于：
    最近章节
    + 人物最终状态
    + Character Knowledge
    + 当前 Arc
    + 世界状态
    + 未解决冲突
    + 未回收伏笔
    + 唯一物品
    + Project Skill。
16. Next Chapter 只能是 AI Suggestion。
17. 支持 QUICK / STANDARD / DEEP。
18. 启动分析前必须先 Estimate。
19. Estimate 与实际费用明确区分。
20. 实际费用复用当前 Usage / Pricing，不维护第二套价格表。
21. 支持用户设置最大预算。
22. 达到预算进入 PAUSED_BUDGET，不继续偷偷调用模型。
23. 支持 Pause / Resume / Cancel。
24. Reconstruction Job 在 Backend 运行，浏览器关闭不能导致 Job 丢失。
25. 保存 Checkpoint。
26. Backend Restart 后应能从安全 Checkpoint 恢复。
27. 同一 chapterContentHash + analysisVersion + promptVersion + model 可以缓存复用。
28. 支持失败章节单独 Retry。
29. 某些章节失败允许 PARTIAL。
30. Global Entity Resolution 等关键阶段失败时不能错误标 READY。
31. 所有 Job / Candidate / Evidence 做 owner 权限校验。
32. 不修改已发布 Flyway Migration，使用新 Migration。
33. 优先复用现有 Workflow、Canon、StoryFact、Character、Worldbook、Outline、Skill、Reviewer、Usage、SSE、Embedding、Retrieval 和 evals。
34. 不建立一套与正式项目域平行的正式数据模型。
35. 不使用 setTimeout、随机百分比、固定假 AI 数据模拟进度或结果。

开始前使用 rg 搜索真实实现：

Project
Character
Worldbook
Outline
Chapter
ChapterVersion
StoryFact
Canon
Memory
Skill
SkillForge
Workflow
Reviewer
SSE
Usage
Pricing
Embedding
Retrieval
Token
Context

根据实际代码确定最终类名、API 和 Migration。

建议能力：

- Reconstruction Estimate；
- Start；
- Status；
- Progress；
- SSE（如果已有基础设施）；
- Pause；
- Resume；
- Cancel；
- Candidate List；
- Candidate Approve；
- Candidate Reject；
- Safe Apply；
- Retry Failed Chapters；
- Reconstruction Report。

前端：

TXT 项目创建完成后显示：

[直接进入项目]
[AI 自动构建完整项目]

分析过程显示：

- 当前真实阶段；
- 已处理章节 / 总章节；
- 已发现人物数；
- 世界书数；
- Event 数；
- 伏笔候选数；
- 当前真实 Token；
- 当前真实费用；
- 失败章节数；
- Pause / Resume。

分析完成后：

- 项目概览出现 Reconstruction Summary；
- 人物页出现人物和待审核项目；
- 世界书页出现结构化条目；
- 大纲页出现 Reverse Outline；
- 章节页出现摘要 / POV / Event 等元数据；
- 伏笔台账出现跨章候选；
- Skill 页出现 Project-local DRAFT；
- 连续写作页可直接建立下一章 Context；
- 模型与费用展示本次真实 Usage。

测试必须覆盖：

- Reconstruction 状态机；
- QUICK / STANDARD / DEEP；
- Chapter / Chunk；
- Structured Output；
- Empty / Invalid / Truncated JSON；
- Entity Alias；
- 同名不同人；
- Worldbook Merge；
- Reverse Outline；
- 未完结作品；
- Foreshadow；
- Evidence；
- Confidence；
- DIRECT_FACT / MODEL_INFERENCE；
- Pause；
- Resume；
- Restart Resume；
- Cancel；
- Partial；
- Retry；
- Budget Pause；
- Idempotency；
- Content Hash；
- 权限；
- Usage；
- Cost；
- 前端刷新恢复。

如果 evals 已存在：

新增 import-reconstruction Dataset / Runner，
但没有真实运行就不准填写效果数字。

普通 CI 不调用大规模真实 DeepSeek。

Live Reconstruction Test 必须有：
- 显式环境开关；
- 最大预算；
- 最大章节数或专用小 Fixture。

完成后实际执行：

Backend：
cd backend
./mvnw clean verify

Frontend：
cd frontend
pnpm lint
pnpm typecheck
pnpm test:unit
pnpm build

根目录：
docker compose config

如果 E2E 环境可用：
执行 TXT Import → Reconstruction → Project 各模块回显流程。

失败必须分析并修复，不得禁用测试。

最终汇报：

1. 实际读取的设计文档；
2. 实际复用的模块；
3. 新增 Migration；
4. Reconstruction Job；
5. 状态机；
6. Pipeline；
7. API；
8. 前端页面；
9. 项目概览填充；
10. 创作工作台填充；
11. 人物重建；
12. Character Knowledge；
13. 世界书重建；
14. Reverse Outline；
15. 章节元数据；
16. 伏笔台账；
17. Skill；
18. 连续写作 Context；
19. Evidence / Confidence；
20. Safe Apply；
21. Pause / Resume / Retry；
22. Token / Cost / Budget；
23. 测试结果；
24. Evaluation；
25. 与设计不同的地方；
26. 未完成项。

现在开始实现。
```

---

# 49. 技术依据与实现提醒

1. Spring AI 的 Structured Output 能把模型结果映射成 Java 类型，并提供 Schema Validation 等能力；但模型输出仍应通过应用层验证，不能因为 JSON 合法就自动视为 Canon。
2. DeepSeek JSON Output 可以要求合法 JSON，但官方提示可能出现空内容，因此批量章节分析必须处理 Empty Content、截断、Retry 和失败结果。
3. 对长任务而言，分块读取、分块处理、周期性 Commit 和 Checkpoint 比“一个超长请求”更适合恢复与重试。若现有 StoryWeaver Workflow 不足，可参考 chunk-oriented processing 思路，但优先复用现有 Workflow。
4. pgvector 可以让 StoryWeaver 在 PostgreSQL 中继续维护向量检索。项目重建后的 Character、Worldbook、Story Event、章节摘要可进入现有 Retrieval / Context Builder，而不是另建一套独立知识库。

---

# 50. 本次功能的最终体验

对于“从零开始”的用户：

```text
创建项目
→ 建人物
→ 建世界书
→ 建大纲
→ 写章节
```

对于已经有几十万、几百万字旧稿的用户：

```text
导入 TXT
→ AI 自动理解整本书
→ 人物 / 世界书 / 大纲 / 伏笔 / Skill 自动建立
→ 审核关键候选
→ 直接连续写作
```

StoryWeaver 因此不只是：

```text
“帮我生成一章小说”
```

而是进一步支持：

```text
“把我已经写了很久的小说接管成一个可管理、可检索、可审查、可继续创作的 Agent 项目。”
```


---

# 51. V1.2 核心修正：RAG 不能单独解决人物卡生命周期

上一版已经解决：

```text
TXT → AI 提取候选 → Evidence → Candidate → Safe Apply
```

但还缺一个关键层：

> AI 发现一条关于某人物的新信息后，到底应该新建人物卡、更新已有卡、记录成事件，还是忽略？

这个问题不能写成：

```text
AI 判断 → 直接导入人物卡
```

也不能认为“用了 RAG 就自然解决”。

正确关系是：

```text
结构化项目状态（Source of Truth）
        ↓
版本 / 生命周期 / 时间有效性
        ↓
RAG 负责在当前任务中召回相关历史
```

结论：

**RAG 解决“找什么”，不解决“什么是真的、什么还有效、该不该建卡、该不该删除”。**

---

# 52. AI 人物候选自动决策

当前人物候选 UI 的：

```text
载入人物卡
标记可信
拒绝
```

升级为带 AI 建议的结构化动作：

```text
CREATE_CHARACTER
UPDATE_PROFILE
APPEND_STATE
APPEND_KNOWLEDGE
APPEND_RELATIONSHIP
APPEND_EVENT
MERGE_ALIAS
IGNORE
NEEDS_REVIEW
```

例：

```text
“沈砚捡起半截黑色灯芯，发现湿透但仍有温度。”
```

更可能是：

```text
APPEND_KNOWLEDGE
APPEND_EVENT
```

而不是修改基础人物卡。

---

# 53. Candidate Policy Engine

最终动作不能纯靠 LLM。

```text
LLM Candidate Classification
→ Java Candidate Policy Engine
→ Entity Resolution
→ Current Canon / Lifecycle Check
→ Evidence Gate
→ Auto Apply / Review
```

建议枚举：

```java
enum CandidateAction {
    CREATE_CHARACTER,
    UPDATE_PROFILE,
    APPEND_STATE,
    APPEND_KNOWLEDGE,
    APPEND_RELATIONSHIP,
    APPEND_EVENT,
    MERGE_ALIAS,
    UPDATE_WORLD_ASSET,
    CREATE_FORESHADOW,
    ADVANCE_FORESHADOW,
    RESOLVE_FORESHADOW,
    UPDATE_ROLLING_OUTLINE,
    IGNORE,
    NEEDS_REVIEW
}
```

---

# 54. 什么情况下新建人物卡

不是每个被提到的人都建立完整人物卡。

满足以下任一条件时才自动建议 `CREATE_CHARACTER`：

```text
1. 有明确专名，并在多个独立段落 / 章节出现；
2. 成为主要冲突参与者；
3. 产生重要 Story Event；
4. 与主要人物形成持续关系；
5. 后续章节持续出现；
6. 用户明确要求所有命名人物建卡。
```

一次性人物：

```text
“门口站着一个保安。”
```

默认：

```text
MENTION_ONLY / EVENT_ONLY
```

---

# 55. Character Importance

新增：

```text
PROTAGONIST
MAJOR
SUPPORTING
MINOR
MENTION_ONLY
```

允许：

```text
MENTION_ONLY → MINOR → SUPPORTING → MAJOR
```

避免长篇导入后人物页出现大量无意义卡片。

---

# 56. 人物发展不能覆盖旧值

例如：

```text
第 10 章：沈砚不信任许岚
第 70 章：沈砚开始信任许岚
```

错误：

```text
relationship = TRUST
```

正确：

```text
Relationship Timeline

Chapter 10—42  SUSPICIOUS
Chapter 43—69  COOPERATING
Chapter 70—    TRUSTING
```

同理适用于：

```text
身体状态
阵营
职务
人物目标
人物关系
知识
道具拥有权
当前地点
```

---

# 57. Story State Versioning

建议：

```text
Entity
→ Entity Version
→ Temporal State / Event
```

字段：

```text
validFromChapter
validToChapter
sourceChapterId
sourceEvidence
confidence
status
supersededBy
```

“当前状态”通过当前章节上的有效状态计算，而不是通过删除历史获得。

---

# 58. 删除人物卡问题

正常创作中不应该直接：

```sql
DELETE FROM character
```

因为以下情况语义不同：

```text
死亡
离队
失踪
暂时退出主线
被误识别
别名合并
用户隐藏
永久清除
```

生命周期：

```text
CANDIDATE
ACTIVE
INACTIVE
DECEASED
MISSING
LEFT_STORY
MERGED
REJECTED
ARCHIVED
PURGED
```

只有用户明确永久删除时才进入：

```text
PURGED
```

---

# 59. 死亡人物为什么保留

人物死亡后仍会影响：

```text
回忆
人物动机
历史事件
道具来源
关系
伏笔
世界观
```

因此：

```text
DECEASED
```

仍可被“历史查询”召回，但不能进入“当前活跃人物”Context。

---

# 60. 错误 AI 候选撤回

如果之前某条 `MODEL_INFERENCE` 被用户确认错误：

不要无痕删除。

改为：

```text
REVOKED / REJECTED
```

并保存：

```text
revokedAt
revokedBy
reason
supersededBy
```

这样可以：

```text
审计
回归 Eval
Embedding 失效
防止后续 Agent 再召回
```


---

# 61. Canon Tombstone 与旧 Embedding

对于：

```text
MERGED
REJECTED
ARCHIVED
SUPERSEDED
```

必须设置：

```text
retrievalEligible = false
```

否则会出现：

> UI 已经删除 / 拒绝一条错误人物关系，但 pgvector 仍然召回旧 Embedding。

所有可检索对象建议保存：

```text
entityId
entityVersion
contentHash
embeddingVersion
embeddingModel
retrievalStatus
```

当人物、世界书、StoryFact、伏笔、摘要被修改或 Supersede：

```text
旧 Embedding → INVALID / TOMBSTONED
新内容 → Re-Embedding
```

---

# 62. RAG 在 StoryWeaver 中真正解决什么

RAG 主要负责：

```text
连续写作
Planner
Writer
Reviewer
历史事件
章节上下文
人物历史
世界书语义联想
伏笔相关上下文
Memory
```

RAG 输入不应是数据库所有历史行，而应是：

```text
Retrieval-Eligible Story Assets
```

---

# 63. Metadata Filter

每次 Retrieval 至少过滤：

```text
projectId == 当前项目
ownerId == 当前用户
retrievalEligible == true
status in 当前任务允许状态
sourceVersion == current
validFromChapter <= 当前章节
validToChapter > 当前章节 OR null
```

按任务再增加：

```text
assetType
characterId
arcId
locationId
foreshadowStatus
```

写第 N 章时，禁止召回 N 之后才发生的事实。

这类错误称为：

```text
Temporal Leakage
```

---

# 64. Character Knowledge 防未来泄漏

人物知识必须保存：

```text
learnedAtChapter
forgottenAtChapter（如业务允许）
sourceEventId
confidence
```

构建第 N 章 Context：

```text
learnedAtChapter <= N
```

否则 Writer 可能让人物提前知道未来秘密。

---

# 65. Hybrid RAG

推荐：

```text
PostgreSQL Keyword / Full-text Search
        +
pgvector Semantic Search
        +
Business Metadata Filter
        +
RRF / Existing Reranker
```

关键词适合：

```text
人名
别名
组织名
地名
唯一道具
专有术语
章节标题
```

向量检索适合：

```text
“之前有人怀疑那盏灯的来源”
“和当前冲突类似的历史事件”
```

优先复用当前 StoryWeaver 已有 Hybrid Retrieval / Ranker，不重复实现一套搜索系统。

---

# 66. RAG 不是 Source of Truth

禁止：

```text
Vector Search Result
→ 直接决定人物当前状态
```

例如：

```text
第20章：沈砚受重伤
第30章：沈砚已经康复
```

向量搜索可能同时找回两条。

因此：

```text
Structured Current State = Canonical Source
Historical RAG = Supplemental Context
```

如果冲突：

```text
Current Confirmed State Wins
```

---

# 67. Structured Retrieval First

以下信息优先使用 SQL / Domain Service：

```text
当前人物状态
当前人物关系
当前 Character Knowledge
当前道具拥有者
当前世界硬规则
当前 Outline Node
当前 Active Foreshadow
```

语义向量主要用于：

```text
历史相关事件
章节摘要
Memory
相关叙事情境
```

核心原则：

> **Structured Retrieval First，Semantic Retrieval Second。**

---

# 68. Context Priority

连续写作 Context 推荐优先级：

```text
P0 用户本次明确要求
P1 当前 Confirmed Canon / Hard Rules
P2 Current Character / Item / Knowledge State
P3 当前章纲 / Rolling Outline / Current Arc
P4 Active Foreshadow
P5 Hybrid RAG Related Events / Memory
P6 Project Skill
P7 Historical Low-priority Context
```

---

# 69. Context Builder v2

建议结构：

```text
Context Builder
├─ Required Structured Context Loader
│  ├─ Current Character State
│  ├─ Character Knowledge
│  ├─ Item State
│  ├─ Hard Rules
│  ├─ Current Outline
│  └─ Active Foreshadow
│
├─ Hybrid RAG Retriever
│  ├─ Keyword / FTS
│  ├─ Vector
│  └─ RRF / Rerank
│
├─ Deduplicator
├─ Temporal Filter
├─ Version Filter
├─ Token Budget Allocator
└─ Canonical Context Packet
```

---

# 70. 图二：滚动大纲必须成为真实模块

左侧：

```text
人物
世界书
大纲
滚动大纲
连续写作
伏笔台账
```

这些不能只是页面名称。

特别是：

```text
滚动大纲
```

应该承担“当前故事实际走到了哪里”的职责。

---

# 71. Static Outline vs Rolling Outline

### 大纲

表示：

```text
作者计划 / TXT 反向结构
Book → Volume → Arc → Chapter
```

### 滚动大纲

表示：

```text
最近真实发生了什么
当前 Arc 走到哪
还有哪些开放线程
哪些人物刚刚变化
下一章有什么硬约束
```

它不是大纲页面的复制。

---

# 72. Rolling Outline 数据

建议：

```text
rolling_outline_snapshot
```

保存：

```text
projectId
baseChapterId
fromChapterIndex
toChapterIndex
recentSummary
currentArcId
currentGoal
activeConflicts
openThreads
recentCharacterChanges
currentLocations
activeItems
activeForeshadow
nextConstraints
sourceChapterIds
contentHash
version
createdAt
```

---

# 73. Rolling Outline 更新时机

只在：

```text
正式 Chapter Confirmed / Committed
```

之后更新。

正确流程：

```text
Chapter Draft
→ Reviewer
→ User Confirm
→ Atomic Commit
→ StoryFact / Current State Update
→ Rolling Outline Update
→ Retrieval / Embedding Refresh
```

草稿阶段不能污染滚动大纲。

---

# 74. Rolling Window

不要每写一章就重新总结整本书。

采用：

```text
最近 5—15 章详细摘要
+
当前 Arc Summary
+
已完成 Arc Summary
+
Current Canon State
```

使 Token 和计算成本保持稳定。

---

# 75. 滚动大纲 + RAG

两者职责不同：

```text
Rolling Outline = Current Narrative State
RAG = Relevant Historical Recall
```

连续写作应组合：

```text
Rolling Outline
+
Current Canon
+
Hybrid RAG
```

而不是只对历史章节做向量搜索。

---

# 76. 连续写作 Context v2

点击“连续写作”：

```text
用户本次要求
+ 当前章纲
+ Rolling Outline
+ Current Character State
+ Character Knowledge
+ Current Item State
+ Hard Rules
+ Active Foreshadow
+ Hybrid RAG
+ Project Skill
+ Recent Text
```

然后进入：

```text
Planner → Writer → Extractor → Reviewer
```

---

# 77. 伏笔台账生命周期

伏笔必须结构化：

```text
CANDIDATE
PLANTED
DEVELOPING
DUE
RESOLVED
PARTIALLY_RESOLVED
ABANDONED
REJECTED
```

字段：

```text
plantedChapter
expectedWindow
relatedCharacters
relatedWorldAssets
developmentEvents
resolutionChapter
priority
confidence
evidence
```

---

# 78. 伏笔召回

Planner 下一章时优先召回：

```text
DUE
+
当前 Arc 相关
+
当前人物相关
+
当前地点相关
```

不要每一章把全项目所有伏笔塞进 Context。

---

# 79. 伏笔老化

长时间未推进：

```text
PLANTED → DUE
```

可以由系统建议。

但不能自动：

```text
DUE → ABANDONED
```

“废弃伏笔”应由用户确认。

---

# 80. 世界书生命周期

世界书也会变化：

```text
地点被毁
组织解散
道具损坏
能力升级
规则被澄清
术语改名
```

需要：

```text
Worldbook Version
+
World State Event
```

Current Retrieval：

优先当前版本。

历史查询：

允许旧版本，但明确标记：

```text
HISTORICAL
```


---

# 81. 大纲生命周期

TXT 导入得到的是：

```text
Reverse Outline
```

继续创作后，未来大纲节点可能是：

```text
PLANNED
IN_PROGRESS
REALIZED
DIVERGED
ARCHIVED
```

如果正文与原章纲不同：

```text
不要强行修改正文
```

而应：

```text
Chapter Outline → DIVERGED
Rolling Outline → 记录真实发生内容
```

---

# 82. Outline Divergence 不是 Canon Conflict

小说创作中：

```text
计划 ≠ 实际
```

这是正常现象。

Reviewer 必须区分：

```text
CANON_CONFLICT
OUTLINE_DIVERGENCE
```

前者可能：

```text
BLOCKER
```

后者通常：

```text
INFO / WARNING
```

---

# 83. Dependency Invalidation

用户修改：

```text
人物卡
世界书
章节正文
章节摘要
伏笔
Skill
```

后，不能只更新当前页面。

必须触发：

```text
Dependency Invalidation
```

例如：

```text
Chapter Updated
→ ChapterAnalysis STALE
→ Related StoryFact STALE / Re-evaluate
→ Rolling Outline STALE
→ Related Embedding STALE
→ Context Cache Evict
```

---

# 84. Lightweight Dependency Graph

建议：

```text
asset_dependency
```

关系示例：

```text
CHAPTER → STORY_FACT
CHAPTER → FORESHADOW
STORY_FACT → CHARACTER
STORY_FACT → WORLDBOOK
ARC → CHAPTER
ROLLING_OUTLINE → CHAPTER
SKILL_RULE → SOURCE
```

目标：

```text
增量失效
局部重算
```

不需要一开始引入图数据库。

---

# 85. Archive / Merge 后的检索处理

### Archive Character

```text
status = ARCHIVED
retrievalEligible = false
cache evict
vector tombstone / delete
```

### Merge Character

旧实体：

```text
status = MERGED
mergedInto = canonicalCharacterId
retrievalEligible = false
```

新实体：

```text
更新 Alias
重新生成 Retrieval Document / Embedding
```

---

# 86. RAG Metadata Schema

建议所有检索资产统一 Metadata：

```text
projectId
ownerId
assetId
assetType
entityId
version
status
retrievalEligible
validFromChapter
validToChapter
arcId
sourceChapterId
confidence
contentHash
embeddingVersion
```

这样同一套 Retrieval 才能同时支持：

```text
当前状态
历史查询
续写
Reviewer
伏笔
人物知识
```

---

# 87. Query Type

Context Builder 不应所有请求共用一个搜索策略。

建议：

```text
CHARACTER_CURRENT_STATE
CHARACTER_HISTORY
CHARACTER_KNOWLEDGE
WORLD_RULE
LOCATION_CONTEXT
ITEM_HISTORY
FORESHADOW
RECENT_EVENT
HISTORICAL_PARALLEL
STYLE_REFERENCE
```

不同类型使用：

```text
不同 Metadata Filter
不同 topK
不同 Token Budget
不同 Keyword / Vector 权重
```

---

# 88. V1.2 是否已经解决“RAG 等技术”的问题

明确结论：

V1.1 已经解决：

```text
TXT 全书分层分析
人物 / 世界书 / 大纲 / 伏笔 / Skill 候选
Evidence
Candidate
Continuation Context
```

但 V1.2 才补齐：

```text
AI 是否应该建人物卡
人物后续发展
人物死亡 / 离场 / 合并 / 隐藏
错误 Candidate 撤回
世界书版本变化
旧 Embedding 失效
未来信息泄漏
Character Knowledge 时间过滤
Rolling Outline
Outline Divergence
伏笔生命周期
Dependency Invalidation
Structured Retrieval + Hybrid RAG
```

因此最终方案不是：

```text
RAG
```

单项技术，而是：

```text
Versioned Structured State
+
Entity Lifecycle
+
Temporal State
+
Evidence
+
Dependency Invalidation
+
Structured Retrieval
+
Hybrid RAG
+
Rolling Outline
+
Context Builder
```

---

# 89. 数据库版本策略

V1.2 推荐继续使用应用层：

```text
valid_from_chapter
valid_to_chapter
version
superseded_by
status
```

而不是为了 Temporal State 立即升级数据库并依赖仍处于新版本阶段的数据库 Temporal 特性。

未来 PostgreSQL 稳定版本与当前 StoryWeaver 基线统一后，再评估数据库原生 Temporal Table。

---

# 90. 新增测试

V1.2 必须增加：

```text
1. 一次性路人不会自动建完整人物卡；
2. 重复出现人物可自动建议 Importance 晋升；
3. 同一人物多个别名不会重复建卡；
4. 同名不同人不会错误 Merge；
5. 人物死亡后不进入 Active Character Retrieval；
6. 死亡人物历史仍可按历史查询召回；
7. ARCHIVED 人物不进入连续写作 Context；
8. REJECTED Candidate 不再召回；
9. SUPERSEDED Fact 不进入 Current Context；
10. 旧 Embedding 正确失效；
11. 当前章节不会召回未来 StoryFact；
12. Character Knowledge 不产生未来泄漏；
13. Relationship Timeline 保留历史；
14. Item Ownership Timeline 正确；
15. OUTLINE_DIVERGENCE 不误判 CANON_CONFLICT；
16. Rolling Outline 只在 Confirmed Chapter Commit 后更新；
17. 修改 Chapter 后相关 Analysis / Embedding 会 stale；
18. DUE Foreshadow 可以按 Arc / Character 召回；
19. RESOLVED Foreshadow 不再作为待回收进入 Planner；
20. Current Structured State 优先于 Historical RAG；
21. Hybrid Retrieval 支持精确专名；
22. Hybrid Retrieval 支持语义历史查询；
23. Merge Character 后旧 ID 不进入 Current Retrieval；
24. PURGED 数据真正被不可逆删除；
25. 当前项目 Retrieval 不会跨 project / owner。
```

---

# 91. 新增 Evaluation

在根目录 `evals/` 增加：

```text
datasets/
├── temporal-rag/
├── entity-lifecycle/
├── rolling-outline/
└── foreshadow/
```

建议指标：

```text
Temporal Leakage Rate
Current State Accuracy
Character Merge Precision
Character Merge Recall
Archived Asset Retrieval Rate
Superseded Fact Retrieval Rate
Character Knowledge Leakage Rate
Rolling Outline State Accuracy
Foreshadow Due Recall@K
Foreshadow False Positive Rate
```

这些没有真实运行时必须是：

```text
null / Not Run
```

禁止写目标数字冒充实测。

---

# 92. V1.2 Codex 实施提示词

本节优先级高于 V1.1 中原有实施提示词。

```text
阅读根目录：

StoryWeaver_TXT导入_AI项目演化与RAG生命周期_Codex更新文档_V1.2.md

同时阅读：
- 根目录 / backend / frontend / evals 的 AGENTS.md；
- 当前 TXT Import / Reconstruction 实现；
- Character / CharacterState / CharacterKnowledge；
- Worldbook；
- StoryFact / Canon；
- Outline / Rolling Outline；
- Foreshadow；
- Memory；
- Context Builder；
- Retrieval / Ranker / pgvector；
- Workflow / Chapter Commit；
- Usage / Eval；
- Flyway / OpenAPI / Tests。

本次不是只修改“载入人物卡”按钮。

目标是补齐 TXT 重建后的长期项目演化、删除 / 合并 / 版本变化和 RAG 生命周期。

必须实现或按当前架构最小补齐：

一、人物候选动作

定义明确动作：
CREATE_CHARACTER
UPDATE_PROFILE
APPEND_STATE
APPEND_KNOWLEDGE
APPEND_RELATIONSHIP
APPEND_EVENT
MERGE_ALIAS
IGNORE
NEEDS_REVIEW

AI 只给 Candidate Classification。

最终：
LLM
→ Candidate Policy Engine
→ Entity Resolution
→ Evidence Gate
→ Apply / Review。

二、人物 Importance

PROTAGONIST
MAJOR
SUPPORTING
MINOR
MENTION_ONLY

一次性 Mention 默认不生成完整人物卡。

三、人物生命周期

CANDIDATE
ACTIVE
INACTIVE
DECEASED
MISSING
LEFT_STORY
MERGED
REJECTED
ARCHIVED
PURGED

正常项目演化禁止直接物理 DELETE。

死亡 / 离场角色保留历史。
只有用户明确永久删除才 PURGED。

四、Temporal State

人物状态、关系、Knowledge、Item Ownership 等使用：

validFromChapter
validToChapter
sourceChapter
version
supersededBy

不能覆盖历史值。

五、Candidate 撤回

错误 MODEL_INFERENCE：

REVOKED / REJECTED

保存 reason / revokedBy / supersededBy。

六、Embedding 生命周期

MERGED / ARCHIVED / REJECTED / SUPERSEDED：

retrievalEligible=false。

旧 Embedding 必须 tombstone / delete。
新版本重新 embedding。

七、Structured Retrieval First

以下数据优先通过 Domain Service / SQL：
- Current Character State
- Character Knowledge
- Current Relationship
- Current Item Owner
- Hard World Rule
- Current Outline
- Active Foreshadow

不要让 pgvector 决定当前真值。

八、Hybrid RAG

复用当前 PostgreSQL / pgvector：

Keyword / Full-text
+
Vector
+
Metadata Filter
+
Existing Ranker / RRF

Metadata 至少：
projectId
ownerId
assetType
version
status
retrievalEligible
validFromChapter
validToChapter
sourceChapterId
arcId
contentHash
embeddingVersion

九、防 Future Leakage

构建 Chapter N：

不得召回 N 之后发生的 StoryFact。
Character Knowledge 必须 learnedAtChapter <= N。

增加自动测试。

十、Context Builder v2

顺序：

P0 User Instruction
P1 Confirmed Canon / Hard Rules
P2 Current Character / Knowledge / Item State
P3 Current Outline / Rolling Outline
P4 Active Foreshadow
P5 Hybrid RAG Events / Memory
P6 Project Skill
P7 Low-priority History

Current Confirmed State 高于 Historical RAG。

十一、滚动大纲

左侧“滚动大纲”必须成为真实数据模块。

表示：
最近实际剧情
Current Arc Progress
Open Threads
Recent Character Changes
Current Locations
Active Items
Active Foreshadow
Next Constraints

只在 Confirmed Chapter Commit 后更新。

使用 Rolling Window：
最近 5—15 章
+ Current Arc Summary
+ Historical Arc Summary
+ Current Canon。

十二、连续写作

Context 必须组合：
用户要求
+ 当前章纲
+ Rolling Outline
+ Current Character State
+ Knowledge
+ Item State
+ Hard Rules
+ Active Foreshadow
+ Hybrid RAG
+ Project Skill
+ Recent Text。

十三、伏笔台账

Lifecycle：
CANDIDATE
PLANTED
DEVELOPING
DUE
RESOLVED
PARTIALLY_RESOLVED
ABANDONED
REJECTED

Planner 优先召回：
DUE
+ Current Arc
+ Current Character
+ Current Location

不加载全部伏笔。

十四、大纲生命周期

PLANNED
IN_PROGRESS
REALIZED
DIVERGED
ARCHIVED

OUTLINE_DIVERGENCE 与 CANON_CONFLICT 分离。

十五、Dependency Invalidation

Chapter / Character / Worldbook / Candidate 修改后：

标记相关：
Analysis
Summary
Rolling Outline
Embedding
Retrieval Document
Context Cache

为 STALE / invalidate。

优先局部重算，不全项目重跑。

十六、数据库

优先使用应用层 version / validFrom / validTo，
不要为了该能力强制升级到 PostgreSQL 19 temporal feature。

新增 Migration，不修改已发布 Migration。

十七、测试

必须覆盖：
Character Lifecycle
Mention-only
Alias Merge
Same-name Different Person
Temporal State
Future Leakage
Character Knowledge Leakage
Embedding Tombstone
Archive
Merge
Reject
Supersede
Rolling Outline
Outline Divergence
Foreshadow Lifecycle
Structured-State-over-RAG
Cross-project Isolation。

十八、Evaluation

如果 evals 已存在，新增：
Temporal Leakage Rate
Current State Accuracy
Character Merge Precision / Recall
Archived Asset Retrieval Rate
Superseded Fact Retrieval Rate
Rolling Outline Accuracy
Foreshadow Due Recall@K。

未执行必须 null / Not Run。

实现前先用 rg 查实际类名和当前能力。

不要复制已有 Retrieval / Ranker / Skill / Canon 体系。

完成后最终汇报必须单独回答：

A. AI 如何决定是否创建人物卡？
B. 一次性人物如何避免污染人物页？
C. 人物后续发展如何保留历史？
D. 死亡、离队、失踪人物如何处理？
E. 用户“删除”人物时到底发生什么？
F. AI 错误 Candidate 如何撤回？
G. 旧 Embedding 如何失效？
H. RAG 如何避免旧事实和未来事实？
I. 滚动大纲如何更新？
J. 连续写作 Context 最终顺序？
K. 伏笔如何按需召回？
L. 哪些问题由 RAG 解决，哪些由结构化状态解决？

最后执行：

Backend：
cd backend
./mvnw clean verify

Frontend：
cd frontend
pnpm lint
pnpm typecheck
pnpm test:unit
pnpm build

根目录：
docker compose config

如果 Offline eval 可运行则执行。
不要默认调用高成本 Live Model Eval。

现在开始。
```

---

# 93. 技术依据

Spring AI 的 RAG API 支持 Metadata Filter、Similarity Threshold、Top-K，以及检索后的 Document Post Processing / Reranking，因此适合 StoryWeaver 在向量检索之前先限制 `project / status / chapter / type`，之后再做相关性排序。

pgvector 可以直接与 PostgreSQL Full-text Search 组合做 Hybrid Search，并给出 RRF / Cross-encoder 作为融合方向。pgvector 同时支持向量 Update / Delete，因此 Story Asset 版本失效后应同步失效旧向量，而不是让历史错误版本继续被召回。

pgvector 的 approximate index 会用部分 Recall 换取速度，因此已有 RAG Recall@K Evaluation 仍应保留。

PostgreSQL 新版本正在增强 Temporal Tables / application-time history；StoryWeaver 当前仍建议先使用应用层章节有效区间与版本字段，等项目数据库基线稳定升级后再评估原生 Temporal 能力。

---

# 94. 最终架构

```text
                    Confirmed Chapter
                           ↓
                 Extract / Candidate
                           ↓
                 Candidate Policy
                           ↓
           Entity Resolution + Evidence
                  ↙                 ↘
       Structured Current State      Historical Events
       Character / Worldbook          / Memory
       Knowledge / Item /             ↓
       Foreshadow / Outline      FTS + pgvector
                  ↘                 ↙
                   Context Builder
                         ↓
                  Rolling Outline
                         ↓
                 Planner / Writer
                         ↓
                    Reviewer
                         ↓
                Human Confirmation
                         ↓
                   Atomic Commit
                         ↓
            Invalidate / Re-embed / Refresh
```

最终设计原则：

```text
结构化状态负责“现在什么是真的”
生命周期负责“这个事实现在是否还有效”
版本负责“以前是什么”
RAG 负责“历史里什么与当前任务相关”
滚动大纲负责“故事现在实际走到哪里”
伏笔台账负责“哪些开放线程需要关注”
Context Builder 负责“这一章模型究竟应该看到什么”
```
