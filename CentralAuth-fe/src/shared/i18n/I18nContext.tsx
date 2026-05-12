import { useCallback, useMemo, useState, type PropsWithChildren } from 'react'
import { I18nContext } from './i18n-store'
import { getCurrentLanguage, storeLanguage } from './language'
import { translate, type Language, type MessageKey, type MessageParams } from './messages'

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
