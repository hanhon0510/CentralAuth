import { describe, expect, it } from 'vitest'
import {
  expiresAtEpochSecondsFromJwt,
  refreshDelayMillisecondsFromJwt,
  rolesFromJwt,
} from './jwt'

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

  it('returns the proactive refresh delay one minute before expiry', () => {
    const token = tokenWithPayload({ exp: 1_000 })

    expect(refreshDelayMillisecondsFromJwt(token, 880)).toBe(60_000)
  })

  it('returns zero when the proactive refresh time has elapsed', () => {
    const token = tokenWithPayload({ exp: 1_000 })

    expect(refreshDelayMillisecondsFromJwt(token, 950)).toBe(0)
  })

  it('returns null refresh delay when expiry is unavailable', () => {
    expect(refreshDelayMillisecondsFromJwt('not-a-jwt', 880)).toBeNull()
    expect(refreshDelayMillisecondsFromJwt(tokenWithPayload({ roles: ['ROLE_USER'] }), 880)).toBeNull()
  })
})
