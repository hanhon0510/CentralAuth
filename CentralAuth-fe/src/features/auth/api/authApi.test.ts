import { afterEach, describe, expect, it, vi } from 'vitest'
import { refreshSession } from './authApi'

describe('authApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts the refresh token to the refresh endpoint', async () => {
    const response = {
      token: 'new-access-token',
      refreshToken: 'new-refresh-token',
      user: {
        id: 'user-123',
        email: 'person@example.com',
        displayName: 'Person Example',
        emailVerified: true,
      },
    }
    const fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        success: true,
        message: 'Session refreshed',
        data: response,
        timestamp: '2026-06-16T00:00:00Z',
      })),
    )
    vi.stubGlobal('fetch', fetch)

    await expect(refreshSession({ refreshToken: 'old-refresh-token' })).resolves.toEqual(response)
    expect(fetch).toHaveBeenCalledWith('/api/v1/auth/refresh', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ refreshToken: 'old-refresh-token' }),
    }))
    expect(Object.fromEntries((fetch.mock.calls[0]?.[1]?.headers as Headers).entries()))
      .toMatchObject({
        'accept-language': 'en',
        'content-type': 'application/json',
      })
  })
})
