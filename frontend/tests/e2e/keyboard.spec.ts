import { expect, test } from '@playwright/test'

import { installDemoApi, loginToDemo } from './fixtures/demo'

test.beforeEach(async ({ page }) => installDemoApi(page))

test('skip link, route focus, project search shortcut, and dialog work with a keyboard', async ({ page }) => {
  await page.goto('/login')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '跳到主要内容' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.locator('#main-content')).toBeFocused()

  await loginToDemo(page)
  await expect(page.getByRole('heading', { name: '青铜城行动', level: 1 })).toBeFocused()

  await page.keyboard.press('ControlOrMeta+k')
  await expect(page.getByPlaceholder('搜索项目')).toBeFocused()
  await page.keyboard.type('青铜')
  await expect(page.getByRole('link', { name: /青铜城行动/ })).toBeVisible()

  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '编辑项目' }).focus()
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog', { name: '编辑项目' })
  await expect(dialog).toBeVisible()
  await expect.poll(() => dialog.evaluate((element) => element.contains(document.activeElement))).toBe(true)
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(page.getByRole('button', { name: '编辑项目' })).toBeFocused()
})
