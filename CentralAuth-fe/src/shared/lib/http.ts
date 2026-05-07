type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  timestamp: string
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null

  if (!response.ok) {
    throw new Error(payload?.message ?? 'Request failed')
  }

  if (!payload?.success) {
    throw new Error(payload?.message ?? 'Request failed')
  }

  return payload.data
}
