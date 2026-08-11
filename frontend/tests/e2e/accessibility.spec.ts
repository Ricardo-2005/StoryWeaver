import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

import { demoProjectId, installDemoApi, loginToDemo } from './fixtures/demo'

const tags = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

async function expectNoAccessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(tags).analyze()
  expect(
    results.violations.map(({ id, impact, nodes }) => ({
      id,
      impact,
      targets: nodes.map((node) => ({ target: node.target, summary: node.failureSummary })),
    })),
  ).toEqual([])
}

test.beforeEach(async ({ page }) => installDemoApi(page))

test('login page meets automated WCAG 2.2 AA checks', async ({ page }) => {
  await page.goto('/login')
  await expectNoAccessibilityViolations(page)
})

test('project dashboard meets automated WCAG 2.2 AA checks in light and dark themes', async ({ page }) => {
  await loginToDemo(page)
  await expectNoAccessibilityViolations(page)

  await page.getByRole('button', { name: '跟随系统' }).click()
  await page.getByRole('button', { name: '浅色' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expectNoAccessibilityViolations(page)
})

test('workspace and observability pages meet automated WCAG checks', async ({ page }) => {
  await loginToDemo(page, `/projects/${demoProjectId}/workspace`)
  await expectNoAccessibilityViolations(page)

  await page.getByRole('link', { name: '模型与费用' }).first().click()
  await expect(page.getByRole('heading', { name: '模型与费用', exact: true })).toBeVisible()
  await expectNoAccessibilityViolations(page)
})
