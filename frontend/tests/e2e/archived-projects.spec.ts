import { expect, test, type Page, type Route } from '@playwright/test'

const now = '2026-08-10T08:00:00Z'
const user = {
  id: '11111111-1111-4111-8111-111111111111',
  username: 'author',
  email: 'author@example.com',
  role: 'USER',
  createdAt: now,
}

function project(id: string, name: string, version: number) {
  return {
    id,
    name,
    genre: '悬疑',
    customGenre: null,
    targetAudience: 'GENERAL',
    narrativePerspective: 'THIRD_PERSON',
    lengthType: 'LONG_NOVEL',
    premise: '一段用于归档项目回归测试的故事构想。',
    description: `${name}的项目简介`,
    authorIntent: null,
    currentFocus: null,
    worldRules: [],
    targetWordCount: null,
    chapterWordTarget: null,
    archived: true,
    version,
    createdAt: now,
    updatedAt: now,
  }
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function login(page: Page): Promise<void> {
  await page.goto('/projects/archived')
  await page.getByLabel('邮箱或用户名').fill('author')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: '归档项目' })).toBeVisible()
}

test('archived projects can be viewed, restored, and permanently deleted', async ({ page }) => {
  const restoredId = '22222222-2222-4222-8222-222222222222'
  const deletedId = '33333333-3333-4333-8333-333333333333'
  let projects = [project(restoredId, '雾港来信', 2), project(deletedId, '旧城手稿', 7)]
  let sawArchivedQuery = false
  let deleteVersion: string | null = null

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()

    if (path === '/api/auth/login' && method === 'POST') {
      return json(route, {
        accessToken: 'archive-test-token',
        tokenType: 'Bearer',
        expiresAt: '2026-08-10T10:00:00Z',
        user,
      })
    }
    if (path === '/api/me') return json(route, user)
    if (path === '/api/projects' && method === 'GET') {
      const includeArchived = url.searchParams.get('includeArchived') === 'true'
      sawArchivedQuery ||= includeArchived
      return json(route, includeArchived ? projects : projects.filter((item) => !item.archived))
    }
    if (path === `/api/projects/${restoredId}` && method === 'PUT') {
      const body = request.postDataJSON() as { archived: boolean; expectedVersion: number }
      expect(body.archived).toBe(false)
      expect(body.expectedVersion).toBe(2)
      projects = projects.map((item) =>
        item.id === restoredId ? { ...item, archived: false, version: 3, updatedAt: now } : item,
      )
      return json(route, projects.find((item) => item.id === restoredId))
    }
    if (path === `/api/projects/${deletedId}` && method === 'DELETE') {
      deleteVersion = url.searchParams.get('expectedVersion')
      projects = projects.filter((item) => item.id !== deletedId)
      return route.fulfill({ status: 204 })
    }

    return json(route, { title: 'Not Found', status: 404 }, 404)
  })

  await login(page)
  await expect(page.getByRole('heading', { name: '雾港来信' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '旧城手稿' })).toBeVisible()
  expect(sawArchivedQuery).toBe(true)

  await page.getByRole('button', { name: '恢复项目 雾港来信' }).click()
  await expect(page.getByRole('heading', { name: '雾港来信' })).toBeHidden()
  await expect(page.getByRole('heading', { name: '旧城手稿' })).toBeVisible()

  await page.getByRole('button', { name: '永久删除项目 旧城手稿' }).click()
  const dialog = page.getByRole('dialog', { name: '永久删除项目' })
  await expect(dialog.getByText(/无法恢复/)).toBeVisible()
  await dialog.getByRole('button', { name: '永久删除' }).click()

  await expect(page.getByRole('heading', { name: '没有归档项目' })).toBeVisible()
  expect(deleteVersion).toBe('7')

  await page.getByRole('link', { name: '查看当前项目' }).click()
  await expect(page.getByRole('heading', { name: '雾港来信' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '旧城手稿' })).toHaveCount(0)
})
