import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'

const accessibilityTags = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

async function expectNoAccessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(accessibilityTags).analyze()
  expect(results.violations.map(({ id, impact, nodes }) => ({ id, impact, targets: nodes.map(node => node.target) }))).toEqual([])
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function mockSkillWorkshop(page: Page) {
  const user = {
    id: '11111111-1111-1111-1111-111111111111',
    username: 'author',
    email: 'author@example.com',
    createdAt: '2026-08-07T00:00:00Z',
  }
  const skills = [
    {
      id: '21111111-1111-1111-1111-111111111111',
      slug: 'chinese-web-fiction-foundation',
      displayName: '中文网文基础写作契约',
      description: '面向长篇网络小说的基础写作方法，约束叙事因果、人物决策、表达节奏和诚实边界。',
      scope: 'BUILT_IN',
      status: 'VALIDATED',
      contract: { identity: { type: 'FOUNDATION' } },
      currentVersionId: '31111111-1111-1111-1111-111111111111',
      version: 1,
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-07T00:00:00Z',
    },
    {
      id: '41111111-1111-1111-1111-111111111111',
      slug: 'my-dialogue-method',
      displayName: '我的对话节奏方法',
      description: '从练习文本中提炼的短回合对话、动作承接和信息留白规则。',
      scope: 'PRIVATE_GLOBAL',
      status: 'WAITING_REVIEW',
      contract: { identity: { type: 'TECHNIQUE' } },
      currentVersionId: null,
      version: 0,
      createdAt: '2026-08-06T00:00:00Z',
      updatedAt: '2026-08-07T00:00:00Z',
    },
  ]
  const versions = [{
    id: '31111111-1111-1111-1111-111111111111',
    globalSkillId: skills[0]?.id,
    versionNo: 1,
    contract: skills[0]?.contract,
    snapshotHash: '91d64c4e18e9d3092c4ee7fa06e21f69e594c4ce8fce93499f12139824944289',
    status: 'VALIDATED',
    tokenEstimate: 1842,
    createdAt: '2026-08-07T00:00:00Z',
  }]
  const tests = Array.from({ length: 8 }, (_, index) => ({
    id: `51111111-1111-1111-1111-11111111111${index}`,
    caseType: index < 3 ? 'TYPICAL' : ['CONFLICT', 'EDGE', 'OUT_OF_EVIDENCE', 'OVERFITTING', 'HONESTY_BOUNDARY'][index - 3],
    title: ['典型写作任务', '典型审查任务', '典型规划任务', '冲突规则', '边界输入', '证据外推', '过拟合检查', '诚实边界'][index],
    prompt: '测试输入',
    expectedAssertions: [],
    latestResult: { runStatus: 'PASSED', score: 100, passed: true, finding: '通过' },
  }))

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/auth/login' && request.method() === 'POST') {
      return json(route, { accessToken: 'test-token', tokenType: 'Bearer', expiresAt: '2026-08-08T00:00:00Z', user })
    }
    if (path === '/api/me') return json(route, user)
    if (path === '/api/projects') return json(route, [])
    if (path === '/api/skills') return json(route, skills)
    if (path === `/api/skills/${skills[0]?.id}`) return json(route, skills[0])
    if (path === `/api/skills/${skills[0]?.id}/versions`) return json(route, versions)
    if (path === `/api/skills/${skills[0]?.id}/tests`) return json(route, tests)
    return json(route, { title: 'Not Found', status: 404 }, 404)
  })
}

async function login(page: Page) {
  await page.goto('/skills')
  await page.getByLabel('邮箱或用户名').fill('author@example.com')
  await page.getByLabel('密码').fill('change-me-123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: 'Skill 工坊' })).toBeVisible()
}

test('Skill 工坊在桌面和移动端保持清晰层级', async ({ page }) => {
  await mockSkillWorkshop(page)
  await login(page)

  await expect(page.getByRole('link', { name: '进入 Skill 熔炉' })).toBeVisible()
  await expect(page.getByText('中文网文基础写作契约')).toBeVisible()
  await expectNoAccessibilityViolations(page)
  await expect(page).toHaveScreenshot('skill-workshop-desktop.png', { fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('link', { name: '进入 Skill 熔炉' })).toBeVisible()
  await expect(page.locator('body')).not.toHaveCSS('overflow-x', 'scroll')
  await expect(page).toHaveScreenshot('skill-workshop-mobile.png', { fullPage: true })
})

test('Skill 页面在深色主题下保持一致层级', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' })
  await mockSkillWorkshop(page)
  await login(page)
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expectNoAccessibilityViolations(page)
  await expect(page).toHaveScreenshot('skill-workshop-dark.png', { fullPage: true })
})

test('Skill 熔炉与详情页保持生产工具密度', async ({ page }) => {
  await mockSkillWorkshop(page)
  await login(page)

  await page.getByRole('link', { name: '进入 Skill 熔炉' }).click()
  await expect(page.getByRole('heading', { name: '创建写作 Skill' })).toBeVisible()
  await expectNoAccessibilityViolations(page)
  await expect(page).toHaveScreenshot('skill-forge-desktop.png', { fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)

  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goBack()
  await page.locator('article').filter({ hasText: '中文网文基础写作契约' }).getByRole('link', { name: '查看契约' }).click()
  await expect(page.getByRole('heading', { name: '中文网文基础写作契约' })).toBeVisible()
  await expect(page.getByText('8 / 8')).toBeVisible()
  await expectNoAccessibilityViolations(page)
  await expect(page).toHaveScreenshot('skill-detail-desktop.png', { fullPage: true })
})
