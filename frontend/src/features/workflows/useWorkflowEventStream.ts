import { useQueryClient } from '@tanstack/vue-query'
import { onBeforeUnmount, toValue, type MaybeRefOrGetter } from 'vue'

import { consumeWorkflowEvents } from '@/api/endpoints/workflowEvents'
import { workflowsApi } from '@/api/endpoints/workflows'
import type { WorkflowResponse } from '@/api/types'
import { isTerminalWorkflow } from '@/queries/workflows'
import { queryKeys } from '@/queries/keys'
import { useWorkflowStreamStore } from '@/stores/workflowStream'

const MAX_RECONNECT_ATTEMPTS = 5
const BASE_RECONNECT_DELAY_MS = 500

function abortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '工作流事件连接失败'
}

function delay(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = globalThis.setTimeout(resolve, milliseconds)
    signal.addEventListener('abort', () => {
      globalThis.clearTimeout(timer)
      reject(new DOMException('连接已关闭', 'AbortError'))
    }, { once: true })
  })
}

export function useWorkflowEventStream(runId: MaybeRefOrGetter<string>) {
  const queryClient = useQueryClient()
  const store = useWorkflowStreamStore()
  let controller: AbortController | undefined
  let activeRunId = ''
  let loopVersion = 0

  async function latestWorkflow(id: string): Promise<WorkflowResponse> {
    return queryClient.fetchQuery({
      queryKey: queryKeys.workflow(id),
      queryFn: () => workflowsApi.get(id),
      staleTime: 0,
    })
  }

  async function runLoop(id: string, version: number): Promise<void> {
    const currentController = new AbortController()
    controller = currentController
    let attempt = 0

    while (!currentController.signal.aborted && version === loopVersion) {
      store.setConnection(attempt === 0 ? 'connecting' : 'reconnecting', attempt)
      try {
        await consumeWorkflowEvents(id, store.lastEventId, currentController.signal, {
          onOpen: () => store.setConnection('open', attempt),
          onEvent: (event) => store.consume(event),
          onHeartbeat: (heartbeat) => {
            if (heartbeat.runId === id) store.heartbeat(heartbeat.timestamp)
          },
        })
        if (currentController.signal.aborted || version !== loopVersion) return
      } catch (error) {
        if (currentController.signal.aborted || version !== loopVersion || abortError(error)) return
        if (attempt >= MAX_RECONNECT_ATTEMPTS) {
          store.fail(errorMessage(error))
          return
        }
      }

      try {
        const workflow = await latestWorkflow(id)
        if (isTerminalWorkflow(workflow)) {
          store.completeFromSnapshot(workflow.draftContent)
          return
        }
      } catch (error) {
        if (currentController.signal.aborted || abortError(error)) return
      }

      attempt += 1
      if (attempt > MAX_RECONNECT_ATTEMPTS) {
        store.fail('事件流多次断开，运行态草稿已保留。')
        return
      }
      store.setConnection('reconnecting', attempt)
      try {
        await delay(Math.min(BASE_RECONNECT_DELAY_MS * 2 ** (attempt - 1), 8_000), currentController.signal)
      } catch {
        return
      }
    }
  }

  function start(): void {
    const id = toValue(runId)
    if (!id) return
    store.prepare(id)
    if (activeRunId === id && controller && !controller.signal.aborted) return
    controller?.abort()
    activeRunId = id
    loopVersion += 1
    void runLoop(id, loopVersion)
  }

  function stop(draftContent?: string | null): void {
    loopVersion += 1
    controller?.abort()
    controller = undefined
    activeRunId = ''
    if (draftContent !== undefined) store.completeFromSnapshot(draftContent)
    else if (store.connection !== 'idle') store.setConnection('closed')
  }

  function reconnect(): void {
    stop()
    start()
  }

  onBeforeUnmount(() => stop())
  return { start, stop, reconnect }
}
