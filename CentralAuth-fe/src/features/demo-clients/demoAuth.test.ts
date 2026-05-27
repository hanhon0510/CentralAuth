import { describe, expect, it } from 'vitest'
import { demoClients } from './demoClients'
import {
  callbackRedirectUri,
  centralLoginUrl,
  clearClientSession,
  validateCallbackState,
} from './demoAuth'

describe('demo auth helpers', () => {
  it('builds callback redirect URIs from the current origin', () => {
    expect(callbackRedirectUri(demoClients.projects, 'http://localhost:5173')).toBe(
      'http://localhost:5173/demo/projects/callback',
    )
    expect(callbackRedirectUri(demoClients.reports, 'http://127.0.0.1:5174')).toBe(
      'http://127.0.0.1:5174/demo/reports/callback',
    )
  })

  it('builds the CentralAuth login URL with client metadata and state', () => {
    const url = new URL(
      centralLoginUrl(demoClients.projects, 'http://localhost:5173', 'state-123'),
      'http://localhost:5173',
    )

    expect(url.pathname).toBe('/signin')
    expect(url.searchParams.get('client_id')).toBe('projects-demo')
    expect(url.searchParams.get('redirect_uri')).toBe(
      'http://localhost:5173/demo/projects/callback',
    )
    expect(url.searchParams.get('state')).toBe('state-123')
  })

  it('validates callback state values before token exchange', () => {
    expect(validateCallbackState('state-123', 'state-123')).toBe(true)
    expect(validateCallbackState('state-123', 'state-456')).toBe(false)
    expect(validateCallbackState('', 'state-123')).toBe(false)
    expect(validateCallbackState('state-123', null)).toBe(false)
  })

  it('clears all client-side demo session storage', () => {
    const storage = new Map<string, string>()
    const demoStorage = {
      getItem: (key: string) => storage.get(key) ?? null,
      removeItem: (key: string) => storage.delete(key),
      setItem: (key: string, value: string) => storage.set(key, value),
    }
    demoStorage.setItem(demoClients.projects.tokenStorageKey, 'token')
    demoStorage.setItem(demoClients.projects.stateStorageKey, 'state')

    clearClientSession(demoClients.projects, demoStorage)

    expect(demoStorage.getItem(demoClients.projects.tokenStorageKey)).toBeNull()
    expect(demoStorage.getItem(demoClients.projects.stateStorageKey)).toBeNull()
  })
})
