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
