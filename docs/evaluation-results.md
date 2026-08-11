# StoryWeaver Agent Evaluation Results

> 此文件由 `evals` Harness 从真实 `summary.json` 自动生成，请勿手写指标。

- Dataset Version: `v1`
- Git Commit: `null`
- Timestamp: `2026-08-11T03:41:31.869001400Z`
- Profile: `local`
- RAG Configuration: `{retrievalMode=VECTOR_ONLY, candidatePoolSize=10, finalRankingSize=2147483647, rrfRankConstant=60}`
- Environment: `{os=Windows 11, osVersion=10.0, java=23.0.2, timezone=Asia/Shanghai}`
- Case Counts: `{rag=50, token=50, consistency=100, workflow=14, mcp=18}`

## Metrics

| Metric | Result |
| --- | ---: |
| ragRecallAt5 | 93.00% |
| ragRecallAt10 | 95.50% |
| allRequiredHitRateAt10 | 100.00% |
| mrr | 1.0 |
| binaryNdcgAt10 | 0.9362261424202579 |
| tokenReduction | 78.59% |
| contextPreservationRate | 100.00% |
| consistencyPassRate | 100.00% |
| conflictF1 | 100.00% |
| workflowEngineSuccessRate | 100.00% |
| liveWorkflowSuccessRate | — |
| mcpToolSuccessRate | 100.00% |

## Method

Deterministic production adapters, versioned fixtures, offline ONNX retrieval, Java validators, workflow stubs, and MCP contract invocation. Retrieval mode and candidate/final ranking parameters are captured in the summary.

## Limitations

Live model metrics remain `null` unless a separately versioned LIVE_MODEL suite is actually executed. Exact in-memory vector search is not a pgvector ANN performance test.

## Result Snapshot

On StoryWeaver Eval v1, measured RAG Recall@5 93.00%, All-Required Hit@10 100.00%, MRR 1.0, Token Reduction 78.59%, Consistency F1 100.00%, Workflow Stub Success 100.00%, and MCP Tool Success 100.00%.
