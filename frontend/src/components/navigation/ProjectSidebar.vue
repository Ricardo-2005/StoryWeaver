<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { useProjectsQuery } from '@/queries/projects'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const uiStore = useUiStore()
const search = ref('')
const searchInput = ref<HTMLInputElement>()
const projectsQuery = useProjectsQuery()
const currentProjectId = computed(() =>
  typeof route.params.projectId === 'string' ? route.params.projectId : '',
)
const assetLinks = [
  ['导入与迁移', 'imports'], ['滚动大纲', 'rolling-outline'], ['连续写作', 'production'], ['伏笔台账', 'foreshadows'],
  ['项目概览', ''], ['创作工作台', 'workspace'], ['人物', 'characters'], ['世界书', 'worldbook'], ['大纲', 'outlines'], ['章节', 'chapters'], ['Skill', 'skills'], ['模型与费用', 'observability'],
] as const

const visibleProjects = computed(() => {
  const term = search.value.trim().toLocaleLowerCase()
  const projects = projectsQuery.data.value ?? []
  return term
    ? projects.filter((project) =>
        [project.name, project.genre, project.description]
          .filter(Boolean)
          .some((value) => value?.toLocaleLowerCase().includes(term)),
      )
    : projects
})

function isCurrentProject(projectId: string): boolean {
  return route.params.projectId === projectId
}

function handleGlobalKeydown(event: KeyboardEvent): void {
  if ((event.ctrlKey || event.metaKey) && event.key.toLocaleLowerCase() === 'k') {
    event.preventDefault()
    uiStore.openSidebar()
    requestAnimationFrame(() => searchInput.value?.focus())
    return
  }

  if (event.key === 'Escape' && uiStore.sidebarOpen) {
    uiStore.closeSidebar()
  }
}

onMounted(() => window.addEventListener('keydown', handleGlobalKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleGlobalKeydown))
</script>

<template>
  <aside id="project-sidebar" class="app-sidebar" aria-label="项目导航">
    <div class="sidebar-brand">
      <RouterLink to="/projects" class="brand-link" @click="uiStore.closeSidebar">
        <span class="brand-symbol" aria-hidden="true">文</span>
        <span><strong>文脉</strong><small>StoryWeaver</small></span>
      </RouterLink>
      <button class="icon-button mobile-only" type="button" aria-label="关闭导航" @click="uiStore.closeSidebar">
        ×
      </button>
    </div>

    <RouterLink class="sidebar-primary-action" to="/projects/new" @click="uiStore.closeSidebar">
      <span aria-hidden="true">＋</span> 新建项目
    </RouterLink>

    <RouterLink class="sidebar-workshop-link" to="/projects/import/txt" @click="uiStore.closeSidebar">
      <span aria-hidden="true">⇧</span> 导入 TXT 书籍
      <small>最大 20 MB</small>
    </RouterLink>

    <RouterLink class="sidebar-workshop-link" to="/skills" @click="uiStore.closeSidebar">
      <span aria-hidden="true">✦</span> Skill 工坊
      <small>全局基础契约</small>
    </RouterLink>

    <button class="sidebar-disabled-action" type="button" disabled title="后端尚未提供会话接口">
      <span aria-hidden="true">＋</span> 新对话
      <small>待后端支持</small>
    </button>

    <label class="sidebar-search">
      <span class="sr-only">搜索项目</span>
      <input ref="searchInput" v-model="search" type="search" placeholder="搜索项目" />
      <kbd aria-hidden="true">Ctrl/⌘ K</kbd>
    </label>

    <nav class="sidebar-nav" aria-label="项目列表">
      <template v-if="currentProjectId">
        <p class="sidebar-section-title">项目文件</p>
        <RouterLink
          v-for="[label, suffix] in assetLinks"
          :key="label"
          :to="`/projects/${currentProjectId}${suffix ? `/${suffix}` : ''}`"
          class="sidebar-asset-link"
          @click="uiStore.closeSidebar"
        >{{ label }}</RouterLink>
      </template>
      <p class="sidebar-section-title">项目</p>
      <p v-if="projectsQuery.isPending.value" class="sidebar-hint" role="status">正在加载项目…</p>
      <button
        v-else-if="projectsQuery.isError.value"
        class="sidebar-retry"
        type="button"
        @click="projectsQuery.refetch()"
      >
        加载失败，重试
      </button>
      <p v-else-if="visibleProjects.length === 0" class="sidebar-hint">
        {{ search ? '没有匹配的项目' : '还没有项目' }}
      </p>
      <RouterLink
        v-for="project in visibleProjects"
        :key="project.id"
        :to="`/projects/${project.id}`"
        class="sidebar-project"
        :class="{ 'is-active': isCurrentProject(project.id) }"
        @click="uiStore.closeSidebar"
      >
        <span class="project-avatar" aria-hidden="true">{{ project.name.slice(0, 1) }}</span>
        <span class="project-link-text">
          <strong>{{ project.name }}</strong>
          <small>{{ project.genre || '未设置类型' }}</small>
        </span>
      </RouterLink>
    </nav>

    <div class="sidebar-conversations">
      <p class="sidebar-section-title">最近会话</p>
      <p class="sidebar-hint">后端尚未提供会话接口，本阶段不展示虚假记录。</p>
    </div>

    <RouterLink class="sidebar-footer-link" to="/projects" @click="uiStore.closeSidebar">
      所有项目
    </RouterLink>
  </aside>
</template>
