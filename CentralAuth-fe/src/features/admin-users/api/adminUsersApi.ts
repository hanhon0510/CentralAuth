import { apiRequest } from '../../../shared/lib/http'
import type { AccountStatus, AdminUser, AdminUserFilters } from '../types/adminUsers'

export function fetchAdminUsers(token: string, filters: AdminUserFilters) {
  const params = new URLSearchParams()
  params.set('limit', String(filters.limit))

  appendFilter(params, 'email', filters.email)
  appendFilter(params, 'status', filters.status)

  return apiRequest<AdminUser[]>(`/api/v1/admin/users?${params.toString()}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
}

export function updateAdminUserStatus(token: string, userId: string, status: AccountStatus) {
  return apiRequest<AdminUser>(`/api/v1/admin/users/${userId}/status`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ status }),
  })
}

function appendFilter(params: URLSearchParams, key: string, value: string | undefined) {
  const trimmed = value?.trim()
  if (trimmed) {
    params.set(key, trimmed)
  }
}
