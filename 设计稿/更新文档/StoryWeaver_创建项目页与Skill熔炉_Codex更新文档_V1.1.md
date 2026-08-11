# StoryWeaver / 文脉
## 创建项目页与全局 Skill 熔炉 Codex 更新文档

> 文档版本：V1.1 Skill Contract Edition  
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

## 23.1 创建方式

```text
空白创建
从已有 Skill 派生
从写作材料熔炼
导入 SKILL.md
组合多个 Skill
```

## 23.2 输入来源

支持：

- 用户自己的写作规范；
- 用户拥有权利的小说片段；
- 公开写作教程；
- 编辑反馈；
- 章节评语；
- 禁用表达；
- 已有 Skill；
- 用户确认过的修改历史。

每份来源保存：

```text
sourceType
title
sourceUri
contentHash
license
ownershipStatement
createdAt
```

## 23.3 合成规则

```text
解析契约
→ 规则规范化
→ 同义去重
→ 冲突分组
→ 作用域过滤
→ 优先级决议
→ Token 裁剪
→ 候选契约
```

优先级：

```text
用户本次明确要求
> CHAPTER
> PROJECT
> FOUNDATION
> GENRE / TECHNIQUE
> BUILT_IN 默认
```

覆盖关系必须可解释。

## 23.4 验证

每个 Skill 至少运行：

```text
3 个典型场景
1 个冲突场景
1 个边缘场景
1 个超出能力范围场景
```

评分维度：

- 指令遵循；
- 风格一致；
- 叙事判断稳定；
- 反模式拦截；
- 诚实边界；
- 输出契约；
- Token 成本；
- 与其他 Skill 的冲突。

验证不通过不得发布。

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
POST /api/skill-forge/runs/{runId}/sources
POST /api/skill-forge/runs/{runId}/resolve-conflicts
POST /api/skill-forge/runs/{runId}/generate-contract
POST /api/skill-forge/runs/{runId}/cancel
```

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
阅读当前仓库中的根目录、frontend、backend AGENTS.md，最新前后端设计文档，以及当前 Project 和 Skill 实现。

本次更新包含：

1. 创建项目页新增题材、读者、视角、篇幅、故事构想；
2. 创建项目页新增独立“04 基础 Skill 契约”；
3. 在项目外新增全局 Skill 工坊与 Skill 熔炉；
4. 实现可版本化、可验证的基础 Skill Contract；
5. 项目绑定 Skill 的具体版本快照，禁止静默升级。

不要把 Skill 实现成一个普通字符串 Prompt，也不要只在创建项目页增加下拉框。

全局路由至少包括：

/skills
/skills/new
/skills/import
/skills/:skillId
/skills/:skillId/edit
/skills/:skillId/forge
/skills/:skillId/versions
/skills/:skillId/tests

Skill 熔炼参考女娲式流程，但改造成写作方法论：

材料收集
→ 六维提取
→ 原子规则
→ 交叉验证
→ 去重与冲突处理
→ 生成契约候选
→ 典型/冲突/边缘/未知场景测试
→ 用户确认
→ 发布版本

六维包括：

- 叙事结构与因果；
- 人物塑造与决策；
- 表达 DNA；
- 节奏、场景和章尾；
- 反模式与失败案例；
- 适用边界、风险和诚实边界。

基础 Skill 契约必须包含：

Identity
Scope
Inputs
Outputs
Preconditions
Workflow
NarrativeModels
DecisionHeuristics
ExpressionDNA
Constraints
AntiPatterns
HonestyBoundaries
ToolPolicy
ContextBudget
Recovery
Termination
Provenance
Evaluation

支持导入导出 Agent Skills 风格目录：

skill-name/
  SKILL.md
  references/
  assets/
  tests/
  LICENSE

项目创建页中，用户可：

- 使用系统推荐；
- 选择已有 Skill；
- 不绑定；
- 前往 Skill 熔炉创建。

推荐不得自动确认。项目保存 baseSkillVersionId 和 snapshotHash。新版本发布后只提示，不自动升级。

后端先复用已有 skill 模块；缺失时最小新增：

SkillDefinition
SkillVersion
SkillSource
AtomicSkillRule
SkillForgeRun
SkillTestCase
SkillTestResult
ProjectSkillBinding

新增 Flyway Migration，不修改已发布 Migration。

安全要求：

- 第三方 Skill 默认不可信；
- MVP 不执行 scripts；
- 防路径穿越；
- 校验 Frontmatter；
- 展示来源和许可；
- 不抓取未授权完整小说；
- 不精确模仿特定在世作者；
- AI 只生成候选；
- 用户不能访问他人的私有 Skill。

测试至少覆盖：

- 无项目时打开 Skill 工坊；
- Skill CRUD 和不可变版本；
- 熔炼状态机；
- 契约必填校验；
- 典型和边缘场景测试；
- 项目绑定权限；
- Snapshot Hash；
- 新版本不自动升级；
- 导入安全；
- 创建项目选择、推荐和不绑定；
- 项目设置正确回显。

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

失败必须修复，不得禁用测试或删除断言。

最终汇报：

- 全局路由和页面；
- Skill 契约；
- 熔炼流程；
- Project API 变化；
- Migration；
- 安全处理；
- 测试和构建结果；
- 与设计差异；
- 未完成项。

现在开始更新。
```

---

# 27. 参考资料

- 女娲 Skill：认知框架提炼、交叉验证、诚实边界与场景测试  
  https://github.com/alchaincyf/nuwa-skill

- Agent Skills 开放规范：目录结构、SKILL.md、Frontmatter 与渐进式加载  
  https://agentskills.io/specification

- Anthropic Agent Skills：可复用文件系统技能、按需加载及第三方 Skill 安全提示  
  https://platform.claude.com/docs/en/managed-agents/skills
