<script setup lang="ts">
import { computed } from 'vue'

import { HttpProblemError, problemMessage } from '@/api/errors'

const props = defineProps<{ error: unknown }>()
defineEmits<{ retry: [] }>()

const traceId = computed(() =>
  props.error instanceof HttpProblemError ? props.error.problem.traceId : undefined,
)
</script>

<template>
  <div class="state-panel state-panel--error" role="alert">
    <p class="state-kicker">暂时无法完成请求</p>
    <h2>{{ problemMessage(error) }}</h2>
    <p>请稍后重试。如果问题持续出现，可将错误追踪号提供给维护者。</p>
    <p v-if="error instanceof HttpProblemError" class="trace-id">追踪号：{{ traceId ?? '后端未提供' }}</p>
    <button class="sw-button sw-button--secondary" type="button" @click="$emit('retry')">
      重新加载
    </button>
  </div>
</template>
