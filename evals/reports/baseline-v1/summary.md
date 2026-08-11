# StoryWeaver Agent Evaluation Baseline

- Dataset: `v1`
- Commit: `null`
- Date: `2026-08-11T02:35:50.164886500Z`
- Profile: `local`
- Mode: `all`
- Live model executed: `false`

## Executive Summary

This report is generated from the versioned offline datasets and production Java capability adapters. It is a baseline, not an optimized target. Failed cases are retained in `raw/failures.json`.

## RAG

| Metric | Result |
| --- | ---: |
| ragRecallAt1 | 1.00% |
| ragRecallAt3 | 5.00% |
| ragRecallAt5 | 22.00% |
| ragRecallAt10 | 76.50% |
| requiredHitRateAt5 | 18.00% |
| requiredHitRateAt10 | 76.00% |

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

- Total: 17
- rag: 17
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
