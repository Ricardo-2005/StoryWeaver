# Phase 3 实施记录

核对日期：2026-08-03。

## 读取依据

- `设计稿/StoryWeaver_CODEX_前端设计文档_龙族模板演示版_V1.3.md`，重点为 1.7、8、12、13、14、26.3 和 Phase 3；
- `设计稿/StoryWeaver_CODEX_后端设计文档_龙族模板演示版_V1.2.md`；
- `backend/docs/api.md`；
- 后端实际 Controller、DTO、鉴权配置和数据库迁移；
- Phase 0—2 已有前端代码。

仓库根目录、`backend` 和新建的 `frontend` 中均未发现 `AGENTS.md`。后端没有可用于类型生成的 OpenAPI/Swagger 文件，因此继续以实际 Java DTO 建立最小 TypeScript 类型。

## 已完成

- `/projects/:projectId/workspace` 创作工作台；
- 左侧既有项目导航、中间工作区、右侧 Canvas 的桌面布局；
- 小屏 Canvas 全屏覆盖，中间工作区可恢复；
- Chat 空状态与真实资产快捷入口，不创建虚假消息；
- 固定底部 Composer：文本输入、人物/世界书/大纲/一致性审查工具菜单、聊天/规划/写作/审查/只读查询模式；
- 明确禁用发送并展示后端能力边界；
- 本地可编辑 Writing Block，可在 Canvas 打开；
- 正典资产 Canvas 读取真实资产，支持修改名称、内容、变更说明并通过 `PUT /api/assets/{assetId}` 保存新版本；
- Canvas 文本选区引用到 Composer 上下文 Chip，并可移除；
- Loading、Error 与无资产状态复用统一组件；
- Pinia 管理 Composer、Canvas、选区与仅内存 Writing Block；TanStack Query 管理正典资产服务器状态。

## 设计稿与实际 API 的差异

设计稿 Phase 3 要求 Conversation、Message、Chat SSE、会话搜索、归档、重试和续写，但实际后端没有这些接口或表。本实现因此：

- 不调用不存在的接口；
- 不使用 `/ai/writer` 或 Workflow SSE 代替 Chat SSE；
- 不模拟 AI 回复、流式输出或延迟；
- Composer 保留输入与交互外壳，但发送保持禁用；
- Writing Block 明确标为仅内存临时内容，不声称已持久化；
- Canvas 只对正典资产执行真实保存；没有后端支持的 Diff、历史、评论或 AI 候选不予伪造。

## 验收覆盖

- 单元测试覆盖 Writing Block 创建/编辑、Canvas 切换、选区与关闭清理；
- Playwright 覆盖登录进入工作台、本地 Writing Block、真实正典 Canvas 编辑与保存、上下文引用，并断言没有 Chat/Conversation/Message 请求；
- 全量 lint、类型检查、单元测试、构建与 E2E 结果以最终交付报告为准。

## 后端就绪后才能完成

- 新建、读取、搜索、固定和归档会话；
- 用户/助手消息持久化；
- Chat SSE、停止、断线恢复、去重；
- 消息重试和继续生成；
- Writing Block 持久化及转换为正式资产；
- Chat 与 Canvas 的 AI 候选、Diff 接受/拒绝和双向服务端引用。
