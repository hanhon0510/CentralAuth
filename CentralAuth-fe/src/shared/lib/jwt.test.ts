import { describe, expect, it } from 'vitest'
import { expiresAtEpochSecondsFromJwt, rolesFromJwt } from './jwt'

function tokenWithPayload(payload: object) {
  const encodedPayload = btoa(JSON.stringify(payload))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replaceAll('=', '')
  return `header.${encodedPayload}.signature`
}

describe('jwt helpers', () => {
  it('returns string roles from a valid JWT payload', () => {
    const token = tokenWithPayload({ roles: ['ROLE_USER', 42, 'ROLE_ADMIN'] })

    expect(rolesFromJwt(token)).toEqual(['ROLE_USER', 'ROLE_ADMIN'])
  })

  it('returns exp from a valid JWT payload', () => {
    const token = tokenWithPayload({ exp: 1_805_000_000 })

    expect(expiresAtEpochSecondsFromJwt(token)).toBe(1_805_000_000)
  })

  it('returns null for malformed tokens', () => {
    expect(expiresAtEpochSecondsFromJwt('not-a-jwt')).toBeNull()
  })

  it('returns null when exp is missing or not a number', () => {
    expect(expiresAtEpochSecondsFromJwt(tokenWithPayload({ roles: ['ROLE_USER'] }))).toBeNull()
    expect(expiresAtEpochSecondsFromJwt(tokenWithPayload({ exp: '1805000000' }))).toBeNull()
  })
})
