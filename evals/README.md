# StoryWeaver Agent Evaluation Harness

该目录是独立于 `backend/src/test` 和 `frontend/tests` 的仓库级评测系统。工程测试回答“代码是否按契约运行”，本 Harness 回答“RAG、上下文压缩、一致性、工作流与 MCP 的实际基线表现如何”。

## Windows 最短用法

在资源管理器双击：

```text
evals\run-evals.cmd
```

默认执行 `local` Profile 的 Offline 全量评测：生产默认 `VECTOR_ONLY` RAG、Token、Consistency Java Validator、Workflow Stub、MCP 和报告生成。默认路径不会调用 DeepSeek。冻结基线可用 `baseline` 单独复现。

命令行可只运行一项：

```bat
evals\run-evals.cmd all
evals\run-evals.cmd rag
evals\run-evals.cmd token
evals\run-evals.cmd consistency
evals\run-evals.cmd workflow
evals\run-evals.cmd mcp
evals\run-evals.cmd baseline
evals\run-evals.cmd experiments
evals\run-evals.cmd help
```

PowerShell 适配层支持版本、输出目录与重复次数：

```powershell
./evals/scripts/run-all.ps1 -Mode all -Profile local -DatasetVersion v1 -Repetitions 1
./evals/scripts/run-all.ps1 -Mode rag -Output D:\temp\storyweaver-eval
```

## 五类指标

- RAG：`Recall@1/3/5/10`、`All-Required Hit@5/10`、MRR、binary NDCG@5/10、首个 Required Rank 分布、按资产类型 Recall 和检索延迟。
- Token：Naive Full Context 与 Production Activation Context 的 Token Reduction，并通过 Required Context Preservation Gate。
- Consistency：TP/TN/FP/FN、Accuracy、Precision、Recall、F1、Clean Pass、Conflict Detection 和 BLOCKER Recall。
- Workflow：不以 HTTP 200 计成功；核对最终状态、必需步骤、结构化输出、恢复、取消与原子提交。Offline 使用明确标记的 Stub LLM。
- MCP：从 `StoryMcpCapabilities` 注解发现真实 Tool，验证调用、非法参数、Not Found、权限、只读、副作用和候选写入边界。

## 实现与真实性边界

- RAG 直接调用生产 `WorldbookService.preview` 和仓库自带 `BAAI/bge-small-zh-v1.5` ONNX 模型。离线向量仓库使用 exact cosine，故不把它写成 pgvector ANN 指标。
- 五种可复现实验模式为 `BASELINE`、`CONSTANT_ISOLATED`、`KEYWORD_ONLY`、`VECTOR_ONLY`、`HYBRID_FUSION`；RRF、候选池、final K 与去重均有确定性测试。
- Token 直接调用生产 `TokenEstimator`，报告明确标记 `ESTIMATED_TOKEN_COUNT`。
- Consistency 直接实例化生产 Java Validators。LLM Reviewer/Combined 未运行时为 `null`。
- Workflow 直接驱动生产 `WorkflowRun` 和 `WorkflowStateMachine`；Planner/Writer/Extractor/Reviewer 的输出由确定性 Stub 提供，绝不冒充 Live Agent。
- Offline `atomicCommitSuccessRate` 检查状态、版本号、审批者和回滚不提交等领域不变量；真正数据库事务回滚仍由需要 Docker 的 Backend Integration Tests 负责。
- MCP 调用自动发现的生产 Capability 方法，底层使用隔离内存 Fixture，不启动正式数据库，也不污染 Canon。

## Dataset 与新增 Case

所有 JSONL Case 都必须包含 `datasetVersion`、`caseId`、`category`、`description`、`fixtureProject`、`input`、`expected`、`tags`、`createdBy` 和 `version`。RAG Relevant/Required IDs、一致性预期、Workflow 最终状态与 MCP 预期结果是 Ground Truth。

新增或修改 Case 时：

1. 保留失败 Case，不以删题提高分数；
2. 人工复核 Ground Truth；
3. 更新 `datasets/CHANGELOG.md`；
4. Ground Truth 变化时升级 Dataset Version；
5. 运行 Harness 测试与 Offline 全量评测。

## 报告与 Regression

每次运行写入：

```text
evals/reports/<timestamp>/
evals/reports/latest/
docs/evaluation-results.md
```

一键运行完成后还会自动生成并在 CMD 窗口打印总览：

```text
evals/reports/latest/all-results.md
evals/reports/latest/all-results.csv
evals/reports/latest/all-results.html
```

总表横向汇总冻结基线、所有实验策略、当前 selected、holdout、RAG 全指标、Token、Consistency、Workflow Stub、MCP、失败数和失败分布。双击 CMD 后默认用浏览器打开 HTML；自动化环境可设置 `STORYWEAVER_EVAL_NO_OPEN=1`。

`raw/failures.json` 保留失败输入、预期、实际、上下文/状态/Tool 信息和脱敏错误。发现回归后先确认 Harness 与 Ground Truth，再修复真实问题并把失败固化为 Case；禁止修改 Scoring 来掩盖回归。

冻结 v1、首次 holdout 与完整实验矩阵分别位于：

```text
evals/reports/baseline-v1/
evals/reports/holdout-v1/
evals/reports/experiments/latest/
```

详细方法、根因和取舍见 `docs/rag-evaluation-optimization.md`。

## Live 安全门禁

Live 入口只有显式门禁后才允许进入：

```bat
set STORYWEAVER_EVAL_LIVE=true
set DEEPSEEK_API_KEY=...
evals\run-evals.cmd live
```

当前 v1 没有经过人工确认的 `LIVE_MODEL` Dataset，因此 Live 入口只验证门禁、配置和报告 null 语义，不发起模型调用；`estimatedCases/estimatedCalls` 为 0，所有 Live 指标保持 `null`。后续增加 Live Dataset 后，价格与成本必须复用 Backend Usage/Pricing，不得在 `evals` 维护第二张价格表。

## 已知限制

- Offline RAG 不测 pgvector ANN 与网络/数据库性能。
- Java Validator 分数不能代表 LLM Reviewer 泛化能力。
- Workflow Stub 分数不能写成 Live Agent Workflow 分数。
- MCP v1 是进程内 Contract/Invocation 评测，不包含 Streamable HTTP 传输压测。
- 当前工作区没有 `.git` 元数据时，报告中的 `gitCommit` 如实为 `null`。
- `datasets/import-reconstruction` 目前只有原创 Draft Fixture，Ground Truth 尚未人工复核，因此人物、别名、世界书、Arc、伏笔与 Evidence 指标均未运行且不得填写效果数字。
- `datasets/temporal-rag`、`entity-lifecycle`、`rolling-outline`、`foreshadow` 是 V1.2 原创 Draft Fixture；当前均为 `Not Run`，对应指标严格为 `null`。
