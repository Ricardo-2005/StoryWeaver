<script setup lang="ts">
import { ref } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'

import { projectsApi } from '@/api/endpoints/projects'
import type { ProjectResponse } from '@/api/types'
import EmptyState from '@/components/base/EmptyState.vue'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import { toUpdateProjectRequest, useProjectsQuery } from '@/queries/projects'

const projectsQuery = useProjectsQuery()
const queryClient = useQueryClient()
const actionError = ref<unknown>()
const archiveMutation = useMutation({
  mutationFn: (project: ProjectResponse) =>
    projectsApi.update(project.id, toUpdateProjectRequest(project, { archived: true })),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['projects'] })
    ElMessage.success('项目已归档')
  },
})

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

async function archive(project: ProjectResponse): Promise<void> {
  actionError.value = undefined
  try {
    await ElMessageBox.confirm(
      `归档“${project.name}”后，默认项目列表将不再显示它。`,
      '归档项目',
      { confirmButtonText: '归档', cancelButtonText: '取消', type: 'warning' },
    )
    await archiveMutation.mutateAsync(project)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') actionError.value = error
  }
}
</script>

<template>
  <main class="page-container">
    <header class="page-header">
      <div>
        <p class="eyebrow">Projects</p>
        <h1 tabindex="-1">你的项目</h1>
        <p>继续创作，或从一个空白项目开始。</p>
      </div>
      <div class="page-header-actions">
        <RouterLink class="sw-button sw-button--secondary" to="/projects/archived">归档项目</RouterLink>
        <RouterLink class="sw-button sw-button--secondary" to="/projects/import/txt">导入 TXT 书籍</RouterLink>
        <RouterLink class="sw-button sw-button--primary" to="/projects/new">新建项目</RouterLink>
      </div>
    </header>

    <div v-if="actionError" class="problem-alert" role="alert">项目操作失败，请重新加载后再试。</div>
    <LoadingState v-if="projectsQuery.isPending.value" label="正在加载项目…" />
    <ErrorState v-else-if="projectsQuery.isError.value" :error="projectsQuery.error.value" @retry="projectsQuery.refetch()" />
    <EmptyState
      v-else-if="projectsQuery.data.value?.length === 0"
      title="还没有项目"
      description="可以从零开始，也可以导入已有 TXT 书籍；正式确认前都不会创建项目。"
    >
      <div class="page-header-actions">
        <RouterLink class="sw-button sw-button--primary" to="/projects/new">从零开始</RouterLink>
        <RouterLink class="sw-button sw-button--secondary" to="/projects/import/txt">导入 TXT 书籍</RouterLink>
      </div>
    </EmptyState>
    <section v-else class="project-grid" aria-label="项目列表">
      <article v-for="project in projectsQuery.data.value" :key="project.id" class="project-card">
        <RouterLink :to="`/projects/${project.id}`" class="project-card-main">
          <div class="project-card-icon" aria-hidden="true">{{ project.name.slice(0, 1) }}</div>
          <div>
            <h2>{{ project.name }}</h2>
            <p>{{ project.description || '尚未填写项目简介' }}</p>
          </div>
        </RouterLink>
        <div class="project-meta">
          <span>{{ project.genre || '未设置类型' }}</span>
          <time :datetime="project.updatedAt">更新于 {{ formatDate(project.updatedAt) }}</time>
        </div>
        <div class="project-card-actions">
          <RouterLink :to="`/projects/${project.id}`">打开</RouterLink>
          <button type="button" :disabled="archiveMutation.isPending.value" @click="archive(project)">归档</button>
        </div>
      </article>
    </section>
  </main>
</template>
