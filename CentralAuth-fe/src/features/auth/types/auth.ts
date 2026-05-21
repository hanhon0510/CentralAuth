export type AuthMode = 'signin' | 'signup'

export type User = {
  id: string
  email: string
  displayName: string | null
  emailVerified: boolean
}

export type AuthResponse = {
  token: string
  refreshToken: string
  user: User
}

export type CentralLoginContext = {
  clientId: string
  clientName: string
  redirectUri: string
  state: string | null
}

export type CentralLoginRequestContext = {
  clientId: string
  redirectUri: string
  state?: string | null
}

export type CentralLoginRedirectResponse = {
  redirectUri: string
  code: string
  state: string | null
}

export type CentralLoginResponse = CentralLoginRedirectResponse & {
  auth: AuthResponse
}
