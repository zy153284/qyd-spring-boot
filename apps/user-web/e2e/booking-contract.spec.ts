import { expect, test } from '@playwright/test'

test.describe('browser contract: venue discovery', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('qyd.tokens', JSON.stringify({
      accessToken: 'browser-contract-token', refreshToken: 'refresh', tokenType: 'Bearer',
    })))
  })

  test('sends bearer token, renders API venues and filters in-browser', async ({ page }) => {
    let authorization = ''
    await page.route('**/api/v1/venues**', async (route) => {
      authorization = route.request().headers().authorization ?? ''
      await route.fulfill({ json: [
        { id: 'v1', name: '奥体羽毛球馆', address: '滨江区', status: 'ACTIVE' },
        { id: 'v2', name: '市民游泳馆', address: '上城区', status: 'ACTIVE' },
      ] })
    })

    await page.goto('/venues')
    await expect(page.getByRole('heading', { name: '场馆' })).toBeVisible()
    await expect(page.getByText('奥体羽毛球馆')).toBeVisible()
    expect(authorization).toBe('Bearer browser-contract-token')

    await page.locator('input[placeholder="按名称或地址筛选"]').fill('游泳')
    await expect(page.getByText('市民游泳馆')).toBeVisible()
    await expect(page.getByText('奥体羽毛球馆')).toBeHidden()
  })

  test('protects booking pages when no session exists', async ({ page }) => {
    await page.addInitScript(() => localStorage.removeItem('qyd.tokens'))
    await page.goto('/checkout')
    await expect(page).toHaveURL(/\/login\?redirect=\/checkout/)
  })
})
