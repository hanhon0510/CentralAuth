import { expect, test } from '@playwright/test'

const email = process.env.E2E_EMAIL
const password = process.env.E2E_PASSWORD

test.describe('Phase 6 demo SSO flow', () => {
  test.skip(!email || !password, 'Set E2E_EMAIL and E2E_PASSWORD for an active verified user.')

  test('projects login, reports SSO continuation, and central logout propagation', async ({ page }) => {
    await page.goto('/demo/projects')
    await page.getByRole('button', { name: /login with centralauth/i }).click()
    await page.getByLabel(/email/i).fill(email!)
    await page.getByLabel(/password/i).fill(password!)
    await page.getByRole('button', { name: /^sign in$/i }).click()

    await expect(page).toHaveURL(/\/demo\/projects\/protected/)
    await expect(page.getByText(new RegExp(`Authenticated as ${escapeRegExp(email!)}`, 'i'))).toBeVisible()

    await page.goto('/demo/reports')
    await page.getByRole('button', { name: /login with centralauth/i }).click()

    await expect(page).toHaveURL(/\/demo\/reports\/protected/)
    await expect(page.getByText(new RegExp(`Authenticated as ${escapeRegExp(email!)}`, 'i'))).toBeVisible()

    await page.goto('/dashboard')
    await page.getByRole('button', { name: /sign out all devices/i }).click()

    await expect.poll(
      async () => page.evaluate(() => ({
        projectsToken: localStorage.getItem('centralauth.demo.projects.token'),
        reportsToken: localStorage.getItem('centralauth.demo.reports.token'),
      })),
    ).toEqual({
      projectsToken: null,
      reportsToken: null,
    })
  })
})

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
