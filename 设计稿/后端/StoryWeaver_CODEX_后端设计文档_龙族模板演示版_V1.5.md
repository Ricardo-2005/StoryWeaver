# StoryWeaver 后端设计文档 V1.5

> 日期：2026-08-04  
> 基线：后端设计 V1.2 与当前 V1.0 实现  
> 目标：为作品导入和连续 1—3 章生产提供真实、可恢复、可审计的后端契约

## 1. 模块边界

新增模块：

```text
importing   文件导入、切分、候选、别名合并
foreshadow  伏笔生命周期
impact      章节影响分析、Git ZIP 导出
production  滚动大纲、章节批次、剧情门禁
branching   章节分支与分支版本
fallback    模型路由、尝试审计和健康状态
```

现有 `workflow` 仍是单章执行真源；`production` 只能编排，不能复制工作流状态机。

## 2. 数据模型

### 2.1 导入

- `import_job`：project、originalName、mediaType、sha256、status、error、version、timestamps；
- `import_chapter`：job、sequenceNo、title、content、contentHash、status、version；
- `import_candidate`：job、chapter、candidateType、naturalKey、payload JSON、status、targetId、version；
- `character_alias_candidate`：job、canonicalName、alias、confidence、status、targetCharacterId；
- 原文件保存到配置目录，数据库只存安全相对路径和摘要。

### 2.2 伏笔与影响

- `foreshadow`、`foreshadow_transition`；
- `chapter_impact_report`、`chapter_impact_item`；
- 影响报告是不可变快照，重复请求用 idempotency key 去重。

### 2.3 连续生产

- `rolling_outline`：五层文本、窗口大小、currentChapterNo、version；
- `chapter_batch`：状态、maxChapters、currentItem、viewpoint、instruction、cancelRequested、version；
- `chapter_batch_item`：sequence、chapter、workflowRun、status、独立错误；
- `story_gate`：gateType、severity、evidence、sourceRun、status、decision、version；
- 数据库保证同一项目最多一个活跃 Batch，同一章节最多属于一个活跃 Batch。

### 2.4 分支与模型尝试

- `chapter_branch`：chapter、name、branchType、baseVersionNo、headVersionNo、status、version；
- `chapter_branch_version`：branch、versionNo、content、summary、changeSummary、contentHash；
- `model_attempt`：workflowRun、agent、attemptNo、provider、model、promptVersion、status、failureCode、Token、cost、duration；
- 分支表不更新 `chapter.current_version_no`，也不写 MAIN 事实/状态表。

## 3. API 契约

### 3.1 Import

```text
POST   /api/projects/{projectId}/imports                 multipart file → 202 ImportJobResponse
GET    /api/projects/{projectId}/imports                 ImportJobResponse[]
GET    /api/imports/{importId}                           ImportJobDetailResponse
PUT    /api/imports/{importId}/chapters                  expectedVersion + chapters
POST   /api/imports/{importId}/extract                   Idempotency-Key → 202
POST   /api/imports/{importId}/retry                     expectedVersion → 202
POST   /api/imports/{importId}/cancel                    expectedVersion
POST   /api/imports/{importId}/candidates/decide         candidate decisions
POST   /api/imports/{importId}/aliases/merge             alias decisions
GET    /api/projects/{projectId}/exports/git             application/zip
```

上传默认限制 50 MiB、最多 500 章、单章 500,000 字符。ZIP 解压后总大小、文件数和路径必须受限。

### 3.2 Foreshadow / Impact

```text
POST   /api/projects/{projectId}/foreshadows
GET    /api/projects/{projectId}/foreshadows
PUT    /api/foreshadows/{id}
POST   /api/foreshadows/{id}/transition
POST   /api/chapters/{chapterId}/impact-reports          Idempotency-Key → 202
GET    /api/chapters/{chapterId}/impact-reports
GET    /api/impact-reports/{reportId}
```

### 3.3 Rolling Outline / Batch / Gate

```text
GET    /api/projects/{projectId}/rolling-outline
PUT    /api/projects/{projectId}/rolling-outline
POST   /api/projects/{projectId}/rolling-outline/advance
POST   /api/projects/{projectId}/chapter-batches         Idempotency-Key → 202
GET    /api/projects/{projectId}/chapter-batches
GET    /api/chapter-batches/{batchId}
POST   /api/chapter-batches/{batchId}/pause
POST   /api/chapter-batches/{batchId}/resume
POST   /api/chapter-batches/{batchId}/cancel
GET    /api/chapter-batches/{batchId}/gates
POST   /api/story-gates/{gateId}/approve
POST   /api/story-gates/{gateId}/reject
```

Batch 创建请求必须包含 1—3 个唯一 chapterId、viewpointCharacterId、默认 instruction 和可选逐章 instruction。服务按 sequence 顺序启动现有 Workflow；一项到达 `WAITING_APPROVAL` 或 Gate 时停止推进。该章原子审批成功后才调度下一项。

### 3.4 Local Revision / Branch / Fallback

```text
POST   /api/workflows/{runId}/local-revisions
POST   /api/chapters/{chapterId}/branches
GET    /api/chapters/{chapterId}/branches
GET    /api/chapter-branches/{branchId}
POST   /api/chapter-branches/{branchId}/versions
POST   /api/chapter-branches/{branchId}/promote-impact
GET    /api/workflows/{runId}/model-attempts
GET    /api/ai/model-health
```

局部修订以 ParagraphKey 或字符范围定位，必须提供 expectedWorkflowVersion。默认 `maxChangedRatio=0.15`，服务端配置是最终上限。

## 4. 状态与事务

- Import 切分和候选决定使用乐观锁；候选批量确认在单事务中执行，任一非法决定全部回滚；
- Batch 调度使用数据库行锁和唯一约束，恢复任务可幂等重复调用；
- Story Gate 决策与 Batch 状态转换在同一事务中；
- 分支版本不可变，提升 MAIN 只生成影响报告，V1.5 不自动合并；
- 模型尝试先落 `STARTED`，成功/失败独立更新，进程崩溃后可标记 `INTERRUPTED`；
- 所有新表包含 `project_id` 或可通过不可变外键追溯项目，查询必须经过 `ProjectAccessService`。

## 5. 文件与安全

- 文件名只用于展示，落盘使用 UUID；
- 校验 MIME 与扩展名，拒绝宏、可执行文件、路径穿越和压缩炸弹；
- DOCX 只读取正文段落和标题样式，不执行外部关系；
- 导出 ZIP 使用流式响应，不包含 Secret、JWT、API Key、原始上传绝对路径或内部用户标识；
- 影响分析、候选确认、门禁和分支操作写结构化审计日志。

## 6. 错误码

```text
import_format_unsupported        415
import_archive_unsafe            422
import_state_conflict            409
batch_limit_exceeded             422
batch_active_conflict            409
story_gate_required              409
local_revision_too_large         422
branch_isolation_violation       409
model_fallback_exhausted         503
```

全部使用现有 Problem Details，并返回 traceId。

## 7. 配置

```yaml
storyweaver:
  import:
    storage-dir: ${STORYWEAVER_IMPORT_DIR:./data/imports}
    max-file-size: 50MB
    max-chapters: 500
  production:
    max-batch-chapters: 3
    max-local-revision-ratio: 0.15
    chapter-cost-gate: 5.00
  llm:
    fallback:
      enabled: true
      retryable-statuses: [408, 429, 500, 502, 503, 504]
```

## 8. 迁移与测试

- 只新增 Flyway 迁移，不修改 V0—V8；
- Testcontainers 验证全量迁移和项目隔离；
- Golden Files 覆盖 TXT/MD/DOCX/ZIP；
- Chaos Test 覆盖 Batch 崩溃、重复调度、取消和恢复；
- 分支隔离测试必须证明 MAIN 的 chapter、fact、state 均未改变；
- fallback 使用 WireMock 验证 429→备用模型、非重试错误不降级和全部失败审计。

## 9. Exit Gate

后端只有在全部核心 API、迁移、权限、状态机和自动化测试通过后，才能把 `backend/docs/roadmap.md` 的 V1.1/V1.5 标为已实现。
