<script setup lang="ts">
withDefaults(defineProps<{ label?: string }>(), {
  label: '正在加载…',
})
</script>

<template>
  <div class="state-panel" role="status" aria-live="polite">
    <span class="loading-indicator" aria-hidden="true" />
    <span>{{ label }}</span>
  </div>
</template>
