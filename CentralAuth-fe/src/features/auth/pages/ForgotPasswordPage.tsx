import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ROUTES } from '../../../shared/constants/routes'
import { useI18n } from '../../../shared/i18n/useI18n'
import { AuthPageLayout } from '../components/AuthPageLayout'
import { ForgotPasswordCard } from '../components/ForgotPasswordCard'
import { useAuthSession } from '../context/useAuthSession'
import { authPathWithSearch, authPathWithoutTransientParams } from '../lib/authNavigation'
import type { ForgotPasswordValues } from './AuthPage'

export function ForgotPasswordPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useI18n()
  const { loading, requestPasswordReset } = useAuthSession()
  const [error, setError] = useState('')

  async function handleForgotPassword(values: ForgotPasswordValues) {
    setError('')
    try {
      await requestPasswordReset(values.email)
      navigate(authPathWithSearch(ROUTES.resetPassword, location.search), {
        state: { passwordResetMessage: t('auth.resetInstructionsSent') },
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
      <ForgotPasswordCard
        loading={loading}
        error={error}
        onBack={handleBackToSignin}
        onSubmit={handleForgotPassword}
      />
    </AuthPageLayout>
  )
}
