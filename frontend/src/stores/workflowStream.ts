import { defineStore } from 'pinia'

import type { WorkflowEventResponse } from '@/api/types'

export type SseConnectionState = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'closed' | 'failed'

const FLUSH_INTERVAL_MS = 80
let flushTimer: ReturnType<typeof globalThis.setTimeout> | undefined
let pendingBuffer = ''

interface WorkflowStreamState {
  runId: string
  connection: SseConnectionState
  text: string
  lastEventId: number
  lastEventAt: string | null
  lastHeartbeatAt: string | null
  currentStep: string | null
  reconnectAttempt: number
  errorMessage: string
  usage: Record<string, unknown> | null
  warning: string | null
}

export const useWorkflowStreamStore = defineStore('workflow-stream', {
  state: (): WorkflowStreamState => ({
    runId: '',
    connection: 'idle',
    text: '',
    lastEventId: 0,
    lastEventAt: null,
    lastHeartbeatAt: null,
    currentStep: null,
    reconnectAttempt: 0,
    errorMessage: '',
    usage: null,
    warning: null,
  }),
  getters: {
    renderedText: (state) => state.text,
    characterCount(): number {
      return this.renderedText.length
    },
  },
  actions: {
    prepare(runId: string) {
      if (this.runId === runId) return
      this.reset()
      this.runId = runId
    },
    reset() {
      if (flushTimer) globalThis.clearTimeout(flushTimer)
      flushTimer = undefined
      pendingBuffer = ''
      this.$reset()
    },
    setConnection(connection: SseConnectionState, attempt?: number) {
      this.connection = connection
      if (attempt !== undefined) this.reconnectAttempt = attempt
      if (connection !== 'failed') this.errorMessage = ''
    },
    fail(message: string) {
      this.flush()
      this.connection = 'failed'
      this.errorMessage = message
    },
    heartbeat(timestamp: string) {
      this.lastHeartbeatAt = timestamp
    },
    consume(event: WorkflowEventResponse) {
      if (event.runId !== this.runId || event.eventId <= this.lastEventId) return false
      this.lastEventId = event.eventId
      this.lastEventAt = event.timestamp
      this.currentStep = event.step

      if (
        event.type === 'warning'
        && event.step === 'WRITING'
        && event.payload.code === 'workflow_recovered'
      ) {
        if (flushTimer) globalThis.clearTimeout(flushTimer)
        flushTimer = undefined
        this.text = ''
        pendingBuffer = ''
        this.warning = '后端已恢复 Writer，本次正文从恢复点重新生成。'
      } else if (event.type === 'text.delta' && typeof event.payload.text === 'string') {
        pendingBuffer += event.payload.text
        this.scheduleFlush()
      } else if (event.type === 'text.completed') {
        this.flush()
      } else if (event.type === 'usage.partial') {
        this.usage = event.payload
      } else if (event.type === 'warning') {
        this.warning = typeof event.payload.code === 'string' ? event.payload.code : '工作流警告'
      } else if (event.type === 'workflow.error') {
        this.warning = typeof event.payload.message === 'string' ? event.payload.message : '工作流执行失败'
      }
      return true
    },
    scheduleFlush() {
      if (flushTimer) return
      flushTimer = globalThis.setTimeout(() => this.flush(), FLUSH_INTERVAL_MS)
    },
    flush() {
      if (flushTimer) globalThis.clearTimeout(flushTimer)
      flushTimer = undefined
      if (!pendingBuffer) return
      this.text += pendingBuffer
      pendingBuffer = ''
    },
    completeFromSnapshot(draftContent: string | null) {
      if (flushTimer) globalThis.clearTimeout(flushTimer)
      flushTimer = undefined
      if (typeof draftContent === 'string') {
        this.text = draftContent
        pendingBuffer = ''
      } else {
        this.flush()
      }
      this.connection = 'closed'
      this.errorMessage = ''
    },
    discardDraft() {
      if (flushTimer) globalThis.clearTimeout(flushTimer)
      flushTimer = undefined
      pendingBuffer = ''
      this.text = ''
    },
  },
})
