# StoryWeaver RAG Experiment Matrix

- Dataset: `v1`
- DeepSeek calls: `0`
- Candidate generation: production `WorldbookService.previewWithOptions`

| Strategy | R@5 | R@10 | AllReq@5 | AllReq@10 | MRR | NDCG@5 | NDCG@10 | Token reduction | Preservation | Failures |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| BASELINE | 22.00% | 76.50% | 18.00% | 76.00% | 0.1899 | 0.1232 | 0.3247 | 78.42% | 100.0% | 17 |
| CONSTANT_ISOLATED | 58.50% | 95.50% | 60.0% | 100.0% | 0.4304 | 0.4052 | 0.5414 | 78.42% | 100.0% | 5 |
| KEYWORD_ONLY | 69.00% | 69.00% | 80.0% | 80.0% | 0.7717 | 0.6643 | 0.6643 | 91.81% | 80.0% | 27 |
| VECTOR_ONLY | 93.00% | 95.50% | 100.0% | 100.0% | 1.0 | 0.9255 | 0.9362 | 78.59% | 100.0% | 5 |
| HYBRID_FUSION | 93.00% | 95.50% | 100.0% | 100.0% | 0.87 | 0.8484 | 0.8591 | 78.42% | 100.0% | 5 |
| HYBRID_POOL_30 | 92.00% | 95.50% | 98.00% | 100.0% | 0.8667 | 0.8420 | 0.8571 | 73.89% | 100.0% | 3 |
| HYBRID_POOL_30_FINAL_10 | 92.00% | 95.50% | 98.00% | 100.0% | 0.8667 | 0.8420 | 0.8571 | 78.19% | 100.0% | 5 |
