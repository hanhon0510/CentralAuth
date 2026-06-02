import type { AuthResponse, User } from '../types/auth'

export type AuthOperation =
  | 'signin'
  | 'centralLogin'
  | 'signup'
  | 'verifyEmail'
  | 'resendVerificationOtp'
  | 'forgotPassword'
  | 'resetPassword'
  | 'signOut'
  | 'signOutAllDevices'

export type AuthSessionState = {
  token: string
  refreshToken: string
  user: User | null
  restoring: boolean
  operation: AuthOperation | null
  sessionError: string
}

type AuthSessionAction =
  | { type: 'restoreStarted' }
  | { type: 'restoreSucceeded'; user: User }
  | { type: 'restoreFailed'; error: string }
  | { type: 'operationStarted'; operation: AuthOperation }
  | { type: 'operationFinished' }
  | { type: 'sessionStored'; response: AuthResponse }
  | { type: 'sessionCleared'; error?: string }

export function createAuthSessionState(token: string, refreshToken: string): AuthSessionState {
  return {
    token,
    refreshToken,
    user: null,
    restoring: Boolean(token),
    operation: null,
    sessionError: '',
  }
}

export function authSessionReducer(
  state: AuthSessionState,
  action: AuthSessionAction,
): AuthSessionState {
  switch (action.type) {
    case 'restoreStarted':
      return { ...state, restoring: true, sessionError: '' }
    case 'restoreSucceeded':
      return {
        ...state,
        user: action.user,
        restoring: false,
        sessionError: '',
      }
    case 'restoreFailed':
      return {
        ...state,
        token: '',
        refreshToken: '',
        user: null,
        restoring: false,
        operation: null,
        sessionError: action.error,
      }
    case 'operationStarted':
      return { ...state, operation: action.operation }
    case 'operationFinished':
      return { ...state, operation: null }
    case 'sessionStored':
      return {
        ...state,
        token: action.response.token,
        refreshToken: action.response.refreshToken,
        user: action.response.user,
        restoring: false,
        operation: null,
        sessionError: '',
      }
    case 'sessionCleared':
      return {
        ...state,
        token: '',
        refreshToken: '',
        user: null,
        restoring: false,
        operation: null,
        sessionError: action.error ?? '',
      }
  }
}
