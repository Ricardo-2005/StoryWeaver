# 部署与运维

本地完整部署由两个 Compose 项目组成：`backend/compose.yaml` 管理应用与依赖，`frontend/compose.frontend.yaml` 管理前端 Nginx 容器。根目录启动脚本会依次处理两者。

## 服务与职责

| 服务 | 职责 | 持久化 |
| --- | --- | --- |
| `postgres` | 业务数据、Flyway 迁移、pgvector | `postgres_data` |
| `redis` | 缓存与协调数据 | `redis_data` |
| `app` | Spring Boot API、MCP、工作流 | 无本地卷 |
| `tempo` | OpenTelemetry Trace | `tempo_data` |
| `prometheus` | 指标采集和查询 | `prometheus_data` |
| `grafana` | 仪表盘与可视化 | `grafana_data` |
| `frontend` | 静态前端与 `/api` 反向代理 | 无本地卷 |

## 健康检查与日志

```powershell
Set-Location backend
docker compose ps
docker compose logs --tail=150 app
docker compose logs --tail=150 postgres

Set-Location ..\frontend
docker compose -f compose.frontend.yaml ps
docker compose -f compose.frontend.yaml logs --tail=150 frontend
```

当前工作区后端检查 `http://127.0.0.1:18080/actuator/health`；前端静态容器检查 `http://127.0.0.1:4173/healthz`。端口来自 `.env`，全新环境可能使用默认 8080。业务 API 是否可用以后端 readiness、登录和项目请求为准。

## 监控

- Prometheus：`http://127.0.0.1:19090`
- Grafana：`http://127.0.0.1:13080`
- 后端 Prometheus 指标：`http://127.0.0.1:18080/actuator/prometheus`

Grafana 的数据源和 StoryWeaver 概览面板由 Compose 挂载的 provisioning 文件自动配置。Trace 由后端 OpenTelemetry 导出到 Tempo。请在生产环境修改 Grafana 初始账号和密码。

## 数据与恢复

`docker compose down` 只停止并删除容器和网络，保留命名卷；这适用于日常重启。`docker compose down -v` 会删除数据卷，运行前必须明确确认可丢弃 PostgreSQL、Redis 与监控历史。

生产环境中，应定期备份 PostgreSQL，并在升级前验证备份可恢复。Flyway 负责模式演进，不要用手工 SQL 绕过迁移管理。

当前本地实例在清理历史测试账号前创建了 `backups/pre-admin-cleanup-20260809-1613.dump`。恢复前必须停止应用写入，并优先恢复到临时数据库验证；该文件包含业务数据，不得公开分发。

## 生产化边界

当前 Compose 适合本地开发与演示。生产部署还应补充：TLS/反向代理、Secret Manager、受限网络、外部数据库备份、多实例策略、资源限制、告警与变更流程。项目 roadmap 中未实现的能力不应被视为已有生产保障。
