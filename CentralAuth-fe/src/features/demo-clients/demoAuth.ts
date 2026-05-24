import type { DemoClient } from './demoClients'

type DemoStorage = Pick<Storage, 'getItem' | 'removeItem' | 'setItem'>

export function callbackRedirectUri(client: DemoClient, origin = window.location.origin) {
  return `${origin}${client.callbackPath}`
}

export function centralLoginUrl(client: DemoClient, origin: string, state: string) {
  const searchParams = new URLSearchParams({
    client_id: client.clientId,
    redirect_uri: callbackRedirectUri(client, origin),
    state,
  })

  return `/signin?${searchParams.toString()}`
}

export function generateCallbackState() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  return `state-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function storeCallbackState(
  client: DemoClient,
  state: string,
  storage: DemoStorage = window.localStorage,
) {
  storage.setItem(client.stateStorageKey, state)
}

export function readCallbackState(
  client: DemoClient,
  storage: DemoStorage = window.localStorage,
) {
  return storage.getItem(client.stateStorageKey)
}

export function clearCallbackState(
  client: DemoClient,
  storage: DemoStorage = window.localStorage,
) {
  storage.removeItem(client.stateStorageKey)
}

export function validateCallbackState(expectedState: string | null, actualState: string | null) {
  return Boolean(expectedState && actualState && expectedState === actualState)
}

export function storeClientToken(
  client: DemoClient,
  token: string,
  storage: DemoStorage = window.localStorage,
) {
  storage.setItem(client.tokenStorageKey, token)
}

export function readClientToken(
  client: DemoClient,
  storage: DemoStorage = window.localStorage,
) {
  return storage.getItem(client.tokenStorageKey)
}

export function clearClientToken(
  client: DemoClient,
  storage: DemoStorage = window.localStorage,
) {
  storage.removeItem(client.tokenStorageKey)
}
