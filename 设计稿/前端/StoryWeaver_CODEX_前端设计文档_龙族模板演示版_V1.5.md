# StoryWeaver 前端设计文档 V1.5

> 日期：2026-08-04  
> 基线：前端设计 V1.3 和当前 Phase 0—9 实现  
> 目标：让作者先安全恢复已有作品，再可控地连续推进 1—3 章

## 1. 设计延续

继续使用 Vue 3、TypeScript、Vite、Vue Router、Pinia、TanStack Query、Element Plus、TipTap 与 ECharts。视觉沿用低装饰、宽留白、墨绿色强调色和浅/深主题；不引入新的视觉语言。

服务器数据只进入 TanStack Query；导入向导草稿、队列筛选、分支比较偏好等客户端状态进入 Pinia 或页面状态。组件不得直接调用 fetch。

## 2. 新增信息架构

项目侧栏顺序：

```text
项目概览
创作工作台
人物 / 世界书 / 大纲 / 章节 / Skill
滚动大纲
连续生产
导入与恢复
模型与费用
```

移动端保持 Drawer；三个新增入口有独立图标文本、当前路由状态和 Loading/Error/Empty。

## 3. 导入与恢复页

路由：`/projects/:projectId/imports`

### 3.1 向导

1. 文件选择：格式、大小和安全提示；
2. 切分预览：可编辑标题、顺序、包含/排除，显示字符数和重复提示；
3. 提取进度：逐章状态、失败原因、重试和取消；
4. 候选审查：按人物/世界书/事件/伏笔分组，接受、拒绝、合并；
5. 完成摘要：写入数量、未确认候选、进入项目按钮。

浏览器不自行解析整本作品作为真源；文件通过 multipart 上传，所有切分结果以服务端响应为准。

### 3.2 影响与导出

章节版本区域增加“影响分析”，展示事实、人物状态、后续章节、Embedding 和 Context 五类结果。项目页提供 Git ZIP 导出，必须使用鉴权下载且不得把 Token 放 URL。

## 4. 滚动大纲页

路由：`/projects/:projectId/rolling-outline`

使用五个纵向区块：总纲、当前卷、故事弧窗口、详细章纲窗口、下一章场景卡。顶部显示当前章节和窗口覆盖范围；编辑使用 expectedVersion。滚动补足是显式按钮，先预览再确认，不自动覆盖用户正在编辑的文本。

## 5. 连续生产页

路由：`/projects/:projectId/production`

### 5.1 创建队列

- 只能选择 1—3 个符合条件的章节；
- 显示顺序、视角人物、默认目标、逐章补充指令；
- 启动前展示预算、滚动大纲覆盖和重大剧情门禁说明；
- 提交使用 Idempotency-Key。

### 5.2 队列状态

左侧为队列列表，主区为章节 Item Stepper，右侧为当前门禁/Workflow 摘要。支持暂停、恢复、取消；取消确认必须说明已提交章节不会回滚。

门禁卡片必须展示明确类型标签、证据、成本、来源章节和“批准/拒绝”。拒绝后保留当前 Workflow 草稿，不自动启动下一章。

## 6. 章节分支

章节编辑器增加“分支”Tab：

- MAIN 只读标识；
- 从当前确认版本创建命名分支；
- 分支版本列表和正文编辑；
- 双栏或段落级比较；
- “评估提升为主线”只创建影响报告，不直接覆盖 MAIN。

分支页必须持续显示“分支事实不会进入主线”的边界提示。

## 7. 局部修订与模型降级

Workflow 审查问题增加“局部修订”操作。用户确认问题段、相邻上下文、替换文本和修改比例后提交；超限错误在原 Dialog 内展示。

Workflow 状态页新增 Model Attempt Timeline，展示 Agent、Provider、模型、尝试、耗时、Token、成本、失败码和最终采用标记。模型与费用页增加降级次数/降级率，但所有数字必须来自后端。

## 8. API 类型

优先从 OpenAPI 生成；若后端仍没有 OpenAPI，则严格根据 V1.5 实际 DTO 建立：

```text
ImportJobResponse / ImportChapterResponse / ImportCandidateResponse
ForeshadowResponse / ImpactReportResponse
RollingOutlineResponse
ChapterBatchResponse / BatchItemResponse / StoryGateResponse
LocalRevisionRequest
ChapterBranchResponse / BranchVersionResponse
ModelAttemptResponse / ModelHealthResponse
```

禁止预先写未落地 Controller 的 endpoint。

## 9. 可访问性和性能

- 导入 Stepper、队列 Stepper、Tabs、Dialog 和候选表支持键盘；
- 上传进度和队列状态使用 `role=status`，错误使用 `role=alert`；
- 状态不只依赖颜色；
- 章节切分和候选列表超过 100 项使用虚拟化或分页；
- 分支 Diff、DOCX 预览和队列详情路由懒加载；
- `prefers-reduced-motion` 下停止进度动画；
- axe、浅深视觉回归和 390px 移动端纳入 E2E。

## 10. 测试场景

```text
导入 TXT → 调整章节 → 提取 → 接受候选
危险 ZIP → 422 Problem Details
修改旧章 → 查看影响报告
创建 3 章队列 → 第一章审批 → 第二章门禁暂停 → 批准 → 完成
取消队列 → 已提交章节保留
局部修订超过 15% → 被阻止
创建分支 → 保存版本 → MAIN 不变
主模型 429 → 降级时间线可见
刷新页面 → 导入/队列/门禁从服务端恢复
```

## 11. V1.5 Definition of Done

```text
[ ] 三个新增项目入口和页面完成
[ ] 导入向导只使用真实 API
[ ] 1—3 章队列可恢复、暂停和取消
[ ] 门禁阻止自动推进
[ ] 分支不污染 MAIN
[ ] 局部修订比例由后端强制
[ ] 降级记录来自后端
[ ] 浅色、深色、键盘、axe、移动端通过
[ ] pnpm lint/typecheck/test/build/E2E 通过
[ ] README 与 Roadmap 不夸大未实现能力
```
