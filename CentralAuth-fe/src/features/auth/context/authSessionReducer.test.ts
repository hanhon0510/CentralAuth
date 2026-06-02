import { describe, expect, it } from 'vitest'
import {
  authSessionReducer,
  createAuthSessionState,
} from './authSessionReducer'

const user = {
  id: 'user-123',
  email: 'person@example.com',
  displayName: 'Person Example',
  emailVerified: true,
}

describe('authSessionReducer', () => {
  it('starts restoring when an access token exists', () => {
    const state = createAuthSessionState('access-token', 'refresh-token')

    expect(state).toEqual({
      token: 'access-token',
      refreshToken: 'refresh-token',
      user: null,
      restoring: true,
      operation: null,
      sessionError: '',
    })
  })

  it('stores authenticated user data after session restore succeeds', () => {
    const state = authSessionReducer(createAuthSessionState('access-token', 'refresh-token'), {
      type: 'restoreSucceeded',
      user,
    })

    expect(state).toMatchObject({
      token: 'access-token',
      refreshToken: 'refresh-token',
      user,
      restoring: false,
      sessionError: '',
    })
  })

  it('clears stale credentials and records the restore error after restore fails', () => {
    const state = authSessionReducer(createAuthSessionState('stale-token', 'stale-refresh'), {
      type: 'restoreFailed',
      error: 'Session expired. Sign in again.',
    })

    expect(state).toEqual({
      token: '',
      refreshToken: '',
      user: null,
      restoring: false,
      operation: null,
      sessionError: 'Session expired. Sign in again.',
    })
  })

  it('tracks the active auth operation separately from restore state', () => {
    const signingIn = authSessionReducer(createAuthSessionState('', ''), {
      type: 'operationStarted',
      operation: 'signin',
    })

    expect(signingIn.operation).toBe('signin')

    const settled = authSessionReducer(signingIn, { type: 'operationFinished' })

    expect(settled.operation).toBeNull()
  })
})
