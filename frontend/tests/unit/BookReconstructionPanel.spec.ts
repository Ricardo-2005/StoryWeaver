import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import BookReconstructionPanel from '@/components/reconstruction/BookReconstructionPanel.vue'

const baseJob = {
  id: null, projectId: 'project-1', mode: 'STANDARD', status: 'NOT_ANALYZED', currentStep: 'NOT_ANALYZED',
  totalChapters: 12, totalChunks: 0, processedChunks: 0, failedChapters: 0, progress: 0,
  estimatedCalls: 0, estimatedInputTokens: 0, estimatedOutputTokens: 0,
  estimatedCostMin: null, estimatedCostMax: null, currency: null, maxBudget: null,
  actualInputTokens: 0, actualOutputTokens: 0, actualReasoningTokens: 0, actualCost: 0,
  retryCount: 0, candidateCount: 0, pendingCandidates: 0, conflicts: 0,
  acceptedCandidates: 0, rejectedCandidates: 0, errorCode: null, errorMessage: null,
  startedAt: null, completedAt: null,
}

describe('BookReconstructionPanel', () => {
  afterEach(() => vi.restoreAllMocks())

  it('shows the estimate and starts only after explicit confirmation', async () => {
    const fetchMock = vi.spyOn(window, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.endsWith('/reconstruction') && (!init?.method || init.method === 'GET')) return response(baseJob)
      if (url.endsWith('/estimate')) return response({
        mode: 'STANDARD', chapters: 12, chunks: 18, estimatedCalls: 23,
        estimatedInputTokens: 42_000, estimatedOutputTokens: 12_000,
        estimatedCostMin: 0.2, estimatedCostMax: 0.8, currency: 'CNY', model: 'extractor', unpriced: false,
      })
      if (url.endsWith('/reconstruction') && init?.method === 'POST') return response({
        ...baseJob, id: 'job-1', status: 'QUEUED', currentStep: 'PREPROCESSING', totalChunks: 18,
      }, 202)
      throw new Error(`Unexpected request ${url}`)
    })

    const wrapper = mount(BookReconstructionPanel, {
      props: { projectId: 'project-1', projectName: '测试书' },
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }]] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('启动前预估')
    expect(wrapper.text()).toContain('23')
    expect(fetchMock.mock.calls.some(([input, init]) => String(input).endsWith('/reconstruction') && init?.method === 'POST')).toBe(false)

    await wrapper.get('button.sw-button--primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('QUEUED')
    expect(fetchMock.mock.calls.some(([input, init]) => String(input).endsWith('/reconstruction') && init?.method === 'POST')).toBe(true)
  })
})

function response(body: unknown, status = 200): Promise<Response> {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }))
}
