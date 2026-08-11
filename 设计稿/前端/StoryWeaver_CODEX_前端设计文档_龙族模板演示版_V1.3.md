# StoryWeaver / 文脉
## Codex 前端实施设计文档（ChatGPT 式对话 + 创作画布 + 手动资产编辑 +《龙族》模板演示）

> 文档版本：V1.3 Dragon-Raja Demo Edition  
> 编写日期：2026-08-03  
> 文档用途：放置于前端仓库根目录，作为 Codex 分阶段开发的实施规格  
> 对接后端：`StoryWeaver_CODEX_后端设计文档_DeepSeek_Docker_Roadmap_V1.1.md`  
> 项目类型：长篇小说创作 Agent Web 工作台  
> 开发目标：8 周内完成可运行、可测试、可演示、可写入简历的前端 MVP  
> 核心体验：**像 ChatGPT 一样自然对话，像 Canvas 一样直接编辑，所有资产仍可手动维护**  
> 前端：Vue 3、TypeScript、Vite、Vue Router、Pinia、TanStack Query、Element Plus、TipTap  
> 流式通信：SSE  
> 可视化：ECharts、Cytoscape.js  
> 包管理：pnpm  
> 部署：Dockerfile + Nginx + Docker Compose  
> 浏览器范围：当前稳定版 Chrome、Edge、Firefox；Safari 作为兼容测试目标  

---

# 0. Codex 总执行规则

Codex 必须先完整阅读本文件和后端设计文档，再开始实现。

## 0.1 分阶段实施

必须按 Phase 0—Phase 8 开发，不得一次性生成整个前端。

每个 Phase 必须：

1. 列出新增和修改文件；
2. 实施代码；
3. 执行类型检查；
4. 执行单元测试；
5. 执行组件测试；
6. 执行端到端测试；
7. 执行生产构建；
8. 修复失败；
9. 输出阶段报告；
10. 等待人工确认后进入下一阶段。

默认命令：

```bash
pnpm lint
pnpm typecheck
pnpm test:unit
pnpm test:e2e
pnpm build
```

---

## 0.2 不得虚构功能

禁止：

- 用 `setTimeout` 伪装后端 Agent 工作流；
- 用本地固定文字伪装 DeepSeek SSE；
- 用随机数字伪装 Token、费用和延迟；
- 声称支持断线恢复，却只重新刷新页面；
- 声称支持版本回滚，却只修改前端状态；
- 声称存在世界书激活解释，却不显示后端返回原因；
- 把 Roadmap 页面做成可点击按钮后声称已实现；
- 用浏览器 `localStorage` 作为章节正文的唯一存储；
- 未经用户确认自动提交候选事实。

允许在开发和测试中使用 Mock Service Worker，但必须通过明确的 `mock` 模式启用。

---

## 0.3 前端边界

前端负责：

- 页面与交互；
- 输入校验；
- 查询缓存；
- 编辑器状态；
- SSE 消费与恢复；
- 工作流可视化；
- 用户确认；
- 版本差异；
- 错误呈现；
- 可访问性；
- 前端性能。

前端不负责：

- 决定小说正典；
- 计算模型价格真值；
- 绕过后端权限；
- 自行推导角色知识；
- 自行执行一致性结论；
- 直接调用 DeepSeek API；
- 保存 DeepSeek API Key；
- 伪造后端 Workflow 状态。

---

## 0.4 API 原则

- 所有业务数据通过后端 REST API；
- 所有生成进度通过后端 SSE；
- 不从浏览器直接访问 PostgreSQL、Redis、MCP 或 DeepSeek；
- API 类型来自 OpenAPI 规范或仓库中受版本控制的契约；
- API Client 不散落在组件中；
- 401、403、409、422、429、5xx 有统一处理；
- 修改请求包含幂等键或版本字段；
- 章节保存携带乐观锁版本；
- 项目 ID 不从界面文本推导。

---

## 0.5 代码规范

- 使用 TypeScript 严格模式；
- Vue 组件采用 `<script setup lang="ts">`；
- 组合式逻辑放入 composable；
- 服务器状态使用 TanStack Query；
- 全局客户端状态使用 Pinia；
- 表单使用明确 Schema；
- API DTO 与 UI ViewModel 分离；
- 禁止组件直接调用 `fetch`；
- 禁止单个 Vue 文件超过 500 行；
- 禁止在模板中写复杂业务表达式；
- 样式优先使用 CSS Variables 和设计 Token；
- 不在组件中写硬编码颜色；
- 交互控件必须支持键盘；
- 所有可见异步操作都有 Loading、Empty、Error 和 Retry 状态。

---

# 1. 产品定位

## 1.1 产品不是聊天框

StoryWeaver 前端不是传统的：

```text
左边聊天
右边生成正文
```

它是一套创作工作台，核心对象是：

- 项目；
- 正典资产；
- 人物；
- 世界书；
- 大纲；
- 章节；
- 事件；
- 事实；
- Skill；
- 工作流；
- 审查问题；
- 版本。

聊天只是一种辅助交互，不是所有信息的唯一入口。

---

## 1.2 核心体验目标

### 可见

用户能看见：

- 当前章依赖哪些设定；
- 世界书为什么被激活；
- 使用了哪些历史事件；
- 当前 Skill 如何合成；
- Agent 正在执行哪一步；
- 本次使用多少 Token 和费用。

### 可控

用户能：

- 修改章纲；
- 删除不需要的上下文；
- 中断生成；
- 编辑正文；
- 拒绝候选事实；
- 请求局部修订；
- 回滚章节版本。

### 可解释

每个问题必须展示：

- 问题位置；
- 严重度；
- 当前正文证据；
- 历史证据；
- 建议修改；
- 是否阻止提交。

### 可恢复

断网、刷新和浏览器关闭后，用户重新进入项目可以恢复：

- 工作流状态；
- 已生成草稿；
- SSE 缺失事件；
- 编辑器未提交修改提示；
- 待处理审查问题。

---

## 1.3 人工主导、AI 辅助

StoryWeaver 的人物、世界书/世界树、大纲和章节都必须支持完整手动编辑。

产品默认原则：

```text
用户输入和确认的内容
> 用户手动修改后的内容
> AI 候选建议
> AI 自动推断
```

AI 不得无提示覆盖用户已经确认的内容。

所有核心资产都支持：

- 从空白开始手动创建；
- 逐字段编辑；
- 自定义字段；
- 拖动排序；
- 复制；
- 合并；
- 归档；
- 版本历史；
- 恢复旧版本；
- AI 生成单个字段；
- AI 重写单个字段；
- 对比后接受或拒绝；
- 查看修改会影响哪些章节和上下文。

人物、世界书和大纲即使完全不使用 AI，也必须能够独立完成创建、编辑、排序和导出。

---

## 1.4 通用的“手动编辑 + AI 建议”交互

每个可编辑字段右侧提供统一操作：

```text
生成
补全
重写
扩写
压缩
列出备选
检查冲突
查看引用
历史版本
```

AI 操作流程：

```text
选择字段
→ 输入可选要求
→ AI 生成候选
→ 显示前后 Diff
→ 接受 / 拒绝 / 继续修改
```

AI 候选不得直接写入确认版数据。

对于多字段资产，允许：

- 只生成空字段；
- 只重写选中字段；
- 根据现有字段补齐缺失字段；
- 生成多个候选版本；
- 锁定不允许 AI 修改的字段。

每个字段可以设置：

```text
MANUAL_ONLY
AI_SUGGEST_ALLOWED
AI_REWRITE_ALLOWED
LOCKED
```

---

## 1.5 资产编辑状态

人物、世界书和大纲使用统一状态：

```text
DRAFT
CANDIDATE
CONFIRMED
CONFLICTED
DEPRECATED
```

界面规则：

- `DRAFT`：可自由编辑；
- `CANDIDATE`：AI 或导入产生，等待确认；
- `CONFIRMED`：修改前显示影响范围；
- `CONFLICTED`：必须处理冲突；
- `DEPRECATED`：保留历史，但默认不注入上下文。

# 1.6 ChatGPT 式交互定位

StoryWeaver 参考 ChatGPT 的页面组织方式，但不复制其品牌或像素级界面。

核心结构：

```text
左侧：项目与对话历史
中间：当前对话
右侧：按需打开的创作画布
```

其中：

- 对话用于提出目标、追问、比较方案、启动工具和解释结果；
- 画布用于直接编辑章节、人物、世界书、大纲和 Skill；
- 正式资产由后端版本化保存；
- 对话中的普通回答不会自动变成正典；
- AI 产生的正文或资产修改必须进入可编辑候选区。

ChatGPT 式设计的价值在于：

```text
入口简单
上下文持续
操作靠近当前任务
复杂功能按需展开
结果可以继续追问
```

StoryWeaver 不应把所有功能一次性铺满主界面。

---

# 1.7 Chat 与 Canvas 的职责

## Chat

适合：

- “帮我规划下一章”；
- “检查楚子航是否已经知道青铜与火之王的真实身份”；
- “列出三个章尾方案”；
- “把这段对话写得更口语化”；
- “打开路明非的人物卡”；
- “比较入学阶段与青铜城行动阶段的状态变化”；
- “开始章节工作流”。

## Canvas

适合：

- 长文直接编辑；
- 人物字段修改；
- 世界书配置；
- 大纲拖动；
- 批量接受或拒绝候选；
- 查看 Diff；
- 处理审查问题；
- 恢复版本。

## 双向联动

```text
Chat 指令
→ 打开对应 Canvas
→ 用户在 Canvas 选择字段或段落
→ Chat 获得选区引用
→ AI 返回候选修改
→ Canvas 显示 Diff
→ 用户确认后保存
```

Chat 不能绕过 Canvas 的确认流程直接覆盖已确认内容。

# 1.8 演示小说模板：《龙族》式现代校园幻想

本文档中的产品示例统一使用《龙族Ⅰ·火之晨曦》作为演示项目模板，用于展示人物卡、世界书、大纲、角色知识、一致性审查和章节工作流。

模板只使用公开可识别的角色名、组织名和世界观名词，不复制原著正文，不将演示内容作为项目内置版权素材发布。

## 演示项目

```text
项目名：龙族Ⅰ·火之晨曦
题材：现代都市 / 校园 / 龙族幻想
当前故事段落：青铜城行动
核心目标：路明非进入卡塞尔学院后，逐步卷入屠龙任务
```

## 核心人物

```text
路明非
楚子航
陈墨瞳（诺诺）
恺撒·加图索
昂热
芬格尔
```

## 核心组织

```text
卡塞尔学院
秘党
学生会
狮心会
执行部
装备部
```

## 核心世界书

```text
龙族与混血种
血统评级
言灵
龙文
炼金术
尼伯龙根
青铜城
青铜与火之王
七宗罪
```

## 核心地点

```text
路明非故乡
芝加哥
卡塞尔学院
三峡
青铜城
```

## 演示原则

- 人物字段、世界书、大纲和章节均可手动修改；
- AI 生成内容只进入候选区；
- 角色知识必须区分“已知、听说、怀疑、未知”；
- 龙族血统、言灵、炼金武器和组织关系适合作为一致性校验示例；
- 所有示例章节名和编号仅服务于产品演示，不声称与原著目录逐章对应。

---

# 2. 视觉与品牌定位

## 2.1 视觉关键词

```text
现代
克制
安静
东方
纸墨
工具感
长时间阅读友好
```

不得做成：

- 大面积仿古纹理；
- 金色龙纹；
- 过度国风装饰；
- 游戏商城式界面；
- 霓虹 AI 控制台；
- 低对比度灰字。

---

## 2.2 品牌概念

中文名：

```text
文脉
```

英文名：

```text
StoryWeaver
```

Logo 概念：

- 一条墨线形成书页和脉络；
- 避免复杂图章；
- 小尺寸仍可辨认；
- App Icon 只保留一个抽象符号。

---

## 2.3 主题

MVP 支持：

- 浅色纸张主题；
- 深色夜写主题；
- 跟随系统。

用户正文编辑区域可独立设置：

- 纸白；
- 米白；
- 深灰；
- 纯黑。

---

## 2.4 ChatGPT 式视觉原则

采用低装饰、内容优先的界面：

- 主对话区保持宽阔留白；
- 消息正文不放入厚重卡片；
- 用户消息和 AI 消息依靠对齐、头像和轻微底色区分；
- 工具执行结果使用可折叠状态块；
- Composer 固定在底部但不遮挡内容；
- 主要按钮数量保持克制；
- 高级功能收纳在工具菜单和 Canvas；
- 生成中的过程显示简短状态，不展示冗长内部推理。

推荐对话正文最大宽度：

```text
760—840px
```

Canvas 打开后，中间对话区可以收缩到：

```text
420—560px
```

# 3. 技术版本基线

截至 2026-08-01，建议锁定以下稳定版本或同一稳定主版本：

```text
Node.js 24 LTS
pnpm 10.x
Vue 3.5.40
Vite 8.2.0
Vue Router 5.2.0
Pinia 4.x
Element Plus 2.14.3
TipTap 3.29.2
TanStack Vue Query 5.x
Vitest 4.1.10
Playwright 1.62.1
TypeScript 5.x
```

规则：

- `package.json` 使用明确版本；
- `pnpm-lock.yaml` 必须提交；
- 不使用 Vue 3.6 RC；
- 不使用 Vitest 5 Beta；
- 升级依赖必须运行完整回归；
- TanStack Query 类型更新可能发生在 Patch 版本，必须锁定精确版本；
- 生产使用 Node 24 LTS，不使用 Node 26 Current。

---

# 4. 技术选型

## 4.1 Vue 3

用途：

- 组件化 UI；
- Composition API；
- 响应式编辑状态；
- 工作流与面板组合。

统一使用：

```text
<script setup>
Composition API
TypeScript
```

---

## 4.2 Vite

用途：

- 开发服务器；
- HMR；
- TypeScript 构建；
- 生产静态资源构建；
- 环境变量注入；
- 分包。

---

## 4.3 Vue Router

负责：

- 登录保护；
- 项目路由；
- 嵌套工作区；
- 页面级代码分割；
- 离开未保存页面的导航拦截；
- 404 与权限页面。

---

## 4.4 Pinia

只保存客户端状态：

- 当前用户轻量状态；
- UI 布局；
- 主题；
- 编辑器会话；
- 当前工作流 UI；
- 本地草稿元数据；
- 全局通知。

不得把全部后端数据长期复制进 Pinia。

---

## 4.5 TanStack Query

负责服务器状态：

- 查询；
- 缓存；
- Mutation；
- Query Invalidation；
- 重试；
- 乐观更新；
- 后台刷新。

Pinia 与 Query 分工：

| 数据 | 工具 |
|---|---|
| 项目列表 | TanStack Query |
| 人物列表 | TanStack Query |
| 世界书条目 | TanStack Query |
| 当前主题 | Pinia |
| 左右面板宽度 | Pinia |
| 编辑器临时选区 | Pinia |
| 当前 SSE 连接状态 | Pinia |
| 章节正式版本 | TanStack Query |
| 运行中生成文本 | Pinia + SSE Buffer |

---

## 4.6 Element Plus

用于：

- Form；
- Dialog；
- Drawer；
- Table；
- Tree；
- Tabs；
- Tooltip；
- Select；
- Notification；
- Skeleton；
- Dropdown。

不直接使用默认视觉作为最终产品。通过 CSS Variables 建立 StoryWeaver 主题。

---

## 4.7 TipTap

用于章节正文和资产编辑。

MVP 扩展：

```text
StarterKit
Placeholder
CharacterCount
Typography
Highlight
TextAlign
History
Link
Custom ParagraphKey
Custom ReviewMark
```

不在 MVP 启用实时多人协作。

---

## 4.8 ECharts

用于：

- Token 趋势；
- 费用趋势；
- 工作流耗时；
- 世界书命中；
- 审查问题分布；
- 章节字数；
- 模型缓存命中。

---

## 4.9 Cytoscape.js

P1/V2 用于：

- 人物关系图；
- 信息传播图；
- 事件因果图；
- 伏笔图。

V1.0 只预留组件边界，不将图谱作为 MVP 阻塞项。

---

# 4.10 AI 写作产品参考与转化原则

本设计参考成熟写作工具中的以下交互模式，但不直接照搬界面。

## Novelcrafter Codex

吸收：

- 人物、地点、道具、设定等统一进入 Codex；
- 名称、别名和标签；
- 自定义类别和自定义字段；
- 自动追踪正文中的提及；
- 查看条目在正文中的全部出现位置；
- Progressions 记录人物和世界随时间变化；
- 描述和条目的历史版本；
- 世界条目按需要进入 AI 上下文。

StoryWeaver 转化为：

```text
人物卡 + 世界书条目 +  mentions + progression + 版本
```

## Sudowrite Story Bible

吸收：

- 人物和世界设定使用可折叠卡片；
- 可以新建空白卡片并完全手动填写；
- 卡片字段可以自定义和重新排序；
- 单个字段可以由 AI 生成或重写；
- 大纲既能手写，也能逐章生成；
- 章节和幕可以手动增加、删除和拖动；
- 生成内容保留历史，可恢复旧版本。

StoryWeaver 转化为：

```text
空白创建优先
+ 字段级 AI 助手
+ 卡片历史
+ 大纲拖动排序
```

## Plottr

吸收：

- 时间线；
- 场景卡；
- 主线和支线并列；
- 卡片拖放；
- 场景自定义属性；
- 人物、地点、标签与场景关联；
- 过滤和不同视图切换。

StoryWeaver 转化为：

```text
树形大纲
+ 卡片大纲
+ 时间线视图
+ 自定义场景字段
```

## NovelAI Lorebook

吸收：

- 条目内容与激活关键词分离；
- 条目优先级；
- Token 预算；
- 上下文插入和裁剪策略；
- 用户可以直接编辑 Lorebook 文本和配置。

StoryWeaver 转化为：

```text
可手动编辑的世界书
+ 激活配置
+ Token 解释
+ 调试预览
```

## 最终原则

参考产品证明的不是“AI 自动生成一切”，而是：

> 作者始终能够手动建立和修正故事资料，AI 只在明确授权的字段或任务中提供建议。

# 5. 前端仓库结构

```text
storyweaver-frontend/
├── CODEX.md
├── README.md
├── package.json
├── pnpm-lock.yaml
├── vite.config.ts
├── vitest.config.ts
├── playwright.config.ts
├── tsconfig.json
├── eslint.config.js
├── Dockerfile
├── nginx.conf
├── compose.frontend.yaml
├── .dockerignore
├── .env.example
├── docs/
│   ├── design-system.md
│   ├── api-contract.md
│   ├── editor.md
│   ├── sse.md
│   ├── accessibility.md
│   └── roadmap.md
├── public/
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── assets/
│   ├── styles/
│   │   ├── tokens.css
│   │   ├── theme-light.css
│   │   ├── theme-dark.css
│   │   ├── element-overrides.css
│   │   └── editor.css
│   ├── router/
│   ├── stores/
│   ├── api/
│   │   ├── client.ts
│   │   ├── generated/
│   │   ├── errors.ts
│   │   └── endpoints/
│   ├── queries/
│   ├── composables/
│   ├── layouts/
│   ├── components/
│   │   ├── base/
│   │   ├── editor/
│   │   ├── workflow/
│   │   ├── worldbook/
│   │   ├── review/
│   │   ├── charts/
│   │   └── navigation/
│   ├── features/
│   │   ├── auth/
│   │   ├── projects/
│   │   ├── dashboard/
│   │   ├── canon/
│   │   ├── outlines/
│   │   ├── chapters/
│   │   ├── characters/
│   │   ├── worldbook/
│   │   ├── memory/
│   │   ├── skills/
│   │   ├── workflows/
│   │   ├── reviews/
│   │   └── usage/
│   ├── pages/
│   ├── types/
│   ├── utils/
│   └── mocks/
└── tests/
    ├── e2e/
    ├── fixtures/
    └── visual/
```

---

# 6. 设计系统

## 6.1 色彩 Token

### 浅色主题

```css
--sw-bg-canvas: #f3f1eb;
--sw-bg-surface: #ffffff;
--sw-bg-subtle: #f8f7f3;
--sw-bg-editor: #fffdf8;

--sw-text-primary: #1f2523;
--sw-text-secondary: #5d6763;
--sw-text-muted: #8b928f;

--sw-border: #d9ddd8;
--sw-border-strong: #bbc3bd;

--sw-accent: #315f52;
--sw-accent-hover: #284f45;
--sw-accent-soft: #e2eee9;

--sw-danger: #b54747;
--sw-warning: #a66a1f;
--sw-success: #347a55;
--sw-info: #486d9b;
```

### 深色主题

```css
--sw-bg-canvas: #171a19;
--sw-bg-surface: #202422;
--sw-bg-subtle: #272c29;
--sw-bg-editor: #1d211f;

--sw-text-primary: #edf1ee;
--sw-text-secondary: #bcc5bf;
--sw-text-muted: #87928b;

--sw-border: #353c38;
--sw-border-strong: #4a554f;

--sw-accent: #78aa95;
--sw-accent-hover: #8dbba7;
--sw-accent-soft: #273d34;
```

---

## 6.2 字体

界面字体：

```text
Inter
Noto Sans SC
system-ui
```

正文编辑字体支持：

- 霞鹜文楷（用户本机或 Web 安全替代，不分发字体文件）；
- Noto Serif SC；
- Source Han Serif SC；
- 系统宋体；
- 无衬线。

默认正文：

```text
Noto Serif SC / system serif
18px
line-height 1.9
```

界面：

```text
14px
line-height 1.5
```

---

## 6.3 间距

使用 4px 基础网格：

```text
4
8
12
16
20
24
32
40
48
64
```

---

## 6.4 圆角与阴影

```text
小控件：6px
卡片：10px
Dialog：12px
编辑器页面：0—8px
```

阴影只用于浮层，不用于每张卡片。

---

## 6.5 状态颜色

| 状态 | 表达 |
|---|---|
| DRAFT | 中性灰 |
| RUNNING | 信息蓝 + 动态图标 |
| WAITING_APPROVAL | 警告琥珀 |
| COMPLETED | 成功绿 |
| BLOCKED | 危险红 |
| FAILED | 危险红 |
| CONTEXT_STALE | 紫色提示 |
| CANDIDATE | 蓝灰 |
| CONFIRMED | 绿色 |
| CONFLICTED | 红色 |
| DEPRECATED | 删除线灰色 |

不能仅靠颜色表达状态，必须同时有文本或图标。

---

# 7. 信息架构

## 7.1 一级入口

```text
项目
工作台
人物
世界书
大纲
章节
记忆
Skill
审查
模型与费用
设置
```

MVP 不单独提供“聊天”一级入口。

---

## 7.2 路由

```text
/login
/register

/projects
/projects/new

/projects/:projectId
/projects/:projectId/dashboard
/projects/:projectId/workspace
/projects/:projectId/characters
/projects/:projectId/characters/:characterId
/projects/:projectId/worldbook
/projects/:projectId/outlines
/projects/:projectId/chapters
/projects/:projectId/chapters/:chapterId
/projects/:projectId/memory
/projects/:projectId/skills
/projects/:projectId/reviews
/projects/:projectId/usage
/projects/:projectId/settings

/workflows/:runId

/403
/404
/error
```

---

## 7.3 路由 Meta

每条项目路由包含：

```ts
interface RouteMeta {
  requiresAuth: boolean
  requiresProject: boolean
  title: string
  feature?: string
  preserveScroll?: boolean
}
```

---

# 8. ChatGPT 式全局布局

## 8.1 默认状态：侧边栏 + 对话

```text
┌────────────────────────────────────────────────────────────────────┐
│ 侧边栏                       当前项目 / 当前会话             用户   │
├──────────────────────┬─────────────────────────────────────────────┤
│ ＋ 新对话             │                                             │
│ 搜索对话  Ctrl/Cmd+K  │             对话消息流                      │
│                      │                                             │
│ 项目：龙族Ⅰ·火之晨曦          │        用户消息 / AI 回复 / 工具状态         │
│  ├ 当前写作           │                                             │
│  ├ 人物讨论           │                                             │
│  └ 世界观整理         │                                             │
│                      │                                             │
│ 最近对话              │                                             │
│ 青铜城行动规划             │                                             │
│ 楚子航人物卡           │                                             │
│ 青铜城设定冲突           │                                             │
│                      │       ┌──────────────────────────────┐      │
│ 归档                   │       │ 输入消息……                  │      │
│ 设置                   │       │ ＋ 附件  工具  模式     发送 │      │
└──────────────────────┴───────┴──────────────────────────────┴──────┘
```

默认进入项目后首先显示对话，而不是同时展示所有资产面板。

---

## 8.2 Canvas 打开状态

```text
┌──────────────┬───────────────────────┬──────────────────────────────┐
│ 对话侧边栏    │ 当前对话               │ 创作 Canvas                  │
│              │                       │                              │
│ 新对话        │ 用户：修改青铜城行动章      │ 青铜城行动章                  │
│ 搜索          │                       │ 标题 / 正文 / 审查标记         │
│ 项目会话      │ AI：已提出三个修改方案  │                              │
│              │                       │ 可直接编辑                    │
│ 最近会话      │ [候选修改卡片]          │ 可选择段落让 AI 重写           │
│              │                       │ 历史 / Diff / 保存             │
│              │ 输入框                 │                              │
└──────────────┴───────────────────────┴──────────────────────────────┘
```

Canvas 可以：

- 占据右侧 55%—70%；
- 全屏；
- 收起；
- 在章节、人物、世界书、大纲之间切换；
- 保持当前 Chat 不丢失。

---

## 8.3 左侧侧边栏

顶部固定：

```text
新对话
搜索
项目切换
```

项目内分组：

```text
项目说明
项目文件
固定会话
最近会话
已归档会话
```

每个会话菜单：

- 重命名；
- 固定；
- 移动到分组；
- 复制会话；
- 导出；
- 归档；
- 删除。

删除需要确认。

侧边栏只快速加载最近会话。较旧会话通过搜索或“查看全部”获取，避免一次加载全部历史。

---

## 8.4 顶部栏

顶部栏保持简洁：

```text
侧边栏开关
项目名
会话标题
模型/工作模式
Canvas 开关
分享/导出
更多
用户
```

项目深层资产导航不放在顶部常驻，而通过：

- 左侧项目菜单；
- `Ctrl/Cmd + K`；
- Chat 工具菜单；
- Canvas 内部导航；

访问。

---

## 8.5 响应式

### ≥ 1440px

支持三栏：

```text
侧边栏 + Chat + Canvas
```

### 1024—1439px

Canvas 打开时：

- 侧边栏自动收缩；
- Chat 宽度降低；
- Canvas 保持主要编辑宽度。

### 768—1023px

Canvas 作为全屏层打开，Chat 可通过返回按钮恢复。

### < 768px

- 侧边栏 Drawer；
- Chat 单栏；
- Canvas 全屏；
- 不支持同时显示 Chat 与 Canvas；
- 可阅读和轻量编辑；
- 长篇排版仍建议桌面端。

# 9. 登录与项目页

## 9.1 登录页

内容：

- Logo；
- 邮箱/用户名；
- 密码；
- 登录；
- 注册；
- 服务状态提示。

错误：

- 账号密码错误；
- 网络失败；
- 后端不可用；
- 账号被限制。

不得显示 DeepSeek Key 输入框。

---

## 9.2 项目列表

卡片显示：

- 项目名；
- 类型；
- 当前卷；
- 当前章；
- 总字数；
- 最近更新时间；
- 未解决 BLOCKER；
- 最近工作流状态；
- 累计费用。

操作：

- 打开；
- 重命名；
- 归档；
- 创建快照；
- 删除。

删除需二次确认，输入项目名。

---

## 9.3 新建项目向导

步骤：

```text
基础信息
→ 作者意图
→ 世界硬规则
→ 主角
→ 基础 Skill
→ 模型预算
→ 完成
```

每一步可保存草稿。

完成后进入项目 Dashboard，不自动生成正文。

---

# 10. 项目 Dashboard

## 10.1 页面目标

在 10 秒内回答：

- 现在写到哪里；
- 当前最大的风险是什么；
- 下一步该做什么；
- 最近花了多少钱。

---

## 10.2 卡片

```text
当前进度
当前章
总字数
未解决审查问题
世界书条目
活跃人物
最近工作流
本周费用
```

---

## 10.3 下一步建议

规则由前端基于后端状态映射，不自行推理小说内容。

示例：

```text
青铜城行动章纲尚未确认
上一章存在 1 个 BLOCKER
当前章可以开始写前预检
Context Packet 已过期
```

---

## 10.4 图表

MVP：

- 章节字数趋势；
- Token/费用趋势；
- 工作流耗时；
- 审查问题分布。

图表均提供文本摘要和数据表入口。

---

# 11. Chat 对话页面

## 11.1 会话类型

项目内会话可以设置用途：

```text
GENERAL
CHAPTER_WRITING
CHARACTER_DESIGN
WORLDBUILDING
OUTLINE
REVIEW
RESEARCH
```

用途只影响默认工具和上下文，不限制用户对话。

---

## 11.2 消息结构

消息类型：

```text
USER_MESSAGE
ASSISTANT_MESSAGE
TOOL_STATUS
WRITING_BLOCK
ASSET_REFERENCE
ERROR_MESSAGE
SYSTEM_NOTICE
```

普通消息采用内容流布局，不使用厚重气泡。

用户消息可使用轻量底色和右对齐；AI 消息使用左对齐宽文本。

---

## 11.3 消息操作

用户消息：

- 编辑并重新发送；
- 复制；
- 引用；
- 删除本地草稿；
- 创建新会话继续。

AI 消息：

- 复制；
- 重试；
- 继续；
- 简化；
- 展开；
- 引用；
- 打开为 Writing Block；
- 保存为候选资产；
- 反馈问题。

重试会创建新的候选回复，不覆盖旧回复。

---

## 11.4 工具执行状态

工具执行使用紧凑折叠块：

```text
✓ 已读取路明非人物卡
✓ 已检索 8 条历史事件
● 正在检查角色知识
○ 等待审查
```

展开后可查看：

- 工具名；
- 状态；
- 输入摘要；
- 结果摘要；
- 耗时；
- Token；
- 是否修改数据。

不展示模型私有思维过程。

---

## 11.5 会话上下文

输入框上方可以显示已绑定上下文 Chip：

```text
青铜城行动章
路明非
楚子航
青铜城世界书
项目 Skill
```

用户可以：

- 查看来源；
- 移除可移除项；
- 添加人物、章节、世界书；
- 切换“仅当前消息”或“保持在会话”。

硬规则只显示，不允许从 Chat 中临时移除。

---

## 11.6 空会话首页

新会话不显示复杂 Dashboard，只显示：

```text
今天想推进什么？
```

快捷动作：

- 规划下一章；
- 继续当前章节；
- 创建人物；
- 打开世界书；
- 检查一致性；
- 导入旧稿。

最近资产和最近会话以少量卡片展示。

---

# 12. Composer 输入区

## 12.1 结构

```text
┌──────────────────────────────────────────┐
│ 已选择：青铜城行动章、楚子航                    │
│ 输入你想做的事情……                        │
│                                          │
│ ＋附件  工具  模式      预计费用     发送  │
└──────────────────────────────────────────┘
```

---

## 12.2 支持内容

- 多行文本；
- `Shift + Enter` 换行；
- `Enter` 发送；
- 文件；
- 章节引用；
- 人物引用；
- 世界书引用；
- 选中段落引用；
- 命令 `/`。

---

## 12.3 工具菜单

MVP 工具：

```text
规划章节
写作
人物
世界书
大纲
检索记忆
一致性审查
局部重写
费用估算
```

用户不必记住命令，但支持：

```text
/plan
/write
/character
/worldbook
/outline
/review
```

---

## 12.4 工作模式

模式：

```text
聊天
规划
写作
审查
只读查询
```

模式只改变默认 Agent 和工具权限。

例如“只读查询”禁止创建候选事实。

---

## 12.5 发送前提示

当操作可能产生高费用或修改数据时显示：

```text
预计调用 Planner Pro
预计费用 ￥X.XX
将创建候选场景计划，不会直接修改正文
```

---

# 13. Writing Block

## 13.1 定位

Writing Block 是嵌入对话中的可编辑文本块，适合：

- 段落候选；
- 章节片段；
- 人物简介；
- 世界书描述；
- 大纲方案；
- 修订版本。

---

## 13.2 操作

Writing Block 支持：

- 直接编辑；
- 复制；
- 打开到 Canvas；
- 保存为候选；
- 替换当前选区；
- 插入当前章节；
- 撤销 AI 修改；
- 重做；
- 查看版本；
- 对选中文字继续提问。

---

## 13.3 与正式资产的区别

Writing Block 默认属于会话内容。

只有点击：

```text
保存为候选资产
插入章节草稿
替换选中字段
```

并经过确认后，才进入正式资产系统。

---

## 13.4 选区修改

用户选中文字后，浮动工具条：

```text
改写
扩写
压缩
更口语化
检查设定
提问
```

AI 返回修改建议时显示：

- 原文；
- 新文；
- Diff；
- 接受；
- 拒绝；
- 再生成。

---

# 14. 创作 Canvas

## 14.1 Canvas 类型

```text
CHAPTER_CANVAS
CHARACTER_CANVAS
WORLDBOOK_CANVAS
OUTLINE_CANVAS
SKILL_CANVAS
REVIEW_CANVAS
DIFF_CANVAS
```

---

## 14.2 Canvas 顶部栏

显示：

- 资产名；
- 状态；
- 保存状态；
- 当前版本；
- 历史；
- Diff；
- 全屏；
- 关闭。

---

## 14.3 Chat 联动

Canvas 选中内容后，Chat Composer 自动显示引用：

```text
已选择：青铜城行动章 / 第8段
```

用户输入：

```text
这段节奏太慢，保留剧情缩短到一半。
```

AI 候选显示在 Canvas 中，而不是只发送一段无法定位的聊天文本。

---

## 14.4 内联建议

Canvas 支持：

- 段落评论标记；
- 选区建议；
- 审查问题；
- AI 修改 Diff；
- 接受单处；
- 接受全部；
- 拒绝；
- 恢复。

---

## 14.5 版本恢复

Canvas 历史面板允许：

- 查看历史版本；
- 查看 AI 和手动修改来源；
- 逐字段恢复；
- 整体恢复；
- 创建新版本而非覆盖历史。

## 14.6 《龙族》模板中的 Canvas 示例

### Character Canvas

```text
路明非
- 身份：卡塞尔学院新生
- 血统评级：S 级（演示字段）
- 当前状态：尚未完全理解自身秘密
- 已知信息：学院的屠龙使命
- 未知信息：自身真实血统与路鸣泽身份
```

### Worldbook Canvas

```text
卡塞尔学院
- 类型：组织 / 地点
- 关键词：卡塞尔、学院、执行部、诺玛
- 常驻：是
- 作用范围：学院相关章节
```

### Outline Canvas

```text
青铜城行动
1. 任务下达
2. 小队进入三峡
3. 青铜城入口开启
4. 角色知识与真实身份发生错位
5. 炼金武器和龙王线索进入下一章
```

以上均为界面与数据结构演示，用户可以手动重写为自己的原创项目。

# 15. 核心写作工作台（Canvas 详细模式）

## 11.1 布局

```text
┌──────────────────────────────────────────────────────────────────────────┐
│ 面包屑 / 青铜城行动章 / 保存状态 / 版本 / 模型 / 费用 / 开始生成 / 更多       │
├───────────────┬───────────────────────────────────┬──────────────────────┤
│ 资产树         │ 正文编辑器                         │ Agent / 上下文面板     │
│               │                                   │                      │
│ 卷纲           │ 章节标题                           │ 工作流步骤             │
│ 章纲           │                                   │ 世界书命中             │
│ 人物           │ 正文                              │ 事件记忆               │
│ 世界书         │                                   │ Skill                  │
│ 事件           │                                   │ 审查问题               │
│               │                                   │ Token                  │
└───────────────┴───────────────────────────────────┴──────────────────────┘
```

---

## 11.2 顶部状态

显示：

- 自动保存状态；
- 正式版本号；
- 本地是否有未保存修改；
- Context 是否过期；
- 工作流状态；
- 预计费用；
- 当前 Agent 模型。

---

## 11.3 左侧资产树

节点：

```text
大纲
  总纲
  第一部·火之晨曦
  青铜城行动章纲
人物
  路明非
  楚子航
  陈墨瞳
  恺撒
世界书
  卡塞尔学院
  青铜城
  青铜与火之王
事件
  龙文考试异常
Skill
  项目写作规则
```

行为：

- 单击打开详情；
- Cmd/Ctrl + 单击在临时面板打开；
- 支持搜索；
- 支持收藏；
- 不支持在树中直接删除确认资产；
- 键盘遵循 Tree View 交互。

---

## 11.4 右侧面板 Tabs

```text
Agent
上下文
审查
版本
```

右侧面板支持：

- 固定；
- 折叠；
- 调整宽度；
- 记忆上次宽度；
- 小屏转 Drawer。

---

# 12. TipTap 编辑器设计

## 12.1 内容边界

MVP 支持：

- 标题；
- 段落；
- 粗体；
- 斜体；
- 删除线；
- 引用；
- 分隔线；
- 文字高亮；
- 查找替换；
- 撤销重做；
- 全屏；
- 字数统计。

不提供：

- 表格；
- 图片内嵌；
- 任意字体颜色；
- 复杂排版；
- HTML 源码编辑。

小说正文应保持结构简单。

---

## 12.2 规范化内容

前端编辑器内部使用 TipTap JSON。

提交后端时发送：

```ts
interface ChapterDraftPayload {
  contentText: string
  contentHtml?: string
  editorDocument: JSONContent
  paragraphMap: Array<{
    key: string
    text: string
    index: number
  }>
  baseVersion: number
  contentHash: string
}
```

若后端 V1.0 暂不保存 `editorDocument`，则：

- `contentText` 为正文真源；
- TipTap JSON 仅作为前端恢复数据；
- ReviewMark 不进入正文；
- 段落键由前端稳定生成并随保存提交。

后端需在 API Contract 中明确实际支持字段。

---

## 12.3 ParagraphKey

每个段落拥有稳定键：

```text
p_<UUID短码>
```

用途：

- 审查问题定位；
- 候选事实证据；
- 局部修订；
- 版本差异；
- SSE 生成段落合并。

用户拆分段落：

- 原段落保留 Key；
- 新段落生成新 Key。

用户合并段落：

- 保留前一段 Key；
- 后一段 Key 进入 Alias 列表。

---

## 12.4 自动保存

策略：

```text
编辑停止 2 秒
→ 保存草稿
```

同时：

- 页面失焦保存；
- 切换章节前保存；
- 浏览器关闭前提示；
- 保存失败保留本地 IndexedDB 临时副本。

不得每次按键请求后端。

---

## 12.5 IndexedDB 临时恢复

只用于：

- 未提交编辑草稿；
- SSE 流式临时文本；
- 网络断开恢复；
- 编辑器布局。

不能作为正式版本真源。

Key：

```text
storyweaver:draft:{projectId}:{chapterId}:{baseVersion}
```

正式保存成功后清理。

---

## 12.6 生成期间编辑

默认策略：

- Writer 生成时编辑器进入“流式草稿模式”；
- 用户可以暂停生成后编辑；
- 生成中不允许在流末尾之外修改正文；
- 选择“接管编辑”会取消工作流流式输出；
- 已收到内容保留为运行态草稿。

避免模型继续输出时用户同时改动中间段落。

---

# 13. 章节生成交互

## 13.1 开始生成按钮

点击后不立即调用 Writer。

先打开 Preflight Dialog。

---

## 13.2 Preflight Dialog

内容：

```text
章纲：已确认
视角人物：路明非
上一章：已提交
世界硬规则：4 条
Skill 冲突：0
预计输入 Token：31,420
预计输出 Token：6,000
预计费用：￥X.XX
```

阻塞问题显示红色并禁用继续。

允许用户：

- 打开章纲；
- 打开冲突；
- 调整预算；
- 查看模型；
- 取消；
- 开始构建上下文。

---

## 13.3 Context Preview

Context 构建完成后展示：

```text
人物与状态
角色知识
世界书
最近章节
历史事件
当前事实
Skill
Token 分配
```

每项可查看：

- 来源；
- 版本；
- 激活原因；
- Token；
- 是否硬规则；
- 是否可移除。

硬规则不可移除，只能回到资产页修改。

---

## 13.4 场景计划

Planner 完成后显示：

- 章节目标；
- 视角人物；
- 场景列表；
- 每场目的；
- 冲突；
- 信息增量；
- Must Include；
- Must Avoid；
- 章尾。

用户可：

- 编辑场景计划；
- 重新规划；
- 接受并写作。

重新规划显示额外费用。

---

## 13.5 Writer 流式状态

右侧显示：

```text
✓ 写前预检
✓ 上下文构建
✓ 场景规划
● 正在生成正文
○ 事实提取
○ 一致性审查
○ 等待确认
```

编辑器中：

- 新增文本逐段出现；
- 光标不强制滚到底部；
- 用户向上阅读时显示“返回生成位置”按钮；
- 每 15 秒更新心跳状态；
- 显示已生成字数和 Token；
- “停止生成”始终可见。

---

## 13.6 中断状态

断流后显示：

```text
生成连接已中断
已保留 2,431 字运行态草稿
```

操作：

- 重新连接事件流；
- 重新生成本章；
- 保留草稿并人工编辑；
- 放弃草稿。

不得自动把两次不同生成拼接成完整章节。

---

# 14. SSE 客户端设计

## 14.1 事件类型

```text
workflow.step
text.delta
usage.partial
warning
heartbeat
text.completed
analysis.completed
review.completed
workflow.completed
workflow.error
```

---

## 14.2 EventSource 限制

原生 EventSource 不支持设置自定义 Authorization Header。

两种可选方案：

### 方案 A：同站 Cookie

推荐。

- REST 与 SSE 使用 HttpOnly Session/JWT Cookie；
- `new EventSource(url, { withCredentials: true })`；
- Nginx 同源代理。

### 方案 B：短期 SSE Ticket

- REST 获取一次性 Ticket；
- SSE URL 使用 Ticket；
- Ticket 短期有效且单次绑定；
- 不在 URL 中使用长期 Access Token。

禁止将长期 JWT 直接放在 SSE Query String。

---

## 14.3 重连

浏览器和客户端共同支持：

- 记录 `eventId`；
- 断开后指数退避；
- 重新请求 Workflow 状态；
- 发送 Last Event ID；
- 获取缺失事件；
- 对 `text.delta` 按 sequence 去重；
- 发现 Workflow 已结束时关闭连接。

---

## 14.4 客户端状态

```ts
type SseConnectionState =
  | 'idle'
  | 'connecting'
  | 'open'
  | 'reconnecting'
  | 'closed'
  | 'failed'
```

---

## 14.5 文本 Buffer

SSE Delta 不直接每个字符修改 TipTap 文档。

策略：

```text
接收 Delta
→ 内存 Buffer
→ 50—100ms 批量刷新
→ 按段落插入
```

避免高频响应式更新导致卡顿。

---

# 14.1 通用资产编辑器

人物、世界书、大纲节点和 Skill 共用统一资产编辑框架。

## 页面结构

```text
左侧：条目树 / 分类 / 搜索
中间：字段编辑器
右侧：引用、历史、AI 建议、影响分析
```

## 保存方式

支持：

```text
手动保存
自动保存草稿
保存并确认
另存为新版本
放弃修改
恢复历史版本
```

自动保存只保存草稿，不自动改变确认状态。

## 字段类型

```text
单行文本
多行富文本
Markdown
数字
布尔值
单选
多选
标签
日期/故事时间
人物引用
地点引用
世界书引用
章节引用
图片引用
自定义 JSON（高级模式）
```

## 自定义字段

用户可以：

- 新增字段；
- 重命名字段；
- 调整顺序；
- 设置必填；
- 设置默认值；
- 设置 AI 是否可修改；
- 保存为模板；
- 应用到同类现有条目。

删除字段前必须展示：

- 有多少条目正在使用；
- 是否会删除已有数据；
- 是否只隐藏字段；
- 是否创建版本快照。

## AI 建议面板

AI 生成结果显示在右侧候选区，不直接写入字段。

候选支持：

- 接受；
- 部分接受；
- 继续编辑；
- 再生成一个；
- 对比现有字段；
- 保存为候选版本；
- 丢弃。

## 字段历史

每个字段记录：

```text
修改前值
修改后值
修改者
修改方式（手动/AI/导入）
时间
来源 Workflow
备注
```

用户可以只恢复一个字段，不必恢复整张卡片。

# 15. 人物页面

## 15.1 人物列表

字段：

- 姓名；
- 别名；
- 角色；
- 生死；
- 当前地点；
- 当前目标；
- 最近变化；
- 状态来源章节。

筛选：

- 主要人物；
- 生存状态；
- 地点；
- 分卷；
- 最近出场。

---

## 15.2 人物详情

Tabs：

```text
人物卡
当前状态
知识
关系
历史版本
```

知识页显示：

- 已确认；
- 他人转述；
- 怀疑；
- 错误认知；
- 来源事件；
- 生效章节。

不显示全部真实秘密给“角色模拟”视图。

---

## 15.3 状态时间轴

按章节展示：

```text
入学前 收到卡塞尔学院录取通知
入学后 完成龙文考试
任务前 得知青铜城行动
```

点击跳转到证据段落。

---

## 15.4 人物卡手动编辑

人物可以通过三种方式创建：

```text
空白人物
从模板创建
AI 辅助创建
```

“空白人物”必须是默认且完整可用的方式。

### 基础字段

```text
姓名
别名 / 昵称 / 称号
角色类型
性别与代词
年龄
身份
所属势力
外貌
性格
背景
目标
动机
恐惧
秘密
能力
弱点
对话风格
禁忌行为
当前状态
```

所有字段均可手动修改。

### 自定义人物类型

用户可以建立：

```text
主角模板
学院导师模板
学生会成员模板
狮心会成员模板
龙类模板
秘党专员模板
```

每种类型可拥有不同字段。

### 自定义字段示例

```text
血统评级
言灵
龙族血统来源
炼金装备
学院阵营
与路明非关系阶段
读者已知秘密
```

### 别名与称呼

别名用于：

- 正文提及追踪；
- 世界书激活；
- 搜索；
- 角色知识识别。

用户可设置：

```text
是否区分大小写
是否完全匹配
是否启用正文追踪
是否仅在指定卷生效
```

### 人物 Mentions

人物详情显示正文中所有提及：

```text
章节
段落
使用名称
上下文片段
是否出场
是否仅被提及
```

点击跳转正文。

### 人物 Progressions

人物不是一张静态卡片。

支持按章节记录：

```text
外貌变化
身份变化
关系变化
能力变化
伤势变化
目标变化
认知变化
立场变化
```

用户可以手动新增 Progression，AI 只能生成候选。

写第 N 章时，系统读取 N 章之前最近生效的 Progression。

### 合并人物

重复人物可手动合并。

合并前展示：

- 别名；
- 世界书引用；
- 正文 Mentions；
- 状态；
- 角色知识；
- 事件；
- 大纲引用。

合并操作必须生成可回滚版本。

### 项目级改名

修改人物主名称时提供：

```text
仅修改人物卡
同时添加旧名为别名
预览全文替换
执行项目级替换
```

默认不自动替换正文。

# 16. 世界书页面

## 16.1 列表布局

左侧分类：

```text
人物
地点
势力
道具
能力
历史
规则
秘密
```

主表：

- 标题；
- 类型；
- 状态；
- 激活方式；
- 优先级；
- 作用范围；
- Token；
- 版本。

---

## 16.2 条目编辑

字段：

- 标题；
- 内容；
- 分类；
- 常驻；
- 关键词；
- 作用范围；
- 优先级；
- Token 上限；
- 可见角色；
- 状态。

保存前展示估算 Token。

---

## 16.3 激活调试器

输入：

- 章纲；
- 人物；
- 地点；
- 用户指令。

输出：

- 被选条目；
- 激活原因；
- 相似度；
- 优先级；
- Token；
- 被丢弃条目；
- 丢弃原因。

这是项目最重要的简历展示页面之一。

---

## 16.4 世界树视图

世界书提供一个可视化“世界树”视图，用于组织世界元素，而不是把所有条目平铺在表格中。

示例：

```text
世界
├── 地域
│   ├── 美国
│   │   ├── 芝加哥
│   │   └── 卡塞尔学院
│   └── 中国
│       ├── 三峡
│       └── 青铜城
├── 势力
│   ├── 秘党
│   ├── 卡塞尔学院
│   ├── 学生会
│   └── 狮心会
├── 规则
│   ├── 龙族血统
│   ├── 言灵
│   ├── 龙文
│   └── 炼金术
├── 道具
│   └── 七宗罪
└── 历史事件
```

用户可以：

- 新增根节点；
- 新增子节点；
- 重命名；
- 拖动移动；
- 调整同级顺序；
- 复制子树；
- 合并节点；
- 归档节点；
- 转换条目类型；
- 展开和折叠；
- 保存为世界模板。

树形层级只用于组织，不自动决定事实的父子逻辑。

## 16.5 世界书条目手动编辑

每个条目至少包含：

```text
名称
别名
类型
描述
关键词
常驻开关
作用范围
优先级
Token 上限
角色可见性
生效章节
失效章节
父节点
标签
状态
```

用户可以完全手动编辑内容与激活配置。

AI 仅提供：

- 根据已有内容补全字段；
- 生成备选描述；
- 提议关键词；
- 检查关键词过宽或过窄；
- 检查与其他条目冲突；
- 根据正文生成候选条目。

## 16.6 世界书自定义类型和字段

用户可创建自定义类型：

```text
学院
社团
秘党组织
龙类
龙文仪式
言灵
炼金武器
校园活动
血统规则
炼金科技
龙族谱系
古诺尔斯语
```

每种类型可以定义自己的字段模板。

示例“炼金武器”：

```text
当前持有人
铸造者
外观
龙文铭刻
能力
使用代价
封印或损坏状态
首次出现
当前归属
```

## 16.7 激活关键词手动配置

关键词编辑器支持：

- 主关键词；
- 别名；
- 排除词；
- 大小写；
- 完整词匹配；
- 正则高级模式；
- 指定卷；
- 指定人物；
- 指定地点；
- 冷却章节数。

实时提示：

```text
预计命中章节数
可能过度命中的关键词
完全没有命中的关键词
预计 Token 消耗
```

## 16.8 世界书 Mentions 和引用

条目详情显示：

- 正文提及；
- 大纲引用；
- 人物引用；
- 事件引用；
- 其他世界书引用；
- 最近被 Context 激活的记录。

## 16.9 世界 Progressions

地点、势力、规则和物品可按时间变化：

```text
青铜城封锁
狮心会更换负责人
炼金武器受损
血统规则被揭示
学生会与狮心会关系恶化
```

每个 Progression 支持：

- 手动创建；
- 生效章节；
- 失效章节；
- 证据；
- 版本；
- 是否正典。

# 17. 大纲页面

## 17.1 层级

```text
总纲
分卷
故事弧
章纲
```

MVP 后端至少支持总纲、分卷和章纲；故事弧在前端以可选节点呈现。

---

## 17.2 视图

### 树形视图

用于结构编辑。

### 卡片视图

用于查看每章目标、冲突、人物和状态。

### 时间轴视图

P1。

---

## 17.3 拖动

MVP 可以支持同级排序，但必须：

- 使用后端排序字段；
- 修改前二次确认影响；
- 失败后恢复原顺序；
- 键盘提供上移/下移操作。

---

## 17.4 大纲必须可完全手动创建

用户可以不调用 AI，从空白项目开始依次创建：

```text
总纲
分卷
故事弧
章节
场景
节拍
```

每个节点支持：

- 内联改名；
- 手动填写摘要；
- 增加子节点；
- 插入前后节点；
- 删除；
- 复制；
- 拆分；
- 合并；
- 拖动排序；
- 移动到其他卷；
- 设置状态；
- 关联人物、地点和世界书；
- 查看历史版本。

## 17.5 大纲节点字段

通用字段：

```text
标题
摘要
目标
冲突
信息增量
视角人物
地点
出场人物
故事时间
情绪
主线/支线
Must Include
Must Avoid
伏笔
回收
章尾
字数目标
状态
```

允许创建自定义字段。

## 17.6 三种大纲编辑视图

### 树形视图

适合维护层级：

```text
卷 → 章 → 场景 → 节拍
```

### 卡片视图

像索引卡一样编辑和拖动：

- 卡片可折叠；
- 显示人物、地点、冲突、状态；
- 支持批量选择；
- 支持颜色和标签；
- 支持宽卡模式。

### 时间线视图

横向展示：

- 主线；
- 支线；
- 人物弧；
- 感情线；
- 伏笔线；
- 反派线。

节点可以拖到其他章节，但前端必须调用后端排序和影响分析接口。

## 17.7 大纲拖动规则

拖动前：

- 显示目标位置；
- 检查层级是否允许；
- 检查是否跨卷；
- 检查是否已有正文；
- 检查后续章节依赖。

拖动后不立即静默确认。

如果节点已有正文或正典影响，弹出：

```text
仅调整显示顺序
修改正式章节顺序
创建大纲分支
取消
```

## 17.8 大纲 AI 辅助

每个节点提供：

```text
生成空白节点内容
根据上下文补全
提供三个方案
重写本章
检查节奏
检查因果
检查人物动机
检查与总纲偏离
```

AI 生成结果进入候选区。

用户可以：

- 接受全部；
- 只接受标题；
- 只接受冲突；
- 只接受章尾；
- 手动修改后接受；
- 保存为大纲分支。

## 17.9 大纲历史

历史支持：

- 整体大纲快照；
- 单个节点历史；
- 字段历史；
- 删除节点恢复；
- 拖动前后对比；
- AI 与手动来源标记。

## 17.10 从大纲创建章节

点击章纲的“开始章节”：

1. 检查章纲状态；
2. 创建空白 Chapter；
3. 关联章纲版本；
4. 用户可先手写正文；
5. 用户也可启动 Agent Workflow。

创建章节不强制调用 AI。

# 18. 记忆与事实页面

## 18.1 Tabs

```text
故事事件
正典事实
物品归属
最近摘要
```

---

## 18.2 故事事件

显示：

- 章节；
- 参与者；
- 地点；
- 时间；
- 结果；
- 重要度；
- 谁知道；
- 证据。

支持按人物、地点、章节和重要度筛选。

---

## 18.3 正典事实

状态：

- Candidate；
- Canon；
- Character Belief；
- Conflict；
- Deprecated。

候选事实支持批量：

- 接受；
- 拒绝；
- 编辑后接受。

接受操作必须显示来源证据。

---

## 18.4 唯一道具

时间线显示持有人变化。

冲突显示：

```text
青铜城行动章：同时检测到路明非和楚子航持有七宗罪剑匣
```

---

# 19. Skill 页面

## 19.1 三层

```text
基础 Skill
项目 Skill
章节 Skill
```

---

## 19.2 可视化规则编辑

规则按组：

- 视角；
- 青春校园感；
- 都市奇幻氛围；
- 节奏；
- 对话；
- 描写；
- 战斗；
- 信息揭示；
- 禁用表达；
- 章尾；
- 示例。

MVP 同时提供高级 Markdown 编辑模式。

---

## 19.3 合成预览

显示：

- 最终值；
- 来源层；
- 被覆盖值；
- 冲突；
- Token；
- 是否阻止生成。

---

# 20. 审查中心

## 20.1 问题列表

字段：

- 严重度；
- 类型；
- 章节；
- 原文位置；
- 状态；
- 是否阻止提交。

筛选：

- 人物；
- 血统与言灵；
- 炼金道具；
- 时间；
- 角色知识；
- 组织关系；
- 文风；
- 严重度。

---

## 20.2 问题详情

```text
问题
当前正文证据
历史证据
规则说明
Reviewer 解释
建议修改
```

操作：

- 接受建议；
- 跳转正文；
- 忽略一次；
- 标记误报；
- 请求局部修订。

BLOCKER 的“忽略”需要填写理由。

---

## 20.3 正文高亮

ReviewMark 仅是编辑器装饰，不修改正文格式。

点击高亮同步右侧问题。

---

# 21. 版本页面

## 21.1 版本列表

显示：

- 版本号；
- 作者；
- 来源；
- 时间；
- 字数；
- Workflow；
- 摘要；
- 费用。

---

## 21.2 Diff

MVP 使用段落级 Diff：

- 新增；
- 删除；
- 修改；
- 段落移动。

长章节不做每字符彩色 Diff，避免噪声和性能问题。

---

## 21.3 恢复

点击恢复：

1. 查看影响；
2. 输入确认；
3. 后端创建新版本；
4. 不删除历史版本；
5. 刷新事实与状态提示。

---

# 22. 模型与费用页面

## 22.1 模型配置

展示后端返回能力：

- 模型名；
- Planner/Writer/Extractor/Reviewer；
- Thinking；
- Reasoning Effort；
- 最大输出；
- 超时；
- 重试；
- 支持/不支持参数。

DeepSeek 不支持的参数置灰：

```text
presence_penalty
frequency_penalty
```

前端不自行判断模型能力，使用后端能力矩阵。

---

## 22.2 费用

图表：

- 日费用；
- 章节费用；
- Agent 费用；
- 输入/输出 Token；
- Cache Hit/Miss；
- 模型对比。

所有价格和费用来自后端。

---

## 22.3 预算

设置：

- 单章上限；
- 每日上限；
- 项目累计提醒；
- 高推理模式确认。

---

# 23. 全局搜索与命令面板

快捷键：

```text
Cmd/Ctrl + K
```

可搜索：

- 页面；
- 人物；
- 世界书；
- 章节；
- 事件；
- 事实；
- Skill；
- 命令。

命令：

```text
创建章节
打开当前章
开始写前预检
查看未解决 BLOCKER
切换主题
```

危险操作不直接在命令面板执行。

---

# 23.1 项目级查找、替换与改名

参考成熟写作工具，提供跨项目查找和替换。

范围：

```text
正文
人物
世界书
大纲
Skill
事件备注
```

模式：

```text
查找
逐个替换
全部替换
仅添加为别名
大小写敏感
完整词
正则高级模式
```

执行“全部替换”前必须：

- 预览命中数量；
- 按资产类型分类；
- 展示上下文；
- 排除误命中；
- 创建项目快照；
- 允许撤销。

人物或地点改名默认推荐：

```text
修改主名称
+ 保留旧名为别名
```

避免历史正文失去追踪。

# 26.1 对话领域对象

```ts
interface ConversationSummary {
  id: string
  projectId: string
  title: string
  purpose: ConversationPurpose
  pinned: boolean
  archived: boolean
  lastMessageAt: string
  preview: string
  activeCanvas?: CanvasReference
}

interface ChatMessage {
  id: string
  conversationId: string
  role: 'user' | 'assistant' | 'tool' | 'system'
  content: MessageContent[]
  status: 'pending' | 'streaming' | 'completed' | 'failed'
  createdAt: string
  parentMessageId?: string
  usage?: UsageSummary
}

interface CanvasReference {
  type: string
  assetId: string
  assetVersion?: number
  selection?: {
    paragraphKeys?: string[]
    fieldKey?: string
    from?: number
    to?: number
  }
}
```

---

# 26.2 对话 API

建议后端补充：

```http
POST   /api/projects/{projectId}/conversations
GET    /api/projects/{projectId}/conversations
GET    /api/conversations/{conversationId}
PATCH  /api/conversations/{conversationId}
DELETE /api/conversations/{conversationId}

POST   /api/conversations/{conversationId}/messages
GET    /api/conversations/{conversationId}/messages
POST   /api/messages/{messageId}/retry
POST   /api/messages/{messageId}/continue
POST   /api/messages/{messageId}/save-as-candidate

GET    /api/conversations/search?q=
POST   /api/conversations/{conversationId}/archive
POST   /api/conversations/{conversationId}/pin
```

---

# 26.3 Chat SSE

聊天消息和章节 Workflow 使用不同事件命名空间：

```text
chat.message.delta
chat.message.completed
chat.tool.started
chat.tool.completed
chat.writing_block.created
chat.canvas.open
chat.error

workflow.step
workflow.text.delta
workflow.completed
```

前端不得混淆普通聊天流和正式章节工作流。

---

# 26.4 搜索

搜索范围：

- 会话标题；
- 用户消息；
- AI 消息；
- 人物；
- 世界书；
- 大纲；
- 章节正文；
- 事件和事实。

默认搜索结果分组显示。

Canvas 内部内容是否进入全局搜索，取决于它是否已保存为正式资产；纯会话 Writing Block 作为会话内容搜索。

# 24. 前端状态设计

## 24.1 Pinia Store

```text
authStore
uiStore
workspaceStore
editorSessionStore
workflowUiStore
notificationStore
preferencesStore
```

---

## 24.2 Query Key

```ts
['projects']
['project', projectId]
['chapters', projectId]
['chapter', chapterId]
['chapter-versions', chapterId]
['characters', projectId]
['character', characterId]
['worldbook', projectId, filters]
['workflow', runId]
['review-issues', projectId, filters]
['usage', projectId, range]
```

---

## 24.3 Mutation 后刷新

示例：

```text
确认资产
→ invalidate project assets
→ invalidate context preview
→ mark running workflow stale
```

---

## 24.4 乐观更新

允许：

- 项目重命名；
- 收藏；
- UI 排序；
- 普通标签。

不允许乐观更新：

- 正典确认；
- 章节提交；
- 状态恢复；
- 候选事实接受；
- 预算扣费。

---

# 25. API Client

## 25.1 OpenAPI

优先方式：

```text
后端生成 OpenAPI 3.1
→ 前端 CI 拉取或读取锁定文件
→ 生成 TypeScript 类型
```

仓库保存：

```text
src/api/generated/schema.d.ts
docs/api-contract.md
```

后端未提供 OpenAPI 前，使用受版本控制的 `openapi.yaml`，不得手写大量重复 DTO。

---

## 25.2 Client 中间件

负责：

- Base URL；
- Cookie/Credentials；
- Trace ID；
- Idempotency Key；
- 401 刷新；
- Problem Details；
- AbortController；
- 超时；
- 统一日期解析。

---

## 25.3 Problem Details

映射：

```ts
interface ApiProblem {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  code?: string
  fieldErrors?: Record<string, string[]>
  traceId?: string
}
```

用户界面显示可理解信息，调试区显示 Trace ID。

---

# 26. 可访问性

## 26.1 目标

MVP 以 WCAG 2.2 AA 为目标。

---

## 26.2 键盘

必须支持：

- Tab 导航；
- Tree View 方向键；
- Tabs 左右键；
- Dialog 焦点锁定；
- Escape 关闭可关闭浮层；
- 编辑器快捷键；
- 命令面板；
- 跳过导航链接。

---

## 26.3 焦点

- Dialog 打开时焦点进入；
- Dialog 关闭后返回触发按钮；
- 路由切换后焦点移动到页面标题；
- SSE 完成不抢夺编辑器焦点；
- 错误提交后聚焦第一个错误字段。

---

## 26.4 动画

遵循：

```css
@media (prefers-reduced-motion: reduce)
```

降低：

- 流式光标动画；
- 页面转场；
- 图表动画；
- Loading 动画。

---

## 26.5 对比度

- 正文和界面达到 AA；
- 状态不只依赖颜色；
- Review 高亮在深浅主题均可辨认；
- 高对比度模式下保留边框和焦点。

---

# 27. 性能

## 27.1 目标

| 指标 | 目标 |
|---|---:|
| 首屏 JS Gzip | < 350 KB，不含编辑器懒加载 |
| 登录页 LCP | < 2.5 秒 |
| 工作台交互可用 | < 3.5 秒 |
| 编辑器输入延迟 | < 50 ms |
| 10 万字章节打开 | < 2 秒目标 |
| SSE Delta UI 刷新 | 50—100 ms 批处理 |
| 路由切换 | < 300 ms 感知目标 |

目标必须实测后再写入简历。

---

## 27.2 分包

懒加载：

- TipTap；
- ECharts；
- Cytoscape；
- Diff；
- 管理页面；
- 使用统计。

---

## 27.3 长列表

使用虚拟滚动：

- 章节列表；
- 世界书；
- 事件；
- 审查问题。

---

## 27.4 编辑器

- 大章节避免全量深度 Watch；
- 使用 `shallowRef` 保存 Editor；
- SSE 批量更新；
- Diff Worker；
- 字数统计节流；
- 自动保存防抖。

---

# 28. 安全

## 28.1 XSS

- TipTap 内容使用受控 Schema；
- 不允许任意 HTML；
- 后端 HTML 进入 DOM 前净化；
- 不使用 `v-html` 渲染未净化内容；
- 外部链接增加安全属性；
- CSP 禁止任意内联脚本。

---

## 28.2 Token

推荐：

- HttpOnly；
- Secure；
- SameSite；
- 同源 Cookie。

若使用 Bearer Token：

- 仅内存保存 Access Token；
- Refresh Token 使用 HttpOnly Cookie；
- 不把长期 Token 存入 LocalStorage。

---

## 28.3 SSE

- 不在 URL 放长期 JWT；
- 使用 Cookie 或短期 Ticket；
- Workflow ID 必须经过后端权限；
- 页面关闭时关闭 EventSource；
- 异常重连有次数和退避。

---

## 28.4 隐私

- 不在前端日志输出正文；
- Sentry/遥测默认不采集输入内容；
- 错误报告脱敏；
- IndexedDB 草稿提供清理入口；
- 退出登录清理本地草稿前提示用户。

---

# 29. 测试策略

## 29.1 单元测试

Vitest 覆盖：

- Skill 合成 ViewModel；
- SSE 去重；
- SSE Buffer；
- 费用格式化；
- 状态映射；
- API Problem 映射；
- ParagraphKey；
- 本地草稿恢复；
- 路由权限；
- Token 分配图数据。

---

## 29.2 组件测试

覆盖：

- Preflight Dialog；
- Workflow Stepper；
- Context Preview；
- Review Issue Card；
- Worldbook Activation Table；
- TipTap Toolbar；
- Version Diff；
- Cost Summary。

---

## 29.3 E2E

Playwright 场景：

1. 登录；
2. 创建项目；
3. 创建人物和世界书；
4. 创建章纲；
5. 开始工作流；
6. 查看上下文；
7. 接收 SSE；
8. 停止生成；
9. 恢复流；
10. 处理 BLOCKER；
11. 确认章节；
12. 查看版本；
13. 查看费用。

---

## 29.4 Mock

MSW 模拟：

- 正常 REST；
- 401；
- 403；
- 409；
- 422；
- 429；
- 500；
- SSE 正常；
- SSE 断流；
- SSE 重复事件；
- Workflow 已结束；
- Context Stale。

---

## 29.5 视觉回归

关键页面截图：

- Dashboard；
- 工作台；
- Preflight；
- Context Preview；
- 浅色/深色；
- 审查中心；
- 世界书调试器。

---

## 29.6 可访问性测试

使用：

- axe-core；
- Playwright；
- 键盘手工测试；
- 屏幕阅读器抽查。

---

# 30. Docker 与 Nginx

## 30.1 Dockerfile

```dockerfile
FROM node:24-alpine AS builder

WORKDIR /app

RUN corepack enable

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget -qO- http://localhost/healthz || exit 1
```

---

## 30.2 Nginx

要求：

- SPA Fallback；
- `/api` 代理后端；
- `/api/workflows/*/events` 关闭代理缓冲；
- SSE 增加长超时；
- 静态资源长期缓存；
- `index.html` 不长期缓存；
- 安全 Header；
- Gzip/Brotli 视环境启用。

参考：

```nginx
location /api/workflows/ {
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 3600s;
    proxy_set_header Connection "";
}

location /api/ {
    proxy_pass http://backend:8080;
}

location /assets/ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}

location / {
    try_files $uri $uri/ /index.html;
}
```

---

## 30.3 Compose

前端服务加入后端 Compose 网络：

```yaml
frontend:
  build:
    context: ./storyweaver-frontend
  ports:
    - "80:80"
  depends_on:
    backend:
      condition: service_healthy
  networks:
    - backend
```

生产环境后端不必暴露公网端口，由 Nginx 同源代理。

---

# 31. CI

GitHub Actions：

```text
checkout
→ setup-node 24
→ corepack enable
→ pnpm install --frozen-lockfile
→ lint
→ typecheck
→ unit tests
→ build
→ Playwright
→ accessibility
→ Docker build
→ image scan
```

普通 CI 使用 Mock API。

与真实后端联调的 E2E Job：

- 手动触发；
- 启动 Docker Compose；
- 创建测试数据；
- 不调用真实 DeepSeek，后端使用 Stub Profile。

---

# 32. 前后端契约补充

为完整支持前端，后端应明确或补充：

## 32.1 OpenAPI

提供：

```text
/v3/api-docs
```

或在仓库保存锁定的 OpenAPI 文件。

---

## 32.2 章节草稿

需要明确：

- 草稿保存 API；
- `baseVersion`；
- `contentHash`；
- ParagraphKey；
- 编辑冲突响应；
- 自动保存是否创建版本。

建议自动保存只更新工作草稿，不创建正式 ChapterVersion。

---

## 32.3 SSE 鉴权

后端必须在 Cookie 或短期 Ticket 中选择一种，文档化：

- 创建方式；
- 有效期；
- 重连；
- Last Event ID；
- 权限错误。

---

## 32.4 Context Preview

后端应返回前端可解释字段：

```text
sourceType
sourceId
sourceVersion
activationReason
tokenCount
hardRule
removable
dropReason
```

---

## 32.5 能力矩阵

后端返回模型参数支持情况，前端不硬编码 DeepSeek 规则。

---

## 32.6 手动资产编辑 API

后端需要为人物、世界书和大纲提供细粒度更新，而不仅是整对象覆盖。

建议接口能力：

```http
PATCH /api/characters/{characterId}
POST  /api/characters/{characterId}/fields
PUT   /api/characters/{characterId}/fields/{fieldId}
POST  /api/characters/{characterId}/progressions
GET   /api/characters/{characterId}/mentions
GET   /api/characters/{characterId}/history
POST  /api/characters/merge

PATCH /api/worldbook-entries/{entryId}
POST  /api/worldbook-entries/{entryId}/fields
POST  /api/worldbook-entries/{entryId}/progressions
POST  /api/worldbook/reorder
POST  /api/worldbook/move
GET   /api/worldbook-entries/{entryId}/mentions
GET   /api/worldbook-entries/{entryId}/history

PATCH /api/outlines/{nodeId}
POST  /api/outlines/{nodeId}/children
POST  /api/outlines/reorder
POST  /api/outlines/move
POST  /api/outlines/{nodeId}/split
POST  /api/outlines/merge
GET   /api/outlines/{nodeId}/history
```

所有更新返回：

```text
新版本
乐观锁版本
影响的 Context Packet
受影响章节数量
是否需要重新审查
```

## 32.7 字段级 AI 候选 API

建议：

```http
POST /api/ai-suggestions/field
POST /api/ai-suggestions/asset
POST /api/ai-suggestions/{suggestionId}/accept
POST /api/ai-suggestions/{suggestionId}/reject
```

AI Suggestion 必须保存：

```text
目标资产
目标字段
原始值
候选值
用户要求
模型
Prompt 版本
Token
费用
状态
```

## 32.8 Mentions 与影响分析 API

建议：

```http
GET  /api/projects/{projectId}/mentions
POST /api/projects/{projectId}/rename-preview
POST /api/projects/{projectId}/replace
GET  /api/assets/{assetId}/impact
```

前端不得仅靠文本搜索决定正式替换范围。

# 33. Phase 实施计划

## Phase 0：脚手架与设计系统

实现：

- Vue；
- Vite；
- TypeScript；
- Router；
- Pinia；
- TanStack Query；
- Element Plus；
- CSS Token；
- 主题；
- ESLint；
- Vitest；
- Playwright；
- Dockerfile；
- Nginx；
- CI。

验收：

```text
pnpm lint 通过
pnpm typecheck 通过
pnpm test:unit 通过
pnpm build 通过
Docker 镜像可启动
/healthz 可访问
```

---

## Phase 1：Auth、ChatGPT 式 App Shell 和对话侧边栏

实现：

- 登录；
- 注册；
- 路由保护；
- 项目列表；
- 新建项目；
- ChatGPT 式 App Shell；
- 新对话；
- 会话列表、固定、归档和搜索；
- 主题；
- 全局错误。

验收：

- 未登录不能访问项目；
- 401 自动回登录；
- 项目列表 Loading/Empty/Error 完整；
- 移动端基础导航可用。

---

## Phase 2：资产页面

实现：

- 人物卡空白创建、逐字段编辑、自定义字段和 Progression；
- 世界书/世界树手动 CRUD、拖动、激活配置和历史；
- 大纲树/卡片手动编辑、增加、拆分、合并和拖动；
- Skill；
- 通用资产编辑器和字段级 AI 候选；
- 版本状态；
- 世界书激活调试器静态框架。

验收：

- CRUD 与后端联调；
- 409 冲突可呈现；
- Tree 键盘可操作；
- 表单错误定位正确。

---

## Phase 3：Chat、Composer、Writing Block 和 Canvas

实现：

- 消息列表；
- Composer；
- 工具菜单；
- 上下文 Chip；
- Chat SSE；
- Writing Block；
- Canvas 容器；
- Chat/Canvas 联动；
- 会话搜索；
- 消息重试和继续。

验收：

- 新会话可正常发送和流式接收；
- Chat 与 Workflow SSE 不混淆；
- Writing Block 可编辑并打开 Canvas；
- Canvas 选区可引用到 Chat；
- 会话搜索和归档可用；
- 长期 Token 不出现在 SSE URL。

---

## Phase 4：章节编辑器

实现：

- TipTap；
- ParagraphKey；
- 自动保存；
- IndexedDB 恢复；
- 字数；
- 查找替换；
- 未保存导航保护；
- 版本列表。

验收：

- 10 万字测试文本可编辑；
- 自动保存失败不丢失；
- 页面刷新可恢复本地草稿；
- 版本恢复通过后端创建新版本。

---

## Phase 5：工作流、Preflight 和 Context Preview

实现：

- Preflight Dialog；
- Context Preview；
- Token 分配；
- 场景计划；
- Workflow Stepper；
- 状态页。

验收：

- BLOCKER 禁止继续；
- Context 来源可查看；
- 硬规则不可移除；
- Context Stale 可处理。

---

## Phase 6：SSE Writer

实现：

- EventSource；
- 鉴权方案；
- Delta Buffer；
- 重连；
- Last Event ID；
- 停止生成；
- 断流草稿；
- 心跳。

验收：

- 重复事件不重复文本；
- 断线后恢复；
- 停止后不继续追加；
- 页面刷新后恢复 Workflow；
- 生成不阻塞页面其他操作。

---

## Phase 7：审查、候选事实和原子提交

实现：

- ReviewMark；
- 问题列表；
- 证据跳转；
- 局部修订；
- 候选事实确认；
- 最终审批；
- 提交结果。

验收：

- BLOCKER 显示并阻止提交；
- 修改正文后提示重新提取；
- 接受候选事实需要证据；
- 提交失败保持用户草稿。

---

## Phase 8：费用、图表和可观测 UI

实现：

- Token；
- Cache Hit/Miss；
- 费用；
- 工作流耗时；
- 模型能力；
- 预算；
- 图表；
- Trace ID 展示。

验收：

- 所有数字来自后端；
- 图表有文本替代；
- 不支持参数置灰；
- 预算超限操作被阻止。

---

## Phase 9：测试、可访问性和 Demo

实现：

- 完整 E2E；
- 视觉回归；
- axe；
- 性能测试；
- Demo 数据；
- README；
- 录屏脚本。

验收：

- 三分钟 Demo 可重复；
- 浅色和深色通过视觉回归；
- 键盘可完成核心流程；
- 简历数字有测试报告；
- Roadmap 不冒充已实现。

---

# 34. 前端 Roadmap

## V1.0

- 项目；
- 资产；
- 编辑器；
- 工作流；
- SSE；
- 上下文；
- 审查；
- 版本；
- 费用；
- Docker。

---

## V1.1

与后端导入版本对齐：

- 文件导入向导；
- 章节切分预览；
- 人物别名合并；
- 候选世界书批量确认；
- 伏笔生命周期；
- 章节影响分析；
- Git 导出。

---

## V1.5

- 连续 1—3 章任务队列；
- 重大剧情门禁；
- 滚动大纲；
- 自动局部修订；
- 章节分支；
- 模型降级可视化。

---

## V2.0

- 造书台；
- 长篇拆解；
- 短篇工作台；
- 多人评论；
- 权限；
- 关系图；
- 时间线；
- 全文搜索；
- Prompt/Context 调试器。

---

## V3.0

- Skill 市场；
- 声明式 Mod；
- 角色与场景图；
- 有声小说；
- 视觉小说导出；
- 平台发布适配。

Roadmap 功能未发布前只显示在文档中，不在稳定界面放置无效入口。

---

# 35. Definition of Done

```text
[ ] Node 24 LTS 和 pnpm 锁定
[ ] Vue/Router/Pinia/Query 架构清晰
[ ] 项目和资产页面可用
[ ] ChatGPT 式左侧会话栏可用
[ ] 项目内可创建、固定、归档和搜索会话
[ ] 中央 Chat 支持流式消息、重试和继续
[ ] Composer 支持工具、上下文引用和模式
[ ] Writing Block 可直接编辑并打开 Canvas
[ ] Chat 与 Canvas 可双向引用选区
[ ] 对话内容不会自动覆盖正式资产
[ ] 人物可以从空白手动创建并逐字段修改
[ ] 人物支持自定义字段、别名、Mentions 和 Progressions
[ ] 世界书可以完全手动创建、修改、排序和配置激活规则
[ ] 世界树支持层级拖动、移动、复制和归档
[ ] 大纲可以从空白手动建立卷、章、场景和节拍
[ ] 大纲支持树形、卡片和拖动排序
[ ] AI 生成只进入候选区，不覆盖确认内容
[ ] 字段和资产均有历史版本
[ ] TipTap 编辑器可稳定编辑长章节
[ ] ParagraphKey 可用于证据定位
[ ] 自动保存和 IndexedDB 恢复可用
[ ] Preflight 和 Context Preview 可用
[ ] SSE 流式生成、停止、重连可用
[ ] Workflow 页面可恢复
[ ] 审查问题可跳转正文
[ ] 候选事实需人工确认
[ ] 章节版本和恢复可用
[ ] Token、缓存和费用可视化
[ ] 深浅主题可用
[ ] 核心流程键盘可操作
[ ] Docker + Nginx 可部署
[ ] E2E 覆盖完整章节流程
[ ] README 区分 V1.0 与 Roadmap
```

---

# 36. 三分钟演示流程

1. 登录后进入项目，左侧显示项目会话；
2. 新建“青铜城行动规划”对话；
3. 在 Composer 中引用青铜城行动章章纲和楚子航；
4. 输入“检查楚子航是否已经知道青铜与火之王的真实身份”；
5. AI 通过工具状态块展示读取人物知识和历史事件；
6. 点击 AI 回复中的“打开人物卡”，右侧打开 Character Canvas；
7. 手动修正人物知识字段并保存候选；
8. 回到 Chat，要求生成三个章节方案；
9. 将选中方案打开为 Outline Canvas 并手动调整；
10. 启动正式章节 Workflow；
11. Preflight 和 Context Preview 展示世界书与 Token；
12. Writer 通过 SSE 写入 Chapter Canvas；
13. 选中一段，在 Chat 输入“保留剧情，缩短一半”；
14. Canvas 显示内联 Diff，用户接受；
15. Reviewer 标记知识越界并跳转证据；
16. 用户确认后原子提交；
17. 在会话侧边栏搜索“青铜城”，重新打开相关历史对话。

# 37. Codex 阶段报告格式

```markdown
## Phase N 完成摘要

### 新增文件
- ...

### 修改文件
- ...

### 页面与组件
- ...

### API 对接
- ...

### 状态与缓存
- ...

### 可访问性
- ...

### Docker 变更
- ...

### 运行命令

```bash
pnpm lint
pnpm typecheck
pnpm test:unit
pnpm test:e2e
pnpm build
```

### 测试结果
- TypeScript：
- 单元测试：
- E2E：
- 可访问性：
- 构建：

### 未完成项
- ...

### 风险
- ...

### 下一阶段建议
- ...
```

---

# 38. 给 Codex 的第一条实施指令

```text
阅读仓库根目录的
StoryWeaver_CODEX_前端设计文档_Vue_Docker_Roadmap_V1.0.md，
并同时阅读后端设计文档。

现在只实施 Phase 0，不要提前创建业务页面。

要求：
1. 使用 Node.js 24 LTS；
2. 使用 pnpm；
3. 使用 Vue 3.5.40、Vite 8.2.0、Vue Router 5.2.0；
4. 使用 TypeScript 严格模式；
5. 安装 Pinia、TanStack Vue Query、Element Plus；
6. 创建浅色和深色 Design Token；
7. 创建 Router、Store、API、Feature 目录；
8. 创建 Vitest、Playwright、ESLint 和类型检查；
9. 创建多阶段 Dockerfile 和 Nginx 配置；
10. 创建 GitHub Actions；
11. 执行 pnpm lint、pnpm typecheck、pnpm test:unit、pnpm build；
12. 构建并启动前端 Docker 镜像；
13. 编译或测试失败必须修复，不得跳过；
14. 按“Codex 阶段报告格式”汇报。
```

---

# 39. 参考资料

## Vue 生态

- Vue 3  
  https://vuejs.org/guide/introduction.html

- Vue Releases  
  https://github.com/vuejs/core/releases

- Vite  
  https://vite.dev/guide/

- Vite Releases  
  https://github.com/vitejs/vite/releases

- Vue Router  
  https://github.com/vuejs/router/releases

- Pinia  
  https://pinia.vuejs.org/introduction.html

- TanStack Vue Query  
  https://tanstack.com/query/latest/docs/framework/vue

## UI 与编辑器

- Element Plus  
  https://element-plus.org/

- Element Plus Releases  
  https://github.com/element-plus/element-plus/releases

- TipTap Vue 3  
  https://tiptap.dev/docs/editor/getting-started/install/vue3

- TipTap Releases  
  https://github.com/ueberdosis/tiptap/releases

- ECharts  
  https://echarts.apache.org/

- Cytoscape.js  
  https://js.cytoscape.org/

## 浏览器与可访问性

- MDN Server-Sent Events  
  https://developer.mozilla.org/docs/Web/API/Server-sent_events/Using_server-sent_events

- WAI-ARIA Tree View Pattern  
  https://www.w3.org/WAI/ARIA/apg/patterns/treeview/

- WAI-ARIA Tabs Pattern  
  https://www.w3.org/WAI/ARIA/apg/patterns/tabs/

- WAI-ARIA Dialog Pattern  
  https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/

- WAI-ARIA Toolbar Pattern  
  https://www.w3.org/WAI/ARIA/apg/patterns/toolbar/

## 测试与运行

- Vitest Releases  
  https://github.com/vitest-dev/vitest/releases

- Playwright Releases  
  https://github.com/microsoft/playwright/releases

- Node.js Releases  
  https://nodejs.org/en/about/previous-releases

- Docker Multi-stage Builds  
  https://docs.docker.com/build/building/multi-stage/

- Nginx  
  https://nginx.org/en/docs/

## AI 写作产品参考

- Novelcrafter Codex  
  https://www.novelcrafter.com/features/codex

- Novelcrafter Codex Entry Anatomy  
  https://www.novelcrafter.com/help/docs/codex/anatomy-codex-entry

- Sudowrite Story Bible Characters  
  https://docs.sudowrite.com/using-sudowrite/1ow1qkGqof9rtcyGnrWUBS/characters/a7tdE1ZB8KvAwMD3Mopwpd

- Sudowrite Worldbuilding  
  https://docs.sudowrite.com/using-sudowrite/1ow1qkGqof9rtcyGnrWUBS/worldbuilding/uc5NfWSz4x8Wm3S19LZeo8

- Sudowrite Outline  
  https://docs.sudowrite.com/using-sudowrite/1ow1qkGqof9rtcyGnrWUBS/outline/3owKyHXUm1bCdp41b2Npjk

- Sudowrite Find and Replace  
  https://docs.sudowrite.com/using-sudowrite/1ow1qkGqof9rtcyGnrWUBS/find-and-replace/7AWj7FsdXYahzxSqSNvXq5

- Plottr Features  
  https://plottr.com/features/

- Plottr Outline Scene Cards  
  https://docs.plottr.com/article/69-outline-scene-cards

- Plottr Timeline Overview  
  https://docs.plottr.com/article/54-timeline-overview

- NovelAI Lorebook Reference  
  https://aedial.github.io/novelaiUKB/en/Lorebook.html

## ChatGPT 交互参考（仅参考交互模式，不复制品牌）

- Projects in ChatGPT  
  https://help.openai.com/en/articles/10169521-chatgpt-projects

- Search Chat History  
  https://help.openai.com/en/articles/10056348

- Canvas in ChatGPT  
  https://help.openai.com/en/articles/9930697-what-is-canvas

- Introducing Canvas  
  https://openai.com/index/introducing-canvas/

- Writing Blocks and Code Blocks  
  https://help.openai.com/en/articles/20001246

- Shared Links FAQ  
  https://help.openai.com/en/articles/7925741-chatgpt-shared-links

## 《龙族》演示模板说明

- 《龙族Ⅰ·火之晨曦》由江南创作；
- 演示只使用角色、组织和世界观名称作为 UI 示例；
- 不内置原著章节正文、长段摘录或可替代购买原作的内容；
- 正式产品应提供“一键清空演示项目”和“创建原创项目”入口；
- 演示数据不得进入公共训练集或公开模板市场。
