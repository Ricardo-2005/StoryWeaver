<script setup lang="ts">
import { computed } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'

import { clearSession } from '@/features/auth/session'
import { useCurrentUserQuery } from '@/queries/auth'
import { useProjectQuery } from '@/queries/projects'
import { useThemeStore } from '@/stores/theme'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const uiStore = useUiStore()
const themeStore = useThemeStore()
const vueQueryClient = useQueryClient()
const currentUserQuery = useCurrentUserQuery()
const projectId = computed(() =>
  typeof route.params.projectId === 'string' ? route.params.projectId : '',
)
const projectQuery = useProjectQuery(projectId)

const projectName = computed(() => projectQuery.data.value?.name ?? '所有项目')
const userLabel = computed(
  () => currentUserQuery.data.value?.username ?? currentUserQuery.data.value?.email ?? '账户',
)
const nextTheme = computed(() => {
  if (themeStore.preference === 'system') return 'light'
  if (themeStore.preference === 'light') return 'dark'
  return 'system'
})
const themeLabel = computed(() => ({ light: '浅色', dark: '深色', system: '跟随系统' })[themeStore.preference])

function cycleTheme(): void {
  themeStore.setPreference(nextTheme.value)
}

async function logout(): Promise<void> {
  clearSession(vueQueryClient)
  await router.replace({ name: 'login' })
}

</script>

<template>
  <header class="top-project-bar">
    <div class="topbar-leading">
      <button
        class="icon-button sidebar-toggle"
        type="button"
        aria-label="打开或关闭导航"
        aria-controls="project-sidebar"
        :aria-expanded="uiStore.sidebarOpen"
        @click="uiStore.toggleSidebar"
      >
        ☰
      </button>
      <div class="project-heading">
        <span>当前项目</span>
        <strong>{{ projectName }}</strong>
      </div>
    </div>

    <div class="topbar-actions">
      <button class="text-button" type="button" :title="`当前：${themeLabel}`" @click="cycleTheme">
        {{ themeLabel }}
      </button>
      <details class="account-menu">
        <summary>{{ userLabel.slice(0, 1).toUpperCase() }}</summary>
        <div class="account-popover">
          <strong>{{ userLabel }}</strong>
          <span>{{ currentUserQuery.data.value?.email }}</span>
          <button type="button" @click="logout">退出登录</button>
        </div>
      </details>
    </div>
  </header>
</template>
