<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElDialog } from 'element-plus'

import type { ChapterResponse } from '@/api/types'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import { buildPreflightChecks, projectedWorkflowTokens } from '@/features/workflows/preflight'
import { useProjectQuery } from '@/queries/projects'
import { useCostSummaryQuery } from '@/queries/usage'
import {
  useBudgetQuery,
  useModelConfigQuery,
  usePreflightChaptersQuery,
  usePreflightCharactersQuery,
  useSkillCompositionQuery,
  useStartWorkflowMutation,
} from '@/queries/workflows'

const props = defineProps<{
  open: boolean
  projectId: string
  chapter: ChapterResponse
  hasUnsavedDraft: boolean
}>()
const emit = defineEmits<{ 'update:open': [value: boolean]; started: [runId: string] }>()

const viewpointCharacterId = ref('')
const instruction = ref('按照已确认章纲生成本章，并遵守项目硬规则。')
const idempotencyKey = ref('')
const projectQuery = useProjectQuery(() => props.projectId)
const charactersQuery = usePreflightCharactersQuery(() => props.projectId)
const chaptersQuery = usePreflightChaptersQuery(() => props.projectId)
const skillsQuery = useSkillCompositionQuery(() => props.projectId, () => props.chapter.id)
const budgetQuery = useBudgetQuery(() => props.projectId)
const costsQuery = useCostSummaryQuery(() => props.projectId)
const modelsQuery = useModelConfigQuery()
const startMutation = useStartWorkflowMutation(() => props.chapter.id)

const dialogOpen = computed({ get: () => props.open, set: (value) => emit('update:open', value) })
const loading = computed(() => [projectQuery, charactersQuery, chaptersQuery, skillsQuery, budgetQuery, costsQuery, modelsQuery].some((query) => query.isPending.value))
const queryError = computed(() => [projectQuery, charactersQuery, chaptersQuery, skillsQuery, budgetQuery, costsQuery, modelsQuery].find((query) => query.isError.value)?.error.value)
const checks = computed(() => {
  if (!projectQuery.data.value || !charactersQuery.data.value || !chaptersQuery.data.value || !skillsQuery.data.value || !budgetQuery.data.value || !costsQuery.data.value || !modelsQuery.data.value) return []
  return buildPreflightChecks({
    project: projectQuery.data.value,
    chapter: props.chapter,
    chapters: chaptersQuery.data.value,
    characters: charactersQuery.data.value,
    viewpointCharacterId: viewpointCharacterId.value,
    skills: skillsQuery.data.value,
    budget: budgetQuery.data.value,
    costs: costsQuery.data.value,
    models: modelsQuery.data.value,
    hasUnsavedDraft: props.hasUnsavedDraft,
  })
})
const blockerCount = computed(() => checks.value.filter((check) => check.status === 'blocker').length)
const canStart = computed(() => !loading.value && !queryError.value && blockerCount.value === 0 && instruction.value.trim().length > 0)
const projectedTokens = computed(() => projectedWorkflowTokens(modelsQuery.data.value ?? []))

watch([() => props.open, charactersQuery.data], ([open, characters]) => {
  if (!open) return
  idempotencyKey.value = `workflow_${globalThis.crypto.randomUUID()}`
  if (!viewpointCharacterId.value) viewpointCharacterId.value = characters?.find((character) => !character.archived)?.id ?? ''
}, { immediate: true })

async function start(): Promise<void> {
  if (!canStart.value) return
  try {
    const workflow = await startMutation.mutateAsync({
      idempotencyKey: idempotencyKey.value,
      request: { viewpointCharacterId: viewpointCharacterId.value, instruction: instruction.value.trim() },
    })
    dialogOpen.value = false
    emit('started', workflow.id)
  } catch {
    // Problem Details is rendered below.
  }
}
</script>

<template>
  <ElDialog v-model="dialogOpen" title="写前预检" width="min(760px, 96vw)" destroy-on-close>
    <div class="preflight-boundary" role="note">
      后端没有“只构建上下文”接口。确认后会启动完整异步工作流，并继续执行 Planner、Writer、提取和审查；状态页会通过 Workflow SSE 和 REST 恢复运行。
    </div>
    <ProblemAlert v-if="startMutation.isError.value" :error="startMutation.error.value" />
    <div v-if="queryError" class="problem-alert" role="alert">预检数据加载失败：{{ queryError instanceof Error ? queryError.message : '未知错误' }}</div>
    <div v-if="loading" class="preflight-loading" role="status">正在核对项目、章节、人物、Skill、预算和模型配置…</div>
    <template v-else>
      <div class="preflight-summary">
        <div><span>阻塞项</span><strong :class="{ danger: blockerCount > 0 }">{{ blockerCount }}</strong></div>
        <div><span>预计最大输出 Token</span><strong>{{ projectedTokens.toLocaleString() }}</strong></div>
        <div><span>模型</span><strong>{{ modelsQuery.data.value?.length ?? 0 }} 个 Agent</strong></div>
      </div>
      <ul class="preflight-checks">
        <li v-for="check in checks" :key="check.code" :class="`is-${check.status}`">
          <span class="preflight-check-icon">{{ check.status === 'pass' ? '✓' : check.status === 'blocker' ? '!' : 'i' }}</span>
          <div><strong>{{ check.label }}</strong><small>{{ check.detail }}</small></div>
          <span>{{ check.status === 'blocker' ? 'BLOCKER' : check.status === 'server' ? '服务端校验' : '通过' }}</span>
        </li>
      </ul>
      <div class="preflight-form-grid">
        <label class="form-field"><span>视角人物</span><select v-model="viewpointCharacterId"><option value="" disabled>请选择</option><option v-for="character in charactersQuery.data.value?.filter((item) => !item.archived)" :key="character.id" :value="character.id">{{ character.name }}</option></select></label>
        <label class="form-field"><span>本次指令</span><textarea v-model="instruction" rows="4" maxlength="20000" required /></label>
      </div>
      <details class="model-config-details"><summary>查看实际模型配置</summary><dl><div v-for="model in modelsQuery.data.value" :key="model.agent"><dt>{{ model.agent }}</dt><dd>{{ model.model }} · {{ model.maxOutputTokens.toLocaleString() }} Token · {{ model.stream ? '流式' : '非流式' }}</dd></div></dl></details>
    </template>
    <template #footer>
      <button class="sw-button sw-button--secondary" type="button" @click="dialogOpen = false">取消</button>
      <button class="sw-button sw-button--primary" type="button" :disabled="!canStart || startMutation.isPending.value" @click="start">{{ startMutation.isPending.value ? '启动中…' : '启动后端工作流' }}</button>
    </template>
  </ElDialog>
</template>
