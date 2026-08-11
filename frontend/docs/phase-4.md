# Phase 4 实施记录

核对日期：2026-08-03。

## 读取依据

- `设计稿/StoryWeaver_CODEX_前端设计文档_龙族模板演示版_V1.3.md` 的核心写作工作台、TipTap 编辑器设计和 Phase 4；
- `设计稿/StoryWeaver_CODEX_后端设计文档_龙族模板演示版_V1.2.md`；
- `backend/docs/api.md`；
- `ChapterController`、`ChapterDtos`、`ChapterService`、章节实体和 V3/V6/V7 数据库迁移；
- Phase 0—3 前端代码。

仓库根目录、`backend` 和 `frontend` 下仍未发现 `AGENTS.md`。后端仍未提供 OpenAPI/Swagger 文件。

## 已完成

- `/projects/:projectId/chapters/:chapterId` 正文编辑路由；
- TipTap 标题/段落正文编辑；
- 粗体、斜体、删除线、引用、分隔线、高亮、撤销和重做；
- 全屏编辑；
- 每个段落生成稳定 `p_<短 UUID>` ParagraphKey；
- 停止编辑两秒后保存 IndexedDB 草稿；
- 窗口失焦和离开路由前尝试保存本地草稿；
- 刷新页面并重新登录后提示恢复或舍弃本地草稿；
- IndexedDB 保存失败时保留当前编辑内容并显示错误；
- 未提交正式版本时的浏览器关闭和路由导航保护；
- 查找、下一处、单处替换和全部替换；
- 去除空白后的字数统计和段落统计；
- 通过后端创建正式章节版本；
- 正式版本列表；
- 通过后端恢复历史版本，恢复操作创建新版本且不覆盖历史；
- 编辑器单独路由懒加载，避免增加登录和项目列表首屏体积；
- 桌面版本侧栏和移动端全屏版本面板。

## 设计稿与实际 API 的差异

- 后端只保存纯文本 `content`，不接收 TipTap JSON、HTML、ParagraphKey Map 或 Content Hash；
- 后端没有草稿保存端点，自动保存只能使用 IndexedDB，不能宣称为服务器保存；
- ParagraphKey 在本地草稿内稳定，提交正式纯文本版本后无法由后端持久化；
- 当前版本响应不包含 Diff、段落 Alias 或编辑来源；
- `backend/docs/api.md` 声明恢复返回 201，但实际 Controller 返回 200；前端不依赖错误的文档状态码；
- 当前 Phase 4 不调用 Writer SSE、工作流、Preflight 或 DeepSeek。

## 测试覆盖

- 单元测试验证 ParagraphKey 映射、空白字数规则和 100,000 字符文档；
- Playwright 验证 100,000 字符正文继续编辑、两秒自动保存、刷新后重新登录、IndexedDB 恢复、查找替换、正式版本创建和后端恢复；
- 浏览器测试记录 `/ai/*` 与 `/workflows/*` 请求并断言本流程没有调用。

## 未完成

等待后端新增草稿契约后，才能把 TipTap JSON、ParagraphKey、服务端自动保存、跨设备草稿恢复和正式段落定位作为服务器能力实现。Preflight、Context Preview、Workflow 和 Writer SSE 属于后续阶段。
