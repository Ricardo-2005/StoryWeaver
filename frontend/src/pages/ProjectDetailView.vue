<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation } from '@tanstack/vue-query'
import { ElDialog, ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { projectsApi } from '@/api/endpoints/projects'
import CanonAssetsPanel from '@/components/canon/CanonAssetsPanel.vue'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import OptionChipGroup from '@/components/base/OptionChipGroup.vue'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import BookReconstructionPanel from '@/components/reconstruction/BookReconstructionPanel.vue'
import { audienceOptions, genreLabel, lengthOptions, moreGenreOptions, perspectiveOptions, primaryGenreOptions, type LengthType, type NarrativePerspective, type ProjectGenre, type TargetAudience } from '@/features/projects/projectOptions'
import { toUpdateProjectRequest, useProjectQuery, useUpdateProjectMutation } from '@/queries/projects'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.projectId ?? ''))
const projectQuery = useProjectQuery(projectId)
const updateMutation = useUpdateProjectMutation()
const editOpen = ref(false)
const form = reactive({
  name: '', genre: undefined as ProjectGenre | undefined, customGenre: '', targetAudience: 'GENERAL' as TargetAudience,
  narrativePerspective: 'THIRD_PERSON' as NarrativePerspective, lengthType: 'LONG_NOVEL' as LengthType, premise: '',
  description: '', authorIntent: '', currentFocus: '', worldRules: [] as string[], targetWordCount: undefined as number | undefined,
  chapterWordTarget: undefined as number | undefined,
})
const genreOptions = [...primaryGenreOptions, ...moreGenreOptions]
const snapshotMutation = useMutation({
  mutationFn: () => {
    const project = projectQuery.data.value
    if (!project) throw new Error('Project is not loaded')
    return projectsApi.snapshot(project.id, { expectedVersion: project.version })
  },
  onSuccess: () => ElMessage.success('项目快照已创建'),
})

function text(value: string): string | null {
  return value.trim() || null
}

function openEdit(): void {
  const project = projectQuery.data.value
  if (!project) return
  Object.assign(form, {
    name: project.name,
    genre: project.genre as ProjectGenre | undefined,
    customGenre: project.customGenre ?? '',
    targetAudience: project.targetAudience ?? 'GENERAL',
    narrativePerspective: project.narrativePerspective ?? 'THIRD_PERSON',
    lengthType: project.lengthType ?? 'LONG_NOVEL',
    premise: project.premise ?? '',
    description: project.description ?? '',
    authorIntent: project.authorIntent ?? '',
    currentFocus: project.currentFocus ?? '',
    worldRules: [...(project.worldRules ?? [])],
    targetWordCount: project.targetWordCount ?? undefined,
    chapterWordTarget: project.chapterWordTarget ?? undefined,
  })
  editOpen.value = true
}

async function saveProject(): Promise<void> {
  const project = projectQuery.data.value
  if (!project || !form.genre || form.premise.trim().length < 10 || (form.genre === 'CUSTOM' && !form.customGenre.trim())) return
  try {
    await updateMutation.mutateAsync({
      projectId: project.id,
      request: toUpdateProjectRequest(project, {
        name: form.name.trim(),
        genre: form.genre,
        customGenre: form.genre === 'CUSTOM' ? text(form.customGenre) : null,
        targetAudience: form.targetAudience,
        narrativePerspective: form.narrativePerspective,
        lengthType: form.lengthType,
        premise: form.premise.trim(),
        description: text(form.description),
        authorIntent: text(form.authorIntent),
        currentFocus: text(form.currentFocus),
        worldRules: form.worldRules.map((rule) => rule.trim()).filter(Boolean),
        targetWordCount: form.targetWordCount || null,
        chapterWordTarget: form.chapterWordTarget || null,
      }),
    })
    editOpen.value = false
    ElMessage.success('项目信息已更新')
  } catch {
    // Problem Details stays visible in the dialog.
  }
}

function addWorldRule(): void { form.worldRules.push('') }
function removeWorldRule(index: number): void { form.worldRules.splice(index, 1) }

async function createSnapshot(): Promise<void> {
  try {
    await snapshotMutation.mutateAsync()
  } catch {
    ElMessage.error('快照创建失败，请刷新后重试')
  }
}

async function archiveProject(): Promise<void> {
  const project = projectQuery.data.value
  if (!project) return
  try {
    await ElMessageBox.confirm(`归档“${project.name}”后可通过包含归档项目的查询恢复。`, '归档项目', {
      confirmButtonText: '归档',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await updateMutation.mutateAsync({
      projectId: project.id,
      request: toUpdateProjectRequest(project, { archived: true }),
    })
    ElMessage.success('项目已归档')
    await router.replace('/projects')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('归档失败，请刷新后重试')
  }
}
</script>

<template>
  <main class="page-container">
    <LoadingState v-if="projectQuery.isPending.value" label="正在加载项目详情…" />
    <ErrorState v-else-if="projectQuery.isError.value" :error="projectQuery.error.value" @retry="projectQuery.refetch()" />
    <template v-else-if="projectQuery.data.value">
      <header class="project-detail-header">
        <div class="project-title-block">
          <span class="project-detail-mark" aria-hidden="true">{{ projectQuery.data.value.name.slice(0, 1) }}</span>
          <div>
            <p class="eyebrow">{{ genreLabel(projectQuery.data.value.genre) }}</p>
            <h1 tabindex="-1">{{ projectQuery.data.value.name }}</h1>
            <p>{{ projectQuery.data.value.description || '尚未填写项目简介。' }}</p>
          </div>
        </div>
        <div class="project-header-actions">
          <button class="sw-button sw-button--secondary" type="button" @click="createSnapshot">
            {{ snapshotMutation.isPending.value ? '创建中…' : '创建快照' }}
          </button>
          <button class="sw-button sw-button--secondary" type="button" @click="openEdit">编辑项目</button>
          <button class="sw-button sw-button--danger" type="button" @click="archiveProject">归档</button>
        </div>
      </header>

      <section class="project-overview" aria-label="项目创作方向">
        <article>
          <span>作者意图</span>
          <p>{{ projectQuery.data.value.authorIntent || '尚未填写。' }}</p>
        </article>
        <article>
          <span>当前焦点</span>
          <p>{{ projectQuery.data.value.currentFocus || '尚未填写。' }}</p>
        </article>
      </section>

      <BookReconstructionPanel
        v-if="projectQuery.data.value.creationSource === 'TXT_IMPORT'"
        :project-id="projectId"
        :project-name="projectQuery.data.value.name"
      />

      <section class="conversation-placeholder" aria-labelledby="conversation-heading">
        <div>
          <p class="eyebrow">Workspace</p>
          <h2 id="conversation-heading">项目工作区</h2>
          <p>会话框架已经就位，但后端尚未提供会话存储或 Chat API，因此这里不会生成假的回复。</p>
        </div>
        <button class="sw-button sw-button--secondary" type="button" disabled>新对话 · 待后端支持</button>
      </section>

      <CanonAssetsPanel :project-id="projectId" />
    </template>

    <ElDialog v-model="editOpen" title="编辑项目" width="min(620px, 92vw)" destroy-on-close>
      <ProblemAlert v-if="updateMutation.isError.value" :error="updateMutation.error.value" />
      <form id="project-edit-form" class="sw-form" @submit.prevent="saveProject">
        <label class="form-field"><span>项目名称</span><input v-model="form.name" maxlength="120" required /></label>
        <div class="form-field"><span>小说题材</span><OptionChipGroup v-model="form.genre" label="小说题材" :options="genreOptions" required /><label v-if="form.genre === 'CUSTOM'" class="form-field"><span>自定义题材名称</span><input v-model="form.customGenre" maxlength="20" required /></label></div>
        <div class="form-field"><span>目标读者</span><OptionChipGroup v-model="form.targetAudience" label="目标读者" :options="audienceOptions" required /></div>
        <div class="form-field"><span>作品视角</span><OptionChipGroup v-model="form.narrativePerspective" label="作品视角" :options="perspectiveOptions" required /></div>
        <div class="form-field"><span>篇幅长短</span><OptionChipGroup v-model="form.lengthType" label="篇幅长短" :options="lengthOptions" required /></div>
        <label class="form-field"><span>故事构想</span><textarea v-model="form.premise" minlength="10" maxlength="500" rows="5" required /><small>{{ form.premise.length }} / 500</small></label>
        <label class="form-field"><span>项目简介</span><textarea v-model="form.description" maxlength="300" rows="3" /></label>
        <label class="form-field"><span>作者意图</span><textarea v-model="form.authorIntent" maxlength="3000" rows="5" /></label>
        <label class="form-field"><span>当前焦点</span><textarea v-model="form.currentFocus" maxlength="2000" rows="4" /></label>
        <fieldset class="world-rules-field"><legend>世界硬规则</legend><p>不填写时不会自动生成。</p><div v-for="(_, index) in form.worldRules" :key="index" class="world-rule-row"><input v-model="form.worldRules[index]" maxlength="500" :aria-label="`世界硬规则 ${index + 1}`" /><button class="text-button" type="button" @click="removeWorldRule(index)">删除</button></div><button class="text-button" type="button" @click="addWorldRule">+ 添加规则</button></fieldset>
        <div class="form-row"><label class="form-field"><span>目标字数</span><input v-model.number="form.targetWordCount" type="number" min="1" /></label><label class="form-field"><span>单章字数</span><input v-model.number="form.chapterWordTarget" type="number" min="1" /></label></div>
      </form>
      <template #footer>
        <button class="sw-button sw-button--secondary" type="button" @click="editOpen = false">取消</button>
        <button class="sw-button sw-button--primary" type="submit" form="project-edit-form" :disabled="updateMutation.isPending.value">
          {{ updateMutation.isPending.value ? '正在保存…' : '保存修改' }}
        </button>
      </template>
    </ElDialog>
  </main>
</template>
