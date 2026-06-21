type JwtPayload = {
  exp?: unknown
  roles?: unknown
}

const REFRESH_SKEW_SECONDS = 60

export function rolesFromJwt(token: string) {
  const payload = decodeJwtPayload(token)
  if (!payload || !Array.isArray(payload.roles)) {
    return []
  }

  return payload.roles.filter((role): role is string => typeof role === 'string')
}

export function expiresAtEpochSecondsFromJwt(token: string) {
  const payload = decodeJwtPayload(token)
  if (!payload || typeof payload.exp !== 'number') {
    return null
  }

  return payload.exp
}

export function refreshDelayMillisecondsFromJwt(token: string, nowEpochSeconds = Date.now() / 1000) {
  const expiresAtEpochSeconds = expiresAtEpochSecondsFromJwt(token)
  if (expiresAtEpochSeconds === null) {
    return null
  }

  const delaySeconds = expiresAtEpochSeconds - nowEpochSeconds - REFRESH_SKEW_SECONDS
  return Math.max(0, Math.floor(delaySeconds * 1000))
}

function decodeJwtPayload(token: string): JwtPayload | null {
  const [, encodedPayload] = token.split('.')
  if (!encodedPayload) {
    return null
  }

  try {
    const base64 = encodedPayload.replaceAll('-', '+').replaceAll('_', '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    return JSON.parse(atob(padded)) as JwtPayload
  } catch {
    return null
  }
}
