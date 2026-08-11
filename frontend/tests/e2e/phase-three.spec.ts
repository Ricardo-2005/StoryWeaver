import { expect, test, type Page, type Route } from '@playwright/test'

const projectId = '22222222-2222-2222-2222-222222222222'
const assetId = '33333333-3333-3333-3333-333333333333'
const now = '2026-08-03T00:00:00Z'
const project = {
  id: projectId,
  name: '雾港来信',
  genre: '现代幻想',
  description: null,
  authorIntent: null,
  currentFocus: null,
  archived: false,
  version: 0,
  createdAt: now,
  updatedAt: now,
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function mockPhaseThreeApi(page: Page) {
  const conversationRequests: string[] = []
  let asset = {
    id: assetId,
    projectId,
    assetType: 'LOCATION',
    name: '雾港',
    status: 'DRAFT',
    currentVersionNo: 1,
    confirmedVersionNo: null,
    version: 0,
    createdAt: now,
    updatedAt: now,
    currentVersion: {
      id: '44444444-4444-4444-8444-444444444444',
      versionNo: 1,
      name: '雾港',
      content: '潮雾覆盖旧码头。',
      changeSummary: null as string | null,
      createdAt: now,
    },
  }

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    if (/conversation|message|chat/i.test(path)) conversationRequests.push(path)

    if (path === '/api/auth/login') {
      return json(route, {
        accessToken: 'phase-three-token',
        tokenType: 'Bearer',
        expiresAt: '2026-08-03T01:00:00Z',
        user: { id: 'user-1', username: 'author', email: 'author@example.com', createdAt: now },
      })
    }
    if (path === '/api/me') {
      return json(route, { id: 'user-1', username: 'author', email: 'author@example.com', createdAt: now })
    }
    if (path === '/api/projects' && method === 'GET') return json(route, [project])
    if (path === `/api/projects/${projectId}` && method === 'GET') return json(route, project)
    if (path === `/api/projects/${projectId}/assets` && method === 'GET') return json(route, [asset])
    if (path === `/api/assets/${assetId}` && method === 'PUT') {
      const body = request.postDataJSON() as {
        name: string
        content: string
        changeSummary: string | null
        expectedVersion: number
      }
      asset = {
        ...asset,
        name: body.name,
        version: asset.version + 1,
        currentVersionNo: asset.currentVersionNo + 1,
        currentVersion: {
          ...asset.currentVersion,
          versionNo: asset.currentVersionNo + 1,
          name: body.name,
          content: body.content,
          changeSummary: body.changeSummary,
        },
      }
      return json(route, asset)
    }
    return json(route, { title: 'Not Found', status: 404 }, 404)
  })

  return conversationRequests
}

async function enterWorkspace(page: Page) {
  await page.goto('/login')
  await page.getByLabel('邮箱或用户名').fill('author')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()
  await page.getByRole('link', { name: '雾港来信' }).first().click()
  await page.getByRole('link', { name: '创作工作台', exact: true }).click()
}

test('uses local Writing Blocks and a real canon Canvas without fake Chat requests', async ({ page }) => {
  const conversationRequests = await mockPhaseThreeApi(page)
  await enterWorkspace(page)

  await expect(page.getByRole('heading', { name: '创作工作台' })).toBeVisible()
  await expect(page.getByRole('button', { name: '发送' })).toBeDisabled()
  await expect(page.getByText(/尚未提供 Conversation、Message 或 Chat SSE/)).toBeVisible()

  await page.getByRole('button', { name: '新建本地 Writing Block' }).click()
  const localCanvas = page.getByRole('complementary', { name: 'Writing Block Canvas' })
  await expect(localCanvas).toBeVisible()
  await localCanvas.getByPlaceholder('输入本地片段').fill('潮声穿过旧码头。')
  await localCanvas.getByRole('button', { name: '关闭 Canvas' }).click()
  await expect(page.getByLabel('Writing Block 内容')).toHaveValue('潮声穿过旧码头。')

  await page.getByRole('button', { name: /雾港/ }).click()
  const canonCanvas = page.getByRole('complementary', { name: '创作 Canvas' })
  const content = canonCanvas.getByLabel('内容')
  await content.fill('潮雾覆盖旧码头，灯塔彻夜不熄。')
  await content.selectText()
  await expect(page.getByLabel('已选择上下文')).toContainText('雾港')
  await canonCanvas.getByLabel('变更说明').fill('补充灯塔设定')
  await canonCanvas.getByRole('button', { name: '保存新版本' }).click()
  await expect(page.getByText('Canvas 已保存为新的资产版本')).toBeVisible()

  expect(conversationRequests).toEqual([])
})
