import { ApiRequestError } from '../../../shared/lib/http'
import type { AuthResponse, RefreshPayload, User } from '../types/auth'

type RestoreStoredSessionOptions = {
  restoreSession: (token: string) => Promise<User>
  refreshSession: (payload: RefreshPayload) => Promise<AuthResponse>
  sessionExpiredMessage: string
}

type RestoreStoredSessionResult =
  | { status: 'restored'; user: User }
  | { status: 'refreshed'; response: AuthResponse }
  | { status: 'failed'; error: string }

export async function restoreStoredSession(
  token: string,
  refreshToken: string,
  {
    restoreSession: restoreStoredAccessToken,
    refreshSession: refreshStoredSession,
    sessionExpiredMessage,
  }: RestoreStoredSessionOptions,
): Promise<RestoreStoredSessionResult> {
  try {
    return {
      status: 'restored',
      user: await restoreStoredAccessToken(token),
    }
  } catch (error) {
    if (!(error instanceof ApiRequestError) || error.status !== 401 || !refreshToken.trim()) {
      return { status: 'failed', error: sessionExpiredMessage }
    }
  }

  try {
    return {
      status: 'refreshed',
      response: await refreshStoredSession({ refreshToken }),
    }
  } catch {
    return { status: 'failed', error: sessionExpiredMessage }
  }
}
