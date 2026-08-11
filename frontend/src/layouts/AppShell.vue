<script setup lang="ts">
import ProjectSidebar from '@/components/navigation/ProjectSidebar.vue'
import TopProjectBar from '@/components/navigation/TopProjectBar.vue'
import { useUiStore } from '@/stores/ui'

const uiStore = useUiStore()
</script>

<template>
  <div class="app-shell" :class="{ 'sidebar-is-open': uiStore.sidebarOpen }">
    <ProjectSidebar />
    <button
      v-if="uiStore.sidebarOpen"
      class="sidebar-backdrop"
      type="button"
      aria-label="关闭导航"
      @click="uiStore.closeSidebar"
    />
    <div class="app-main-column">
      <TopProjectBar />
      <div id="main-content" class="app-route-content" tabindex="-1">
        <RouterView />
      </div>
    </div>
  </div>
</template>
