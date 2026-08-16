import { apiClient } from '@/api/client'
import type {
  ReconstructionCandidate,
  ReconstructionEstimate,
  ReconstructionJob,
  ReconstructionOptions,
} from '@/api/types'

export const reconstructionApi = {
  estimate: (projectId: string, options: ReconstructionOptions) =>
    apiClient.post<ReconstructionEstimate>(`/api/projects/${projectId}/reconstruction/estimate`, options),
  start: (projectId: string, request: ReconstructionOptions & { maxBudget: number | null }) =>
    apiClient.post<ReconstructionJob>(`/api/projects/${projectId}/reconstruction`, request),
  status: (projectId: string) =>
    apiClient.get<ReconstructionJob>(`/api/projects/${projectId}/reconstruction`),
  pause: (projectId: string) =>
    apiClient.post<ReconstructionJob>(`/api/projects/${projectId}/reconstruction/pause`),
  resume: (projectId: string, maxBudget: number | null = null) =>
    apiClient.post<ReconstructionJob>(`/api/projects/${projectId}/reconstruction/resume`, { maxBudget }),
  cancel: (projectId: string) =>
    apiClient.post<ReconstructionJob>(`/api/projects/${projectId}/reconstruction/cancel`),
  retryFailed: (projectId: string) =>
    apiClient.post<ReconstructionJob>(`/api/projects/${projectId}/reconstruction/retry-failed`),
  candidates: (projectId: string, filters: { status?: string; type?: string } = {}) => {
    const query = new URLSearchParams()
    if (filters.status) query.set('status', filters.status)
    if (filters.type) query.set('type', filters.type)
    const suffix = query.size ? `?${query.toString()}` : ''
    return apiClient.get<ReconstructionCandidate[]>(`/api/projects/${projectId}/reconstruction/candidates${suffix}`)
  },
  decideCandidate: (projectId: string, candidateId: string, approve: boolean) =>
    apiClient.patch<ReconstructionCandidate>(`/api/projects/${projectId}/reconstruction/candidates/${candidateId}`, { approve }),
  restoreCandidate: (projectId: string, candidateId: string) =>
    apiClient.post<ReconstructionCandidate>(`/api/projects/${projectId}/reconstruction/candidates/${candidateId}/restore`),
  revokeCandidate: (projectId: string, candidateId: string, reason: string) =>
    apiClient.post<ReconstructionCandidate>(`/api/projects/${projectId}/reconstruction/candidates/${candidateId}/revoke`, { reason }),
  approveSafe: (projectId: string) =>
    apiClient.post<ReconstructionJob>(`/api/projects/${projectId}/reconstruction/approve-safe`),
}
