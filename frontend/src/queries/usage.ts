import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { usageApi } from '@/api/endpoints/usage'
import type { UpdateBudgetRequest } from '@/api/types'
import { queryKeys } from '@/queries/keys'

export function useUsageQuery(projectId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.usage(toValue(projectId))),
    queryFn: () => usageApi.list(toValue(projectId)),
    enabled: () => toValue(projectId).length > 0,
  })
}

export function useCostSummaryQuery(projectId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.costs(toValue(projectId))),
    queryFn: () => usageApi.costs(toValue(projectId)),
    enabled: () => toValue(projectId).length > 0,
  })
}

export function usePricingRulesQuery() {
  return useQuery({ queryKey: queryKeys.pricingRules, queryFn: usageApi.pricingRules })
}

export function useUpdateBudgetMutation(projectId: MaybeRefOrGetter<string>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateBudgetRequest) => usageApi.updateBudget(toValue(projectId), request),
    onSuccess: async (budget) => {
      queryClient.setQueryData(queryKeys.budget(budget.projectId), budget)
      await queryClient.invalidateQueries({ queryKey: queryKeys.costs(budget.projectId) })
    },
  })
}
