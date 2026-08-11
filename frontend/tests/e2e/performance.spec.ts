import { expect, test } from '@playwright/test'

import { demoProjectId, installDemoApi, loginToDemo } from './fixtures/demo'

test.describe.configure({ mode: 'serial' })
test.beforeEach(async ({ page }) => installDemoApi(page))

test('login LCP and authenticated workspace readiness stay within design targets', async ({ page }, testInfo) => {
  await page.addInitScript(() => {
    const metrics = window as typeof window & { __storyweaverLcp?: number }
    metrics.__storyweaverLcp = 0
    new PerformanceObserver((list) => {
      const lastEntry = list.getEntries().at(-1)
      if (lastEntry) metrics.__storyweaverLcp = lastEntry.startTime
    }).observe({ type: 'largest-contentful-paint', buffered: true })
  })
  await page.goto('/login')
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(100)
  const loginLcp = await page.evaluate(() =>
    (window as typeof window & { __storyweaverLcp?: number }).__storyweaverLcp ?? 0,
  )

  const workspaceStart = Date.now()
  await loginToDemo(page, `/projects/${demoProjectId}/workspace`)
  await expect(page.getByRole('heading', { name: '创作工作台' })).toBeVisible()
  const workspaceReady = Date.now() - workspaceStart

  await testInfo.attach('performance.json', {
    body: JSON.stringify({ loginLcpMillis: loginLcp, workspaceReadyMillis: workspaceReady }, null, 2),
    contentType: 'application/json',
  })
  console.log(`PERFORMANCE_METRICS loginLcp=${loginLcp.toFixed(1)}ms workspaceReady=${workspaceReady}ms`)

  expect(loginLcp).toBeGreaterThan(0)
  expect(loginLcp).toBeLessThan(2500)
  expect(workspaceReady).toBeLessThan(3500)
})
