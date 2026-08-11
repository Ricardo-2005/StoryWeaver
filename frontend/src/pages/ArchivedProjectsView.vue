<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'

import { projectsApi } from '@/api/endpoints/projects'
import type { ProjectResponse } from '@/api/types'
import EmptyState from '@/components/base/EmptyState.vue'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import { toUpdateProjectRequest, useProjectsQuery } from '@/queries/projects'
import { queryKeys } from '@/queries/keys'

const projectsQuery = useProjectsQuery(true)
const queryClient = useQueryClient()
const actionError = ref<unknown>()
const archivedProjects = computed(() => projectsQuery.data.value?.filter((project) => project.archived) ?? [])

const restoreMutation = useMutation({
  mutationFn: (project: ProjectResponse) =>
    projectsApi.update(project.id, toUpdateProjectRequest(project, { archived: false })),
  onSuccess: async (project) => {
    queryClient.setQueryData(queryKeys.project(project.id), project)
    await queryClient.invalidateQueries({ queryKey: ['projects'] })
    ElMessage.success('项目已恢复')
  },
})

const deleteMutation = useMutation({
  mutationFn: (project: ProjectResponse) => projectsApi.remove(project.id, project.version),
  onSuccess: async (_, project) => {
    queryClient.removeQueries({ queryKey: queryKeys.project(project.id) })
    await queryClient.invalidateQueries({ queryKey: ['projects'] })
    ElMessage.success('项目已永久删除')
  },
})

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

async function restore(project: ProjectResponse): Promise<void> {
  actionError.value = undefined
  try {
    await restoreMutation.mutateAsync(project)
  } catch (error) {
    actionError.value = error
  }
}

async function remove(project: ProjectResponse): Promise<void> {
  actionError.value = undefined
  try {
    await ElMessageBox.confirm(
      `永久删除“${project.name}”后，章节、人物、世界书、Skill 绑定和历史版本都无法恢复。`,
      '永久删除项目',
      {
        confirmButtonText: '永久删除',
        cancelButtonText: '取消',
        type: 'error',
        confirmButtonClass: 'el-button--danger',
      },
    )
    await deleteMutation.mutateAsync(project)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') actionError.value = error
  }
}
</script>

<template>
  <main class="page-container">
    <header class="page-header">
      <div>
        <p class="eyebrow">Archived projects</p>
        <h1 tabindex="-1">归档项目</h1>
        <p>归档不会删除内容。你可以恢复项目，或永久删除不再需要的数据。</p>
      </div>
      <RouterLink class="sw-button sw-button--secondary" to="/projects">返回项目列表</RouterLink>
    </header>

    <div v-if="actionError" class="problem-alert" role="alert">项目操作失败，请刷新后重试。</div>
    <LoadingState v-if="projectsQuery.isPending.value" label="正在加载归档项目…" />
    <ErrorState
      v-else-if="projectsQuery.isError.value"
      :error="projectsQuery.error.value"
      @retry="projectsQuery.refetch()"
    />
    <EmptyState
      v-else-if="archivedProjects.length === 0"
      title="没有归档项目"
      description="归档后的项目会保留在这里，不会出现在默认项目列表中。"
    >
      <RouterLink class="sw-button sw-button--secondary" to="/projects">查看当前项目</RouterLink>
    </EmptyState>
    <section v-else class="project-grid" aria-label="归档项目列表" :aria-busy="restoreMutation.isPending.value || deleteMutation.isPending.value">
      <article v-for="project in archivedProjects" :key="project.id" class="project-card">
        <RouterLink :to="`/projects/${project.id}`" class="project-card-main">
          <div class="project-card-icon" aria-hidden="true">{{ project.name.slice(0, 1) }}</div>
          <div>
            <h2>{{ project.name }}</h2>
            <p>{{ project.description || '尚未填写项目简介' }}</p>
          </div>
        </RouterLink>
        <div class="project-meta">
          <span>{{ project.genre || '未设置类型' }}</span>
          <time :datetime="project.updatedAt">归档于 {{ formatDate(project.updatedAt) }}</time>
        </div>
        <div class="project-card-actions">
          <RouterLink :to="`/projects/${project.id}`">查看</RouterLink>
          <button
            type="button"
            :aria-label="`恢复项目 ${project.name}`"
            :disabled="restoreMutation.isPending.value || deleteMutation.isPending.value"
            @click="restore(project)"
          >恢复</button>
          <button
            class="danger-action"
            type="button"
            :aria-label="`永久删除项目 ${project.name}`"
            :disabled="restoreMutation.isPending.value || deleteMutation.isPending.value"
            @click="remove(project)"
          >永久删除</button>
        </div>
      </article>
    </section>
  </main>
</template>
