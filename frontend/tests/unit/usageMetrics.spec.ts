import { describe, expect, it } from 'vitest'

import type { UsageResponse } from '@/api/types'
import { agentUsage, currencies, dailyUsage, formatCost, formatDuration, modelUsage, usageOverview } from '@/features/usage/metrics'

function usage(overrides: Partial<UsageResponse>): UsageResponse {
  return {
    id: 'usage-1', projectId: 'project-1', agent: 'WRITER', model: 'deepseek-v4-pro', requestId: 'request-1',
    status: 'SUCCEEDED', promptTokens: 1_000, completionTokens: 500, reasoningTokens: 0,
    promptCacheHitTokens: 600, promptCacheMissTokens: 200, attempts: 1, durationMillis: 1_000,
    requestedAt: '2026-08-01T10:00:00Z', pricingRuleId: 'rule-1', pricingRuleVersion: 'v1',
    estimatedCost: 0.01, actualCost: 0.01, currency: 'USD',
    ...overrides,
  }
}

describe('Phase 8 usage metrics', () => {
  const records = [
    usage({ id: 'u1' }),
    usage({ id: 'u2', agent: 'PLANNER', model: 'deepseek-reasoner', promptTokens: 2_000, completionTokens: 100, reasoningTokens: 900, promptCacheHitTokens: 0, promptCacheMissTokens: 800, durationMillis: 4_000, requestedAt: '2026-08-02T10:00:00Z', actualCost: 0.02 }),
    usage({ id: 'u3', status: 'FAILED', promptTokens: 0, completionTokens: 0, promptCacheHitTokens: 0, promptCacheMissTokens: 0, durationMillis: 8_000, requestedAt: '2026-08-02T11:00:00Z', pricingRuleId: null, pricingRuleVersion: null, estimatedCost: null, actualCost: null, currency: null }),
  ]

  it('aggregates only backend values and keeps unpriced requests explicit', () => {
    const result = usageOverview(records, { projectId: 'project-1', estimatedCost: 0.03, actualCost: 0.03, unpricedRequests: 1, requests: 3 })
    expect(result.totalTokens).toBe(4_500)
    expect(result.cacheHitRate).toBe(600 / 1_600)
    expect(result.averageDurationMillis).toBeCloseTo(4_333.333)
    expect(result.p95DurationMillis).toBe(8_000)
    expect(result.actualCost).toBe(0.03)
    expect(result.unpricedRequests).toBe(1)
  })

  it('groups data by UTC day, agent, and model', () => {
    expect(dailyUsage(records).map((bucket) => [bucket.key, bucket.requests])).toEqual([['2026-08-01', 1], ['2026-08-02', 2]])
    expect(agentUsage(records).map((bucket) => bucket.key)).toEqual(['PLANNER', 'WRITER'])
    expect(modelUsage(records).find((bucket) => bucket.key === 'deepseek-v4-pro')?.requests).toBe(2)
  })

  it('does not invent currency conversion and formats durations', () => {
    expect(currencies(records)).toEqual(['USD'])
    expect(formatCost(0.03, 'USD')).toContain('$')
    expect(formatCost(0.03, undefined)).toBe('0.030000')
    expect(formatDuration(850)).toBe('850 ms')
    expect(formatDuration(4_000)).toBe('4.00 s')
  })
})
