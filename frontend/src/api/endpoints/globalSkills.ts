import { apiClient, apiDownload, apiFormData } from '@/api/client'
import type {
  AtomicSkillRuleResponse,
  ContractValidationResponse,
  CreateGlobalSkillRequest,
  ForgeRunResponse,
  CreateForgeRunRequest,
  ForgeMaterialType,
  FoundationBindingResponse,
  ForgeSourceResponse,
  ForgeStepResponse,
  GlobalSkillResponse,
  GlobalSkillVersionResponse,
  ManualTextSourceRequest,
  SkillTestCaseResponse,
} from '@/api/types'

export const globalSkillsApi = {
  list: () => apiClient.get<GlobalSkillResponse[]>('/api/skills'),
  get: (skillId: string) => apiClient.get<GlobalSkillResponse>(`/api/skills/${skillId}`),
  create: (request: CreateGlobalSkillRequest) => apiClient.post<GlobalSkillResponse>('/api/skills', request),
  versions: (skillId: string) => apiClient.get<GlobalSkillVersionResponse[]>(`/api/skills/${skillId}/versions`),
  validate: (skillId: string) => apiClient.post<ContractValidationResponse>(`/api/skills/${skillId}/validate`),
  archive: (skillId: string) => apiClient.delete<void>(`/api/skills/${skillId}`),
  tests: (skillId: string) => apiClient.get<SkillTestCaseResponse[]>(`/api/skills/${skillId}/tests`),
  export: (skillId: string) => apiDownload(`/api/skills/${skillId}/export`),
  createForgeRun: (request: CreateForgeRunRequest) => apiClient.post<ForgeRunResponse>('/api/skill-forge/runs', request),
  getForgeRun: (runId: string) => apiClient.get<ForgeRunResponse>(`/api/skill-forge/runs/${runId}`),
  forgeEvents: (runId: string) => apiClient.get<ForgeStepResponse[]>(`/api/skill-forge/runs/${runId}/events`),
  addManualSource: (runId: string, request: ManualTextSourceRequest) => apiClient.post<ForgeSourceResponse>(`/api/skill-forge/runs/${runId}/sources/text`, request),
  addTxtSources: (runId: string, files: Array<{ file: File; title: string }>, materialType: ForgeMaterialType, ownershipConfirmed: boolean) => {
    const body = new FormData()
    files.forEach(item => {
      body.append('files', item.file, item.file.name)
      body.append('titles', item.title)
    })
    body.append('materialType', materialType)
    body.append('ownershipConfirmed', String(ownershipConfirmed))
    return apiFormData<ForgeSourceResponse[]>(`/api/skill-forge/runs/${runId}/sources/txt`, body)
  },
  forgeSources: (runId: string) => apiClient.get<ForgeSourceResponse[]>(`/api/skill-forge/runs/${runId}/sources`),
  deleteForgeSource: (runId: string, sourceId: string) => apiClient.delete<void>(`/api/skill-forge/runs/${runId}/sources/${sourceId}`),
  startDistillation: (runId: string) => apiClient.post<ForgeRunResponse>(`/api/skill-forge/runs/${runId}/start`),
  forgeRules: (runId: string) => apiClient.get<AtomicSkillRuleResponse[]>(`/api/skill-forge/runs/${runId}/rules`),
  reviewForgeRule: (runId: string, ruleId: string, action: 'ACCEPT' | 'EDIT' | 'DELETE', statement?: string) =>
    apiClient.patch<AtomicSkillRuleResponse>(`/api/skill-forge/runs/${runId}/rules/${ruleId}`, { action, statement }),
  resolveForgeConflicts: (runId: string, resolutions: Array<{ ruleId: string; action: 'ACCEPT' | 'EDIT' | 'DELETE'; statement?: string }>) =>
    apiClient.post<AtomicSkillRuleResponse[]>(`/api/skill-forge/runs/${runId}/resolve-conflicts`, { resolutions }),
  generateForgeContract: (runId: string) => apiClient.post<ForgeRunResponse>(`/api/skill-forge/runs/${runId}/generate-contract`),
  validateForge: (runId: string) => apiClient.post<ContractValidationResponse>(`/api/skill-forge/runs/${runId}/validate`),
  cancelForge: (runId: string) => apiClient.post<void>(`/api/skill-forge/runs/${runId}/cancel`),
  bindings: (projectId: string) => apiClient.get<FoundationBindingResponse[]>(`/api/projects/${projectId}/skill-bindings`),
  bindFoundation: (projectId: string, globalSkillVersionId: string) =>
    apiClient.post<FoundationBindingResponse>(`/api/projects/${projectId}/skill-bindings/foundation`, { globalSkillVersionId }),
  removeFoundation: (projectId: string) => apiClient.delete<void>(`/api/projects/${projectId}/skill-bindings/foundation`),
}
