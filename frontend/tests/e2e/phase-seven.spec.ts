import { expect, test, type Page, type Route } from '@playwright/test'

const projectId = '22222222-2222-4222-8222-222222222222'
const chapterId = '77777777-7777-4777-8777-777777777777'
const runId = '99999999-9999-4999-8999-999999999999'
const characterId = '66666666-6666-4666-8666-666666666666'
const now = '2026-08-03T00:00:00Z'
const draft = '潮声越过旧码头。\n林雾推开灯塔的门，发现守塔人已经离开。'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function issue(blocking: boolean) {
  return {
    id: 'issue-1', source: 'LLM', category: 'KNOWLEDGE_BOUNDARY', severity: blocking ? 'BLOCKER' : 'MEDIUM',
    message: blocking ? '视角人物提前知道守塔人的去向' : '灯塔门的状态缺少铺垫',
    evidence: '林雾推开灯塔的门', historicalEvidence: '上一章中灯塔门处于关闭状态',
    suggestion: '保留进入灯塔的动作，但不要直接说明守塔人的去向。', blocking, resolved: false, createdAt: now,
  }
}

function workflow(status: 'WAITING_APPROVAL' | 'TEXT_READY' | 'COMPLETED', options: { blocking?: boolean; revisedDraft?: string } = {}) {
  const currentDraft = options.revisedDraft ?? draft
  return {
    id: runId, projectId, chapterId, viewpointCharacterId: characterId, status, draftContent: currentDraft,
    plan: { chapterTitle: '灯塔之夜' }, extraction: { summary: '林雾进入灯塔' }, review: {},
    cancelRequested: false, recoveryCount: 0, revisionCount: options.revisedDraft ? 1 : 0,
    committedVersionNo: status === 'COMPLETED' ? 2 : null,
    approvedBy: status === 'COMPLETED' ? 'user-1' : null, approvedAt: status === 'COMPLETED' ? now : null,
    failureCode: null, failureMessage: null, heartbeatAt: now, startedAt: now,
    finishedAt: status === 'COMPLETED' ? now : null, version: status === 'COMPLETED' ? 17 : options.revisedDraft ? 16 : 14,
    createdAt: now, updatedAt: now,
    contextPacket: { id: 'packet-1', tokenEstimate: 8000, estimatedCost: 0, expiresAt: '2026-08-04T00:00:00Z', stale: false, createdAt: now },
    steps: [],
    candidateFacts: [
      { id: 'fact-1', candidateIndex: 0, factKey: 'lighthouse-entered', content: '林雾进入了旧灯塔', evidence: '林雾推开灯塔的门', paragraphKey: 'p-1', status: status === 'COMPLETED' ? 'ACCEPTED' : 'CANDIDATE', createdAt: now },
      { id: 'fact-2', candidateIndex: 1, factKey: 'keeper-left', content: '守塔人已经离开', evidence: '发现守塔人已经离开', paragraphKey: 'p-1', status: status === 'COMPLETED' ? 'REJECTED' : 'CANDIDATE', createdAt: now },
    ],
    reviewIssues: status === 'WAITING_APPROVAL' && !options.revisedDraft ? [issue(Boolean(options.blocking))] : [],
  }
}

function sseStep() {
  const event = { eventId: 1, runId, type: 'workflow.step', step: 'REVIEWING', timestamp: now, payload: { status: 'REVIEWING' } }
  return `id: 1\nevent: workflow.step\ndata: ${JSON.stringify(event)}\n\n`
}

async function mockPhaseSevenApi(page: Page, blocking = false) {
  const approvalRequests: unknown[] = []
  const revisionRequests: unknown[] = []
  const eventRequests: string[] = []
  let revisedDraft = ''
  let completed = false
  const project = { id: projectId, name: '雾港来信', genre: '现代幻想', description: null, authorIntent: '保持克制', currentFocus: null, archived: false, version: 0, createdAt: now, updatedAt: now }
  const chapter = { id: chapterId, projectId, outlineNodeId: null, chapterNo: 1, title: '潮声', outline: '进入灯塔', status: completed ? 'CONFIRMED' : 'WAITING_APPROVAL', currentVersionNo: completed ? 2 : 1, version: completed ? 2 : 1, createdAt: now, updatedAt: now, currentVersion: { id: completed ? 'v2' : 'v1', chapterId, versionNo: completed ? 2 : 1, title: '潮声', content: completed ? draft : '', summary: null, changeSummary: null, restoredFromVersionNo: null, createdAt: now } }
  const character = { id: characterId, projectId, name: '林雾', aliases: null, role: '主角', description: null, personality: null, background: null, goals: null, appearance: null, notes: null, archived: false, version: 0, createdAt: now, updatedAt: now, state: { id: 'state-1', projectId, characterId, lifeStatus: 'ALIVE', currentLocation: '旧码头', physicalCondition: null, emotionalState: null, abilities: null, inventoryNotes: null, notes: null, version: 3, createdAt: now, updatedAt: now } }

  await page.route('**/api/**', async (route) => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    if (path === '/api/auth/login') return json(route, { accessToken: 'phase-seven-token', tokenType: 'Bearer', expiresAt: '2026-08-03T01:00:00Z', user: { id: 'user-1', username: 'author', email: 'author@example.com', createdAt: now } })
    if (path === '/api/me') return json(route, { id: 'user-1', username: 'author', email: 'author@example.com', createdAt: now })
    if (path === '/api/projects') return json(route, [project])
    if (path === `/api/projects/${projectId}`) return json(route, project)
    if (path === `/api/projects/${projectId}/chapters`) return json(route, [chapter])
    if (path === `/api/chapters/${chapterId}`) return json(route, chapter)
    if (path === `/api/chapters/${chapterId}/versions`) return json(route, [chapter.currentVersion])
    if (path === `/api/projects/${projectId}/characters`) return json(route, [character])
    if (path === `/api/projects/${projectId}/budget`) return json(route, { projectId, taskTokenLimit: 40000, userDailyCostLimit: 10, projectCostLimit: 100, writerOutputTokenLimit: 12000, plannerReasoningTokenLimit: 6000, version: 0 })
    if (path === `/api/workflows/${runId}/events`) {
      eventRequests.push(request.url())
      return route.fulfill({ status: 200, contentType: 'text/event-stream', body: sseStep() })
    }
    if (path === `/api/workflows/${runId}/approve` && method === 'POST') {
      approvalRequests.push(request.postDataJSON())
      completed = true
      return json(route, workflow('COMPLETED'))
    }
    if (path === `/api/workflows/${runId}/request-revision` && method === 'POST') {
      const body = request.postDataJSON() as { revisedDraft: string }
      revisionRequests.push(body)
      revisedDraft = body.revisedDraft
      return json(route, workflow('TEXT_READY', { revisedDraft }))
    }
    if (path === `/api/workflows/${runId}`) {
      if (completed) return json(route, workflow('COMPLETED'))
      if (revisedDraft) return json(route, workflow('WAITING_APPROVAL', { revisedDraft }))
      return json(route, workflow('WAITING_APPROVAL', { blocking }))
    }
    return json(route, { title: 'Not Found', status: 404 }, 404)
  })
  return { approvalRequests, revisionRequests, eventRequests }
}

async function loginToWorkflow(page: Page) {
  await page.goto(`/projects/${projectId}/chapters/${chapterId}/workflows/${runId}`)
  await page.getByLabel('邮箱或用户名').fill('author')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(new RegExp(`/workflows/${runId}$`))
}

test('reviews evidence, accepts a candidate fact, and performs an atomic approval', async ({ page }) => {
  const calls = await mockPhaseSevenApi(page)
  await loginToWorkflow(page)

  await expect(page.getByText('当前正文证据')).toBeVisible()
  await page.getByRole('button', { name: '跳转正文证据' }).click()
  await expect(page.locator('.review-mark-active')).toHaveText('林雾推开灯塔的门')
  await page.locator('.candidate-fact-card').filter({ hasText: '林雾进入了旧灯塔' }).getByRole('checkbox').check()
  await page.getByLabel('提交说明').fill('确认第一章')
  await page.getByRole('button', { name: '确认并原子提交' }).click()

  await expect(page.getByRole('heading', { name: '章节已原子提交' })).toBeVisible()
  await expect(page.getByText('正式版本 v2')).toBeVisible()
  expect(calls.approvalRequests).toEqual([{
    expectedVersion: 14,
    changeSummary: '确认第一章',
    acceptedFactIndexes: [0],
    characterStateChanges: [], itemChanges: [], timelineEvents: [], knowledgeChanges: [],
  }])
  expect(calls.eventRequests).toEqual([])
})

test('BLOCKER prevents approval until the full draft is revised and re-extracted', async ({ page }) => {
  const calls = await mockPhaseSevenApi(page, true)
  await loginToWorkflow(page)

  await expect(page.getByText('阻止提交')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认并原子提交' })).toBeDisabled()
  await page.getByRole('button', { name: '根据建议修订' }).click()
  const textarea = page.getByLabel('修订后的完整正文')
  await textarea.fill('潮声越过旧码头。\n林雾推开灯塔的门，但没有发现守塔人的去向。')
  await page.getByRole('button', { name: '提交并重新提取' }).click()

  await expect(page.getByText('本次审查没有返回问题。')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认并原子提交' })).toBeEnabled()
  expect(calls.revisionRequests).toEqual([{ revisedDraft: '潮声越过旧码头。\n林雾推开灯塔的门，但没有发现守塔人的去向。' }])
  expect(calls.eventRequests).toHaveLength(1)
})
