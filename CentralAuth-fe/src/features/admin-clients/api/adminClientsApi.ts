import { apiRequest } from '../../../shared/lib/http'
import type { AdminClient, CreateClientPayload, UpdateClientActivePayload, UpdateClientPayload } from '../types/adminClients'

export function fetchAdminClients(token: string) {
  return apiRequest<AdminClient[]>('/api/v1/admin/clients', {
    headers: { Authorization: `Bearer ${token}` },
  })
}

export function createAdminClient(token: string, payload: CreateClientPayload) {
  return apiRequest<AdminClient>('/api/v1/admin/clients', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

export function updateAdminClient(token: string, clientId: string, payload: UpdateClientPayload) {
  return apiRequest<AdminClient>(`/api/v1/admin/clients/${encodeURIComponent(clientId)}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

export function updateAdminClientActive(token: string, clientId: string, active: boolean) {
  const payload: UpdateClientActivePayload = { active }

  return apiRequest<AdminClient>(`/api/v1/admin/clients/${encodeURIComponent(clientId)}/active`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}
