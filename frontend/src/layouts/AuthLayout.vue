<script setup lang="ts">
import { computed } from 'vue'

import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
const themeText = computed(() =>
  ({ light: '浅色', dark: '深色', system: '跟随系统' })[themeStore.preference],
)

function cycleTheme(): void {
  const order = ['system', 'light', 'dark'] as const
  const currentIndex = order.indexOf(themeStore.preference)
  themeStore.setPreference(order[(currentIndex + 1) % order.length] ?? 'system')
}
</script>

<template>
  <div class="auth-shell">
    <header class="auth-header">
      <RouterLink class="brand-link" to="/">
        <span class="brand-symbol" aria-hidden="true">文</span>
        <span><strong>文脉</strong><small>StoryWeaver</small></span>
      </RouterLink>
      <button class="text-button" type="button" @click="cycleTheme">{{ themeText }}</button>
    </header>

    <main id="main-content" class="auth-main" tabindex="-1">
      <section class="auth-card">
        <slot />
      </section>
      <p class="service-note"><span aria-hidden="true" />连接到 StoryWeaver 后端服务</p>
    </main>
  </div>
</template>
