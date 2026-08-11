import { apiClient } from '@/api/client'
import type {
  AssetResponse,
  AssetTransitionRequest,
  CreateAssetRequest,
  UpdateAssetRequest,
} from '@/api/types'

export const canonApi = {
  list: (projectId: string) =>
    apiClient.get<AssetResponse[]>(`/api/projects/${projectId}/assets`),
  create: (projectId: string, request: CreateAssetRequest) =>
    apiClient.post<AssetResponse>(`/api/projects/${projectId}/assets`, request),
  update: (assetId: string, request: UpdateAssetRequest) =>
    apiClient.put<AssetResponse>(`/api/assets/${assetId}`, request),
  confirm: (assetId: string, request: AssetTransitionRequest) =>
    apiClient.post<AssetResponse>(`/api/assets/${assetId}/confirm`, request),
  deprecate: (assetId: string, request: AssetTransitionRequest) =>
    apiClient.post<AssetResponse>(`/api/assets/${assetId}/deprecate`, request),
}
