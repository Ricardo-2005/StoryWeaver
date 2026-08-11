# Roadmap 状态

| 范围 | 状态 | 说明 |
|---|---|---|
| Phase 0 基础设施 | 已实现 | Maven、Spring Boot、Docker、Flyway、Testcontainers、ArchUnit、Actuator、Prometheus、Grafana、CI |
| Phase 1 Auth/Project/Canon | 已实现 | 用户、JWT、项目、快照、正典资产及版本、隔离、乐观锁 |
| Phase 2 人物/大纲/章节/Skill | 已实现 | 人物状态、大纲树、章节版本、三级 Skill 合成与冲突检测 |
| Phase 3 DeepSeek/Agents | 已实现 | V4 Adapter、四类 Agent、Writer SSE、重试、并发控制、Usage |
| Phase 4 世界书/记忆 | 已实现 | 三种激活模式、本地 ONNX、pgvector 精确检索、激活解释、Token 裁剪、StoryEvent、降级与隔离测试 |
| Phase 5 工作流 | 已实现 | WorkflowRun、状态机、预检、上下文包、三节点编排、持久化 SSE、恢复、取消、幂等和单 Writer |
| Phase 6 一致性与提交 | 已实现 | StoryFact、道具/知识状态、四类校验、Reviewer、修订重提取、BLOCKER 门禁、审批、原子提交与回滚 |
| Phase 7 MCP/费用/可观测 | 已实现 | Stateless Streamable HTTP、6 Tools、5 Resources、3 Prompts、候选写入与审计、定价/预算、Prometheus、OpenTelemetry/Tempo、Grafana |
| Phase 8 Eval/Demo | 已实现 | 120 组冲突固定集、Context Baseline、本地性能微基准、20 章 Demo 数据、三分钟脚本、十章连续工作流、可复现实测报告 |
| V1.1 导入与长篇基础 | 已实现 | TXT/Markdown/DOCX/ZIP 导入、章节切分、AI 候选审查、别名合并、伏笔台账、影响分析与 Git ZIP 导出 |
| V1.5 半自动长篇生产 | 已实现 | 滚动大纲、1—3 章串行批次、重大剧情门、15% 局部修订、章节分支、DeepSeek Pro/Flash 回退与尝试审计 |

状态只描述仓库中已实现并通过自动化验收的能力，不把设计稿中的未来能力算作完成。

## V1.5 之后的基础设施增强（未实现）

- Prompt Registry、Agent Contract 版本库和 Golden Regression；
- Embedding 重建、混合检索、缓存与 HNSW 调优；
- MCP 授权 Scope、第三方工具白名单和风险确认；
- Compose Secrets、Staging、备份恢复演练、SBOM 和镜像扫描加固；
- TLS、反向代理、多实例、高可用与 Kubernetes。

V1.5 完成代表当前单机产品范围的工程验收完成，不代表上述基础设施增强已经交付。
