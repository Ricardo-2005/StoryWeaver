<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElDialog, ElMessage, ElMessageBox, ElTag } from 'element-plus'

import type { AssetResponse, CanonStatus } from '@/api/types'
import EmptyState from '@/components/base/EmptyState.vue'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import {
  useCanonAssetsQuery,
  useCreateAssetMutation,
  useTransitionAssetMutation,
  useUpdateAssetMutation,
} from '@/queries/canon'

const props = defineProps<{ projectId: string }>()
const assetsQuery = useCanonAssetsQuery(() => props.projectId)
const createMutation = useCreateAssetMutation(() => props.projectId)
const updateMutation = useUpdateAssetMutation(() => props.projectId)
const confirmMutation = useTransitionAssetMutation(() => props.projectId, 'confirm')
const deprecateMutation = useTransitionAssetMutation(() => props.projectId, 'deprecate')
const dialogOpen = ref(false)
const editingAsset = ref<AssetResponse>()
const form = reactive({ assetType: '', name: '', content: '', changeSummary: '' })

const statusLabels: Readonly<Record<CanonStatus, string>> = {
  DRAFT: '草稿',
  CANDIDATE: '候选',
  CONFIRMED: '已确认',
  CONFLICTED: '有冲突',
  DEPRECATED: '已废弃',
}

function openCreate(): void {
  editingAsset.value = undefined
  Object.assign(form, { assetType: '', name: '', content: '', changeSummary: '' })
  dialogOpen.value = true
}

function openEdit(asset: AssetResponse): void {
  editingAsset.value = asset
  Object.assign(form, {
    assetType: asset.assetType,
    name: asset.name,
    content: asset.currentVersion.content,
    changeSummary: '',
  })
  dialogOpen.value = true
}

async function save(): Promise<void> {
  try {
    if (editingAsset.value) {
      await updateMutation.mutateAsync({
        assetId: editingAsset.value.id,
        request: {
          name: form.name.trim(),
          content: form.content,
          changeSummary: form.changeSummary.trim() || null,
          expectedVersion: editingAsset.value.version,
        },
      })
      ElMessage.success('正典资产已更新并创建新版本')
    } else {
      await createMutation.mutateAsync({
        assetType: form.assetType.trim(),
        name: form.name.trim(),
        content: form.content,
        changeSummary: form.changeSummary.trim() || null,
      })
      ElMessage.success('正典资产已创建')
    }
    dialogOpen.value = false
  } catch {
    // Mutation errors remain visible in the dialog.
  }
}

async function transition(asset: AssetResponse, action: 'confirm' | 'deprecate'): Promise<void> {
  try {
    if (action === 'deprecate') {
      await ElMessageBox.confirm(`确定废弃“${asset.name}”吗？`, '废弃正典资产', {
        confirmButtonText: '废弃',
        cancelButtonText: '取消',
        type: 'warning',
      })
    }
    const mutation = action === 'confirm' ? confirmMutation : deprecateMutation
    await mutation.mutateAsync({ assetId: asset.id, expectedVersion: asset.version })
    ElMessage.success(action === 'confirm' ? '当前版本已确认为正典' : '正典资产已废弃')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('操作失败，请刷新后重试')
  }
}
</script>

<template>
  <section class="content-section" aria-labelledby="canon-heading">
    <header class="section-header">
      <div>
        <p class="eyebrow">Canon assets</p>
        <h2 id="canon-heading">正典资产</h2>
        <p>手动维护设定；确认和废弃均由后端执行版本与状态迁移。</p>
      </div>
      <button class="sw-button sw-button--secondary" type="button" @click="openCreate">新建资产</button>
    </header>

    <LoadingState v-if="assetsQuery.isPending.value" label="正在加载正典资产…" />
    <ErrorState v-else-if="assetsQuery.isError.value" :error="assetsQuery.error.value" @retry="assetsQuery.refetch()" />
    <EmptyState v-else-if="assetsQuery.data.value?.length === 0" title="还没有正典资产" description="从空白条目开始，内容不会被 AI 自动覆盖。">
      <button class="sw-button sw-button--primary" type="button" @click="openCreate">创建第一个资产</button>
    </EmptyState>
    <div v-else class="asset-list">
      <article v-for="asset in assetsQuery.data.value" :key="asset.id" class="asset-card">
        <div class="asset-card-heading">
          <div>
            <span class="asset-type">{{ asset.assetType }}</span>
            <h3>{{ asset.name }}</h3>
          </div>
          <ElTag :type="asset.status === 'CONFIRMED' ? 'success' : asset.status === 'CONFLICTED' ? 'danger' : 'info'" effect="plain">
            {{ statusLabels[asset.status] }}
          </ElTag>
        </div>
        <p>{{ asset.currentVersion.content || '空白内容' }}</p>
        <div class="asset-version">当前版本 {{ asset.currentVersionNo }} · 资源版本 {{ asset.version }}</div>
        <div class="asset-actions">
          <button type="button" @click="openEdit(asset)">编辑</button>
          <button v-if="asset.status !== 'CONFIRMED' && asset.status !== 'DEPRECATED'" type="button" @click="transition(asset, 'confirm')">确认为正典</button>
          <button v-if="asset.status !== 'DEPRECATED'" class="danger-link" type="button" @click="transition(asset, 'deprecate')">废弃</button>
        </div>
      </article>
    </div>

    <ElDialog v-model="dialogOpen" :title="editingAsset ? '编辑正典资产' : '新建正典资产'" width="min(560px, 92vw)" destroy-on-close>
      <ProblemAlert v-if="createMutation.isError.value" :error="createMutation.error.value" />
      <ProblemAlert v-if="updateMutation.isError.value" :error="updateMutation.error.value" />
      <form id="asset-form" class="sw-form" @submit.prevent="save">
        <label class="form-field">
          <span>资产类型</span>
          <input v-model="form.assetType" name="assetType" maxlength="40" pattern="[A-Za-z][A-Za-z0-9_-]*" :disabled="Boolean(editingAsset)" required placeholder="CHARACTER" />
          <small>英文开头，可包含数字、下划线和连字符</small>
        </label>
        <label class="form-field">
          <span>名称</span>
          <input v-model="form.name" name="name" maxlength="120" required />
        </label>
        <label class="form-field">
          <span>内容</span>
          <textarea v-model="form.content" name="content" maxlength="200000" rows="8" required />
        </label>
        <label class="form-field">
          <span>变更说明</span>
          <input v-model="form.changeSummary" name="changeSummary" maxlength="500" />
        </label>
      </form>
      <template #footer>
        <button class="sw-button sw-button--secondary" type="button" @click="dialogOpen = false">取消</button>
        <button class="sw-button sw-button--primary" form="asset-form" type="submit" :disabled="createMutation.isPending.value || updateMutation.isPending.value">
          {{ createMutation.isPending.value || updateMutation.isPending.value ? '正在保存…' : '保存' }}
        </button>
      </template>
    </ElDialog>
  </section>
</template>
