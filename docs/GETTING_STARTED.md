# 快速开始

本指南说明如何在 Windows 上运行完整的 StoryWeaver 开发环境。根目录脚本使用 Docker 启动后端、数据库、缓存、监控服务与前端；无需本机安装 Java、Maven、Node.js 或 pnpm。

## 前置条件

- Windows 10/11 与 PowerShell 5.1 或更新版本。
- 已安装并启动 Docker Desktop，且使用 Linux Containers 模式。
- 可访问 Docker 镜像仓库。首次构建前端和后端时需要下载基础镜像和依赖。
- 推荐至少 8 GB 可用内存和 15 GB 可用磁盘空间。

检查 Docker：

```powershell
docker version
docker compose version
```

## 启动

在仓库根目录运行：

```powershell
.\Start-StoryWeaver.ps1
```

首次运行时，脚本会：

1. 检查 Docker CLI、Compose 和 Docker 引擎连接。
2. 若 `backend/.env` 不存在，从 `.env.example` 创建本地配置副本。
3. 使用 `backend/compose.yaml` 启动 PostgreSQL、Redis、后端、Tempo、Prometheus 与 Grafana。
4. 使用 `frontend/compose.frontend.yaml` 启动 Nginx 承载的前端。
5. 等待后端和前端健康检查通过，再打开 `http://127.0.0.1:4173`。

不希望脚本自动打开浏览器时：

```powershell
.\Start-StoryWeaver.ps1 -NoBrowser
```

默认会复用本机已有镜像，启动更快，也不会因为临时网络问题重复拉取基础镜像。修改后端/前端源码或 Dockerfile 后，需要明确重新构建：

```powershell
.\Start-StoryWeaver.ps1 -Rebuild
```

若当前 PowerShell 禁止执行本地脚本，可只对本次命令绕过策略：

```powershell
powershell -ExecutionPolicy Bypass -File .\Start-StoryWeaver.ps1
```

## 验证服务

启动完成后访问：

```powershell
Invoke-WebRequest http://127.0.0.1:18080/actuator/health
Invoke-WebRequest http://127.0.0.1:4173/healthz
```

前端首页：`http://127.0.0.1:4173`。后端健康端点返回 HTTP 200，表示应用及其必需依赖已经就绪。

## 停止与重启

停止并保留数据库、Redis 和监控数据卷：

```powershell
Push-Location backend
docker compose down
Pop-Location
Push-Location frontend
docker compose -f compose.frontend.yaml down
Pop-Location
```

再次执行根目录启动脚本即可重启。不要随意加 `-v`：该参数会移除 Docker 数据卷，导致本地 PostgreSQL、Redis、Prometheus、Grafana 和 Tempo 数据丢失。

## 端口

| 端口 | 服务 | 用途 |
| --- | --- | --- |
| 4173 | 前端 | StoryWeaver Web 界面 |
| 18080 | 后端 | 当前工作区 REST API、健康检查、MCP；容器内仍为 8080 |
| 5432 | PostgreSQL | 主数据与 pgvector |
| 6379 | Redis | 缓存与协调 |
| 19090 | Prometheus | 当前工作区指标查询；容器内为 9090 |
| 13080 | Grafana | 当前工作区监控仪表盘；容器内为 3000 |

当前工作区使用替代宿主映射，其中 9090 位于 Windows 保留端口范围；具体值来自 `backend/.env`。全新环境仍以 `.env.example` 的 8080/9090/3000 为默认值。可通过 `APP_PORT`、`PROMETHEUS_PORT`、`GRAFANA_PORT` 调整宿主端口；容器内端口不变。前端端口可通过启动前设置 `FRONTEND_PORT` 改变。

## 常见问题

### Docker 未运行

启动 Docker Desktop，等待状态变为 Running 后再执行脚本。`docker info` 能正常返回服务器信息才表示引擎可用。

### 后端健康检查超时

检查容器和日志：

```powershell
Set-Location backend
docker compose ps
docker compose logs --tail=150 app
```

首次启动需要拉取镜像、构建 Java 工程与执行 Flyway 迁移，耗时会明显长于后续启动。数据库端口冲突、Docker 资源不足或依赖下载失败也会导致超时。

### 前端健康检查超时

```powershell
Set-Location frontend
docker compose -f compose.frontend.yaml ps
docker compose -f compose.frontend.yaml logs --tail=150 frontend
```

确认 4173 未被占用。不要在当前完整环境中直接以默认参数单独重建前端，否则 `BACKEND_UPSTREAM` 可能回落到宿主 8080。优先运行根目录 `Start-StoryWeaver.ps1`，脚本会读取 `APP_PORT` 并把实际地址注入前端容器。

### 端口被 Windows 禁止绑定

“An attempt was made to access a socket in a way forbidden by its access permissions” 不一定表示有进程占用端口，也可能是 Hyper-V/WSL 保留端口段。检查：

```powershell
netsh interface ipv4 show excludedportrange protocol=tcp
```

如果端口落在保留范围内，在 `backend/.env` 选择未保留的宿主端口，再重新运行根启动脚本。当前实例采用 `APP_PORT=18080`、`PROMETHEUS_PORT=19090`、`GRAFANA_PORT=13080`。

### AI 生成功能不可用

在 `backend/.env` 填写有效的 `DEEPSEEK_API_KEY`，然后重启后端容器。没有 Key 时不影响登录、项目管理、编辑与大部分非生成型功能。

### 世界书语义检索降级

`backend/models` 下缺少 ONNX 模型或 tokenizer 时，应用会降级为常量与关键词检索。需要向量检索时，参照 `backend/scripts/download-embedding-model.ps1` 下载模型后重启服务。
