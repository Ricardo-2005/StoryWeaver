import { apiStream } from '@/api/client'
import { SseParser, type SseMessage } from '@/api/sse'
import type { WorkflowEventResponse } from '@/api/types'

export interface WorkflowHeartbeat {
  runId: string
  timestamp: string
}

export interface WorkflowEventCallbacks {
  onOpen?: () => void
  onEvent: (event: WorkflowEventResponse) => void
  onHeartbeat?: (heartbeat: WorkflowHeartbeat) => void
}

function objectValue(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined
}

export function decodeWorkflowMessage(message: SseMessage):
  | { kind: 'event'; value: WorkflowEventResponse }
  | { kind: 'heartbeat'; value: WorkflowHeartbeat }
  | undefined {
  let parsed: unknown
  try {
    parsed = JSON.parse(message.data)
  } catch {
    return undefined
  }
  const value = objectValue(parsed)
  if (!value) return undefined

  if (message.event === 'heartbeat') {
    if (typeof value.runId !== 'string' || typeof value.timestamp !== 'string') return undefined
    return { kind: 'heartbeat', value: { runId: value.runId, timestamp: value.timestamp } }
  }

  if (
    typeof value.eventId !== 'number'
    || typeof value.runId !== 'string'
    || typeof value.type !== 'string'
    || typeof value.step !== 'string'
    || typeof value.timestamp !== 'string'
  ) return undefined
  const payload = objectValue(value.payload)
  if (!payload) return undefined
  return {
    kind: 'event',
    value: {
      eventId: value.eventId,
      runId: value.runId,
      type: value.type,
      step: value.step,
      timestamp: value.timestamp,
      payload,
    },
  }
}

export async function consumeWorkflowEvents(
  runId: string,
  afterEventId: number,
  signal: AbortSignal,
  callbacks: WorkflowEventCallbacks,
): Promise<void> {
  const headers = new Headers()
  if (afterEventId > 0) headers.set('Last-Event-ID', String(afterEventId))
  const response = await apiStream(`/api/workflows/${runId}/events`, { method: 'GET', headers, signal })
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('text/event-stream')) {
    throw new Error('工作流事件接口未返回 text/event-stream')
  }
  if (!response.body) throw new Error('浏览器未提供可读取的事件流')

  callbacks.onOpen?.()
  const parser = new SseParser()
  const decoder = new TextDecoder()
  const reader = response.body.getReader()
  const dispatch = (messages: SseMessage[]) => {
    for (const message of messages) {
      const decoded = decodeWorkflowMessage(message)
      if (decoded?.kind === 'event') callbacks.onEvent(decoded.value)
      else if (decoded?.kind === 'heartbeat') callbacks.onHeartbeat?.(decoded.value)
    }
  }
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      dispatch(parser.feed(decoder.decode(value, { stream: true })))
    }
    dispatch(parser.feed(decoder.decode()))
    dispatch(parser.finish())
  } finally {
    reader.releaseLock()
  }
}
