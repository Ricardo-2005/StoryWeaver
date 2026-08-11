<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'

import { txtImportsApi, validateTxtImportFile } from '@/api/endpoints/txtImports'
import type { BookAnalysisRequest, TxtImportChapterResponse, TxtImportJobResponse } from '@/api/types'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import { moreGenreOptions, primaryGenreOptions, type ProjectGenre } from '@/features/projects/projectOptions'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const importId = computed(() => typeof route.params.importId === 'string' ? route.params.importId : '')
const queryKey = computed(() => ['txt-import', importId.value])
const jobQuery = useQuery({
  queryKey,
  queryFn: () => txtImportsApi.get(importId.value),
  enabled: () => Boolean(importId.value),
})
const job = computed(() => jobQuery.data.value)
const selectedChapterId = ref('')
const page = ref(1)
const pageSize = 50
const fixedCharacters = ref(10_000)
const uploadError = ref('')
const encoding = ref<'AUTO' | 'UTF-8' | 'GB18030' | 'GBK'>('AUTO')
const titleDrafts = reactive<Record<string, string>>({})
const analysisEnabled = ref(false)

const project = reactive({
  name: '',
  genre: 'FANTASY_GENERAL' as ProjectGenre,
  customGenre: '',
  premise: '导入自 TXT 原稿，保留原文内容并在文脉中继续整理与创作。',
  description: '',
  targetAudience: 'GENERAL' as const,
  narrativePerspective: 'THIRD_PERSON' as const,
  lengthType: 'LONG_NOVEL' as const,
})
const analysisOptions = reactive<BookAnalysisRequest>({
  extractCharacters: true,
  extractWorldbook: true,
  extractOutline: false,
  extractEvents: true,
  extractSkills: false,
})

const chapters = computed(() => job.value?.chapters ?? [])
const pageCount = computed(() => Math.max(1, Math.ceil(chapters.value.length / pageSize)))
const visibleChapters = computed(() => chapters.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const selectedChapter = computed(() => chapters.value.find(chapter => chapter.id === selectedChapterId.value))
const contentQuery = useQuery({
  queryKey: computed(() => ['txt-import-content', importId.value, selectedChapterId.value]),
  queryFn: () => txtImportsApi.content(importId.value, selectedChapterId.value),
  enabled: () => Boolean(importId.value && selectedChapterId.value),
})
const analysisQueryKey = computed(() => ['txt-import-analysis', importId.value])
const analysisQuery = useQuery({
  queryKey: analysisQueryKey,
  queryFn: () => txtImportsApi.analysis(importId.value),
  enabled: () => Boolean(importId.value && analysisEnabled.value),
})
const analysisResult = computed(() => analysisQuery.data.value)

watch(job, (value) => {
  if (!value) return
  if (!project.name) project.name = value.filename.replace(/\.txt$/i, '')
  for (const chapter of value.chapters) titleDrafts[chapter.id] ??= chapter.title
  if (!selectedChapterId.value && value.chapters[0]) selectedChapterId.value = value.chapters[0].id
  if (page.value > pageCount.value) page.value = pageCount.value
}, { immediate: true })

async function applyResult(value: TxtImportJobResponse): Promise<void> {
  queryClient.setQueryData(queryKey.value, value)
}

const uploadMutation = useMutation({
  mutationFn: txtImportsApi.upload,
  onSuccess: async (value) => {
    await router.replace(`/projects/import/txt/${value.id}`)
    queryClient.setQueryData(['txt-import', value.id], value)
  },
})
const parseMutation = useMutation({
  mutationFn: () => txtImportsApi.parse(importId.value, encoding.value),
  onSuccess: applyResult,
})
const editMutation = useMutation({
  mutationFn: (operation: () => Promise<TxtImportJobResponse>) => operation(),
  onSuccess: applyResult,
})
const commitMutation = useMutation({
  mutationFn: () => {
    if (!job.value) throw new Error('导入任务尚未加载')
    return txtImportsApi.commit(importId.value, job.value.version, {
      name: project.name.trim(),
      genre: project.genre,
      customGenre: project.genre === 'CUSTOM' ? project.customGenre.trim() : null,
      targetAudience: project.targetAudience,
      narrativePerspective: project.narrativePerspective,
      lengthType: project.lengthType,
      premise: project.premise.trim(),
      description: project.description.trim() || null,
      authorIntent: null,
      currentFocus: null,
      worldRules: [],
      targetWordCount: null,
      chapterWordTarget: null,
      baseSkillVersionId: null,
    })
  },
  onSuccess: applyResult,
})
const analysisMutation = useMutation({
  mutationFn: () => {
    if (!job.value?.projectId) throw new Error('项目尚未创建')
    return txtImportsApi.startAnalysis(job.value.projectId, analysisOptions)
  },
  onSuccess: (value) => {
    analysisEnabled.value = true
    queryClient.setQueryData(analysisQueryKey.value, value)
  },
})
const decisionMutation = useMutation({
  mutationFn: ({ candidateId, accepted }: { candidateId: string; accepted: boolean }) =>
    txtImportsApi.decideCandidate(importId.value, candidateId, accepted),
  onSuccess: value => queryClient.setQueryData(analysisQueryKey.value, value),
})

function selectFile(event: Event): void {
  uploadError.value = ''
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const validationError = validateTxtImportFile(file)
  if (validationError) { uploadError.value = validationError; return }
  uploadMutation.mutate(file)
}

function saveChapter(chapter: TxtImportChapterResponse, included = chapter.included): void {
  if (!job.value) return
  editMutation.mutate(() => txtImportsApi.updateChapter(importId.value, chapter.id, {
    expectedVersion: job.value!.version,
    title: titleDrafts[chapter.id]?.trim() || chapter.title,
    included,
  }))
}

function moveChapter(chapter: TxtImportChapterResponse, direction: -1 | 1): void {
  if (!job.value) return
  const ids = job.value.chapters.map(value => value.id)
  const index = ids.indexOf(chapter.id)
  const target = index + direction
  if (target < 0 || target >= ids.length) return
  ;[ids[index], ids[target]] = [ids[target]!, ids[index]!]
  editMutation.mutate(() => txtImportsApi.reorder(importId.value, { expectedVersion: job.value!.version, chapterIds: ids }))
}

function mergeNext(chapter: TxtImportChapterResponse): void {
  if (!job.value) return
  const index = job.value.chapters.findIndex(value => value.id === chapter.id)
  const next = job.value.chapters[index + 1]
  if (!next) return
  editMutation.mutate(() => txtImportsApi.merge(importId.value, {
    expectedVersion: job.value!.version,
    firstChapterId: chapter.id,
    secondChapterId: next.id,
    title: titleDrafts[chapter.id] || chapter.title,
  }))
}

function splitChapter(chapter: TxtImportChapterResponse): void {
  if (!job.value) return
  const value = window.prompt(`请输入拆分位置（1—${chapter.characterCount - 1} 字符）`, String(Math.floor(chapter.characterCount / 2)))
  if (!value) return
  const splitOffset = Number(value)
  if (!Number.isInteger(splitOffset) || splitOffset <= 0 || splitOffset >= chapter.characterCount) {
    uploadError.value = '拆分位置必须位于当前章节正文内部。'
    return
  }
  editMutation.mutate(() => txtImportsApi.split(importId.value, {
    expectedVersion: job.value!.version,
    chapterId: chapter.id,
    splitOffset,
    secondTitle: `${titleDrafts[chapter.id] || chapter.title}（下）`,
  }))
}

function keepWholeBook(): void {
  if (!job.value) return
  editMutation.mutate(() => txtImportsApi.whole(importId.value, {
    expectedVersion: job.value!.version,
    title: project.name || job.value!.filename.replace(/\.txt$/i, ''),
  }))
}

function splitByLength(): void {
  if (!job.value) return
  editMutation.mutate(() => txtImportsApi.fixedSplit(importId.value, {
    expectedVersion: job.value!.version,
    targetCharacters: fixedCharacters.value,
  }))
}

async function refreshAnalysis(): Promise<void> {
  analysisEnabled.value = true
  await analysisQuery.refetch()
  await jobQuery.refetch()
}

const anyError = computed(() => uploadMutation.error.value
  ?? parseMutation.error.value
  ?? editMutation.error.value
  ?? commitMutation.error.value
  ?? analysisMutation.error.value
  ?? decisionMutation.error.value)
const canCommit = computed(() => Boolean(job.value?.status === 'WAITING_CONFIRMATION'
  && job.value.chapters.some(chapter => chapter.included)
  && project.name.trim()
  && project.premise.trim().length >= 10
  && (project.genre !== 'CUSTOM' || project.customGenre.trim())))
</script>

<template>
  <main class="page-container txt-import-page">
    <RouterLink class="back-link" to="/projects/new">← 返回创建方式</RouterLink>
    <header class="page-header">
      <div><p class="eyebrow">TXT book import</p><h1 tabindex="-1">导入 TXT 书籍并创建项目</h1><p>先上传和确认章节，再创建正式项目；基础导入不会调用 AI。</p></div>
      <span class="status-pill">{{ job?.status ?? '等待上传' }}</span>
    </header>

    <div v-if="uploadError" class="problem-alert" role="alert">{{ uploadError }}</div>
    <ProblemAlert v-if="anyError" :error="anyError" />

    <section v-if="!importId" class="form-section import-upload-card">
      <span class="step-number">01</span>
      <div><h2>上传 TXT</h2><p>只支持单个 .txt，最大 20 MB。原始文件以 UUID 临时保存，默认 24 小时后清理。</p></div>
      <label class="txt-dropzone">
        <strong>{{ uploadMutation.isPending.value ? '正在上传…' : '选择 TXT 文件' }}</strong>
        <small>支持 UTF-8、UTF-8 BOM、GB18030、GBK</small>
        <input type="file" accept=".txt,text/plain" :disabled="uploadMutation.isPending.value" @change="selectFile" />
      </label>
    </section>

    <LoadingState v-else-if="jobQuery.isPending.value" label="正在读取导入任务…" />
    <ErrorState v-else-if="jobQuery.isError.value" :error="jobQuery.error.value" @retry="jobQuery.refetch()" />
    <template v-else-if="job">
      <section class="v15-panel import-source-summary">
        <div><strong>{{ job.filename }}</strong><span>{{ (job.sizeBytes / 1024 / 1024).toFixed(2) }} MB</span></div>
        <div><span>SHA-256</span><code>{{ job.sha256 }}</code></div>
        <p v-if="job.duplicateImportId" class="duplicate-note">检测到同一账户曾上传完全相同的文件。你仍可继续本次导入。<RouterLink v-if="job.duplicateProjectId" :to="`/projects/${job.duplicateProjectId}`">打开已有项目</RouterLink></p>
      </section>

      <section v-if="job.status === 'UPLOADED' || job.status === 'FAILED'" class="form-section">
        <span class="step-number">02</span>
        <div><h2>确认编码并解析</h2><p>检测结果：{{ job.detectedEncoding }}。{{ job.encodingConfident ? '编码判断可靠。' : '编码存在不确定性，请手动选择并检查预览。' }}</p></div>
        <label class="form-field"><span>文本编码</span><select v-model="encoding"><option value="AUTO">自动（{{ job.selectedEncoding }}）</option><option value="UTF-8">UTF-8</option><option value="GB18030">GB18030</option><option value="GBK">GBK</option></select></label>
        <button class="sw-button sw-button--primary" :disabled="parseMutation.isPending.value" @click="parseMutation.mutate()">{{ parseMutation.isPending.value ? '流式解析中…' : '解析并预览章节' }}</button>
      </section>

      <template v-if="['WAITING_CONFIRMATION','COMPLETED'].includes(job.status)">
        <section class="form-section">
          <span class="step-number">02</span>
          <div><h2>章节预览</h2><p>识别到 {{ job.totalChapters }} 个候选，其中 {{ job.headingCount }} 个独立标题行；解析器 {{ job.parserVersion }}。</p></div>
          <div v-if="job.headingCount === 0" class="no-heading-options">
            <p>没有识别到章节标题。可保留整本一个章节，或主动按段落边界接近固定字数切分。</p>
            <button type="button" @click="keepWholeBook">整本一个章节</button>
            <input v-model.number="fixedCharacters" type="number" min="1000" max="100000" step="1000" aria-label="固定切分字数" />
            <button type="button" @click="splitByLength">按固定字数切分</button>
          </div>
          <div class="chapter-preview-layout">
            <ol class="import-chapter-list">
              <li v-for="chapter in visibleChapters" :key="chapter.id" :class="{ 'is-selected': selectedChapterId === chapter.id }">
                <label><input :checked="chapter.included" type="checkbox" @change="saveChapter(chapter, ($event.target as HTMLInputElement).checked)" /><span class="sr-only">包含章节</span></label>
                <button class="chapter-select" type="button" @click="selectedChapterId = chapter.id"><span>{{ chapter.sequenceNo }}</span><small>{{ chapter.characterCount.toLocaleString() }} 字符 · {{ chapter.paragraphCount }} 段</small></button>
                <input v-model="titleDrafts[chapter.id]" maxlength="160" :aria-label="`章节 ${chapter.sequenceNo} 标题`" @change="saveChapter(chapter)" />
                <div class="chapter-row-actions"><button type="button" :disabled="chapter.sequenceNo === 1" @click="moveChapter(chapter, -1)">上移</button><button type="button" :disabled="chapter.sequenceNo === chapters.length" @click="moveChapter(chapter, 1)">下移</button><button type="button" :disabled="chapter.sequenceNo === chapters.length" @click="mergeNext(chapter)">合并下章</button><button type="button" :disabled="chapter.characterCount < 2" @click="splitChapter(chapter)">拆分</button></div>
              </li>
            </ol>
            <aside class="chapter-content-preview"><h3>{{ selectedChapter?.title ?? '正文预览' }}</h3><LoadingState v-if="contentQuery.isPending.value" label="加载当前章节…" /><ProblemAlert v-else-if="contentQuery.isError.value" :error="contentQuery.error.value" /><pre v-else>{{ contentQuery.data.value?.content }}</pre><small v-if="contentQuery.data.value?.truncated">仅展示前 5,000 字符。</small></aside>
          </div>
          <nav v-if="pageCount > 1" class="pagination" aria-label="章节分页"><button :disabled="page === 1" @click="page--">上一页</button><span>{{ page }} / {{ pageCount }}</span><button :disabled="page === pageCount" @click="page++">下一页</button></nav>
        </section>

        <section v-if="job.status !== 'COMPLETED'" class="form-section">
          <span class="step-number">03</span>
          <div><h2>项目信息</h2><p>项目名称可修改；其他选项复用现有项目模型。</p></div>
          <label class="form-field"><span>项目名称</span><input v-model="project.name" maxlength="80" required /></label>
          <label class="form-field"><span>题材</span><select v-model="project.genre"><option v-for="option in [...primaryGenreOptions, ...moreGenreOptions]" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
          <label v-if="project.genre === 'CUSTOM'" class="form-field"><span>自定义题材</span><input v-model="project.customGenre" maxlength="20" /></label>
          <label class="form-field form-field--full"><span>故事构想</span><textarea v-model="project.premise" minlength="10" maxlength="500" rows="3" /></label>
          <label class="form-field form-field--full"><span>项目简介</span><textarea v-model="project.description" maxlength="300" rows="2" /></label>
        </section>

        <section v-if="job.status !== 'COMPLETED'" class="import-confirmation">
          <p>将创建 {{ job.chapters.filter(chapter => chapter.included).length }} 个 Chapter 与对应 ChapterVersion。不会调用 DeepSeek。</p>
          <button class="sw-button sw-button--primary" :disabled="!canCommit || commitMutation.isPending.value" @click="commitMutation.mutate()">{{ commitMutation.isPending.value ? '正在创建项目与章节…' : '确认导入并创建项目' }}</button>
        </section>
      </template>

      <section v-if="job.status === 'COMPLETED' && job.projectId" class="form-section analysis-section">
        <span class="step-number">04</span>
        <div><h2>导入完成</h2><p>{{ job.processedChapters }} / {{ job.totalChapters }} 章已真实写入。现在才可选择 AI 分析，所有结果只进入 Candidate 审查区。</p></div>
        <RouterLink class="sw-button sw-button--primary" :to="`/projects/${job.projectId}`">打开项目</RouterLink>
        <fieldset><legend>可选 AI 分析（按 Chapter / 12,000 字符 Chunk）</legend><label><input v-model="analysisOptions.extractCharacters" type="checkbox" />人物候选</label><label><input v-model="analysisOptions.extractWorldbook" type="checkbox" />世界书候选</label><label><input v-model="analysisOptions.extractOutline" type="checkbox" />回顾大纲候选</label><label><input v-model="analysisOptions.extractEvents" type="checkbox" />事件候选</label><label><input v-model="analysisOptions.extractSkills" type="checkbox" />Skill 候选</label></fieldset>
        <div class="asset-actions"><button :disabled="analysisMutation.isPending.value || ['QUEUED','ANALYZING'].includes(job.analysisStatus)" @click="analysisMutation.mutate()">启动可选分析</button><button v-if="job.analysisStatus !== 'NOT_REQUESTED' || analysisResult" @click="refreshAnalysis">刷新真实状态</button></div>
        <div v-if="analysisResult" class="analysis-results"><p role="status">状态：{{ analysisResult.status }} · 已处理 {{ analysisResult.processedChunks }} 个 Chunk</p><article v-for="candidate in analysisResult.candidates" :key="candidate.id" class="candidate-row"><span class="asset-type">{{ candidate.candidateType }} · Chunk {{ candidate.chunkIndex }}</span><p>{{ candidate.content }}</p><div v-if="candidate.status === 'CANDIDATE'" class="asset-actions"><button @click="decisionMutation.mutate({ candidateId: candidate.id, accepted: true })">接受</button><button @click="decisionMutation.mutate({ candidateId: candidate.id, accepted: false })">拒绝</button></div><span v-else class="status-pill">{{ candidate.status }}</span></article></div>
      </section>
    </template>
  </main>
</template>

<style scoped>
.txt-import-page { max-width: 1240px; }
.import-upload-card { min-height: 300px; }
.txt-dropzone { grid-column: 2; display: grid; place-items: center; gap: .5rem; min-height: 180px; border: 1px dashed var(--sw-border-strong); border-radius: var(--sw-radius-card); cursor: pointer; }
.txt-dropzone input { max-width: 260px; }
.import-source-summary { display: grid; gap: .75rem; }
.import-source-summary > div { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 1rem; }
.import-source-summary code { overflow-wrap: anywhere; }
.duplicate-note { margin: 0; color: var(--sw-warning-text); }
.no-heading-options { grid-column: 2; display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; padding: 1rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); }
.no-heading-options p { flex-basis: 100%; margin: 0; }
.no-heading-options input { width: 140px; }
.chapter-preview-layout { grid-column: 1 / -1; display: grid; grid-template-columns: minmax(520px, 1.2fr) minmax(320px, .8fr); gap: 1rem; min-width: 0; }
.import-chapter-list { display: grid; gap: .5rem; margin: 0; padding: 0; list-style: none; }
.import-chapter-list li { display: grid; grid-template-columns: auto 110px minmax(180px, 1fr) auto; align-items: center; gap: .5rem; padding: .65rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); }
.import-chapter-list li.is-selected { border-color: var(--sw-accent); }
.chapter-select { display: grid; text-align: left; }
.chapter-select small { color: var(--sw-text-secondary); }
.chapter-row-actions { display: flex; flex-wrap: wrap; gap: .25rem; }
.chapter-content-preview { position: sticky; top: 1rem; align-self: start; min-width: 0; padding: 1rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-card); background: var(--sw-bg-subtle); }
.chapter-content-preview pre { max-height: 520px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; line-height: 1.8; }
.pagination { grid-column: 1 / -1; display: flex; justify-content: center; align-items: center; gap: 1rem; }
.import-confirmation { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 1.25rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-card); background: var(--sw-bg-surface); }
.analysis-section fieldset { grid-column: 2; display: flex; flex-wrap: wrap; gap: 1rem; }
.analysis-results { grid-column: 1 / -1; display: grid; gap: .75rem; }
@media (max-width: 900px) { .chapter-preview-layout { grid-template-columns: 1fr; } .chapter-content-preview { position: static; } .import-chapter-list li { grid-template-columns: auto 90px 1fr; } .chapter-row-actions { grid-column: 2 / -1; } }
@media (max-width: 600px) { .txt-dropzone, .no-heading-options { grid-column: 1; } .import-confirmation { align-items: stretch; flex-direction: column; } }
</style>
