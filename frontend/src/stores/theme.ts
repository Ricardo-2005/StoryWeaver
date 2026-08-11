import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type ThemePreference = 'light' | 'dark' | 'system'
export type ResolvedTheme = Exclude<ThemePreference, 'system'>

const STORAGE_KEY = 'storyweaver.theme'

function isThemePreference(value: string | null): value is ThemePreference {
  return value === 'light' || value === 'dark' || value === 'system'
}

export const useThemeStore = defineStore('theme', () => {
  const preference = ref<ThemePreference>('system')
  const systemIsDark = ref(false)
  const initialized = ref(false)
  let mediaQuery: MediaQueryList | undefined

  const resolvedTheme = computed<ResolvedTheme>(() => {
    if (preference.value === 'system') {
      return systemIsDark.value ? 'dark' : 'light'
    }

    return preference.value
  })

  function applyTheme(): void {
    document.documentElement.dataset.theme = resolvedTheme.value
    document.documentElement.style.colorScheme = resolvedTheme.value

    const themeColor = resolvedTheme.value === 'dark' ? '#171a19' : '#f3f1eb'
    document
      .querySelector<HTMLMetaElement>('meta[name="theme-color"]')
      ?.setAttribute('content', themeColor)
  }

  function handleSystemThemeChange(event: MediaQueryListEvent): void {
    systemIsDark.value = event.matches
    if (preference.value === 'system') {
      applyTheme()
    }
  }

  function setPreference(nextPreference: ThemePreference): void {
    preference.value = nextPreference
    localStorage.setItem(STORAGE_KEY, nextPreference)
    applyTheme()
  }

  function initialize(): void {
    if (initialized.value) {
      return
    }

    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    systemIsDark.value = mediaQuery.matches

    const savedPreference = localStorage.getItem(STORAGE_KEY)
    preference.value = isThemePreference(savedPreference) ? savedPreference : 'system'
    mediaQuery.addEventListener('change', handleSystemThemeChange)
    initialized.value = true
    applyTheme()
  }

  function dispose(): void {
    mediaQuery?.removeEventListener('change', handleSystemThemeChange)
    mediaQuery = undefined
    initialized.value = false
  }

  return {
    preference,
    resolvedTheme,
    initialized,
    initialize,
    setPreference,
    dispose,
  }
})
