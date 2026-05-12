import type { Language } from './messages'

export const languageStorageKey = 'centralauth.language'
export const supportedLanguages = ['en', 'vi'] as const satisfies readonly Language[]

export function normalizeLanguage(value: string | null | undefined): Language | null {
  if (!value) return null
  const normalized = value.toLowerCase()
  if (normalized.startsWith('vi')) return 'vi'
  if (normalized.startsWith('en')) return 'en'
  return null
}

export function getStoredLanguage() {
  return normalizeLanguage(localStorage.getItem(languageStorageKey))
}

export function getBrowserLanguage() {
  return normalizeLanguage(navigator.language)
}

export function getCurrentLanguage(): Language {
  return getStoredLanguage() ?? getBrowserLanguage() ?? 'en'
}

export function storeLanguage(language: Language) {
  localStorage.setItem(languageStorageKey, language)
}
