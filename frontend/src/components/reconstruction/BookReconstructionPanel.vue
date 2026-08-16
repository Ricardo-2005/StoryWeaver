<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import { reconstructionApi } from '@/api/endpoints/reconstruction'
import type { ReconstructionJob, ReconstructionMode, ReconstructionOptions } from '@/api/types'
import ProblemAlert from '@/components/base/ProblemAlert.vue'

const props = defineProps<{ projectId: string; projectName?: string }>()
const queryClient = useQueryClient()
const queryKey = computed(() => ['book-reconstruction', props.projectId])
const options = reactive<ReconstructionOptions>({
  mode: 'STANDARD',
  includeSkillDistillation: true,
  includeForeshadowing: true,
})
const maxBudget = ref<number | null>(null)
const showReview = ref(false)

const statusQuery = useQuery({
  queryKey,
  queryFn: () => reconstructionApi.status(props.projectId),
  enabled: () => Boolean(props.projectId),
  refetchInterval: query => isRunning((query.state.data as ReconstructionJob | undefined)?.status) ? 3_000 : false,
})
const estimateQuery = useQuery({
  queryKey: computed(() => ['book-reconstruction-estimate', props.projectId, options.mode, options.includeSkillDistillation, options.includeForeshadowing]),
  queryFn: () => reconstructionApi.estimate(props.projectId, { ...options }),
  enabled: () => Boolean(props.projectId && statusQuery.data.value?.status === 'NOT_ANALYZED'),
})
const candidateQuery = useQuery({
  queryKey: computed(() => ['book-reconstruction-candidates', props.projectId]),
  queryFn: () => reconstructionApi.candidates(props.projectId),
  enabled: () => Boolean(showReview.value && statusQuery.data.value?.id),
})

function updateJob(value: ReconstructionJob): void {
  queryClient.setQueryData(queryKey.value, value)
}

const startMutation = useMutation({
  mutationFn: () => reconstructionApi.start(props.projectId, { ...options, maxBudget: maxBudget.value }),
  onSuccess: updateJob,
})
const controlMutation = useMutation({
  mutationFn: (action: 'pause' | 'resume' | 'cancel' | 'retry' | 'safe') => {
    if (action === 'pause') return reconstructionApi.pause(props.projectId)
    if (action === 'resume') return reconstructionApi.resume(props.projectId, maxBudget.value)
    if (action === 'retry') return reconstructionApi.retryFailed(props.projectId)
    if (action === 'safe') return reconstructionApi.approveSafe(props.projectId)
    return reconstructionApi.cancel(props.projectId)
  },
  onSuccess: async (value) => {
    updateJob(value)
    await candidateQuery.refetch()
  },
})
const decisionMutation = useMutation({
  mutationFn: ({ id, approve }: { id: string; approve: boolean }) =>
    reconstructionApi.decideCandidate(props.projectId, id, approve),
  onSuccess: async () => {
    await Promise.all([candidateQuery.refetch(), statusQuery.refetch()])
  },
})

const job = computed(() => statusQuery.data.value)
const progressPercent = computed(() => Math.min(100, Math.floor((job.value?.progress ?? 0) * 100)))
const anyError = computed(() => statusQuery.error.value ?? estimateQuery.error.value
  ?? startMutation.error.value ?? controlMutation.error.value ?? decisionMutation.error.value)

function isRunning(status?: string): boolean {
  return Boolean(status && [
    'QUEUED', 'PREPROCESSING', 'CHAPTER_ANALYSIS', 'VOLUME_AGGREGATION',
    'ENTITY_RESOLUTION', 'GLOBAL_RECONSTRUCTION', 'FORESHADOW_ANALYSIS',
    'SKILL_DISTILLATION', 'VALIDATING', 'APPLYING',
  ].includes(status))
}

function money(value: number | null | undefined, currency: string | null | undefined): string {
  if (value == null) return '暂不可计价'
  return `${currency ?? 'CNY'} ${value.toFixed(4)}`
}

function modeLabel(mode: ReconstructionMode): string {
  return ({ QUICK: '快速', STANDARD: '标准（推荐）', DEEP: '深度' })[mode]
}
</script>

<template>
  <section class="reconstruction-panel" aria-labelledby="reconstruction-title">
    <header>
      <div>
        <p class="eyebrow">Hierarchical reconstruction</p>
        <h2 id="reconstruction-title">✨ AI 自动构建完整项目</h2>
        <p>按章节与 Chunk 分层分析，不改写导入原文；全书归并后自动创建可信人物卡，其余重要结果进入 Candidate 审核区。</p>
      </div>
      <span v-if="job" class="status-pill">{{ job.status }}</span>
    </header>

    <ProblemAlert v-if="anyError" :error="anyError" />

    <div v-if="statusQuery.isPending.value" class="reconstruction-loading" role="status">正在读取重建状态…</div>

    <template v-else-if="job?.status === 'NOT_ANALYZED'">
      <div class="mode-grid" aria-label="分析模式">
        <label v-for="mode in (['QUICK','STANDARD','DEEP'] as ReconstructionMode[])" :key="mode" :class="{ selected: options.mode === mode }">
          <input v-model="options.mode" type="radio" :value="mode" />
          <strong>{{ modeLabel(mode) }}</strong>
          <small>{{ mode === 'QUICK' ? '概览、主要实体、章节摘要' : mode === 'STANDARD' ? '人物、世界书、反向大纲、伏笔与 Skill' : '增加知识边界、物品流转与细粒度事实' }}</small>
        </label>
      </div>
      <div class="reconstruction-options">
        <label><input v-model="options.includeForeshadowing" type="checkbox" />跨章伏笔候选</label>
        <label><input v-model="options.includeSkillDistillation" type="checkbox" />Project-local Skill 候选</label>
      </div>
      <div v-if="estimateQuery.data.value" class="estimate-card">
        <strong>启动前预估</strong>
        <dl>
          <div><dt>章节 / Chunk</dt><dd>{{ estimateQuery.data.value.chapters }} / {{ estimateQuery.data.value.chunks }}</dd></div>
          <div><dt>预计调用</dt><dd>{{ estimateQuery.data.value.estimatedCalls }}</dd></div>
          <div><dt>预计 Token</dt><dd>{{ (estimateQuery.data.value.estimatedInputTokens + estimateQuery.data.value.estimatedOutputTokens).toLocaleString() }}</dd></div>
          <div><dt>预计费用区间</dt><dd>{{ money(estimateQuery.data.value.estimatedCostMin, estimateQuery.data.value.currency) }} — {{ money(estimateQuery.data.value.estimatedCostMax, estimateQuery.data.value.currency) }}</dd></div>
        </dl>
        <label class="budget-field"><span>最大分析预算（可选）</span><input v-model.number="maxBudget" type="number" min="0" step="0.01" placeholder="达到后自动暂停" /></label>
        <small v-if="estimateQuery.data.value.unpriced">当前模型没有可用价格规则，实际 Usage 仍会记录，但无法可靠估算费用。</small>
      </div>
      <p class="cost-confirmation">只有点击开始后才会调用模型并产生费用。</p>
      <button class="sw-button sw-button--primary" type="button" :disabled="startMutation.isPending.value || estimateQuery.isPending.value" @click="startMutation.mutate()">
        {{ startMutation.isPending.value ? '正在创建后台任务…' : '确认费用并开始分析' }}
      </button>
    </template>

    <template v-else-if="job">
      <div class="progress-card" role="status" aria-live="polite">
        <div class="progress-heading"><strong>正在理解《{{ projectName || '导入作品' }}》</strong><span>{{ progressPercent }}%</span></div>
        <progress :value="job.processedChunks" :max="Math.max(1, job.totalChunks)">{{ progressPercent }}%</progress>
        <p>{{ job.processedChunks }} / {{ job.totalChunks }} Chunk · 当前阶段：{{ job.currentStep }}<template v-if="job.processedChunks === job.totalChunks && isRunning(job.status)"> · Chunk 已完成，正在进行全书聚合</template></p>
        <dl>
          <div><dt>候选</dt><dd>{{ job.candidateCount }}</dd></div>
          <div><dt>待审核 / 冲突</dt><dd>{{ job.pendingCandidates }} / {{ job.conflicts }}</dd></div>
          <div><dt>失败章节</dt><dd>{{ job.failedChapters }}</dd></div>
          <div><dt>实际 Token</dt><dd>{{ (job.actualInputTokens + job.actualOutputTokens + job.actualReasoningTokens).toLocaleString() }}</dd></div>
          <div><dt>实际费用</dt><dd>{{ money(job.actualCost, job.currency) }}</dd></div>
          <div><dt>真实重试</dt><dd>{{ job.retryCount }}</dd></div>
        </dl>
      </div>
      <p v-if="job.errorMessage" class="reconstruction-error" role="alert">{{ job.errorMessage }}</p>
      <div class="reconstruction-actions">
        <button v-if="isRunning(job.status)" type="button" @click="controlMutation.mutate('pause')">暂停</button>
        <button v-if="['PAUSED','PAUSED_BUDGET'].includes(job.status)" type="button" @click="controlMutation.mutate('resume')">恢复</button>
        <button v-if="job.status === 'PARTIAL' && job.failedChapters" type="button" @click="controlMutation.mutate('retry')">重试失败章节</button>
        <button v-if="!['COMPLETED','CANCELLED'].includes(job.status)" type="button" @click="controlMutation.mutate('cancel')">取消</button>
        <button v-if="['WAITING_REVIEW','COMPLETED'].includes(job.status)" type="button" @click="showReview = !showReview">{{ showReview ? '收起候选' : '审核分析结果' }}</button>
        <button v-if="job.status === 'WAITING_REVIEW'" class="sw-button sw-button--primary" type="button" @click="controlMutation.mutate('safe')">一键应用安全结果</button>
      </div>

      <div v-if="showReview" class="candidate-review">
        <p>章节摘要和明确事件标记为低风险；人物关系、硬规则、知识、物品归属、伏笔与 Skill 必须逐项确认。</p>
        <article v-for="candidate in candidateQuery.data.value" :key="candidate.id">
          <div class="candidate-meta">
            <span>{{ candidate.candidateType }}</span><span>{{ candidate.confidence }}</span><span>{{ candidate.inferenceType }}</span><span>Evidence {{ candidate.evidenceCount }}</span><span v-if="candidate.safeToApply">可安全应用</span>
          </div>
          <p>{{ candidate.content }}</p>
          <details v-if="candidate.evidenceCount"><summary>查看依据</summary><pre>{{ candidate.sourceAnchors }}</pre></details>
          <div v-if="['CANDIDATE','CONFLICT'].includes(candidate.status)" class="candidate-actions">
            <button type="button" @click="decisionMutation.mutate({ id: candidate.id, approve: true })">确认</button>
            <button type="button" @click="decisionMutation.mutate({ id: candidate.id, approve: false })">拒绝</button>
          </div>
          <span v-else class="status-pill">{{ candidate.status }}</span>
        </article>
      </div>
    </template>
  </section>
</template>

<style scoped>
.reconstruction-panel { display: grid; gap: 1rem; padding: 1.5rem; border: 1px solid var(--sw-border-strong); border-radius: var(--sw-radius-card); background: var(--sw-bg-surface); }
.reconstruction-panel > header { display: flex; justify-content: space-between; gap: 1rem; }
.reconstruction-panel h2, .reconstruction-panel p { margin-top: 0; }
.mode-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: .75rem; }
.mode-grid label { display: grid; grid-template-columns: auto 1fr; gap: .25rem .5rem; padding: 1rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); cursor: pointer; }
.mode-grid label.selected { border-color: var(--sw-accent); background: var(--sw-accent-soft); }
.mode-grid small { grid-column: 2; color: var(--sw-text-secondary); }
.reconstruction-options, .reconstruction-actions, .candidate-actions { display: flex; flex-wrap: wrap; gap: .75rem; }
.estimate-card, .progress-card { display: grid; gap: 1rem; padding: 1rem; border-radius: var(--sw-radius-control); background: var(--sw-bg-subtle); }
.estimate-card dl, .progress-card dl { display: grid; grid-template-columns: repeat(3, 1fr); gap: .75rem; margin: 0; }
.estimate-card dl div, .progress-card dl div { display: grid; gap: .25rem; }
dt { color: var(--sw-text-secondary); font-size: .85rem; } dd { margin: 0; font-weight: 650; }
.budget-field { display: flex; align-items: center; gap: .75rem; }.budget-field input { max-width: 180px; }
.cost-confirmation { color: var(--sw-warning-text); }
.progress-heading { display: flex; justify-content: space-between; }.progress-card progress { width: 100%; accent-color: var(--sw-accent); }
.reconstruction-error { color: var(--sw-danger); }
.candidate-review { display: grid; gap: .75rem; }
.candidate-review article { padding: 1rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); }
.candidate-meta { display: flex; flex-wrap: wrap; gap: .5rem; color: var(--sw-text-secondary); font-size: .8rem; }
.candidate-meta span { padding: .2rem .45rem; border-radius: 999px; background: var(--sw-bg-subtle); }
.candidate-review pre { max-height: 180px; overflow: auto; white-space: pre-wrap; }
@media (max-width: 760px) { .mode-grid, .estimate-card dl, .progress-card dl { grid-template-columns: 1fr; } }
</style>
