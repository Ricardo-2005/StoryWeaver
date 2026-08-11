# 开发与测试

完整环境推荐使用根目录的 `Start-StoryWeaver.ps1`。如果需要修改某一端，可按下面方式在本机运行对应服务。

## 后端开发

要求：JDK 21、Docker Desktop。数据库和 Redis 仍建议由 Compose 提供。

```powershell
Set-Location backend
Copy-Item .env.example .env   # 仅在 .env 尚不存在时执行
docker compose up -d postgres redis
.\mvnw.cmd spring-boot:run
```

本地 profile 默认连接 `localhost:5432` 与 `localhost:6379`。常用命令：

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spotless:apply
.\scripts\run-phase8-evaluation.ps1
```

`verify` 会运行格式检查、单元测试和集成测试；集成测试依赖 Docker/Testcontainers。后端测试、架构和运维细节见 [`backend/docs/testing.md`](../backend/docs/testing.md)。

PowerShell 运行单个集成测试时应给包含点号的 Maven 属性加引号，例如：

```powershell
.\mvnw.cmd '-Dit.test=Phase1ApiIT' verify
```

## 前端开发

要求：Node.js 24 LTS、Corepack、pnpm 10。前端开发服务器默认运行在 `127.0.0.1:5173`，并将 `/api` 代理到 `127.0.0.1:8080`。

```powershell
Set-Location frontend
corepack enable
pnpm install
pnpm dev
```

常用验证命令：

```powershell
pnpm lint
pnpm typecheck
pnpm test:unit
pnpm build
pnpm test:e2e
pnpm test:a11y
pnpm test:visual
pnpm test:performance
```

首次运行 Playwright 测试时，可能需要执行 `pnpm exec playwright install` 安装测试浏览器。前端阶段记录与验收范围见 [`frontend/docs`](../frontend/docs)。

如果系统全局 Node/pnpm 与项目版本不符，不要关闭 engine-strict；应使用 Node 24 和 Corepack 激活 `pnpm@10.30.0`。完整 Docker 构建已经固定正确版本。

## 修改前后的最低验证

| 变更范围 | 最低建议验证 |
| --- | --- |
| 前端页面、组件、状态管理 | `pnpm lint`、`pnpm typecheck`、相关 Vitest 用例 |
| 前端接口或路由 | 上述命令 + 相关 Playwright 用例 |
| Java 业务代码 | `./mvnw.cmd test` |
| 数据库迁移、工作流、鉴权 | `./mvnw.cmd verify` |
| Compose、Docker、环境变量 | 完整启动后检查两个健康端点 |

## 代码边界

- 后端以模块化单体组织，遵循 ArchUnit 约束；跨模块依赖应通过清晰的应用层边界发生。
- 数据库变更新增 Flyway 脚本，已发布迁移不可修改。
- 前端页面不直接发起 `fetch`；使用 `src/api/endpoints` 下的接口封装和共享 `apiClient`。
- 生成结果经 Reviewer 后停在 `WAITING_APPROVAL`；只有显式审批才可在一个事务中创建正式章节版本并回写已接受的故事状态。
