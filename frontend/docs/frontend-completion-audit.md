# StoryWeaver 前端完成度审计

审计日期：2026-08-09。结论基于前端设计 V1.3/V1.5、Skill 更新文档、实际 Controller/DTO/V15 迁移和当前前端源码。

## 结论

**按真实后端已经提供的 API，Phase 0—9 前端已完成并具备自动化验收。**

**按前端设计 V1.3 的全部愿景，前端不能称为 100% 完成。** 未完成部分均需要当前不存在的后端业务契约，或属于设计稿 Roadmap；前端没有用本地假数据、假回复或虚构接口掩盖这些缺口。

## Phase 状态

| Phase | 状态 | 已交付 |
|---|---|---|
| 0 | 完成 | Vue/TS/Vite、Router、Pinia、Query、Element Plus、主题、Docker/Nginx、CI |
| 1 | 完成 | Auth、项目、快照、正典资产、App Shell、统一错误 |
| 2 | 完成（按 API） | 人物/状态、世界书、大纲、章节骨架、Skill CRUD 与乐观锁 |
| 3 | 部分产品能力被阻塞 | Composer、Writing Block、Canvas 和选区引用完成；持久会话与 Chat 不可实现 |
| 4 | 完成（按 API） | TipTap、ParagraphKey、IndexedDB 草稿、查找替换、正式版本与恢复 |
| 5 | 完成（按 API） | Preflight、Context、Token 预算、计划、状态页和轮询 |
| 6 | 完成（按 API） | Workflow SSE、去重、Buffer、续传、重连、心跳和停止 |
| 7 | 完成（按 API） | ReviewMark、证据、修订重提取、候选事实、审批和原子提交 |
| 8 | 完成（按 API） | Usage、Token、Cache、费用、耗时、模型能力、预算和图表 |
| 9 | 完成 | axe、键盘、视觉回归、性能预算、Demo、README 和审计 |

Phase 之后的产品更新同样已完成：创建项目选项式向导、项目外全局 Skill 工坊、TXT/手写混合熔炼、28 套动态模板、题材注入、不覆盖用户修改、10 MiB TXT 限制，以及 `USER/ADMIN` 用户类型契约。管理员业务后台仍未实现，不能因已有角色字段而宣称完成。

## 被后端契约阻塞的设计能力

### 会话和 Chat

- Conversation/Message 持久化；
- 新建、固定、归档、搜索和恢复会话；
- Chat SSE、重试、继续和假回复替代方案；
- Chat 与 Canvas 的完整双向消息引用和 Chat 内联 Diff。

### 资产高级能力

- 字段级 AI 候选和候选接受接口；
- 人物自定义字段、Mentions 和 Progressions；
- 通用资产/字段历史查询；
- 世界树层级移动、复制、归档、拖动排序和激活调试查询；
- 大纲拆分、合并、移动、批量重排和历史接口。

### 工作流与可观测性增强

- Context 逐来源完整明细和重建接口；
- PLAN_READY 暂停、计划编辑/重规划；
- Workflow/Chapter 维度费用关联和项目耗时趋势；
- Usage 分页/过滤、用户今日费用、预算历史与提醒阈值；
- Trace 查询接口。

## 已验证的横向质量

- 401/403/409/422/429/5xx Problem Details；
- 长期 Token 不写 localStorage，SSE 使用 Authorization Header；
- 服务器状态与客户端状态分离；
- 浅色/深色和 `prefers-reduced-motion`；
- axe WCAG 2.2 A/AA、跳过导航、路由焦点和键盘 Dialog；
- 初始 JS gzip 预算；
- Dockerfile、Nginx SPA fallback、同源 API 代理和 `/healthz`；
- Phase 1—9 Playwright 回归。
- Skill 工坊桌面/移动/深色视觉回归、动态模板单元测试，以及“不覆盖手写内容”回归。

## Roadmap 声明

上述阻塞项不是当前已实现能力。后端新增正式 Controller/DTO/OpenAPI 后，前端应先更新 `docs/api-contract.md` 和 TypeScript 类型，再实现对应 UI；不得直接把测试 Demo fixture 接入生产应用。
