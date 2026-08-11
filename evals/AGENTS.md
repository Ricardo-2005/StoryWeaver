# StoryWeaver Agent Evaluation Rules

1. 所有结果必须来自真实执行，禁止随机或手写指标。
2. 禁止删除困难或失败 Case 来提高分数；回归 Case 只能按版本化流程变更。
3. Dataset 必须声明版本、来源与 Ground Truth 状态。
4. 默认只运行 DETERMINISTIC Offline Evaluation，绝不默认调用 DeepSeek。
5. Live Evaluation 必须同时经过 `STORYWEAVER_EVAL_LIVE=true` 门禁；不得打印 Secret。
6. Fixture 只能使用原创或仓库技术演示数据，不得使用真实用户小说。
7. 未运行的指标必须是 JSON `null`，不能写成 0 或 PASS。
8. Report 必须记录 Git Commit；仓库无 Git 元数据时如实记录 `null`。
9. 修改 Scoring 规则必须同步更新 Harness 测试、Dataset 文档和 Changelog。
10. Evaluation 只能调用或适配生产逻辑，不得为刷分修改 Production Logic。
