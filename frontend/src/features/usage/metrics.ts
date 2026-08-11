import type { CostSummaryResponse, UsageResponse } from '@/api/types'

export interface UsageOverview {
  requests: number
  succeeded: number
  failed: number
  promptTokens: number
  completionTokens: number
  reasoningTokens: number
  cacheHitTokens: number
  cacheMissTokens: number
  totalTokens: number
  cacheHitRate: number | null
  averageDurationMillis: number
  p95DurationMillis: number
  actualCost: number
  estimatedCost: number
  unpricedRequests: number
}

export interface UsageBucket {
  key: string
  requests: number
  promptTokens: number
  completionTokens: number
  reasoningTokens: number
  cacheHitTokens: number
  cacheMissTokens: number
  durationMillis: number
  actualCost: number
}

function sum(values: number[]): number {
  return values.reduce((total, value) => total + value, 0)
}

export function usageOverview(records: UsageResponse[], costs: CostSummaryResponse): UsageOverview {
  const durations = records.map((record) => record.durationMillis).sort((left, right) => left - right)
  const cacheHitTokens = sum(records.map((record) => record.promptCacheHitTokens))
  const cacheMissTokens = sum(records.map((record) => record.promptCacheMissTokens))
  const cacheTotal = cacheHitTokens + cacheMissTokens
  const p95Index = durations.length ? Math.min(durations.length - 1, Math.ceil(durations.length * 0.95) - 1) : 0
  return {
    requests: costs.requests,
    succeeded: records.filter((record) => record.status === 'SUCCEEDED').length,
    failed: records.filter((record) => record.status === 'FAILED').length,
    promptTokens: sum(records.map((record) => record.promptTokens)),
    completionTokens: sum(records.map((record) => record.completionTokens)),
    reasoningTokens: sum(records.map((record) => record.reasoningTokens)),
    cacheHitTokens,
    cacheMissTokens,
    totalTokens: sum(records.map((record) => record.promptTokens + record.completionTokens + record.reasoningTokens)),
    cacheHitRate: cacheTotal > 0 ? cacheHitTokens / cacheTotal : null,
    averageDurationMillis: records.length ? sum(durations) / records.length : 0,
    p95DurationMillis: durations[p95Index] ?? 0,
    actualCost: costs.actualCost,
    estimatedCost: costs.estimatedCost,
    unpricedRequests: costs.unpricedRequests,
  }
}

export function groupUsage(records: UsageResponse[], keyOf: (record: UsageResponse) => string): UsageBucket[] {
  const buckets = new Map<string, UsageBucket>()
  for (const record of records) {
    const key = keyOf(record)
    const bucket = buckets.get(key) ?? {
      key,
      requests: 0,
      promptTokens: 0,
      completionTokens: 0,
      reasoningTokens: 0,
      cacheHitTokens: 0,
      cacheMissTokens: 0,
      durationMillis: 0,
      actualCost: 0,
    }
    bucket.requests += 1
    bucket.promptTokens += record.promptTokens
    bucket.completionTokens += record.completionTokens
    bucket.reasoningTokens += record.reasoningTokens
    bucket.cacheHitTokens += record.promptCacheHitTokens
    bucket.cacheMissTokens += record.promptCacheMissTokens
    bucket.durationMillis += record.durationMillis
    bucket.actualCost += record.actualCost ?? 0
    buckets.set(key, bucket)
  }
  return [...buckets.values()]
}

export function dailyUsage(records: UsageResponse[]): UsageBucket[] {
  return groupUsage(records, (record) => record.requestedAt.slice(0, 10))
    .sort((left, right) => left.key.localeCompare(right.key))
}

export function agentUsage(records: UsageResponse[]): UsageBucket[] {
  return groupUsage(records, (record) => record.agent)
    .sort((left, right) => right.promptTokens + right.completionTokens - left.promptTokens - left.completionTokens)
}

export function modelUsage(records: UsageResponse[]): UsageBucket[] {
  return groupUsage(records, (record) => record.model)
    .sort((left, right) => right.requests - left.requests)
}

export function currencies(records: UsageResponse[]): string[] {
  return [...new Set(records.map((record) => record.currency).filter((value): value is string => Boolean(value)))].sort()
}

export function formatCost(value: number, currency: string | undefined): string {
  if (!currency) return value.toFixed(6)
  try {
    return new Intl.NumberFormat('zh-CN', { style: 'currency', currency, maximumFractionDigits: 6 }).format(value)
  } catch {
    return `${value.toFixed(6)} ${currency}`
  }
}

export function formatDuration(milliseconds: number): string {
  if (milliseconds < 1_000) return `${Math.round(milliseconds)} ms`
  return `${(milliseconds / 1_000).toFixed(milliseconds < 10_000 ? 2 : 1)} s`
}
