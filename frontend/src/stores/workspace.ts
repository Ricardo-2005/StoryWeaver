import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type ComposerMode = '聊天' | '规划' | '写作' | '审查' | '只读查询'
export type ComposerTool = '人物' | '世界书' | '大纲' | '一致性审查' | null
export interface LocalWritingBlock { id: string; title: string; content: string }
export interface CanvasSelection { assetName: string; excerpt: string; from: number; to: number }

export const useWorkspaceStore = defineStore('workspace', () => {
  const composerDraft = ref('')
  const mode = ref<ComposerMode>('聊天')
  const tool = ref<ComposerTool>(null)
  const canvasOpen = ref(false)
  const canvasAssetId = ref<string>()
  const canvasBlockId = ref<string>()
  const selection = ref<CanvasSelection>()
  const writingBlocks = ref<LocalWritingBlock[]>([])
  const canSend = computed(() => false)

  function openAsset(assetId: string): void { canvasAssetId.value = assetId; canvasBlockId.value = undefined; canvasOpen.value = true; selection.value = undefined }
  function openBlock(blockId: string): void { canvasBlockId.value = blockId; canvasAssetId.value = undefined; canvasOpen.value = true; selection.value = undefined }
  function closeCanvas(): void { canvasOpen.value = false; canvasAssetId.value = undefined; canvasBlockId.value = undefined; selection.value = undefined }
  function setSelection(next: CanvasSelection | undefined): void { selection.value = next }
  function clearTool(): void { tool.value = null }
  function createWritingBlock(): LocalWritingBlock {
    const block = { id: globalThis.crypto.randomUUID(), title: '未命名片段', content: '' }
    writingBlocks.value.push(block)
    return block
  }
  function updateWritingBlock(id: string, content: string): void {
    const block = writingBlocks.value.find((item) => item.id === id)
    if (block) block.content = content
  }

  return { composerDraft, mode, tool, canvasOpen, canvasAssetId, canvasBlockId, selection, writingBlocks, canSend, openAsset, openBlock, closeCanvas, setSelection, clearTool, createWritingBlock, updateWritingBlock }
})
