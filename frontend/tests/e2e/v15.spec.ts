import { expect, test, type Route } from '@playwright/test'
import { demoNow, demoProjectId, installDemoApi, loginToDemo } from './fixtures/demo'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

test('imports a manuscript and opens the real split-review result', async ({ page }) => {
  await installDemoApi(page)
  let jobs: unknown[] = []
  await page.route(`**/api/projects/${demoProjectId}/imports`, async route => {
    if (route.request().method() === 'GET') return json(route, jobs)
    const job = {
      id: 'import-1', projectId: demoProjectId, fileName: 'novel.txt', mediaType: 'text/plain',
      status: 'SPLIT_REVIEW', errorMessage: null, version: 0, createdAt: demoNow, updatedAt: demoNow,
      chapters: [{ id: 'part-1', sequenceNo: 1, title: '第一章 潜入', content: '青铜门在水下缓缓开启。', included: true, createdChapterId: null }],
      candidates: [],
    }
    jobs = [job]
    return json(route, job, 202)
  })

  await loginToDemo(page, `/projects/${demoProjectId}/imports`)
  await expect(page.getByRole('heading', { name: '导入与迁移' })).toBeVisible()
  await page.locator('input[type="file"]').setInputFiles({ name: 'novel.txt', mimeType: 'text/plain', buffer: Buffer.from('第一章 潜入\n青铜门在水下缓缓开启。') })
  await page.getByRole('button', { name: '上传并切分' }).click()
  await expect(page.getByRole('heading', { name: 'novel.txt' })).toBeVisible()
  const openDetails = page.getByRole('button', { name: '查看内容' })
  if (await openDetails.isVisible()) await openDetails.click()
  await expect(page.getByLabel('章节标题')).toHaveValue('第一章 潜入')
})

test('creates a gated one-chapter production batch', async ({ page }) => {
  await installDemoApi(page)
  const chapterId = '88888888-8888-4888-8888-888888888888'
  const characterId = '99999999-9999-4999-8999-999999999999'
  let batches: unknown[] = []
  await page.route(`**/api/projects/${demoProjectId}/chapters`, route => json(route, [{ id: chapterId, projectId: demoProjectId, outlineNodeId: null, chapterNo: 1, title: '潜入青铜城', outline: '进入水下遗迹', status: 'DRAFT', currentVersionNo: 0, version: 0, createdAt: demoNow, updatedAt: demoNow, currentVersion: null }]))
  await page.route(`**/api/projects/${demoProjectId}/characters`, route => json(route, [{ id: characterId, projectId: demoProjectId, name: '路明非', aliases: null, role: '主角', description: null, personality: null, background: null, goals: null, appearance: null, notes: null, archived: false, version: 0, createdAt: demoNow, updatedAt: demoNow, state: { id: 'state-1', projectId: demoProjectId, characterId, lifeStatus: 'ALIVE', currentLocation: null, physicalCondition: null, emotionalState: null, abilities: null, inventoryNotes: null, notes: null, version: 0, createdAt: demoNow, updatedAt: demoNow } }]))
  await page.route(`**/api/projects/${demoProjectId}/chapter-batches`, async route => {
    if (route.request().method() === 'GET') return json(route, batches)
    const batch = { id: 'batch-1', projectId: demoProjectId, viewpointCharacterId: characterId, instruction: '潜入后发现龙王线索', status: 'WAITING_GATE', currentIndex: 0, version: 1, createdAt: demoNow, updatedAt: demoNow, items: [{ id: 'item-1', sequenceNo: 1, chapterId, workflowRunId: null, status: 'QUEUED' }] }
    batches = [batch]
    return json(route, batch, 202)
  })

  await loginToDemo(page, `/projects/${demoProjectId}/production`)
  await page.getByLabel('视角人物').selectOption(characterId)
  await page.getByLabel('批次写作指令').fill('潜入后发现龙王线索')
  const row = page.getByText('第 1 章 · 潜入青铜城').locator('..')
  await row.locator('input[type="checkbox"]').first().check()
  await row.locator('input[type="checkbox"]').nth(1).check()
  await page.getByRole('button', { name: '启动批次' }).click()
  await expect(page.getByText('WAITING_GATE')).toBeVisible()
})
