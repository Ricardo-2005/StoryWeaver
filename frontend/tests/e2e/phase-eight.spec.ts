import { expect, test, type Page, type Route } from '@playwright/test'

const projectId = '22222222-2222-4222-8222-222222222222'
const now = '2026-08-03T00:00:00Z'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function mockPhaseEightApi(page: Page) {
  const budgetUpdates: unknown[] = []
  const project = { id: projectId, name: '雾港来信', genre: '现代幻想', description: null, authorIntent: '保持克制', currentFocus: null, archived: false, version: 0, createdAt: now, updatedAt: now }
  const usage = [
    { id: 'usage-1', projectId, agent: 'WRITER', model: 'deepseek-v4-pro', requestId: 'ds-request-1', status: 'SUCCEEDED', promptTokens: 1000, completionTokens: 500, reasoningTokens: 0, promptCacheHitTokens: 600, promptCacheMissTokens: 200, attempts: 1, durationMillis: 1500, requestedAt: '2026-08-01T10:00:00Z', pricingRuleId: 'rule-1', pricingRuleVersion: '2026-08', estimatedCost: 0.012345, actualCost: 0.012345, currency: 'USD' },
    { id: 'usage-2', projectId, agent: 'PLANNER', model: 'deepseek-v4-pro', requestId: 'ds-request-2', status: 'SUCCEEDED', promptTokens: 2000, completionTokens: 300, reasoningTokens: 100, promptCacheHitTokens: 0, promptCacheMissTokens: 800, attempts: 2, durationMillis: 4200, requestedAt: '2026-08-02T10:00:00Z', pricingRuleId: 'rule-1', pricingRuleVersion: '2026-08', estimatedCost: 0.02, actualCost: 0.02, currency: 'USD' },
    { id: 'usage-3', projectId, agent: 'REVIEWER', model: 'deepseek-v4-flash', requestId: null, status: 'FAILED', promptTokens: 0, completionTokens: 0, reasoningTokens: 0, promptCacheHitTokens: 0, promptCacheMissTokens: 0, attempts: 1, durationMillis: 8000, requestedAt: '2026-08-02T11:00:00Z', pricingRuleId: null, pricingRuleVersion: null, estimatedCost: null, actualCost: null, currency: null },
  ]
  const models = [
    { agent: 'PLANNER', model: 'deepseek-v4-pro', thinking: true, reasoningEffort: 'high', temperature: null, jsonOutput: true, stream: false, maxOutputTokens: 6000, maxAttempts: 2, ignoredParameters: ['presence_penalty'] },
    { agent: 'WRITER', model: 'deepseek-v4-pro', thinking: false, reasoningEffort: null, temperature: 0.78, jsonOutput: false, stream: true, maxOutputTokens: 12000, maxAttempts: 1, ignoredParameters: ['frequency_penalty'] },
    { agent: 'EXTRACTOR', model: 'deepseek-v4-flash', thinking: false, reasoningEffort: null, temperature: 0.1, jsonOutput: true, stream: false, maxOutputTokens: 7000, maxAttempts: 3, ignoredParameters: [] },
    { agent: 'REVIEWER', model: 'deepseek-v4-pro', thinking: true, reasoningEffort: 'high', temperature: null, jsonOutput: true, stream: false, maxOutputTokens: 8000, maxAttempts: 2, ignoredParameters: [] },
  ]
  let budget = { projectId, taskTokenLimit: 40000, userDailyCostLimit: 10, projectCostLimit: 100, writerOutputTokenLimit: 12000, plannerReasoningTokenLimit: 6000, version: 0 }

  await page.route('**/api/**', async (route) => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    if (path === '/api/auth/login') return json(route, { accessToken: 'phase-eight-token', tokenType: 'Bearer', expiresAt: '2026-08-03T01:00:00Z', user: { id: 'user-1', username: 'author', email: 'author@example.com', createdAt: now } })
    if (path === '/api/me') return json(route, { id: 'user-1', username: 'author', email: 'author@example.com', createdAt: now })
    if (path === '/api/projects') return json(route, [project])
    if (path === `/api/projects/${projectId}`) return json(route, project)
    if (path === `/api/projects/${projectId}/usage`) return json(route, usage)
    if (path === `/api/projects/${projectId}/costs`) return json(route, { projectId, estimatedCost: 0.04, actualCost: 0.032345, unpricedRequests: 1, requests: 3 })
    if (path === `/api/projects/${projectId}/budget` && method === 'GET') return json(route, budget)
    if (path === `/api/projects/${projectId}/budget` && method === 'PUT') {
      expect(request.headers().authorization).toBe('Bearer phase-eight-token')
      const body = request.postDataJSON() as typeof budget
      budgetUpdates.push(body)
      budget = { ...body, projectId, version: 1 }
      return json(route, budget)
    }
    if (path === '/api/ai/model-config') return json(route, models)
    if (path === '/api/pricing-rules') return json(route, [{ id: 'rule-1', ruleVersion: '2026-08', model: 'deepseek-v4-pro', currency: 'USD', inputPerMillion: 1, outputPerMillion: 2, reasoningPerMillion: 3, cacheHitPerMillion: 0.1, cacheMissPerMillion: 0.5, effectiveFrom: now, effectiveTo: null }])
    return json(route, { title: 'Not Found', status: 404 }, 404)
  })
  return { budgetUpdates }
}

test('renders backend usage charts and updates the versioned project budget', async ({ page }) => {
  const calls = await mockPhaseEightApi(page)
  await page.goto(`/projects/${projectId}/observability`)
  await page.getByLabel('邮箱或用户名').fill('author')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page.getByRole('heading', { name: '模型与费用', exact: true })).toBeVisible()
  await expect(page.getByText('1 个请求未计价')).toBeVisible()
  await expect(page.getByText('3,900', { exact: true })).toBeVisible()
  await expect(page.getByText(/0\.032345/).first()).toBeVisible()
  await expect(page.locator('.usage-chart canvas')).toHaveCount(4)
  await expect(page.getByRole('img', { name: '每日输入、输出和推理 Token 堆叠柱状图' })).toBeVisible()
  await expect(page.getByText('presence_penalty')).toHaveAttribute('aria-disabled', 'true')
  await expect(page.getByText('模型 requestId 不是应用 Trace ID')).toBeVisible()

  await page.getByRole('button', { name: '调整预算' }).click()
  await page.getByLabel('项目累计费用上限').fill('150')
  await page.getByRole('button', { name: '保存预算' }).click()
  await expect(page.getByRole('dialog')).not.toBeVisible()

  expect(calls.budgetUpdates).toEqual([{
    taskTokenLimit: 40000,
    userDailyCostLimit: 10,
    projectCostLimit: 150,
    writerOutputTokenLimit: 12000,
    plannerReasoningTokenLimit: 6000,
    expectedVersion: 0,
  }])
})
