# 配置与安全

后端本地配置文件为 `backend/.env`，它已被 Git 忽略。首次使用根目录启动脚本时，会从 `backend/.env.example` 复制生成。示例文件只保留占位值，真实密钥不可提交到仓库。

## 关键配置

| 配置项 | 作用 | 本地建议 |
| --- | --- | --- |
| `APP_PORT` | 后端宿主端口 | 默认 `8080`；当前工作区 `18080` |
| `POSTGRES_*` | PostgreSQL 数据库、账号、端口 | 保持本地默认或替换为私有开发值 |
| `REDIS_PORT` | Redis 对外端口 | `6379` |
| `PROMETHEUS_PORT` | Prometheus 宿主端口 | 默认 `9090`；当前工作区 `19090` |
| `GRAFANA_PORT` | Grafana 宿主端口 | 默认 `3000`；当前工作区 `13080` |
| `JWT_SECRET` | JWT 签名密钥 | 至少 32 字节的随机值 |
| `DEEPSEEK_API_KEY` | DeepSeek 调用凭证 | 仅存于本机 `.env` 或安全的密钥管理系统 |
| `DEEPSEEK_USER_ID_SECRET` | 上游用户标识 HMAC 密钥 | 与 JWT 密钥分离 |
| `EMBEDDING_ENABLED` | 是否启用 ONNX 向量模型 | 模型缺失时可设为 `false` |
| `EMBEDDING_MODELS_DIR` | 容器挂载的模型目录 | 默认 `./models`（相对 backend） |
| `GRAFANA_ADMIN_*` | Grafana 初始管理员凭证 | 本地也应修改示例密码 |

完整变量清单及 profile 行为见 [`backend/docs/configuration.md`](../backend/docs/configuration.md)。宿主端口只改变 Docker 映射，不改变容器内应用端口。前端容器必须使用与 `APP_PORT` 一致的 `BACKEND_UPSTREAM`；根启动脚本会自动完成该注入，因此完整环境不要绕过脚本分别启动。

## DeepSeek

在 `backend/.env` 中设置：

```dotenv
DEEPSEEK_API_KEY=你的真实密钥
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_USER_ID_SECRET=独立且随机的HMAC密钥
```

修改后重启后端：

```powershell
Set-Location backend
docker compose up -d --build app
```

不要在源码、提交记录、截图、前端环境变量或客户端代码中暴露 API Key。DeepSeek 未配置时，应用可以运行，但写作生成相关操作会失败或不可用。

## Embedding 模型

后端默认使用 BAAI/bge-small-zh-v1.5 的 ONNX 模型和 tokenizer，容器内默认从 `/models/model.onnx`、`/models/tokenizer.json` 读取，对应仓库中的 `backend/models` 目录。

```powershell
Set-Location backend
.\scripts\download-embedding-model.ps1
```

模型文件被 Git 忽略。缺失或初始化失败时，系统保持启动并退化为常量与关键词检索；这会影响语义召回质量，但不会阻断基本业务功能。

## 生产环境原则

- 不使用示例中的默认密码或默认 JWT 密钥。
- 将密钥交由部署平台的 Secret Manager 或等价机制注入。
- 通过 TLS 与反向代理暴露 Web/API，避免直接暴露内部监控端口。
- 限制 PostgreSQL、Redis、Prometheus 和 Grafana 的网络访问范围。
- 在升级镜像或数据库前执行备份和恢复演练。
