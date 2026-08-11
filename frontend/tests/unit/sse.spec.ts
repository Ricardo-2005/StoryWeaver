import { afterEach, describe, expect, it, vi } from 'vitest'

import { consumeWorkflowEvents, decodeWorkflowMessage } from '@/api/endpoints/workflowEvents'
import { SseParser } from '@/api/sse'
import { clearAccessToken, setAccessToken } from '@/api/tokenMemory'

afterEach(() => {
  clearAccessToken()
  vi.unstubAllGlobals()
})

describe('SseParser', () => {
  it('parses messages split across chunks with CRLF and multi-line data', () => {
    const parser = new SseParser()
    expect(parser.feed('id: 4\r')).toEqual([])
    expect(parser.feed('\nevent: text.del')).toEqual([])
    expect(parser.feed('ta\r\ndata: {"part":\r\ndata: "港口"}\r\n\r')).toEqual([])
    expect(parser.feed('\n')).toEqual([
      { id: '4', event: 'text.delta', data: '{"part":\n"港口"}' },
    ])
  })

  it('ignores comments and malformed workflow JSON', () => {
    const parser = new SseParser()
    expect(parser.feed(': keepalive\n\nevent: warning\ndata: not-json\n\n')).toEqual([
      { event: 'warning', data: 'not-json' },
    ])
    expect(decodeWorkflowMessage({ event: 'warning', data: 'not-json' })).toBeUndefined()
  })
})

describe('consumeWorkflowEvents', () => {
  it('uses the in-memory Bearer token and Last-Event-ID header', async () => {
    setAccessToken('memory-only-token')
    const event = {
      eventId: 8,
      runId: 'run-1',
      type: 'text.delta',
      step: 'WRITING',
      timestamp: '2026-08-03T00:00:00Z',
      payload: { text: '潮声' },
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      `id: 8\nevent: text.delta\ndata: ${JSON.stringify(event)}\n\n`,
      { status: 200, headers: { 'content-type': 'text/event-stream;charset=UTF-8' } },
    ))
    vi.stubGlobal('fetch', fetchMock)
    const received = vi.fn()

    await consumeWorkflowEvents('run-1', 7, new AbortController().signal, { onEvent: received })

    expect(received).toHaveBeenCalledWith(event)
    const [url, options] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/workflows/run-1/events')
    expect(url).not.toContain('token')
    const headers = new Headers(options.headers)
    expect(headers.get('Authorization')).toBe('Bearer memory-only-token')
    expect(headers.get('Last-Event-ID')).toBe('7')
  })
})
