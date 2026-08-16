<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessageBox } from 'element-plus'

import { reconstructionApi } from '@/api/endpoints/reconstruction'
import type { ReconstructionCandidate } from '@/api/types'
import ProblemAlert from '@/components/base/ProblemAlert.vue'

const props = defineProps<{
  projectId: string
  candidateType: string
  title: string
  description: string
  emptyDescription?: string
  useLabel?: string
  showRejected?: boolean
}>()
const emit = defineEmits<{ use: [candidate: ReconstructionCandidate] }>()
const client = useQueryClient()
const page = ref(1)
const pageSize = 12
const key = computed(() => ['reconstruction-module-candidates', props.projectId, props.candidateType])
const query = useQuery({
  queryKey: key,
  queryFn: () => reconstructionApi.candidates(props.projectId, { type: props.candidateType }),
})
const candidates = computed(() => (query.data.value ?? []).filter(candidate =>
  ['CANDIDATE', 'CONFLICT', 'ACCEPTED'].includes(candidate.status)
    || (props.showRejected && candidate.status === 'REJECTED'),
))
const pageCount = computed(() => Math.max(1, Math.ceil(candidates.value.length / pageSize)))
const visible = computed(() => candidates.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const refresh = async (): Promise<void> => {
  await Promise.all([
    client.invalidateQueries({ queryKey: key.value }),
    client.invalidateQueries({ queryKey: ['book-reconstruction', props.projectId] }),
  ])
}
const decision = useMutation({
  mutationFn: ({ id, approve }: { id: string; approve: boolean }) =>
    reconstructionApi.decideCandidate(props.projectId, id, approve),
  onSuccess: refresh,
})
const revoke = useMutation({
  mutationFn: ({ id, reason }: { id: string; reason: string }) =>
    reconstructionApi.revokeCandidate(props.projectId, id, reason),
  onSuccess: refresh,
})
const restore = useMutation({
  mutationFn: (id: string) => reconstructionApi.restoreCandidate(props.projectId, id),
  onSuccess: refresh,
})

function pending(candidate: ReconstructionCandidate): boolean {
  return candidate.status === 'CANDIDATE' || candidate.status === 'CONFLICT'
}

async function requestRevoke(candidate: ReconstructionCandidate): Promise<void> {
  const result = await ElMessageBox.prompt('说明撤销原因。撤销后该候选不再参与检索。', '撤销候选', {
    confirmButtonText: '确认撤销',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '请输入撤销原因',
  }).catch(() => null)
  if (result) revoke.mutate({ id: candidate.id, reason: result.value.trim() })
}
</script>

<template>
  <section v-if="query.isPending.value || candidates.length || emptyDescription" class="module-candidate-panel">
    <header>
      <div><p class="eyebrow">AI reconstruction candidates</p><h2>{{ title }}</h2><p>{{ description }}</p></div>
      <span class="status-pill">{{ candidates.length }} 条</span>
    </header>
    <ProblemAlert v-if="query.isError.value || decision.isError.value || restore.isError.value || revoke.isError.value" :error="query.error.value || decision.error.value || restore.error.value || revoke.error.value" />
    <p v-if="query.isPending.value" role="status">正在读取 AI 候选……</p>
    <p v-else-if="!candidates.length" class="candidate-empty">{{ emptyDescription }}</p>
    <div v-else class="module-candidate-list">
      <article v-for="candidate in visible" :key="candidate.id">
        <div class="candidate-labels">
          <span>{{ candidate.suggestedAction }}</span><span v-if="candidate.characterImportance">{{ candidate.characterImportance }}</span>
          <span>{{ candidate.confidence }}</span><span>{{ candidate.inferenceType }}</span>
          <span>Evidence {{ candidate.evidenceCount }}</span><span>{{ candidate.status }}</span>
        </div>
        <h3 v-if="candidate.subjectName">{{ candidate.subjectName }}</h3>
        <p>{{ candidate.content }}</p>
        <p v-if="candidate.policyReason" class="policy-reason">策略：{{ candidate.policyReason }}</p>
        <p v-if="candidate.revocationReason" class="policy-reason">已撤销：{{ candidate.revocationReason }}</p>
        <details v-if="candidate.evidenceCount"><summary>查看原文依据</summary><pre>{{ candidate.sourceAnchors }}</pre></details>
        <div class="asset-actions">
          <button v-if="useLabel && candidate.status !== 'REVOKED'" type="button" @click="emit('use', candidate)">{{ useLabel }}</button>
          <button v-if="pending(candidate)" type="button" @click="decision.mutate({ id: candidate.id, approve: true })">标记可信</button>
          <button v-if="pending(candidate)" type="button" @click="decision.mutate({ id: candidate.id, approve: false })">拒绝</button>
          <button v-if="candidate.status === 'ACCEPTED'" type="button" @click="decision.mutate({ id: candidate.id, approve: false })">取消可信</button>
          <button v-if="candidate.status === 'REJECTED'" type="button" @click="restore.mutate(candidate.id)">恢复候选</button>
          <button v-if="candidate.status === 'APPLIED'" type="button" @click="requestRevoke(candidate)">撤销应用</button>
        </div>
      </article>
    </div>
    <nav v-if="pageCount > 1" class="candidate-pagination" aria-label="AI 候选分页">
      <button type="button" :disabled="page === 1" @click="page--">上一页</button><span>{{ page }} / {{ pageCount }}</span><button type="button" :disabled="page === pageCount" @click="page++">下一页</button>
    </nav>
  </section>
</template>

<style scoped>
.module-candidate-panel { display: grid; gap: 1rem; margin-bottom: 1.25rem; padding: 1.25rem; border: 1px solid var(--sw-border-strong); border-radius: var(--sw-radius-card); background: var(--sw-bg-surface); }
.module-candidate-panel > header { display: flex; justify-content: space-between; gap: 1rem; }.module-candidate-panel h2,.module-candidate-panel p { margin-top: 0; }
.module-candidate-list { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: .75rem; }.module-candidate-list article { padding: 1rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); background: var(--sw-bg-subtle); }
.candidate-labels { display: flex; flex-wrap: wrap; gap: .4rem; font-size: .78rem; color: var(--sw-text-secondary); }.candidate-labels span { padding: .15rem .4rem; border-radius: 999px; background: var(--sw-bg-surface); }
.policy-reason { color: var(--sw-text-secondary); font-size: .9rem; }.module-candidate-list pre { max-height: 160px; overflow: auto; white-space: pre-wrap; }.candidate-empty { color: var(--sw-text-secondary); }.candidate-pagination { display: flex; justify-content: center; align-items: center; gap: 1rem; }
@media (max-width:760px){.module-candidate-list{grid-template-columns:1fr}}
</style>
