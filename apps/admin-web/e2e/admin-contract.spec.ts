import { expect, test } from '@playwright/test'

const envelope = <T>(data: T) => ({ success: true, data, message: 'ok', timestamp: new Date().toISOString() })
const jwt = `x.${Buffer.from(JSON.stringify({
  sub: 'admin-1', username: 'admin', role: 'PLATFORM_ADMIN',
})).toString('base64url')}.x`

test('browser contract: login drives authenticated dashboard API', async ({ page }) => {
  let loginBody: unknown
  let dashboardAuthorization = ''
  await page.route('**/api/v1/auth/login', async (route) => {
    loginBody = route.request().postDataJSON()
    await route.fulfill({ json: envelope({ accessToken: jwt, refreshToken: 'refresh', tokenType: 'Bearer' }) })
  })
  await page.route('**/api/v1/dashboard/statistics', async (route) => {
    dashboardAuthorization = route.request().headers().authorization ?? ''
    await route.fulfill({ json: {
      venues: 3, orders: 12, paidOrders: 10, orderAmount: 1200,
      payments: 10, paidAmount: 1000, refunds: 1, refundAmount: 50,
      settlements: 2, settlementAmount: 950,
    } })
  })

  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill('admin')
  await page.locator('input[autocomplete="current-password"]').fill('safe-test-password')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByRole('heading', { name: '运营概览' })).toBeVisible()
  await expect(page.getByText('12 / 10')).toBeVisible()
  expect(loginBody).toEqual({ username: 'admin', password: 'safe-test-password' })
  expect(dashboardAuthorization).toBe(`Bearer ${jwt}`)
})

test('browser contract: protected route redirects anonymous user', async ({ page }) => {
  await page.goto('/orders')
  await expect(page).toHaveURL(/\/login\?redirect=\/orders/)
})
