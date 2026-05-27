export type AdminClient = {
  clientId: string
  clientName: string
  redirectUris: string[]
  allowedOrigins: string[]
  logoutUris: string[]
  active: boolean
  createdAt: string
  updatedAt: string
}

export type CreateClientPayload = {
  clientId: string
  clientName: string
  redirectUris: string[]
  allowedOrigins: string[]
  logoutUris: string[]
  active: boolean
}

export type UpdateClientPayload = {
  clientName: string
  redirectUris: string[]
  allowedOrigins: string[]
  logoutUris: string[]
  active: boolean
}

export type UpdateClientActivePayload = {
  active: boolean
}
