import { expect, test } from '@playwright/test'

test.describe('Auth pages', () => {
  test('exposes direct pages for registration, verification, login, and password recovery', async ({
    page,
  }) => {
    await page.goto('/signup')
    await expect(page.getByRole('button', { name: /create account/i })).toBeVisible()
    await expect(page.getByLabel(/display name/i)).toBeVisible()
    await expect(page.getByLabel(/^email$/i)).toBeVisible()
    await expect(page.getByLabel(/^password$/i)).toBeVisible()

    await page.goto('/verify-email?email=person%40example.com')
    await expect(page.getByLabel(/^email$/i)).toHaveValue('person@example.com')
    await expect(page.getByLabel(/^otp$/i)).toBeVisible()
    await expect(page.getByRole('button', { name: /^verify email$/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /^resend otp$/i })).toBeVisible()

    await page.goto('/signin')
    await expect(page.getByRole('button', { name: /^sign in$/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /forgot password/i })).toBeVisible()
    await expect(page.getByLabel(/^email$/i)).toBeVisible()
    await expect(page.getByLabel(/^password$/i)).toBeVisible()
    await page.getByRole('button', { name: /forgot password/i }).click()
    await expect(page).toHaveURL(/\/forgot-password$/)

    await page.goto('/forgot-password')
    await expect(page.getByLabel(/^email$/i)).toBeVisible()
    await expect(page.getByRole('button', { name: /send reset instructions/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /back to sign in/i })).toBeVisible()

    await page.goto('/reset-password?token=reset-token-123')
    await expect(page.getByLabel(/reset token/i)).toHaveValue('reset-token-123')
    await expect(page.getByLabel(/new password/i)).toBeVisible()
    await expect(page.getByRole('button', { name: /^reset password$/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /back to sign in/i })).toBeVisible()
  })
})
