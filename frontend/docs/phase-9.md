# Phase 9 实施记录

核对日期：2026-08-03。

## 读取依据

- `设计稿/StoryWeaver_CODEX_前端设计文档_龙族模板演示版_V1.3.md` 的可访问性、性能、测试、Docker、Phase 9、Definition of Done 和三分钟演示流程；
- `设计稿/StoryWeaver_CODEX_后端设计文档_龙族模板演示版_V1.2.md`；
- `设计稿/文脉_StoryWeaver_简历落地版产品设计稿.md`；
- `backend/docs/api.md`、实际 Controller/DTO/迁移和 `backend/docs/roadmap.md`；
- 前端 Phase 0—8 源码、测试、Docker/Nginx 与 CI。

根目录、`backend` 和 `frontend` 下均没有 `AGENTS.md`。后端仍没有 OpenAPI/Swagger 文件。

## 已完成

- 引入 `@axe-core/playwright`，对登录、项目 Dashboard、工作台和模型费用页执行 WCAG 2.2 A/AA 自动检查；
- 修复浅色弱化文字和深色 Element Plus Tag 的对比度问题；
- 挂载可见的跳过导航链接，并正确把焦点移动到主要内容；
- 路由切换后等待异步标题出现再聚焦，首次加载保留跳过导航的自然 Tab 顺序；
- 实现 Ctrl/⌘K 聚焦项目搜索、Escape 关闭移动导航，以及导航按钮 `aria-controls/aria-expanded`；
- 验证 Element Plus Dialog 焦点进入、Escape 关闭和焦点返回触发按钮；
- 增加浅色项目页、深色项目页、工作台和模型费用页四张视觉回归基线；
- Vite 生成 manifest，构建脚本递归计算初始同步 JavaScript gzip 体积并强制 350 KB 上限；
- 增加登录 LCP 和工作台可用时间的真实浏览器性能测试；
- 增加只用于 Playwright 的确定性 Demo 数据、三分钟自动化路线和带视频/Trace 的录屏配置；
- CI 在构建后强制执行初始 JavaScript 预算检查；
- Docker build 支持可选 npm registry 参数和依赖缓存/下载重试；Phase 9 镜像已实际构建，并验证 `/healthz` 与深层 SPA fallback 均返回 200；
- README 明确区分已实现能力、后端契约阻塞项和 Roadmap；
- 新增完整前端完成度审计，不把设计稿中的未来能力冒充为完成。

## 新增测试

- `tests/e2e/accessibility.spec.ts`：axe WCAG 2.2 A/AA；
- `tests/e2e/keyboard.spec.ts`：跳过导航、路由焦点、快捷搜索和 Dialog；
- `tests/e2e/visual.spec.ts`：四张跨平台命名视觉基线；
- `tests/e2e/performance.spec.ts`：LCP 与工作台可用时间；
- `tests/e2e/demo.spec.ts`：可重复演示路线；
- `tests/e2e/fixtures/demo.ts`：确定性测试数据，不进入生产应用。

## 性能实测

本机 Node 24、Vite Preview 和 Playwright Chromium 样本：

- 初始同步 JavaScript：56,361 B gzip，预算 358,400 B；
- 登录页 LCP：112.0 ms，目标小于 2,500 ms；
- 工作台可用：148 ms，目标小于 3,500 ms。

最终全量 16-worker 并发回归时同一测试为 LCP 316 ms、工作台可用 288 ms，仍通过目标。

这些是本地确定性测试环境结果，不等同于真实公网用户数据。详见 `docs/phase-9-performance.md`。

## 设计稿与实际能力差异

- 设计稿三分钟流程依赖 Conversation、Message、Chat SSE、会话搜索/归档、字段级 AI 候选和 Chat 内联 Diff，后端没有这些接口；演示脚本只展示真实可用能力。
- 世界书激活解释在后端服务内部存在，但当前业务 Controller 没有前端调试查询契约；不伪造调试器结果。
- 视觉回归覆盖当前真实关键页面；不存在后端契约的会话历史、Chat 回复和世界书调试结果没有制作假截图。
- 性能报告只列本轮实际测量的数字；编辑器输入延迟和公网 LCP 没有测量，因此不写入简历数字。

## 验收结论

Phase 9 的前端实现、自动化测试、可访问性、视觉基线、性能预算、Demo 和文档已完成。按真实后端 API 可交付的 Phase 0—9 前端范围已收口；设计稿中仍被后端契约阻塞的能力见 `docs/frontend-completion-audit.md`。

最终回归：13 个 Vitest 文件共 35 项通过，Playwright 24 项通过。
