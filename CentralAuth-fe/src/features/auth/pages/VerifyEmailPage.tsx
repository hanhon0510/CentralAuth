import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiRequestError } from '../../../shared/lib/http'
import { ROUTES } from '../../../shared/constants/routes'
import { useI18n } from '../../../shared/i18n/useI18n'
import { AuthPageLayout } from '../components/AuthPageLayout'
import { VerifyEmailCard } from '../components/VerifyEmailCard'
import { useAuthSession } from '../context/useAuthSession'
import { authPathWithoutTransientParams } from '../lib/authNavigation'

export function VerifyEmailPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useI18n()
  const { loading, verifyEmailWithOtp, resendVerificationOtp } = useAuthSession()
  const initialEmail = useMemo(() => {
    return new URLSearchParams(location.search).get('email')?.trim() ?? ''
  }, [location.search])
  const [error, setError] = useState('')
  const [resendSucceeded, setResendSucceeded] = useState(false)
  const [resendCooldownSeconds, setResendCooldownSeconds] = useState(0)
  const [resending, setResending] = useState(false)

  useEffect(() => {
    if (resendCooldownSeconds <= 0) return

    const timeout = window.setTimeout(() => {
      setResendCooldownSeconds((seconds) => Math.max(0, seconds - 1))
    }, 1000)

    return () => window.clearTimeout(timeout)
  }, [resendCooldownSeconds])

  async function handleVerifyEmail(otp: string, submittedEmail: string) {
    setError('')
    try {
      await verifyEmailWithOtp(submittedEmail, otp)
      navigate(authPathWithoutTransientParams(ROUTES.signin, location.search), {
        replace: true,
        state: { verifiedEmail: submittedEmail },
      })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    }
  }

  async function handleResendVerificationOtp(submittedEmail: string) {
    setError('')
    setResendSucceeded(false)
    setResending(true)
    try {
      const cooldownSeconds = await resendVerificationOtp(submittedEmail)
      setResendCooldownSeconds(cooldownSeconds)
      setResendSucceeded(true)
    } catch (requestError) {
      if (requestError instanceof ApiRequestError && requestError.retryAfterSeconds) {
        setResendCooldownSeconds(requestError.retryAfterSeconds)
      }
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    } finally {
      setResending(false)
    }
  }

  function handleBackToSignin() {
    navigate(authPathWithoutTransientParams(ROUTES.signin, location.search))
  }

  return (
    <AuthPageLayout>
      <VerifyEmailCard
        email={initialEmail}
        emailReadonly={false}
        verifying={loading && !resending}
        resending={resending}
        error={error}
        resendSucceeded={resendSucceeded}
        resendCooldownSeconds={resendCooldownSeconds}
        onBack={handleBackToSignin}
        onResend={handleResendVerificationOtp}
        onSubmit={handleVerifyEmail}
      />
    </AuthPageLayout>
  )
}
