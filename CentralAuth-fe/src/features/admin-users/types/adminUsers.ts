export const accountStatuses = ['ACTIVE', 'DISABLED', 'LOCKED', 'UNVERIFIED'] as const

export type AccountStatus = (typeof accountStatuses)[number]

export type AdminUser = {
  id: string
  email: string
  displayName: string | null
  enabled: boolean
  emailVerified: boolean
  accountStatus: AccountStatus
  roles: string[]
  createdAt: string
  updatedAt: string
}

export type AdminUserFilters = {
  email?: string
  limit: number
  status?: AccountStatus
}
