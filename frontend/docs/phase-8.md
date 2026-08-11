# Phase 8 实施记录

核对日期：2026-08-03。

## 读取依据

- `设计稿/StoryWeaver_CODEX_前端设计文档_龙族模板演示版_V1.3.md` 的项目 Dashboard、模型与费用、API Client、测试策略和 Phase 8；
- `设计稿/StoryWeaver_CODEX_后端设计文档_龙族模板演示版_V1.2.md`；
- `backend/docs/api.md`；
- 实际 `UsageController`、`UsageService`、`BudgetService`、`PricingService`；
- `UsageRecord`、`ProjectBudget`、`PricingRule`、模型配置 DTO 和 V4.2/V8 数据库迁移；
- Workflow 时间戳、Problem Details 与前端 Phase 0—7 实现。

根目录、`backend` 和 `frontend` 下仍未发现 `AGENTS.md`。后端仍未提供 OpenAPI/Swagger 文件。

## 已完成

- 新增路由级懒加载的“模型与费用”项目页面和左侧导航入口；
- 接入项目 Usage 明细、成本汇总、预算读写、Pricing Rules 和实际 Model Config；
- ECharts 6 图表：每日输入/输出/推理 Token、每日实际费用、Cache Hit/Miss、Agent 平均 LLM 请求耗时；
- 每张图提供可读文字摘要和数据表或定义列表替代；
- 总 Token、请求成功/失败、Cache 命中率、平均耗时、P95 和实际费用 KPI；
- 未计价请求单独告警，不把 null 成本伪装为零成本；
- 仅用 `actualCost` 绘制实际费用，保留后端 `estimatedCost` 口径；
- Agent 与模型维度聚合和模型用量对比表；
- 模型能力矩阵：Thinking、Reasoning Effort、输出上限、流式、重试和后端返回的不支持参数；
- 不支持参数置灰并显示删除线，不由前端自行推断；
- 后端 Pricing Rule 版本和五类每百万 Token 价格表；
- 项目预算进度、单工作流/每日/项目/Writer/Planner 上限；
- 使用 `expectedVersion` 更新预算，不做乐观更新；
- 预算输入基础校验，并提示 Writer/Planner 上限低于实际模型合同时会导致后端预检失败；
- 项目实际成本达到上限时显示耗尽告警；
- 写前预检新增真实 Project Cost 检查，达到上限时禁用启动；跨项目用户日成本仍交给后端最终强制校验；
- 当前 Workflow 页面按后端 `startedAt/finishedAt` 显示端到端耗时；
- Usage 请求表展示 Model Provider requestId，并明确它不是应用 Trace ID；
- ProblemAlert 和 ErrorState 只在 Problem Details 或 `X-Trace-ID` 实际返回时展示 Trace ID；
- Usage 表最多渲染最近 100 条，但所有后端返回记录仍参与聚合。

## 对接的实际接口

- `GET /api/projects/{projectId}/usage`；
- `GET /api/projects/{projectId}/costs`；
- `GET /api/projects/{projectId}/budget`；
- `PUT /api/projects/{projectId}/budget`；
- `GET /api/pricing-rules`；
- `GET /api/ai/model-config`；
- `GET /api/workflows/{runId}` 的 startedAt/finishedAt；
- Problem Details body 和可选 `X-Trace-ID` Header。

## 设计稿与实际 API 的差异

- Usage 明细没有 `workflowRunId/chapterId`，无法计算章节费用、单次 Workflow 的 LLM 总费用或 Workflow 分步耗时；页面只展示请求级用量，当前 Workflow 的端到端耗时单独来自 WorkflowResponse。
- 没有 Workflow 列表或耗时统计接口，不能绘制项目级“工作流耗时趋势”。当前图明确标为 Agent LLM 请求耗时。
- CostSummary 没有 currency，且服务端会直接汇总所有记录数值；前端从 Usage 明细推导货币标签，发现多币种时明确提示且不自行换算。
- Usage 接口没有分页、时间范围、Agent/模型过滤；页面使用全部返回数据聚合，图表显示最近最多 30 个有请求的 UTC 日期，明细最多渲染 100 条。
- 没有“当前用户今日费用”接口，只有跨项目每日上限配置；该限制仍由后端 Workflow Preflight 执行。
- Model Config 没有超时字段，也没有 DeepSeek Key 是否已配置字段；页面不虚构。
- Budget 没有提醒阈值或高推理确认字段，只有五个硬限制。
- Usage `requestId` 是模型提供方请求 ID，不是 Trace ID。后端没有 Trace 查询接口；应用 Trace ID 只可能出现在错误响应。
- 没有章节费用、Cache 金额节省或预算历史接口，前端不进行估算。
- Pricing Rule 只能读取，不能通过业务 API 修改。

## 测试覆盖

- Vitest：Token、Cache 命中率、平均/P95 耗时、实际成本、未计价请求、UTC 日期/Agent/模型聚合、货币和格式化；
- Vitest：项目费用达到预算时 Preflight 产生 BLOCKER；
- Playwright：四张 ECharts Canvas、文字信息、模型能力、不支持参数和请求 ID 边界；
- Playwright：带 Bearer Token 和 expectedVersion 的预算 PUT；
- Playwright：项目费用耗尽后启动工作流按钮禁用；
- Phase 0—7 单元和 E2E 全量回归。

## 未完成

Phase 9 的视觉回归、完整无障碍审计与 Demo 数据编排未提前实现。后端缺失的 Workflow/Chapter 用量关联、Trace 查询、分页过滤、用户今日费用、预算历史和提醒阈值未虚构。
