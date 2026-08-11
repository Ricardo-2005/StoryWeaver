import { apiClient } from '@/api/client'
import type {
  BudgetResponse,
  CostSummaryResponse,
  PricingRuleResponse,
  UpdateBudgetRequest,
  UsageResponse,
} from '@/api/types'

export const usageApi = {
  list: (projectId: string) => apiClient.get<UsageResponse[]>(`/api/projects/${projectId}/usage`),
  costs: (projectId: string) => apiClient.get<CostSummaryResponse>(`/api/projects/${projectId}/costs`),
  budget: (projectId: string) => apiClient.get<BudgetResponse>(`/api/projects/${projectId}/budget`),
  updateBudget: (projectId: string, request: UpdateBudgetRequest) =>
    apiClient.put<BudgetResponse>(`/api/projects/${projectId}/budget`, request),
  pricingRules: () => apiClient.get<PricingRuleResponse[]>('/api/pricing-rules'),
}
