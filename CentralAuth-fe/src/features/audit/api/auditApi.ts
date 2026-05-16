import { apiRequest } from '../../../shared/lib/http'
import type { AuditLog, AuditLogFilters } from '../types/audit'

export function fetchAuditLogs(token: string, filters: AuditLogFilters) {
  const params = new URLSearchParams()
  params.set('limit', String(filters.limit))

  appendFilter(params, 'eventType', filters.eventType)
  appendFilter(params, 'email', filters.email)
  appendFilter(params, 'userId', filters.userId)

  return apiRequest<AuditLog[]>(`/api/v1/audit-logs?${params.toString()}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
}

function appendFilter(params: URLSearchParams, key: string, value: string | undefined) {
  const trimmed = value?.trim()
  if (trimmed) {
    params.set(key, trimmed)
  }
}
