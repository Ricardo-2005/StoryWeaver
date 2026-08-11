# StoryWeaver Agent Evaluation Report

- Dataset: `v1`
- Commit: `null`
- Date: `2026-08-11T03:10:08.015189400Z`
- Profile: `local`
- Mode: `all`
- RAG Configuration: `{retrievalMode=CONSTANT_ISOLATED, candidatePoolSize=10, finalRankingSize=2147483647, rrfRankConstant=60}`
- Live model executed: `false`

## Executive Summary

This report is generated from versioned offline datasets and production Java capability adapters. The retrieval configuration is recorded in `summary.json`; failures remain in `raw/failures.json`.

## RAG

| Metric | Result |
| --- | ---: |
| ragRecallAt1 | 12.00% |
| ragRecallAt3 | 32.00% |
| ragRecallAt5 | 58.50% |
| ragRecallAt10 | 95.50% |
| requiredHitRateAt5 | 60.00% |
| requiredHitRateAt10 | 100.00% |
| allRequiredHitRateAt5 | 60.00% |
| allRequiredHitRateAt10 | 100.00% |

## RAG Ranking Quality

| Metric | Result |
| --- | ---: |
| mrr | 0.43038095238095236 |
| binaryNdcgAt5 | 0.4052206014130727 |
| binaryNdcgAt10 | 0.5414227614548733 |
| meanFirstRequiredRank | 4.98 |
| medianFirstRequiredRank | 5.0 |
| p95FirstRequiredRank | 9.0 |

## Token

| Metric | Result |
| --- | ---: |
| tokenReduction | 78.42% |
| contextPreservationRate | 100.00% |
| qualityPreservingTokenReduction | 78.42% |

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
