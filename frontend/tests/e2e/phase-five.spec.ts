import { expect, test, type Page, type Route } from '@playwright/test'

const projectId = '22222222-2222-2222-2222-222222222222'
const chapterId = '77777777-7777-4777-8777-777777777777'
const runId = '99999999-9999-4999-8999-999999999999'
const characterId = '66666666-6666-4666-8666-666666666666'
const now = '2026-08-03T00:00:00Z'
function json(route: Route, body: unknown, status = 200) { return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) }) }

async function mockPhaseFiveApi(page: Page, blockedPreflight = false, actualCost = 0) {
  const eventRequests: string[] = []
  const workflowStarts: string[] = []
  const project = { id: projectId, name: '雾港来信', genre: '现代幻想', description: null, authorIntent: blockedPreflight ? null : '保持克制与悬疑', currentFocus: null, archived: false, version: 0, createdAt: now, updatedAt: now }
  const chapter = { id: chapterId, projectId, outlineNodeId: null, chapterNo: 1, title: '潮声', outline: '林雾进入灯塔。', status: 'DRAFT', currentVersionNo: 1, version: 1, createdAt: now, updatedAt: now, currentVersion: { id: 'v1', chapterId, versionNo: 1, title: '潮声', content: '潮雾覆盖旧码头。', summary: null, changeSummary: null, restoredFromVersionNo: null, createdAt: now } }
  const character = { id: characterId, projectId, name: '林雾', aliases: null, role: '主角', description: null, personality: null, background: null, goals: null, appearance: null, notes: null, archived: false, version: 0, createdAt: now, updatedAt: now, state: { id: 's1', projectId, characterId, lifeStatus: 'ALIVE', currentLocation: '雾港', physicalCondition: null, emotionalState: null, abilities: null, inventoryNotes: null, notes: null, version: 0, createdAt: now, updatedAt: now } }
  const models = [
    { agent: 'PLANNER', model: 'deepseek-v4-pro', thinking: true, reasoningEffort: 'high', temperature: null, jsonOutput: true, stream: false, maxOutputTokens: 6000, maxAttempts: 2, ignoredParameters: [] },
    { agent: 'WRITER', model: 'deepseek-v4-pro', thinking: false, reasoningEffort: null, temperature: 0.78, jsonOutput: false, stream: true, maxOutputTokens: 12000, maxAttempts: 1, ignoredParameters: [] },
    { agent: 'EXTRACTOR', model: 'deepseek-v4-flash', thinking: false, reasoningEffort: null, temperature: 0.1, jsonOutput: true, stream: false, maxOutputTokens: 7000, maxAttempts: 3, ignoredParameters: [] },
    { agent: 'REVIEWER', model: 'deepseek-v4-pro', thinking: true, reasoningEffort: 'high', temperature: null, jsonOutput: true, stream: false, maxOutputTokens: 8000, maxAttempts: 2, ignoredParameters: [] },
  ]
  const workflow = {
    id: runId, projectId, chapterId, viewpointCharacterId: characterId, status: 'BLOCKED', draftContent: null,
    plan: { chapterTitle: '灯塔之夜', chapterGoal: '发现港城异变', viewpointCharacterId: characterId, scenes: [{ title: '进入灯塔', goal: '寻找守塔人', summary: '林雾沿旋梯上行。', mustInclude: ['潮声'], mustAvoid: ['解释全部谜底'] }], mustInclude: ['灯塔'], mustAvoid: ['提前揭晓'], exitHook: '灯光突然熄灭' },
    extraction: {}, review: {}, cancelRequested: false, recoveryCount: 0, revisionCount: 0, committedVersionNo: null, approvedBy: null, approvedAt: null, failureCode: 'context_packet_stale', failureMessage: 'Context Packet expired before this step', heartbeatAt: now, startedAt: now, finishedAt: now, version: 5, createdAt: now, updatedAt: now,
    contextPacket: { id: 'context-1', tokenEstimate: 18420, estimatedCost: 0, expiresAt: '2026-08-03T00:01:00Z', stale: true, createdAt: now },
    steps: [{ id: 'step-1', stepName: 'PREFLIGHT', status: 'COMPLETED', attempt: 1, errorCode: null, errorMessage: null, startedAt: now, finishedAt: now }, { id: 'step-2', stepName: 'CONTEXT', status: 'COMPLETED', attempt: 1, errorCode: null, errorMessage: null, startedAt: now, finishedAt: now }, { id: 'step-3', stepName: 'PLANNING', status: 'COMPLETED', attempt: 1, errorCode: null, errorMessage: null, startedAt: now, finishedAt: now }], candidateFacts: [], reviewIssues: [],
  }

  await page.route('**/api/**', async (route) => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    if (path.endsWith('/events')) eventRequests.push(path)
    if (path === '/api/auth/login') return json(route, { accessToken: 'phase-five-token', tokenType: 'Bearer', expiresAt: '2026-08-03T01:00:00Z', user: { id: 'u', username: 'author', email: 'author@example.com', createdAt: now } })
    if (path === '/api/me') return json(route, { id: 'u', username: 'author', email: 'author@example.com', createdAt: now })
    if (path === '/api/projects' && method === 'GET') return json(route, [project])
    if (path === `/api/projects/${projectId}`) return json(route, project)
    if (path === `/api/projects/${projectId}/chapters`) return json(route, [chapter])
    if (path === `/api/chapters/${chapterId}`) return json(route, chapter)
    if (path === `/api/chapters/${chapterId}/versions`) return json(route, [chapter.currentVersion])
    if (path === `/api/projects/${projectId}/characters`) return json(route, [character])
    if (path === `/api/projects/${projectId}/skills/compose`) return json(route, { resolved: true, effectiveRules: { tone: { key: 'tone', value: '克制', scope: 'PROJECT', skillId: 'skill-1', skillName: '项目规则' } }, conflicts: [] })
    if (path === `/api/projects/${projectId}/budget`) return json(route, { projectId, taskTokenLimit: 40000, userDailyCostLimit: 10, projectCostLimit: 100, writerOutputTokenLimit: 12000, plannerReasoningTokenLimit: 6000, version: 0 })
    if (path === `/api/projects/${projectId}/costs`) return json(route, { projectId, estimatedCost: actualCost, actualCost, unpricedRequests: 0, requests: actualCost ? 10 : 0 })
    if (path === '/api/ai/model-config') return json(route, models)
    if (path === `/api/chapters/${chapterId}/workflows` && method === 'POST') {
      workflowStarts.push(path)
      expect(request.headers()['idempotency-key']).toMatch(/^workflow_[0-9a-f-]{36}$/)
      return json(route, workflow, 202)
    }
    if (path === `/api/workflows/${runId}`) return json(route, workflow)
    return json(route, { title: 'Not Found', status: 404 }, 404)
  })
  return { eventRequests, workflowStarts }
}

async function enterEditor(page: Page) {
  await page.goto(`/projects/${projectId}/chapters/${chapterId}`)
  await page.getByLabel('邮箱或用户名').fill('author')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()
  await page.getByRole('button', { name: '开始工作流' }).click()
}

test('preflight BLOCKER disables workflow start', async ({ page }) => {
  const calls = await mockPhaseFiveApi(page, true)
  await enterEditor(page)
  await expect(page.getByText('项目缺少作者意图')).toBeVisible()
  await expect(page.getByRole('button', { name: '启动后端工作流' })).toBeDisabled()
  expect(calls.workflowStarts).toEqual([])
})

test('exhausted project cost budget disables workflow start', async ({ page }) => {
  const calls = await mockPhaseFiveApi(page, false, 100)
  await enterEditor(page)
  await expect(page.getByText('项目费用预算')).toBeVisible()
  await expect(page.getByText('100.000000 / 100.000000')).toBeVisible()
  await expect(page.getByRole('button', { name: '启动后端工作流' })).toBeDisabled()
  expect(calls.workflowStarts).toEqual([])
})

test('shows real workflow context, tokens, plan, stepper, and stale handling without SSE', async ({ page }) => {
  const calls = await mockPhaseFiveApi(page)
  await enterEditor(page)
  await expect(page.locator('.preflight-summary').getByText('0', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '启动后端工作流' }).click()

  await expect(page).toHaveURL(new RegExp(`/workflows/${runId}$`))
  await expect(page.getByRole('heading', { name: '已阻塞' })).toBeVisible()
  await expect(page.getByText('Context Packet 已过期')).toBeVisible()
  await expect(page.getByText('18,420', { exact: true })).toBeVisible()
  await expect(page.getByText('来源明细未由 API 返回').first()).toBeVisible()
  await expect(page.getByText('Skill 硬规则')).toBeVisible()
  await expect(page.getByText('锁定')).toBeVisible()
  await expect(page.getByRole('heading', { name: '灯塔之夜' })).toBeVisible()
  await expect(page.getByText('写前预检')).toBeVisible()
  await expect(page.getByRole('link', { name: '返回并重新预检' })).toBeVisible()
  expect(calls.workflowStarts).toHaveLength(1)
  expect(calls.eventRequests).toEqual([])
})
