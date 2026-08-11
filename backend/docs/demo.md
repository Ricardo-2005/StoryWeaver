# Phase 8《龙族》主题三分钟后端 Demo

Demo 分为可重复的确定性证据模式和可选的真实 DeepSeek 模式。角色和设定名称只用于非商业技术演示与功能测试；所有演示章号、剧情、正文和摘要均为原创产品 Fixture，不是《龙族》原著真实章节，也不要求模型模仿作者文风。

## 模板内容

- 人物：路明非、楚子航、诺诺、恺撒、昂热、芬格尔；
- 地点/组织：卡塞尔学院、三峡、青铜城、秘党、学生会、狮心会；
- 世界设定：混血种、龙族、言灵、龙文、炼金术、血统、七宗罪；
- 剧情：卡塞尔学院执行青铜城调查任务，行动成员在进入青铜城过程中核对人物知识、炼金武器归属和事件时间线。

## 准备

```powershell
Copy-Item .env.example .env
docker compose up -d --build
.\scripts\seed-phase8-demo.ps1
```

种子脚本只调用公开 JWT API，创建独立项目：20 个原创短测试章及正式版本、6 个角色、60 个世界书条目和 150 个故事事件。执行回执写入 `target/phase8-demo-seed.json`，不保存 Token 或密码。

默认重跑会先归档当前 Demo 账号下 `description` 与清单专用标记完全一致的旧模板项目，再创建新项目；不会碰其他用户或无标记项目。由于现有正式 API 没有物理删除项目路径，安全移除沿用现有 `archived` 语义；`-KeepExistingDemo` 可以跳过归档。

## 00:00–03:00

1. `00:00`：运行 `.\scripts\demo-phase8.ps1`，展示青铜城 Demo 项目规模及版权声明。
2. `00:20`：读取人物状态和 150 条事件，展示卡塞尔学院、三峡与青铜城之间的状态和知情人范围。
3. `00:40`：调用世界书 Preview，展示青铜城水下结构、龙文机关、炼金武器限制、混血种血统规则和角色可见信息的 CONSTANT/KEYWORD/VECTOR 激活、Token 裁剪及降级原因。
4. `01:00`：展示固定 120 组冲突的 Precision/Recall/F1、BLOCKER 漏检和证据定位结果。
5. `01:15`：展示 Baseline A 与动态上下文 B 的输入 Token 对照。
6. `01:30`：默认跳过真实模型；显式运行 `.\scripts\demo-phase8.ps1 -StartLiveWorkflow` 才调用 DeepSeek。
7. `02:20`：实时模式展示 Workflow、Context Token、Reviewer 问题与 SSE；工作流停在 `WAITING_APPROVAL`，不会自动提交。
8. `02:35`：查询 Usage/Cost；未配置 PricingRule 的请求明确标记为 unpriced。
9. `02:50`：通过 MCP 调用 `get_item_owner(seven-sins-sword-case)`；未经审批建立归属时结果应为空，不能伪造“完整七宗罪剑匣属于路明非”。
10. `03:00`：打开 Grafana，展示 LLM、Workflow、Review、Cost 与 Tempo Trace 面板。

## 一致性场景

- 路明非在同一故事时间同时出现在卡塞尔学院与三峡任务现场；
- 路明非与楚子航同时被记为完整七宗罪剑匣持有人；
- 进入青铜城的结果早于发现水下入口；
- 楚子航在没有证据和传播事件时提前确认青铜与火之王身份；
- 普通人物没有血统或训练依据却直接识别龙文并使用言灵。

## 可复现验收

```powershell
.\scripts\run-phase8-evaluation.ps1 -Clean
.\mvnw.cmd clean verify
```

`Phase8DemoIT` 使用确定性 Agent Stub 连续完成 10 个原创测试章，并断言 10 个 Workflow 和 10 个 ChapterVersion 全部提交、没有半提交章节。真实 DeepSeek Demo 受模型输出、网络、额度和 PricingRule 配置影响，不计入固定成绩。
