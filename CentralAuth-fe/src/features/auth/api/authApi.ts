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

type ResendVerificationOtpPayload = {
  email: string
}

export type ResendVerificationOtpResponse = {
  resendCooldownSeconds: number
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

export function resendVerificationOtp(payload: ResendVerificationOtpPayload) {
  return apiRequest<ResendVerificationOtpResponse>('/api/v1/auth/resend-verification-otp', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function logout(token: string, refreshToken: string) {
  return apiRequest<void>('/api/v1/auth/logout', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ refreshToken }),
  })
}

export function logoutAllDevices(token: string) {
  return apiRequest<void>('/api/v1/auth/logout-all-devices', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
}

export function signup(payload: SignupPayload) {
  return apiRequest<AuthResponse>('/api/v1/auth/signup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
