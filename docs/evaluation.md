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

## TXT 重建与生命周期数据集状态

仓库还包含以下独立原创夹具，它们用于 V1.1/V1.2 后续评测，不属于上方 `evaluation-results.md` 已发布的 v1 指标：

| Dataset | 目标 | 当前状态 |
| --- | --- | --- |
| `import-reconstruction` | 人物/别名/世界实体/章节到 Arc/伏笔/Evidence | `DRAFT_NOT_REVIEWED` / Not Run |
| `temporal-rag` | 未来泄漏、当前状态、归档/取代事实和人物知识边界 | `DRAFT_NOT_REVIEWED` / Not Run |
| `entity-lifecycle` | 同名歧义、别名合并、一次性人物和 MERGED/ARCHIVED/PURGED 检索 | `DRAFT_NOT_REVIEWED` / Not Run |
| `rolling-outline` | 仅由确认章节刷新、来源变化后旧快照失效 | `DRAFT_NOT_REVIEWED` / Not Run |
| `foreshadow` | 到期召回、误报和已回收伏笔退出待办上下文 | `DRAFT_NOT_REVIEWED` / Not Run |

这些目录中的指标当前全部为 JSON `null`。在 Ground Truth 人工复核、Harness 接线并实际运行之前，不得从 Fixture、单元测试或 UI 状态推导准确率，也不应手工修改自动生成的 `evaluation-results.md` 冒充结果。
