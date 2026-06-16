import { describe, expect, it, vi } from 'vitest'
import { ApiRequestError } from '../../../shared/lib/http'
import { restoreStoredSession } from './restoreStoredSession'

const user = {
  id: 'user-123',
  email: 'person@example.com',
  displayName: 'Person Example',
  emailVerified: true,
}

const authResponse = {
  token: 'new-access-token',
  refreshToken: 'new-refresh-token',
  user,
}

describe('restoreStoredSession', () => {
  it('returns restored user data when the stored access token is valid', async () => {
    const restoreSession = vi.fn().mockResolvedValue(user)
    const refreshSession = vi.fn()

    await expect(restoreStoredSession('access-token', 'refresh-token', {
      restoreSession,
      refreshSession,
      sessionExpiredMessage: 'Session expired. Sign in again.',
    })).resolves.toEqual({ status: 'restored', user })

    expect(restoreSession).toHaveBeenCalledWith('access-token')
    expect(refreshSession).not.toHaveBeenCalled()
  })

  it('refreshes the session when the stored access token is unauthorized', async () => {
    const restoreSession = vi.fn().mockRejectedValue(new ApiRequestError('Unauthorized', 401))
    const refreshSession = vi.fn().mockResolvedValue(authResponse)

    await expect(restoreStoredSession('expired-access-token', 'refresh-token', {
      restoreSession,
      refreshSession,
      sessionExpiredMessage: 'Session expired. Sign in again.',
    })).resolves.toEqual({ status: 'refreshed', response: authResponse })

    expect(refreshSession).toHaveBeenCalledWith({ refreshToken: 'refresh-token' })
  })

  it('fails restore without refreshing when the restore error is not unauthorized', async () => {
    const restoreSession = vi.fn().mockRejectedValue(new ApiRequestError('Network failed', 0))
    const refreshSession = vi.fn()

    await expect(restoreStoredSession('access-token', 'refresh-token', {
      restoreSession,
      refreshSession,
      sessionExpiredMessage: 'Session expired. Sign in again.',
    })).resolves.toEqual({
      status: 'failed',
      error: 'Session expired. Sign in again.',
    })

    expect(refreshSession).not.toHaveBeenCalled()
  })

  it('fails restore when refresh is unavailable or rejected', async () => {
    const restoreSession = vi.fn().mockRejectedValue(new ApiRequestError('Unauthorized', 401))
    const refreshSession = vi.fn().mockRejectedValue(new ApiRequestError('Invalid refresh', 401))

    await expect(restoreStoredSession('expired-access-token', 'refresh-token', {
      restoreSession,
      refreshSession,
      sessionExpiredMessage: 'Session expired. Sign in again.',
    })).resolves.toEqual({
      status: 'failed',
      error: 'Session expired. Sign in again.',
    })
  })
})
