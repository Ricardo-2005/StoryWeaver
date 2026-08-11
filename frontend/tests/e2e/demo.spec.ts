import { expect, test } from '@playwright/test'

import { installDemoApi, loginToDemo } from './fixtures/demo'

test('repeatable supported three-minute demo route', async ({ page }) => {
  await installDemoApi(page)

  await test.step('登录并查看项目真实资产', async () => {
    await loginToDemo(page)
    await expect(page.getByText('保持知识边界清晰，悬念逐层推进。')).toBeVisible()
    await expect(page.getByRole('heading', { name: '青铜城', level: 3 })).toBeVisible()
  })

  await test.step('进入创作工作台并创建本地 Writing Block', async () => {
    await page.getByRole('link', { name: '创作工作台' }).first().click()
    await page.getByRole('button', { name: '新建本地 Writing Block' }).click()
    await expect(page.getByText('未命名片段', { exact: true }).first()).toBeVisible()
    await expect(page.getByLabel('Writing Block 内容')).toBeVisible()
  })

  await test.step('检查世界书配置', async () => {
    await page.getByRole('link', { name: '世界书' }).first().click()
    await expect(page.getByRole('heading', { name: '青铜城机关' })).toBeVisible()
  })

  await test.step('查看后端 Usage、费用和模型能力', async () => {
    await page.getByRole('link', { name: '模型与费用' }).first().click()
    await expect(page.getByRole('heading', { name: '模型与费用', exact: true })).toBeVisible()
    await expect(page.getByLabel('用量摘要').getByText('5,000', { exact: true })).toBeVisible()
    await expect(page.locator('.usage-chart canvas')).toHaveCount(4)
  })
})
