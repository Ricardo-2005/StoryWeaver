# StoryWeaver / 文脉
## 创建项目页与全局 Skill 熔炉 Codex 更新文档（TXT / 手写文本熔炼版）

> 文档版本：V1.2 Text Distillation Edition  
> 更新范围：创建项目页、全局 Skill 工坊、Skill 熔炼、基础 Skill 契约、项目绑定、必要的前后端字段与测试  
> 参考界面：  
> - 图一：现有“基础信息 / 创作方向”分段卡片表单  
> - 图二：题材、读者、视角、篇幅的标签式选择区域  
> 核心目标：首次创建项目时快速确定小说定位并选择可验证的基础 Skill 契约；用户也能脱离具体项目，在全局 Skill 工坊中创建、熔炼、测试、版本化和复用 Skill。

---

# 1. 更新目标

将当前创建项目页：

```text
项目名称
类型文本框
项目简介
作者意图
……
```

调整为：

```text
项目名称
→ 小说题材
→ 目标读者
→ 作品视角
→ 篇幅长短
→ 故事构想
→ 可选高级设置
→ 创建项目
```

设计要求：

1. 仿照参考图二的横向标签选择形式；
2. 保留参考图一的白色卡片、编号分区和纵向表单结构；
3. 题材、目标读者、作品视角、篇幅均可手动选择；
4. 用户以后可以在项目设置中再次修改；
5. AI 不替用户自动确认这些基础字段；
6. 不破坏当前后端已经实现的项目创建流程；
7. 后端没有对应字段时，先检查现有 `Project DTO`、Entity 和 Migration，再做最小增量更新；
8. 不修改已经发布的 Flyway Migration。

---

# 2. 页面结构

## 2.1 页面整体

```text
┌─────────────────────────────────────────────────────────────┐
│ 创建新项目                                      保存草稿     │
│ 用几个简单选项确定作品的基础方向，之后都可以修改。           │
├─────────────────────────────────────────────────────────────┤
│ 01 基础信息                                                 │
│    项目名称                                                 │
│    项目简介                                                 │
├─────────────────────────────────────────────────────────────┤
│ 02 创作偏好                                                 │
│    小说题材  [言情] [现实情感] [悬疑] [惊悚] …… [更多⌄]      │
│    目标读者  [男频] [女频] [全频]                           │
│    作品视角  [第一人称] [第三人称]                           │
│    篇幅长短  [短篇小说] [长篇小说]                           │
├─────────────────────────────────────────────────────────────┤
│ 03 故事构想                                                 │
│    请输入故事核心、主角处境或开篇想法……                     │
│                                                   0 / 500   │
├─────────────────────────────────────────────────────────────┤
│ 04 基础 Skill 契约                                          │
│    [系统推荐] [选择已有] [不绑定] [前往 Skill 熔炉]           │
│    当前：长篇网文基础 v1.2 / 已验证                          │
├─────────────────────────────────────────────────────────────┤
│ ▸ 高级设置                                                  │
│    作者意图 / 当前焦点 / 世界硬规则 / 目标字数                │
├─────────────────────────────────────────────────────────────┤
│                              [取消] [创建项目并进入工作台]    │
└─────────────────────────────────────────────────────────────┘
```

---

## 2.2 第一区：基础信息

### 项目名称

- 必填；
- 长度：1—80；
- Placeholder：`例如：雾港来信`；
- 失焦后执行去除首尾空格；
- 不允许仅空白字符；
- 已存在同名项目时可以创建，但给出非阻塞提示。

### 项目简介

- 可选；
- 长度：0—300；
- Placeholder：`用一两句话说明这部作品讲什么。`；
- 与“故事构想”不同：
  - 项目简介用于项目列表展示；
  - 故事构想用于创作初始化和后续 Agent 上下文。

---

# 3. 第二区：创作偏好

## 3.1 小说题材

### 默认展示

```text
言情
现实情感
悬疑
惊悚
科幻
武侠
脑洞
太空歌剧
赛博朋克
游戏
仙侠
历史
玄幻
更多
```

### 交互

- 单选；
- 初始不强制预选题材；
- 必须选择后才能创建；
- 选中项使用浅绿色背景、绿色边框和深色文字；
- 未选中项使用白色背景和浅灰边框；
- Hover、Focus、Selected 状态必须区分；
- 支持左右方向键在选项之间切换；
- 支持 `Space` 或 `Enter` 选择；
- 支持自动换行，不出现横向溢出。

### “更多”

点击“更多”展开：

```text
都市
校园
青春
家庭
职场
商战
军事
战争
末世
无限流
克苏鲁
推理
奇幻
西幻
轻小说
同人
自定义
```

选择“自定义”后显示输入框：

```text
自定义题材名称，最多 20 字
```

自定义题材需要同时保存：

```text
genre = CUSTOM
customGenre = 用户输入
```

### 枚举建议

```ts
type ProjectGenre =
  | 'ROMANCE'
  | 'REALISTIC_EMOTION'
  | 'MYSTERY'
  | 'THRILLER'
  | 'SCIENCE_FICTION'
  | 'WUXIA'
  | 'HIGH_CONCEPT'
  | 'SPACE_OPERA'
  | 'CYBERPUNK'
  | 'GAME'
  | 'XIANXIA'
  | 'HISTORY'
  | 'FANTASY'
  | 'URBAN'
  | 'CAMPUS'
  | 'YOUTH'
  | 'FAMILY'
  | 'WORKPLACE'
  | 'BUSINESS'
  | 'MILITARY'
  | 'WAR'
  | 'APOCALYPSE'
  | 'INFINITE_FLOW'
  | 'CTHULHU'
  | 'DETECTIVE'
  | 'WESTERN_FANTASY'
  | 'LIGHT_NOVEL'
  | 'FAN_FICTION'
  | 'CUSTOM'
```

后端已有 `type` 或 `genre` 字段时，优先复用，不创建语义重复字段。

---

## 3.2 目标读者

选项：

```text
男频
女频
全频
```

建议默认：

```text
全频
```

枚举：

```ts
type TargetAudience = 'MALE' | 'FEMALE' | 'GENERAL'
```

说明：

- 该字段只是作品定位；
- 不限制人物、题材或内容；
- 后续可以修改；
- 不将“男频/女频”硬编码为固定写作规则；
- Agent 需要使用时，由后端转成可解释的上下文说明。

---

## 3.3 作品视角

选项：

```text
第一人称
第三人称
```

建议默认：

```text
第三人称
```

枚举：

```ts
type NarrativePerspective =
  | 'FIRST_PERSON'
  | 'THIRD_PERSON'
```

MVP 只展示两个主选项。

“第三人称限知、多视角、全知”等详细规则放到高级设置或 Skill 中，不在首次创建页堆叠。

---

## 3.4 篇幅长短

选项：

```text
短篇小说
长篇小说
```

建议默认：

```text
长篇小说
```

枚举：

```ts
type LengthType = 'SHORT_NOVEL' | 'LONG_NOVEL'
```

映射建议：

| 选择 | 默认章节目标 | 默认总字数目标 |
|---|---:|---:|
| 短篇小说 | 1—10 章 | 1 万—5 万字 |
| 长篇小说 | 50 章以上 | 用户后续填写 |

这些是初始化建议，不是强制限制。

---

# 4. 第三区：故事构想

字段名：

```text
故事构想
```

Placeholder：

```text
例如：一个普通外卖员发现自己拥有超能力，从此卷入一场外太空阴谋……
```

要求：

- 必填；
- 10—500 字；
- 实时显示字数；
- 支持多行；
- 高度约 140—180px；
- 不自动调用 AI；
- 创建后映射到作者意图或项目初始 Premise；
- 用户可以在高级设置中继续补充。

建议字段：

```ts
premise: string
```

如果后端已经存在：

```text
authorIntent
summary
description
foundation
```

Codex 必须先判断语义，再决定映射：

- `description`：项目列表简介；
- `premise`：故事构想；
- `authorIntent`：作者希望探索的主题、基调和边界。

不得把三个字段无差别合并。

---

# 5. 高级设置

首次创建页默认折叠。

点击后展开现有设计中的：

```text
作者意图
当前创作焦点
世界硬规则
目标字数
单章字数
```

要求：

- 全部可选；
- 保留现有表单数据；
- 展开和折叠不丢失输入；
- 高级设置不阻塞快速创建；
- 没有填写时由系统使用空值或项目默认值；
- 不自动生成世界规则。

标题：

```text
高级设置
可选，创建后仍可在项目设置中补充。
```

---

# 6. 创建按钮

按钮文案：

```text
创建项目并进入工作台
```

不要使用：

```text
开始创作
```

原因：创建项目后仍可能需要补充人物、世界书和大纲，不应暗示立即生成正文。

按钮状态：

- 表单无效：Disabled；
- 请求中：`正在创建项目…`；
- 成功：跳转项目首页或新项目对话页；
- 失败：保留全部输入；
- 重复点击：只发送一次请求；
- 支持幂等键。

成功后推荐路由：

```text
/projects/{projectId}
```

或实际项目 Dashboard 路由。

---

# 7. 与 ChatGPT 式前端的衔接

项目创建成功后进入项目内新会话页。

空会话欢迎内容：

```text
项目已创建。

接下来可以：
- 创建主角
- 搭建世界书
- 规划故事大纲
- 完善作者意图
- 直接打开章节编辑器
```

Composer 中自动绑定：

```text
当前项目
小说题材
目标读者
作品视角
篇幅
故事构想
```

这些信息属于项目基础上下文，但聊天回复不能直接改写它们。

用户在聊天中要求修改时：

```text
打开项目设置
→ 显示字段修改候选
→ 用户确认
→ 保存新版本
```

---

# 8. 前端实现建议

## 8.1 组件拆分

```text
CreateProjectPage.vue
CreateProjectForm.vue
ProjectBasicInfoSection.vue
ProjectPreferenceSection.vue
GenreSelector.vue
AudienceSelector.vue
PerspectiveSelector.vue
LengthSelector.vue
ProjectPremiseSection.vue
AdvancedProjectSettings.vue
CreateProjectActions.vue
```

通用单选组件：

```text
OptionChipGroup.vue
```

Props 建议：

```ts
interface OptionItem<T extends string> {
  label: string
  value: T
  disabled?: boolean
  description?: string
}

interface OptionChipGroupProps<T extends string> {
  modelValue?: T
  label: string
  options: OptionItem<T>[]
  required?: boolean
  wrap?: boolean
}
```

---

## 8.2 Element Plus

优先使用：

- `el-form`；
- `el-form-item`；
- `el-input`；
- `el-segmented` 或受控 `el-radio-group`；
- `el-collapse`；
- `el-button`；
- `el-dropdown`；
- `el-tooltip`。

题材数量多，不建议用单个固定宽度 `el-segmented` 强行横排。

推荐：

- 目标读者、作品视角、篇幅：`el-segmented`；
- 小说题材：自定义 `OptionChipGroup`，语义仍为单选组；
- “更多”：Dropdown + 扩展选项面板。

---

## 8.3 样式

```css
.project-option-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.project-option-chip {
  min-height: 36px;
  padding: 7px 18px;
  border: 1px solid var(--sw-border);
  border-radius: 6px;
  background: var(--sw-bg-surface);
  color: var(--sw-text-primary);
}

.project-option-chip[aria-checked='true'] {
  border-color: var(--sw-success);
  background: var(--sw-accent-soft);
  color: var(--sw-success);
}

.project-option-chip:focus-visible {
  outline: 2px solid var(--sw-accent);
  outline-offset: 2px;
}
```

移动端：

```css
@media (max-width: 768px) {
  .project-option-chip {
    flex: 1 1 calc(50% - 10px);
    justify-content: center;
  }
}
```

---

# 9. 表单 ViewModel

```ts
interface CreateProjectFormModel {
  name: string
  description: string

  genre?: ProjectGenre
  customGenre?: string
  targetAudience: TargetAudience
  narrativePerspective: NarrativePerspective
  lengthType: LengthType
  premise: string

  authorIntent?: string
  currentFocus?: string
  worldRules?: string[]
  baseSkillId?: string
  baseSkillVersionId?: string
  baseSkillBindingMode: 'RECOMMENDED' | 'SELECTED' | 'NONE'
  targetWordCount?: number
  chapterWordTarget?: number
}
```

默认值：

```ts
const initialForm: CreateProjectFormModel = {
  name: '',
  description: '',
  genre: undefined,
  customGenre: '',
  targetAudience: 'GENERAL',
  narrativePerspective: 'THIRD_PERSON',
  lengthType: 'LONG_NOVEL',
  premise: '',
  authorIntent: '',
  currentFocus: '',
  worldRules: [],
  baseSkillId: undefined,
  baseSkillVersionId: undefined,
  baseSkillBindingMode: 'RECOMMENDED',
}
```

---

# 10. 后端与 API 对齐

Codex 必须先读取：

- 实际 `NovelProject` Entity；
- Create Project Request；
- Project Response；
- Flyway；
- Controller；
- 前端 API Client；
- OpenAPI。

## 10.1 推荐请求

仅在后端语义一致且字段确实需要时使用：

```json
{
  "name": "雾港来信",
  "description": "一部现代幻想悬疑长篇。",
  "genre": "MYSTERY",
  "customGenre": null,
  "targetAudience": "GENERAL",
  "narrativePerspective": "THIRD_PERSON",
  "lengthType": "LONG_NOVEL",
  "premise": "一个普通外卖员发现自己拥有超能力，从此卷入一场外太空阴谋。",
  "authorIntent": "",
  "currentFocus": "",
  "worldRules": [],
  "targetWordCount": null,
  "chapterWordTarget": null
}
```

## 10.2 最小后端更新原则

### 情况 A：后端已有等价字段

只修改前端映射，不新增数据库列。

### 情况 B：后端有 `settings` JSONB

可以把偏好字段保存到明确的 Project Settings Value Object 中，但不得在前端随意拼匿名 JSON。

### 情况 C：后端没有字段且需要查询和约束

新增：

```text
genre
custom_genre
target_audience
narrative_perspective
length_type
premise
```

要求：

- 新增 Flyway Migration；
- 不修改旧 Migration；
- Java 使用 Enum；
- 数据库存字符串；
- 兼容旧项目；
- 为旧项目设置合理默认值或允许 NULL；
- Response 返回完整字段；
- OpenAPI 同步更新。

---

# 11. 数据迁移建议

如果需要新增列：

```sql
ALTER TABLE novel_project
    ADD COLUMN genre VARCHAR(40),
    ADD COLUMN custom_genre VARCHAR(40),
    ADD COLUMN target_audience VARCHAR(20),
    ADD COLUMN narrative_perspective VARCHAR(30),
    ADD COLUMN length_type VARCHAR(20),
    ADD COLUMN premise VARCHAR(500);
```

旧项目兼容：

```text
target_audience = GENERAL
narrative_perspective = THIRD_PERSON
length_type = LONG_NOVEL
genre 和 premise 暂时允许 NULL
```

新项目由应用层要求 `genre` 和 `premise` 必填。

是否设置数据库 `NOT NULL`，由 Codex 根据现有数据和迁移策略决定，不得直接导致旧数据迁移失败。

---

# 12. 校验规则

| 字段 | 规则 |
|---|---|
| name | 必填，1—80 |
| description | 0—300 |
| genre | 新建项目必填 |
| customGenre | genre=CUSTOM 时必填，1—20 |
| targetAudience | 必填 |
| narrativePerspective | 必填 |
| lengthType | 必填 |
| premise | 必填，10—500 |
| authorIntent | 0—3000 |
| currentFocus | 0—2000 |
| worldRules | 单条 1—500，数量按现有后端限制 |
| targetWordCount | 正整数 |
| chapterWordTarget | 正整数 |

前后端校验规则保持一致。

后端校验是最终可信边界。

---

# 13. 可访问性

互斥选项必须使用真实单选语义：

```html
<div role="radiogroup" aria-labelledby="genre-label">
  <button role="radio" aria-checked="true">悬疑</button>
</div>
```

或者直接使用原生 Radio/Element Plus Radio Group。

要求：

- 组有可见 Label；
- `Tab` 进入当前选择项；
- 左右方向键切换；
- `Space` 选择；
- Focus Ring 清晰；
- 不只通过颜色表示选中；
- 触控区域不小于设计系统约定；
- 错误信息通过 `aria-describedby` 关联；
- 自动聚焦第一个错误字段。

---

# 14. 状态与异常

必须覆盖：

```text
初始
填写中
校验失败
创建中
创建成功
401
403
409
422
429
网络错误
服务器错误
```

409：

- 如果是乐观锁问题，显示冲突说明；
- 如果是业务重复，显示后端 Problem Details；
- 不用通用“创建失败”掩盖具体原因。

422：

- 映射字段错误；
- 自动滚动并聚焦第一个错误字段。

网络失败：

- 保留表单；
- 提供重试；
- 可将未提交内容保存到 `sessionStorage` 或现有草稿机制；
- 成功创建后清理临时草稿。

---

# 15. 测试

## 15.1 前端单元测试

至少覆盖：

- 题材单选；
- “更多”展开；
- 自定义题材校验；
- 默认目标读者；
- 默认第三人称；
- 默认长篇；
- 故事构想字数；
- 高级设置展开不丢数据；
- 无效表单禁止提交；
- 请求 Payload 映射；
- 创建成功跳转；
- API 失败保留输入。

## 15.2 组件测试

覆盖：

- `OptionChipGroup`；
- 键盘选择；
- Focus；
- Error；
- Disabled；
- 自动换行；
- 移动端布局。

## 15.3 E2E

流程：

1. 打开创建项目页；
2. 输入项目名称；
3. 选择“悬疑”；
4. 选择“全频”；
5. 选择“第三人称”；
6. 选择“长篇小说”；
7. 输入故事构想；
8. 展开高级设置；
9. 填写作者意图；
10. 创建项目；
11. 跳转项目工作台；
12. 项目设置中能正确回显；
13. 刷新后数据仍存在。

另测：

- 未选题材；
- 故事构想不足 10 字；
- 自定义题材为空；
- 后端 422；
- 重复点击提交；
- 网络中断。

## 15.4 后端测试

字段新增时覆盖：

- Request Validation；
- Enum 持久化；
- 旧项目兼容；
- 创建后查询回显；
- 修改项目偏好；
- Flyway；
- OpenAPI；
- 项目所有权。

---

# 16. 验收标准

```text
[ ] 创建项目页保留原有卡片分区视觉
[ ] 新增小说题材标签单选
[ ] 新增目标读者单选
[ ] 新增作品视角单选
[ ] 新增篇幅长短单选
[ ] 新增 500 字故事构想
[ ] “更多”题材可以展开
[ ] 自定义题材可输入
[ ] 高级设置默认折叠
[ ] 所有字段可在创建后修改
[ ] AI 不自动覆盖用户选择
[ ] 前端与实际 Project DTO 对齐
[ ] 必要时使用新 Flyway，不修改旧 Migration
[ ] 键盘可完成全部选择
[ ] 手机端自动换行
[ ] 创建失败不丢输入
[ ] 创建成功进入项目工作台
[ ] 单元、组件、E2E 测试通过
[ ] README 或变更记录更新
```

---

# 17. Codex 更新提示词

将以下内容直接复制到 Codex：

```text
阅读当前仓库中的：

1. 根目录、frontend、backend 下的 AGENTS.md；
2. 最新版 StoryWeaver 前端设计文档；
3. 最新版后端设计文档；
4. 实际创建项目页面；
5. 实际 Project Controller、DTO、Entity、Flyway 和 OpenAPI；
6. 当前前端 API Client、表单和测试。

本次任务：更新“创建项目”页面，仿照仓库需求图片中的标签式选择形式，在原有卡片分区页面中增加小说题材、目标读者、作品视角、篇幅长短和故事构想。

只修改与创建项目和项目基础设置直接有关的代码，不重构无关页面，不实现新的 Agent、SSE 或 Roadmap 功能。

具体要求：

1. 保留现有“01 基础信息”的卡片样式。
2. 将原来的自由文本“类型”改为题材标签单选。
3. 新增“02 创作偏好”：
   - 小说题材：言情、现实情感、悬疑、惊悚、科幻、武侠、脑洞、太空歌剧、赛博朋克、游戏、仙侠、历史、玄幻和更多；
   - 目标读者：男频、女频、全频；
   - 作品视角：第一人称、第三人称；
   - 篇幅长短：短篇小说、长篇小说。
4. 题材默认不选；目标读者默认全频；视角默认第三人称；篇幅默认长篇。
5. “更多”可以展开额外题材，并支持自定义题材。
6. 新增“03 故事构想”多行输入，必填，10—500 字，显示实时字数。
7. 现有作者意图、当前焦点、世界规则和 Skill 移到默认折叠的“高级设置”，不能删除。
8. 按钮改为“创建项目并进入工作台”。
9. 所有选项创建后都能在项目设置中重新修改。
10. AI 生成内容只能作为候选，不能自动改变用户选择。
11. 使用 Element Plus 和当前设计 Token；题材使用可换行 OptionChipGroup，其余互斥字段可以使用 el-segmented 或 radio group。
12. 单选组支持 Tab、方向键、Space/Enter，选中状态不能只靠颜色。
13. API 以当前后端实际实现为准，不虚构字段。
14. 如果后端已有等价字段，只做映射；如果缺少且确实需要，新增最小字段和新的 Flyway Migration，不修改旧 Migration。
15. 保持旧项目兼容。
16. 前后端校验保持一致。
17. 更新 OpenAPI、API 类型、项目设置回显和测试。
18. 不修改现有设计文档和用户数据。

建议表单字段：

name
description
genre
customGenre
targetAudience
narrativePerspective
lengthType
premise
authorIntent
currentFocus
worldRules
baseSkillId
targetWordCount
chapterWordTarget

开始前先使用 rg 搜索当前实现和字段，输出简短实施计划，然后直接完成代码、测试和修复，不要只给建议。

完成后执行实际存在的命令：

前端：
cd frontend
pnpm lint
pnpm typecheck
pnpm test:unit
pnpm build
pnpm test:e2e（环境支持时）

后端字段发生变化时：
cd backend
./mvnw clean verify

根目录：
docker compose config

编译或测试失败必须修复，不得禁用测试。

最终汇报：

- 读取的设计文档；
- 修改的页面和组件；
- Project DTO/API 变化；
- 是否新增 Migration；
- 表单默认值；
- 创建后回显；
- 测试与构建结果；
- 与需求不同的内容；
- 未完成项。

现在开始更新创建项目页面。
```

---

# 18. 不在本次范围

```text
AI 自动生成完整项目
自动创建人物
自动创建世界书
自动生成大纲
立即生成第一章
模型参数弹窗
文件导入
项目模板市场
多人协作
```

这些能力后续可以从项目创建成功后的 Chat 工作台继续完成。
---

# 19. 本次 V1.1 核心更新

本版在原“选项式创建项目”基础上新增两项能力：

```text
创建项目页中的基础 Skill 契约选择
+
位于项目之外的全局 Skill 工坊 / Skill 熔炉
```

基础 Skill 不再是一段普通 Prompt，也不只存在于创建项目表单中。它必须是：

> 可读、可验证、可版本化、可回滚、可导入导出、可跨项目复用的行为契约。

用户即使尚未创建小说项目，也可以先进入 Skill 工坊创建或熔炼自己的写作 Skill。

---

# 20. 参考女娲 Skill 的“熔炼”方法

## 20.1 吸收的核心思想

女娲 Skill 的价值不在于收集语录，而在于把来源材料转化为可运行的认知框架，并经过交叉验证与场景测试。

StoryWeaver 将这一思想改造成写作 Skill 熔炼：

```text
来源材料
→ 多维提取
→ 原子规则
→ 交叉验证
→ 去重与冲突检测
→ Skill 契约候选
→ 场景测试
→ 用户确认
→ 发布版本
```

不允许把多个 Prompt 直接拼接后称为“熔炼”。

## 20.2 写作 Skill 的五层结构

| 女娲式提炼层次 | StoryWeaver 写作 Skill |
|---|---|
| 怎么说话 | 表达 DNA：句式、节奏、措辞、对话和描写密度 |
| 怎么想 | 叙事模型：信息差、人物弧、因果和场景功能 |
| 怎么判断 | 决策启发式：删留、转折、章尾和伏笔处理 |
| 什么不做 | 反模式：视角越界、重复解释、套式修辞 |
| 知道局限 | 诚实边界：来源不足时不臆造，不冒充精确作者风格 |

## 20.3 六维熔炼

写作材料进入六个维度：

```text
1. 叙事结构与因果
2. 人物塑造与决策
3. 表达 DNA
4. 节奏、场景和章尾
5. 反模式、失败案例与批评
6. 适用边界、风险和诚实边界
```

每个维度输出原子规则：

```ts
interface AtomicSkillRule {
  id: string
  dimension: SkillDimension
  statement: string
  rationale: string
  evidenceRefs: string[]
  confidence: number
  applicability: string[]
  exclusions: string[]
}
```

原子规则进入契约前必须满足：

- 有明确来源；
- 能指导具体决策；
- 有适用条件；
- 有禁用条件或反例；
- 不与高优先级硬规则冲突；
- 置信度达到阈值。

出现冲突时由用户选择，系统不能静默决定创作价值观。

---

# 21. 全局 Skill 工坊

## 21.1 全局入口

在项目之外的主导航新增：

```text
项目
Skill 工坊
导入
设置
```

建议路由：

```text
/skills
/skills/new
/skills/import
/skills/:skillId
/skills/:skillId/edit
/skills/:skillId/forge
/skills/:skillId/versions
/skills/:skillId/tests
```

该环境不要求存在当前项目。

## 21.2 Skill 工坊首页

```text
┌─────────────────────────────────────────────────────────────┐
│ Skill 工坊                          [空白创建] [开始熔炼] [导入]│
├─────────────────────────────────────────────────────────────┤
│ 搜索  类型  状态  来源  最近使用                             │
├─────────────────────────────────────────────────────────────┤
│ 我的基础 Skill                                              │
│ [长篇网文基础] [悬疑叙事] [对话自然化] [去AI味审查]           │
├─────────────────────────────────────────────────────────────┤
│ 最近熔炼                                                    │
│ 运行中 / 待确认 / 验证失败 / 已验证                          │
└─────────────────────────────────────────────────────────────┘
```

每张卡片显示：

- 名称；
- 类型；
- 版本；
- 状态；
- 验证分数；
- 适用题材；
- Token 估算；
- 最近更新时间；
- 项目绑定数量；
- 来源和许可状态。

操作：

```text
查看
编辑
复制
熔炼新版本
运行测试
导出
归档
```

## 21.3 Skill 作用域

```text
BUILT_IN
PRIVATE_GLOBAL
PROJECT_LOCAL
IMPORTED
```

- `BUILT_IN`：系统只读，复制后才能修改；
- `PRIVATE_GLOBAL`：用户跨项目复用；
- `PROJECT_LOCAL`：仅一个项目可见；
- `IMPORTED`：从外部 Skill Bundle 导入，默认不可信。

MVP 不建设公开 Skill 市场。

---

# 22. 基础 Skill 契约

## 22.1 契约必须回答的问题

```text
何时触发
接收什么输入
输出什么
有哪些前置条件
执行哪些步骤
使用哪些工具
哪些行为禁止
如何判断成功
失败如何恢复
何时终止
来源是什么
如何验证
```

## 22.2 与开放 Agent Skills 格式兼容

导出结构：

```text
skill-name/
├── SKILL.md
├── references/
├── assets/
├── tests/
└── LICENSE
```

最低 Frontmatter：

```yaml
---
name: longform-web-fiction
description: >
  为中文长篇网文提供章节规划、场景写作和基础审查契约。
  在用户进行长篇章节规划、写作或修订时使用。
license: Proprietary
compatibility: StoryWeaver 1.x
metadata:
  storyweaver-version: "1.0"
  contract-version: "1"
  skill-type: "FOUNDATION"
---
```

导出前必须校验：

- `name` 只使用小写字母、数字和连字符；
- `description` 同时说明“做什么”和“何时使用”；
- 详细材料放入 `references/`，按需加载；
- `SKILL.md` 保持精炼，避免把整份资料塞入上下文。

## 22.3 StoryWeaver 契约结构

```yaml
contract:
  identity:
    displayName: 长篇网文基础
    type: FOUNDATION
    version: 1.2.0
    status: VALIDATED

  scope:
    useWhen:
      - 长篇章节规划
      - 场景正文生成
      - 章节局部修订
    doNotUseWhen:
      - 学术论文
      - 法律文书

  inputs:
    required:
      - authorIntent
      - chapterOutline
      - canonContext
    optional:
      - recentText
      - reviewIssues
      - userInstruction

  outputs:
    planning: ChapterPlan
    writing: ChapterDraft
    review: ReviewFinding

  preconditions:
    - 章纲存在
    - 视角人物明确
    - 世界硬规则可用

  workflow:
    - 读取任务与正典
    - 识别本章信息增量
    - 规划场景
    - 执行写作
    - 自检反模式
    - 输出结果与不确定项

  narrativeModels:
    - 信息差逐步释放
    - 人物选择推动情节
    - 每场必须改变状态

  decisionHeuristics:
    - id: protect-viewpoint
      when: 当前视角无法知道信息
      then: 改为可观察证据或移至其他场景

  expressionDNA:
    sentenceRhythm: 长短句混合
    dialogueDensity: medium
    descriptionDensity: medium
    forbiddenPatterns:
      - 不是……而是……
      - 无意义总结句

  antiPatterns:
    - 重复解释同一设定
    - 连续大段上帝视角
    - 用旁白替人物做决定

  honestyBoundaries:
    - 来源不足时标记不确定
    - 不声称精确复刻特定在世作者
    - 不虚构未提供的正典事实

  toolPolicy:
    allowed:
      - query-canon
      - query-character-state
      - query-recent-events
    requiresConfirmation:
      - save-candidate-fact
    forbidden:
      - publish-content

  contextBudget:
    maxInstructionTokens: 5000
    referenceLoading: on-demand

  recovery:
    invalidOutput: 按输出 Schema 重试一次
    canonConflict: 停止并返回 BLOCKER
    missingContext: 请求补充或降级为候选建议

  termination:
    success:
      - 输出满足 Schema
      - 无 BLOCKER
      - 必须内容已覆盖
    failure:
      - 正典冲突无法解决
      - 必需上下文缺失

  provenance:
    sources: []
    generatedBy: MANUAL_OR_FORGE
    reviewedByUser: true

  evaluation:
    testSuiteId: longform-base-v1
    minimumScore: 85
```

## 22.4 必填契约段

```text
Identity
Description
Scope
Inputs
Outputs
Preconditions
Workflow
Constraints
AntiPatterns
HonestyBoundaries
Recovery
Termination
Provenance
Evaluation
```

缺少任一必填段，不允许状态变为 `VALIDATED`。

## 22.5 状态

```text
DRAFT
DISTILLING
WAITING_REVIEW
VALIDATING
VALIDATION_FAILED
VALIDATED
DEPRECATED
ARCHIVED
```

只有 `VALIDATED` 默认进入项目创建页推荐列表。用户选择草稿 Skill 时必须看到风险提示。

---

# 23. Skill 熔炉流程

## 23.1 MVP 只保留两种熔炼入口

本版 Skill 熔炼不从网页自动搜索，也不要求用户整理复杂资料包。

用户只需要二选一：

```text
A. 上传 TXT 文档
B. 直接写一段 / 粘贴一段自己的文字
```

两种输入最终都进入同一条“文本 → 写作规律 → Skill 契约”的熔炼流水线。

界面顶部：

```text
┌──────────────────────────────────────────────────────────────┐
│ 创建写作 Skill                                               │
│                                                              │
│  [上传 TXT 文档]       [粘贴 / 手写文字]                     │
└──────────────────────────────────────────────────────────────┘
```

MVP 暂不支持：

```text
PDF
DOCX
EPUB
网页 URL
自动联网搜集语料
音频 / 视频
ZIP Skill Bundle 作为熔炼素材
```

这些放入 Roadmap，避免第一版把“熔炼”做成复杂导入系统。

---

## 23.2 入口 A：上传 TXT 文档

### 上传区域

```text
┌──────────────────────────────────────────────────────────────┐
│              拖入 TXT 文件，或点击选择                       │
│                                                              │
│  支持一个或多个 .txt                                        │
│  建议使用自己写的小说、章节、写作笔记或写作规范              │
└──────────────────────────────────────────────────────────────┘
```

支持：

- 单个 TXT；
- 多个 TXT 一次熔炼；
- 拖拽上传；
- 点击上传；
- 删除单个文件；
- 调整文件顺序；
- 文件标题编辑；
- 上传前预览；
- 字数统计。

建议限制：

```text
单文件：≤ 5 MB
一次：≤ 20 个文件
总文本：≤ 20 MB
```

限制必须通过配置管理，不能散落在组件中硬编码。

### 编码

优先支持：

```text
UTF-8
UTF-8 BOM
GB18030 / GBK 兼容读取
```

处理建议：

```text
检测 BOM
→ 严格 UTF-8 解码
→ 失败后尝试 GB18030
→ 仍失败则要求用户重新选择编码
```

不得用乱码文本继续熔炼。

### 文件列表

```text
我的长篇_01.txt         8,421 字   UTF-8   ✓
我的长篇_02.txt        11,208 字   UTF-8   ✓
对话练习.txt            3,901 字   UTF-8   ✓
```

允许给每个文件设置素材标签：

```text
正文
对话
人物
描写
大纲
写作规范
其他
```

标签只是帮助模型理解来源，不自动决定 Skill 规则。

---

## 23.3 入口 B：粘贴 / 手写文字

用户可以直接在网页中输入自己写的文本。

界面：

```text
┌──────────────────────────────────────────────────────────────┐
│  把你自己写的文字放在这里                                   │
│                                                              │
│  可以是一段小说、一场对话、几段描写，或者你的写作原则。      │
│                                                              │
│                                                              │
│                                                    0 / 50000 │
└──────────────────────────────────────────────────────────────┘
```

支持：

- 手写；
- 粘贴；
- 多行文本；
- 自动保存草稿；
- 字数统计；
- 清空；
- 从剪贴板粘贴；
- 保存为本次熔炼素材。

建议：

```text
最低允许：200 字
少于 1000 字：显示“样本较少”提示
5000 字以上：更适合提取稳定的多维写作规律
```

这里的字数提示只是“证据充分度提示”，不能向用户保证文本越长 Skill 就一定越好。

### 短文本保护

如果用户只提供一小段文字，例如 300—800 字：

系统可以提炼：

- 句子节奏；
- 对话倾向；
- 描写方式；
- 高频结构；
- 局部语言习惯。

系统不能无依据断言：

- 完整人物塑造方法；
- 整部长篇节奏模型；
- 稳定的章尾策略；
- 完整叙事价值观。

这类规则必须标记：

```text
LOW_EVIDENCE
LOCAL_PATTERN
NEEDS_MORE_SAMPLES
```

---

## 23.4 熔炼前设置

用户只需补充少量信息：

```text
Skill 名称 *
Skill 类型 *
我希望重点学习什么（可选）
素材说明（可选）
```

Skill 类型：

```text
FOUNDATION
GENRE
TECHNIQUE
REVIEW
```

“希望重点学习什么”示例：

```text
重点学习我的对话节奏和人物说话方式。
重点学习悬疑铺垫，不要学习具体角色名和世界观。
重点学习短句和场景推进方式。
只提炼写作方法，不保留故事设定。
```

默认勾选：

```text
排除人物专有名词
排除具体地点和世界设定
排除剧情事实
只提炼可复用的写作方法
```

用户可以关闭这些排除项，但系统必须说明这可能导致 Skill 过拟合单个作品。

---

## 23.5 素材权利确认

开始熔炼前显示：

```text
[ ] 我确认上传 / 粘贴的文字由我创作，或我拥有用于本项目分析和生成 Skill 的权利。
```

未确认不得开始。

系统保存：

```text
ownershipStatement
confirmedAt
```

StoryWeaver 不主动将这些私有文本用于公共 Skill 或其他用户项目。

---

## 23.6 文本预处理

原始素材必须不可变保存一份 Source Snapshot。

处理流程：

```text
原始 TXT / 手写文本
→ 编码规范化
→ 换行规范化
→ 去除明显无意义空白
→ 段落切分
→ 文本块切分
→ 生成 Paragraph Key
→ 内容 Hash
→ 进入熔炼
```

禁止在进入熔炼前：

- 自动改写用户文字；
- 自动润色；
- 删除模型认为“不好”的段落；
- 把多份素材总结成一段后再提炼。

必须保留原始证据。

---

## 23.7 六维写作提取

参考女娲 Skill 的“多维提取 → 交叉验证”，但对象从“人物认知框架”改为“用户自己的写作方法”。

六个维度：

### 1. 叙事与因果

提取：

- 场景如何开始；
- 冲突如何进入；
- 信息如何递进；
- 因果如何连接；
- 场景结束时发生什么变化。

### 2. 人物与决策

提取：

- 人物通过什么展示性格；
- 是否喜欢用动作代替解释；
- 人物做决定前有什么铺垫；
- 对话是否推动关系变化。

### 3. 表达 DNA

提取：

- 长短句比例；
- 段落长度；
- 动词使用；
- 修辞倾向；
- 对话长度；
- 口语化程度；
- 高频转折方式；
- 描写密度。

### 4. 节奏、场景与章尾

提取：

- 场景推进速度；
- 描写和行动比例；
- 信息释放频率；
- 悬念位置；
- 章尾处理方式。

### 5. 反模式

寻找文本中稳定避免的写法，以及用户明确提供的禁用原则。

例如：

```text
不连续解释同一设定
少用总结句
不频繁使用“不是……而是……”
对话不长篇说教
```

不能因为某种表达在一个样本中没出现，就自动断言“作者禁止这种写法”。

### 6. 适用边界

判断规则证据来自：

```text
单段
单章
多章
多份独立文本
用户明确写作规范
```

并据此决定证据等级。

---

## 23.8 原子规则必须带证据

每一条熔炼出来的规则必须可回溯到用户文本。

```ts
interface AtomicSkillRule {
  id: string
  dimension: SkillDimension
  statement: string
  rationale?: string

  evidence: Array<{
    sourceId: string
    paragraphKey: string
    startOffset?: number
    endOffset?: number
    excerptHash: string
  }>

  evidenceLevel:
    | 'LOW'
    | 'MEDIUM'
    | 'HIGH'

  scope:
    | 'LOCAL_PATTERN'
    | 'REPEATED_PATTERN'
    | 'EXPLICIT_USER_RULE'

  confidence: number
}
```

UI 中点击规则：

```text
对话倾向：单次发言通常较短，依靠多轮来回推进冲突。

证据：
TXT 01 / 第 18 段
TXT 02 / 第 7 段
TXT 02 / 第 21 段
```

不要只显示模型结论。

---

## 23.9 去重与交叉验证

多份 TXT 时：

```text
文件 A 提取
文件 B 提取
文件 C 提取
→ 规则聚类
→ 查找重复证据
→ 查找反例
→ 判断是否稳定
```

证据越独立，规则可信度越高。

示例：

```text
规则：
“重要人物对话多使用短回合推进，很少一次讲完。”

支持：
A.txt × 4
B.txt × 3
C.txt × 5

反例：
C.txt × 1

结果：
REPEATED_PATTERN / HIGH
```

如果不同文本明显冲突：

```text
保留冲突
→ 展示给用户
→ 用户选择：
   保留两种条件规则
   只保留 A
   只保留 B
   删除此规则
```

不能自动“平均化”风格。

---

## 23.10 熔炼结果预览

熔炼结束后先进入“候选 Skill”，不直接发布。

页面：

```text
┌──────────────────────────────────────────────────────────────┐
│ Skill：我的长篇写作 v0.1                                    │
│ 状态：WAITING_REVIEW                                        │
├──────────────────────────────────────────────────────────────┤
│ 叙事模型        4 条                                         │
│ 人物决策        5 条                                         │
│ 表达 DNA       11 条                                         │
│ 节奏与章尾      6 条                                         │
│ 反模式          4 条                                         │
│ 诚实边界        3 条                                         │
├──────────────────────────────────────────────────────────────┤
│ [逐条审查] [运行测试] [重新熔炼] [保存草稿]                  │
└──────────────────────────────────────────────────────────────┘
```

每条规则：

```text
✓ 接受
✎ 修改
× 删除
? 查看证据
```

只有用户接受的规则才进入最终契约。

---

## 23.11 从文本生成 Skill 契约

确认规则后：

```text
已确认原子规则
→ 按契约 Section 编排
→ 生成 SKILL.md 候选
→ 契约校验
→ 场景测试
→ 用户确认
→ VALIDATED / DRAFT
```

生成内容仍遵守本设计第 22 节的基础 Skill Contract。

核心 `SKILL.md` 应保持精炼；大量证据、示例和详细分析放入：

```text
references/
```

这样 Agent 激活 Skill 时，不需要一次加载全部原始 TXT。

---

## 23.12 生成的 Skill 目录

```text
my-writing-skill/
├── SKILL.md
├── references/
│   ├── evidence-map.md
│   ├── narrative-patterns.md
│   ├── character-patterns.md
│   ├── expression-dna.md
│   ├── pacing-patterns.md
│   ├── anti-patterns.md
│   └── boundaries.md
├── tests/
│   └── test-cases.json
└── LICENSE
```

默认不把用户上传的完整原始 TXT 放进导出包。

如果用户主动选择：

```text
包含我的原始素材
```

才允许将原文作为私有参考一起导出。

---

## 23.13 Skill 验证

每个 Skill 至少运行：

```text
3 个典型场景
1 个冲突场景
1 个边缘场景
1 个超出证据范围场景
```

重点增加：

### 风格复现测试

不是比较“像不像某个作者”，而是检查：

- 是否遵守提取出的句式倾向；
- 是否保持对话密度；
- 是否遵守视角规则；
- 是否遵守反模式；
- 是否没有复制素材原句。

### 过拟合测试

给一个完全不同题材：

```text
用户素材：都市悬疑
测试：古代客栈冲突
```

好的 Skill 应迁移“方法”，而不是继续生成原文人物、地点和剧情。

### 诚实边界测试

素材只包含对话时，询问：

```text
“按照这个 Skill 的章尾方法写一个钩子。”
```

如果没有足够章尾证据，应说明该维度证据不足，而不是假装这是用户稳定写法。

---

## 23.14 熔炼状态机

```text
CREATED
→ SOURCE_READY
→ PREPROCESSING
→ EXTRACTING
→ CROSS_VALIDATING
→ WAITING_CONFLICT_RESOLUTION
→ BUILDING_CONTRACT
→ WAITING_REVIEW
→ VALIDATING
→ VALIDATED

异常：
FAILED
CANCELLED
VALIDATION_FAILED
```

---

## 23.15 MVP 输入边界

V1.2 明确：

### 支持

```text
.txt 文件
网页文本框手写
网页文本框粘贴
多个 TXT
TXT + 手写文字混合
```

### 暂不支持

```text
PDF
DOCX
EPUB
URL 抓取
自动搜索互联网语料
音频
视频
图片 OCR
第三方代码脚本
```

后续即使增加 PDF / DOCX，本质也必须先转成“可追溯文本 Source”，再进入同一套熔炼流水线。

---

# 24. 创建项目时选择基础 Skill

## 24.1 独立分区

在“03 故事构想”之后新增：

```text
04 基础 Skill 契约
```

选项：

```text
使用系统推荐
选择已有 Skill
不绑定基础 Skill
前往 Skill 熔炉创建
```

该项不再隐藏在高级设置里。

## 24.2 推荐逻辑

推荐只能基于明确选择：

```text
题材
目标读者
作品视角
篇幅
```

展示：

```text
推荐：长篇悬疑基础 v1.3
原因：悬疑 + 第三人称 + 长篇小说
验证：92 / 100
```

推荐不自动选中。

## 24.3 选择器

支持：

- 搜索；
- 类型过滤；
- 状态过滤；
- 查看契约摘要；
- 查看测试结果；
- 查看来源；
- 版本选择；
- Token 预览；
- 两个 Skill 比较。

项目保存：

```text
baseSkillId
baseSkillVersionId
baseSkillSnapshotHash
```

项目绑定特定版本，不自动跟随全局 Skill 更新。

## 24.4 升级

新版本发布后提示：

```text
基础 Skill 有新版本 v1.3
当前项目仍使用 v1.2
```

操作：

```text
查看 Diff
试运行
升级
保持旧版
创建项目派生版
```

禁止静默升级。

## 24.5 不绑定

允许不绑定。此时只使用系统最小安全契约：

```text
输出 Schema
正典保护
权限
费用门禁
错误恢复
```

---

# 25. 后端、API 与数据

## 25.1 核心表

```text
skill_definition
skill_version
skill_source
skill_atomic_rule
skill_forge_run
skill_forge_step
skill_test_case
skill_test_run
skill_test_result
project_skill_binding
```

项目绑定表至少保存：

```text
project_id
binding_type
skill_id
skill_version_id
snapshot_hash
priority
enabled
created_at
```

`skill_source` 至少保存：

```text
id
forge_run_id
source_type          # TXT / MANUAL_TEXT
title
material_type
original_filename
detected_encoding
content_hash
character_count
paragraph_count
ownership_confirmed
raw_content_storage_ref
created_at
```

`skill_atomic_rule` 必须保存：

```text
rule_text
dimension
scope
evidence_level
confidence
evidence_json
status
created_at
updated_at
```

不得只保存最终 `SKILL.md` 而丢失来源和证据链。

## 25.2 API

全局 Skill：

```http
POST   /api/skills
GET    /api/skills
GET    /api/skills/{skillId}
PATCH  /api/skills/{skillId}
POST   /api/skills/{skillId}/versions
GET    /api/skills/{skillId}/versions
POST   /api/skills/{skillId}/validate
GET    /api/skills/{skillId}/tests
POST   /api/skills/import
GET    /api/skills/{skillId}/export
POST   /api/skills/{skillId}/archive
```

Skill 熔炉：

```http
POST /api/skill-forge/runs
GET  /api/skill-forge/runs/{runId}
GET  /api/skill-forge/runs/{runId}/events

POST /api/skill-forge/runs/{runId}/sources/text
POST /api/skill-forge/runs/{runId}/sources/txt
GET  /api/skill-forge/runs/{runId}/sources
DELETE /api/skill-forge/runs/{runId}/sources/{sourceId}

POST /api/skill-forge/runs/{runId}/start
GET  /api/skill-forge/runs/{runId}/rules
PATCH /api/skill-forge/runs/{runId}/rules/{ruleId}
POST /api/skill-forge/runs/{runId}/resolve-conflicts
POST /api/skill-forge/runs/{runId}/generate-contract
POST /api/skill-forge/runs/{runId}/validate
POST /api/skill-forge/runs/{runId}/cancel
```

`/sources/text`：

```json
{
  "title": "我写的一段悬疑小说",
  "content": "……",
  "materialType": "PROSE",
  "ownershipConfirmed": true
}
```

`/sources/txt` 使用 `multipart/form-data`，后端负责：

- 文件类型校验；
- 大小限制；
- 编码检测；
- Hash；
- 原始 Source Snapshot；
- 段落切分；
- 不接受可执行文件。

项目绑定：

```http
GET    /api/projects/{projectId}/skill-bindings
POST   /api/projects/{projectId}/skill-bindings
PATCH  /api/projects/{projectId}/skill-bindings/{bindingId}
DELETE /api/projects/{projectId}/skill-bindings/{bindingId}
POST   /api/projects/{projectId}/skill-bindings/{bindingId}/upgrade
```

项目创建请求可包含：

```json
{
  "baseSkillVersionId": "uuid"
}
```

后端校验：

- Skill 为系统内置、当前用户所有或已授权；
- 版本存在；
- 状态允许绑定；
- Snapshot Hash 正确；
- 私有 Skill 不跨用户泄露。

## 25.3 安全

导入 Skill Bundle：

- 默认不可信；
- MVP 不执行第三方 `scripts/`；
- 防止 Zip Slip 和路径穿越；
- 限制文件大小和数量；
- 校验 YAML 与 Markdown；
- 显示工具权限；
- 用户确认后才能启用；
- 不默认抓取未授权完整小说；
- 脚本沙箱放入 Roadmap。

---

# 26. 替代原第 17 节的 Codex 更新提示词

以下提示词优先级高于原文第 17 节：

```text
阅读根目录、frontend、backend 的 AGENTS.md，最新前后端设计文档，以及当前 Skill 模块实现。

本次重点更新 Skill 熔炉。

Skill 熔炼的 MVP 输入只保留两种：

1. 上传一个或多个 .txt 文档；
2. 在网页中直接手写或粘贴一段用户自己的文字。

不要实现网页抓取、PDF、DOCX、EPUB、音频、视频或自动联网搜集语料。

一、前端

全局 Skill 工坊中新增“开始熔炼”。

熔炼页第一步提供两个 Tab：

[上传 TXT]
[粘贴 / 手写]

TXT：
- 支持拖拽和多文件；
- 显示文件名、字数、编码和状态；
- 可以删除和调整顺序；
- 支持 UTF-8 / UTF-8 BOM / GB18030；
- 解码失败必须提示，不能用乱码继续；
- 配置文件数量、单文件和总大小限制。

手写：
- 多行文本框；
- 实时字数；
- 自动保存草稿；
- 最低允许 200 字；
- 少于 1000 字显示“样本较少”提示；
- 不把字数提示描述为质量保证。

用户补充：
- Skill 名称；
- Skill 类型；
- 希望重点学习什么；
- 素材说明；
- 素材权利确认。

默认开启：
- 排除人物专有名词；
- 排除具体地点；
- 排除剧情事实；
- 只提炼可复用写作方法。

二、熔炼逻辑

TXT 和手写文本进入完全相同的流水线：

原始 Source Snapshot
→ 编码和文本规范化
→ 段落切分
→ 六维提取
→ 原子规则
→ 多来源交叉验证
→ 去重
→ 冲突检测
→ 用户逐条审查
→ 生成 Skill Contract
→ 测试
→ 发布版本

六维：

1. 叙事与因果；
2. 人物与决策；
3. 表达 DNA；
4. 节奏、场景与章尾；
5. 反模式；
6. 适用边界。

短文本只能提取有证据的局部模式。
如果文本没有章尾、人物弧或长篇结构证据，不得虚构这些规则。

三、证据链

每条 AtomicSkillRule 必须记录：

- sourceId；
- paragraphKey；
- excerptHash；
- evidenceLevel；
- scope；
- confidence。

UI 点击规则必须能查看来源段落。

多份 TXT 中重复出现的规则提高证据等级；出现反例时保留反例。
冲突规则必须让用户选择，不自动平均化。

四、候选规则审查

熔炼后进入 WAITING_REVIEW。

用户可以逐条：

- 接受；
- 编辑；
- 删除；
- 查看证据。

只有用户确认的规则才能进入最终 Skill 契约。

五、Skill 输出

生成：

skill-name/
  SKILL.md
  references/
    evidence-map.md
    narrative-patterns.md
    character-patterns.md
    expression-dna.md
    pacing-patterns.md
    anti-patterns.md
    boundaries.md
  tests/
    test-cases.json
  LICENSE

核心 SKILL.md 保持精炼。
大量证据放 references 中按需加载。

默认不把用户完整原始 TXT 放入导出包。
只有用户明确选择时才允许包含原始素材。

六、后端

复用现有 skill 模块并最小扩展。

SkillSource 至少支持：
- TXT；
- MANUAL_TEXT。

新增或调整接口：

POST /api/skill-forge/runs
POST /api/skill-forge/runs/{runId}/sources/text
POST /api/skill-forge/runs/{runId}/sources/txt
GET  /api/skill-forge/runs/{runId}/sources
DELETE /api/skill-forge/runs/{runId}/sources/{sourceId}
POST /api/skill-forge/runs/{runId}/start
GET  /api/skill-forge/runs/{runId}/rules
PATCH /api/skill-forge/runs/{runId}/rules/{ruleId}
POST /api/skill-forge/runs/{runId}/resolve-conflicts
POST /api/skill-forge/runs/{runId}/generate-contract
POST /api/skill-forge/runs/{runId}/validate
POST /api/skill-forge/runs/{runId}/cancel

TXT 上传使用 multipart/form-data。

原始 Source Snapshot 必须保留 content hash 和证据定位。
不要只保存最终 SKILL.md。

新增 Flyway 时禁止修改已发布 Migration。

七、安全

- 必须勾选素材权利确认；
- Skill 私有数据不能跨用户；
- 不自动用于其他用户；
- 不自动用于公共 Skill；
- 不执行上传文件；
- 只接受 .txt MIME / 扩展和内容检查；
- 防超大文件和资源耗尽；
- 文件名不能决定服务端路径；
- 不抓取未授权小说；
- 不把“像某作者”当作熔炼目标；
- 熔炼的是用户自己的可复用写作方法。

八、验证

Skill 至少测试：

3 个典型场景
1 个冲突场景
1 个边缘场景
1 个超出证据范围场景

增加“过拟合测试”：
换不同题材，检查 Skill 是否迁移写作方法而不是复制原素材的人名、地点和剧情。

增加“诚实边界测试”：
素材缺少某维度时，Skill 必须承认证据不足。

九、测试

前端覆盖：
- TXT 单文件；
- 多 TXT；
- TXT 删除和顺序；
- 编码错误；
- 手写输入；
- 短文本提示；
- 权利确认；
- 熔炼状态；
- 规则证据查看；
- 规则接受/编辑/删除；
- 冲突决议；
- Skill 输出预览。

后端覆盖：
- TXT 文件类型；
- 文件大小；
- UTF-8；
- GB18030；
- 解码失败；
- Source Hash；
- Paragraph Key；
- 用户权限；
- 原子规则证据；
- 状态机；
- 取消；
- Skill Contract；
- Flyway；
- OpenAPI；
- Testcontainers。

执行：

前端：
cd frontend
pnpm lint
pnpm typecheck
pnpm test:unit
pnpm build
pnpm test:e2e（环境支持时）

后端：
cd backend
./mvnw clean verify
./mvnw dependency:tree

根目录：
docker compose config

失败必须修复，不能禁用测试或删除断言。

最终汇报：
- 页面和路由；
- TXT / 手写输入实现；
- 文件限制与编码策略；
- 熔炼状态机；
- 证据链；
- API 和 Migration；
- 测试结果；
- 与设计差异；
- 未完成项。

现在开始更新。
```

---

# 27. 参考资料

- 仓颉 Skill：将书籍、长视频转写、播客、课程、访谈和长文蒸馏为原子化可执行 Skills；强调不是简单摘要  
  https://github.com/kangarooking/cangjie-skill

- 女娲 Skill：认知框架提炼、交叉验证、诚实边界与场景测试  
  https://github.com/alchaincyf/nuwa-skill

- Agent Skills 开放规范：目录结构、SKILL.md、Frontmatter 与渐进式加载  
  https://agentskills.io/specification

- Anthropic Agent Skills：可复用文件系统技能、按需加载及第三方 Skill 安全提示  
  https://platform.claude.com/docs/en/managed-agents/skills
