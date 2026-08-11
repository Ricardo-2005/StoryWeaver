<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElDialog, ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'

import { chaptersApi } from '@/api/endpoints/assets'
import type { ChapterResponse } from '@/api/types'
import EmptyState from '@/components/base/EmptyState.vue'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import { queryKeys } from '@/queries/keys'

const route = useRoute()
const queryClient = useQueryClient()
const projectId = computed(() => String(route.params.projectId ?? ''))
const query = useQuery({
  queryKey: computed(() => queryKeys.chapters(projectId.value)),
  queryFn: () => chaptersApi.list(projectId.value),
})
const open = ref(false)
const editing = ref<ChapterResponse>()
const form = reactive({ chapterNo: 1, title: '', outlineNodeId: '', outline: '' })
const mutation = useMutation({
  mutationFn: () => editing.value
    ? chaptersApi.updateOutline(editing.value.id, {
        outlineNodeId: form.outlineNodeId || null,
        title: form.title.trim(),
        outline: form.outline.trim() || null,
        expectedVersion: editing.value.version,
      })
    : chaptersApi.create(projectId.value, {
        chapterNo: Number(form.chapterNo),
        title: form.title.trim(),
        outlineNodeId: form.outlineNodeId || null,
        outline: form.outline.trim() || null,
      }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.chapters(projectId.value) })
    open.value = false
    ElMessage.success('章节信息已保存')
  },
})

function show(chapter?: ChapterResponse): void {
  editing.value = chapter
  Object.assign(form, chapter
    ? {
        chapterNo: chapter.chapterNo,
        title: chapter.title,
        outlineNodeId: chapter.outlineNodeId ?? '',
        outline: chapter.outline ?? '',
      }
    : {
        chapterNo: (query.data.value?.length ?? 0) + 1,
        title: '',
        outlineNodeId: '',
        outline: '',
      })
  open.value = true
}

async function save(): Promise<void> {
  try {
    await mutation.mutateAsync()
  } catch {
    // Problem Details remains visible in the dialog.
  }
}
</script>

<template>
  <main class="page-container">
    <header class="page-header">
      <div>
        <p class="eyebrow">Chapters</p>
        <h1 tabindex="-1">章节</h1>
        <p>维护章纲并进入 TipTap 正文编辑器；可在编辑器中完成预检、启动工作流和跟踪 Writer SSE。</p>
      </div>
      <button class="sw-button sw-button--primary" @click="show()">新建章节</button>
    </header>

    <LoadingState v-if="query.isPending.value" />
    <ErrorState
      v-else-if="query.isError.value"
      :error="query.error.value"
      @retry="query.refetch()"
    />
    <EmptyState
      v-else-if="!query.data.value?.length"
      title="还没有章节"
      description="创建章节骨架，不会自动生成正文。"
    >
      <button class="sw-button sw-button--primary" @click="show()">创建章节</button>
    </EmptyState>
    <section v-else class="asset-list">
      <article v-for="chapter in query.data.value" :key="chapter.id" class="asset-card">
        <div class="asset-card-heading">
          <div>
            <span class="asset-type">第 {{ chapter.chapterNo }} 章</span>
            <h2>{{ chapter.title }}</h2>
          </div>
          <span class="status-pill">{{ chapter.status }}</span>
        </div>
        <p>{{ chapter.outline || '尚未填写章纲。' }}</p>
        <div class="asset-version">
          正文版本 {{ chapter.currentVersionNo }} · 资源版本 {{ chapter.version }}
        </div>
        <div class="asset-actions">
          <RouterLink :to="`/projects/${projectId}/chapters/${chapter.id}`">编辑正文</RouterLink>
          <button @click="show(chapter)">编辑章纲</button>
        </div>
      </article>
    </section>

    <ElDialog v-model="open" :title="editing ? '编辑章节' : '新建章节'" width="min(620px, 94vw)">
      <ProblemAlert v-if="mutation.isError.value" :error="mutation.error.value" />
      <form id="chapter-form" class="sw-form" @submit.prevent="save">
        <label class="form-field">
          <span>章节编号</span>
          <input v-model.number="form.chapterNo" type="number" min="1" :disabled="Boolean(editing)" required />
        </label>
        <label class="form-field">
          <span>标题</span>
          <input v-model="form.title" maxlength="160" required />
        </label>
        <label class="form-field">
          <span>关联大纲 UUID（可空）</span>
          <input v-model="form.outlineNodeId" />
        </label>
        <label class="form-field">
          <span>章纲</span>
          <textarea v-model="form.outline" rows="8" maxlength="50000" />
        </label>
      </form>
      <template #footer>
        <button class="sw-button sw-button--secondary" @click="open = false">取消</button>
        <button class="sw-button sw-button--primary" form="chapter-form">保存</button>
      </template>
    </ElDialog>
  </main>
</template>
