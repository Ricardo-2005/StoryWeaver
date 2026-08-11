<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { AssetResponse } from '@/api/types'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import { useUpdateAssetMutation } from '@/queries/canon'
import { useWorkspaceStore } from '@/stores/workspace'

const props=defineProps<{projectId:string;asset:AssetResponse}>();const workspace=useWorkspaceStore();const mutation=useUpdateAssetMutation(()=>props.projectId);const form=reactive({name:'',content:'',changeSummary:''});
watch(()=>props.asset,asset=>Object.assign(form,{name:asset.name,content:asset.currentVersion.content,changeSummary:''}),{immediate:true});
const dirty=computed(()=>form.name!==props.asset.name||form.content!==props.asset.currentVersion.content)
async function save(){try{await mutation.mutateAsync({assetId:props.asset.id,request:{name:form.name.trim(),content:form.content,changeSummary:form.changeSummary.trim()||null,expectedVersion:props.asset.version}});ElMessage.success('Canvas 已保存为新的资产版本')}catch{/*rendered*/}}
interface TextAreaTarget { selectionStart: number; selectionEnd: number; value: string }
function selectText(event:globalThis.Event){const target=event.currentTarget as unknown as TextAreaTarget;const from=target.selectionStart,to=target.selectionEnd;workspace.setSelection(to>from?{assetName:props.asset.name,excerpt:target.value.slice(from,to).slice(0,60),from,to}:undefined)}
</script>
<template><aside class="canvas-panel" aria-label="创作 Canvas"><header class="canvas-header"><div><span>{{ asset.assetType }} · v{{ asset.currentVersionNo }}</span><strong>{{ asset.name }}</strong></div><div><span class="save-state">{{ dirty?'未保存':'已保存' }}</span><button class="icon-button" aria-label="关闭 Canvas" @click="workspace.closeCanvas">×</button></div></header><ProblemAlert v-if="mutation.isError.value" :error="mutation.error.value"/><form class="canvas-form" @submit.prevent="save"><label class="form-field"><span>名称</span><input v-model="form.name" maxlength="120" required/></label><label class="form-field canvas-editor"><span>内容</span><textarea v-model="form.content" rows="20" maxlength="200000" @select="selectText"/></label><label class="form-field"><span>变更说明</span><input v-model="form.changeSummary" maxlength="500" placeholder="说明本次手动修改"/></label><button class="sw-button sw-button--primary" :disabled="!dirty||mutation.isPending.value">{{ mutation.isPending.value?'保存中…':'保存新版本' }}</button></form><footer class="canvas-footer">选中文字会作为 Composer 的本地上下文引用；不会自动发送或修改其他资产。</footer></aside></template>
