import type { ApproveWorkflowRequest, WorkflowResponse } from '@/api/types'

export interface ReviewTextSegment {
  text: string
  highlighted: boolean
}

export function reviewTextSegments(draft: string, evidence: string | null | undefined): ReviewTextSegment[] {
  const needle = evidence?.trim() ?? ''
  if (!needle) return [{ text: draft, highlighted: false }]
  const start = draft.indexOf(needle)
  if (start < 0) return [{ text: draft, highlighted: false }]
  const segments: ReviewTextSegment[] = []
  if (start > 0) segments.push({ text: draft.slice(0, start), highlighted: false })
  segments.push({ text: draft.slice(start, start + needle.length), highlighted: true })
  if (start + needle.length < draft.length) {
    segments.push({ text: draft.slice(start + needle.length), highlighted: false })
  }
  return segments
}

export function createApprovalRequest(workflow: WorkflowResponse): ApproveWorkflowRequest {
  return {
    expectedVersion: workflow.version,
    changeSummary: null,
    acceptedFactIndexes: workflow.candidateFacts
      .filter((fact) => fact.status === 'ACCEPTED')
      .map((fact) => fact.candidateIndex),
    characterStateChanges: [],
    itemChanges: [],
    timelineEvents: [],
    knowledgeChanges: [],
  }
}

export function approvalValidationErrors(
  workflow: WorkflowResponse,
  request: ApproveWorkflowRequest,
  projectCharacterIds: ReadonlySet<string>,
): string[] {
  const errors: string[] = []
  if (workflow.status !== 'WAITING_APPROVAL') errors.push('工作流当前不在等待审批状态。')
  if (!workflow.contextPacket || workflow.contextPacket.stale) errors.push('Context Packet 已失效，不能提交。')
  if (workflow.reviewIssues.some((issue) => issue.blocking && !issue.resolved)) errors.push('仍有未解决的 BLOCKER。')
  if ((request.changeSummary?.length ?? 0) > 500) errors.push('提交说明不能超过 500 字符。')

  const availableIndexes = new Set(workflow.candidateFacts.map((fact) => fact.candidateIndex))
  for (const index of request.acceptedFactIndexes) {
    const fact = workflow.candidateFacts.find((candidate) => candidate.candidateIndex === index)
    if (!availableIndexes.has(index)) errors.push(`候选事实 #${index} 不属于当前工作流。`)
    else if (!fact?.evidence.trim()) errors.push(`候选事实 #${index} 缺少正文证据。`)
  }
  if (new Set(request.acceptedFactIndexes).size !== request.acceptedFactIndexes.length) {
    errors.push('候选事实不能重复选择。')
  }

  request.characterStateChanges.forEach((change, index) => {
    if (!projectCharacterIds.has(change.characterId)) errors.push(`人物状态变更 ${index + 1} 未选择项目人物。`)
    if (!change.evidence.trim()) errors.push(`人物状态变更 ${index + 1} 缺少正文证据。`)
  })
  request.itemChanges.forEach((change, index) => {
    if (!change.itemKey.trim() || !change.itemName.trim()) errors.push(`道具变更 ${index + 1} 缺少 Key 或名称。`)
    if (change.fromOwnerCharacterId && !projectCharacterIds.has(change.fromOwnerCharacterId)) errors.push(`道具变更 ${index + 1} 的原持有人不属于当前项目。`)
    if (change.toOwnerCharacterId && !projectCharacterIds.has(change.toOwnerCharacterId)) errors.push(`道具变更 ${index + 1} 的最终持有人不属于当前项目。`)
    if (!change.evidence.trim()) errors.push(`道具变更 ${index + 1} 缺少正文证据。`)
  })
  request.timelineEvents.forEach((event, index) => {
    if (![...event.participantIds, ...event.knownByIds].every((id) => projectCharacterIds.has(id))) errors.push(`时间线事件 ${index + 1} 引用了非项目人物。`)
    if (!event.action.trim() || !event.result.trim()) errors.push(`时间线事件 ${index + 1} 缺少行动或结果。`)
    if (event.importance < 0 || event.importance > 1) errors.push(`时间线事件 ${index + 1} 的重要度必须在 0—1。`)
    if (!event.evidence.trim()) errors.push(`时间线事件 ${index + 1} 缺少正文证据。`)
  })
  request.knowledgeChanges.forEach((change, index) => {
    if (!projectCharacterIds.has(change.characterId)) errors.push(`知识变更 ${index + 1} 未选择项目人物。`)
    if (!change.factKey.trim() || !change.content.trim()) errors.push(`知识变更 ${index + 1} 缺少事实 Key 或内容。`)
    if (!change.evidence.trim()) errors.push(`知识变更 ${index + 1} 缺少正文证据。`)
  })
  return errors
}
