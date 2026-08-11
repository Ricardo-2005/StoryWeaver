<script setup lang="ts">
import { onMounted } from 'vue'

import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

onMounted(() => {
  themeStore.initialize()
})

function skipToContent(): void {
  document.querySelector<HTMLElement>('#main-content')?.focus()
}
</script>

<template>
  <a
    class="skip-link"
    href="#main-content"
    @click.prevent="skipToContent"
  >跳到主要内容</a>
  <RouterView />
</template>
