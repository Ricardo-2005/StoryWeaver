import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useThemeStore } from '@/stores/theme'

describe('theme store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('follows the saved preference and applies the document theme', () => {
    localStorage.setItem('storyweaver.theme', 'dark')
    const store = useThemeStore()

    store.initialize()

    expect(store.preference).toBe('dark')
    expect(store.resolvedTheme).toBe('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('persists only the theme preference', () => {
    const store = useThemeStore()
    store.initialize()
    store.setPreference('light')

    expect(localStorage.getItem('storyweaver.theme')).toBe('light')
    expect(document.documentElement.dataset.theme).toBe('light')
  })
})
