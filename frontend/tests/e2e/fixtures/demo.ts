import type { Page, Route } from '@playwright/test'

export const demoProjectId = '22222222-2222-4222-8222-222222222222'
export const demoAssetId = '33333333-3333-4333-8333-333333333333'
export const demoNow = '2026-08-03T10:00:00Z'

const user = {
  id: '11111111-1111-4111-8111-111111111111',
  username: 'author',
  email: 'author@example.com',
  createdAt: demoNow,
}

export const demoProject = {
  id: demoProjectId,
  name: '青铜城行动',
  genre: '现代幻想',
  description: '在水下青铜城中寻找龙王苏醒的线索。',
  authorIntent: '保持知识边界清晰，悬念逐层推进。',
  currentFocus: '楚子航进入青铜城前的准备。',
  archived: false,
  version: 3,
  createdAt: demoNow,
  updatedAt: demoNow,
}

const demoAsset = {
  id: demoAssetId,
  projectId: demoProjectId,
  assetType: 'LOCATION',
  name: '青铜城',
  status: 'CONFIRMED',
  currentVersionNo: 2,
  confirmedVersionNo: 2,
  version: 2,
  createdAt: demoNow,
  updatedAt: demoNow,
  currentVersion: {
    id: '44444444-4444-4444-8444-444444444444',
    versionNo: 2,
    name: '青铜城',
    content: '沉没于长江水下的龙族遗迹，内部机关仍在运转。',
    changeSummary: '确认演示正典',
    createdAt: demoNow,
  },
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

export async function installDemoApi(page: Page): Promise<void> {
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()

    if (path === '/api/auth/login' && method === 'POST') {
      return json(route, {
        accessToken: 'phase-nine-demo-token',
        tokenType: 'Bearer',
        expiresAt: '2026-08-03T12:00:00Z',
        user,
      })
    }
    if (path === '/api/me') return json(route, user)
    if (path === '/api/projects') return json(route, [demoProject])
    if (path === `/api/projects/${demoProjectId}`) return json(route, demoProject)
    if (path === `/api/projects/${demoProjectId}/assets`) return json(route, [demoAsset])
    if (path === `/api/projects/${demoProjectId}/worldbook-entries`) {
      return json(route, [{
        id: '55555555-5555-4555-8555-555555555555',
        projectId: demoProjectId,
        title: '青铜城机关',
        content: '机关以龙文和活灵驱动，仅在特定血统靠近时激活。',
        active: true,
        constantEnabled: false,
        vectorEnabled: true,
        keywords: ['青铜城', '活灵', '机关'],
        priority: 90,
        scopeType: 'PROJECT',
        scopeRefId: null,
        visibilityType: 'ALL',
        visibilityRefId: null,
        embeddingStatus: 'AVAILABLE',
        embeddingModel: 'demo-embedding',
        version: 1,
        createdAt: demoNow,
        updatedAt: demoNow,
      }])
    }
    if (path === `/api/projects/${demoProjectId}/usage`) {
      return json(route, [{
        id: '66666666-6666-4666-8666-666666666666',
        projectId: demoProjectId,
        agent: 'WRITER',
        model: 'deepseek-v4-pro',
        requestId: 'provider-request-demo',
        status: 'SUCCEEDED',
        promptTokens: 3200,
        completionTokens: 1800,
        reasoningTokens: 0,
        promptCacheHitTokens: 2100,
        promptCacheMissTokens: 600,
        attempts: 1,
        durationMillis: 2400,
        requestedAt: demoNow,
        pricingRuleId: '77777777-7777-4777-8777-777777777777',
        pricingRuleVersion: '2026-08',
        estimatedCost: 0.018,
        actualCost: 0.018,
        currency: 'USD',
      }])
    }
    if (path === `/api/projects/${demoProjectId}/costs`) {
      return json(route, {
        projectId: demoProjectId,
        estimatedCost: 0.018,
        actualCost: 0.018,
        unpricedRequests: 0,
        requests: 1,
      })
    }
    if (path === `/api/projects/${demoProjectId}/budget`) {
      return json(route, {
        projectId: demoProjectId,
        taskTokenLimit: 40000,
        userDailyCostLimit: 10,
        projectCostLimit: 100,
        writerOutputTokenLimit: 12000,
        plannerReasoningTokenLimit: 6000,
        version: 1,
      })
    }
    if (path === '/api/ai/model-config') {
      return json(route, [{
        agent: 'WRITER',
        model: 'deepseek-v4-pro',
        thinking: false,
        reasoningEffort: null,
        temperature: 0.7,
        jsonOutput: false,
        stream: true,
        maxOutputTokens: 12000,
        maxAttempts: 2,
        ignoredParameters: ['frequency_penalty'],
      }])
    }
    if (path === '/api/pricing-rules') {
      return json(route, [{
        id: '77777777-7777-4777-8777-777777777777',
        ruleVersion: '2026-08',
        model: 'deepseek-v4-pro',
        currency: 'USD',
        inputPerMillion: 1,
        outputPerMillion: 2,
        reasoningPerMillion: 3,
        cacheHitPerMillion: 0.1,
        cacheMissPerMillion: 0.5,
        effectiveFrom: demoNow,
        effectiveTo: null,
      }])
    }

    return json(route, {
      type: 'about:blank',
      title: 'Not Found',
      status: 404,
      detail: `Demo fixture does not implement ${method} ${path}`,
    }, 404)
  })
}

export async function loginToDemo(page: Page, destination = `/projects/${demoProjectId}`): Promise<void> {
  await page.goto(destination)
  await page.getByLabel('邮箱或用户名').fill('author')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()
  await page.getByRole('heading', { level: 1 }).waitFor()
}
