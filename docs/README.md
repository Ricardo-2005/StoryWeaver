# StoryWeaver 文档中心

> 深入了解完整技术决策、真实评测边界与面试表达，请阅读 [StoryWeaver 项目技术白皮书](StoryWeaver_项目技术白皮书/StoryWeaver_项目详细技术文档.md)。

本目录保存面向使用、开发、部署和交接的当前文档。`设计稿/` 保存产品目标与更新需求，`frontend/docs/phase-*.md` 保存阶段历史；当前可运行事实以源码、迁移、自动化测试和本目录中央文档为准。

## 阅读路线

### 第一次使用

1. [快速开始](GETTING_STARTED.md)
2. [项目流程](PROJECT_FLOW.md)
3. [当前实现状态](IMPLEMENTATION_STATUS.md)

### 开发与联调

1. [系统架构](SYSTEM_ARCHITECTURE.md)
2. [接口与数据](API_AND_DATA.md)
3. [开发与测试](DEVELOPMENT.md)
4. [技术选型与问题解答](TECHNICAL_QA.md)

### 配置与运维

1. [配置与安全](CONFIGURATION.md)
2. [部署与运维](OPERATIONS.md)
3. [后端配置手册](../backend/docs/configuration.md)
4. [后端运维手册](../backend/docs/operations.md)

## 详细契约

- [Agent Evaluation](evaluation.md)：RAG、Token、一致性、Workflow Stub 与 MCP 的离线评测。
- [RAG 评测驱动优化](rag-evaluation-optimization.md)：冻结基线、失败根因、实验矩阵、选型与 holdout。
- [后端 API 契约](../backend/docs/api.md)：128 条 REST 路由、MCP 与 Actuator。
- [后端架构](../backend/docs/architecture.md)：模块、事务、工作流、MCP 和可观测性。
- [后端数据库](../backend/docs/database.md)：V0—V15、51 张业务表、约束和迁移纪律。
- [后端测试](../backend/docs/testing.md)：单元、集成、Testcontainers 与 CI。
- [后端实现审计](../backend/docs/acceptance.md)：能力证据、限制和复验方式。
- [前端 API 契约](../frontend/docs/api-contract.md)：真实 DTO、差异和 Skill 熔炉契约。
- [前端完成度审计](../frontend/docs/frontend-completion-audit.md)：已完成范围和后端阻塞项。
- [前端设计系统](../frontend/docs/design-system.md)：主题、组件、表单、响应式和可访问性规则。

## 文档状态规则

- 中央文档描述当前状态，阶段文档只作为历史记录。
- API 数量、迁移版本和测试结果必须能由代码或自动化验证复现。
- 文档不保存密码、JWT、API Key、完整 Prompt、用户正文或其他敏感数据。
- 本地实例端口与默认端口必须明确区分。
- 规划能力必须标注“未实现”，不能用 Demo fixture 冒充正式数据。
