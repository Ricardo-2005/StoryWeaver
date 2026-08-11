# Phase 7 实施记录

核对日期：2026-08-03。

## 读取依据

- `设计稿/StoryWeaver_CODEX_前端设计文档_龙族模板演示版_V1.3.md` 的章节编辑器、记忆与事实、审查中心、测试策略和 Phase 7；
- `设计稿/StoryWeaver_CODEX_后端设计文档_龙族模板演示版_V1.2.md`；
- `backend/docs/api.md`；
- 实际 `WorkflowController`、`WorkflowDtos`、`WorkflowApprovalService`、`WorkflowAtomicCommitter`、`WorkflowService`；
- `ConsistencyReviewService`、`ConsistencyValidatorEngine`、各一致性 Validator、领域枚举和 V7 数据库迁移；
- Phase 0—6 前端实现。

根目录、`backend` 和 `frontend` 下仍未发现 `AGENTS.md`。后端仍未提供 OpenAPI/Swagger 文件。

## 已完成

- 全量映射实际 `ApproveWorkflowRequest` 和 `RevisionRequest` TypeScript 类型；
- `POST /api/workflows/{runId}/request-revision` 全文修订与重新提取；
- `POST /api/workflows/{runId}/approve` 人工审批和原子提交；
- 审查问题列表，展示严重度、来源、类别、正文证据、历史证据、Reviewer 建议和是否阻止提交；
- 使用后端 evidence 在运行态正文中创建非侵入式 ReviewMark，并跳转到精确证据；
- BLOCKER、Context Stale、非 WAITING_APPROVAL 状态均在客户端阻止审批，后端仍作为最终真源再次校验；
- 全文修订弹窗，修改后明确提示必须重新提取；提交失败时保留弹窗中的用户正文；
- 修订成功后恢复同一 Workflow 的 SSE/REST 状态跟踪，不新建 Workflow；
- 候选事实逐项展示正文证据并按 `candidateIndex` 选择；没有证据的候选禁止接受；
- 明确未勾选候选会在提交事务中被标记为 REJECTED；
- 可选高级原子变更表单：人物状态、道具归属、时间线事件、人物知识；
- 人物状态自动带入真实 `CharacterState.version`，全部变更要求正文证据；
- 审批提交使用当前 Workflow `version`，不做乐观更新；
- 成功后失效 Chapter、章节列表和版本列表 Query，并显示正式版本号与审批时间；
- 审批失败后保留全部表单；同时重新获取 Workflow，以展示后端校验新增的 ReviewIssue；
- 完成态提供返回章节编辑器查看正式版本入口。

## 对接的实际接口

- `GET /api/workflows/{runId}`：审查问题、候选事实、Workflow 版本与提交结果；
- `POST /api/workflows/{runId}/request-revision`：`{ revisedDraft }`；
- `POST /api/workflows/{runId}/approve`：Workflow 版本、提交说明、接受的候选索引及四类原子变更；
- `GET /api/projects/{projectId}/characters`：人物选择与人物状态乐观锁版本；
- `GET /api/chapters/{chapterId}`、章节列表和版本列表：提交成功后的缓存刷新；
- Phase 6 Workflow SSE：修订后继续跟踪重新提取与审查。

## 设计稿与实际 API 的差异

- `ReviewIssueResponse` 没有 `paragraphKey/from/to/range`，只能用 evidence 精确文本定位；证据不是当前正文片段时明确禁用或提示，不能伪造位置。
- 后端没有解决、忽略一次、标记误报或接受单条建议的接口；`resolved` 只有响应字段。未解决 BLOCKER 只能通过修订并重新提取消除。
- `request-revision` 与 `reextract` 实际调用同一服务逻辑，二者都提交完整 `revisedDraft`，不是局部 Patch；前端因此不伪装自动局部替换。
- 候选事实审批只接受 `candidateIndex[]`，没有编辑候选内容或逐条拒绝接口；原子提交时未接受的候选由后端统一标记为 REJECTED。
- 后端为所有提取候选使用正文第一段作为 evidence，并固定 `paragraphKey="p-1"`；这与前端稳定 ParagraphKey 设计不一致。
- Extraction 的 `characterChanges/itemTransfers/events/knowledgeTransfers` 都是字符串数组，没有结构化 ID、版本和证据，不能安全自动填充审批 DTO；高级变更必须由用户显式确认和填写。
- 审批并不接收 Chapter `expectedVersion`；它锁定 Workflow 并检查 Workflow `expectedVersion`，随后在同一事务中创建 ChapterVersion。
- 后端没有单独“预览原子提交结果”或 dry-run 接口。客户端基础校验不替代后端确定性校验。
- 后端校验提案时可能追加 ReviewIssue 后返回 409；前端失败后刷新 Workflow 并保持表单。

## 测试覆盖

- Vitest：审批请求默认值与版本、ReviewMark 精确证据、Context Stale、BLOCKER、候选证据、高级变更证据和项目人物引用；
- Playwright：证据跳转、候选事实选择、真实请求体和原子提交结果；
- Playwright：BLOCKER 禁止提交、全文修订、重新提取、恢复可审批状态；
- Phase 0—6 单元与 E2E 全量回归。

## 未完成

Phase 8 的费用、Cache Hit/Miss、工作流耗时、图表、预算可视化和 Trace UI 未提前实现。后端缺失的 ReviewIssue 处置、候选事实编辑、局部 Patch、提交 dry-run 和结构化提取提案未虚构。
