import { afterEach, describe, expect, it, vi } from 'vitest'

import { apiClient, apiFormData, setUnauthorizedHandler } from '@/api/client'
import { HttpProblemError } from '@/api/errors'
import { clearAccessToken, setAccessToken } from '@/api/tokenMemory'

afterEach(() => {
  clearAccessToken()
  setUnauthorizedHandler(undefined)
  vi.unstubAllGlobals()
})

describe('apiClient', () => {
  it('adds the in-memory Bearer token and parses JSON', async () => {
    setAccessToken('phase-one-token')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: 'project-1' }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiClient.get('/api/projects/project-1')).resolves.toEqual({ id: 'project-1' })
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(new Headers(request.headers).get('Authorization')).toBe('Bearer phase-one-token')
  })

  it('normalizes 401 and invokes the session-expiry hook', async () => {
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            type: 'urn:storyweaver:error:authentication_required',
            title: 'Unauthorized',
            status: 401,
            detail: 'Authentication is required',
            code: 'authentication_required',
          }),
          { status: 401, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    )

    await expect(apiClient.get('/api/me')).rejects.toBeInstanceOf(HttpProblemError)
    expect(unauthorized).toHaveBeenCalledOnce()
  })

  it('uploads FormData without forcing a JSON content type', async () => {
    setAccessToken('upload-token')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: 'import-1', status: 'SPLIT_REVIEW' }), {
        status: 202,
        headers: { 'content-type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const body = new FormData()
    body.set('file', new File(['第一章\n正文'], 'novel.txt', { type: 'text/plain' }))

    await expect(apiFormData('/api/projects/project-1/imports', body)).resolves.toMatchObject({ id: 'import-1' })
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    const headers = new Headers(request.headers)
    expect(headers.get('Authorization')).toBe('Bearer upload-token')
    expect(headers.has('Content-Type')).toBe(false)
    expect(request.body).toBe(body)
  })
})
