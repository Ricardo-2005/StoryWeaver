# StoryWeaver / 文脉
## TXT 导入后 AI 全项目重建 — Codex 更新文档

> 文档版本：V1.1  
> 前置能力：沿用现有“TXT 书籍导入并创建项目”，单文件 `.txt` ≤ 20 MB  
> 更新目标：TXT 导入成功后，用户可选择“AI 自动构建完整项目”，让系统分层读取整本书，并尽可能自动填充左侧项目导航中的内容模块。  
> 核心原则：**原文先可靠导入；AI 负责反向理解与结构重建；高风险结果默认 Candidate；重要结论必须有 Evidence；用户拥有最终确认权。**
> 版本衔接：本文件的可恢复重建基础设施由 V17 实现；人物/事实生命周期、时间过滤、RAG 失效、滚动大纲和伏笔治理已在 V1.2/V18 继续落地。当前事实以本文件第 51 节、V1.2 实施记录和源码为准。

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

# 51. Codex 实际实现记录（2026-08-13）

本节以仓库当前代码为真源，区分已经落地的能力与后续阶段，不把设计目标写成已实现结果。

## 51.1 已落地

- TXT 导入成功页和 TXT 项目概览页均提供“AI 自动构建完整项目”；默认不启动模型。
- 启动前必须调用 Estimate，并显示章节、Chunk、预计调用、预计 Token、价格区间和模型；价格复用 `PricingService`，没有适用价格规则时明确显示不可计价。
- 支持 QUICK / STANDARD / DEEP、可选伏笔分析、可选 Skill 候选和最大预算；达到预算进入 `PAUSED_BUDGET`。
- 新增独立 `book_reconstruction_job`，后台虚拟线程执行；浏览器关闭不影响任务。启动恢复器会把超时的运行中任务回到安全队列并从已保存 Chunk 状态继续。
- 确定性预处理从已导入的 `ChapterVersion` 建立 `book_analysis_chunk`，保存 chapter、顺序、字符范围、SHA-256、Token 估算和处理状态；不把整本 TXT 放入一次 Prompt。
- Chapter/Chunk 阶段复用真实 `ExtractorGateway`；之后执行 Volume/Outline、Entity Resolution、Global Reconstruction、Foreshadow、Skill 和 Validation 阶段。所有模型结果先写 `project_reconstruction_candidate`。
- Candidate 保存 `confidence`、`inferenceType`、Evidence 数量、覆盖率、Source Anchor、是否可安全应用；原始章节正文不被修改。
- 支持 Pause / Resume / Cancel / Retry Failed / Safe Apply / 单项确认或拒绝；进度来自数据库已完成 Chunk，不使用定时假百分比。
- Usage 通过 `UsageAttributionContext` 关联到 Reconstruction Job，实际 Token、Cost、Retry 从 `usage_record` 聚合，不维护第二套价格表。
- Owner 权限复用 `ProjectAccessService` 并在 Job 查询上再次限定 `owner_id`。
- 新增 Flyway `V17__txt_book_reconstruction.sql`，未修改 V0—V16。
- 新增 `evals/datasets/import-reconstruction` 原创 Draft Fixture；Ground Truth 尚未复核，因此没有填写或宣称评测数字。

## 51.2 真实 API

```text
POST  /api/projects/{projectId}/reconstruction/estimate
POST  /api/projects/{projectId}/reconstruction
GET   /api/projects/{projectId}/reconstruction
POST  /api/projects/{projectId}/reconstruction/pause
POST  /api/projects/{projectId}/reconstruction/resume
POST  /api/projects/{projectId}/reconstruction/cancel
POST  /api/projects/{projectId}/reconstruction/retry-failed
GET   /api/projects/{projectId}/reconstruction/candidates
PATCH /api/projects/{projectId}/reconstruction/candidates/{candidateId}
POST  /api/projects/{projectId}/reconstruction/approve-safe
```

## 51.3 实际状态

```text
NOT_ANALYZED（Project 展示状态）
→ QUEUED
→ PREPROCESSING
→ CHAPTER_ANALYSIS
→ VOLUME_AGGREGATION
→ ENTITY_RESOLUTION
→ GLOBAL_RECONSTRUCTION
→ FORESHADOW_ANALYSIS（可选）
→ SKILL_DISTILLATION（可选）
→ VALIDATING
→ WAITING_REVIEW
→ APPLYING
→ COMPLETED

分支：PAUSED / PAUSED_BUDGET / PARTIAL / CANCELLED / FAILED
```

## 51.4 当前边界

- 当前 Safe Apply 只把低风险章节摘要写入 `chapter_reconstruction_metadata`；人物、世界硬规则、Character Knowledge、唯一物品、伏笔和 Skill 不会未经人工确认成为正式资产。
- 当前 `ExtractorGateway` 的结构化类型仍是既有 `ExtractionResult`，不是设计稿中更细的每类 Java Schema；已做空值过滤和 Candidate/Evidence 边界，但更完整的 Schema Validation 与 Failure Artifact 仍需后续扩展。
- Global Entity Resolution 当前是保守的模型聚合候选并以 `NEEDS_REVIEW`/冲突文本进入审核，不会自动强 Merge；尚未实现专用别名图算法。
- 项目概览、创作工作台、正式 Character、Worldbook、Outline、Foreshadow、Project-local Skill 和 Continuation Context 的“确认后写入正式域”尚未全部接通。本阶段先完成可恢复分析、成本治理、候选证据与安全审核基础设施。
- 没有新增 Reconstruction SSE；前端按 3 秒读取真实持久化状态。仓库现有 SSE 面向 Workflow，未为本功能复制一套事件流。
- 普通测试不发起真实 DeepSeek 全书调用。集成测试用 `maxBudget=0` 验证任务创建、确定性 Chunk、预算暂停、权限和 Flyway；Live Reconstruction E2E 尚未运行。
