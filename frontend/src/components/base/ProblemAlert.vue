<script setup lang="ts">
import { computed } from 'vue'

import { HttpProblemError, problemMessage } from '@/api/errors'

const props = defineProps<{ error: unknown }>()

const fieldErrors = computed(() =>
  props.error instanceof HttpProblemError ? props.error.problem.fieldErrors : undefined,
)
const traceId = computed(() =>
  props.error instanceof HttpProblemError ? props.error.problem.traceId : undefined,
)
</script>

<template>
  <div class="problem-alert" role="alert">
    <strong>{{ problemMessage(error) }}</strong>
    <ul v-if="fieldErrors">
      <template v-for="(messages, field) in fieldErrors" :key="field">
        <li v-for="message in messages" :key="message">{{ field }}：{{ message }}</li>
      </template>
    </ul>
    <small v-if="traceId" class="trace-id">追踪号：{{ traceId }}</small>
  </div>
</template>
