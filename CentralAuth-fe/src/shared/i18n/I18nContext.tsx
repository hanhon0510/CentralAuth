import { createContext, useCallback, useMemo, useState, type PropsWithChildren } from 'react'
import { getCurrentLanguage, storeLanguage } from './language'
import { translate, type Language, type MessageKey, type MessageParams } from './messages'

type I18nContextValue = {
  language: Language
  setLanguage: (language: Language) => void
  t: (key: MessageKey, params?: MessageParams) => string
}

export const I18nContext = createContext<I18nContextValue | null>(null)

export function I18nProvider({ children }: PropsWithChildren) {
  const [language, setLanguageState] = useState(getCurrentLanguage)

  const setLanguage = useCallback((nextLanguage: Language) => {
    storeLanguage(nextLanguage)
    setLanguageState(nextLanguage)
  }, [])

  const t = useCallback(
    (key: MessageKey, params?: MessageParams) => translate(language, key, params),
    [language],
  )

  const value = useMemo(() => ({ language, setLanguage, t }), [language, setLanguage, t])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}
