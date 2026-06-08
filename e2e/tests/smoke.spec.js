import { test, expect } from '@playwright/test'

test('首页加载并显示三个步骤', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'WS Audio Demo' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '1. 上传 WAV' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '2. 发起处理' })).toBeVisible()
})

test('未上传时处理按钮不可用', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('button', { name: '开始处理' })).toBeDisabled()
})
