import { apiClient } from '@/api/client'
import type {
  ApproveWorkflowRequest,
  ModelConfigurationResponse,
  RevisionRequest,
  StartWorkflowRequest,
  WorkflowResponse,
} from '@/api/types'

export const workflowsApi = {
  start: (chapterId: string, request: StartWorkflowRequest, idempotencyKey: string) =>
    apiClient.post<WorkflowResponse>(`/api/chapters/${chapterId}/workflows`, request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  get: (runId: string) => apiClient.get<WorkflowResponse>(`/api/workflows/${runId}`),
  cancel: (runId: string) => apiClient.post<WorkflowResponse>(`/api/workflows/${runId}/cancel`),
  requestRevision: (runId: string, request: RevisionRequest) =>
    apiClient.post<WorkflowResponse>(`/api/workflows/${runId}/request-revision`, request),
  reextract: (runId: string, request: RevisionRequest) =>
    apiClient.post<WorkflowResponse>(`/api/workflows/${runId}/reextract`, request),
  approve: (runId: string, request: ApproveWorkflowRequest) =>
    apiClient.post<WorkflowResponse>(`/api/workflows/${runId}/approve`, request),
  modelConfig: () => apiClient.get<ModelConfigurationResponse[]>('/api/ai/model-config'),
}
