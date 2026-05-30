import { expect, test } from '@playwright/test'

const accessToken = 'header.eyJyb2xlcyI6WyJST0xFX1VTRVIiXX0.signature'

test.describe('Profile page', () => {
  test('shows the current authenticated user details', async ({ page }) => {
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          message: 'OK',
          data: {
            id: 'user-123',
            email: 'ava@example.com',
            displayName: 'Ava Nguyen',
            emailVerified: true,
          },
          timestamp: '2026-05-30T00:00:00Z',
        }),
      })
    })

    await page.addInitScript((token) => {
      window.localStorage.setItem('centralauth.token', token)
      window.localStorage.setItem('centralauth.refreshToken', 'refresh-token')
    }, accessToken)

    await page.goto('/profile')

    await expect(page).toHaveURL(/\/profile$/)
    await expect(page.getByText('CentralAuth Profile')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Ava Nguyen' })).toBeVisible()
    await expect(page.getByText('ava@example.com').first()).toBeVisible()
    await expect(page.getByText('user-123')).toBeVisible()
    await expect(page.getByText('ROLE_USER')).toBeVisible()
  })
})
