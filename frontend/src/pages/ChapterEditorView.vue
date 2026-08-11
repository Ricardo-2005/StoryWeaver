<script setup lang="ts">
import Highlight from '@tiptap/extension-highlight'
import StarterKit from '@tiptap/starter-kit'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { ElDialog, ElMessage, ElMessageBox } from 'element-plus'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { branchesApi, impactApi } from '@/api/endpoints/v15'
import type { ChapterBranchResponse } from '@/api/types'

import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import PreflightDialog from '@/components/workflow/PreflightDialog.vue'
import {
  countCharacters,
  documentFromText,
  paragraphMapFromDocument,
} from '@/features/chapters/chapterDocument'
import {
  chapterDraftKey,
  deleteChapterDraft,
  readChapterDraft,
  writeChapterDraft,
  type ChapterDraft,
} from '@/features/chapters/draftStorage'
import { ensureParagraphKeys, ParagraphKey } from '@/features/chapters/paragraphKey'
import {
  useChapterQuery,
  useChapterVersionsQuery,
  useCreateChapterVersionMutation,
  useRestoreChapterVersionMutation,
} from '@/queries/chapters'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const projectId = computed(() => String(route.params.projectId ?? ''))
const chapterId = computed(() => String(route.params.chapterId ?? ''))
const chapterQuery = useChapterQuery(chapterId)
const versionsQuery = useChapterVersionsQuery(chapterId)
const createVersion = useCreateChapterVersionMutation(projectId, chapterId)
const restoreVersion = useRestoreChapterVersionMutation(projectId, chapterId)
const branchesQuery = useQuery({ queryKey: computed(() => ['chapter-branches', chapterId.value]), queryFn: () => branchesApi.list(chapterId.value) })
const impactQuery = useQuery({ queryKey: computed(() => ['impact-reports', chapterId.value]), queryFn: () => impactApi.list(chapterId.value), enabled: false })

const title = ref('')
const baseVersion = ref(0)
const initialSignature = ref('')
const initialized = ref(false)
const updateTick = ref(0)
const localSaveState = ref<'clean' | 'pending' | 'saving' | 'saved' | 'error'>('clean')
const localSaveError = ref('')
const recoverableDraft = ref<ChapterDraft>()
const saveDialogOpen = ref(false)
const versionsOpen = ref(true)
const findOpen = ref(false)
const fullscreen = ref(false)
const preflightOpen = ref(route.query.preflight === '1')
const branchesOpen = ref(false)
const impactOpen = ref(false)
const branchForm = reactive({ name: '', description: '', changeSummary: '' })
const selectedBranchId = ref('')
const branchDraft = reactive({ title: '', content: '', changeSummary: '' })
const selectedBranch = computed(() => branchesQuery.data.value?.find(branch => branch.id === selectedBranchId.value))
const createBranch = useMutation({
  mutationFn: () => branchesApi.create(chapterId.value, { name: branchForm.name.trim(), description: branchForm.description.trim() || null, title: title.value, content: editorText(), changeSummary: branchForm.changeSummary.trim() || null }),
  onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['chapter-branches', chapterId.value] }); Object.assign(branchForm, { name: '', description: '', changeSummary: '' }); ElMessage.success('独立章节分支已创建') },
})
const promoteBranch = useMutation({ mutationFn: ({ id, version }: { id: string; version: number }) => branchesApi.promoteImpact(id, version), onSuccess: async () => queryClient.invalidateQueries({ queryKey: ['chapter-branches', chapterId.value] }) })
const addBranchVersion = useMutation({
  mutationFn: () => {
    if (!selectedBranch.value) throw new Error('未选择章节分支')
    return branchesApi.addVersion(selectedBranch.value.id, { expectedVersion: selectedBranch.value.version, title: branchDraft.title.trim(), content: branchDraft.content, changeSummary: branchDraft.changeSummary.trim() || null })
  },
  onSuccess: async branch => { await queryClient.invalidateQueries({ queryKey: ['chapter-branches', chapterId.value] }); compareBranch(branch); ElMessage.success('分支版本已保存，MAIN 未改变') },
})
const createImpact = useMutation({ mutationFn: () => impactApi.create(chapterId.value), onSuccess: async () => { await impactQuery.refetch(); ElMessage.success('影响报告已生成') } })
const findText = ref('')
const replaceText = ref('')
const activeMatch = ref(-1)
const versionForm = reactive({ summary: '', changeSummary: '' })
let autosaveTimer: ReturnType<typeof globalThis.setTimeout> | undefined

function compareBranch(branch: ChapterBranchResponse): void {
  const latest = branch.versions.at(-1)
  selectedBranchId.value = branch.id
  branchDraft.title = latest?.title ?? title.value
  branchDraft.content = latest?.content ?? ''
  branchDraft.changeSummary = ''
}

const editor = useEditor({
  extensions: [StarterKit, Highlight, ParagraphKey],
  content: documentFromText(''),
  editorProps: {
    attributes: {
      class: 'chapter-prose',
      'aria-label': '章节正文',
      spellcheck: 'true',
    },
  },
  onUpdate: ({ editor: currentEditor }) => {
    ensureParagraphKeys(currentEditor)
    updateTick.value += 1
    scheduleLocalSave()
  },
})

function editorText(): string {
  return editor.value?.getText({ blockSeparator: '\n' }) ?? ''
}

function signature(): string {
  return `${title.value}\u0000${editorText()}`
}

const dirty = computed(() => {
  return updateTick.value >= 0 && initialized.value && signature() !== initialSignature.value
})
const characterCount = computed(() => {
  if (updateTick.value < 0) return 0
  return countCharacters(editorText())
})
const paragraphCount = computed(() => {
  if (updateTick.value < 0) return 0
  return editor.value ? paragraphMapFromDocument(editor.value.getJSON()).length : 0
})
const draftKey = computed(() => chapterDraftKey(projectId.value, chapterId.value, baseVersion.value))
const saveStateLabel = computed(() => ({
  clean: '正式版本', pending: '等待本地保存', saving: '正在保存本地草稿',
  saved: '本地草稿已保存', error: '本地保存失败',
})[localSaveState.value])

interface TextMatch { from: number; to: number }
function textMatches(): TextMatch[] {
  if (updateTick.value < 0) return []
  const current = editor.value
  const needle = findText.value
  if (!current || !needle) return []
  const matches: TextMatch[] = []
  current.state.doc.descendants((node, position) => {
    if (!node.isText || !node.text) return
    let offset = 0
    while (offset <= node.text.length - needle.length) {
      const found = node.text.indexOf(needle, offset)
      if (found < 0) break
      matches.push({ from: position + found, to: position + found + needle.length })
      offset = found + Math.max(needle.length, 1)
    }
  })
  return matches
}
const matchCount = computed(() => textMatches().length)

function scheduleLocalSave(): void {
  if (!initialized.value) return
  localSaveState.value = 'pending'
  if (autosaveTimer) globalThis.clearTimeout(autosaveTimer)
  autosaveTimer = globalThis.setTimeout(() => void saveLocalDraft(), 2_000)
}

async function saveLocalDraft(): Promise<void> {
  if (!initialized.value || !editor.value || !dirty.value) return
  if (autosaveTimer) globalThis.clearTimeout(autosaveTimer)
  autosaveTimer = undefined
  localSaveState.value = 'saving'
  try {
    await writeChapterDraft({
      key: draftKey.value,
      projectId: projectId.value,
      chapterId: chapterId.value,
      baseVersion: baseVersion.value,
      title: title.value,
      contentText: editorText(),
      editorDocument: editor.value.getJSON(),
      updatedAt: new Date().toISOString(),
    })
    localSaveState.value = 'saved'
    localSaveError.value = ''
  } catch (error) {
    localSaveState.value = 'error'
    localSaveError.value = error instanceof Error ? error.message : '本地草稿保存失败'
  }
}

async function initializeEditor(): Promise<void> {
  const chapter = chapterQuery.data.value
  const currentEditor = editor.value
  if (!chapter || !currentEditor || initialized.value) return
  title.value = chapter.currentVersion?.title ?? chapter.title
  baseVersion.value = chapter.version
  currentEditor.commands.setContent(documentFromText(chapter.currentVersion?.content ?? ''), { emitUpdate: false })
  ensureParagraphKeys(currentEditor)
  updateTick.value += 1
  initialSignature.value = signature()
  initialized.value = true
  try {
    const localDraft = await readChapterDraft(draftKey.value)
    if (localDraft && `${localDraft.title}\u0000${localDraft.contentText}` !== initialSignature.value) {
      recoverableDraft.value = localDraft
    }
  } catch (error) {
    localSaveState.value = 'error'
    localSaveError.value = error instanceof Error ? error.message : '无法读取本地草稿'
  }
}

function recoverDraft(): void {
  if (!recoverableDraft.value || !editor.value) return
  title.value = recoverableDraft.value.title
  editor.value.commands.setContent(recoverableDraft.value.editorDocument, { emitUpdate: false })
  ensureParagraphKeys(editor.value)
  updateTick.value += 1
  recoverableDraft.value = undefined
  localSaveState.value = 'saved'
}

async function discardDraft(): Promise<void> {
  if (!recoverableDraft.value) return
  try {
    await deleteChapterDraft(recoverableDraft.value.key)
    recoverableDraft.value = undefined
    localSaveState.value = 'clean'
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '无法清理本地草稿')
  }
}

function openSaveDialog(): void {
  if (characterCount.value > 500_000) {
    ElMessage.error('正文超过后端允许的 500,000 字符上限')
    return
  }
  versionForm.summary = chapterQuery.data.value?.currentVersion?.summary ?? ''
  versionForm.changeSummary = ''
  saveDialogOpen.value = true
}

async function submitVersion(): Promise<void> {
  if (!editor.value || !title.value.trim()) return
  const oldDraftKey = draftKey.value
  try {
    const chapter = await createVersion.mutateAsync({
      title: title.value.trim(),
      content: editorText(),
      summary: versionForm.summary.trim() || null,
      changeSummary: versionForm.changeSummary.trim() || null,
      expectedVersion: baseVersion.value,
    })
    await deleteChapterDraft(oldDraftKey)
    baseVersion.value = chapter.version
    title.value = chapter.currentVersion?.title ?? chapter.title
    initialSignature.value = signature()
    updateTick.value += 1
    localSaveState.value = 'clean'
    saveDialogOpen.value = false
    ElMessage.success(`正式版本 v${chapter.currentVersionNo} 已创建`)
  } catch {
    // Problem Details is rendered in the dialog.
  }
}

async function restore(versionNo: number): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `恢复 v${versionNo} 会通过后端创建一个新版本，不会覆盖历史。`,
      '恢复章节版本',
      { confirmButtonText: '创建恢复版本', cancelButtonText: '取消' },
    )
    const oldDraftKey = draftKey.value
    const chapter = await restoreVersion.mutateAsync({
      versionNo,
      request: { expectedVersion: baseVersion.value, changeSummary: `恢复自 v${versionNo}` },
    })
    await deleteChapterDraft(oldDraftKey)
    title.value = chapter.currentVersion?.title ?? chapter.title
    editor.value?.commands.setContent(documentFromText(chapter.currentVersion?.content ?? ''), { emitUpdate: false })
    if (editor.value) ensureParagraphKeys(editor.value)
    baseVersion.value = chapter.version
    updateTick.value += 1
    initialSignature.value = signature()
    localSaveState.value = 'clean'
    ElMessage.success(`已创建 v${chapter.currentVersionNo}`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && !restoreVersion.isError.value) throw error
  }
}

function findNext(): void {
  const matches = textMatches()
  if (!editor.value || matches.length === 0) return
  activeMatch.value = (activeMatch.value + 1) % matches.length
  editor.value.chain().focus().setTextSelection(matches[activeMatch.value]!).scrollIntoView().run()
}

function replaceCurrent(): void {
  const matches = textMatches()
  if (!editor.value || matches.length === 0) return
  const index = activeMatch.value >= 0 ? activeMatch.value % matches.length : 0
  editor.value.chain().focus().insertContentAt(matches[index]!, replaceText.value).run()
  activeMatch.value = -1
  findNext()
}

function replaceAll(): void {
  const matches = textMatches()
  const currentEditor = editor.value
  if (!currentEditor || matches.length === 0) return
  const transaction = currentEditor.state.tr
  for (const match of [...matches].reverse()) {
    transaction.insertText(replaceText.value, match.from, match.to)
  }
  currentEditor.view.dispatch(transaction)
  activeMatch.value = -1
  ElMessage.success(`已替换 ${matches.length} 处`)
}

function handleWindowBlur(): void {
  void saveLocalDraft()
}

function workflowStarted(runId: string): void {
  void router.push(`/projects/${projectId.value}/chapters/${chapterId.value}/workflows/${runId}`)
}
function handleBeforeUnload(event: globalThis.BeforeUnloadEvent): void {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch([chapterQuery.data, editor], () => void initializeEditor(), { immediate: true })
watch(title, () => scheduleLocalSave())
watch(findText, () => { activeMatch.value = -1 })
watch(() => route.query.preflight, (value) => { if (value === '1') preflightOpen.value = true })
onMounted(() => {
  globalThis.addEventListener('blur', handleWindowBlur)
  globalThis.addEventListener('beforeunload', handleBeforeUnload)
})
onBeforeUnmount(() => {
  if (autosaveTimer) globalThis.clearTimeout(autosaveTimer)
  globalThis.removeEventListener('blur', handleWindowBlur)
  globalThis.removeEventListener('beforeunload', handleBeforeUnload)
})
onBeforeRouteLeave(async () => {
  if (!dirty.value) return true
  await saveLocalDraft()
  return globalThis.confirm('当前章节有尚未提交为正式版本的修改，离开后可从本地草稿恢复。确定离开吗？')
})
</script>

<template>
  <main class="chapter-editor-page" :class="{ 'is-fullscreen': fullscreen }">
    <LoadingState v-if="chapterQuery.isPending.value" label="加载章节编辑器…" />
    <ErrorState
      v-else-if="chapterQuery.isError.value"
      :error="chapterQuery.error.value"
      @retry="chapterQuery.refetch()"
    />
    <template v-else-if="chapterQuery.data.value">
      <header class="chapter-editor-header">
        <div class="chapter-editor-heading">
          <RouterLink :to="`/projects/${projectId}/chapters`">章节</RouterLink>
          <span>/</span>
          <strong>第 {{ chapterQuery.data.value.chapterNo }} 章</strong>
        </div>
        <div class="chapter-save-status" :class="`is-${localSaveState}`" role="status">
          <span>{{ saveStateLabel }}</span>
          <small>正式版本 v{{ chapterQuery.data.value.currentVersionNo }}</small>
        </div>
        <div class="chapter-header-actions">
          <button class="sw-button sw-button--secondary" type="button" @click="preflightOpen = true">开始工作流</button>
          <button class="sw-button sw-button--secondary" type="button" @click="findOpen = !findOpen">查找替换</button>
          <button class="sw-button sw-button--secondary" type="button" @click="versionsOpen = !versionsOpen">版本</button>
          <button class="sw-button sw-button--secondary" type="button" @click="branchesOpen = true">分支</button>
          <button class="sw-button sw-button--secondary" type="button" @click="impactOpen = true; impactQuery.refetch()">影响分析</button>
          <button class="sw-button sw-button--primary" type="button" :disabled="!dirty" @click="openSaveDialog">保存正式版本</button>
        </div>
      </header>

      <div v-if="recoverableDraft" class="draft-recovery-banner" role="alert">
        <div><strong>发现未提交的本地草稿</strong><span>保存于 {{ new Date(recoverableDraft.updatedAt).toLocaleString() }}</span></div>
        <button class="sw-button sw-button--primary" type="button" @click="recoverDraft">恢复草稿</button>
        <button class="sw-button sw-button--secondary" type="button" @click="discardDraft">舍弃</button>
      </div>
      <div v-if="localSaveError" class="editor-problem" role="alert">{{ localSaveError }}。编辑内容仍保留在当前页面，请勿关闭标签页。</div>

      <div class="chapter-editor-layout" :class="{ 'versions-is-open': versionsOpen }">
        <section class="chapter-document-column">
          <div v-if="findOpen" class="find-replace-bar" aria-label="查找替换">
            <label><span class="sr-only">查找</span><input v-model="findText" type="search" placeholder="查找文字" /></label>
            <span>{{ matchCount }} 处</span>
            <button type="button" :disabled="matchCount === 0" @click="findNext">下一处</button>
            <label><span class="sr-only">替换为</span><input v-model="replaceText" placeholder="替换为" /></label>
            <button type="button" :disabled="matchCount === 0" @click="replaceCurrent">替换</button>
            <button type="button" :disabled="matchCount === 0" @click="replaceAll">全部替换</button>
          </div>

          <div v-if="editor" class="editor-toolbar" role="toolbar" aria-label="正文格式">
            <button type="button" :class="{ active: editor.isActive('bold') }" @click="editor.chain().focus().toggleBold().run()"><strong>B</strong></button>
            <button type="button" :class="{ active: editor.isActive('italic') }" @click="editor.chain().focus().toggleItalic().run()"><em>I</em></button>
            <button type="button" :class="{ active: editor.isActive('strike') }" @click="editor.chain().focus().toggleStrike().run()"><s>S</s></button>
            <button type="button" :class="{ active: editor.isActive('highlight') }" @click="editor.chain().focus().toggleHighlight().run()">高亮</button>
            <button type="button" :class="{ active: editor.isActive('blockquote') }" @click="editor.chain().focus().toggleBlockquote().run()">引用</button>
            <button type="button" @click="editor.chain().focus().setHorizontalRule().run()">分隔线</button>
            <span class="toolbar-separator" />
            <button type="button" :disabled="!editor.can().undo()" @click="editor.chain().focus().undo().run()">撤销</button>
            <button type="button" :disabled="!editor.can().redo()" @click="editor.chain().focus().redo().run()">重做</button>
            <button type="button" @click="fullscreen = !fullscreen">{{ fullscreen ? '退出全屏' : '全屏' }}</button>
          </div>

          <article class="chapter-paper">
            <label class="chapter-title-field">
              <span class="sr-only">章节标题</span>
              <input v-model="title" maxlength="160" aria-label="章节标题" />
            </label>
            <EditorContent v-if="editor" :editor="editor" />
          </article>
          <footer class="chapter-editor-footer">
            <span>{{ characterCount.toLocaleString() }} 字</span>
            <span>{{ paragraphCount }} 段</span>
            <span>ParagraphKey 仅保存在本地编辑草稿中</span>
          </footer>
        </section>

        <aside v-if="versionsOpen" class="chapter-version-panel" aria-label="章节版本">
          <header><div><span>History</span><strong>版本记录</strong></div><button class="icon-button" type="button" aria-label="关闭版本面板" @click="versionsOpen = false">×</button></header>
          <ProblemAlert v-if="restoreVersion.isError.value" :error="restoreVersion.error.value" />
          <LoadingState v-if="versionsQuery.isPending.value" label="加载版本…" />
          <ErrorState v-else-if="versionsQuery.isError.value" :error="versionsQuery.error.value" @retry="versionsQuery.refetch()" />
          <div v-else class="chapter-version-list">
            <article v-for="version in versionsQuery.data.value" :key="version.id">
              <div><strong>v{{ version.versionNo }} · {{ version.title }}</strong><span>{{ new Date(version.createdAt).toLocaleString() }}</span></div>
              <p>{{ version.changeSummary || '没有变更说明' }}</p>
              <small v-if="version.restoredFromVersionNo">恢复自 v{{ version.restoredFromVersionNo }}</small>
              <button type="button" :disabled="restoreVersion.isPending.value" @click="restore(version.versionNo)">恢复为新版本</button>
            </article>
            <p v-if="!versionsQuery.data.value?.length" class="chapter-version-empty">还没有正式正文版本。</p>
          </div>
        </aside>
      </div>
    </template>

    <ElDialog v-model="saveDialogOpen" title="保存正式版本" width="min(560px, 94vw)">
      <ProblemAlert v-if="createVersion.isError.value" :error="createVersion.error.value" />
      <form id="chapter-version-form" class="sw-form" @submit.prevent="submitVersion">
        <label class="form-field"><span>章节摘要</span><textarea v-model="versionForm.summary" rows="5" maxlength="50000" /></label>
        <label class="form-field"><span>变更说明</span><input v-model="versionForm.changeSummary" maxlength="500" placeholder="例如：完成初稿" /></label>
        <p class="capability-note">提交后将调用后端创建不可变正式版本；本地 TipTap JSON 和 ParagraphKey 不在当前后端 DTO 中。</p>
      </form>
      <template #footer>
        <button class="sw-button sw-button--secondary" type="button" @click="saveDialogOpen = false">取消</button>
        <button class="sw-button sw-button--primary" form="chapter-version-form" :disabled="createVersion.isPending.value">{{ createVersion.isPending.value ? '保存中…' : '创建正式版本' }}</button>
      </template>
    </ElDialog>
    <ElDialog v-model="branchesOpen" title="章节分支" width="min(1100px, 96vw)">
      <ProblemAlert v-if="createBranch.isError.value || promoteBranch.isError.value || addBranchVersion.isError.value" :error="createBranch.error.value || promoteBranch.error.value || addBranchVersion.error.value" />
      <form class="sw-form" @submit.prevent="createBranch.mutate()">
        <div class="form-row"><label class="form-field"><span>分支名称</span><input v-model="branchForm.name" maxlength="160" required /></label><label class="form-field"><span>变更说明</span><input v-model="branchForm.changeSummary" maxlength="500" /></label></div>
        <label class="form-field"><span>分支说明</span><textarea v-model="branchForm.description" rows="2" /></label>
        <p class="capability-note">创建时会复制当前编辑器正文到独立分支；不会覆盖主线章节，也不会自动传播正典影响。</p>
        <button class="sw-button sw-button--primary" :disabled="createBranch.isPending.value">从当前正文创建分支</button>
      </form>
      <LoadingState v-if="branchesQuery.isPending.value" label="加载分支…" />
      <ErrorState v-else-if="branchesQuery.isError.value" :error="branchesQuery.error.value" @retry="branchesQuery.refetch()" />
      <div v-else class="asset-list">
        <article v-for="branch in branchesQuery.data.value" :key="branch.id" class="asset-card"><div class="asset-card-heading"><div><span class="asset-type">{{ branch.versions.length }} 个版本</span><h3>{{ branch.name }}</h3></div><span class="status-pill">{{ branch.promoted ? '已标记影响' : branch.status }}</span></div><p>{{ branch.description || '无说明' }}</p><div class="asset-actions"><button type="button" @click="compareBranch(branch)">编辑与比较</button><button v-if="!branch.promoted" type="button" @click="promoteBranch.mutate({ id: branch.id, version: branch.version })">标记为影响分析候选</button></div></article>
        <form v-if="selectedBranch" class="sw-form" @submit.prevent="addBranchVersion.mutate()">
          <div class="asset-card-heading"><div><span class="asset-type">双栏比较</span><h3>{{ selectedBranch.name }}</h3></div><span class="status-pill">MAIN 只读</span></div>
          <p class="capability-note">分支事实不会进入主线；保存只新增分支版本，提升操作只生成影响报告。</p>
          <div class="v15-detail-grid">
            <label class="form-field"><span>MAIN · {{ title }}</span><textarea :value="editorText()" rows="16" readonly aria-label="MAIN 正文只读比较" /></label>
            <div class="sw-form"><label class="form-field"><span>分支标题</span><input v-model="branchDraft.title" maxlength="160" required /></label><label class="form-field"><span>分支正文</span><textarea v-model="branchDraft.content" rows="13" maxlength="500000" required aria-label="分支正文" /></label><label class="form-field"><span>版本说明</span><input v-model="branchDraft.changeSummary" maxlength="500" /></label></div>
          </div>
          <button class="sw-button sw-button--primary" :disabled="addBranchVersion.isPending.value">保存分支新版本</button>
        </form>
      </div>
    </ElDialog>
    <ElDialog v-model="impactOpen" title="章节影响分析" width="min(760px, 94vw)">
      <div class="project-header-actions"><p class="capability-note">报告扫描已接受事实、伏笔链接和后续章节，结果来自后端数据库快照。</p><button class="sw-button sw-button--primary" :disabled="createImpact.isPending.value" @click="createImpact.mutate()">生成新报告</button></div>
      <ProblemAlert v-if="createImpact.isError.value" :error="createImpact.error.value"/>
      <LoadingState v-if="impactQuery.isFetching.value" label="加载影响报告…"/><ErrorState v-else-if="impactQuery.isError.value" :error="impactQuery.error.value" @retry="impactQuery.refetch()"/><div v-else class="asset-list"><article v-for="report in impactQuery.data.value" :key="report.id" class="asset-card"><div class="asset-card-heading"><div><span class="asset-type">{{ new Date(report.createdAt).toLocaleString() }}</span><h3>{{ report.summary }}</h3></div><span class="status-pill">{{ report.status }}</span></div><pre class="impact-json">{{ JSON.stringify(report.affected, null, 2) }}</pre></article><p v-if="!impactQuery.data.value?.length" class="muted-copy">还没有影响报告。</p></div>
    </ElDialog>
    <PreflightDialog v-if="chapterQuery.data.value && preflightOpen" v-model:open="preflightOpen" :project-id="projectId" :chapter="chapterQuery.data.value" :has-unsaved-draft="dirty" @started="workflowStarted" />
  </main>
</template>
