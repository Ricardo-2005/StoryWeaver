<script setup lang="ts">
import { computed, ref } from 'vue'
import { useWorkspaceStore, type ComposerMode, type ComposerTool } from '@/stores/workspace'

const workspace = useWorkspaceStore()
const showTools = ref(false)
const modes: ComposerMode[] = ['聊天','规划','写作','审查','只读查询']
const tools: Exclude<ComposerTool,null>[] = ['人物','世界书','大纲','一致性审查']
const disabledReason = computed(() => '后端尚未提供 Conversation、Message 或 Chat SSE 接口，无法发送。')
</script>
<template>
  <div class="composer-wrap">
    <div v-if="workspace.selection" class="context-chips" aria-label="已选择上下文">
      <span>{{ workspace.selection.assetName }} · “{{ workspace.selection.excerpt }}”<button aria-label="移除选区引用" @click="workspace.setSelection(undefined)">×</button></span>
    </div>
    <div class="composer-panel">
      <textarea v-model="workspace.composerDraft" rows="3" placeholder="输入你想做的事情……" aria-label="消息内容" />
      <div class="composer-toolbar">
        <div class="composer-tools">
          <button type="button" @click="showTools=!showTools">＋ 工具</button>
          <div v-if="showTools" class="tool-menu" role="menu">
            <button v-for="item in tools" :key="item" role="menuitem" @click="workspace.tool=item;showTools=false">{{ item }}</button>
          </div>
          <select v-model="workspace.mode" aria-label="工作模式"><option v-for="item in modes" :key="item">{{ item }}</option></select>
          <span v-if="workspace.tool" class="selected-tool">{{ workspace.tool }}</span>
        </div>
        <button class="send-button" type="button" disabled :title="disabledReason">发送</button>
      </div>
    </div>
    <p class="composer-boundary" role="status">{{ disabledReason }} 输入内容仅保留在当前内存会话中。</p>
  </div>
</template>
