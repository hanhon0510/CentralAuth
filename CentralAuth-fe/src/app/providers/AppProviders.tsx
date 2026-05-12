import type { PropsWithChildren } from 'react'
import { ConfigProvider } from 'antd'
import enUS from 'antd/locale/en_US'
import viVN from 'antd/locale/vi_VN'
import { BrowserRouter } from 'react-router-dom'
import { AuthSessionProvider } from '../../features/auth/context/AuthSessionContext'
import { I18nProvider } from '../../shared/i18n/I18nContext'
import { useI18n } from '../../shared/i18n/useI18n'

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <I18nProvider>
      <LocalizedConfigProvider>
        <BrowserRouter>
          <AuthSessionProvider>{children}</AuthSessionProvider>
        </BrowserRouter>
      </LocalizedConfigProvider>
    </I18nProvider>
  )
}

function LocalizedConfigProvider({ children }: PropsWithChildren) {
  const { language } = useI18n()

  return (
    <ConfigProvider
      locale={language === 'vi' ? viVN : enUS}
      theme={{
        token: {
          colorPrimary: '#246bfe',
          borderRadius: 10,
        },
      }}
    >
      {children}
    </ConfigProvider>
  )
}
