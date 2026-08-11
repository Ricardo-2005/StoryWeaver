import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useWorkspaceStore } from '@/stores/workspace'

describe('workspace store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue(
      '55555555-5555-4555-8555-555555555555',
    )
  })

  it('keeps writing blocks local and opens them in Canvas', () => {
    const store = useWorkspaceStore()
    const block = store.createWritingBlock()

    store.updateWritingBlock(block.id, '潮声穿过旧码头。')
    store.openBlock(block.id)

    expect(store.writingBlocks).toEqual([
      { id: block.id, title: '未命名片段', content: '潮声穿过旧码头。' },
    ])
    expect(store.canvasOpen).toBe(true)
    expect(store.canvasBlockId).toBe(block.id)
    expect(store.canvasAssetId).toBeUndefined()
    expect(store.canSend).toBe(false)
  })

  it('switches to a canon asset and clears Canvas context on close', () => {
    const store = useWorkspaceStore()

    store.openAsset('asset-1')
    store.setSelection({ assetName: '雾港', excerpt: '潮雾', from: 0, to: 2 })
    store.closeCanvas()

    expect(store.canvasOpen).toBe(false)
    expect(store.canvasAssetId).toBeUndefined()
    expect(store.selection).toBeUndefined()
  })
})
