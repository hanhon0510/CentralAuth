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
  const response = await fetch(path, init)
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null

  if (!response.ok) {
    throw new ApiRequestError(
      payload?.message ?? 'Request failed',
      response.status,
      retryAfterSeconds(response.headers),
    )
  }

  if (!payload?.success) {
    throw new ApiRequestError(payload?.message ?? 'Request failed', response.status)
  }

  return payload.data
}

function retryAfterSeconds(headers: Headers) {
  const retryAfter = headers.get('Retry-After')
  if (!retryAfter) return undefined

  const seconds = Number.parseInt(retryAfter, 10)
  if (!Number.isFinite(seconds) || seconds <= 0) return undefined

  return seconds
}
