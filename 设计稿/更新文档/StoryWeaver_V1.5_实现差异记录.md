# StoryWeaver V1.5 实现差异记录

> 日期：2026-08-04  
> 原则：设计稿先行；实现阶段发现既有架构约束时，以实际 Controller、DTO、迁移和自动化验收为准。

## 已按设计落地

- V1.1：TXT、Markdown、DOCX、ZIP 导入；服务端章节切分；现有 ExtractorGateway 候选抽取；候选人工决定；别名合并；伏笔生命周期；章节影响报告；鉴权 Git ZIP 导出。
- V1.5：滚动大纲；1—3 章串行批次；显式重大剧情门；暂停、恢复和取消；审批完成后自动推进下一章；15% 局部修订上限；独立章节分支及版本；提升前影响报告；DeepSeek V4 Pro/Flash 回退；模型调用审计和健康状态。
- 前端：导入与迁移、滚动大纲、连续写作、伏笔台账页面；章节编辑器分支与影响报告；Workflow 局部修订和模型尝试审计；统一 Loading、Empty、Error 和 Problem Details。

## 实际契约相对初版 V1.5 设计的差异

| 项目 | 初版设计 | 实际实现 | 原因 |
|---|---|---|---|
| OpenAPI | 优先生成类型 | 仓库仍无 OpenAPI，按 Java DTO 手工建立 TypeScript 类型 | 不虚构规范文件 |
| 导入原文件 | 配置目录持久化原文件 | 请求内安全解析，不保存原文件 | 当前部署没有对象存储与保留策略，减少敏感原稿留存 |
| 导入状态推进 | extract/retry 异步 202 | 当前调用现有同步 ExtractorGateway；返回完整最新任务 | 现有 Agent 网关没有导入任务消息队列 |
| 导入完成 | 设计初稿未单列 | 新增 `POST /api/imports/{id}/complete` | 人工确认后必须有显式动作创建真实章节 |
| 滚动大纲 | 五层独立文本 | 当前章、窗口、摘要、目标和风险 | 复用现有 Outline 作为长期层级真源，避免复制总纲 |
| 剧情门来源 | 自动风险检测与成本门 | 批次创建时由用户显式选择 `gatedChapterIds` | 不用关键词猜测重大剧情，不虚构成本预测接口 |
| 局部修订定位 | ParagraphKey 或字符范围 | 实际 DTO 使用 UTF-16 字符偏移范围 | 后端正文尚未持久化 ParagraphKey |
| 模型提供商 | 多 Provider 路由 | DeepSeek V4 Pro 与 Flash 交叉回退 | 仓库只有 DeepSeek Provider；不创建未配置的第三方 Provider |
| 模型尝试数据 | 独立逐尝试成本记录 | 从真实 `usage_record` 按 Workflow 时间窗返回模型、尝试数、耗时与状态 | 现有 Usage 已是调用计费真源 |
| 错误状态 | 新接口草案包含若干 422 | 沿用既有 Problem Details：业务输入 400、版本/状态冲突 409 | 保持全仓错误语义兼容 |

## 明确不属于 V1.5 产品交付

- 第三方模型 Provider、本地大模型、Kubernetes、多实例高可用、KMS、HNSW 调优、混合检索重构。
- 自动聊天回复、Conversation/Message 持久化、假的 AI 延时或不存在的接口。
- 分支自动覆盖 MAIN；V1.5 只生成影响报告，主线变更仍需人工操作。
