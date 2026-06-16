type JwtPayload = {
  exp?: unknown
  roles?: unknown
}

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
