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
  loginState: string
}

export type CentralLoginRequestContext = {
  clientId: string
  redirectUri: string
  state?: string | null
  loginState?: string | null
}

export type CentralLoginRedirectResponse = {
  redirectUri: string
  code: string
  state: string | null
  redirectUrl: string
}

export type CentralLoginResponse = CentralLoginRedirectResponse
