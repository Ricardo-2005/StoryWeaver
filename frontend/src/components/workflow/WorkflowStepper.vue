<script setup lang="ts">
import { computed } from 'vue'

import type { WorkflowResponse, WorkflowStepStatus } from '@/api/types'

const props = defineProps<{ workflow: WorkflowResponse }>()
const definitions = [
  ['PREFLIGHT', '写前预检'], ['CONTEXT', '上下文构建'], ['PLANNING', '场景规划'],
  ['WRITING', '正文生成'], ['EXTRACTING', '事实提取'], ['VALIDATING', '确定性校验'], ['REVIEWING', '一致性审查'],
] as const

const steps = computed(() => definitions.map(([key, label]) => {
  const actual = props.workflow.steps.find((step) => step.stepName === key)
  return {
    key,
    label,
    status: actual?.status ?? 'PENDING' as WorkflowStepStatus | 'PENDING',
    attempt: actual?.attempt ?? 0,
    error: actual?.errorMessage,
  }
}))
</script>

<template>
  <ol class="workflow-stepper" aria-label="工作流步骤">
    <li v-for="step in steps" :key="step.key" :class="`is-${step.status.toLowerCase()}`">
      <span class="workflow-step-mark">{{ step.status === 'COMPLETED' ? '✓' : step.status === 'RUNNING' ? '●' : step.status === 'FAILED' ? '!' : '○' }}</span>
      <div><strong>{{ step.label }}</strong><small>{{ step.status }}<template v-if="step.attempt > 1"> · 第 {{ step.attempt }} 次尝试</template></small><p v-if="step.error">{{ step.error }}</p></div>
    </li>
  </ol>
</template>
