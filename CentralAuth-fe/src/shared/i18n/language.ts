import type { Language } from './messages'

export const languageStorageKey = 'centralauth.language'
export const supportedLanguages = ['en', 'vi'] as const satisfies readonly Language[]

let currentLanguage: Language | null = null

export function normalizeLanguage(value: string | null | undefined): Language | null {
  if (!value) return null
  const normalized = value.toLowerCase()
  if (normalized.startsWith('vi')) return 'vi'
  if (normalized.startsWith('en')) return 'en'
  return null
}

export function getStoredLanguage() {
  try {
    return normalizeLanguage(globalThis.localStorage?.getItem(languageStorageKey))
  } catch {
    return null
  }
}

export function getBrowserLanguage() {
  try {
    return normalizeLanguage(globalThis.navigator?.language)
  } catch {
    return null
  }
}

export function getCurrentLanguage(): Language {
  const language = currentLanguage ?? getStoredLanguage() ?? getBrowserLanguage() ?? 'en'
  currentLanguage = language
  return language
}

export function storeLanguage(language: Language) {
  currentLanguage = language

  try {
    globalThis.localStorage?.setItem(languageStorageKey, language)
  } catch {
    // Ignore storage failures so React state can still update.
  }
}
