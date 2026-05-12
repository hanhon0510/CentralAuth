import { Segmented } from 'antd'
import { supportedLanguages } from './language'
import type { Language } from './messages'
import { useI18n } from './useI18n'

const languageLabels: Record<Language, string> = {
  en: 'EN',
  vi: 'VI',
}

const languageOptions = supportedLanguages.map((language) => ({
  label: languageLabels[language],
  value: language,
}))

export function LanguageSwitcher() {
  const { language, setLanguage } = useI18n()

  return (
    <Segmented<Language>
      options={languageOptions}
      value={language}
      onChange={setLanguage}
    />
  )
}
