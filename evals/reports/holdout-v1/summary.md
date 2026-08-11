# StoryWeaver Agent Evaluation Baseline

- Dataset: `rag-holdout-v1`
- Commit: `null`
- Date: `2026-08-11T02:50:55.590490700Z`
- Profile: `local`
- Mode: `rag-token`
- Live model executed: `false`

## Executive Summary

This report is generated from the versioned offline datasets and production Java capability adapters. It is a baseline, not an optimized target. Failed cases are retained in `raw/failures.json`.

## RAG

| Metric | Result |
| --- | ---: |
| ragRecallAt1 | 48.96% |
| ragRecallAt3 | 84.03% |
| ragRecallAt5 | 94.10% |
| ragRecallAt10 | 98.61% |
| requiredHitRateAt5 | 91.67% |
| requiredHitRateAt10 | 100.00% |
| allRequiredHitRateAt5 | 91.67% |
| allRequiredHitRateAt10 | 100.00% |
| mrr | 97.92% |
| binaryNdcgAt5 | 91.71% |
| binaryNdcgAt10 | 93.98% |

## RAG Rank Distribution

| Metric | Result |
| --- | ---: |
| meanFirstRequiredRank | 1.125 |
| medianFirstRequiredRank | 1.0 |
| p95FirstRequiredRank | 2.0 |

## Token

| Metric | Result |
| --- | ---: |
| tokenReduction | 72.89% |
| contextPreservationRate | 100.00% |
| qualityPreservingTokenReduction | 72.89% |

## Consistency

| Metric | Result |
| --- | ---: |
| consistencyPassRate | — |
| conflictPrecision | — |
| conflictRecall | — |
| conflictF1 | — |
| blockerRecall | — |

## Workflow

| Metric | Result |
| --- | ---: |
| workflowEngineSuccessRate | — |
| liveWorkflowSuccessRate | — |
| atomicCommitSuccessRate | — |
| recoverySuccessRate | — |

## MCP

| Metric | Result |
| --- | ---: |
| mcpToolSuccessRate | — |
| authorizationEnforcementRate | — |
| outputSchemaPassRate | — |

## Failed Cases

- Total: 1
- rag: 1
- token: 0

## Limitations

- RAG uses the production `WorldbookService` and repository ONNX model, with an in-memory exact-cosine adapter instead of PostgreSQL pgvector ANN.
- Token counts use the production `TokenEstimator` (`ESTIMATED_TOKEN_COUNT`), not provider billing counts.
- Consistency measures deterministic Java validators; LLM Reviewer and Combined metrics are `null`.
- Workflow measures the production state machine with deterministic LLM stubs; live workflow, provider token and cost metrics are `null`.
- Atomic commit cases measure domain state/version invariants in the Stub harness; database transaction rollback remains covered by Docker-backed backend integration tests.
- MCP invokes discovered production capability methods against an isolated deterministic service fixture; it does not start the HTTP transport.
- RAG relevant IDs and expected outcomes are versioned human-authored fixture ground truth.
