import { useCallback, useEffect, useMemo, useReducer } from 'react'
import type { PropsWithChildren } from 'react'
import {
  centralLogin,
  forgotPassword as requestPasswordResetApi,
  logout,
  logoutAllDevices,
  resendVerificationOtp as requestVerificationOtpResend,
  resetPassword as resetPasswordApi,
  refreshSession,
  restoreSession,
  signin,
  signup,
  verifyEmail,
} from '../api/authApi'
import { refreshTokenStorageKey, tokenStorageKey } from '../../../shared/constants/storage'
import type { AuthResponse, CentralLoginRequestContext } from '../types/auth'
import { AuthSessionStore } from './auth-session-store'
import { refreshDelayMillisecondsFromJwt, rolesFromJwt } from '../../../shared/lib/jwt'
import { propagateFrontChannelLogout } from '../lib/frontChannelLogout'
import {
  authSessionReducer,
  createAuthSessionState,
  type AuthOperation,
} from './authSessionReducer'
import { getCurrentLanguage } from '../../../shared/i18n/language'
import { translate } from '../../../shared/i18n/messages'
import { restoreStoredSession } from './restoreStoredSession'

export function AuthSessionProvider({ children }: PropsWithChildren) {
  const [state, dispatch] = useReducer(authSessionReducer, undefined, () =>
    createAuthSessionState(
      localStorage.getItem(tokenStorageKey) ?? '',
      localStorage.getItem(refreshTokenStorageKey) ?? '',
    ),
  )
  const {
    operation,
    refreshToken,
    restoring,
    sessionError,
    token,
    user,
  } = state
  const loading = Boolean(operation)

  const storeSession = useCallback((response: AuthResponse) => {
    localStorage.setItem(tokenStorageKey, response.token)
    localStorage.setItem(refreshTokenStorageKey, response.refreshToken)
    dispatch({ type: 'sessionStored', response })
  }, [])

  const clearSession = useCallback((error?: string) => {
    localStorage.removeItem(tokenStorageKey)
    localStorage.removeItem(refreshTokenStorageKey)
    dispatch({ type: 'sessionCleared', error })
  }, [])

  useEffect(() => {
    if (!token || user) return

    let cancelled = false
    async function fetchCurrentUser() {
      dispatch({ type: 'restoreStarted' })
      const result = await restoreStoredSession(token, refreshToken, {
        restoreSession,
        refreshSession,
        sessionExpiredMessage: translate(getCurrentLanguage(), 'auth.sessionExpired'),
      })
      if (cancelled) {
        return
      }

      if (result.status === 'restored') {
        dispatch({ type: 'restoreSucceeded', user: result.user })
        return
      }

      if (result.status === 'refreshed') {
        storeSession(result.response)
        return
      }

      localStorage.removeItem(tokenStorageKey)
      localStorage.removeItem(refreshTokenStorageKey)
      dispatch({ type: 'restoreFailed', error: result.error })
    }

    fetchCurrentUser()
    return () => {
      cancelled = true
    }
  }, [refreshToken, storeSession, token, user])

  useEffect(() => {
    if (!token || !refreshToken.trim()) return
    if (operation === 'signOut' || operation === 'signOutAllDevices') return

    const refreshDelayMilliseconds = refreshDelayMillisecondsFromJwt(token)
    if (refreshDelayMilliseconds === null) return

    let cancelled = false
    const timeoutId = window.setTimeout(() => {
      void refreshCurrentSession()
    }, refreshDelayMilliseconds)

    async function refreshCurrentSession() {
      try {
        const response = await refreshSession({ refreshToken })
        if (!cancelled) {
          storeSession(response)
        }
      } catch {
        if (!cancelled) {
          clearSession(translate(getCurrentLanguage(), 'auth.sessionExpired'))
        }
      }
    }

    return () => {
      cancelled = true
      window.clearTimeout(timeoutId)
    }
  }, [clearSession, operation, refreshToken, storeSession, token])

  const tokenPreview = useMemo(() => {
    if (!token) return ''
    if (token.length <= 28) return token
    return `${token.slice(0, 16)}...${token.slice(-10)}`
  }, [token])

  const roles = useMemo(() => rolesFromJwt(token), [token])
  const isAdmin = roles.includes('ROLE_ADMIN')

  async function signinWithPassword(email: string, password: string) {
    return runOperation('signin', async () => {
      const response = await signin({ email, password })
      storeSession(response)
    })
  }

  async function signinWithCentralLogin(
    email: string,
    password: string,
    context: CentralLoginRequestContext,
  ) {
    return runOperation('centralLogin', async () => {
      const response = await centralLogin({
        email,
        password,
        clientId: context.clientId,
        redirectUri: context.redirectUri,
        state: context.state ?? undefined,
        loginState: context.loginState ?? undefined,
      })
      storeSession(response.auth)
      return response
    })
  }

  async function signupWithPassword(email: string, password: string, displayName: string) {
    return runOperation('signup', async () => {
      const response = await signup({ email, password, displayName })
      return response.user
    })
  }

  async function verifyEmailWithOtp(email: string, otp: string) {
    return runOperation('verifyEmail', async () => {
      await verifyEmail({ email, otp })
    })
  }

  async function resendVerificationOtp(email: string) {
    return runOperation('resendVerificationOtp', async () => {
      const response = await requestVerificationOtpResend({ email })
      return response.resendCooldownSeconds
    })
  }

  async function requestPasswordReset(email: string) {
    return runOperation('forgotPassword', async () => {
      await requestPasswordResetApi({ email })
    })
  }

  async function resetPasswordWithToken(token: string, newPassword: string) {
    return runOperation('resetPassword', async () => {
      await resetPasswordApi({ token, newPassword })
    })
  }

  async function signOut() {
    if (!token || !refreshToken) {
      clearSession()
      return
    }

    return runOperation('signOut', async () => {
      try {
        const response = await logout(token, refreshToken)
        propagateFrontChannelLogout(response.logoutUris)
      } finally {
        clearSession()
      }
    })
  }

  async function signOutAllDevices() {
    if (!token) {
      clearSession()
      return
    }

    return runOperation('signOutAllDevices', async () => {
      try {
        const response = await logoutAllDevices(token)
        propagateFrontChannelLogout(response.logoutUris)
      } finally {
        clearSession()
      }
    })
  }

  async function runOperation<T>(authOperation: AuthOperation, action: () => Promise<T>) {
    dispatch({ type: 'operationStarted', operation: authOperation })
    try {
      return await action()
    } finally {
      dispatch({ type: 'operationFinished' })
    }
  }

  const value = {
    isAdmin,
    loading,
    operation,
    roles,
    restoring,
    sessionError,
    token,
    tokenPreview,
    user,
    signinWithPassword,
    signinWithCentralLogin,
    signupWithPassword,
    verifyEmailWithOtp,
    resendVerificationOtp,
    requestPasswordReset,
    resetPasswordWithToken,
    signOut,
    signOutAllDevices,
    clearSession,
  }

  return <AuthSessionStore.Provider value={value}>{children}</AuthSessionStore.Provider>
}
