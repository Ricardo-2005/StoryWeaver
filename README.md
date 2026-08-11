# 文脉 StoryWeaver

StoryWeaver（文脉）是一个面向长篇小说创作的全栈应用。它将项目、世界书、人物、章节、滚动大纲与 AI 写作工作流整合在一个工作台中，并通过一致性校验、审核与版本化机制，帮助作者管理长篇创作中的设定和连续性。

当前版本为 **V1.5 + 2026-08 功能更新**。项目由 Vue 3 前端和 Spring Boot 后端组成；本仓库的后端已覆盖 Phase 0–8，前端已覆盖 Phase 0–9、V1.5 长篇生产、项目选项式向导、全局 Skill 工坊及动态模板熔炉。产品设计稿和各阶段实现记录位于 [`设计稿`](设计稿) 目录，当前事实以[实现状态](docs/IMPLEMENTATION_STATUS.md)为准。

## 快速启动

在 Windows PowerShell 中，从仓库根目录执行：

```powershell
Set-Location 'D:\实习\StoryWeaver'
.\Start-StoryWeaver.ps1
```

脚本会检查 Docker、首次创建后端本地配置、启动后端与前端，等待健康检查通过后打开浏览器。默认复用本机已有镜像；源码或 Dockerfile 有变化时使用 `./Start-StoryWeaver.ps1 -Rebuild` 重新构建。

也可以在资源管理器中直接双击 [`启动StoryWeaver.cmd`](启动StoryWeaver.cmd)。它会自动调用同一套启动逻辑；启动失败时会保留命令窗口，便于查看错误信息。

默认访问地址：

| 服务 | 地址 |
| --- | --- |
| StoryWeaver 前端 | http://127.0.0.1:4173 |
| 后端健康检查 | http://127.0.0.1:18080/actuator/health |
| Prometheus | http://127.0.0.1:19090 |
| Grafana | http://127.0.0.1:13080 |
| MCP 端点 | http://127.0.0.1:18080/mcp |

首次启动前，请先安装并运行 Docker Desktop。详细要求、故障排查和停止方式请参阅[快速开始指南](docs/GETTING_STARTED.md)。

## 项目能力

- 账号认证、`USER/ADMIN` 角色、项目管理、归档查看/恢复/永久删除、项目快照和版本化正典资产；创建项目时可选择题材、目标读者、作品视角、篇幅与故事构想，并在项目设置中修改。
- 人物、人物状态、世界书、大纲、章节与章节不可变版本管理。
- 独立于小说项目的全局 Skill 工坊：可用 UTF-8/GB18030 TXT 与手写粘贴文本熔炼 Skill；7 种素材 × 4 种 Skill 类型提供 28 套动态模板，逐条核验证据、运行 8 类边界测试并导出不含原文的标准 Skill 包。
- 导入 TXT、Markdown、DOCX、ZIP；支持章节切分、候选审核和 Git ZIP 导出。
- 基于 Planner、Writer、Extractor、Reviewer 的写作工作流，提供预检、上下文预览、SSE 进度、暂停/恢复、取消与审批。
- 通过 StoryFact、物品归属、人物知识边界和时间线校验，阻止明显的设定冲突。
- 长篇生产能力：滚动大纲、批量串行章节、重大剧情门、局部修订、章节分支与模型尝试审计。
- 用量、Token、成本、请求耗时、预算与可观测性面板。

AI 写作工作流可配置 DeepSeek；没有配置 API Key 时，服务仍可启动，但需要模型调用的功能不可用。世界书向量模型缺失时会降级为常量与关键词检索。

## 技术架构

```text
浏览器
  │
  ├── Vue 3 + TypeScript + Vite（frontend，:4173）
  │       └── /api 反向代理
  │
  └── Spring Boot（容器 :8080；当前宿主 :18080）
          ├── PostgreSQL + pgvector（:5432）
          ├── Redis（:6379）
          ├── Prometheus（容器 :9090；当前宿主 :19090）
          ├── Grafana（容器 :3000；当前宿主 :13080）
          ├── Tempo（链路追踪）
          └── DeepSeek / MCP
```

| 层级 | 主要技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Query、TipTap、ECharts、Element Plus |
| 后端 | Java 21、Spring Boot 4、Spring AI、JPA、Flyway、Spring Security |
| 数据与运行 | PostgreSQL 18 + pgvector、Redis 8、Docker Compose |
| 质量保障 | Vitest、Playwright、axe、Testcontainers、ArchUnit、Spotless |
| 可观测性 | Actuator、Micrometer、Prometheus、Grafana、OpenTelemetry、Tempo |

## 仓库结构

```text
StoryWeaver/
├── Start-StoryWeaver.ps1  # 根目录一键启动脚本
├── README.md              # 项目总览（本文件）
├── docs/                  # 根目录使用与维护文档
├── evals/                 # Agent Evaluation Harness 与版本化 Dataset
├── frontend/              # Vue 3 前端
├── backend/               # Spring Boot 后端、Compose 与运维资源
└── 设计稿/                # 产品、前端、后端设计稿与实现差异记录
```

## 文档导航

- [文档中心](docs/README.md)：按使用、开发、运维和详细契约组织的完整索引。
- [项目流程说明](docs/PROJECT_FLOW.md)：从建项目、沉淀设定到生成、审核和发布章节的完整操作流程。
- [当前实现状态](docs/IMPLEMENTATION_STATUS.md)：现有功能、迁移、测试、运行端口、当前本地实例和明确边界。
- [技术选型与问题解答](docs/TECHNICAL_QA.md)：为什么采用这些技术、遇到的问题、解决方案和替代方案。
- [系统架构说明](docs/SYSTEM_ARCHITECTURE.md)：前后端、核心模块、数据流和部署拓扑。
- [接口与数据说明](docs/API_AND_DATA.md)：接口分组、认证、版本与一致性规则。
- [快速开始](docs/GETTING_STARTED.md)：前置条件、启动、停止、端口与常见问题。
- [开发与测试](docs/DEVELOPMENT.md)：本地开发模式、常用命令和验证策略。
- [Agent Evaluation](docs/evaluation.md)：RAG、Token、一致性、Workflow Stub 与 MCP 的离线基线和报告。
- [配置与安全](docs/CONFIGURATION.md)：环境变量、DeepSeek、Embedding 与本地配置边界。
- [部署与运维](docs/OPERATIONS.md)：健康检查、日志、监控、数据持久化与恢复注意事项。
- [后端架构](backend/docs/architecture.md)、[后端 API](backend/docs/api.md)、[后端测试说明](backend/docs/testing.md)。
- [前端 API 契约](frontend/docs/api-contract.md)、[前端完成度审计](frontend/docs/frontend-completion-audit.md)、[前端设计系统](frontend/docs/design-system.md)。

## 开发约定

- 不要提交 `backend/.env`、模型文件、构建产物或本地数据卷。
- 后端数据库结构只通过 Flyway 迁移变更；不要手工修改已应用的迁移文件。
- 前端业务请求经 `src/api/endpoints` 与共享 API Client 发出，不在页面中直接调用后端。
- 正式设定变更必须走审核语义；MCP 仅可创建带证据的候选事实，不能直接写入正典。

更多边界与已知限制请以各子项目 README 和设计稿中的实现差异记录为准。

## Agent Evaluation

Windows 用户可双击 `evals\run-evals.cmd` 一键运行 Offline 全量评测，并在 `evals\reports\latest\summary.md` 查看最新结果。默认不会调用 DeepSeek。

冻结 v1 基线为 Recall@5 22.00%、Recall@10 76.50%、Token Reduction 78.42%；评测驱动选择的生产 `VECTOR_ONLY` 策略达到 Recall@5 93.00%、All-Required@10 100.00%、MRR 1.0000、Token Reduction 78.59%。预先冻结的 24 条 holdout 得到 Recall@5 94.10%、All-Required@10 100.00%，且未据此回调参数。详见 [RAG 评测驱动优化](docs/rag-evaluation-optimization.md) 与 [evals/README.md](evals/README.md)。
