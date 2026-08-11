# Phase 9 前端性能报告

测量日期：2026-08-03。

## 环境与口径

- Node.js 24；
- pnpm 10.30.0；
- Vite 8 production build + preview；
- Playwright Chromium；
- REST 使用确定性本地路由数据，不包含公网延迟；
- 初始 JS 通过 Vite manifest 递归统计入口的静态 imports，并逐文件 gzip；动态路由 chunk 不计入首屏。

## 结果

| 指标 | 实测 | 设计目标 | 结果 |
|---|---:|---:|---|
| 初始同步 JS gzip | 56,361 B | < 358,400 B | 通过 |
| 登录页 LCP | 112.0 ms | < 2,500 ms | 通过 |
| 工作台可用 | 148 ms | < 3,500 ms | 通过 |

上述浏览器数字来自单 worker 专项测量。最终 16-worker 并发 E2E 中的样本为登录 LCP 316 ms、工作台可用 288 ms，也通过同一阈值。

初始入口不包含按路由加载的 TipTap 和 ECharts。当前最大路由 chunk 是 ECharts 可观测性页，约 189.57 KB gzip；Vite 对其 568.49 KB 的压缩前体积发出非失败提示。

## 可重复命令

```bash
pnpm test:performance
```

该命令重新构建、执行 `scripts/check-bundle-budget.mjs`，并用单 worker 运行性能浏览器测试。结果会随硬件、浏览器版本和系统负载变化。

## 不应宣称的数字

本轮没有在真实网络、真实后端负载或低端设备上测量公网 LCP、编辑器输入延迟和 10 万字首开耗时，因此这些指标不能作为已验证的简历数字。
