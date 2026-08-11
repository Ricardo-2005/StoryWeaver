<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { importsApi } from '@/api/endpoints/v15'
import type { ImportResponse } from '@/api/types'
import EmptyState from '@/components/base/EmptyState.vue'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import ProblemAlert from '@/components/base/ProblemAlert.vue'

const route = useRoute()
const queryClient = useQueryClient()
const projectId = computed(() => String(route.params.projectId ?? ''))
const file = ref<File>()
const selected = ref<ImportResponse>()
const editableChapters = ref<Array<{ title: string; content: string; included: boolean }>>([])
const queryKey = computed(() => ['imports', projectId.value])
const query = useQuery({ queryKey, queryFn: () => importsApi.list(projectId.value) })
const upload = useMutation({ mutationFn: () => { if (!file.value) throw new Error('请选择文件'); return importsApi.upload(projectId.value, file.value) }, onSuccess: refresh })
const action = useMutation({ mutationFn: ({ id, type }: { id: string; type: 'extract' | 'complete' | 'cancel' | 'retry' }) => importsApi[type](id), onSuccess: refresh })
const decision = useMutation({ mutationFn: ({ job, candidateId, accepted }: { job: ImportResponse; candidateId: string; accepted: boolean }) => importsApi.decide(job.id, { decisions: [{ candidateId, accepted }] }), onSuccess: refresh })
const saveChapters = useMutation({ mutationFn: () => { if (!selected.value) throw new Error('未选择导入任务'); return importsApi.replaceChapters(selected.value.id, { expectedVersion: selected.value.version, chapters: editableChapters.value }) }, onSuccess: refresh })

async function refresh(value?: ImportResponse): Promise<void> {
  if (value) selectJob(value)
  await queryClient.invalidateQueries({ queryKey: queryKey.value })
}
function selectJob(job?: ImportResponse): void {
  selected.value = job
  editableChapters.value = job?.chapters.map(chapter => ({ title: chapter.title, content: chapter.content, included: chapter.included })) ?? []
}
async function exportProject(): Promise<void> {
  const blob = await importsApi.exportGit(projectId.value)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a'); link.href = url; link.download = `storyweaver-${projectId.value}.zip`; link.click()
  URL.revokeObjectURL(url); ElMessage.success('Git 目录包已导出')
}
</script>

<template>
  <main class="page-container">
    <header class="page-header"><div><p class="eyebrow">Import & export</p><h1 tabindex="-1">导入与迁移</h1><p>导入 TXT、Markdown、DOCX 或 ZIP，确认章节切分后再抽取候选事实；所有候选项都需要人工决定。</p></div><button class="sw-button sw-button--secondary" @click="exportProject">导出 Git 包</button></header>
    <section class="v15-toolbar" aria-label="上传小说"><input type="file" accept=".txt,.md,.markdown,.docx,.zip" @change="file = ($event.target as HTMLInputElement).files?.[0]" /><button class="sw-button sw-button--primary" :disabled="!file || upload.isPending.value" @click="upload.mutate()">{{ upload.isPending.value ? '上传中…' : '上传并切分' }}</button></section>
    <ProblemAlert v-if="upload.isError.value" :error="upload.error.value" />
    <LoadingState v-if="query.isPending.value" />
    <ErrorState v-else-if="query.isError.value" :error="query.error.value" @retry="query.refetch()" />
    <EmptyState v-else-if="!query.data.value?.length" title="还没有导入任务" description="选择一个受支持的文件开始。" />
    <section v-else class="asset-list">
      <article v-for="job in query.data.value" :key="job.id" class="asset-card">
        <div class="asset-card-heading"><div><span class="asset-type">{{ job.chapters.length }} 章 · {{ job.candidates.length }} 个候选</span><h2>{{ job.fileName }}</h2></div><span class="status-pill">{{ job.status }}</span></div>
        <p v-if="job.errorMessage" class="problem-alert">{{ job.errorMessage }}</p>
        <div class="asset-actions"><button @click="selectJob(selected?.id === job.id ? undefined : job)">{{ selected?.id === job.id ? '收起' : '查看内容' }}</button><button v-if="job.status === 'SPLIT_REVIEW'" @click="action.mutate({ id: job.id, type: 'extract' })">抽取候选</button><button v-if="job.status === 'FAILED'" @click="action.mutate({ id: job.id, type: 'retry' })">重试抽取</button><button v-if="['SPLIT_REVIEW','CANDIDATE_REVIEW'].includes(job.status)" @click="action.mutate({ id: job.id, type: 'complete' })">创建章节</button><button v-if="!['COMPLETED','CANCELLED'].includes(job.status)" @click="action.mutate({ id: job.id, type: 'cancel' })">取消</button></div>
        <div v-if="selected?.id === job.id" class="v15-detail-grid">
          <section><h3>章节切分</h3><ProblemAlert v-if="saveChapters.isError.value" :error="saveChapters.error.value"/><ol><li v-for="(chapter,index) in editableChapters" :key="index" class="import-chapter-editor"><label><input v-model="chapter.included" type="checkbox"/>包含第 {{ index + 1 }} 章</label><input v-model="chapter.title" maxlength="160" aria-label="章节标题"/><textarea v-model="chapter.content" rows="6" maxlength="500000" aria-label="章节正文"/></li></ol><button v-if="selected.status === 'SPLIT_REVIEW'" class="sw-button sw-button--secondary" :disabled="saveChapters.isPending.value" @click="saveChapters.mutate()">保存切分调整</button></section>
          <section><h3>候选项</h3><p v-if="!selected.candidates.length" class="muted-copy">尚未抽取候选。</p><article v-for="candidate in selected.candidates" :key="candidate.id" class="candidate-row"><span class="asset-type">{{ candidate.candidateType }} · 第 {{ candidate.sourceChapterNo }} 章</span><p>{{ candidate.content }}</p><div v-if="candidate.decision === 'PENDING'" class="asset-actions"><button @click="decision.mutate({ job: selected!, candidateId: candidate.id, accepted: true })">接受</button><button @click="decision.mutate({ job: selected!, candidateId: candidate.id, accepted: false })">拒绝</button></div><span v-else class="status-pill">{{ candidate.decision }}</span></article></section>
        </div>
      </article>
    </section>
  </main>
</template>
