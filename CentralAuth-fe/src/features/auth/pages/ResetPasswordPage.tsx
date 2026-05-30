import { useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ROUTES } from '../../../shared/constants/routes'
import { useI18n } from '../../../shared/i18n/useI18n'
import { AuthPageLayout } from '../components/AuthPageLayout'
import { ResetPasswordCard } from '../components/ResetPasswordCard'
import { useAuthSession } from '../context/useAuthSession'
import { authPathWithoutTransientParams } from '../lib/authNavigation'
import type { ResetPasswordValues } from './AuthPage'

type ResetPasswordRouteState = {
  passwordResetMessage?: string
}

export function ResetPasswordPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useI18n()
  const { loading, resetPasswordWithToken } = useAuthSession()
  const [error, setError] = useState('')
  const initialToken = useMemo(() => {
    return new URLSearchParams(location.search).get('token')?.trim() ?? ''
  }, [location.search])
  const routeState = location.state as ResetPasswordRouteState | null
  const message = routeState?.passwordResetMessage ?? ''

  async function handleResetPassword(values: ResetPasswordValues) {
    setError('')
    try {
      await resetPasswordWithToken(values.token, values.newPassword)
      navigate(authPathWithoutTransientParams(ROUTES.signin, location.search), {
        replace: true,
        state: { passwordResetSucceeded: true },
      })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    }
  }

  function handleBackToSignin() {
    navigate(authPathWithoutTransientParams(ROUTES.signin, location.search))
  }

  return (
    <AuthPageLayout>
      <ResetPasswordCard
        initialToken={initialToken}
        loading={loading}
        error={error}
        message={message}
        onBack={handleBackToSignin}
        onSubmit={handleResetPassword}
      />
    </AuthPageLayout>
  )
}
