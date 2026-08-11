import { expect, test, type Page, type Route } from '@playwright/test'

const user = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'author',
  email: 'author@example.com',
  createdAt: '2026-08-03T00:00:00Z',
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function mockPhaseOneApi(page: Page) {
  let project: Record<string, unknown> | undefined
  let assets: Array<Record<string, unknown>> = []

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path === '/api/auth/login' && request.method() === 'POST') {
      return json(route, {
        accessToken: 'test-token',
        tokenType: 'Bearer',
        expiresAt: '2026-08-03T01:00:00Z',
        user,
      })
    }
    if (path === '/api/auth/register' && request.method() === 'POST') {
      return json(
        route,
        { accessToken: 'register-token', tokenType: 'Bearer', expiresAt: '2026-08-03T01:00:00Z', user },
        201,
      )
    }
    if (path === '/api/me') return json(route, user)
    if (path === '/api/projects' && request.method() === 'GET') return json(route, project ? [project] : [])
    if (path === '/api/projects' && request.method() === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>
      project = {
        id: '22222222-2222-2222-2222-222222222222',
        ...body,
        archived: false,
        version: 0,
        createdAt: '2026-08-03T00:00:00Z',
        updatedAt: '2026-08-03T00:00:00Z',
      }
      return json(route, project, 201)
    }
    if (path === '/api/projects/22222222-2222-2222-2222-222222222222/assets' && request.method() === 'GET') {
      return json(route, assets)
    }
    if (path === '/api/projects/22222222-2222-2222-2222-222222222222/assets' && request.method() === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>
      const asset = {
        id: '33333333-3333-3333-3333-333333333333',
        projectId: '22222222-2222-2222-2222-222222222222',
        ...body,
        status: 'DRAFT',
        currentVersionNo: 1,
        confirmedVersionNo: null,
        version: 0,
        createdAt: '2026-08-03T00:00:00Z',
        updatedAt: '2026-08-03T00:00:00Z',
        currentVersion: {
          id: '44444444-4444-4444-4444-444444444444',
          versionNo: 1,
          name: body.name,
          content: body.content,
          changeSummary: body.changeSummary,
          createdAt: '2026-08-03T00:00:00Z',
        },
      }
      assets = [asset]
      return json(route, asset, 201)
    }
    if (path === '/api/projects/22222222-2222-2222-2222-222222222222' && request.method() === 'GET') {
      return json(route, project)
    }

    return json(route, { title: 'Not Found', status: 404 }, 404)
  })
}

test('login, create a project, and create a real canon asset', async ({ page }) => {
  await mockPhaseOneApi(page)
  await page.goto('/projects')

  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('邮箱或用户名').fill('author@example.com')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page.getByRole('heading', { name: '你的项目' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '还没有项目' })).toBeVisible()
  await page.getByRole('link', { name: '创建第一个项目' }).click()
  await page.getByLabel('项目名称').fill('雾港来信')
  await page.getByRole('radio', { name: '悬疑' }).click()
  await page.getByLabel('故事构想').fill('雾港来信揭开了一桩被潮雾掩盖的失踪案。')
  await page.getByRole('button', { name: '创建项目并进入工作台' }).click()

  await expect(page.getByRole('heading', { name: '雾港来信', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: '还没有正典资产' })).toBeVisible()
  await page.getByRole('button', { name: '创建第一个资产' }).click()
  await page.getByLabel('资产类型').fill('LOCATION')
  await page.getByLabel('名称').fill('雾港')
  await page.getByLabel('内容').fill('终年被潮雾笼罩的港城。')
  await page.getByRole('button', { name: '保存', exact: true }).click()

  await expect(page.getByRole('heading', { name: '雾港', level: 3 })).toBeVisible()
  await expect(page.getByText('终年被潮雾笼罩的港城。')).toBeVisible()

  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '打开或关闭导航' }).click()
  await expect(page.getByRole('complementary', { name: '项目导航' })).toBeVisible()
})

test('registration establishes a session without persisting a token', async ({ page }) => {
  await mockPhaseOneApi(page)
  await page.goto('/register')
  await page.getByLabel('用户名').fill('author')
  await page.getByLabel('邮箱').fill('author@example.com')
  await page.locator('input[name="password"]').fill('change-me-123')
  await page.getByLabel('确认密码').fill('change-me-123')
  await page.getByRole('button', { name: '创建账户' }).click()
  await expect(page.getByRole('heading', { name: '你的项目' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('accessToken'))).toBeNull()
})

test('401 clears the in-memory session and returns to login', async ({ page }) => {
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/auth/login') {
      return json(route, {
        accessToken: 'expired-token',
        tokenType: 'Bearer',
        expiresAt: '2026-08-03T01:00:00Z',
        user,
      })
    }
    return route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'urn:storyweaver:error:authentication_required',
        title: 'Unauthorized',
        status: 401,
        detail: 'Authentication is required',
        code: 'authentication_required',
      }),
    })
  })

  await page.goto('/login')
  await page.getByLabel('邮箱或用户名').fill('author')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/login\?redirect=\/projects$/)
})
