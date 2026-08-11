<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'

import { globalSkillsApi } from '@/api/endpoints/globalSkills'
import { projectsApi } from '@/api/endpoints/projects'
import type {
  AtomicSkillRuleResponse,
  ForgeMaterialType,
  ForgeRunResponse,
  ForgeSkillType,
  ForgeSourceResponse,
  ProjectResponse,
} from '@/api/types'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import { getSkillMeltTemplate, type SkillMeltTemplate } from '@/config/skillTemplateMap'
import { genreLabel } from '@/features/projects/projectOptions'
import { forgeLimits } from '@/features/skills/forgeLimits'

type InputMode = 'TXT' | 'MANUAL_TEXT'
type Stage = 'SOURCES' | 'REVIEW' | 'CONTRACT'

interface LocalFile {
  id: string
  file: File
  title: string
  detectedEncoding: string
  characterCount: number | null
  preview: string
  error: string | null
}

const draftKey = 'storyweaver:skill-forge-draft:v1.3'
const legacyDraftKey = 'storyweaver:skill-forge-draft:v1.2'
const inputMode = ref<InputMode>('TXT')
const stage = ref<Stage>('SOURCES')
const busy = ref(false)
const error = ref<unknown>(null)
const dragActive = ref(false)
const files = ref<LocalFile[]>([])
const run = ref<ForgeRunResponse | null>(null)
const sources = ref<ForgeSourceResponse[]>([])
const rules = ref<AtomicSkillRuleResponse[]>([])
const expandedEvidence = ref(new Set<string>())
const editingRuleId = ref<string | null>(null)
const editingStatement = ref('')
const validationScore = ref<number | null>(null)
const slugTouched = ref(false)
const projects = ref<ProjectResponse[]>([])
const focusCustomized = ref(false)
const descriptionCustomized = ref(false)
const pendingTemplate = ref<SkillMeltTemplate | null>(null)

const form = reactive({
  displayName: '',
  slug: '',
  skillType: '' as ForgeSkillType | '',
  materialType: '' as ForgeMaterialType | '',
  sourceProjectId: '',
  genre: '',
  learningFocus: '',
  materialDescription: '',
  manualTitle: '我的手写文本',
  manualText: '',
  excludeCharacterNames: true,
  excludeLocations: true,
  excludePlotFacts: true,
  reusableMethodsOnly: true,
  ownershipConfirmed: false,
})

const skillTypeOptions: Array<{ value: ForgeSkillType; label: string }> = [
  { value: 'FOUNDATION', label: '基础写作' },
  { value: 'GENRE', label: '题材方法' },
  { value: 'TECHNIQUE', label: '写作技法' },
  { value: 'REVIEW', label: '审查规则' },
]
const materialTypeOptions: Array<{ value: ForgeMaterialType; label: string }> = [
  { value: 'PROSE', label: '正文' },
  { value: 'DIALOGUE', label: '对话' },
  { value: 'CHARACTER', label: '人物' },
  { value: 'DESCRIPTION', label: '描写' },
  { value: 'OUTLINE', label: '大纲' },
  { value: 'WRITING_RULES', label: '写作规范' },
  { value: 'OTHER', label: '其他' },
]
const dimensionLabels: Record<AtomicSkillRuleResponse['dimension'], string> = {
  NARRATIVE: '叙事与因果',
  CHARACTER: '人物与决策',
  EXPRESSION: '表达 DNA',
  PACING: '节奏、场景与章尾',
  ANTI_PATTERN: '反模式',
  BOUNDARY: '适用边界',
}

const totalFileBytes = computed(() => files.value.reduce((sum, item) => sum + item.file.size, 0))
const hasManualText = computed(() => form.manualText.trim().length > 0)
const manualTextValid = computed(() => !hasManualText.value || form.manualText.length >= forgeLimits.minManualCharacters)
const sourceReady = computed(() => files.value.length > 0 || form.manualText.length >= forgeLimits.minManualCharacters)
const canCreateRun = computed(() => Boolean(
  form.displayName.trim()
  && /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(form.slug.trim())
  && form.skillType
  && form.materialType
  && form.ownershipConfirmed
  && sourceReady.value
  && manualTextValid.value
  && files.value.every(item => !item.error),
))
const unresolvedCount = computed(() => rules.value.filter(rule => rule.status === 'CANDIDATE' || rule.status === 'CONFLICT').length)
const acceptedCount = computed(() => rules.value.filter(rule => rule.status === 'ACCEPTED').length)
const rejectedCount = computed(() => rules.value.filter(rule => rule.status === 'REJECTED').length)

function suggestSlug(): void {
  if (slugTouched.value) return
  form.slug = form.displayName.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || 'my-writing-skill'
}

function saveDraft(): void {
  sessionStorage.setItem(draftKey, JSON.stringify({
    form,
    inputMode: inputMode.value,
    focusCustomized: focusCustomized.value,
    descriptionCustomized: descriptionCustomized.value,
  }))
}

function restoreDraft(): void {
  const raw = sessionStorage.getItem(draftKey) ?? sessionStorage.getItem(legacyDraftKey)
  if (!raw) return
  try {
    const draft = JSON.parse(raw) as {
      form?: Partial<typeof form>
      inputMode?: InputMode
      focusCustomized?: boolean
      descriptionCustomized?: boolean
    }
    if (draft.form) Object.assign(form, draft.form)
    if (draft.inputMode) inputMode.value = draft.inputMode
    focusCustomized.value = draft.focusCustomized ?? false
    descriptionCustomized.value = draft.descriptionCustomized ?? false
  } catch {
    sessionStorage.removeItem(draftKey)
    sessionStorage.removeItem(legacyDraftKey)
  }
}

restoreDraft()
watch(form, saveDraft, { deep: true })
watch(inputMode, saveDraft)
watch([focusCustomized, descriptionCustomized], saveDraft)

watch(
  [() => form.materialType, () => form.skillType, () => form.genre],
  ([materialTag, skillType], previous) => {
    if (!materialTag || !skillType) {
      pendingTemplate.value = null
      return
    }
    const recommended = getSkillMeltTemplate(materialTag, skillType, form.genre)
    const initialSelection = !previous?.[0] || !previous?.[1]
    if (initialSelection) {
      if (!focusCustomized.value) form.learningFocus = recommended.focus
      if (!descriptionCustomized.value) form.materialDescription = recommended.description
      return
    }
    if (!focusCustomized.value) form.learningFocus = recommended.focus
    if (!descriptionCustomized.value) form.materialDescription = recommended.description
    if (focusCustomized.value || descriptionCustomized.value) pendingTemplate.value = recommended
    else pendingTemplate.value = null
  },
  { immediate: true },
)

onMounted(async () => {
  try {
    projects.value = await projectsApi.list(false)
  } catch {
    projects.value = []
  }
})

function projectGenre(project: ProjectResponse): string {
  return project.genre === 'CUSTOM' && project.customGenre?.trim()
    ? project.customGenre.trim()
    : genreLabel(project.genre)
}

function selectProject(): void {
  const project = projects.value.find(item => item.id === form.sourceProjectId)
  if (project) form.genre = projectGenre(project)
}

function markFocusCustomized(): void {
  focusCustomized.value = true
}

function markDescriptionCustomized(): void {
  descriptionCustomized.value = true
}

function restoreFocusTemplate(): void {
  if (!form.materialType || !form.skillType) return
  form.learningFocus = getSkillMeltTemplate(form.materialType, form.skillType, form.genre).focus
  focusCustomized.value = false
}

function restoreDescriptionTemplate(): void {
  if (!form.materialType || !form.skillType) return
  form.materialDescription = getSkillMeltTemplate(form.materialType, form.skillType, form.genre).description
  descriptionCustomized.value = false
}

function keepCustomizedTemplate(): void {
  pendingTemplate.value = null
}

function usePendingTemplate(): void {
  if (!pendingTemplate.value) return
  form.learningFocus = pendingTemplate.value.focus
  form.materialDescription = pendingTemplate.value.description
  focusCustomized.value = false
  descriptionCustomized.value = false
  pendingTemplate.value = null
}

async function inspectFile(file: File): Promise<LocalFile> {
  const item: LocalFile = {
    id: `${file.name}-${file.size}-${file.lastModified}-${crypto.randomUUID()}`,
    file,
    title: file.name.replace(/\.txt$/i, ''),
    detectedEncoding: '等待检测',
    characterCount: null,
    preview: '',
    error: null,
  }
  if (!file.name.toLowerCase().endsWith('.txt')) item.error = '仅支持 .txt 文件'
  else if (file.size > forgeLimits.maxFileBytes) item.error = `单文件不能超过 ${formatBytes(forgeLimits.maxFileBytes)}`
  if (item.error) return item
  const bytes = new Uint8Array(await file.arrayBuffer())
  const candidates: Array<{ name: string; decoder: TextDecoder; offset: number }> = []
  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    candidates.push({ name: 'UTF-8 BOM', decoder: new TextDecoder('utf-8', { fatal: true }), offset: 3 })
  } else {
    candidates.push({ name: 'UTF-8', decoder: new TextDecoder('utf-8', { fatal: true }), offset: 0 })
    candidates.push({ name: 'GB18030', decoder: new TextDecoder('gb18030', { fatal: true }), offset: 0 })
  }
  for (const candidate of candidates) {
    try {
      const text = candidate.decoder.decode(bytes.slice(candidate.offset))
      if (text.includes('\u0000') || text.includes('\ufffd')) continue
      item.detectedEncoding = candidate.name
      item.characterCount = text.length
      item.preview = text.slice(0, 500)
      return item
    } catch {
      // Try the next supported encoding. The backend remains the trust boundary.
    }
  }
  item.error = '无法按 UTF-8 或 GB18030 解码'
  return item
}

async function addFiles(selected: FileList | File[]): Promise<void> {
  error.value = null
  const incoming = Array.from(selected)
  if (files.value.length + incoming.length > forgeLimits.maxFiles) {
    error.value = new Error(`一次最多添加 ${forgeLimits.maxFiles} 个 TXT`)
    return
  }
  if (totalFileBytes.value + incoming.reduce((sum, file) => sum + file.size, 0) > forgeLimits.maxTotalBytes) {
    error.value = new Error(`TXT 总大小不能超过 ${formatBytes(forgeLimits.maxTotalBytes)}`)
    return
  }
  files.value.push(...await Promise.all(incoming.map(inspectFile)))
}

function onFileInput(event: Event): void {
  const input = event.target as HTMLInputElement
  if (input.files) void addFiles(input.files)
  input.value = ''
}

function onDrop(event: DragEvent): void {
  dragActive.value = false
  if (event.dataTransfer?.files) void addFiles(event.dataTransfer.files)
}

function removeFile(id: string): void {
  files.value = files.value.filter(item => item.id !== id)
}

function moveFile(index: number, delta: -1 | 1): void {
  const target = index + delta
  if (target < 0 || target >= files.value.length) return
  const copy = [...files.value]
  const [item] = copy.splice(index, 1)
  if (item) copy.splice(target, 0, item)
  files.value = copy
}

function clearManualText(): void {
  form.manualText = ''
}

async function createAndDistill(): Promise<void> {
  if (!canCreateRun.value || busy.value) return
  busy.value = true
  error.value = null
  try {
    const created = await globalSkillsApi.createForgeRun({
      slug: form.slug.trim(),
      displayName: form.displayName.trim(),
      skillType: form.skillType as ForgeSkillType,
      materialTag: form.materialType as ForgeMaterialType,
      genre: form.genre.trim() || null,
      sourceProjectId: form.sourceProjectId || null,
      focus: form.learningFocus.trim() || null,
      materialDescription: form.materialDescription.trim() || null,
      excludeCharacterNames: form.excludeCharacterNames,
      excludeLocations: form.excludeLocations,
      excludePlotFacts: form.excludePlotFacts,
      reusableMethodsOnly: form.reusableMethodsOnly,
      ownershipConfirmed: form.ownershipConfirmed,
      ownershipStatement: '我确认提供的文字由我创作，或我拥有用于分析和生成私有 Skill 的权利。',
    })
    run.value = created
    const added: ForgeSourceResponse[] = []
    if (files.value.length) added.push(...await globalSkillsApi.addTxtSources(created.id, files.value.map(item => ({ file: item.file, title: item.title })), form.materialType as ForgeMaterialType, true))
    if (hasManualText.value) {
      added.push(await globalSkillsApi.addManualSource(created.id, {
        title: form.manualTitle.trim() || '手写文本',
        content: form.manualText,
        materialType: form.materialType as ForgeMaterialType,
        ownershipConfirmed: true,
      }))
    }
    sources.value = added
    run.value = await globalSkillsApi.startDistillation(created.id)
    rules.value = await globalSkillsApi.forgeRules(created.id)
    stage.value = 'REVIEW'
    sessionStorage.removeItem(draftKey)
  } catch (caught) {
    error.value = caught
  } finally {
    busy.value = false
  }
}

async function reviewRule(rule: AtomicSkillRuleResponse, action: 'ACCEPT' | 'EDIT' | 'DELETE'): Promise<void> {
  if (!run.value || busy.value) return
  busy.value = true
  error.value = null
  try {
    const updated = await globalSkillsApi.reviewForgeRule(
      run.value.id,
      rule.id,
      action,
      action === 'EDIT' ? editingStatement.value : undefined,
    )
    rules.value = rules.value.map(item => item.id === updated.id ? updated : item)
    editingRuleId.value = null
    editingStatement.value = ''
  } catch (caught) {
    error.value = caught
  } finally {
    busy.value = false
  }
}

function beginEdit(rule: AtomicSkillRuleResponse): void {
  editingRuleId.value = rule.id
  editingStatement.value = rule.statement
}

function toggleEvidence(ruleId: string): void {
  const next = new Set(expandedEvidence.value)
  if (next.has(ruleId)) next.delete(ruleId)
  else next.add(ruleId)
  expandedEvidence.value = next
}

async function generateContract(): Promise<void> {
  if (!run.value || unresolvedCount.value > 0 || acceptedCount.value === 0 || busy.value) return
  busy.value = true
  error.value = null
  try {
    run.value = await globalSkillsApi.generateForgeContract(run.value.id)
    stage.value = 'CONTRACT'
  } catch (caught) {
    error.value = caught
  } finally {
    busy.value = false
  }
}

async function validateContract(): Promise<void> {
  if (!run.value || busy.value) return
  busy.value = true
  error.value = null
  try {
    const result = await globalSkillsApi.validateForge(run.value.id)
    validationScore.value = result.score
    run.value = await globalSkillsApi.getForgeRun(run.value.id)
  } catch (caught) {
    error.value = caught
  } finally {
    busy.value = false
  }
}

function formatBytes(value: number): string {
  return value >= 1024 * 1024 ? `${(value / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(value / 1024)} KB`
}
</script>

<template>
  <main class="page-container forge-page">
    <RouterLink class="back-link" to="/skills">← 返回 Skill 工坊</RouterLink>
    <header class="page-header forge-hero">
      <div>
        <p class="eyebrow">Skill forge · Text evidence</p>
        <h1 tabindex="-1">创建写作 Skill</h1>
        <p>从你有权使用的 TXT 或手写文本中提炼可复用方法。候选规则必须逐条确认，不会直接发布。</p>
      </div>
      <aside class="forge-hero__boundary"><strong>证据优先</strong><span>原文仅用于私有分析</span><span>规则逐条确认后生效</span><span>验证通过才可绑定项目</span></aside>
    </header>

    <nav class="forge-progress" aria-label="Skill 熔炼进度">
      <span :class="{ active: stage === 'SOURCES', complete: stage !== 'SOURCES' }"><b>准备素材</b><small>设置用途并添加文本</small></span>
      <span :class="{ active: stage === 'REVIEW', complete: stage === 'CONTRACT' }"><b>审阅规则</b><small>核对每条证据和边界</small></span>
      <span :class="{ active: stage === 'CONTRACT' }"><b>验证契约</b><small>运行测试并发布版本</small></span>
    </nav>

    <ProblemAlert v-if="error" :error="error" />

    <form v-if="stage === 'SOURCES'" class="sw-form project-form" @submit.prevent="createAndDistill">
      <section class="form-section forge-form-section">
        <span class="step-number">设定</span>
        <div><h2>Skill 设置</h2><p>先定义用途。熔炼目标是你的写作方法，不是模仿其他作者。</p></div>
        <label class="form-field"><span>Skill 名称 <b>*</b></span><input v-model="form.displayName" maxlength="120" required placeholder="例如：我的长篇写作方法" @input="suggestSlug" /></label>
        <label class="form-field"><span>标识 <b>*</b></span><input v-model="form.slug" maxlength="80" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" required placeholder="my-writing-skill" @input="slugTouched = true" /></label>
        <label class="form-field"><span>Skill 类型 <b>*</b></span><select v-model="form.skillType" required><option disabled value="">请选择 Skill 类型</option><option v-for="option in skillTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
        <label class="form-field"><span>素材标签 <b>*</b></span><select v-model="form.materialType" required><option disabled value="">请选择素材标签</option><option v-for="option in materialTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
        <label class="form-field"><span>关联项目 <small>可选</small></span><select v-model="form.sourceProjectId" @change="selectProject"><option value="">不关联项目</option><option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }} · {{ projectGenre(project) }}</option></select></label>
        <label class="form-field"><span>题材上下文 <small>可选</small></span><input v-model="form.genre" maxlength="80" placeholder="例如：仙侠；关联项目后自动读取" /></label>

        <div v-if="pendingTemplate" class="template-change-prompt form-field--full" role="status">
          <div><strong>推荐模板已变化</strong><span>当前内容已被修改，是否使用新的推荐模板？</span></div>
          <div><button type="button" @click="keepCustomizedTemplate">保留当前内容</button><button type="button" class="sw-button--primary" @click="usePendingTemplate">使用新模板</button></div>
        </div>

        <label class="form-field form-field--full template-field">
          <span><span>希望重点学习什么 <em>{{ focusCustomized ? '已修改' : form.learningFocus ? '推荐内容' : '' }}</em></span><button v-if="form.materialType && form.skillType" type="button" @click="restoreFocusTemplate">恢复推荐内容</button></span>
          <textarea v-model="form.learningFocus" maxlength="1000" rows="7" placeholder="请选择素材标签和 Skill 类型后自动生成建议内容。" @input="markFocusCustomized" />
        </label>
        <label class="form-field form-field--full template-field">
          <span><span>素材说明 <em>{{ descriptionCustomized ? '已修改' : form.materialDescription ? '推荐内容' : '' }}</em></span><button v-if="form.materialType && form.skillType" type="button" @click="restoreDescriptionTemplate">恢复推荐内容</button></span>
          <textarea v-model="form.materialDescription" maxlength="1000" rows="4" placeholder="系统会根据素材类型生成对应说明，你也可以自行修改。" @input="markDescriptionCustomized" />
        </label>
      </section>

      <section class="form-section forge-form-section forge-source-section">
        <span class="step-number">素材</span>
        <div><h2>添加文本来源</h2><p>TXT 与手写文字可以混合使用，最终进入同一条可追溯流水线。</p></div>
        <div class="forge-tabs form-field--full" role="tablist" aria-label="文本来源方式">
          <button type="button" role="tab" :aria-selected="inputMode === 'TXT'" :class="{ active: inputMode === 'TXT' }" @click="inputMode = 'TXT'">上传 TXT</button>
          <button type="button" role="tab" :aria-selected="inputMode === 'MANUAL_TEXT'" :class="{ active: inputMode === 'MANUAL_TEXT' }" @click="inputMode = 'MANUAL_TEXT'">粘贴 / 手写</button>
        </div>

        <div v-show="inputMode === 'TXT'" class="form-field--full">
          <label class="txt-drop-zone" :class="{ 'is-dragging': dragActive }" @dragover.prevent="dragActive = true" @dragleave.prevent="dragActive = false" @drop.prevent="onDrop">
            <input class="visually-hidden" type="file" accept=".txt,text/plain" multiple @change="onFileInput" />
            <strong>拖入 TXT 文件，或点击选择</strong>
            <span>最多 {{ forgeLimits.maxFiles }} 个；单个 {{ formatBytes(forgeLimits.maxFileBytes) }}；总计 {{ formatBytes(forgeLimits.maxTotalBytes) }}</span>
            <span>支持 UTF-8、UTF-8 BOM、GB18030；解码失败不会继续熔炼。</span>
          </label>
          <ol v-if="files.length" class="forge-file-list" aria-label="待上传 TXT">
            <li v-for="(item, index) in files" :key="item.id" :class="{ 'has-error': item.error }">
              <div><strong>{{ item.file.name }}</strong><label><span class="visually-hidden">来源标题</span><input v-model="item.title" maxlength="200" aria-label="来源标题" /></label><small>{{ formatBytes(item.file.size) }} · {{ item.detectedEncoding }} · {{ item.characterCount ?? '待检测' }} 字</small><small v-if="item.error">{{ item.error }}</small></div>
              <details v-if="item.preview"><summary>上传前预览</summary><pre>{{ item.preview }}</pre></details>
              <div class="file-actions"><button type="button" :disabled="index === 0" aria-label="上移" @click="moveFile(index, -1)">↑</button><button type="button" :disabled="index === files.length - 1" aria-label="下移" @click="moveFile(index, 1)">↓</button><button type="button" @click="removeFile(item.id)">删除</button></div>
            </li>
          </ol>
        </div>

        <div v-show="inputMode === 'MANUAL_TEXT'" class="manual-source form-field--full">
          <label class="form-field"><span>来源标题</span><input v-model="form.manualTitle" maxlength="200" /></label>
          <label class="form-field form-field--full"><span>把你自己写的文字放在这里</span><textarea v-model="form.manualText" :maxlength="forgeLimits.maxManualCharacters" rows="14" placeholder="可以是一段小说、一场对话、几段描写，或者你的写作原则。" /><small>{{ form.manualText.length }} / {{ forgeLimits.maxManualCharacters }}</small></label>
          <p v-if="hasManualText && form.manualText.length < forgeLimits.minManualCharacters" class="field-error">至少需要 {{ forgeLimits.minManualCharacters }} 字。</p>
          <p v-else-if="form.manualText.length >= forgeLimits.minManualCharacters && form.manualText.length < forgeLimits.sampleWarningCharacters" class="sample-warning">样本较少：只会提取有证据的局部模式，不会推断完整长篇结构。</p>
          <button v-if="hasManualText" class="text-button" type="button" @click="clearManualText">清空手写文本</button>
        </div>
      </section>

      <section class="form-section forge-form-section">
        <span class="step-number">边界</span>
        <div><h2>排除项与权利确认</h2><p>默认只提炼可迁移的方法，降低对单个作品的人名、地点和剧情过拟合。</p></div>
        <fieldset class="forge-checks form-field--full"><legend>默认排除</legend><label><input v-model="form.excludeCharacterNames" type="checkbox" />排除人物专有名词</label><label><input v-model="form.excludeLocations" type="checkbox" />排除具体地点和世界设定</label><label><input v-model="form.excludePlotFacts" type="checkbox" />排除剧情事实</label><label><input v-model="form.reusableMethodsOnly" type="checkbox" />只提炼可复用写作方法</label><p v-if="!form.excludeCharacterNames || !form.excludeLocations || !form.excludePlotFacts || !form.reusableMethodsOnly" class="sample-warning">关闭排除项可能让 Skill 过拟合当前作品。</p></fieldset>
        <label class="ownership-confirm form-field--full"><input v-model="form.ownershipConfirmed" type="checkbox" required /><span>我确认上传 / 粘贴的文字由我创作，或我拥有用于本次分析和生成私有 Skill 的权利。</span></label>
      </section>

      <div class="form-actions"><RouterLink class="sw-button sw-button--secondary" to="/skills">取消</RouterLink><button class="sw-button sw-button--primary" type="submit" :disabled="busy || !canCreateRun">{{ busy ? '正在保存来源并熔炼…' : '开始熔炼' }}</button></div>
    </form>

    <section v-else-if="stage === 'REVIEW'" class="forge-review">
      <div class="forge-status-card"><div><span class="status-badge">{{ run?.status }}</span><h2>{{ run?.summary }}</h2></div><dl><div><dt>来源</dt><dd>{{ sources.length }}</dd></div><div><dt>已接受</dt><dd>{{ acceptedCount }}</dd></div><div><dt>已删除</dt><dd>{{ rejectedCount }}</dd></div><div><dt>待处理</dt><dd>{{ unresolvedCount }}</dd></div></dl></div>
      <article v-for="rule in rules" :key="rule.id" class="atomic-rule" :class="`is-${rule.status.toLowerCase()}`">
        <header><div><span>{{ dimensionLabels[rule.dimension] }}</span><span>{{ rule.evidenceLevel }} · {{ Math.round(rule.confidence * 100) }}%</span><span>{{ rule.scope }}</span></div><strong>{{ rule.status }}</strong></header>
        <textarea v-if="editingRuleId === rule.id" v-model="editingStatement" maxlength="2000" rows="4" />
        <p v-else>{{ rule.statement }}</p>
        <small>{{ rule.rationale }}</small>
        <div class="rule-actions">
          <template v-if="editingRuleId === rule.id"><button type="button" :disabled="busy" @click="reviewRule(rule, 'EDIT')">保存并接受</button><button type="button" @click="editingRuleId = null">取消编辑</button></template>
          <template v-else><button type="button" :disabled="busy" @click="reviewRule(rule, 'ACCEPT')">✓ 接受</button><button type="button" :disabled="busy" @click="beginEdit(rule)">✎ 修改</button><button type="button" :disabled="busy" @click="reviewRule(rule, 'DELETE')">× 删除</button><button type="button" @click="toggleEvidence(rule.id)">? {{ expandedEvidence.has(rule.id) ? '收起证据' : '查看证据' }}</button></template>
        </div>
        <ul v-if="expandedEvidence.has(rule.id)" class="evidence-list"><li v-for="evidence in rule.evidence" :key="`${evidence.sourceId}-${evidence.paragraphKey}`"><strong>{{ sources.find(item => item.id === evidence.sourceId)?.title ?? evidence.sourceId }} / {{ evidence.paragraphKey }}</strong><blockquote>{{ evidence.excerpt }}</blockquote><small>SHA-256：{{ evidence.excerptHash }}</small></li></ul>
      </article>
      <div class="form-actions"><button class="sw-button sw-button--secondary" type="button" disabled>保存草稿（已自动保存）</button><button class="sw-button sw-button--primary" type="button" :disabled="busy || unresolvedCount > 0 || acceptedCount === 0" @click="generateContract">{{ unresolvedCount > 0 ? `还有 ${unresolvedCount} 条待审查` : '生成 Skill 契约' }}</button></div>
    </section>

    <section v-else class="contract-preview">
      <div class="forge-status-card"><div><span class="status-badge">{{ run?.status }}</span><h2>Skill 契约候选</h2><p>核心契约只保留已确认规则和来源 Hash，完整原始文本不会进入契约。</p></div></div>
      <pre>{{ JSON.stringify(run?.candidateContract, null, 2) }}</pre>
      <div v-if="validationScore !== null" class="validation-result"><strong>验证分数：{{ validationScore }} / 100</strong><span>{{ run?.status }}</span></div>
      <div class="form-actions"><RouterLink class="sw-button sw-button--secondary" :to="`/skills/${run?.globalSkillId}`">查看 Skill 详情</RouterLink><button class="sw-button sw-button--primary" type="button" :disabled="busy || run?.status === 'VALIDATED'" @click="validateContract">{{ busy ? '正在运行 8 个测试场景…' : run?.status === 'VALIDATED' ? '已验证' : '运行测试并发布版本' }}</button></div>
    </section>
  </main>
</template>

<style scoped>
.forge-page { max-width: 1100px; padding-bottom: 5rem; }
.forge-page .back-link { padding-bottom: .15rem; border-bottom: 1px solid transparent; }
.forge-page .back-link:hover { border-bottom-color: currentColor; }
.forge-hero { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(260px, .65fr); align-items: end; padding-bottom: var(--sw-space-8); border-bottom: 1px solid var(--sw-border); }
.forge-hero h1 { max-width: 14ch; font-size: clamp(2.5rem, 5.5vw, 4.25rem); line-height: 1.06; }
.forge-hero p:not(.eyebrow) { max-width: 58ch; margin-top: var(--sw-space-5); line-height: 1.75; }
.forge-hero__boundary { display: grid; gap: .5rem; padding-left: var(--sw-space-6); border-left: 1px solid var(--sw-border); }
.forge-hero__boundary strong { margin-bottom: .2rem; color: var(--sw-accent); font-family: var(--sw-font-serif); font-size: 1.05rem; }
.forge-hero__boundary span { color: var(--sw-text-secondary); font-size: .8rem; }
.forge-progress { display: grid; grid-template-columns: repeat(3, 1fr); margin-bottom: var(--sw-space-8); border: 1px solid var(--sw-border); border-radius: var(--sw-radius-card); background: var(--sw-bg-surface); }
.forge-progress > span { position: relative; display: grid; gap: .2rem; padding: var(--sw-space-4) var(--sw-space-5); }
.forge-progress > span + span { border-left: 1px solid var(--sw-border); }
.forge-progress > span::after { position: absolute; right: var(--sw-space-5); bottom: -1px; left: var(--sw-space-5); height: 2px; background: transparent; content: ''; }
.forge-progress > span.active::after, .forge-progress > span.complete::after { background: var(--sw-accent); }
.forge-progress b { font-size: .85rem; }
.forge-progress small { color: var(--sw-text-muted); font-size: .72rem; }
.forge-progress > span:not(.active):not(.complete) { color: var(--sw-text-muted); }
.project-form { gap: var(--sw-space-5); }
.forge-form-section { grid-template-columns: 72px minmax(0, 1fr); gap: var(--sw-space-6); padding: clamp(1.25rem, 3.5vw, 2rem); border-color: color-mix(in srgb, var(--sw-border) 82%, transparent); }
.forge-form-section .step-number { align-self: start; padding-top: .2rem; color: var(--sw-accent); font-family: var(--sw-font-serif); font-size: .9rem; letter-spacing: .08em; }
.forge-form-section h2 { font-family: var(--sw-font-serif); font-size: 1.4rem; }
.forge-form-section > div > p { max-width: 68ch; line-height: 1.7; }
.forge-form-section > .form-field--full { grid-column: 2; }
.forge-form-section :deep(input), .forge-form-section :deep(select), .forge-form-section :deep(textarea) { transition: border-color 140ms ease, box-shadow 140ms ease, background-color 140ms ease; }
.forge-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: .5rem; padding: .3rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); background: var(--sw-bg-subtle); }
.forge-tabs button { min-height: 42px; border: 1px solid transparent; border-radius: calc(var(--sw-radius-control) - 1px); background: transparent; color: var(--sw-text-secondary); cursor: pointer; font-weight: 700; transition: background-color 140ms ease, border-color 140ms ease, color 140ms ease; }
.forge-tabs button:hover { color: var(--sw-text-primary); }
.forge-tabs button.active { border-color: var(--sw-border); background: var(--sw-bg-surface); color: var(--sw-accent); box-shadow: 0 3px 12px color-mix(in srgb, var(--sw-accent) 7%, transparent); }
.txt-drop-zone { display: grid; place-items: center; gap: .45rem; min-height: 190px; padding: 2rem; border: 1px dashed var(--sw-border-strong); border-radius: var(--sw-radius-card); background: var(--sw-bg-subtle); text-align: center; cursor: pointer; transition: border-color 140ms ease, background-color 140ms ease, transform 140ms ease; }
.txt-drop-zone:hover, .txt-drop-zone.is-dragging { border-color: var(--sw-accent); background: var(--sw-accent-soft); }
.txt-drop-zone.is-dragging { transform: scale(.995); }
.txt-drop-zone strong { font-family: var(--sw-font-serif); font-size: 1.1rem; }
.txt-drop-zone span { color: var(--sw-text-secondary); font-size: .82rem; }
.visually-hidden { position: absolute; inline-size: 1px; block-size: 1px; overflow: hidden; clip: rect(0 0 0 0); }
.forge-file-list { display: grid; gap: 0; margin: var(--sw-space-4) 0 0; padding: 0; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-card); background: var(--sw-bg-surface); list-style: none; }
.forge-file-list li { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--sw-space-3); padding: var(--sw-space-4); }
.forge-file-list li + li { border-top: 1px solid var(--sw-border); }
.forge-file-list li.has-error { box-shadow: inset 3px 0 0 var(--sw-danger); }
.forge-file-list div:first-child { display: grid; gap: .3rem; }
.forge-file-list input { height: 38px; padding: 0 var(--sw-space-3); border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); background: var(--sw-bg-subtle); color: var(--sw-text-primary); }
.forge-file-list small { color: var(--sw-text-muted); }
.forge-file-list details { grid-column: 1 / -1; }
.forge-file-list summary { color: var(--sw-accent); cursor: pointer; font-size: .78rem; font-weight: 700; }
.forge-file-list pre { max-height: 180px; overflow: auto; padding: var(--sw-space-3); border-radius: var(--sw-radius-control); background: var(--sw-bg-editor); color: var(--sw-text-secondary); line-height: 1.7; white-space: pre-wrap; }
.file-actions { display: flex; gap: .35rem; align-items: start; }
.file-actions button, .rule-actions button { min-height: 34px; padding: 0 .65rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); background: var(--sw-bg-surface); color: var(--sw-text-primary); cursor: pointer; font-size: .78rem; }
.file-actions button:hover, .rule-actions button:hover { border-color: var(--sw-accent); color: var(--sw-accent); }
.file-actions button:active, .rule-actions button:active { transform: translateY(1px); }
.file-actions button:disabled, .rule-actions button:disabled { cursor: not-allowed; opacity: .45; }
.manual-source { display: grid; gap: .75rem; }
.template-field > span { display: flex; align-items: center; justify-content: space-between; gap: var(--sw-space-3); }
.template-field > span > span { display: flex; align-items: center; gap: .55rem; }
.template-field em { padding: .18rem .45rem; border-radius: 999px; background: var(--sw-accent-soft); color: var(--sw-accent-strong); font-size: .68rem; font-style: normal; font-weight: 700; }
.template-field > span > button { min-height: 30px; padding: 0 .55rem; border: 0; background: transparent; color: var(--sw-accent); cursor: pointer; font-size: .76rem; font-weight: 700; }
.template-field textarea { line-height: 1.7; }
.template-change-prompt { display: flex; align-items: center; justify-content: space-between; gap: var(--sw-space-4); padding: var(--sw-space-4); border: 1px solid color-mix(in srgb, var(--sw-warning) 35%, var(--sw-border)); border-radius: var(--sw-radius-control); background: color-mix(in srgb, var(--sw-warning) 8%, var(--sw-bg-surface)); }
.template-change-prompt > div { display: flex; gap: .35rem; }
.template-change-prompt > div:first-child { flex-direction: column; }
.template-change-prompt span { color: var(--sw-text-secondary); font-size: .8rem; }
.template-change-prompt button { min-height: 36px; padding: 0 .7rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); background: var(--sw-bg-surface); color: var(--sw-text-primary); cursor: pointer; }
.template-change-prompt button.sw-button--primary { border-color: var(--sw-accent); background: var(--sw-accent); color: var(--sw-text-on-accent); }
.sample-warning { padding: .7rem .85rem; border: 1px solid color-mix(in srgb, var(--sw-warning) 35%, var(--sw-border)); border-radius: var(--sw-radius-control); background: color-mix(in srgb, var(--sw-warning) 9%, var(--sw-bg-surface)); color: var(--sw-warning); }
.forge-checks { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .65rem; margin: 0; padding: 0; border: 0; }
.forge-checks legend { margin-bottom: .65rem; font-family: var(--sw-font-serif); font-weight: 700; }
.forge-checks label, .ownership-confirm { display: flex; min-height: 44px; align-items: flex-start; gap: .65rem; padding: .7rem .8rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); background: var(--sw-bg-subtle); color: var(--sw-text-secondary); }
.forge-checks input, .ownership-confirm input { margin-top: .2rem; accent-color: var(--sw-accent); }
.ownership-confirm { border-color: color-mix(in srgb, var(--sw-accent) 35%, var(--sw-border)); background: var(--sw-accent-soft); color: var(--sw-text-primary); }
.forge-review, .contract-preview { display: grid; gap: var(--sw-space-4); }
.forge-status-card { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: var(--sw-space-6); padding: var(--sw-space-5) var(--sw-space-6); border: 1px solid var(--sw-border); border-radius: var(--sw-radius-card); background: var(--sw-bg-surface); }
.forge-status-card h2 { margin: .5rem 0 0; font-family: var(--sw-font-serif); font-size: 1.25rem; }
.forge-status-card p { color: var(--sw-text-secondary); }
.forge-status-card dl { display: grid; grid-template-columns: repeat(4, minmax(64px, 1fr)); gap: var(--sw-space-4); margin: 0; }
.forge-status-card dl div { padding-left: var(--sw-space-4); border-left: 1px solid var(--sw-border); text-align: left; }
.forge-status-card dt { color: var(--sw-text-muted); font-size: .72rem; }
.forge-status-card dd { margin: .15rem 0 0; font-family: var(--sw-font-serif); font-size: 1.3rem; font-weight: 750; }
.atomic-rule { position: relative; padding: var(--sw-space-5) var(--sw-space-6); border: 1px solid var(--sw-border); border-radius: var(--sw-radius-card); background: var(--sw-bg-surface); }
.atomic-rule::before { position: absolute; inset: 0 auto 0 0; width: 3px; border-radius: var(--sw-radius-card) 0 0 var(--sw-radius-card); background: var(--sw-border-strong); content: ''; }
.atomic-rule.is-accepted::before { background: var(--sw-success); }
.atomic-rule.is-rejected { opacity: .64; }
.atomic-rule header { display: flex; justify-content: space-between; gap: .75rem; }
.atomic-rule header div { display: flex; flex-wrap: wrap; gap: .45rem; }
.atomic-rule header span { padding: .2rem .5rem; border-radius: 999px; background: var(--sw-bg-subtle); color: var(--sw-text-secondary); font-size: .72rem; }
.atomic-rule > p { max-width: 76ch; margin: var(--sw-space-4) 0 .35rem; font-family: var(--sw-font-serif); font-size: 1.08rem; line-height: 1.7; }
.atomic-rule > small { color: var(--sw-text-muted); line-height: 1.6; }
.atomic-rule textarea { width: 100%; margin-top: var(--sw-space-4); padding: var(--sw-space-3); border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); background: var(--sw-bg-editor); color: var(--sw-text-primary); line-height: 1.7; }
.rule-actions { display: flex; flex-wrap: wrap; gap: .5rem; margin-top: var(--sw-space-4); }
.evidence-list { display: grid; gap: var(--sw-space-3); margin: var(--sw-space-5) 0 0; padding: 0; list-style: none; }
.evidence-list li { padding: var(--sw-space-4); border-left: 3px solid var(--sw-accent); background: var(--sw-bg-subtle); }
.evidence-list blockquote { margin: .55rem 0; color: var(--sw-text-secondary); line-height: 1.75; white-space: pre-wrap; }
.evidence-list small { color: var(--sw-text-muted); font-family: "Cascadia Code", Consolas, monospace; font-size: .68rem; overflow-wrap: anywhere; }
.contract-preview > pre { max-height: 62vh; overflow: auto; margin: 0; padding: var(--sw-space-6); border: 1px solid var(--sw-border); border-radius: var(--sw-radius-card); background: var(--sw-bg-editor); color: var(--sw-text-primary); font-family: "Cascadia Code", Consolas, monospace; font-size: .78rem; line-height: 1.7; white-space: pre-wrap; }
.validation-result { display: flex; justify-content: space-between; padding: var(--sw-space-4) var(--sw-space-5); border: 1px solid color-mix(in srgb, var(--sw-success) 32%, var(--sw-border)); border-radius: var(--sw-radius-card); background: var(--sw-accent-soft); }
@media (prefers-reduced-motion: reduce) { .forge-tabs button, .txt-drop-zone, .forge-form-section :deep(input), .forge-form-section :deep(select), .forge-form-section :deep(textarea) { transition: none; } .txt-drop-zone.is-dragging { transform: none; } }
@media (max-width: 820px) { .forge-hero { grid-template-columns: 1fr; } .forge-hero__boundary { padding-top: var(--sw-space-4); padding-left: 0; border-top: 1px solid var(--sw-border); border-left: 0; } .forge-status-card { grid-template-columns: 1fr; } .forge-status-card dl { width: 100%; } }
@media (max-width: 680px) { .forge-progress { grid-template-columns: 1fr; } .forge-progress > span + span { border-top: 1px solid var(--sw-border); border-left: 0; } .forge-form-section { grid-template-columns: 1fr; gap: var(--sw-space-3); } .forge-form-section .step-number, .forge-form-section .form-field, .forge-form-section > .form-field--full { grid-column: 1; } .template-change-prompt { align-items: stretch; flex-direction: column; } .template-change-prompt > div:last-child { display: grid; grid-template-columns: 1fr 1fr; } .forge-checks { grid-template-columns: 1fr; } .forge-file-list li { grid-template-columns: 1fr; } .forge-status-card dl { grid-template-columns: repeat(2, 1fr); row-gap: var(--sw-space-4); } .atomic-rule { padding: var(--sw-space-4); } }
@media (max-width: 480px) { .forge-tabs { grid-template-columns: 1fr; } .forge-status-card dl { grid-template-columns: 1fr 1fr; } .forge-status-card dl div:nth-child(odd) { border-left: 0; padding-left: 0; } .forge-page .form-actions, .forge-page .form-actions > * { width: 100%; } }
</style>
