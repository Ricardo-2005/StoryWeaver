import { expect, test, type Page, type Route } from '@playwright/test'

const projectId = '22222222-2222-2222-2222-222222222222'
const chapterId = '77777777-7777-4777-8777-777777777777'
const now = '2026-08-03T00:00:00Z'
const project = { id: projectId, name: '雾港来信', genre: '现代幻想', description: null, authorIntent: null, currentFocus: null, archived: false, version: 0, createdAt: now, updatedAt: now }

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function mockPhaseFourApi(page: Page, initialContent = '潮雾覆盖旧码头。') {
  const forbiddenAiRequests: string[] = []
  let chapterVersion = 0
  let currentVersionNo = 1
  let current = {
    id: '88888888-8888-4888-8888-888888888881',
    chapterId,
    versionNo: 1,
    title: '潮声',
    content: initialContent,
    summary: null as string | null,
    changeSummary: '初稿' as string | null,
    restoredFromVersionNo: null as number | null,
    createdAt: now,
  }
  let versions = [current]
  const chapterResponse = () => ({
    id: chapterId,
    projectId,
    outlineNodeId: null,
    chapterNo: 1,
    title: current.title,
    outline: '林雾抵达港城。',
    status: 'DRAFT',
    currentVersionNo,
    version: chapterVersion,
    createdAt: now,
    updatedAt: now,
    currentVersion: current,
  })

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    if ((/\/ai\//.test(path) && method === 'POST') || /\/workflows/.test(path)) forbiddenAiRequests.push(path)

    if (path === '/api/auth/login') return json(route, {
      accessToken: 'phase-four-token', tokenType: 'Bearer', expiresAt: '2026-08-03T01:00:00Z',
      user: { id: 'user-1', username: 'author', email: 'author@example.com', createdAt: now },
    })
    if (path === '/api/me') return json(route, { id: 'user-1', username: 'author', email: 'author@example.com', createdAt: now })
    if (path === '/api/projects' && method === 'GET') return json(route, [project])
    if (path === `/api/projects/${projectId}` && method === 'GET') return json(route, project)
    if (path === `/api/projects/${projectId}/chapters` && method === 'GET') return json(route, [chapterResponse()])
    if (path === `/api/chapters/${chapterId}` && method === 'GET') return json(route, chapterResponse())
    if (path === `/api/chapters/${chapterId}/versions` && method === 'GET') return json(route, versions)
    if (path === `/api/chapters/${chapterId}/versions` && method === 'POST') {
      const body = request.postDataJSON() as { title: string; content: string; summary: string | null; changeSummary: string | null; expectedVersion: number }
      expect(body.expectedVersion).toBe(chapterVersion)
      chapterVersion += 1
      currentVersionNo += 1
      current = { id: `version-${currentVersionNo}`, chapterId, versionNo: currentVersionNo, title: body.title, content: body.content, summary: body.summary, changeSummary: body.changeSummary, restoredFromVersionNo: null, createdAt: now }
      versions = [...versions, current]
      return json(route, chapterResponse(), 201)
    }
    if (path === `/api/chapters/${chapterId}/restore/1` && method === 'POST') {
      const body = request.postDataJSON() as { expectedVersion: number; changeSummary: string | null }
      expect(body.expectedVersion).toBe(chapterVersion)
      chapterVersion += 1
      currentVersionNo += 1
      const first = versions[0]!
      current = { ...first, id: `version-${currentVersionNo}`, versionNo: currentVersionNo, changeSummary: body.changeSummary, restoredFromVersionNo: 1 }
      versions = [...versions, current]
      return json(route, chapterResponse())
    }
    return json(route, { title: 'Not Found', status: 404 }, 404)
  })

  return forbiddenAiRequests
}

async function login(page: Page) {
  await page.getByLabel('邮箱或用户名').fill('author')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()
}

test('recovers a local TipTap draft, creates a formal version, and restores through the backend', async ({ page }) => {
  const forbiddenAiRequests = await mockPhaseFourApi(page)
  await page.goto(`/projects/${projectId}/chapters/${chapterId}`)
  await login(page)

  const editor = page.getByLabel('章节正文')
  await expect(editor).toContainText('潮雾覆盖旧码头。')
  await expect(editor.locator('p')).toHaveAttribute('data-paragraph-key', /^p_/)
  await page.getByLabel('章节标题').fill('灯塔彻夜不熄')
  await editor.fill('潮雾覆盖旧码头。\n灯塔彻夜不熄。')
  await expect(page.getByText('本地草稿已保存')).toBeVisible({ timeout: 5_000 })

  await page.reload()
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await login(page)
  await expect(page.getByText('发现未提交的本地草稿')).toBeVisible()
  await page.getByRole('button', { name: '恢复草稿' }).click()
  await expect(page.getByLabel('章节标题')).toHaveValue('灯塔彻夜不熄')
  await expect(page.getByLabel('章节正文')).toContainText('灯塔彻夜不熄。')

  await page.getByRole('button', { name: '查找替换' }).click()
  await page.getByPlaceholder('查找文字').fill('潮雾')
  await page.getByPlaceholder('替换为').fill('海雾')
  await page.getByRole('button', { name: '全部替换' }).click()
  await expect(page.getByLabel('章节正文')).toContainText('海雾覆盖旧码头。')

  await page.getByRole('button', { name: '保存正式版本' }).click()
  await page.getByLabel('变更说明').fill('完成灯塔段落')
  await page.getByRole('button', { name: '创建正式版本' }).click()
  await expect(page.getByText('正式版本 v2 已创建')).toBeVisible()
  await expect(page.getByText('v2 · 灯塔彻夜不熄')).toBeVisible()

  const firstVersion = page.getByRole('complementary', { name: '章节版本' }).locator('article').filter({ hasText: 'v1 · 潮声' })
  await firstVersion.getByRole('button', { name: '恢复为新版本' }).click()
  await page.getByRole('button', { name: '创建恢复版本' }).click()
  await expect(page.getByText('已创建 v3')).toBeVisible()
  await expect(page.getByLabel('章节标题')).toHaveValue('潮声')

  expect(forbiddenAiRequests).toEqual([])
})

test('edits a 100,000-character chapter in TipTap', async ({ page }) => {
  await mockPhaseFourApi(page, '雾'.repeat(100_000))
  await page.goto(`/projects/${projectId}/chapters/${chapterId}`)
  await login(page)

  const editor = page.getByLabel('章节正文')
  await expect(page.getByText(/100,000 字/)).toBeVisible()
  await editor.press('End')
  await editor.pressSequentially('潮')
  await expect(page.getByText(/100,001 字/)).toBeVisible()
})
