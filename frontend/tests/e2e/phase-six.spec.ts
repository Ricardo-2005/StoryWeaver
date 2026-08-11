import { expect, test, type Page, type Route } from '@playwright/test'

const projectId = '22222222-2222-4222-8222-222222222222'
const chapterId = '77777777-7777-4777-8777-777777777777'
const runId = '99999999-9999-4999-8999-999999999999'
const characterId = '66666666-6666-4666-8666-666666666666'
const now = '2026-08-03T00:00:00Z'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function workflow(status: 'WRITING' | 'WAITING_APPROVAL' | 'CANCELLED', draftContent: string) {
  return {
    id: runId, projectId, chapterId, viewpointCharacterId: characterId, status, draftContent,
    plan: {}, extraction: {}, review: {}, cancelRequested: status === 'CANCELLED', recoveryCount: 0,
    revisionCount: 0, committedVersionNo: null, approvedBy: null, approvedAt: null,
    failureCode: null, failureMessage: null, heartbeatAt: now, startedAt: now,
    finishedAt: status === 'WRITING' ? null : now, version: 3, createdAt: now, updatedAt: now,
    contextPacket: null, steps: [], candidateFacts: [], reviewIssues: [],
  }
}

function sseEvent(eventId: number, type: string, payload: Record<string, unknown>) {
  return `id: ${eventId}\nevent: ${type}\ndata: ${JSON.stringify({
    eventId, runId, type, step: 'WRITING', timestamp: now, payload,
  })}\n\n`
}

async function mockApi(page: Page, mode: 'reconnect' | 'stop') {
  const eventRequests: { url: string; authorization: string | undefined; lastEventId: string | undefined }[] = []
  let cancelCalls = 0
  const project = { id: projectId, name: '雾港来信', genre: '现代幻想', description: null, authorIntent: '保持克制', currentFocus: null, archived: false, version: 0, createdAt: now, updatedAt: now }
  const chapter = { id: chapterId, projectId, outlineNodeId: null, chapterNo: 1, title: '潮声', outline: '进入灯塔', status: 'GENERATING', currentVersionNo: 1, version: 1, createdAt: now, updatedAt: now, currentVersion: { id: 'v1', chapterId, versionNo: 1, title: '潮声', content: '', summary: null, changeSummary: null, restoredFromVersionNo: null, createdAt: now } }

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/auth/login') return json(route, { accessToken: 'phase-six-token', tokenType: 'Bearer', expiresAt: '2026-08-03T01:00:00Z', user: { id: 'user-1', username: 'author', email: 'author@example.com', createdAt: now } })
    if (path === '/api/me') return json(route, { id: 'user-1', username: 'author', email: 'author@example.com', createdAt: now })
    if (path === '/api/projects') return json(route, [project])
    if (path === `/api/projects/${projectId}`) return json(route, project)
    if (path === `/api/projects/${projectId}/chapters`) return json(route, [chapter])
    if (path === `/api/chapters/${chapterId}`) return json(route, chapter)
    if (path === `/api/projects/${projectId}/budget`) return json(route, { projectId, taskTokenLimit: 40000, userDailyCostLimit: 10, projectCostLimit: 100, writerOutputTokenLimit: 12000, plannerReasoningTokenLimit: 6000, version: 0 })
    if (path === `/api/workflows/${runId}/events`) {
      const headers = request.headers()
      eventRequests.push({ url: request.url(), authorization: headers.authorization, lastEventId: headers['last-event-id'] })
      const body = eventRequests.length === 1
        ? sseEvent(1, 'text.delta', { text: '潮' }) + sseEvent(2, 'text.delta', { text: '声' })
        : sseEvent(2, 'text.delta', { text: '声' }) + sseEvent(3, 'text.delta', { text: '响起。' })
      return route.fulfill({ status: 200, contentType: 'text/event-stream', body })
    }
    if (path === `/api/workflows/${runId}/cancel`) {
      cancelCalls += 1
      return json(route, workflow('CANCELLED', '潮声'))
    }
    if (path === `/api/workflows/${runId}`) {
      if (mode === 'reconnect' && eventRequests.length >= 2) return json(route, workflow('WAITING_APPROVAL', '潮声响起。'))
      return json(route, workflow('WRITING', eventRequests.length ? '潮声' : ''))
    }
    return json(route, { title: 'Not Found', status: 404 }, 404)
  })
  return { eventRequests, cancelCalls: () => cancelCalls }
}

async function loginToWorkflow(page: Page) {
  await page.goto(`/projects/${projectId}/chapters/${chapterId}/workflows/${runId}`)
  await page.getByLabel('邮箱或用户名').fill('author')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(new RegExp(`/workflows/${runId}$`))
}

test('streams with memory Bearer auth, deduplicates, and resumes from Last-Event-ID', async ({ page }) => {
  const calls = await mockApi(page, 'reconnect')
  await loginToWorkflow(page)

  await expect(page.locator('.streaming-draft-copy')).toHaveText('潮声响起。')
  await expect(page.getByRole('heading', { name: '等待人工确认' })).toBeVisible()
  expect(calls.eventRequests).toHaveLength(2)
  expect(calls.eventRequests[0]?.authorization).toBe('Bearer phase-six-token')
  expect(calls.eventRequests[1]?.lastEventId).toBe('2')
  expect(calls.eventRequests.every((request) => !request.url.includes('token'))).toBe(true)
})

test('stopping generation cancels the backend run and prevents later reconnects', async ({ page }) => {
  const calls = await mockApi(page, 'stop')
  await loginToWorkflow(page)
  await expect(page.locator('.streaming-draft-copy')).toContainText('潮声')

  await page.getByRole('button', { name: '停止生成' }).click()
  await expect(page.getByText('事件流已关闭', { exact: true })).toBeVisible()
  await expect(page.locator('.streaming-draft-copy')).toHaveText('潮声')
  const requestsAfterStop = calls.eventRequests.length
  await page.waitForTimeout(900)

  expect(calls.cancelCalls()).toBe(1)
  expect(calls.eventRequests).toHaveLength(requestsAfterStop)
})
