# Agent Evaluation

StoryWeaver 的仓库级 Agent Evaluation 位于 [`../evals`](../evals)。它使用版本化原创 Fixture 和确定性的生产适配器，测量 RAG Recall@K、Token Reduction、Consistency、Workflow Stub 与 MCP，而不把单元测试数量或 HTTP 200 当作 Agent 效果。

## 执行

Windows 用户双击 `evals\run-evals.cmd` 即可运行 Offline 全量 Baseline。默认不读取或调用 DeepSeek。单项模式、Dataset 规则、Live 门禁、报告结构和已知限制见 [`../evals/README.md`](../evals/README.md)。

## 结果

- 当前完整报告：`evals/reports/latest/summary.md`
- 机器可读指标：`evals/reports/latest/summary.json`
- 失败 Artifact：`evals/reports/latest/raw/failures.json`
- 自动生成的项目指标摘要：[`evaluation-results.md`](evaluation-results.md)

未执行的能力始终写为 JSON `null`。Live Workflow 与 Offline Workflow Stub 必须分开呈现。
