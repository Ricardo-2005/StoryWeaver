import { useQuery } from '@tanstack/vue-query'

import { authApi } from '@/api/endpoints/auth'
import { getAccessToken } from '@/api/tokenMemory'
import { queryKeys } from '@/queries/keys'

export function useCurrentUserQuery() {
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: authApi.me,
    enabled: () => Boolean(getAccessToken()),
    staleTime: 5 * 60_000,
  })
}
