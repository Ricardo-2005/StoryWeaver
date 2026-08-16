<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'

import { chaptersApi } from '@/api/endpoints/assets'
import { foreshadowsApi } from '@/api/endpoints/v15'
import type { ReconstructionCandidate } from '@/api/types'
import EmptyState from '@/components/base/EmptyState.vue'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import ReconstructionCandidatePanel from '@/components/reconstruction/ReconstructionCandidatePanel.vue'

const route = useRoute()
const client = useQueryClient()
const projectId = computed(() => String(route.params.projectId ?? ''))
const key = computed(() => ['foreshadows', projectId.value])
const candidateKey = computed(() => ['reconstruction-module-candidates', projectId.value, 'FORESHADOW'])
const query = useQuery({ queryKey: key, queryFn: () => foreshadowsApi.list(projectId.value) })
const chapters = useQuery({
  queryKey: computed(() => ['chapters', projectId.value]),
  queryFn: () => chaptersApi.list(projectId.value),
})
const resolutions = reactive<Record<string, string>>({})
const form = reactive({ title: '', description: '', targetChapterNo: undefined as number | undefined, notes: '' })

async function refreshAssets(): Promise<void> {
  await Promise.all([
    client.invalidateQueries({ queryKey: key.value }),
    client.invalidateQueries({ queryKey: candidateKey.value }),
    client.invalidateQueries({ queryKey: ['book-reconstruction', projectId.value] }),
  ])
}

const create = useMutation({
  mutationFn: () => foreshadowsApi.create(projectId.value, {
    title: form.title,
    description: form.description || null,
    plantedChapterId: null,
    targetChapterNo: form.targetChapterNo ?? null,
    notes: form.notes || null,
  }),
  onSuccess: async () => {
    Object.assign(form, { title: '', description: '', targetChapterNo: undefined, notes: '' })
    await refreshAssets()
    ElMessage.success('伏笔已创建')
  },
})
const transition = useMutation({
  mutationFn: ({ id, version, status, resolvedChapterId = null }: {
    id: string
    version: number
    status: string
    resolvedChapterId?: string | null
  }) => foreshadowsApi.transition(id, { expectedVersion: version, status, resolvedChapterId }),
  onSuccess: async () => client.invalidateQueries({ queryKey: key.value }),
})
const cancel = useMutation({
  mutationFn: (id: string) => foreshadowsApi.cancel(id),
  onSuccess: async () => {
    await refreshAssets()
    ElMessage.success('伏笔登记已取消，关联候选已恢复')
  },
  onError: () => ElMessage.error('取消伏笔登记失败'),
})

function useCandidate(candidate: ReconstructionCandidate): void {
  form.title = candidate.content.split(/[，。；：]/, 1)[0]!.slice(0, 80)
  form.description = candidate.content
  form.notes = `AI 伏笔候选 · ${candidate.confidence} · Evidence ${candidate.evidenceCount}`
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function requestCancel(id: string, title: string): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `取消“${title}”后，该伏笔会从下方台账删除；如果它来自 AI 拆书，原候选将恢复到上方。`,
    '取消伏笔登记',
    { confirmButtonText: '确认取消', cancelButtonText: '保留', type: 'warning' },
  ).catch(() => false)
  if (confirmed) cancel.mutate(id)
}
</script>

<template>
  <main class="page-container">
    <header class="page-header">
      <div>
        <p class="eyebrow">Foreshadow ledger</p>
        <h1 tabindex="-1">伏笔台账</h1>
        <p>拆书识别结果自动登记；生命周期仍由你确认，到目标章只提示 DUE，不自动伪造回收。</p>
      </div>
    </header>

    <ReconstructionCandidatePanel
      :project-id="projectId"
      candidate-type="FORESHADOW"
      title="AI 伏笔候选"
      description="尚未写入台账的拆书候选显示在这里；已自动登记或手动保存的候选不会重复显示。"
      empty-description="全部伏笔候选均已登记，或本次重建未发现伏笔。"
      use-label="载入登记表"
      @use="useCandidate"
    />

    <section class="v15-panel">
      <h2>登记伏笔</h2>
      <ProblemAlert v-if="create.isError.value" :error="create.error.value" />
      <form class="sw-form" @submit.prevent="create.mutate()">
        <div class="form-row">
          <label class="form-field"><span>标题</span><input v-model="form.title" maxlength="160" required /></label>
          <label class="form-field"><span>目标章节号</span><input v-model.number="form.targetChapterNo" type="number" min="1" /></label>
        </div>
        <label class="form-field"><span>说明</span><textarea v-model="form.description" rows="3" /></label>
        <label class="form-field"><span>备注</span><textarea v-model="form.notes" rows="2" /></label>
        <button class="sw-button sw-button--primary">创建</button>
      </form>
    </section>

    <ProblemAlert v-if="cancel.isError.value" :error="cancel.error.value" />
    <LoadingState v-if="query.isPending.value" />
    <ErrorState v-else-if="query.isError.value" :error="query.error.value" @retry="query.refetch()" />
    <EmptyState
      v-else-if="!query.data.value?.length"
      title="正式伏笔台账为空"
      description="完成 TXT 拆书后会自动登记，也可以在这里手动创建。"
    />
    <section v-else class="asset-list">
      <article v-for="item in query.data.value" :key="item.id" class="asset-card">
        <div class="asset-card-heading">
          <div><span class="asset-type">目标第 {{ item.targetChapterNo ?? '-' }} 章</span><h2>{{ item.title }}</h2></div>
          <span class="status-pill">{{ item.status }}</span>
        </div>
        <p>{{ item.description || '无说明' }}</p>
        <div v-if="['PLANTED', 'DEVELOPING', 'DUE', 'PARTIALLY_RESOLVED'].includes(item.status)" class="form-row">
          <label class="form-field">
            <span>回收章节</span>
            <select v-model="resolutions[item.id]">
              <option value="">选择章节</option>
              <option v-for="chapter in chapters.data.value" :key="chapter.id" :value="chapter.id">
                第 {{ chapter.chapterNo }} 章 · {{ chapter.title }}
              </option>
            </select>
          </label>
          <button
            class="sw-button sw-button--secondary"
            :disabled="!resolutions[item.id]"
            @click="transition.mutate({ id: item.id, version: item.version, status: 'RESOLVED', resolvedChapterId: resolutions[item.id] ?? null })"
          >标记已回收</button>
        </div>
        <div class="asset-actions">
          <button v-if="item.status === 'CANDIDATE'" @click="transition.mutate({ id: item.id, version: item.version, status: 'PLANTED' })">确认已埋设</button>
          <button v-if="item.status === 'PLANTED'" @click="transition.mutate({ id: item.id, version: item.version, status: 'DEVELOPING' })">标记发展中</button>
          <button v-if="['DEVELOPING', 'DUE'].includes(item.status)" @click="transition.mutate({ id: item.id, version: item.version, status: 'PARTIALLY_RESOLVED' })">部分回收</button>
          <button v-if="!['RESOLVED', 'ABANDONED', 'REJECTED'].includes(item.status)" @click="transition.mutate({ id: item.id, version: item.version, status: 'ABANDONED' })">放弃</button>
          <button type="button" :disabled="cancel.isPending.value" @click="requestCancel(item.id, item.title)">取消</button>
        </div>
      </article>
    </section>
  </main>
</template>
