import { apiClient } from '@/api/client'
import type {
  ChapterResponse, ChapterVersionResponse, CharacterResponse, CharacterStateResponse,
  CreateChapterRequest, CreateChapterVersionRequest, CreateCharacterRequest, CreateOutlineRequest,
  CreateSkillRequest, CreateWorldbookEntryRequest, OutlineResponse, SkillCompositionResponse,
  RestoreChapterVersionRequest, SkillResponse, UpdateChapterOutlineRequest, UpdateCharacterRequest, UpdateCharacterStateRequest,
  UpdateOutlineRequest, UpdateSkillRequest, UpdateWorldbookEntryRequest, WorldbookEntryResponse,
} from '@/api/types'

export const charactersApi = {
  list: (projectId: string) => apiClient.get<CharacterResponse[]>(`/api/projects/${projectId}/characters`),
  create: (projectId: string, body: CreateCharacterRequest) => apiClient.post<CharacterResponse>(`/api/projects/${projectId}/characters`, body),
  update: (id: string, body: UpdateCharacterRequest) => apiClient.put<CharacterResponse>(`/api/characters/${id}`, body),
  updateState: (id: string, body: UpdateCharacterStateRequest) => apiClient.put<CharacterStateResponse>(`/api/characters/${id}/state`, body),
}

export const worldbookApi = {
  list: (projectId: string) => apiClient.get<WorldbookEntryResponse[]>(`/api/projects/${projectId}/worldbook-entries`),
  create: (projectId: string, body: CreateWorldbookEntryRequest) => apiClient.post<WorldbookEntryResponse>(`/api/projects/${projectId}/worldbook-entries`, body),
  update: (id: string, body: UpdateWorldbookEntryRequest) => apiClient.put<WorldbookEntryResponse>(`/api/worldbook-entries/${id}`, body),
}

export const outlinesApi = {
  list: (projectId: string) => apiClient.get<OutlineResponse[]>(`/api/projects/${projectId}/outlines`),
  create: (projectId: string, body: CreateOutlineRequest) => apiClient.post<OutlineResponse>(`/api/projects/${projectId}/outlines`, body),
  update: (id: string, body: UpdateOutlineRequest) => apiClient.put<OutlineResponse>(`/api/outlines/${id}`, body),
}

export const chaptersApi = {
  list: (projectId: string) => apiClient.get<ChapterResponse[]>(`/api/projects/${projectId}/chapters`),
  get: (id: string) => apiClient.get<ChapterResponse>(`/api/chapters/${id}`),
  create: (projectId: string, body: CreateChapterRequest) => apiClient.post<ChapterResponse>(`/api/projects/${projectId}/chapters`, body),
  updateOutline: (id: string, body: UpdateChapterOutlineRequest) => apiClient.put<ChapterResponse>(`/api/chapters/${id}/outline`, body),
  addVersion: (id: string, body: CreateChapterVersionRequest) => apiClient.post<ChapterResponse>(`/api/chapters/${id}/versions`, body),
  versions: (id: string) => apiClient.get<ChapterVersionResponse[]>(`/api/chapters/${id}/versions`),
  restore: (id: string, versionNo: number, body: RestoreChapterVersionRequest) => apiClient.post<ChapterResponse>(`/api/chapters/${id}/restore/${versionNo}`, body),
}

export const skillsApi = {
  list: (projectId: string) => apiClient.get<SkillResponse[]>(`/api/projects/${projectId}/skills`),
  create: (projectId: string, body: CreateSkillRequest) => apiClient.post<SkillResponse>(`/api/projects/${projectId}/skills`, body),
  update: (id: string, body: UpdateSkillRequest) => apiClient.put<SkillResponse>(`/api/skills/${id}`, body),
  compose: (projectId: string, chapterId: string | null = null) => apiClient.post<SkillCompositionResponse>(`/api/projects/${projectId}/skills/compose`, { chapterId }),
}
