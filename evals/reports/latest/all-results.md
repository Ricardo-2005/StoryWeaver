# StoryWeaver Evaluation - All Results

- Generated: `2026-08-12T08:28:47.9268288Z`
- DeepSeek calls: `0`; Live values remain `null`.
- Frozen baseline advanced ranking metrics come from the deterministic replay of the immutable v1 inputs.

| Scope | Dataset | Configuration | Strategy | Pool | Final K | RAG Cases | R@1 | R@3 | R@5 | R@10 | AllReq@5 | AllReq@10 | MRR | NDCG@5 | NDCG@10 | First Req Mean | Median | P95 | Token Reduction | Preservation | RAG Failures | Consistency | Workflow Stub | MCP | Live | Failure Distribution |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| FROZEN_BASELINE_V1 | v1 | BASELINE | BASELINE | 10 | unbounded | 50 | 1.00% | 5.00% | 22.00% | 76.50% | 18.00% | 76.00% | 0.1899 | 0.1232 | 0.3247 | 7.8200 | 8.0000 | 12.0000 | 78.42% | 100.00% | 17 | 100.00% | 100.00% | 100.00% | null | `{"CONSTANT_RULE_CROWDING":12,"TRUE_RETRIEVAL_MISS":5}` |
| V1_EXPERIMENT | v1 | CONSTANT_ISOLATED | CONSTANT_ISOLATED | 10 | unbounded | 50 | 12.00% | 32.00% | 58.50% | 95.50% | 60.00% | 100.00% | 0.4304 | 0.4052 | 0.5414 | 4.9800 | 5.0000 | 9.0000 | 78.42% | 100.00% | 5 | 100.00% | 100.00% | 100.00% | null | `{"TRUE_RETRIEVAL_MISS":5}` |
| V1_EXPERIMENT | v1 | KEYWORD_ONLY | KEYWORD_ONLY | 10 | unbounded | 50 | 44.00% | 67.00% | 69.00% | 69.00% | 80.00% | 80.00% | 0.7717 | 0.6643 | 0.6643 | 1.4500 | 1.0000 | 3.0000 | 91.81% | 80.00% | 27 | 100.00% | 100.00% | 100.00% | null | `{"KEYWORD_MISMATCH":23,"MULTI_ASSET_MISSING":1,"TRUE_RETRIEVAL_MISS":3}` |
| V1_EXPERIMENT | v1 | VECTOR_ONLY | VECTOR_ONLY | 10 | unbounded | 50 | 63.50% | 88.00% | 93.00% | 95.50% | 100.00% | 100.00% | 1.0000 | 0.9255 | 0.9362 | 1.0400 | 1.0000 | 1.0000 | 78.59% | 100.00% | 5 | 100.00% | 100.00% | 100.00% | null | `{"TRUE_RETRIEVAL_MISS":5}` |
| V1_EXPERIMENT | v1 | HYBRID_FUSION | HYBRID_FUSION | 10 | unbounded | 50 | 49.00% | 87.00% | 93.00% | 95.50% | 100.00% | 100.00% | 0.8700 | 0.8484 | 0.8591 | 1.5000 | 1.0000 | 3.0000 | 78.42% | 100.00% | 5 | 100.00% | 100.00% | 100.00% | null | `{"TRUE_RETRIEVAL_MISS":5}` |
| V1_EXPERIMENT | v1 | HYBRID_POOL_30 | HYBRID_FUSION | 30 | unbounded | 50 | 49.00% | 87.00% | 92.00% | 95.50% | 98.00% | 100.00% | 0.8667 | 0.8420 | 0.8571 | 1.5400 | 1.0000 | 3.0000 | 73.89% | 100.00% | 3 | 100.00% | 100.00% | 100.00% | null | `{"TOKEN_BUDGET_TRUNCATION":2,"TRUE_RETRIEVAL_MISS":1}` |
| V1_EXPERIMENT | v1 | HYBRID_POOL_30_FINAL_10 | HYBRID_FUSION | 30 | 10 | 50 | 49.00% | 87.00% | 92.00% | 95.50% | 98.00% | 100.00% | 0.8667 | 0.8420 | 0.8571 | 1.5400 | 1.0000 | 3.0000 | 78.19% | 100.00% | 5 | 100.00% | 100.00% | 100.00% | null | `{"KEYWORD_MISMATCH":4,"TRUE_RETRIEVAL_MISS":1}` |
| CURRENT_FULL | v1 | SELECTED_LATEST | VECTOR_ONLY | 10 | unbounded | 50 | 63.50% | 88.00% | 93.00% | 95.50% | 100.00% | 100.00% | 1.0000 | 0.9255 | 0.9362 | 1.0400 | 1.0000 | 1.0000 | 78.59% | 100.00% | 5 | 100.00% | 100.00% | 100.00% | null | `{"TRUE_RETRIEVAL_MISS":5}` |
| HOLDOUT_FIRST_RUN | rag-holdout-v1 | HOLDOUT_V1 | VECTOR_ONLY | 10 | unbounded | 24 | 48.96% | 84.03% | 94.10% | 98.61% | 91.67% | 100.00% | 0.9792 | 0.9171 | 0.9398 | 1.1250 | 1.0000 | 2.0000 | 72.89% | 100.00% | 1 | - | - | - | null | `{"OTHER":1}` |

## Report Paths

- `BASELINE`: `D:\实习\StoryWeaver\evals\reports\baseline-v1\summary.md`
- `CONSTANT_ISOLATED`: `D:\实习\StoryWeaver\evals\reports\experiments\latest\constant_isolated\summary.md`
- `KEYWORD_ONLY`: `D:\实习\StoryWeaver\evals\reports\experiments\latest\keyword_only\summary.md`
- `VECTOR_ONLY`: `D:\实习\StoryWeaver\evals\reports\experiments\latest\vector_only\summary.md`
- `HYBRID_FUSION`: `D:\实习\StoryWeaver\evals\reports\experiments\latest\hybrid_fusion\summary.md`
- `HYBRID_POOL_30`: `D:\实习\StoryWeaver\evals\reports\experiments\latest\hybrid_pool_30\summary.md`
- `HYBRID_POOL_30_FINAL_10`: `D:\实习\StoryWeaver\evals\reports\experiments\latest\hybrid_pool_30_final_10\summary.md`
- `SELECTED_LATEST`: `D:\实习\StoryWeaver\evals\reports\latest\summary.md`
- `HOLDOUT_V1`: `D:\实习\StoryWeaver\evals\reports\holdout-v1\summary.md`
