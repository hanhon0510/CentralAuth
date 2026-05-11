import { apiRequest } from '../../../shared/lib/http'
import type { AuthResponse, User } from '../types/auth'

type SigninPayload = {
  email: string
  password: string
}

type SignupPayload = SigninPayload & {
  displayName: string
}

type VerifyEmailPayload = {
  email: string
  otp: string
}

export function restoreSession(token: string) {
  return apiRequest<User>('/api/v1/auth/me', {
    headers: { Authorization: `Bearer ${token}` },
  })
}

export function signin(payload: SigninPayload) {
  return apiRequest<AuthResponse>('/api/v1/auth/signin', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function verifyEmail(payload: VerifyEmailPayload) {
  return apiRequest<void>('/api/v1/auth/verify-email', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function signup(payload: SignupPayload) {
  return apiRequest<AuthResponse>('/api/v1/auth/signup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
