import { apiClient, apiFormData } from '@/api/client'
import type {
  BookAnalysisRequest,
  BookAnalysisResponse,
  TxtImportJobResponse,
  TxtImportProjectInput,
} from '@/api/types'

export const TXT_IMPORT_MAX_BYTES = 20 * 1024 * 1024

export function validateTxtImportFile(file: Pick<File, 'name' | 'size'>): string | null {
  if (!file.name.toLowerCase().endsWith('.txt')) return '只支持 .txt 文件。'
  if (file.size <= 0) return 'TXT 文件不能为空。'
  if (file.size > TXT_IMPORT_MAX_BYTES) return 'TXT 单文件不能超过 20 MB。'
  return null
}

export const txtImportsApi = {
  upload: (file: File) => {
    const data = new FormData()
    data.set('file', file)
    return apiFormData<TxtImportJobResponse>('/api/imports/txt', data)
  },
  get: (importId: string) => apiClient.get<TxtImportJobResponse>(`/api/txt-imports/${importId}`),
  parse: (importId: string, encoding: 'AUTO' | 'UTF-8' | 'GB18030' | 'GBK') =>
    apiClient.post<TxtImportJobResponse>(`/api/txt-imports/${importId}/parse`, { encoding }, { timeoutMs: 120_000 }),
  content: (importId: string, chapterId: string) =>
    apiClient.get<{ content: string; truncated: boolean }>(`/api/txt-imports/${importId}/chapters/${chapterId}/content`),
  updateChapter: (importId: string, chapterId: string, request: { expectedVersion: number; title: string; included: boolean }) =>
    apiClient.patch<TxtImportJobResponse>(`/api/txt-imports/${importId}/chapters/${chapterId}`, request),
  reorder: (importId: string, request: { expectedVersion: number; chapterIds: string[] }) =>
    apiClient.post<TxtImportJobResponse>(`/api/txt-imports/${importId}/chapters/reorder`, request),
  merge: (importId: string, request: { expectedVersion: number; firstChapterId: string; secondChapterId: string; title: string | null }) =>
    apiClient.post<TxtImportJobResponse>(`/api/txt-imports/${importId}/chapters/merge`, request),
  split: (importId: string, request: { expectedVersion: number; chapterId: string; splitOffset: number; secondTitle: string | null }) =>
    apiClient.post<TxtImportJobResponse>(`/api/txt-imports/${importId}/chapters/split`, request),
  whole: (importId: string, request: { expectedVersion: number; title: string }) =>
    apiClient.post<TxtImportJobResponse>(`/api/txt-imports/${importId}/chapters/whole`, request),
  fixedSplit: (importId: string, request: { expectedVersion: number; targetCharacters: number }) =>
    apiClient.post<TxtImportJobResponse>(`/api/txt-imports/${importId}/chapters/fixed-split`, request),
  commit: (importId: string, expectedVersion: number, project: TxtImportProjectInput) =>
    apiClient.post<TxtImportJobResponse>(`/api/txt-imports/${importId}/commit`, { expectedVersion, project }, { timeoutMs: 120_000 }),
  cancel: (importId: string) => apiClient.post<TxtImportJobResponse>(`/api/txt-imports/${importId}/cancel`),
  startAnalysis: (projectId: string, request: BookAnalysisRequest) =>
    apiClient.post<BookAnalysisResponse>(`/api/projects/${projectId}/book-analysis`, request),
  analysis: (importId: string) => apiClient.get<BookAnalysisResponse>(`/api/txt-imports/${importId}/analysis`),
  decideCandidate: (importId: string, candidateId: string, accepted: boolean) =>
    apiClient.patch<BookAnalysisResponse>(`/api/txt-imports/${importId}/analysis/candidates/${candidateId}`, { accepted }),
}
