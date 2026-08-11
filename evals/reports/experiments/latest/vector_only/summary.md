# StoryWeaver Agent Evaluation Report

- Dataset: `v1`
- Commit: `null`
- Date: `2026-08-11T03:10:20.206306600Z`
- Profile: `local`
- Mode: `all`
- RAG Configuration: `{retrievalMode=VECTOR_ONLY, candidatePoolSize=10, finalRankingSize=2147483647, rrfRankConstant=60}`
- Live model executed: `false`

## Executive Summary

This report is generated from versioned offline datasets and production Java capability adapters. The retrieval configuration is recorded in `summary.json`; failures remain in `raw/failures.json`.

## RAG

| Metric | Result |
| --- | ---: |
| ragRecallAt1 | 63.50% |
| ragRecallAt3 | 88.00% |
| ragRecallAt5 | 93.00% |
| ragRecallAt10 | 95.50% |
| requiredHitRateAt5 | 100.00% |
| requiredHitRateAt10 | 100.00% |
| allRequiredHitRateAt5 | 100.00% |
| allRequiredHitRateAt10 | 100.00% |

## RAG Ranking Quality

| Metric | Result |
| --- | ---: |
| mrr | 1.0 |
| binaryNdcgAt5 | 0.9255134429051463 |
| binaryNdcgAt10 | 0.9362261424202579 |
| meanFirstRequiredRank | 1.04 |
| medianFirstRequiredRank | 1.0 |
| p95FirstRequiredRank | 1.0 |

## Token

| Metric | Result |
| --- | ---: |
| tokenReduction | 78.59% |
| contextPreservationRate | 100.00% |
| qualityPreservingTokenReduction | 78.59% |

## Consistency

| Metric | Result |
| --- | ---: |
| consistencyPassRate | 100.00% |
| conflictPrecision | 100.00% |
| conflictRecall | 100.00% |
| conflictF1 | 100.00% |
| blockerRecall | 100.00% |

## Workflow

| Metric | Result |
| --- | ---: |
| workflowEngineSuccessRate | 100.00% |
| liveWorkflowSuccessRate | — |
| atomicCommitSuccessRate | 100.00% |
| recoverySuccessRate | 100.00% |

## MCP

| Metric | Result |
| --- | ---: |
| mcpToolSuccessRate | 100.00% |
| authorizationEnforcementRate | 100.00% |
| outputSchemaPassRate | 100.00% |

## Failed Cases

- Total: 5
- rag: 5
- token: 0
- consistency: 0
- workflow: 0
- mcp: 0

## Limitations

- RAG uses the production `WorldbookService` and repository ONNX model, with an in-memory exact-cosine adapter instead of PostgreSQL pgvector ANN.
- Token counts use the production `TokenEstimator` (`ESTIMATED_TOKEN_COUNT`), not provider billing counts.
- Consistency measures deterministic Java validators; LLM Reviewer and Combined metrics are `null`.
- Workflow measures the production state machine with deterministic LLM stubs; live workflow, provider token and cost metrics are `null`.
- Atomic commit cases measure domain state/version invariants in the Stub harness; database transaction rollback remains covered by Docker-backed backend integration tests.
- MCP invokes discovered production capability methods against an isolated deterministic service fixture; it does not start the HTTP transport.
- RAG relevant IDs and expected outcomes are versioned human-authored fixture ground truth.
