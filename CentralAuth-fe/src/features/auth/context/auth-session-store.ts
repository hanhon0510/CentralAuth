import { createContext } from 'react'
import type { User } from '../types/auth'

export type AuthSessionContextValue = {
  loading: boolean
  restoring: boolean
  token: string
  tokenPreview: string
  user: User | null
  signinWithPassword: (email: string, password: string) => Promise<void>
  signupWithPassword: (email: string, password: string, displayName: string) => Promise<User>
  verifyEmailWithOtp: (email: string, otp: string) => Promise<void>
  resendVerificationOtp: (email: string) => Promise<number>
  clearSession: () => void
}

export const AuthSessionStore = createContext<AuthSessionContextValue | null>(null)
