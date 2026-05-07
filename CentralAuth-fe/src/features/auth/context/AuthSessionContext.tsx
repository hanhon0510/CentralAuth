import { useEffect, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'
import { restoreSession, signin, signup } from '../api/authApi'
import { tokenStorageKey } from '../../../shared/constants/storage'
import type { User } from '../types/auth'
import { AuthSessionStore } from './auth-session-store'

export function AuthSessionProvider({ children }: PropsWithChildren) {
  const [loading, setLoading] = useState(false)
  const [token, setToken] = useState(() => localStorage.getItem(tokenStorageKey) ?? '')
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
      localStorage.setItem(tokenStorageKey, response.token)
      setToken(response.token)
      setUser(response.user)
    } finally {
      setLoading(false)
    }
  }

  async function signupWithPassword(email: string, password: string, displayName: string) {
    setLoading(true)
    try {
      const response = await signup({ email, password, displayName })
      localStorage.setItem(tokenStorageKey, response.token)
      setToken(response.token)
      setUser(response.user)
    } finally {
      setLoading(false)
    }
  }

  function clearSession() {
    localStorage.removeItem(tokenStorageKey)
    setToken('')
    setUser(null)
    setRestoring(false)
  }

  const value = useMemo(
    () => ({
      loading,
      restoring,
      token,
      tokenPreview,
      user,
      signinWithPassword,
      signupWithPassword,
      clearSession,
    }),
    [loading, restoring, token, tokenPreview, user],
  )

  return <AuthSessionStore.Provider value={value}>{children}</AuthSessionStore.Provider>
}
