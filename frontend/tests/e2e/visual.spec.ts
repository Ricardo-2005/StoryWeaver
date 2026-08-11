import { expect, test } from '@playwright/test'

import { demoProjectId, installDemoApi, loginToDemo } from './fixtures/demo'

test.beforeEach(async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await installDemoApi(page)
})

test('project dashboard light theme visual baseline', async ({ page }) => {
  await loginToDemo(page)
  await expect(page).toHaveScreenshot('project-dashboard-light.png', {
    animations: 'disabled',
    fullPage: true,
    maxDiffPixelRatio: 0.02,
  })
})

test('project dashboard dark theme visual baseline', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('storyweaver.theme', 'dark'))
  await loginToDemo(page)
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(page).toHaveScreenshot('project-dashboard-dark.png', {
    animations: 'disabled',
    fullPage: true,
    maxDiffPixelRatio: 0.02,
  })
})

test('workspace and model cost pages visual baselines', async ({ page }) => {
  await loginToDemo(page, `/projects/${demoProjectId}/workspace`)
  await expect(page).toHaveScreenshot('workspace-light.png', {
    animations: 'disabled',
    fullPage: true,
    maxDiffPixelRatio: 0.02,
  })

  await page.getByRole('link', { name: '模型与费用' }).first().click()
  await expect(page.getByRole('heading', { name: '模型与费用', exact: true })).toBeVisible()
  await expect(page.locator('.usage-chart canvas')).toHaveCount(4)
  await expect(page).toHaveScreenshot('observability-light.png', {
    animations: 'disabled',
    fullPage: true,
    maxDiffPixelRatio: 0.02,
  })
})
