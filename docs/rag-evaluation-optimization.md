# StoryWeaver RAG 评测驱动优化

## 结论

本轮在不调用 DeepSeek、不使用 LLM reranker、不修改 v1 Ground Truth 的前提下，先冻结真实基线，再对生产 `WorldbookService` 的候选生成与排序进行可切换实验。最终选择 `VECTOR_ONLY`（exact-cosine 候选池 10、无额外 final 截断）作为生产默认配置。

选择依据不是单看 Recall@10：该配置在 v1 上同时取得 Recall@5 93.00%、All-Required Hit@5/10 100.00%、MRR 1.0000、NDCG@10 0.9362、上下文保真 100.00%，Token Reduction 78.59%。RRF 已真实执行，但同候选池下 MRR、NDCG 和 Token 均未超过 `VECTOR_ONLY`。

## 冻结基线

`baseline-v1` 在任何检索代码修改前复跑并冻结。运行输入与报告由 `evals/scripts/verify-frozen-assets.ps1` 在每次评测前校验 SHA-256，哈希不一致会直接失败。

| 资产 | SHA-256 |
| --- | --- |
| `evals/datasets/rag/retrieval_cases.jsonl` | `CF5969C299F21F437CEBF28A4E3087175E672EB41635239C41521D11E60ADF21` |
| `evals/fixtures/worldbook/eval-project-v1.json` | `37E856BD348BC1E4FB5113669D350E74EA80630C180A8237C953EB49991B9DF7` |
| `evals/reports/baseline-v1/summary.json` | `0DEF8250BF155989CF5955320FEA11553E56B5478613AEDA4635E7F17F7CD128` |
| `evals/reports/baseline-v1/raw/failures.json` | `785216F8E54849EF807410B8A363C205EFA213F030B6F661890658269B91C883` |

冻结基线为 50 条 RAG、50 条 Token、100 条 Consistency、14 条 Workflow Stub、18 条 MCP：

| 指标 | Baseline v1 |
| --- | ---: |
| Recall@1 / @3 / @5 / @10 | 1.00% / 5.00% / 22.00% / 76.50% |
| All-Required Hit@5 / @10 | 18.00% / 76.00% |
| MRR | 0.1899 |
| binary NDCG@5 / @10 | 0.1232 / 0.3247 |
| 首个 Required Rank（mean / median / P95） | 7.82 / 8.00 / 12.00 |
| Token Reduction / Context Preservation | 78.42% / 100.00% |
| 严格失败 | 17 |

原始冻结产物位于 `evals/reports/baseline-v1/`，清单位于 `evals/baselines/baseline-v1.json`。

## 根因分析

分类器只使用可观察行为，不引用 case ID。17 条基线失败中：

- `CONSTANT_RULE_CROWDING`: 12（70.59%）。三个优先级 100/99/98 的常量规则固定占据前三位，相关资产被推到 Top K 之外。
- `TRUE_RETRIEVAL_MISS`: 5（29.41%）。目标的次要相关资产没有进入 vector top-10 与关键词候选并集。

Trace 为每条 case 保存 query、expected/required、rank/id/type/source、constant、keyword/vector/final score、token estimate、候选池/最终排名、首个 Required Rank、All-Required 与失败原因。完整失败枚举还包括多资产缺失、语义/关键词不匹配、重复、错误资产类型、scope 过滤、token 截断、低排名与其他。

## 实验设计与结果

候选生成继续调用生产 `WorldbookService.previewWithOptions` 与仓库 ONNX `BAAI/bge-small-zh-v1.5`；向量仓库为确定性的 exact cosine，而不是 pgvector ANN。常量资产在非 Baseline 模式中保留到最终上下文，但不挤占普通查询相关排名；若常量自身被关键词明确命中，仍可进入查询排名。

为避免多变量混杂，矩阵先固定 pool=10、无 final 截断逐一切换模式，再单独扩大 Hybrid pool，最后单独增加 final=10：

| 实验 | R@5 | R@10 | AllReq@5 | AllReq@10 | MRR | NDCG@5 | NDCG@10 | Token Reduction | Preservation | 失败 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| BASELINE | 22.00% | 76.50% | 18.00% | 76.00% | 0.1899 | 0.1232 | 0.3247 | 78.42% | 100% | 17 |
| CONSTANT_ISOLATED | 58.50% | 95.50% | 60.00% | 100.00% | 0.4304 | 0.4052 | 0.5414 | 78.42% | 100% | 5 |
| KEYWORD_ONLY | 69.00% | 69.00% | 80.00% | 80.00% | 0.7717 | 0.6643 | 0.6643 | 91.81% | 80% | 27 |
| VECTOR_ONLY | 93.00% | 95.50% | 100.00% | 100.00% | 1.0000 | 0.9255 | 0.9362 | 78.59% | 100% | 5 |
| HYBRID_FUSION (RRF) | 93.00% | 95.50% | 100.00% | 100.00% | 0.8700 | 0.8484 | 0.8591 | 78.42% | 100% | 5 |
| HYBRID_POOL_30 | 92.00% | 95.50% | 98.00% | 100.00% | 0.8667 | 0.8420 | 0.8571 | 73.89% | 100% | 3 |
| HYBRID_POOL_30_FINAL_10 | 92.00% | 95.50% | 98.00% | 100.00% | 0.8667 | 0.8420 | 0.8571 | 78.19% | 100% | 5 |

机器可读矩阵与各组原始 case trace 位于 `evals/reports/experiments/latest/`。

`HYBRID_POOL_30` 的严格失败最少，但 All-Required@5 降至 98%、MRR/NDCG 更低，Token Reduction 下降 4.70 个百分点；因此没有仅按失败数或 Recall@10 选择它。

## Holdout

在查看结果前冻结了 `rag-holdout-v1`：24 条 case、44 个资产，覆盖同名消歧、状态变化、跨章节、时间线、否定事实、知识边界、相似地点/物品、新旧状态、多 Required、读者与人物知识、物品所有权和 StoryFact 依赖。Manifest 固定了策略、case/fixture 哈希与覆盖项。

选定的 `VECTOR_ONLY` 只运行一次：

| 指标 | Holdout |
| --- | ---: |
| Recall@1 / @3 / @5 / @10 | 48.96% / 84.03% / 94.10% / 98.61% |
| All-Required Hit@5 / @10 | 91.67% / 100.00% |
| MRR / NDCG@5 / NDCG@10 | 0.9792 / 0.9171 / 0.9398 |
| 首个 Required Rank（mean / median / P95） | 1.125 / 1.0 / 2.0 |
| Token Reduction / Context Preservation | 72.89% / 100.00% |
| 严格失败 | 1 / 24 |

唯一严格失败 `holdout-011` 的两个 Required 均排名 1、2；失败来自一个非 Required 的相关常量规则已在最终上下文中，但按常量隔离定义不计入查询排名。该 case 与原始结果保留，不据此调参。

## 生产配置与复现

生产默认值位于 `backend/src/main/resources/application.yml`：

```yaml
worldbook-mode: VECTOR_ONLY
worldbook-candidate-pool-size: 10
worldbook-final-ranking-size: 2147483647
worldbook-rrf-rank-constant: 60
```

Windows 双击 `evals/run-evals.cmd` 会校验冻结哈希、运行 Offline 全量评测并更新 latest；不会调用 DeepSeek。

```bat
evals\run-evals.cmd
evals\run-evals.cmd baseline
evals\run-evals.cmd experiments
```

## 真实性边界与下一步

- 所有 Live 指标保持 `null`，本轮 DeepSeek 调用数为 0。
- Token 使用生产 `TokenEstimator` 的估算值，不是供应商账单 Token。
- exact cosine 结果不代表 pgvector ANN 的召回、延迟或并发性能。
- v1 剩余 5 个严格失败都已命中 Required，但缺少次要 relevant asset；下一步应单独研究 candidate recall（例如受控扩大 vector pool 或资产类型分层），不能修改 v1 题目或把常量重新塞回排名。
