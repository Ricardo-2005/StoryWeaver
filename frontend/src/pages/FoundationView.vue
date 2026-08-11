<script setup lang="ts">
import { ElRadioButton, ElRadioGroup } from 'element-plus'
import { computed } from 'vue'

import { useThemeStore, type ThemePreference } from '@/stores/theme'

const themeStore = useThemeStore()

const selectedTheme = computed({
  get: () => themeStore.preference,
  set: (value: ThemePreference) => themeStore.setPreference(value),
})

const foundations = [
  'Vue 3 + TypeScript + Vite',
  'Vue Router + Pinia',
  'TanStack Query + Element Plus',
  'Vitest + Playwright',
]
</script>

<template>

  <main id="main-content" class="foundation-page" tabindex="-1">
    <section class="foundation-card" aria-labelledby="foundation-title">
      <div class="brand-mark" aria-hidden="true">文</div>
      <p class="eyebrow">StoryWeaver · Phase 0</p>
      <h1 id="foundation-title">前端基础设施已就绪</h1>
      <p class="intro">
        设计 Token、主题、路由、客户端状态、服务器缓存与统一 API 错误基础层已经配置。
        业务页面将在后续阶段按真实后端契约逐步实现。
      </p>

      <ul class="foundation-list" aria-label="已配置的技术基础">
        <li v-for="item in foundations" :key="item">
          <span class="status-dot" aria-hidden="true" />
          {{ item }}
        </li>
      </ul>

      <div class="theme-control">
        <span id="theme-label">界面主题</span>
        <ElRadioGroup v-model="selectedTheme" aria-labelledby="theme-label" size="small">
          <ElRadioButton value="light">浅色</ElRadioButton>
          <ElRadioButton value="dark">深色</ElRadioButton>
          <ElRadioButton value="system">跟随系统</ElRadioButton>
        </ElRadioGroup>
      </div>

      <p class="phase-note">当前严格停留在 Phase 0，未创建登录、项目或 AI 工作流页面。</p>
    </section>
  </main>
</template>
