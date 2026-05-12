import { getCurrentLanguage } from '../i18n/language'
import { translate } from '../i18n/messages'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  timestamp: string
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly retryAfterSeconds?: number

  constructor(message: string, status: number, retryAfterSeconds?: number) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.retryAfterSeconds = retryAfterSeconds
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, withLanguageHeader(init))
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null
  const fallback = translate(getCurrentLanguage(), 'common.requestFailed')

  if (!response.ok) {
    throw new ApiRequestError(
      payload?.message ?? fallback,
      response.status,
      retryAfterSeconds(response.headers),
    )
  }

  if (!payload?.success) {
    throw new ApiRequestError(payload?.message ?? fallback, response.status)
  }

  return payload.data
}

function withLanguageHeader(init?: RequestInit) {
  const headers = new Headers(init?.headers)
  if (!headers.has('Accept-Language')) {
    headers.set('Accept-Language', getCurrentLanguage())
  }

  return { ...init, headers }
}

function retryAfterSeconds(headers: Headers) {
  const retryAfter = headers.get('Retry-After')
  if (!retryAfter) return undefined

  const seconds = Number.parseInt(retryAfter, 10)
  if (!Number.isFinite(seconds) || seconds <= 0) return undefined

  return seconds
}
