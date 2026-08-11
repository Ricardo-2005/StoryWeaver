# Phase 8 可复现评测

本目录只保存可复现数据、执行说明和真实运行结果，不包含伪造的模型成绩。

## 数据集

- `datasets/conflicts.jsonl`：120 组《龙族》主题确定性冲突，每组同时包含一个冲突输入和一个安全对照；分布为人物状态 40、唯一道具 20、时间线 30、角色知识 30。
- `datasets/dragon-template-scenarios.json`：人物双地点、完整七宗罪剑匣双持有人、青铜城因果倒置、楚子航知识越界和普通人物无依据识别龙文/使用言灵五类演示场景。
- `datasets/demo-manifest.json`：20 个原创测试章、6 个指定角色、60 个世界书条目、150 个故事事件的确定性龙族模板清单，也是上下文 Baseline 的输入规模。

冲突集用于验证当前 Java 确定性 Validator，不代表开放域自然语言或 LLM 泛化能力。Precision、Recall 和 F1 只对该固定规则集有效。

人物和设定名称只用于非商业技术演示与功能测试；清单中的章号、剧情、正文和摘要均为原创产品 Fixture，不是《龙族》原著真实章节，也不要求模型模仿作者文风。

## 执行

在 `backend` 目录运行：

```powershell
.\scripts\run-phase8-evaluation.ps1
```

完整回归：

```powershell
.\mvnw.cmd clean verify
```

测试会把本机原始结果写入 `target/phase8-results/phase8-results.json`。仓库中的 `results/phase8-results.json` 是最近一次已验证快照，包含数据集 SHA-256、运行环境和指标口径。

## 指标口径

- 冲突评测：每组冲突输入为正样本、安全输入为负样本，共 240 次预测。
- 证据定位：命中的 BLOCKER 必须在 `evidence` 或 `historicalEvidence` 中包含数据集的证据标记。
- Context Baseline：Baseline A 注入前 15 章全文；StoryWeaver B 注入上一章摘要、动态世界书、相关事件、正典状态和 Skill。
- 性能：在单 JVM 预热后统计 Validator 单次调用与 Context 组装/估算的 P50/P95；这是本地微基准，不等同于生产并发容量。
