import type { PropsWithChildren } from 'react'
import { ConfigProvider } from 'antd'
import { BrowserRouter } from 'react-router-dom'
import { AuthSessionProvider } from '../../features/auth/context/AuthSessionContext'

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#246bfe',
          borderRadius: 10,
        },
      }}
    >
      <BrowserRouter>
        <AuthSessionProvider>{children}</AuthSessionProvider>
      </BrowserRouter>
    </ConfigProvider>
  )
}
