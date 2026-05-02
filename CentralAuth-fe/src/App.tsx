import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

type AuthMode = 'signin' | 'signup'

type User = {
  id: string
  email: string
  displayName: string | null
  emailVerified: boolean
}

type AuthResponse = {
  token: string
  user: User
}

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  timestamp: string
}

const tokenStorageKey = 'centralauth.token'

function App() {
  const [mode, setMode] = useState<AuthMode>('signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [token, setToken] = useState(() => localStorage.getItem(tokenStorageKey) ?? '')
  const [user, setUser] = useState<User | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [restoring, setRestoring] = useState(Boolean(token))

  const tokenPreview = useMemo(() => {
    if (!token) return ''
    if (token.length <= 28) return token
    return `${token.slice(0, 16)}...${token.slice(-10)}`
  }, [token])

  useEffect(() => {
    if (!token) {
      setRestoring(false)
      return
    }

    let cancelled = false
    async function restoreSession() {
      setRestoring(true)
      try {
        const currentUser = await apiRequest<User>('/api/v1/auth/me', {
          headers: { Authorization: `Bearer ${token}` },
        })
        if (!cancelled) {
          setUser(currentUser)
          setError('')
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

    restoreSession()
    return () => {
      cancelled = true
    }
  }, [token])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setLoading(true)

    try {
      const response = await apiRequest<AuthResponse>(
        mode === 'signup' ? '/api/v1/auth/signup' : '/api/v1/auth/signin',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email,
            password,
            ...(mode === 'signup' ? { displayName } : {}),
          }),
        },
      )
      localStorage.setItem(tokenStorageKey, response.token)
      setToken(response.token)
      setUser(response.user)
      setPassword('')
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Request failed')
    } finally {
      setLoading(false)
    }
  }

  function clearSession() {
    localStorage.removeItem(tokenStorageKey)
    setToken('')
    setUser(null)
  }

  function switchMode(nextMode: AuthMode) {
    setMode(nextMode)
    setError('')
  }

  return (
    <main className="auth-shell">
      <section className="auth-panel" aria-label="CentralAuth email authentication">
        <div className="brand-strip">
          <div>
            <p className="eyebrow">CentralAuth</p>
            <h1>Email access</h1>
          </div>
          <span className={user ? 'status-pill active' : 'status-pill'}>
            {user ? 'Signed in' : 'Guest'}
          </span>
        </div>

        {user ? (
          <section className="session-view" aria-label="Current session">
            <div>
              <p className="eyebrow">Current user</p>
              <h2>{user.displayName || user.email}</h2>
              <p className="muted">{user.email}</p>
            </div>

            <dl className="session-details">
              <div>
                <dt>User ID</dt>
                <dd>{user.id}</dd>
              </div>
              <div>
                <dt>Email verified</dt>
                <dd>{user.emailVerified ? 'Yes' : 'No'}</dd>
              </div>
              <div>
                <dt>Token</dt>
                <dd>{tokenPreview}</dd>
              </div>
            </dl>

            <button type="button" className="secondary-button" onClick={clearSession}>
              Sign out
            </button>
          </section>
        ) : (
          <>
            <div className="mode-tabs" role="tablist" aria-label="Authentication mode">
              <button
                type="button"
                role="tab"
                aria-selected={mode === 'signin'}
                className={mode === 'signin' ? 'tab active' : 'tab'}
                onClick={() => switchMode('signin')}
              >
                Sign in
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={mode === 'signup'}
                className={mode === 'signup' ? 'tab active' : 'tab'}
                onClick={() => switchMode('signup')}
              >
                Sign up
              </button>
            </div>

            <form className="auth-form" onSubmit={handleSubmit}>
              {mode === 'signup' && (
                <label>
                  Display name
                  <input
                    type="text"
                    value={displayName}
                    maxLength={120}
                    autoComplete="name"
                    onChange={(event) => setDisplayName(event.target.value)}
                  />
                </label>
              )}

              <label>
                Email
                <input
                  type="email"
                  value={email}
                  maxLength={320}
                  autoComplete="email"
                  required
                  onChange={(event) => setEmail(event.target.value)}
                />
              </label>

              <label>
                Password
                <input
                  type="password"
                  value={password}
                  minLength={mode === 'signup' ? 8 : 1}
                  maxLength={120}
                  autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                  required
                  onChange={(event) => setPassword(event.target.value)}
                />
              </label>

              {error && <p className="error-message">{error}</p>}

              <button type="submit" className="primary-button" disabled={loading || restoring}>
                {loading ? 'Working...' : mode === 'signup' ? 'Create account' : 'Sign in'}
              </button>
            </form>
          </>
        )}
      </section>
    </main>
  )
}

async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null

  if (!response.ok) {
    throw new Error(payload?.message ?? 'Request failed')
  }

  if (!payload?.success) {
    throw new Error(payload?.message ?? 'Request failed')
  }

  return payload.data
}

export default App
