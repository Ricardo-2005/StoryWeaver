import { afterEach, vi } from 'vitest'

class MatchMediaMock implements MediaQueryList {
  readonly media: string
  readonly onchange = null
  matches = false

  constructor(media: string) {
    this.media = media
  }

  addEventListener = vi.fn()
  removeEventListener = vi.fn()
  addListener = vi.fn()
  removeListener = vi.fn()
  dispatchEvent = vi.fn(() => true)
}

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn((query: string) => new MatchMediaMock(query)),
})

afterEach(() => {
  localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
  document.documentElement.removeAttribute('style')
  vi.restoreAllMocks()
})
