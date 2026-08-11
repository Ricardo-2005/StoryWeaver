# Phase 5 实施记录

核对日期：2026-08-03。

## 读取依据

- `设计稿/StoryWeaver_CODEX_前端设计文档_龙族模板演示版_V1.3.md` 的章节生成交互、Context Preview、Workflow 状态和 Phase 5；
- `设计稿/StoryWeaver_CODEX_后端设计文档_龙族模板演示版_V1.2.md`；
- `backend/docs/api.md`；
- `WorkflowController`、`WorkflowDtos`、`WorkflowPreflight`、`WorkflowContextBuilder`、`WorkflowOrchestrator`、预算和模型配置实现；
- Phase 0—4 前端代码。

根目录、`backend` 和 `frontend` 下仍未发现 `AGENTS.md`。后端仍未提供 OpenAPI/Swagger 文件。

## 已完成

- 章节编辑器“开始工作流”入口和 Preflight Dialog；
- 使用真实 Project、Chapter、Character、Skill Composition、Budget 和 Model Config 数据进行客户端预检；
- 检查项目归档、作者意图、章纲、视角人物、上一章正式版本、Skill 冲突、三类 Token 上限和未提交本地草稿；
- BLOCKER 存在时禁用启动；
- DeepSeek 配置、项目成本余额和用户日成本明确保留给后端最终预检，不伪造通过；
- 使用随机 UUID 构建安全格式 `Idempotency-Key`，仅保存在当前弹窗内存；
- 启动前明确提示实际接口会连续执行完整后端工作流；
- `/projects/:projectId/chapters/:chapterId/workflows/:runId` 状态页；
- 每两秒 REST 轮询，进入终态后停止；
- 工作流取消；
- PREFLIGHT、CONTEXT、PLANNING、WRITING、EXTRACTING、VALIDATING、REVIEWING Stepper；
- Context Packet 总 Token、后端费用值、失效时间和任务预算占用；
- Context Stale/BLOCKED 状态及返回重新预检入口；
- Skill 硬规则类别显示锁定且没有移除操作；
- 真实场景计划、Must Include、Must Avoid 和章尾钩子只读展示；
- 运行态正文只显示存在与字符数，不提前并入 TipTap；
- WAITING_APPROVAL 明确留给后续审批阶段；
- 未连接 `/api/workflows/{runId}/events`。

## 设计稿与实际 API 的差异

- 没有只运行 Preflight 或只构建 Context 的接口；
- Workflow 启动后不会在 Context Preview 或 PLAN_READY 暂停；
- 没有计划编辑、重新规划、接受计划接口；
- Context API 没有逐来源、版本、激活原因、硬规则、可移除或逐来源 Token 字段；
- 无法展示本次 Context 的真实来源明细，只能明确列出后端构建器的来源类别；
- Context Stale 没有重建接口，只能使用新 Idempotency-Key 重新启动工作流；
- Model Config 不返回 DeepSeek 是否已配置，必须由服务端 Preflight 决定；
- 没有按章节查询最近 Workflow 的接口，状态页需要启动响应中的 runId；
- Phase 5 不消费 Workflow SSE，正文流式显示和断线恢复属于 Phase 6。

## 测试覆盖

- 单元测试验证 33,000 Token 投影与客户端 BLOCKER 规则；
- Playwright 验证 BLOCKER 禁止启动；
- Playwright 验证幂等 Header、Context 聚合、Token、只读场景计划、Stepper 和 Stale 处理；
- 测试记录并断言 Phase 5 没有请求 Workflow `/events`。

## 未完成

真实逐来源 Context Preview、逐来源 Token 分配、Context 重建、计划暂停/编辑/接受，需要后端新增契约。Workflow SSE、正文增量、断线重连和停止后的编辑器合并留待 Phase 6。
