<script setup lang="ts">
withDefaults(defineProps<{ title: string; description?: string }>(), {
  description: '',
})
</script>

<template>
  <div class="state-panel state-panel--empty">
    <div class="empty-mark" aria-hidden="true">文</div>
    <h2>{{ title }}</h2>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>
