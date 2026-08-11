import type { QueryClient } from '@tanstack/vue-query'

import { clearAccessToken, setAccessToken } from '@/api/tokenMemory'
import type { AuthResponse } from '@/api/types'
import { queryKeys } from '@/queries/keys'

export function establishSession(response: AuthResponse, queryClient: QueryClient): void {
  setAccessToken(response.accessToken)
  queryClient.setQueryData(queryKeys.me, response.user)
}

export function clearSession(queryClient: QueryClient): void {
  clearAccessToken()
  queryClient.clear()
}
