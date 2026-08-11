# StoryWeaver TXT 书籍导入并创建项目——Codex 实现更新 V1.0

> 更新日期：2026-08-11  
> 本文档记录仓库当前真实实现，不将设计稿中未落地的类名或 API 当作已实现能力。

## 1. 实际基线

实现前已阅读并以下列内容为真源：

- `设计稿/更新文档/StoryWeaver_TXT书籍导入项目_Codex更新文档_V1.0.md`；
- `设计稿/后端/StoryWeaver_CODEX_后端设计文档_龙族模板演示版_V1.5.md`；
- `设计稿/前端/StoryWeaver_CODEX_前端设计文档_龙族模板演示版_V1.5.md`；
- 根目录、`backend`、`frontend`、现有文档与测试；
- 真实 `NovelProject`、`Chapter`、`ChapterVersion`、`ProjectService`、`ChapterService`、Flyway、既有 `StoryImportService`、Spring Multipart、Nginx、Vue Router 和 `NewProjectView`。

仓库中既有 `com.storyweaver.importing` 的语义是“向已存在项目导入 TXT/Markdown/DOCX/ZIP 素材”。新需求是“上传 TXT、预览、再创建项目”，因此实际新增为 `com.storyweaver.importing.book`，不覆盖既有流程。

## 2. 用户流程

入口在项目首页顶部、无项目空状态、全局侧边栏和 `/projects/new` 创建方式区均可见；`/projects/new` 显示两个真实入口：

1. 从零开始：保留原有项目创建表单；
2. 导入 TXT 书籍：进入 `/projects/import/txt`。

TXT 流程：

```text
选择 .txt
→ 前端 20 MiB 校验
→ 后端流式落盘 + SHA-256
→ 编码检测
→ 用户自动/手动选择编码
→ 流式规范化和章节解析
→ 预览与人工编辑
→ 确认后创建 NovelProject / Chapter / ChapterVersion
→ 基础导入完成
→ 可选按 Chapter / Chunk 做 AI Candidate 分析
```

上传后不会立即创建 `NovelProject`。

## 3. 真实 API

为避免与已有 `GET /api/imports/{importId}` 冲突，新流程使用下列路由：

```text
POST  /api/imports/txt
GET   /api/txt-imports/{importId}
POST  /api/txt-imports/{importId}/parse
GET   /api/txt-imports/{importId}/preview
GET   /api/txt-imports/{importId}/chapters/{chapterId}/content
PATCH /api/txt-imports/{importId}/chapters/{chapterId}
POST  /api/txt-imports/{importId}/chapters/reorder
POST  /api/txt-imports/{importId}/chapters/merge
POST  /api/txt-imports/{importId}/chapters/split
POST  /api/txt-imports/{importId}/chapters/whole
POST  /api/txt-imports/{importId}/chapters/fixed-split
POST  /api/txt-imports/{importId}/commit
POST  /api/txt-imports/{importId}/cancel

POST  /api/projects/{projectId}/book-analysis
GET   /api/txt-imports/{importId}/analysis
PATCH /api/txt-imports/{importId}/analysis/candidates/{candidateId}
```

Preview 只返回章节元数据。当前章节正文通过独立 content API 最多读取 5,000 字符，不会把整本 20 MiB 文本一次返回前端。

## 4. 20 MiB 限制

限制位置：

- 前端：`TXT_IMPORT_MAX_BYTES = 20 * 1024 * 1024`；
- Spring Multipart：`max-file-size: 20MB`、`max-request-size: 25MB`、`file-size-threshold: 2MB`；
- 业务层：`TxtImportProperties.maxFileSize`，同时检查 `MultipartFile#getSize()` 和实际流式读取字节数；
- Nginx：`client_max_body_size 25m`，为 multipart boundary 留开销；
- 既有项目内素材导入的业务常量同步为 20 MiB，避免声称 50 MiB 但被 Spring 先拦截。

`ImportSourceStorage` 使用 64 KiB buffer 从 `MultipartFile#getInputStream()` 复制到私有目录，复制时同步计算 SHA-256。上传、解码、解析不使用 `getBytes()`。正式导入按源 offset 顺序一次流式读取，任一时刻只组装当前章节正文。

## 5. 编码策略

检测顺序：

1. UTF-8 BOM；
2. 严格 UTF-8 decoder（malformed/unmappable 均 `REPORT`）；
3. 严格 GB18030 decoder；
4. 全部失败则返回 `INVALID_TEXT_ENCODING`。

用户可重新选择 `UTF-8`、`GB18030` 或 `GBK` 并重新解析。自动落到 GB18030 时 `encodingConfident=false`，前端会明确提示人工检查预览。

安全规范化只包括：

- CRLF / CR 通过流式行读取规范为 LF；
- 移除文件头 BOM；
- 移除 NUL；
- 连续空行最多保留两行。

不修改标点、错别字、人名或正文。

## 6. 章节解析

`TxtChapterParser` 的真实版本号为 `txt-lines-v1`。只有独立行、不超过 120 字符且完整匹配 heading pattern 时才切分。

支持：

- `第一章`、`第1章`、`第001章`及带标题形式；
- `第一回`、`第一卷`、`卷一`、部/篇/集；
- `楔子`、`序章`、`序言`、`前言`、`引子`、`尾声`、`终章`、`后记`、`番外`；
- `Chapter 1`、罗马数字 Chapter、`Prologue`、`Epilogue`；
- 大小写不敏感的英文标题。

“他在正文中说第一章只是代号”不匹配整行 heading，不会误切。

无标题 TXT 首次解析为整本单章，预览页另提供：

- 保留整本一章；
- 用户主动按 1,000—100,000 目标字符、优先在段落边界切分；
- 在任一章内按用户给定字符 offset 手动拆分。

Preview 还可修改标题、包含/排除、上下排序和合并相邻章节。

## 7. Import Job 与权限

基础导入状态：

```text
UPLOADED
→ DECODING
→ PARSED
→ WAITING_CONFIRMATION
→ IMPORTING
→ COMPLETED

任意处理错误 → FAILED
用户取消/源过期 → CANCELLED
```

AI 分析状态单独保存：

```text
NOT_REQUESTED → QUEUED → ANALYZING → WAITING_REVIEW → COMPLETED
                                          └→ FAILED
```

`book_import_job`、`book_import_source`、`book_import_chapter` 都可直接或通过不变外键追溯 `owner_id`。所有 get/parse/preview/edit/commit/cancel/analysis 查询都限定当前 JWT subject；访问他人导入返回 404，不泄露对象是否存在。

预览编辑使用 `book_import_job.version` 乐观锁。

## 8. 来源证据与重复检测

正式导入后记录：

- `novel_project.creation_source = TXT_IMPORT`；
- Project 级 `source_hash`、`source_encoding`、`parser_version`；
- Chapter 级 `import_source_id`、`source_start_offset`、`source_end_offset`、`source_hash`；
- ChapterVersion 级 `creation_source`、`import_source_id`、offset、hash、encoding、parserVersion。

每次上传计算 SHA-256。查询同一 `owner_id + sha256` 的过往任务，返回 `duplicateImportId` 和可用的 `duplicateProjectId`。它是提示，不强制禁止重复导入。

## 9. Source 存储与清理

- 原始文件不在 Web Root；
- 服务器文件名只是 `<UUID>.txt`；
- `originalFilename` 只用于展示元数据，会去除用户路径并限长；
- DB 只存相对 `storage_key`，权限不依赖 storage key；
- 默认 TTL 为 24 小时；
- 定时任务删除过期文件并将 `storage_key` 置空、记录 `deleted_at`；
- 未完成任务在源过期时转为 `CANCELLED / IMPORT_EXPIRED`；
- 已创建的 Project/Chapter/ChapterVersion 不随临时源文件删除。

Docker Compose 为后端配置 `import_sources:/data/imports` 私有卷。

## 10. 正式创建与 AI 边界

基础 commit 复用真实 `ProjectService.create`、`ChapterService.create`和 `ChapterService.addVersion`。该路径没有 `ExtractorGateway`、`PlannerGateway`或 DeepSeek 依赖。

可选 AI 分析只在已完成 TXT import 的 Project 上启动，使用虚拟线程异步执行：

```text
ChapterVersion Reader
→ 每章按行读取
→ 最大 12,000 字符 Chunk
→ 分类调用现有 ExtractorGateway
→ book_analysis_candidate(status=CANDIDATE)
→ 用户接受/拒绝
```

可选类别为人物、世界书、回顾大纲、事件和 Skill。结果不直接写入正典人物/世界书/大纲/事件/Skill 表，更不会自动标记为已确认。

## 11. Migration

只新增：

```text
backend/src/main/resources/db/migration/V16__txt_book_import.sql
```

没有修改 V0—V15。

V16 新增：

- `book_import_source`；
- `book_import_job`；
- `book_import_chapter`；
- `book_analysis_candidate`；
- Project/Chapter/ChapterVersion 导入来源与证据字段。

## 12. 与设计稿的差异

1. 设计稿示例使用 `/api/imports/{importId}`，但仓库已有同路由。实现使用 `/api/txt-imports/{importId}`，不制造 Spring mapping 冲突。
2. 设计稿建议包名 `com.storyweaver.importer`，真实仓库已有 `com.storyweaver.importing`，因此使用 `com.storyweaver.importing.book`。
3. 本次不实现 SSE 虚拟进度。基础 commit 是同步事务，只记录真实 `processed_chapters`；AI 分析异步并记录真实已处理 Chunk 数。
4. 源文件默认按需求使用 24h TTL；不因 commit 成功就无限期保留原文。证据 hash 和 offset 元数据继续保留。
5. 当前 `ExtractorGateway` 的结构化合约是 summary/events/candidateFacts/characterChanges/itemTransfers/knowledgeTransfers。为不伪造数据，新分析对每个用户选定类别发出真实分类提取请求，并把网关实际返回值保存为 Candidate；不预写模型输出。

## 13. 测试覆盖

后端单元测试覆盖：

- UTF-8 BOM 和 GB18030 检测；
- 中英文标题、序章/楔子/番外/后记/卷；
- 正文中“第一章”不误切；
- 无章节单候选；
- UUID 存储路径、SHA-256、实际流限额和部分文件清理。

Testcontainers API 集成测试覆盖：

- 上传、解析、Preview、改名、Commit；
- 另一用户访问返回 404；
- 真实 Project / Chapter / ChapterVersion 内容；
- `creationSource=TXT_IMPORT`、hash、encoding、parserVersion 证据字段；
- 全量 Flyway 迁移到 V16。

前端单元测试覆盖 `.txt`、空文件、精确 20 MiB 边界和超限文件。Playwright 场景覆盖上传、解析、正文预览、章名修改、拆分、合并、Commit 和完成后 AI Candidate 入口。

最终验证结果（2026-08-11）：

- `backend/.\mvnw.cmd clean verify`：通过；单元测试 19 项、Failsafe/Testcontainers 集成测试 20 项，Flyway 最终版本 V16；
- `frontend/pnpm lint`：通过；
- `frontend/pnpm typecheck`：通过；
- `frontend/pnpm test:unit`：18 个测试文件、49 项测试全部通过；
- `frontend/pnpm build`：通过；仅有既存的大 Chunk 优化提示；
- `playwright test tests/e2e/txt-book-import.spec.ts --workers=1`：Chromium 1 项通过；
- 根目录 `docker compose config --quiet`：通过。

## 14. 未实现或有意不做

- 没有 SSE 进度接口；不生成假进度。
- 没有把 AI Candidate 自动写入正典表；用户对 Candidate 的“接受”目前只记录 Candidate 决策，不隐式修改项目资产。
- 基础 commit 是一个原子事务，未实现设计稿可选的多批 checkpoint/retry partial project。失败时不留下半项目。
- 没有扩展 TXT 之外格式；新建项目入口只接受 `.txt`。
