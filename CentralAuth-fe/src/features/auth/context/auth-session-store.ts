import { createContext } from 'react'
import type { CentralLoginRedirectResponse, CentralLoginRequestContext, User } from '../types/auth'

export type AuthSessionContextValue = {
  isAdmin: boolean
  loading: boolean
  roles: string[]
  restoring: boolean
  token: string
  tokenPreview: string
  user: User | null
  signinWithPassword: (email: string, password: string) => Promise<void>
  signinWithCentralLogin: (
    email: string,
    password: string,
    context: CentralLoginRequestContext,
  ) => Promise<CentralLoginRedirectResponse>
  signupWithPassword: (email: string, password: string, displayName: string) => Promise<User>
  verifyEmailWithOtp: (email: string, otp: string) => Promise<void>
  resendVerificationOtp: (email: string) => Promise<number>
  requestPasswordReset: (email: string) => Promise<void>
  resetPasswordWithToken: (token: string, newPassword: string) => Promise<void>
  signOut: () => Promise<void>
  signOutAllDevices: () => Promise<void>
  clearSession: () => void
}

export const AuthSessionStore = createContext<AuthSessionContextValue | null>(null)
