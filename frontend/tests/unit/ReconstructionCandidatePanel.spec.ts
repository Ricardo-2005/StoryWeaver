import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import ReconstructionCandidatePanel from '@/components/reconstruction/ReconstructionCandidatePanel.vue'

describe('ReconstructionCandidatePanel', () => {
  afterEach(() => vi.restoreAllMocks())

  it('loads candidates for the requested module and emits the selected item', async () => {
    const candidate = {
      id: 'candidate-1', chapterId: 'chapter-1', candidateType: 'WORLDBOOK',
      content: '青崖宗以青色灯火作为内门示警。', status: 'CANDIDATE', confidence: 'HIGH',
      inferenceType: 'DIRECT_FACT', evidenceCount: 2, sourceCoverage: 0.8,
      sourceAnchors: '[{"chapterNo":1,"quote":"青色灯火"}]', safeToApply: false,
      createdAt: '2026-08-13T00:00:00Z',
    }
    const fetchMock = vi.spyOn(window, 'fetch').mockResolvedValue(response([candidate]))
    const wrapper = mount(ReconstructionCandidatePanel, {
      props: {
        projectId: 'project-1', candidateType: 'WORLDBOOK', title: 'AI 世界书候选',
        description: '从原文提取', useLabel: '载入世界书表单',
      },
      global: {
        plugins: [[VueQueryPlugin, {
          queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }),
        }]],
      },
    })
    await flushPromises()

    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/reconstruction/candidates?type=WORLDBOOK')
    expect(wrapper.text()).toContain(candidate.content)
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('use')?.[0]?.[0]).toEqual(candidate)
  })

  it('lets an accepted candidate return to pending review', async () => {
    const candidate = {
      id: 'candidate-accepted', chapterId: null, candidateType: 'WORLDBOOK',
      content: '白石峰是宗门禁地。', status: 'ACCEPTED', confidence: 'HIGH',
      inferenceType: 'USER_CONFIRMED', evidenceCount: 1, sourceCoverage: 1,
      sourceAnchors: '[]', safeToApply: false, createdAt: '2026-08-13T00:00:00Z',
    }
    const fetchMock = vi.spyOn(window, 'fetch').mockImplementation(async (_input, init) => {
      if (init?.method === 'PATCH') return response({ ...candidate, status: 'CANDIDATE' })
      return response([candidate])
    })
    const wrapper = mount(ReconstructionCandidatePanel, {
      props: {
        projectId: 'project-1', candidateType: 'WORLDBOOK', title: 'AI 世界书候选',
        description: '从原文提取',
      },
      global: {
        plugins: [[VueQueryPlugin, {
          queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }),
        }]],
      },
    })
    await flushPromises()

    await wrapper.get('button').trigger('click')
    await flushPromises()

    const patchCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'PATCH')
    expect(patchCall).toBeTruthy()
    expect(patchCall?.[1]?.body).toBe(JSON.stringify({ approve: false }))
  })

  it('does not repeat candidates already applied to a formal asset', async () => {
    const candidate = {
      id: 'candidate-applied', chapterId: null, candidateType: 'WORLDBOOK',
      content: '已写入的世界事实', status: 'APPLIED', confidence: 'HIGH',
      inferenceType: 'MODEL_INFERENCE', evidenceCount: 1, sourceCoverage: 1,
      sourceAnchors: '[]', safeToApply: false, createdAt: '2026-08-13T00:00:00Z',
    }
    vi.spyOn(window, 'fetch').mockResolvedValue(response([candidate]))
    const wrapper = mount(ReconstructionCandidatePanel, {
      props: { projectId: 'project-1', candidateType: 'WORLDBOOK', title: 'AI 世界书候选', description: '候选' },
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }]] },
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain(candidate.content)
  })

  it('shows rejected character candidates and restores them for another review', async () => {
    const candidate = {
      id: 'candidate-rejected', chapterId: null, candidateType: 'CHARACTER',
      content: '韩守拙听到青铜铃后脸色变化。', status: 'REJECTED', confidence: 'MEDIUM',
      inferenceType: 'MODEL_INFERENCE', evidenceCount: 1, sourceCoverage: 1,
      sourceAnchors: '[]', safeToApply: false, createdAt: '2026-08-13T00:00:00Z',
    }
    const fetchMock = vi.spyOn(window, 'fetch').mockImplementation(async (_input, init) => {
      if (init?.method === 'POST') return response({ ...candidate, status: 'CANDIDATE' })
      return response([candidate])
    })
    const wrapper = mount(ReconstructionCandidatePanel, {
      props: {
        projectId: 'project-1', candidateType: 'CHARACTER', title: 'AI 人物拆书记录',
        description: '人物候选', showRejected: true,
      },
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }]] },
    })
    await flushPromises()

    await wrapper.get('button').trigger('click')
    await flushPromises()

    const restoreCall = fetchMock.mock.calls.find(([input, init]) =>
      init?.method === 'POST' && String(input).endsWith('/candidates/candidate-rejected/restore'))
    expect(restoreCall).toBeTruthy()
  })
})

function response(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}
