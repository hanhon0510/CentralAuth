export type AuditLog = {
  id: string
  eventType: string
  userId: string | null
  email: string | null
  clientIp: string | null
  reason: string | null
  occurredAt: string
  consumedAt: string
  kafkaTopic: string | null
  kafkaKey: string | null
  payloadJson: string
}

export type AuditLogFilters = {
  email?: string
  eventType?: string
  limit: number
  userId?: string
}
