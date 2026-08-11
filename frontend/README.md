# 文脉 StoryWeaver 前端

Vue 3 + TypeScript 前端。当前完成 **Phase 0—9 与产品 V1.5**，范围以仓库中的真实后端 Controller、DTO、数据库迁移和 V1.5 设计文档为准。

## 已实现

- 登录、注册、内存 Bearer 凭证、路由保护和统一 Problem Details；
- 项目、快照、正典资产、人物/状态、世界书、大纲、章节/版本和 Skill；
- ChatGPT 式 App Shell、Composer、仅内存 Writing Block、资产 Canvas；
- TipTap 长章节编辑、ParagraphKey、IndexedDB 草稿恢复、查找替换和版本恢复；
- Workflow Preflight、Context Preview、SSE 去重/重连/停止、Review、候选事实和原子审批；
- Usage、Token、Cache、费用、请求耗时、模型能力和版本化预算；
- 深浅主题、跳过导航、路由焦点、Ctrl/⌘K 项目搜索和响应式导航；
- Vitest、Playwright E2E、axe、视觉回归、性能预算、录屏配置、Docker/Nginx 和 CI。
- V1.1 导入恢复：TXT/Markdown/DOCX/ZIP 上传、服务端章节切分、候选审查、伏笔台账与鉴权 Git ZIP 导出；
- V1.5 长篇生产：滚动大纲、1—3 章串行批次、重大剧情门、暂停/恢复/取消、局部修订、章节分支和模型尝试审计。
- 全局 Skill 工坊：项目外 Skill 管理、TXT/手写混合来源、段落证据、28 套动态模板、逐条审阅、8 类验证、安全 ZIP 导出和项目基础 Skill 绑定。
- 认证角色契约：前端识别后端 `USER/ADMIN` 用户响应；当前没有虚构管理员后台。

详细完成度和后端阻塞项见 [前端完成度审计](docs/frontend-completion-audit.md)。Phase 9 测试与性能结果见 [Phase 9 实施记录](docs/phase-9.md) 和 [性能报告](docs/phase-9-performance.md)。

## 环境

- Node.js 24 LTS
- pnpm 10.30.0

版本由 `engines`、`packageManager` 和 Corepack 锁定；`.npmrc` 的 `engine-strict=true` 会拒绝错误的 Node 主版本。

## 本地命令

```bash
pnpm install
pnpm dev
pnpm lint
pnpm typecheck
pnpm test:unit
pnpm build
pnpm test:e2e
```

专项验收：

```bash
pnpm test:a11y
pnpm test:visual
pnpm test:performance
pnpm demo:record
```

`demo:record` 使用 Playwright 固定 Demo 数据和路由拦截，只服务于自动化演示与测试，不会进入生产构建或冒充后端数据。录屏产物位于 `artifacts/demo`，操作说明见 [三分钟 Demo 脚本](docs/demo-script.md)。

开发服务器将 `/api` 代理到 `http://127.0.0.1:8080`。生产镜像中的 Nginx 使用 `BACKEND_UPSTREAM` 指定后端，浏览器始终访问同源 `/api`。当前完整工作区后端宿主端口为 18080，应使用根目录启动脚本自动注入，不要让单独 Compose 启动回落到默认 8080。

## Docker

```bash
docker build -t storyweaver-frontend:phase9 .
docker run --rm -p 4173:8080 storyweaver-frontend:phase9
curl http://127.0.0.1:4173/healthz
```

网络环境需要镜像源时可临时传入 `--build-arg NPM_REGISTRY=https://registry.npmmirror.com`；默认值仍是官方 npm registry。

也可运行：

```bash
docker compose -f compose.frontend.yaml up --build -d
```

`/healthz` 只验证静态前端容器；业务 API 健康由后端负责。

## 契约与 Roadmap 边界

后端没有 OpenAPI/Swagger 文件，因此 TypeScript 类型严格依据实际 Java DTO 建立，业务请求只允许从 `src/api/endpoints` 调用共享 `apiClient`。完整差异见 [API 契约记录](docs/api-contract.md)。

当前后端没有 Conversation、Message、Chat SSE、会话固定/归档/搜索、字段级 AI 候选、世界树操作及部分历史/调试接口。对应 UI 不生成假回复、不持久化假会话，也不创建不存在的接口。这些是后端契约阻塞项，不计入已实现能力。

Phase 0—9 的逐阶段历史记录保存在 `docs/phase-0.md` 至 `docs/phase-9.md`。早期阶段文件描述的是当时边界，当前完成状态以本 README 和完成度审计为准。
