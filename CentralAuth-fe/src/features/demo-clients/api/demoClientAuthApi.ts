import { apiRequest } from '../../../shared/lib/http'
import type { User } from '../../auth/types/auth'
import type { DemoClient } from '../demoClients'

export type DemoClientTokenResponse = {
  token: string
  user: User
}

export function exchangeDemoClientCode(client: DemoClient, code: string, redirectUri: string) {
  return apiRequest<DemoClientTokenResponse>('/api/v1/auth/central-login/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      code,
      clientId: client.clientId,
      redirectUri,
    }),
  })
}

export function getDemoClientUser(token: string) {
  return apiRequest<User>('/api/v1/auth/me', {
    headers: { Authorization: `Bearer ${token}` },
  })
}
