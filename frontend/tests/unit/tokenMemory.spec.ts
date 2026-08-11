import { afterEach, describe, expect, it } from 'vitest'

import { clearAccessToken, getAccessToken, setAccessToken } from '@/api/tokenMemory'

describe('in-memory access token', () => {
  afterEach(() => clearAccessToken())

  it('keeps the token in module memory without browser persistence', () => {
    setAccessToken('short-lived-jwt')

    expect(getAccessToken()).toBe('short-lived-jwt')
    expect(localStorage.length).toBe(0)

    clearAccessToken()
    expect(getAccessToken()).toBeUndefined()
  })
})
