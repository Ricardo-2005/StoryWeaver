import { apiClient, apiDownload, apiFormData } from '@/api/client'
import type {
  AdvanceRollingOutlineRequest, AliasMergeRequest, ChapterBatchResponse, ChapterBranchResponse,
  CreateChapterBatchRequest, CreateChapterBranchRequest, CreateChapterBranchVersionRequest,
  DecideImportCandidatesRequest, ForeshadowInput, ForeshadowResponse, ImpactReportResponse,
  ImportResponse, LocalRevisionRequest, ModelAttemptResponse, ModelHealthResponse,
  PutRollingOutlineRequest, ReplaceImportChaptersRequest, RollingOutlineResponse, StoryGateResponse,
  WorkflowResponse,
} from '@/api/types'

export const importsApi = {
  upload: (projectId: string, file: File) => { const data = new FormData(); data.set('file', file); return apiFormData<ImportResponse>(`/api/projects/${projectId}/imports`, data) },
  list: (projectId: string) => apiClient.get<ImportResponse[]>(`/api/projects/${projectId}/imports`),
  get: (id: string) => apiClient.get<ImportResponse>(`/api/imports/${id}`),
  replaceChapters: (id: string, request: ReplaceImportChaptersRequest) => apiClient.put<ImportResponse>(`/api/imports/${id}/chapters`, request),
  extract: (id: string) => apiClient.post<ImportResponse>(`/api/imports/${id}/extract`),
  retry: (id: string) => apiClient.post<ImportResponse>(`/api/imports/${id}/retry`),
  cancel: (id: string) => apiClient.post<ImportResponse>(`/api/imports/${id}/cancel`),
  complete: (id: string) => apiClient.post<ImportResponse>(`/api/imports/${id}/complete`),
  decide: (id: string, request: DecideImportCandidatesRequest) => apiClient.post<ImportResponse>(`/api/imports/${id}/candidates/decide`, request),
  mergeAlias: (id: string, request: AliasMergeRequest) => apiClient.post<void>(`/api/imports/${id}/aliases/merge`, request),
  exportGit: (projectId: string) => apiDownload(`/api/projects/${projectId}/exports/git`),
}
export const foreshadowsApi = {
  list: (projectId: string) => apiClient.get<ForeshadowResponse[]>(`/api/projects/${projectId}/foreshadows`),
  create: (projectId: string, value: ForeshadowInput) => apiClient.post<ForeshadowResponse>(`/api/projects/${projectId}/foreshadows`, value),
  update: (id: string, value: ForeshadowInput & { expectedVersion: number }) => apiClient.put<ForeshadowResponse>(`/api/foreshadows/${id}`, value),
  transition: (id: string, value: { expectedVersion: number; status: string; resolvedChapterId: string | null }) => apiClient.post<ForeshadowResponse>(`/api/foreshadows/${id}/transition`, value),
}
export const impactApi = { create: (chapterId: string) => apiClient.post<ImpactReportResponse>(`/api/chapters/${chapterId}/impact-reports`), list: (chapterId: string) => apiClient.get<ImpactReportResponse[]>(`/api/chapters/${chapterId}/impact-reports`), get: (id: string) => apiClient.get<ImpactReportResponse>(`/api/impact-reports/${id}`) }
export const rollingOutlineApi = { get: (projectId: string) => apiClient.get<RollingOutlineResponse>(`/api/projects/${projectId}/rolling-outline`), put: (projectId: string, value: PutRollingOutlineRequest) => apiClient.put<RollingOutlineResponse>(`/api/projects/${projectId}/rolling-outline`, value), advance: (projectId: string, value: AdvanceRollingOutlineRequest) => apiClient.post<RollingOutlineResponse>(`/api/projects/${projectId}/rolling-outline/advance`, value) }
export const batchesApi = { list: (projectId: string) => apiClient.get<ChapterBatchResponse[]>(`/api/projects/${projectId}/chapter-batches`), create: (projectId: string, value: CreateChapterBatchRequest) => apiClient.post<ChapterBatchResponse>(`/api/projects/${projectId}/chapter-batches`, value), get: (id: string) => apiClient.get<ChapterBatchResponse>(`/api/chapter-batches/${id}`), pause: (id: string) => apiClient.post<ChapterBatchResponse>(`/api/chapter-batches/${id}/pause`), resume: (id: string) => apiClient.post<ChapterBatchResponse>(`/api/chapter-batches/${id}/resume`), cancel: (id: string) => apiClient.post<ChapterBatchResponse>(`/api/chapter-batches/${id}/cancel`), gates: (id: string) => apiClient.get<StoryGateResponse[]>(`/api/chapter-batches/${id}/gates`), decideGate: (id: string, approve: boolean) => apiClient.post<StoryGateResponse>(`/api/story-gates/${id}/${approve ? 'approve' : 'reject'}`) }
export const branchesApi = { list: (chapterId: string) => apiClient.get<ChapterBranchResponse[]>(`/api/chapters/${chapterId}/branches`), get: (id: string) => apiClient.get<ChapterBranchResponse>(`/api/chapter-branches/${id}`), create: (chapterId: string, value: CreateChapterBranchRequest) => apiClient.post<ChapterBranchResponse>(`/api/chapters/${chapterId}/branches`, value), addVersion: (id: string, value: CreateChapterBranchVersionRequest) => apiClient.post<ChapterBranchResponse>(`/api/chapter-branches/${id}/versions`, value), promoteImpact: (id: string, expectedVersion: number) => apiClient.post<ChapterBranchResponse>(`/api/chapter-branches/${id}/promote-impact`, { expectedVersion }) }
export const v15WorkflowApi = { localRevision: (runId: string, value: LocalRevisionRequest) => apiClient.post<WorkflowResponse>(`/api/workflows/${runId}/local-revisions`, value), attempts: (runId: string) => apiClient.get<ModelAttemptResponse[]>(`/api/workflows/${runId}/model-attempts`), modelHealth: () => apiClient.get<ModelHealthResponse>('/api/ai/model-health') }
