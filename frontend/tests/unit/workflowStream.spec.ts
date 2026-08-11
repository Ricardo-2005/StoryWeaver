import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { WorkflowEventResponse } from '@/api/types'
import { useWorkflowStreamStore } from '@/stores/workflowStream'

function event(eventId: number, type: string, payload: Record<string, unknown>): WorkflowEventResponse {
  return {
    eventId,
    runId: 'run-1',
    type,
    step: 'WRITING',
    timestamp: '2026-08-03T00:00:00Z',
    payload,
  }
}

describe('workflow stream store', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('buffers deltas for 80ms and ignores duplicate event IDs', () => {
    const store = useWorkflowStreamStore()
    store.prepare('run-1')

    expect(store.consume(event(1, 'text.delta', { text: '潮' }))).toBe(true)
    expect(store.consume(event(1, 'text.delta', { text: '潮' }))).toBe(false)
    expect(store.consume(event(2, 'text.delta', { text: '声' }))).toBe(true)
    expect(store.text).toBe('')
    expect(store.renderedText).toBe('')

    vi.advanceTimersByTime(79)
    expect(store.text).toBe('')
    vi.advanceTimersByTime(1)
    expect(store.text).toBe('潮声')
    expect(store.lastEventId).toBe(2)
  })

  it('clears an abandoned draft when the backend recovers Writer', () => {
    const store = useWorkflowStreamStore()
    store.prepare('run-1')
    store.consume(event(1, 'text.delta', { text: '旧正文' }))
    store.consume(event(2, 'warning', { code: 'workflow_recovered', recoveryCount: 1 }))
    store.consume(event(3, 'text.delta', { text: '新正文' }))
    store.flush()

    expect(store.text).toBe('新正文')
    expect(store.warning).toContain('重新生成')
  })

  it('uses the authoritative REST snapshot when a run finishes', () => {
    const store = useWorkflowStreamStore()
    store.prepare('run-1')
    store.consume(event(1, 'text.delta', { text: '局部' }))
    store.completeFromSnapshot('后端完整正文')

    expect(store.renderedText).toBe('后端完整正文')
    expect(store.connection).toBe('closed')
  })
})
