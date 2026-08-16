import { describe, expect, it } from 'vitest'

import type { PreflightInput } from '@/features/workflows/preflight'
import { buildPreflightChecks, projectedWorkflowTokens } from '@/features/workflows/preflight'

const now = '2026-08-03T00:00:00Z'
function input(): PreflightInput {
  return {
    project: { id: 'p', name: '雾港', genre: 'MYSTERY', customGenre: null, targetAudience: 'GENERAL', narrativePerspective: 'THIRD_PERSON', lengthType: 'LONG_NOVEL', premise: '一封来信揭开雾港失踪案。', description: null, authorIntent: '保持克制', currentFocus: null, worldRules: [], targetWordCount: null, chapterWordTarget: null, archived: false, version: 0, createdAt: now, updatedAt: now },
    chapter: { id: 'c2', projectId: 'p', outlineNodeId: null, chapterNo: 2, title: '第二章', outline: '进入灯塔', status: 'DRAFT', currentVersionNo: 0, version: 0, createdAt: now, updatedAt: now, currentVersion: null },
    chapters: [
      { id: 'c1', projectId: 'p', outlineNodeId: null, chapterNo: 1, title: '第一章', outline: '抵达', status: 'DRAFT', currentVersionNo: 1, version: 1, createdAt: now, updatedAt: now, currentVersion: null },
    ],
    characters: [{ id: 'v', projectId: 'p', name: '林雾', aliases: null, role: null, description: null, personality: null, background: null, goals: null, appearance: null, notes: null, archived: false, importance: 'PROTAGONIST', lifecycleStatus: 'ACTIVE', mergedInto: null, retrievalEligible: true, version: 0, createdAt: now, updatedAt: now, state: { id: 's', projectId: 'p', characterId: 'v', lifeStatus: 'ALIVE', currentLocation: null, physicalCondition: null, emotionalState: null, abilities: null, inventoryNotes: null, notes: null, version: 0, createdAt: now, updatedAt: now } }],
    viewpointCharacterId: 'v',
    skills: { resolved: true, effectiveRules: {}, conflicts: [] },
    budget: { projectId: 'p', taskTokenLimit: 40_000, userDailyCostLimit: 10, projectCostLimit: 100, writerOutputTokenLimit: 12_000, plannerReasoningTokenLimit: 6_000, version: 0 },
    costs: { projectId: 'p', estimatedCost: 1, actualCost: 0.8, unpricedRequests: 0, requests: 4 },
    models: [
      { agent: 'PLANNER', model: 'p', thinking: true, reasoningEffort: 'high', temperature: null, jsonOutput: true, stream: false, maxOutputTokens: 6_000, maxAttempts: 2, ignoredParameters: [] },
      { agent: 'WRITER', model: 'w', thinking: false, reasoningEffort: null, temperature: 0.78, jsonOutput: false, stream: true, maxOutputTokens: 12_000, maxAttempts: 1, ignoredParameters: [] },
      { agent: 'EXTRACTOR', model: 'e', thinking: false, reasoningEffort: null, temperature: 0.1, jsonOutput: true, stream: false, maxOutputTokens: 7_000, maxAttempts: 3, ignoredParameters: [] },
      { agent: 'REVIEWER', model: 'r', thinking: true, reasoningEffort: 'high', temperature: null, jsonOutput: true, stream: false, maxOutputTokens: 8_000, maxAttempts: 2, ignoredParameters: [] },
    ],
    hasUnsavedDraft: false,
  }
}

describe('workflow preflight', () => {
  it('matches the backend projected token contract', () => {
    const value = input()
    expect(projectedWorkflowTokens(value.models)).toBe(33_000)
    expect(buildPreflightChecks(value).filter((check) => check.status === 'blocker')).toEqual([])
  })

  it('blocks missing prerequisites and local unsaved content', () => {
    const value = input()
    value.project.authorIntent = null
    value.skills = { resolved: false, effectiveRules: {}, conflicts: [{ scope: 'PROJECT', key: 'tone', values: ['a', 'b'], skillIds: ['1', '2'] }] }
    value.hasUnsavedDraft = true

    expect(buildPreflightChecks(value).filter((check) => check.status === 'blocker').map((check) => check.code)).toEqual([
      'author_intent', 'skills', 'local_draft',
    ])
  })

  it('blocks starting when the backend project cost summary reaches the configured limit', () => {
    const value = input()
    value.costs.actualCost = 100
    expect(buildPreflightChecks(value).find((check) => check.code === 'project_cost_budget')?.status).toBe('blocker')
  })
})
