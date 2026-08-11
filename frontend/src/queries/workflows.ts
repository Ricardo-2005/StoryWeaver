import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { chaptersApi, charactersApi, skillsApi } from '@/api/endpoints/assets'
import { workflowsApi } from '@/api/endpoints/workflows'
import { usageApi } from '@/api/endpoints/usage'
import type { ApproveWorkflowRequest, RevisionRequest, StartWorkflowRequest, WorkflowResponse } from '@/api/types'
import { queryKeys } from '@/queries/keys'

const terminalStatuses = new Set<WorkflowResponse['status']>([
  'WAITING_APPROVAL', 'REVISION_REQUIRED', 'COMPLETED', 'BLOCKED',
  'FAILED', 'CANCELLED', 'ROLLED_BACK',
])

export function isTerminalWorkflow(workflow: WorkflowResponse | undefined): boolean {
  return Boolean(workflow && terminalStatuses.has(workflow.status))
}

export function usePreflightCharactersQuery(projectId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.characters(toValue(projectId))),
    queryFn: () => charactersApi.list(toValue(projectId)),
    enabled: () => toValue(projectId).length > 0,
  })
}

export function usePreflightChaptersQuery(projectId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.chapters(toValue(projectId))),
    queryFn: () => chaptersApi.list(toValue(projectId)),
    enabled: () => toValue(projectId).length > 0,
  })
}

export function useSkillCompositionQuery(
  projectId: MaybeRefOrGetter<string>,
  chapterId: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => queryKeys.skillComposition(toValue(projectId), toValue(chapterId))),
    queryFn: () => skillsApi.compose(toValue(projectId), toValue(chapterId)),
    enabled: () => toValue(projectId).length > 0 && toValue(chapterId).length > 0,
  })
}

export function useBudgetQuery(projectId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.budget(toValue(projectId))),
    queryFn: () => usageApi.budget(toValue(projectId)),
    enabled: () => toValue(projectId).length > 0,
  })
}

export function useModelConfigQuery() {
  return useQuery({ queryKey: queryKeys.modelConfig, queryFn: workflowsApi.modelConfig })
}

export function useWorkflowQuery(runId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.workflow(toValue(runId))),
    queryFn: () => workflowsApi.get(toValue(runId)),
    enabled: () => toValue(runId).length > 0,
    refetchInterval: (query) => isTerminalWorkflow(query.state.data) ? false : 2_000,
  })
}

export function useStartWorkflowMutation(chapterId: MaybeRefOrGetter<string>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ request, idempotencyKey }: { request: StartWorkflowRequest; idempotencyKey: string }) =>
      workflowsApi.start(toValue(chapterId), request, idempotencyKey),
    onSuccess: (workflow) => queryClient.setQueryData(queryKeys.workflow(workflow.id), workflow),
  })
}

export function useCancelWorkflowMutation(runId: MaybeRefOrGetter<string>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => workflowsApi.cancel(toValue(runId)),
    onSuccess: (workflow) => queryClient.setQueryData(queryKeys.workflow(workflow.id), workflow),
  })
}

export function useRequestRevisionMutation(runId: MaybeRefOrGetter<string>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: RevisionRequest) => workflowsApi.requestRevision(toValue(runId), request),
    onSuccess: (workflow) => queryClient.setQueryData(queryKeys.workflow(workflow.id), workflow),
  })
}

export function useApproveWorkflowMutation(
  projectId: MaybeRefOrGetter<string>,
  chapterId: MaybeRefOrGetter<string>,
  runId: MaybeRefOrGetter<string>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: ApproveWorkflowRequest) => workflowsApi.approve(toValue(runId), request),
    onError: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.workflow(toValue(runId)) })
    },
    onSuccess: async (workflow) => {
      queryClient.setQueryData(queryKeys.workflow(workflow.id), workflow)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.chapter(toValue(chapterId)) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.chapters(toValue(projectId)) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.chapterVersions(toValue(chapterId)) }),
      ])
    },
  })
}
