# Phase 6 实施记录

核对日期：2026-08-03。

## 读取依据

- `设计稿/StoryWeaver_CODEX_前端设计文档_龙族模板演示版_V1.3.md` 的生成期间编辑、Writer 流式状态、SSE 客户端设计、SSE 安全和 Phase 6；
- `设计稿/StoryWeaver_CODEX_后端设计文档_龙族模板演示版_V1.2.md`；
- `backend/docs/api.md`；
- 实际 `WorkflowController`、`WorkflowDtos.WorkflowEventResponse`、`WorkflowService`、`WorkflowStore`、`WorkflowOrchestrator`、`WorkflowWriterService`、`WorkflowEvent` 与 V6 数据库迁移；
- Phase 0—5 前端代码。

根目录、`backend` 和 `frontend` 下仍未发现 `AGENTS.md`。后端仍未提供 OpenAPI/Swagger 文件。

## 已完成

- 中央 `api` 目录中的 Workflow SSE 请求和增量解析器；
- SSE 复用内存 Bearer JWT、统一 Problem Details 和 401 会话失效处理；
- 长期 Token 不进入 URL、LocalStorage 或日志；
- 支持跨网络 chunk、CRLF、多行 `data`、注释和无效 JSON 容错；
- 使用真实 `eventId` 去重，重连通过 `Last-Event-ID` Header 请求缺失事件；
- 断流后先通过 TanStack Query 刷新 Workflow，再以最多 5 次指数退避重连；
- Pinia 保存连接状态、运行态正文、事件游标、心跳、用量和警告；
- `text.delta` 以 80ms Buffer 批量刷新，不逐 Token 修改 TipTap；
- 后端 Writer 恢复时识别 `warning/workflow_recovered`，清空旧的不完整正文后接收新一轮输出，避免拼接两次生成；
- 运行态正文面板、流式光标、字符数、完成后的 completion tokens、连接状态和心跳状态；
- 用户向上阅读时停止自动滚底，并提供“返回生成位置”；
- “停止生成”调用真实 `POST /api/workflows/{runId}/cancel`，停止后关闭事件流并以取消响应中的正文为准；
- 断流提示、保留字数和手动重新连接；
- “接管编辑”先停止活跃工作流，再写入既有章节 IndexedDB 草稿键；覆盖已有本地草稿前要求确认；
- 页面刷新后通过 Workflow REST 状态和事件历史恢复，不创建新的 Workflow；
- 终态以 REST `draftContent` 校正正文，SSE 不直接覆盖章节正式版本。

## 对接的实际接口与事件

- `GET /api/workflows/{runId}/events`：`text/event-stream`，支持 `Last-Event-ID` Header 和 `afterEventId` Query；前端只使用 Header；
- `GET /api/workflows/{runId}`：断流后确认终态并校正完整运行态正文；
- `POST /api/workflows/{runId}/cancel`：停止生成；
- 实际持久事件：`workflow.created`、`workflow.step`、`text.delta`、`usage.partial`、`text.completed`、`warning`、`workflow.cancelled`、`workflow.error`、`workflow.completed`；
- 非持久 SSE 心跳：`heartbeat`，数据只有 `runId/timestamp`，没有 `eventId`。

## 设计稿与实际 API 的差异

- 设计稿 Phase 6 写 `EventSource`，但实际后端只接受登录返回的 Bearer JWT，且没有同站鉴权 Cookie或短期 SSE Ticket；原生 EventSource 无法设置 Authorization Header，因此实际实现使用带 Header 的流式 `fetch`。
- 设计稿建议按 `sequence` 去重，实际 `text.delta` payload 只有 `{ text }`；前端按数据库 `BIGSERIAL eventId` 去重。
- 设计稿列出 `analysis.completed` 和 `review.completed`，实际后端没有发出这两个事件；Extractor/Reviewer 进度通过 `workflow.step` 表示。
- `heartbeat` 不是 `WorkflowEventResponse`，没有事件 ID；前端单独解析，且不推进重放游标。
- 后端没有暂停/继续、放弃运行态草稿、只重启 Writer 或按章节查询最近 Workflow 的接口。
- `usage.partial` 实际在 Writer 完成后一次性发出，而非生成过程中持续更新；Token 只会在该事件到达后展示。
- 后端恢复 Worker 在 WRITING 状态会清空服务端正文，但没有专门的 `text.reset` 事件；前端根据 `warning` 的 `workflow_recovered` code 和 WRITING step 执行同等清空。
- 设计稿偏好 HttpOnly Cookie，实际仍是仅内存 Access Token，刷新页面需要重新登录；登录后重新进入带 runId 的 URL 可恢复 Workflow。

## 测试覆盖

- Vitest：SSE 跨 chunk/CRLF/多行 data、无效消息、Bearer Header、Last-Event-ID、URL 无 Token；
- Vitest：80ms Buffer、重复 eventId 去重、Writer 恢复清空、终态 REST 正文校正；
- Playwright：首次流式正文、断流重连、续传 Header、重复事件、终态恢复；
- Playwright：停止生成调用 cancel，关闭后不再重连或追加正文；
- 原 Phase 0—5 测试继续全量回归。

## 未完成

Phase 7 的审查问题处置、候选事实选择、修订/重新提取、审批与原子提交未提前实现。后端缺失的暂停/继续、短期 SSE Ticket、只重启 Writer、运行态草稿删除和最近 Workflow 查询也未虚构。
