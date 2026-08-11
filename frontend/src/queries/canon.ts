import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { canonApi } from '@/api/endpoints/canon'
import type { CreateAssetRequest, UpdateAssetRequest } from '@/api/types'
import { queryKeys } from '@/queries/keys'

export function useCanonAssetsQuery(projectId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.assets(toValue(projectId))),
    queryFn: () => canonApi.list(toValue(projectId)),
    enabled: () => toValue(projectId).length > 0,
  })
}

export function useCreateAssetMutation(projectId: MaybeRefOrGetter<string>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateAssetRequest) => canonApi.create(toValue(projectId), request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.assets(toValue(projectId)) })
    },
  })
}

export function useUpdateAssetMutation(projectId: MaybeRefOrGetter<string>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ assetId, request }: { assetId: string; request: UpdateAssetRequest }) =>
      canonApi.update(assetId, request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.assets(toValue(projectId)) })
    },
  })
}

export function useTransitionAssetMutation(
  projectId: MaybeRefOrGetter<string>,
  transition: 'confirm' | 'deprecate',
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ assetId, expectedVersion }: { assetId: string; expectedVersion: number }) =>
      canonApi[transition](assetId, { expectedVersion }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.assets(toValue(projectId)) })
    },
  })
}
