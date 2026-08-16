import { afterEach, describe, expect, it, vi } from 'vitest'
import { batchesApi, foreshadowsApi, rollingOutlineApi, v15WorkflowApi } from '@/api/endpoints/v15'

afterEach(() => vi.unstubAllGlobals())

describe('V1.5 API contracts', () => {
  it('uses the implemented rolling outline and chapter batch routes', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ projectId: 'p1', version: 0 }), { status: 200, headers: { 'content-type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'b1', status: 'QUEUED' }), { status: 202, headers: { 'content-type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await rollingOutlineApi.get('p1')
    await batchesApi.create('p1', { viewpointCharacterId: 'c1', instruction: '继续主线', chapterIds: ['ch1'], gatedChapterIds: [] })

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/projects/p1/rolling-outline')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/projects/p1/chapter-batches')
    expect((fetchMock.mock.calls[1]?.[1] as RequestInit).method).toBe('POST')
  })

  it('posts bounded local revisions to the workflow route', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'run-1' }), { status: 200, headers: { 'content-type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await v15WorkflowApi.localRevision('run-1', { expectedVersion: 4, startOffset: 10, endOffset: 12, replacement: '新句', reason: '修复称谓' })
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/workflows/run-1/local-revisions')
    expect(JSON.parse(String((fetchMock.mock.calls[0]?.[1] as RequestInit).body))).toMatchObject({ expectedVersion: 4, startOffset: 10 })
  })

  it('cancels a materialized foreshadow through the delete route', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await foreshadowsApi.cancel('foreshadow-1')

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/foreshadows/foreshadow-1')
    expect((fetchMock.mock.calls[0]?.[1] as RequestInit).method).toBe('DELETE')
  })
})
