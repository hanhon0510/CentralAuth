import { createContext } from 'react'
import type { Language, MessageKey, MessageParams } from './messages'

export type I18nContextValue = {
  language: Language
  setLanguage: (language: Language) => void
  t: (key: MessageKey, params?: MessageParams) => string
}

export const I18nContext = createContext<I18nContextValue | null>(null)
