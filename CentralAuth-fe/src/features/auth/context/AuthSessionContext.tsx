import { useEffect, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'
import {
  forgotPassword as requestPasswordResetApi,
  logout,
  logoutAllDevices,
  resendVerificationOtp as requestVerificationOtpResend,
  resetPassword as resetPasswordApi,
  restoreSession,
  signin,
  signup,
  verifyEmail,
} from '../api/authApi'
import { refreshTokenStorageKey, tokenStorageKey } from '../../../shared/constants/storage'
import type { AuthResponse, User } from '../types/auth'
import { AuthSessionStore } from './auth-session-store'

export function AuthSessionProvider({ children }: PropsWithChildren) {
  const [loading, setLoading] = useState(false)
  const [token, setToken] = useState(() => localStorage.getItem(tokenStorageKey) ?? '')
  const [refreshToken, setRefreshToken] = useState(() => localStorage.getItem(refreshTokenStorageKey) ?? '')
  const [user, setUser] = useState<User | null>(null)
  const [restoring, setRestoring] = useState(Boolean(token))

  useEffect(() => {
    if (!token) return

    let cancelled = false
    async function fetchCurrentUser() {
      setRestoring(true)
      try {
        const currentUser = await restoreSession(token)
        if (!cancelled) {
          setUser(currentUser)
        }
      } catch {
        if (!cancelled) {
          clearSession()
        }
      } finally {
        if (!cancelled) {
          setRestoring(false)
        }
      }
    }

    fetchCurrentUser()
    return () => {
      cancelled = true
    }
  }, [token])

  const tokenPreview = useMemo(() => {
    if (!token) return ''
    if (token.length <= 28) return token
    return `${token.slice(0, 16)}...${token.slice(-10)}`
  }, [token])

  async function signinWithPassword(email: string, password: string) {
    setLoading(true)
    try {
      const response = await signin({ email, password })
      storeSession(response)
    } finally {
      setLoading(false)
    }
  }

  async function signupWithPassword(email: string, password: string, displayName: string) {
    setLoading(true)
    try {
      const response = await signup({ email, password, displayName })
      return response.user
    } finally {
      setLoading(false)
    }
  }

  async function verifyEmailWithOtp(email: string, otp: string) {
    setLoading(true)
    try {
      await verifyEmail({ email, otp })
    } finally {
      setLoading(false)
    }
  }

  async function resendVerificationOtp(email: string) {
    setLoading(true)
    try {
      const response = await requestVerificationOtpResend({ email })
      return response.resendCooldownSeconds
    } finally {
      setLoading(false)
    }
  }

  async function requestPasswordReset(email: string) {
    setLoading(true)
    try {
      await requestPasswordResetApi({ email })
    } finally {
      setLoading(false)
    }
  }

  async function resetPasswordWithToken(token: string, newPassword: string) {
    setLoading(true)
    try {
      await resetPasswordApi({ token, newPassword })
    } finally {
      setLoading(false)
    }
  }

  async function signOut() {
    if (!token || !refreshToken) {
      clearSession()
      return
    }

    setLoading(true)
    try {
      await logout(token, refreshToken)
    } finally {
      clearSession()
      setLoading(false)
    }
  }

  async function signOutAllDevices() {
    if (!token) {
      clearSession()
      return
    }

    setLoading(true)
    try {
      await logoutAllDevices(token)
    } finally {
      clearSession()
      setLoading(false)
    }
  }

  function storeSession(response: AuthResponse) {
    localStorage.setItem(tokenStorageKey, response.token)
    localStorage.setItem(refreshTokenStorageKey, response.refreshToken)
    setToken(response.token)
    setRefreshToken(response.refreshToken)
    setUser(response.user)
  }

  function clearSession() {
    localStorage.removeItem(tokenStorageKey)
    localStorage.removeItem(refreshTokenStorageKey)
    setToken('')
    setRefreshToken('')
    setUser(null)
    setRestoring(false)
  }

  const value = {
    loading,
    restoring,
    token,
    tokenPreview,
    user,
    signinWithPassword,
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
