# StoryWeaver Agent Evaluation Harness
## Codex 根目录执行文档

> 文档版本：V1.0  
> 放置位置：StoryWeaver 仓库根目录  
> 执行者：Codex  
> 任务性质：仓库级 Agent Evaluation 基础设施  
> 核心目标：为 StoryWeaver 建立真实、可复现、可回归的 Agent Evaluation，并产生可用于简历和项目报告的实测结果。  
> 核心指标：RAG Recall@K、Token Reduction、Consistency Pass Rate、Workflow Success Rate、MCP Tool Success Rate。  
> 重要原则：**先建立 Baseline，再优化模型或 Prompt。不得用工程测试数量替代 Agent 效果指标。**

---

# 0. Codex 执行入口

当 Codex 收到：

```text
阅读仓库根目录的 STORYWEAVER_AGENT_EVAL_CODEX.md，
完整执行其中要求。
先建立真实 Evaluation Baseline，不要提前优化模型、Prompt、RAG 或 Workflow。
完成实现、测试和可执行的离线评测后再汇报。
```

时，应把本文件作为本次任务的主要执行规格。

如果仓库中存在：

```text
AGENTS.md
backend/AGENTS.md
frontend/AGENTS.md
evals/AGENTS.md
```

则同时遵守这些规则。

优先级：

```text
当前用户指令
> 本文档
> 根目录 AGENTS.md
> evals/AGENTS.md
> backend / frontend AGENTS.md
> 最新 StoryWeaver 设计文档
> README
```

如果实际代码与设计文档冲突：

```text
以实际代码、Flyway、Controller、DTO、测试和运行行为为准。
```

不得为了满足设计稿而虚构已不存在的接口、Tool、Workflow 或数据结构。

---

# 1. 本次任务

在 StoryWeaver 仓库最外层新增独立：

```text
evals/
```

不要把本套 Evaluation 放进：

```text
backend/src/test
frontend/tests
```

最终仓库结构至少形成：

```text
StoryWeaver/
├── backend/
├── frontend/
├── evals/
│   ├── AGENTS.md
│   ├── README.md
│   ├── run-evals.cmd
│   ├── datasets/
│   │   ├── rag/
│   │   ├── consistency/
│   │   ├── workflow/
│   │   └── mcp/
│   ├── fixtures/
│   │   ├── projects/
│   │   ├── canon/
│   │   ├── chapters/
│   │   ├── worldbook/
│   │   └── expected/
│   ├── config/
│   │   ├── eval.yml
│   │   ├── eval-ci.yml
│   │   └── eval-live.yml
│   ├── scripts/
│   │   ├── run-all.ps1
│   │   ├── run-rag.*
│   │   ├── run-token.*
│   │   ├── run-consistency.*
│   │   ├── run-workflow.*
│   │   └── run-mcp.*
│   ├── reports/
│   │   ├── .gitkeep
│   │   └── latest/
│   └── src/
│       └── ...
├── docs/
│   ├── evaluation.md
│   └── evaluation-results.md
└── .github/
    └── workflows/
        └── agent-evals.yml
```

具体 Evaluation Runner 使用 Java、Python、TypeScript 或混合方式，由 Codex 根据当前仓库真实代码选择。

判断原则：

1. 如果直接调用 Java 后端内部 Context Builder / Validator 最可靠，优先使用 Java；
2. 如果 REST / MCP 黑盒评测更符合真实生产路径，可用独立 Python 或 TypeScript Runner；
3. 不为了“技术栈统一”牺牲可复现性；
4. 不复制一套新的生产业务逻辑到 `evals/`；
5. 优先调用真实实现。

---

# 2. 开始前必须分析仓库

先执行：

```bash
git status
git rev-parse --show-toplevel
rg --files
```

如果没有 `rg`，Windows 使用 PowerShell：

```powershell
Get-ChildItem -Recurse -File
```

完整读取：

```text
根目录 AGENTS.md
backend/AGENTS.md
frontend/AGENTS.md
最新后端设计文档
最新前端设计文档
最新项目详细文档
README
compose.yaml
```

重点分析：

```text
backend 实际代码
Flyway Migration
Controller
DTO
OpenAPI
Context Builder
Canonical Context Packet
Worldbook Retrieval
Memory Retrieval
Embedding / pgvector
Token Usage
Consistency Validator
LLM Reviewer
Workflow Engine
Planner
Writer
Extractor
Reviewer
MCP Server
MCP Tools
Usage / Pricing
Testcontainers
已有 Test Fixture / Demo
```

使用 `rg` 搜索至少：

```text
retrieval
embedding
vector
pgvector
context
ContextPacket
worldbook
memory
token
usage
workflow
review
consistency
validator
mcp
tool
Planner
Writer
Extractor
Reviewer
```

最终 Evaluation Harness 必须根据实际类名、接口和数据模型实现。

---

# 3. 真实性原则

本套 Evaluation 的用途是解决：

> 工程实现有了，但缺少真实 Agent / RAG / Workflow 效果结果。

因此禁止：

- 随机生成结果；
- 把设计目标当实测结果；
- 把 Maven Tests Passed 当 Agent Success Rate；
- 把 HTTP 200 当 Workflow Success；
- 用字符串 Contains 代替真实 RAG；
- 用 Mock 结果冒充 Live Agent；
- 用一个 Prompt 自评自答后写成“准确率”；
- 删除困难 Case 以提高分数；
- 未执行指标写 0% 或 100%；
- 没有真实 DeepSeek Key 却显示 Live Eval Passed；
- 为了让 Evaluation 通过修改 Production Logic，而没有明确记录。

没有运行的指标：

```json
null
```

不得：

```json
0
```

---

# 4. Evaluation 类型

所有 Case 和 Result 都要区分：

```text
DETERMINISTIC
LIVE_MODEL
HUMAN_REVIEW
```

### DETERMINISTIC

适合：

- Retrieval；
- Token；
- Java Validator；
- Workflow Stub；
- MCP Schema / Permission；
- Report Generator。

### LIVE_MODEL

适合：

- DeepSeek Reviewer；
- 真正 Agent Workflow；
- 模型结构化输出稳定性。

### HUMAN_REVIEW

适合：

- Ground Truth 人工确认；
- RAG Relevant IDs；
- 边界 Case；
- Reviewer 争议案例。

CI 默认不执行 `LIVE_MODEL`。

---

# 5. Dataset 与 Result Schema

所有 Dataset 固定版本：

```text
datasetVersion: v1
```

每个 Case 至少：

```json
{
  "caseId": "example-001",
  "category": "RAG",
  "description": "...",
  "fixtureProject": "eval-project-v1",
  "input": {},
  "expected": {},
  "tags": [],
  "createdBy": "HUMAN",
  "version": 1
}
```

每个 Result 至少：

```json
{
  "caseId": "example-001",
  "passed": true,
  "actual": {},
  "expected": {},
  "latencyMs": 0,
  "tokenUsage": null,
  "cost": null,
  "error": null,
  "metadata": {}
}
```

报告必须记录 Git Commit、Dataset Version、运行时间、运行环境、Profile、是否 Live、模型版本。

---

# 6. Evaluation Fixture

不要直接使用真实用户小说。

优先：

```text
仓库已有 Demo Project
仓库已有龙族技术演示模板
原创 Evaluation World
人工构造冲突数据
```

如果使用《龙族》演示：

- 只使用名称和设定做技术 Fixture；
- 不复制原著正文；
- 测试章节必须是原创短文本；
- 不把演示章号描述为原著真实章节。

Evaluation Fixture 必须：

- 使用独立 Evaluation Project；
- 可重复初始化；
- 可 teardown；
- 使用稳定逻辑 ID 或固定 UUID；
- 不污染真实用户数据；
- 不污染正式 Redis；
- 每次运行结果可复现。

优先考虑 Testcontainers、独立 Compose Profile 或独立 Evaluation Database，根据当前工程真实情况选择。

---

# 7. Eval 1：RAG Recall@K

## 7.1 目标

测量 StoryWeaver 的 Worldbook、Character、Memory、Canon、Historical Event、Character Knowledge、Recent Chapter 检索能否把应该进入 Context 的内容召回。

## 7.2 Dataset

创建：

```text
evals/datasets/rag/retrieval_cases.jsonl
```

每个 Case：

```json
{
  "caseId": "rag-001",
  "query": "当前章节需要判断角色是否知道青铜城入口信息",
  "projectFixture": "eval-project-v1",
  "expectedRelevantIds": [
    "asset-character-knowledge-001",
    "asset-worldbook-003"
  ],
  "expectedRequiredIds": [
    "asset-character-knowledge-001"
  ],
  "tags": [
    "character-knowledge",
    "worldbook"
  ]
}
```

第一版建议至少 50 个真正不同的 Case。

覆盖：

- 人物；
- 人物别名；
- 地点；
- 势力；
- 世界规则；
- 道具；
- 历史事件；
- Character Knowledge；
- 最近章节；
- 跨章节状态；
- 关键词激活；
- 向量激活；
- 混合检索；
- 相似条目混淆。

Ground Truth 的 `expectedRelevantIds` 和 `expectedRequiredIds` 必须人工确认或人工最终审核。

## 7.3 指标

计算：

```text
Recall@1
Recall@3
Recall@5
Recall@10
Required Hit Rate@5
Required Hit Rate@10
```

Recall@K：

```text
Top K 中命中的 Relevant Asset 数
/
Ground Truth Relevant Asset 数
```

Required Hit Rate@K：

```text
expectedRequiredIds 是否全部出现在 Top K
```

还输出：

```text
按 Asset Type Recall
平均 Retrieval Latency
P95 Retrieval Latency
失败 Case
漏召回 ID
错误召回 ID
```

## 7.4 Exact Baseline

如果 Production Retrieval 使用 pgvector approximate index：

```text
同一 Query：
Exact Search
vs
Production Search
```

以 Exact Search 作为近似索引 Recall 对照。不要为了评测修改生产检索逻辑。

---

# 8. Eval 2：Token Reduction

## 8.1 目标

证明 Dynamic Context、Worldbook Activation、Memory Retrieval、Token Budget、Canonical Context Packet 相对“全部资料塞入模型”的 Naive Baseline 确实减少 Token。

## 8.2 Baseline

同一 Evaluation Case 构造：

### A. Naive Full Context

包含当前算法可访问的全部人物、世界书、状态、最近章节、历史事件、Skill。

### B. StoryWeaver Optimized Context

使用当前 Production Context Builder、Retrieval、Activation、Token Budget、Context Packet。

## 8.3 Token 计数

优先级：

1. 当前 Production 使用的真实 Provider Token Count；
2. 当前 Backend 已实际使用的 Token Estimator。

报告必须注明：

```text
ACTUAL_PROVIDER_COUNT
```

或：

```text
ESTIMATED_TOKEN_COUNT
```

不得混用而不声明。

## 8.4 指标

每个 Case：

```text
baselineTokens
optimizedTokens
```

Token Reduction：

```text
1 - optimizedTokens / baselineTokens
```

输出 Mean、Median、P50、P90、Min、Max。

## 8.5 Context Preservation Gate

每个 Case 必须检查 `expectedRequiredIds` 是否仍被保留。

额外输出：

```text
Context Preservation Rate
Quality-Preserving Token Reduction
```

只统计 Required Context Gate 通过的 Case。

---

# 9. Eval 3：Consistency

## 9.1 Dataset

创建：

```text
evals/datasets/consistency/consistency_cases.jsonl
```

第一版建议：

```text
100 Cases
50 Clean
50 Conflict
```

尽量均衡覆盖：

```text
CHARACTER_STATE
UNIQUE_ITEM
TIMELINE
CHARACTER_KNOWLEDGE
WORLD_RULE
```

Case：

```json
{
  "caseId": "consistency-001",
  "projectFixture": "eval-project-v1",
  "chapterText": "原创测试正文……",
  "expected": {
    "shouldPass": false,
    "violations": [
      {
        "type": "UNIQUE_ITEM",
        "severity": "BLOCKER"
      }
    ]
  }
}
```

## 9.2 指标

计算 TP、TN、FP、FN，并输出：

```text
Consistency Pass Rate
Conflict Precision
Conflict Recall
Conflict F1
Clean Chapter Pass Rate
Conflict Detection Rate
BLOCKER Recall
```

其中：

```text
Consistency Pass Rate =
正确判定 Case
/
全部 Case
```

不能只展示一个总体 Pass Rate。

## 9.3 分层评测

如果实际系统同时存在 Deterministic Java Validator、LLM Reviewer、Combined Pipeline，则分别测：

```text
Java Validator
LLM Reviewer
Combined
```

---

# 10. Eval 4：Workflow Success Rate

## 10.1 Case

覆盖实际 Workflow 的：

```text
正常章节
缺少上下文
Planner Structured Output 失败
Writer 中断
Extractor JSON 错误
Reviewer BLOCKER
用户拒绝
Retry
SSE 恢复
Cancel
Atomic Commit
重复请求
预算超限
```

根据当前真实状态机调整，不使用已经不存在的状态。

## 10.2 Success 定义

正常 Case 只有同时满足以下条件才成功：

- 进入预期最终状态；
- 必须步骤全部执行；
- 无未处理异常；
- Structured Output 合法；
- 不重复提交；
- Chapter Version 正确；
- Canon / State 更新符合预期；
- 原子提交成立；
- Usage 数据存在；
- 可恢复 Case 能真正恢复。

不能：

```text
HTTP 200 == Workflow Success
```

## 10.3 指标

输出：

```text
Workflow Success Rate
Planner Success Rate
Writer Success Rate
Extractor Success Rate
Reviewer Success Rate
Atomic Commit Success Rate
Recovery Success Rate
Cancellation Correctness Rate
Mean Latency
P95 Latency
Mean Token
Mean Cost
```

## 10.4 Stub 与 Live 分开

DETERMINISTIC 使用 Stub LLM 测 Workflow Engine；LIVE_MODEL 使用真实 DeepSeek 测真实 Agent Pipeline。

报告必须分别：

```text
Workflow Engine Success Rate
Live Agent Workflow Success Rate
```

---

# 11. Eval 5：MCP Tool Success Rate

## 11.1 真实 Tool 枚举

从当前实际 MCP Server 自动发现 Tool，不根据旧设计稿硬编码。

## 11.2 Case 类型

```text
VALID_CALL
INVALID_ARGUMENT
NOT_FOUND
FORBIDDEN
READ_ONLY
CANDIDATE_WRITE
```

验证：

```text
Tool 是否存在
Input Schema 是否正确
执行是否成功
Output Schema 是否正确
副作用是否符合预期
错误语义是否正确
权限是否正确
只读是否真的只读
候选写入是否未污染 Canon
```

## 11.3 指标

输出：

```text
MCP Tool Success Rate
Valid Invocation Success Rate
Invalid Input Rejection Rate
Authorization Enforcement Rate
Output Schema Pass Rate
```

如果当前 Agent 会自动选 MCP Tool，再额外测：

```text
Tool Selection Accuracy
Tool Argument Accuracy
```

---

# 12. Evaluation Runner

必须有统一命令，至少支持：

```text
rag
token
consistency
workflow
mcp
all
```

Profiles：

```text
ci
local
live
```

参数：

```text
--dataset-version
--output
--repetitions
```

无论底层使用 Java、Python 还是 TypeScript，都必须提供稳定 Windows 入口：

```text
evals/run-evals.cmd
```

---

# 13. 一键启动 CMD

## 13.1 文件路径

Codex 必须创建：

```text
evals/run-evals.cmd
```

以及稳定 PowerShell 适配层：

```text
evals/scripts/run-all.ps1
```

`run-evals.cmd` 不直接承载复杂 Evaluation 业务。

职责：

```text
切到仓库根目录
→ 检查基础环境
→ 调用 run-all.ps1
→ 返回 Exit Code
→ 显示最新报告路径
```

## 13.2 默认双击行为

直接双击：

```text
evals/run-evals.cmd
```

必须运行：

```text
Offline / Deterministic 全量 Baseline
```

即：

```text
RAG
Token
Consistency Deterministic
Workflow Stub
MCP
Report Generation
```

默认绝不能调用真实 DeepSeek。

默认 Profile：

```text
local
```

Live 指标如果未执行，必须为 `null`。

## 13.3 参数

必须支持：

```bat
run-evals.cmd
run-evals.cmd all
run-evals.cmd rag
run-evals.cmd token
run-evals.cmd consistency
run-evals.cmd workflow
run-evals.cmd mcp
run-evals.cmd live
run-evals.cmd help
```

## 13.4 Live 安全门禁

`run-evals.cmd live` 只有环境变量：

```text
STORYWEAVER_EVAL_LIVE=true
```

存在时才允许 Live。

否则输出清晰错误并：

```text
exit /b 2
```

如果 `DEEPSEEK_API_KEY` 缺失，则 Live Metric 保持 `null`，不得把 SKIPPED 算 PASS。

## 13.5 Windows 兼容

必须：

```bat
chcp 65001 >nul
```

并使用：

```bat
cd /d
```

保证中文提示和跨盘符路径可靠。

## 13.6 CMD 预期骨架

Codex 可根据真实 Runner 微调，但语义至少类似：

```bat
@echo off
setlocal EnableExtensions
chcp 65001 >nul

set "EVALS_DIR=%~dp0"
cd /d "%EVALS_DIR%.."

set "MODE=%~1"
if "%MODE%"=="" set "MODE=all"

if /I "%MODE%"=="help" goto :help

if /I "%MODE%"=="live" (
    if /I not "%STORYWEAVER_EVAL_LIVE%"=="true" (
        echo.
        echo [ERROR] Live evaluation is disabled.
        echo Set STORYWEAVER_EVAL_LIVE=true before running live evals.
        echo.
        exit /b 2
    )
)

if not exist "%EVALS_DIR%scripts\run-all.ps1" (
    echo [ERROR] Missing evals\scripts\run-all.ps1
    exit /b 3
)

powershell -NoProfile -ExecutionPolicy Bypass ^
  -File "%EVALS_DIR%scripts\run-all.ps1" ^
  -Mode "%MODE%" ^
  -Profile "local"

set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
    echo [OK] StoryWeaver Agent Evaluation completed.
    echo Report: evals\reports\latest\summary.md
) else (
    echo [FAILED] Evaluation exited with code %EXIT_CODE%.
)

echo.
pause
exit /b %EXIT_CODE%

:help
echo StoryWeaver Agent Evaluation
echo.
echo Usage:
echo   run-evals.cmd
echo   run-evals.cmd all
echo   run-evals.cmd rag
echo   run-evals.cmd token
echo   run-evals.cmd consistency
echo   run-evals.cmd workflow
echo   run-evals.cmd mcp
echo   run-evals.cmd live
echo.
exit /b 0
```

必须保留：

- 双击 Offline All；
- Live 门禁；
- Exit Code；
- UTF-8；
- 相对路径；
- Report 路径；
- Help。

---

# 14. PowerShell 稳定适配层

创建：

```text
evals/scripts/run-all.ps1
```

目的：

> `run-evals.cmd` 永远只调用这一层。以后底层 Runner 从 Python 改成 Java，也不要求用户换 CMD。

参数建议：

```powershell
param(
    [ValidateSet(
        "all",
        "rag",
        "token",
        "consistency",
        "workflow",
        "mcp",
        "live"
    )]
    [string]$Mode = "all",

    [ValidateSet(
        "local",
        "ci",
        "live"
    )]
    [string]$Profile = "local",

    [string]$DatasetVersion = "v1",

    [int]$Repetitions = 1
)
```

职责：

1. 定位 Repo Root；
2. 检查实际需要的 Docker / Java / Python / Node 环境；
3. 初始化隔离 Eval Fixture；
4. 执行选定 Eval；
5. 汇总结果；
6. 生成 `summary.json`；
7. 生成 `summary.md`；
8. 更新 `reports/latest`；
9. teardown；
10. 保留失败 Artifact；
11. 返回正确 Exit Code。

---

# 15. Reports

每次运行：

```text
evals/reports/<timestamp>/
```

例如：

```text
evals/reports/2026-08-10_171500/
├── summary.json
├── summary.md
├── rag.json
├── token.json
├── consistency.json
├── workflow.json
├── mcp.json
└── raw/
```

然后更新：

```text
evals/reports/latest/
```

Windows 下避免强依赖 symbolic link，可以复制最新 summary 或维护 `latest.json` 指针。

---

# 16. Summary JSON

至少：

```json
{
  "datasetVersion": "v1",
  "gitCommit": null,
  "timestamp": null,
  "profile": "local",
  "metrics": {
    "ragRecallAt1": null,
    "ragRecallAt3": null,
    "ragRecallAt5": null,
    "ragRecallAt10": null,
    "requiredHitRateAt5": null,
    "requiredHitRateAt10": null,
    "tokenReduction": null,
    "contextPreservationRate": null,
    "qualityPreservingTokenReduction": null,
    "consistencyPassRate": null,
    "conflictPrecision": null,
    "conflictRecall": null,
    "conflictF1": null,
    "blockerRecall": null,
    "workflowEngineSuccessRate": null,
    "liveWorkflowSuccessRate": null,
    "atomicCommitSuccessRate": null,
    "recoverySuccessRate": null,
    "mcpToolSuccessRate": null,
    "authorizationEnforcementRate": null,
    "outputSchemaPassRate": null
  }
}
```

未运行必须保持 `null`。

---

# 17. Summary Markdown

自动生成：

```text
evals/reports/latest/summary.md
```

包含：

- Dataset；
- Commit；
- Date；
- Profile；
- Live Model 状态；
- Executive Summary；
- RAG；
- Token；
- Consistency；
- Workflow；
- MCP；
- Failed Cases；
- Limitations。

---

# 18. Resume Metrics

真实 Evaluation 成功后自动生成：

```text
docs/evaluation-results.md
```

包含：

```text
Dataset Version
Git Commit
运行环境
Case 数
RAG Recall@5
RAG Recall@10
Token Reduction
Context Preservation Rate
Consistency Pass Rate
Conflict F1
Workflow Engine Success Rate
Live Workflow Success Rate
MCP Tool Success Rate
测试方法
局限性
```

报告末尾生成 `Resume Metrics Candidate`。

数字必须程序化读取 `summary.json`，不得手写。

---

# 19. Dataset Ground Truth

第一版允许 LLM 辅助生成 Case 草稿，但以下 Ground Truth 必须人工确认：

```text
RAG Relevant IDs
Required IDs
Consistency Expected Violations
Workflow Expected Final State
MCP Expected Result
```

Dataset README 明确每个 Case 来源：

```text
HUMAN
PROGRAMMATIC
MODEL_ASSISTED_HUMAN_REVIEWED
```

---

# 20. 回归策略

每次发现真实 Failure：

```text
修复
→ 将 Failure 固化成 Dataset Case
→ 更新 Dataset Version / Changelog
→ 重新跑 Eval
```

禁止因为 Case 难而删除。

Dataset 变更必须记录：

```text
新增 Case
删除 Case
修改 Ground Truth
原因
Reviewer
```

---

# 21. Live Eval 稳定性

Deterministic 默认运行 1 次。

Live 支持：

```text
--repetitions 3
```

报告 Mean、Min、Max、Standard Deviation。

如果只运行 1 次，明确写：

```text
single-run result
```

---

# 22. 成本

每个 Case 尽量记录：

```text
Latency
Input Tokens
Output Tokens
Reasoning Tokens
Cache Tokens
Cost
```

价格真值必须复用 Backend 现有 Usage / Pricing，不在 `evals/` 维护第二套价格表。

Live 开始前可以估算调用量和预算，但估算值不得写成实际成本。

---

# 23. Failure Artifact

失败 Case 保存：

```text
input
expected
actual
retrievedIds
contextIds
workflowStates
toolCalls
errors
usage
```

必须脱敏：

```text
API Key
JWT
Secret
真实用户正文
```

---

# 24. Evaluation Harness 自身测试

至少测试：

```text
Dataset Parser
Dataset Version
Recall Calculator
Required Hit Rate
Token Reduction Calculator
Preservation Gate
Confusion Matrix
Precision / Recall / F1
Workflow Scorer
MCP Scorer
Skipped / Null Metric
Report Generator
Failure Artifact
Secret Redaction
CMD / PowerShell argument mapping
```

---

# 25. CI

创建：

```text
.github/workflows/agent-evals.yml
```

默认 CI：

```text
Dataset Schema Validation
Harness Unit Tests
RAG Deterministic
Token
Consistency Deterministic
Workflow Stub
MCP
Report Generation
```

绝不默认调用 DeepSeek。

Live Eval 通过 `workflow_dispatch`，并明确要求 `STORYWEAVER_EVAL_LIVE=true`。

---

# 26. evals/AGENTS.md

至少包含：

```text
1. 结果必须真实运行；
2. 禁止随机数字；
3. 禁止删除困难失败 Case；
4. Dataset 必须版本化；
5. Live Eval 不能默认运行；
6. 不打印 Secret；
7. 不使用真实用户小说作为 Fixture；
8. 未运行指标必须是 null；
9. Report 必须带 Git Commit；
10. 修改 Scoring 规则必须更新测试和文档。
```

根目录 `AGENTS.md` 只增加必要的一句：

```text
Agent Evaluation 位于 evals/，执行规则见 evals/AGENTS.md。
```

---

# 27. evals/README.md

说明：

```text
为什么需要 Agent Evaluation
五个核心指标
Dataset
Offline vs Live
如何双击 run-evals.cmd
如何从命令行只跑某一项
如何运行 Live
如何查看报告
如何新增 Case
如何处理 Regression
如何看失败 Artifact
成本规则
已知限制
```

Windows 最短用法：

```text
双击：

evals\run-evals.cmd
```

命令行：

```bat
evals\run-evals.cmd rag
evals\run-evals.cmd consistency
evals\run-evals.cmd all
```

---

# 28. 根 README

增加简洁的 Agent Evaluation 章节。

若已有真实结果可展示指标；若未完整执行，则只写：

```text
Run the evaluation suite to generate current metrics.
```

不得填假数字。

---

# 29. Evaluation Config

创建：

```text
evals/config/eval.yml
```

至少：

```yaml
datasetVersion: v1

rag:
  ks: [1, 3, 5, 10]

token:
  qualityGate: true

consistency:
  categories:
    - CHARACTER_STATE
    - UNIQUE_ITEM
    - TIMELINE
    - CHARACTER_KNOWLEDGE
    - WORLD_RULE

workflow:
  liveModelEnabled: false

mcp:
  authorizationTests: true

report:
  json: true
  markdown: true
```

---

# 30. 本次不提前优化

本次第一目标：

```text
建立真实 Baseline
```

不是：

```text
把 Recall 调到 95%
调整 Prompt
调整 Reranker
修改 Workflow
为了过 Eval 改 Production Logic
```

如果发现问题：

```text
记录 Failure
生成报告
提出下一轮优化建议
```

除非是 Harness 本身 Bug，否则本次不做“刷分式”优化。

---

# 31. 实际执行

实现后先验证 Backend：

```bash
cd backend
./mvnw clean verify
```

根目录：

```bash
docker compose config
```

然后真实执行：

```text
RAG Eval
Token Eval
Consistency Deterministic Eval
Workflow Deterministic Eval
MCP Eval
Report Generation
```

Windows 最终必须真实执行：

```bat
evals\run-evals.cmd
```

如果当前 Codex 环境不是 Windows：

- 静态检查 `run-evals.cmd`；
- 使用 `run-all.ps1` 执行同等路径；
- 报告注明 Windows CMD 未实际双击验证。

---

# 32. Live Eval

除非当前环境已经存在：

```text
STORYWEAVER_EVAL_LIVE=true
```

否则不要调用真实 DeepSeek。

Codex 应：

1. 实现 Live Runner；
2. 验证配置解析；
3. 输出预计 Case / 调用数；
4. Live Metrics 保持 `null`；
5. 文档说明如何运行：

```bat
set STORYWEAVER_EVAL_LIVE=true
set DEEPSEEK_API_KEY=...
evals\run-evals.cmd live
```

不要把 API Key 写进 Git 或报告。

---

# 33. Definition of Done

必须满足：

```text
[ ] evals 位于仓库根目录
[ ] evals 不属于 backend/src/test
[ ] 五项核心指标有正式定义
[ ] RAG 有人工 Relevant Ground Truth
[ ] Recall@1/3/5/10 可运行
[ ] Token 有 Naive Baseline
[ ] Token 有 Context Preservation Gate
[ ] Consistency 有 Clean + Conflict
[ ] Consistency 输出 Precision / Recall / F1
[ ] Workflow 不以 HTTP 200 判成功
[ ] Workflow 区分 Stub 与 Live
[ ] MCP 测成功、非法参数、权限、Schema
[ ] Dataset 有版本
[ ] Report 带 Git Commit
[ ] 未运行 Metric 输出 null
[ ] Failure Case 有 Artifact
[ ] Harness 自身有测试
[ ] CI 默认不调用 DeepSeek
[ ] Live Eval 单独门禁
[ ] evals/run-evals.cmd 可作为 Windows 一键入口
[ ] CMD 默认运行 Offline All
[ ] CMD 支持 rag/token/consistency/workflow/mcp/live/help
[ ] CMD Live 有 STORYWEAVER_EVAL_LIVE 门禁
[ ] CMD 返回正确 Exit Code
[ ] evals/scripts/run-all.ps1 是稳定适配层
[ ] 根 README 不包含假结果
[ ] docs/evaluation-results.md 只能由真实 summary 生成
[ ] 不修改真实用户项目
[ ] 不删除困难 Case
```

---

# 34. 最终汇报格式

Codex 完成后按以下格式汇报：

```text
StoryWeaver Agent Evaluation Harness

读取的文档：
- ...

Evaluation 实现方式：
- Java / Python / TypeScript / Hybrid
- 为什么这样选择

新增目录：
- evals/...

一键启动：
- evals/run-evals.cmd
- 默认行为
- 支持参数
- Live 门禁

Datasets：
- RAG Case 数
- Consistency Case 数
- Workflow Case 数
- MCP Case 数
- Dataset Version

核心指标：
- RAG Recall@1
- RAG Recall@3
- RAG Recall@5
- RAG Recall@10
- Required Hit Rate@5
- Required Hit Rate@10
- Token Reduction
- Context Preservation Rate
- Quality-Preserving Token Reduction
- Consistency Pass Rate
- Conflict Precision
- Conflict Recall
- Conflict F1
- BLOCKER Recall
- Workflow Engine Success Rate
- Live Workflow Success Rate
- Atomic Commit Success Rate
- Recovery Success Rate
- MCP Tool Success Rate
- Authorization Enforcement Rate
- Output Schema Pass Rate

实际执行：
- Backend Tests
- docker compose config
- run-evals.cmd / equivalent
- Harness Tests

生成报告：
- evals/reports/<timestamp>/summary.md
- evals/reports/latest/summary.md
- docs/evaluation-results.md

Live Eval：
- 是否执行
- 未执行时为什么
- Live Metrics 是否正确为 null

失败 Case：
- 数量
- 主要类型

发现的问题：
- ...

与设计不同：
- ...

下一步建议：
- 只能基于真实 Failure 和 Metric 提建议
```

不要提前优化模型，先交付真实 Baseline。

---

# 35. 本次给 Codex 的最短提示词

把本文件放到 StoryWeaver 仓库根目录后，只需给 Codex：

```text
阅读根目录的 STORYWEAVER_AGENT_EVAL_CODEX.md，并完整执行。

先阅读 AGENTS.md、当前 backend/frontend 代码和最新设计文档，
根据实际实现建立根目录 evals/ Agent Evaluation Harness。

必须实现并真实运行可执行的 Offline Baseline：
RAG Recall@K、Token Reduction、Consistency、Workflow Stub、MCP。

必须创建：
evals/run-evals.cmd

Windows 用户双击该文件应一键执行 Offline 全量评测并生成 latest report。

不要默认调用真实 DeepSeek。
不要提前优化 Agent。
先建立真实 Baseline，完成测试和报告后再汇报。
```

---

# 36. 设计依据

本 Evaluation Harness 遵循以下原则：

- 先明确 Evaluation Objective；
- 建立可重复 Dataset；
- 为具体能力定义具体 Metric；
- 同时覆盖典型、边缘和失败 Case；
- 自动化可确定性指标；
- 将 Agent Workflow 与 Tool 行为单独评估；
- 通过失败 Case 持续扩充 Regression Dataset；
- 将 Live Model Eval 与 CI Deterministic Eval 分离；
- 不采用“感觉效果不错”的 Vibe-based Evaluation。

参考：

- OpenAI Evaluation Best Practices  
  https://developers.openai.com/api/docs/guides/evaluation-best-practices

- OpenAI Agent Evals  
  https://developers.openai.com/api/docs/guides/agent-evals

- OpenAI Codex Prompting Guide  
  https://developers.openai.com/cookbook/examples/gpt-5/codex_prompting_guide.md

- OpenAI Code Modernization Cookbook  
  https://developers.openai.com/cookbook/examples/codex/code_modernization
