<script setup lang="ts">
import { computed } from 'vue'
import { useWorkspaceStore } from '@/stores/workspace'
const workspace=useWorkspaceStore();const block=computed(()=>workspace.writingBlocks.find(item=>item.id===workspace.canvasBlockId));
interface TextAreaTarget { selectionStart: number; selectionEnd: number; value: string }
function updateContent(event:globalThis.Event){const target=event.currentTarget as unknown as TextAreaTarget;if(block.value)workspace.updateWritingBlock(block.value.id,target.value)}
function selectText(event:globalThis.Event){const target=event.currentTarget as unknown as TextAreaTarget;const from=target.selectionStart,to=target.selectionEnd;workspace.setSelection(to>from&&block.value?{assetName:block.value.title,excerpt:target.value.slice(from,to).slice(0,60),from,to}:undefined)}
</script>
<template><aside v-if="block" class="canvas-panel" aria-label="Writing Block Canvas"><header class="canvas-header"><div><span>WRITING_BLOCK · 本地</span><strong>{{ block.title }}</strong></div><div><span class="save-state">仅内存</span><button class="icon-button" aria-label="关闭 Canvas" @click="workspace.closeCanvas">×</button></div></header><div class="canvas-form"><label class="form-field canvas-editor"><span>内容</span><textarea :value="block.content" rows="24" placeholder="输入本地片段" @input="updateContent" @select="selectText"/></label></div><footer class="canvas-footer">后端没有会话/Writing Block 保存接口；刷新页面会丢失此临时内容。</footer></aside></template>
