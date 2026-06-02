import { expect, test } from '@playwright/test'

test.describe('Auth state, protected routes, and feedback', () => {
  test('redirects protected pages to sign in with a clear continuation message', async ({ page }) => {
    await page.goto('/profile')

    await expect(page).toHaveURL(/\/signin$/)
    await expect(page.getByText('Sign in to continue.')).toBeVisible()
  })

  test('shows backend login failure feedback', async ({ page }) => {
    await page.route('**/api/v1/auth/signin', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          message: 'Invalid email or password',
          data: null,
          timestamp: '2026-06-02T00:00:00Z',
        }),
      })
    })

    await page.goto('/signin')
    await page.getByLabel(/^email$/i).fill('wrong@example.com')
    await page.getByLabel(/^password$/i).fill('bad-password')
    await page.getByRole('button', { name: /^sign in$/i }).click()

    await expect(page.getByText('Invalid email or password')).toBeVisible()
  })

  test('shows backend OTP failure feedback', async ({ page }) => {
    await page.route('**/api/v1/auth/verify-email', async (route) => {
      await route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          message: 'Invalid or expired email verification OTP',
          data: null,
          timestamp: '2026-06-02T00:00:00Z',
        }),
      })
    })

    await page.goto('/verify-email?email=person%40example.com')
    await page.getByLabel(/^otp$/i).fill('123456')
    await page.getByRole('button', { name: /^verify email$/i }).click()

    await expect(page.getByText('Invalid or expired email verification OTP')).toBeVisible()
  })

  test('shows verification success feedback after OTP succeeds', async ({ page }) => {
    await page.route('**/api/v1/auth/verify-email', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          message: 'Email verified',
          data: null,
          timestamp: '2026-06-02T00:00:00Z',
        }),
      })
    })

    await page.goto('/verify-email?email=person%40example.com')
    await page.getByLabel(/^otp$/i).fill('123456')
    await page.getByRole('button', { name: /^verify email$/i }).click()

    await expect(page).toHaveURL(/\/signin$/)
    await expect(page.getByText('Email verified. Sign in to continue.')).toBeVisible()
  })
})
