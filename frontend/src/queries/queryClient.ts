import { QueryClient } from '@tanstack/vue-query'

import { HttpProblemError } from '@/api/errors'

function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= 2) {
    return false
  }

  if (error instanceof HttpProblemError) {
    return error.problem.status >= 500
  }

  return failureCount < 1
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: shouldRetry,
      retryDelay: (attempt) => Math.min(1_000 * 2 ** attempt, 8_000),
    },
    mutations: {
      retry: false,
    },
  },
})
