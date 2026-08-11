import { apiClient } from '@/api/client'
import type {
  CreateProjectRequest,
  ProjectResponse,
  SnapshotRequest,
  SnapshotResponse,
  UpdateProjectRequest,
} from '@/api/types'

export const projectsApi = {
  list: (includeArchived = false) =>
    apiClient.get<ProjectResponse[]>(`/api/projects?includeArchived=${String(includeArchived)}`),
  get: (projectId: string) => apiClient.get<ProjectResponse>(`/api/projects/${projectId}`),
  create: (request: CreateProjectRequest) =>
    apiClient.post<ProjectResponse>('/api/projects', request),
  update: (projectId: string, request: UpdateProjectRequest) =>
    apiClient.put<ProjectResponse>(`/api/projects/${projectId}`, request),
  remove: (projectId: string, expectedVersion: number) =>
    apiClient.delete<void>(`/api/projects/${projectId}?expectedVersion=${expectedVersion}`),
  snapshot: (projectId: string, request: SnapshotRequest) =>
    apiClient.post<SnapshotResponse>(`/api/projects/${projectId}/snapshots`, request),
}
