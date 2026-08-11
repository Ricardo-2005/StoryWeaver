import { describe, expect, it } from 'vitest'

import type { WorkflowResponse } from '@/api/types'
import { approvalValidationErrors, createApprovalRequest, reviewTextSegments } from '@/features/workflows/approval'

const now = '2026-08-03T00:00:00Z'

function workflow(overrides: Partial<WorkflowResponse> = {}): WorkflowResponse {
  return {
    id: 'run-1', projectId: 'project-1', chapterId: 'chapter-1', viewpointCharacterId: 'character-1',
    status: 'WAITING_APPROVAL', draftContent: '潮声越过码头。\n林雾推开灯塔的门。', plan: {}, extraction: {}, review: {},
    cancelRequested: false, recoveryCount: 0, revisionCount: 0, committedVersionNo: null,
    approvedBy: null, approvedAt: null, failureCode: null, failureMessage: null, heartbeatAt: now,
    startedAt: now, finishedAt: null, version: 14, createdAt: now, updatedAt: now,
    contextPacket: { id: 'packet-1', tokenEstimate: 100, estimatedCost: 0, expiresAt: '2026-08-04T00:00:00Z', stale: false, createdAt: now },
    steps: [],
    candidateFacts: [{ id: 'fact-1', candidateIndex: 0, factKey: 'fog', content: '林雾进入灯塔', evidence: '林雾推开灯塔的门。', paragraphKey: 'p-1', status: 'CANDIDATE', createdAt: now }],
    reviewIssues: [],
    ...overrides,
  }
}

describe('Phase 7 approval model', () => {
  it('creates a complete empty atomic proposal using the current workflow version', () => {
    const request = createApprovalRequest(workflow())
    expect(request).toEqual({
      expectedVersion: 14,
      changeSummary: null,
      acceptedFactIndexes: [],
      characterStateChanges: [],
      itemChanges: [],
      timelineEvents: [],
      knowledgeChanges: [],
    })
  })

  it('creates a non-invasive ReviewMark only around exact evidence', () => {
    expect(reviewTextSegments('前文。证据片段。后文。', '证据片段。')).toEqual([
      { text: '前文。', highlighted: false },
      { text: '证据片段。', highlighted: true },
      { text: '后文。', highlighted: false },
    ])
    expect(reviewTextSegments('正文', '不存在')).toEqual([{ text: '正文', highlighted: false }])
  })

  it('blocks stale context, unresolved blockers, and accepted facts without evidence', () => {
    const current = workflow({
      contextPacket: { id: 'packet-1', tokenEstimate: 100, estimatedCost: 0, expiresAt: now, stale: true, createdAt: now },
      candidateFacts: [{ id: 'fact-1', candidateIndex: 0, factKey: 'fog', content: '事实', evidence: '', paragraphKey: 'p-1', status: 'CANDIDATE', createdAt: now }],
      reviewIssues: [{ id: 'issue-1', source: 'JAVA', category: 'TIMELINE', severity: 'BLOCKER', message: '冲突', evidence: '证据', historicalEvidence: null, suggestion: '修订', blocking: true, resolved: false, createdAt: now }],
    })
    const request = createApprovalRequest(current)
    request.acceptedFactIndexes = [0]
    const errors = approvalValidationErrors(current, request, new Set(['character-1']))
    expect(errors).toContain('Context Packet 已失效，不能提交。')
    expect(errors).toContain('仍有未解决的 BLOCKER。')
    expect(errors).toContain('候选事实 #0 缺少正文证据。')
  })

  it('validates evidence and project references for optional atomic changes', () => {
    const current = workflow()
    const request = createApprovalRequest(current)
    request.itemChanges.push({ itemKey: 'sword', itemName: '剑', fromOwnerCharacterId: null, toOwnerCharacterId: 'outsider', status: 'ACTIVE', evidence: '' })
    request.timelineEvents.push({ participantIds: ['outsider'], knownByIds: [], location: null, storyTime: null, action: '', result: '', importance: 2, evidence: '' })
    const errors = approvalValidationErrors(current, request, new Set(['character-1']))
    expect(errors).toContain('道具变更 1 的最终持有人不属于当前项目。')
    expect(errors).toContain('道具变更 1 缺少正文证据。')
    expect(errors).toContain('时间线事件 1 引用了非项目人物。')
    expect(errors).toContain('时间线事件 1 的重要度必须在 0—1。')
  })
})
