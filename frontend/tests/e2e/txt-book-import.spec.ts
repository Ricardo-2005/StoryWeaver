import { expect, test, type Route } from '@playwright/test'

import { demoNow, installDemoApi, loginToDemo } from './fixtures/demo'

const importId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const projectId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

test('uploads, previews, edits, splits, merges and commits a TXT project', async ({ page }) => {
  await installDemoApi(page)
  let version = 0
  let status = 'UPLOADED'
  let project: string | null = null
  let reconstructionStatus = 'NOT_ANALYZED'
  let chapters = [
    chapter('chapter-1', 1, '序章', 0, 18),
    chapter('chapter-2', 2, '第001章 雾港', 25, 60),
  ]
  const job = () => ({
    id: importId,
    sourceId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    projectId: project,
    status,
    analysisStatus: 'NOT_REQUESTED',
    filename: '雾港.txt',
    sizeBytes: 86,
    sha256: 'a'.repeat(64),
    detectedEncoding: 'UTF-8',
    selectedEncoding: 'UTF-8',
    encodingConfident: true,
    totalCharacters: 60,
    totalChapters: chapters.length,
    processedChapters: status === 'COMPLETED' ? chapters.length : 0,
    headingCount: 2,
    analysisProcessedChunks: 0,
    parserVersion: 'txt-lines-v2',
    errorCode: null,
    errorMessage: null,
    duplicateImportId: null,
    duplicateProjectId: null,
    version,
    expiresAt: demoNow,
    createdAt: demoNow,
    updatedAt: demoNow,
    chapters,
  })

  await page.route('**/api/imports/txt', route => json(route, job(), 201))
  await page.route(`**/api/txt-imports/${importId}/**`, async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path.endsWith('/parse')) {
      status = 'WAITING_CONFIRMATION'; version++
      return json(route, job())
    }
    if (path.includes('/content')) return json(route, { content: '这是服务器返回的当前章节正文预览。', truncated: false })
    if (path.endsWith('/chapters/split')) {
      chapters = [chapter('chapter-1', 1, '自定义序章', 0, 5), chapter('chapter-split', 2, '自定义序章（下）', 5, 18), chapter('chapter-2', 3, '第001章 雾港', 25, 60)]
      version++
      return json(route, job())
    }
    if (path.endsWith('/chapters/merge')) {
      chapters = [chapter('chapter-1', 1, '自定义序章', 0, 18), chapter('chapter-2', 2, '第001章 雾港', 25, 60)]
      version++
      return json(route, job())
    }
    if (path.endsWith('/commit')) {
      status = 'COMPLETED'; project = projectId; version++
      return json(route, job(), 201)
    }
    if (request.method() === 'PATCH') {
      const body = request.postDataJSON() as { title: string; included: boolean }
      chapters[0] = { ...chapters[0]!, title: body.title, included: body.included }
      version++
      return json(route, job())
    }
    return json(route, job())
  })
  await page.route(`**/api/txt-imports/${importId}`, route => json(route, job()))
  await page.route(`**/api/projects/${projectId}/reconstruction/estimate`, route => json(route, {
    mode: 'STANDARD', chapters: 2, chunks: 2, estimatedCalls: 7,
    estimatedInputTokens: 1_200, estimatedOutputTokens: 700,
    estimatedCostMin: 0.01, estimatedCostMax: 0.04, currency: 'CNY', model: 'deepseek-extractor', unpriced: false,
  }))
  await page.route(`**/api/projects/${projectId}/reconstruction`, route => {
    if (route.request().method() === 'POST') reconstructionStatus = 'QUEUED'
    return json(route, {
      id: reconstructionStatus === 'NOT_ANALYZED' ? null : 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
      projectId, mode: 'STANDARD', status: reconstructionStatus, currentStep: reconstructionStatus === 'QUEUED' ? 'PREPROCESSING' : 'NOT_ANALYZED',
      totalChapters: 2, totalChunks: reconstructionStatus === 'QUEUED' ? 2 : 0, processedChunks: 0,
      failedChapters: 0, progress: 0, estimatedCalls: 7, estimatedInputTokens: 1_200,
      estimatedOutputTokens: 700, estimatedCostMin: 0.01, estimatedCostMax: 0.04,
      currency: 'CNY', maxBudget: null, actualInputTokens: 0, actualOutputTokens: 0,
      actualReasoningTokens: 0, actualCost: 0, retryCount: 0, candidateCount: 0,
      pendingCandidates: 0, conflicts: 0, acceptedCandidates: 0, rejectedCandidates: 0,
      errorCode: null, errorMessage: null, startedAt: null, completedAt: null,
    }, route.request().method() === 'POST' ? 202 : 200)
  })

  await loginToDemo(page, '/projects')
  const importEntry = page.getByRole('main').getByRole('link', { name: '导入 TXT 书籍' })
  await expect(importEntry).toBeVisible()
  await importEntry.click()
  await expect(page).toHaveURL(/\/projects\/import\/txt$/)
  await expect(page.getByRole('heading', { name: '导入 TXT 书籍并创建项目' })).toBeVisible()
  await page.locator('input[type="file"]').setInputFiles({
    name: '雾港.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('序章\n正文\n第001章 雾港\n正文'),
  })
  await expect(page).toHaveURL(new RegExp(`/projects/import/txt/${importId}$`))
  await page.getByRole('button', { name: '解析并预览章节' }).click()
  await expect(page.getByText('识别到 2 个候选')).toBeVisible()
  await expect(page.getByText('这是服务器返回的当前章节正文预览。')).toBeVisible()

  const firstTitle = page.getByLabel('章节 1 标题')
  await firstTitle.fill('自定义序章')
  await firstTitle.press('Tab')
  await expect(firstTitle).toHaveValue('自定义序章')

  page.once('dialog', dialog => dialog.accept('5'))
  await page.getByRole('button', { name: '拆分' }).first().click()
  await expect(page.getByLabel('章节 2 标题')).toHaveValue('自定义序章（下）')
  await page.getByRole('button', { name: '合并下章' }).first().click()
  await expect(page.getByLabel('章节 2 标题')).toHaveValue('第001章 雾港')

  await page.getByRole('button', { name: '确认导入并创建项目' }).click()
  await expect(page.getByRole('heading', { name: '导入完成' })).toBeVisible()
  await expect(page.getByRole('link', { name: '打开项目' })).toHaveAttribute('href', `/projects/${projectId}`)
  await expect(page.getByRole('heading', { name: '✨ AI 自动构建完整项目' })).toBeVisible()
  await expect(page.getByText('启动前预估')).toBeVisible()
  await expect(page.getByText('CNY 0.0100 — CNY 0.0400')).toBeVisible()
  await page.getByRole('button', { name: '确认费用并开始分析' }).click()
  await expect(page.getByText('QUEUED')).toBeVisible()
})

function chapter(id: string, sequenceNo: number, title: string, startOffset: number, endOffset: number) {
  return { id, sequenceNo, title, startOffset, endOffset, characterCount: endOffset - startOffset, paragraphCount: 1, included: true }
}
