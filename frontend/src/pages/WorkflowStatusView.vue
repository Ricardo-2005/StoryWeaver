<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { ElDialog, ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'

import ContextPreview from '@/components/workflow/ContextPreview.vue'
import ScenePlanPanel from '@/components/workflow/ScenePlanPanel.vue'
import WorkflowApprovalPanel from '@/components/workflow/WorkflowApprovalPanel.vue'
import WorkflowStepper from '@/components/workflow/WorkflowStepper.vue'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import { documentFromText } from '@/features/chapters/chapterDocument'
import { chapterDraftKey, readChapterDraft, writeChapterDraft } from '@/features/chapters/draftStorage'
import { useWorkflowEventStream } from '@/features/workflows/useWorkflowEventStream'
import { reviewTextSegments } from '@/features/workflows/approval'
import { useChapterQuery } from '@/queries/chapters'
import {
  isTerminalWorkflow,
  useApproveWorkflowMutation,
  useBudgetQuery,
  useCancelWorkflowMutation,
  usePreflightCharactersQuery,
  useRequestRevisionMutation,
  useWorkflowQuery,
} from '@/queries/workflows'
import { useWorkflowStreamStore } from '@/stores/workflowStream'
import type { ApproveWorkflowRequest, ReviewIssueResponse } from '@/api/types'
import { v15WorkflowApi } from '@/api/endpoints/v15'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const projectId = computed(() => String(route.params.projectId ?? ''))
const chapterId = computed(() => String(route.params.chapterId ?? ''))
const runId = computed(() => String(route.params.runId ?? ''))
const attemptsQuery = useQuery({ queryKey: computed(() => ['model-attempts', runId.value]), queryFn: () => v15WorkflowApi.attempts(runId.value), refetchInterval: 10_000 })
const localRevisionForm = reactive({ startOffset: 0, endOffset: 0, replacement: '', reason: '' })
const workflowQuery = useWorkflowQuery(runId)
const chapterQuery = useChapterQuery(chapterId)
const budgetQuery = useBudgetQuery(projectId)
const charactersQuery = usePreflightCharactersQuery(projectId)
const cancelMutation = useCancelWorkflowMutation(runId)
const revisionMutation = useRequestRevisionMutation(runId)
const approvalMutation = useApproveWorkflowMutation(projectId, chapterId, runId)
const streamStore = useWorkflowStreamStore()
const stream = useWorkflowEventStream(runId)
const { connection, renderedText, characterCount, lastEventId, lastHeartbeatAt, usage, errorMessage, warning } = storeToRefs(streamStore)
const workflow = computed(() => workflowQuery.data.value)
const localRevisionMutation = useMutation({
  mutationFn: () => v15WorkflowApi.localRevision(runId.value, { expectedVersion: workflow.value?.version ?? 0, ...localRevisionForm }),
  onSuccess: async revised => { streamStore.completeFromSnapshot(revised.draftContent); stream.start(); await queryClient.invalidateQueries({ queryKey: ['workflow', runId.value] }); Object.assign(localRevisionForm, { startOffset: 0, endOffset: 0, replacement: '', reason: '' }); ElMessage.success('局部修订已提交并重新进入抽取审校') },
})
const active = computed(() => workflow.value ? !isTerminalWorkflow(workflow.value) : false)
const blocked = computed(() => workflow.value?.status === 'BLOCKED' || workflow.value?.contextPacket?.stale)
const blockerIssues = computed(() => workflow.value?.reviewIssues.filter((issue) => issue.blocking && !issue.resolved) ?? [])
const draftScroller = ref<globalThis.HTMLElement>()
const followingOutput = ref(true)
const handoffPending = ref(false)
const selectedIssueId = ref('')
const revisionOpen = ref(false)
const revisionDraft = ref('')
const revisionBaseline = ref('')
const revisionSuggestion = ref('')
const now = ref(Date.now())
const statusLabels: Record<string, string> = {
  CREATED: '已创建', PREFLIGHT: '写前预检', CONTEXT_READY: '上下文就绪', PLANNING: '场景规划中', PLAN_READY: '计划就绪', WRITING: '正文生成中', TEXT_READY: '正文已生成', EXTRACTING: '事实提取中', VALIDATING: '确定性校验', REVIEWING: '一致性审查', WAITING_APPROVAL: '等待人工确认', REVISION_REQUIRED: '需要修订', COMMITTING: '提交中', COMPLETED: '已完成', BLOCKED: '已阻塞', FAILED: '失败', CANCELLED: '已取消', ROLLED_BACK: '已回滚',
}
const connectionLabels = {
  idle: '事件流待连接', connecting: '事件流连接中', open: '事件流已连接',
  reconnecting: '事件流重连中', closed: '事件流已关闭', failed: '事件流已中断',
}
const completionTokens = computed(() => typeof usage.value?.completionTokens === 'number' ? usage.value.completionTokens : null)
const selectedIssue = computed(() => workflow.value?.reviewIssues.find((issue) => issue.id === selectedIssueId.value))
const markedDraft = computed(() => reviewTextSegments(renderedText.value, selectedIssue.value?.evidence))
const revisionDirty = computed(() => revisionDraft.value !== revisionBaseline.value)
const canRequestRevision = computed(() => workflow.value?.status === 'WAITING_APPROVAL' || workflow.value?.status === 'REVISION_REQUIRED')
const heartbeatLabel = computed(() => {
  if (!lastHeartbeatAt.value) return '等待事件心跳'
  const seconds = Math.max(0, Math.floor((now.value - new Date(lastHeartbeatAt.value).getTime()) / 1_000))
  return seconds < 15 ? '心跳正常' : `${seconds} 秒前收到心跳`
})
const workflowDuration = computed(() => {
  const startedAt = workflow.value?.startedAt
  if (!startedAt) return '尚未开始'
  const end = workflow.value?.finishedAt ? new Date(workflow.value.finishedAt).getTime() : now.value
  const milliseconds = Math.max(0, end - new Date(startedAt).getTime())
  if (milliseconds < 1_000) return `${milliseconds} ms`
  if (milliseconds < 60_000) return `${(milliseconds / 1_000).toFixed(1)} s`
  return `${Math.floor(milliseconds / 60_000)}m ${Math.floor((milliseconds % 60_000) / 1_000)}s`
})
const heartbeatTimer = globalThis.setInterval(() => { now.value = Date.now() }, 15_000)

watch(workflow, (current) => {
  if (!current) return
  streamStore.prepare(current.id)
  if (isTerminalWorkflow(current)) stream.stop(current.draftContent)
  else stream.start()
}, { immediate: true })

watch(characterCount, async () => {
  if (!followingOutput.value) return
  await nextTick()
  scrollToOutput()
})

function onDraftScroll(): void {
  const element = draftScroller.value
  if (!element) return
  followingOutput.value = element.scrollHeight - element.scrollTop - element.clientHeight < 48
}

function scrollToOutput(): void {
  const element = draftScroller.value
  if (!element) return
  element.scrollTop = element.scrollHeight
  followingOutput.value = true
}

function evidenceAvailable(issue: ReviewIssueResponse): boolean {
  return Boolean(issue.evidence.trim() && renderedText.value.includes(issue.evidence.trim()))
}

async function jumpToIssue(issue: ReviewIssueResponse): Promise<void> {
  selectedIssueId.value = issue.id
  await nextTick()
  const marker = draftScroller.value?.querySelector<globalThis.HTMLElement>('.review-mark-active')
  if (!marker) {
    ElMessage.warning('后端证据不是当前正文的精确片段，无法自动定位')
    return
  }
  marker.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function openRevision(issue?: ReviewIssueResponse): void {
  const draft = workflow.value?.draftContent ?? renderedText.value
  revisionDraft.value = draft
  revisionBaseline.value = draft
  revisionSuggestion.value = issue?.suggestion ?? ''
  if (issue) selectedIssueId.value = issue.id
  revisionMutation.reset()
  revisionOpen.value = true
}

async function submitRevision(): Promise<void> {
  if (!revisionDraft.value.trim() || revisionDraft.value.length > 500_000) return
  try {
    const revised = await revisionMutation.mutateAsync({ revisedDraft: revisionDraft.value })
    streamStore.completeFromSnapshot(revised.draftContent)
    stream.start()
    revisionOpen.value = false
    selectedIssueId.value = ''
    ElMessage.success('修订正文已提交，后端正在重新提取和审查')
  } catch { /* The dialog retains the draft and renders Problem Details. */ }
}

async function approve(request: ApproveWorkflowRequest): Promise<void> {
  try {
    const completed = await approvalMutation.mutateAsync(request)
    stream.stop(completed.draftContent)
    ElMessage.success(`原子提交完成，已创建章节版本 v${completed.committedVersionNo}`)
  } catch { /* The approval panel retains all user input. */ }
}

async function stopGeneration(): Promise<void> {
  const cancelled = await cancelMutation.mutateAsync()
  stream.stop(cancelled.draftContent)
  ElMessage.success('已停止生成，收到的运行态草稿已保留')
}

async function cancel(): Promise<void> {
  try { await stopGeneration() } catch { /* Problem Details is rendered below. */ }
}

async function handoffToEditor(): Promise<void> {
  if (!renderedText.value || !chapterQuery.data.value) return
  handoffPending.value = true
  try {
    if (active.value) await stopGeneration()
    const chapter = chapterQuery.data.value
    const key = chapterDraftKey(projectId.value, chapterId.value, chapter.version)
    const existing = await readChapterDraft(key)
    if (existing && existing.contentText !== renderedText.value) {
      await ElMessageBox.confirm(
        '章节编辑器中已有未提交的本地草稿。继续会用当前运行态正文替换它。',
        '替换本地草稿？',
        { confirmButtonText: '替换并接管', cancelButtonText: '保留原草稿', type: 'warning' },
      )
    }
    await writeChapterDraft({
      key,
      projectId: projectId.value,
      chapterId: chapterId.value,
      baseVersion: chapter.version,
      title: chapter.currentVersion?.title ?? chapter.title,
      contentText: renderedText.value,
      editorDocument: documentFromText(renderedText.value),
      updatedAt: new Date().toISOString(),
    })
    await router.push(`/projects/${projectId.value}/chapters/${chapterId.value}`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '无法接管运行态草稿')
    }
  } finally {
    handoffPending.value = false
  }
}

onBeforeUnmount(() => globalThis.clearInterval(heartbeatTimer))
</script>

<template>
  <main class="workflow-status-page">
    <LoadingState v-if="workflowQuery.isPending.value" label="加载工作流状态…" />
    <ErrorState v-else-if="workflowQuery.isError.value" :error="workflowQuery.error.value" @retry="workflowQuery.refetch()" />
    <template v-else-if="workflow">
      <header class="workflow-status-header">
        <div><RouterLink :to="`/projects/${projectId}/chapters/${chapterId}`">返回章节编辑器</RouterLink><p class="eyebrow">Workflow</p><h1 tabindex="-1">{{ statusLabels[workflow.status] || workflow.status }}</h1><span>Run {{ workflow.id }}</span></div>
        <div class="workflow-status-actions"><span class="workflow-live-indicator" :class="{ active: connection === 'open' }">{{ connectionLabels[connection] }}</span><button v-if="active" class="sw-button sw-button--danger" type="button" :disabled="cancelMutation.isPending.value" @click="cancel">{{ cancelMutation.isPending.value ? '停止中…' : '停止生成' }}</button></div>
      </header>
      <ProblemAlert v-if="cancelMutation.isError.value" :error="cancelMutation.error.value" />
      <section v-if="connection === 'failed'" class="workflow-stream-interruption" role="alert">
        <div><strong>生成连接已中断</strong><span>已保留 {{ characterCount.toLocaleString() }} 字运行态草稿。{{ errorMessage }}</span></div>
        <div><button class="sw-button sw-button--secondary" type="button" @click="stream.reconnect()">重新连接事件流</button><button v-if="renderedText" class="sw-button sw-button--primary" type="button" :disabled="handoffPending" @click="handoffToEditor">保留草稿并人工编辑</button></div>
      </section>
      <section v-if="workflow.failureCode || blocked" class="workflow-blocker" role="alert">
        <div><span>BLOCKER</span><h2>{{ workflow.failureCode || 'context_packet_stale' }}</h2><p>{{ workflow.failureMessage || 'Context Packet 已过期，当前运行不能继续。' }}</p></div>
        <RouterLink class="sw-button sw-button--primary" :to="{ path: `/projects/${projectId}/chapters/${chapterId}`, query: { preflight: '1' } }">返回并重新预检</RouterLink>
      </section>
      <section v-if="workflow.status === 'WAITING_APPROVAL'" class="workflow-boundary-note"><strong>工作流正在等待人工确认</strong><span>核对审查证据与候选事实后，由后端在一个事务中提交章节版本和一致性变更。</span></section>
      <section v-if="workflow.status === 'COMPLETED'" class="workflow-commit-result" role="status"><div><p class="eyebrow">Atomic Commit</p><h2>章节已原子提交</h2><span>正式版本 v{{ workflow.committedVersionNo }} · {{ workflow.approvedAt ? new Date(workflow.approvedAt).toLocaleString() : '审批时间未知' }}</span></div><RouterLink class="sw-button sw-button--primary" :to="`/projects/${projectId}/chapters/${chapterId}`">查看正式版本</RouterLink></section>
      <div class="workflow-dashboard">
        <aside class="workflow-step-panel"><header><p class="eyebrow">Progress</p><h2>执行步骤</h2></header><WorkflowStepper :workflow="workflow" /><dl class="workflow-run-meta"><div><dt>恢复次数</dt><dd>{{ workflow.recoveryCount }}</dd></div><div><dt>事件游标</dt><dd>{{ lastEventId || '尚无' }}</dd></div><div><dt>连接心跳</dt><dd>{{ heartbeatLabel }}</dd></div><div><dt>端到端耗时</dt><dd>{{ workflowDuration }}</dd></div></dl></aside>
        <div class="workflow-main-panels">
          <section class="workflow-panel streaming-draft-panel">
            <header><div><p class="eyebrow">Writer Stream</p><h2>运行态正文</h2></div><div class="streaming-draft-stats"><span>{{ characterCount.toLocaleString() }} 字符</span><span v-if="completionTokens !== null">{{ completionTokens.toLocaleString() }} Tokens</span></div></header>
            <div v-if="warning" class="streaming-draft-warning" role="status">{{ warning }}</div>
            <div ref="draftScroller" class="streaming-draft-scroll" tabindex="0" aria-label="Writer 运行态正文" @scroll="onDraftScroll">
              <div v-if="renderedText" class="streaming-draft-copy"><template v-for="(segment, index) in markedDraft" :key="index"><mark v-if="segment.highlighted" class="review-mark-active">{{ segment.text }}</mark><span v-else>{{ segment.text }}</span></template><span v-if="connection === 'open'" class="streaming-caret" aria-hidden="true"></span></div>
              <div v-else class="workflow-empty">{{ active ? '等待 Writer 输出正文…' : '本次工作流没有运行态正文。' }}</div>
            </div>
            <footer><span>{{ heartbeatLabel }} · {{ connectionLabels[connection] }}</span><div><button v-if="!followingOutput && renderedText" class="sw-button sw-button--secondary" type="button" @click="scrollToOutput">返回生成位置</button><button v-if="renderedText" class="sw-button sw-button--secondary" type="button" :disabled="handoffPending" @click="handoffToEditor">{{ handoffPending ? '准备编辑器…' : '接管编辑' }}</button></div></footer>
          </section>
          <ContextPreview v-if="workflow.contextPacket" :packet="workflow.contextPacket" :budget="budgetQuery.data.value" />
          <section v-else class="workflow-panel"><header><div><p class="eyebrow">Context Preview</p><h2>上下文包</h2></div></header><div class="workflow-empty">上下文尚未构建完成。</div></section>
          <ScenePlanPanel :plan="workflow.plan" />
          <section v-if="workflow.reviewIssues.length || workflow.status === 'WAITING_APPROVAL'" class="workflow-panel review-center-panel">
            <header><div><p class="eyebrow">Consistency Review</p><h2>审查问题</h2></div><div class="review-header-actions"><span class="status-pill" :class="{ danger: blockerIssues.length }">{{ workflow.reviewIssues.length }} 项 · {{ blockerIssues.length }} BLOCKER</span><button v-if="canRequestRevision" class="sw-button sw-button--secondary" type="button" @click="openRevision()">修订并重新提取</button></div></header>
            <div v-if="workflow.reviewIssues.length" class="review-issue-grid">
              <article v-for="issue in workflow.reviewIssues" :key="issue.id" class="review-issue-card" :class="[{ active: selectedIssueId === issue.id }, `severity-${issue.severity.toLowerCase()}`]">
                <header><div><span>{{ issue.severity }}</span><small>{{ issue.source }} · {{ issue.category }}</small></div><strong v-if="issue.blocking && !issue.resolved">阻止提交</strong></header>
                <h3>{{ issue.message }}</h3>
                <dl><div><dt>当前正文证据</dt><dd>{{ issue.evidence || '后端未提供' }}</dd></div><div v-if="issue.historicalEvidence"><dt>历史证据</dt><dd>{{ issue.historicalEvidence }}</dd></div><div v-if="issue.suggestion"><dt>建议</dt><dd>{{ issue.suggestion }}</dd></div></dl>
                <footer><button class="sw-button sw-button--secondary" type="button" :disabled="!evidenceAvailable(issue)" @click="jumpToIssue(issue)">跳转正文证据</button><button v-if="canRequestRevision" class="sw-button sw-button--secondary" type="button" @click="openRevision(issue)">根据建议修订</button></footer>
              </article>
            </div>
            <div v-else class="workflow-empty">本次审查没有返回问题。</div>
          </section>
          <WorkflowApprovalPanel v-if="workflow.status === 'WAITING_APPROVAL'" :workflow="workflow" :characters="charactersQuery.data.value ?? []" :pending="approvalMutation.isPending.value" :error="approvalMutation.error.value" @approve="approve" />
        </div>
      </div>
    </template>
    <section v-if="workflow" class="workflow-v15-panels">
      <article class="workflow-panel"><header><div><p class="eyebrow">Model fallback</p><h2>模型尝试审计</h2></div></header><LoadingState v-if="attemptsQuery.isPending.value" label="加载模型尝试…"/><ErrorState v-else-if="attemptsQuery.isError.value" :error="attemptsQuery.error.value" @retry="attemptsQuery.refetch()"/><div v-else-if="attemptsQuery.data.value?.length" class="usage-table-wrap"><table><thead><tr><th>Agent</th><th>模型</th><th>尝试</th><th>状态</th><th>耗时</th></tr></thead><tbody><tr v-for="attempt in attemptsQuery.data.value" :key="attempt.id"><td>{{ attempt.agent }}</td><td>{{ attempt.model }}</td><td>{{ attempt.attempts }}</td><td>{{ attempt.status }}</td><td>{{ attempt.durationMillis }} ms</td></tr></tbody></table></div><div v-else class="workflow-empty">当前运行尚无模型调用记录。</div></article>
      <article v-if="workflow.status === 'WAITING_APPROVAL' || workflow.status === 'REVISION_REQUIRED'" class="workflow-panel"><header><div><p class="eyebrow">Local revision</p><h2>局部修订（最多 15%）</h2></div></header><form class="sw-form workflow-local-revision" @submit.prevent="localRevisionMutation.mutate()"><ProblemAlert v-if="localRevisionMutation.isError.value" :error="localRevisionMutation.error.value"/><div class="form-row"><label class="form-field"><span>开始偏移</span><input v-model.number="localRevisionForm.startOffset" type="number" min="0" required/></label><label class="form-field"><span>结束偏移</span><input v-model.number="localRevisionForm.endOffset" type="number" :min="localRevisionForm.startOffset" required/></label></div><label class="form-field"><span>替换文字</span><textarea v-model="localRevisionForm.replacement" rows="5" maxlength="60000"/></label><label class="form-field"><span>修订原因</span><input v-model="localRevisionForm.reason" maxlength="500" required/></label><button class="sw-button sw-button--primary" :disabled="localRevisionMutation.isPending.value">提交局部修订</button></form></article>
    </section>
    <ElDialog v-model="revisionOpen" width="min(920px, 94vw)" title="修订正文并重新提取" destroy-on-close>
      <div class="revision-dialog-body">
        <div v-if="revisionSuggestion" class="revision-suggestion"><strong>Reviewer 建议</strong><span>{{ revisionSuggestion }}</span></div>
        <p>后端没有局部 Patch 接口；提交后会以这份完整正文替换运行态草稿，并重新执行事实提取、确定性校验和审查。</p>
        <textarea v-model="revisionDraft" aria-label="修订后的完整正文" maxlength="500000"></textarea>
        <div class="revision-dialog-meta"><span>{{ revisionDraft.length.toLocaleString() }} / 500,000 字符</span><strong v-if="revisionDirty">正文已修改，必须重新提取后才能审批</strong></div>
        <ProblemAlert v-if="revisionMutation.isError.value" :error="revisionMutation.error.value" />
      </div>
      <template #footer><button class="sw-button sw-button--secondary" type="button" @click="revisionOpen = false">取消</button><button class="sw-button sw-button--primary" type="button" :disabled="revisionMutation.isPending.value || !revisionDraft.trim()" @click="submitRevision">{{ revisionMutation.isPending.value ? '提交中…' : revisionDirty ? '提交并重新提取' : '使用当前正文重新提取' }}</button></template>
    </ElDialog>
  </main>
</template>
