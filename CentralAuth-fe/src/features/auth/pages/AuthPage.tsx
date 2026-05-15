import { useEffect, useState } from 'react'
import { Alert, Col, Layout, Row, Space, Typography } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import type { AuthMode } from '../types/auth'
import { AuthFormCard } from '../components/AuthFormCard'
import { VerifyEmailCard } from '../components/VerifyEmailCard'
import { useAuthSession } from '../context/useAuthSession'
import { ROUTES } from '../../../shared/constants/routes'
import { LanguageSwitcher } from '../../../shared/i18n/LanguageSwitcher'
import { useI18n } from '../../../shared/i18n/useI18n'
import { ApiRequestError } from '../../../shared/lib/http'
import { ForgotPasswordCard } from '../components/ForgotPasswordCard'
import { ResetPasswordCard } from '../components/ResetPasswordCard'

export type AuthFormValues = {
  email: string
  password: string
  displayName?: string
}

type PasswordResetStep = 'auth' | 'forgotPassword' | 'resetPassword'

export type ForgotPasswordValues = {
  email: string
}

export type ResetPasswordValues = {
  token: string
  newPassword: string
}

type AuthPageProps = {
  mode: AuthMode
}

export function AuthPage({ mode }: AuthPageProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useI18n()
  const {
    user,
    loading,
    restoring,
    signinWithPassword,
    signupWithPassword,
    verifyEmailWithOtp,
    resendVerificationOtp,
    requestPasswordReset,
    resetPasswordWithToken,
  } = useAuthSession()
  const [error, setError] = useState('')
  const [verificationEmail, setVerificationEmail] = useState('')
  const [resendSucceeded, setResendSucceeded] = useState(false)
  const [resendCooldownSeconds, setResendCooldownSeconds] = useState(0)
  const [resending, setResending] = useState(false)
  const [passwordResetStep, setPasswordResetStep] = useState<PasswordResetStep>('auth')
  const [passwordResetMessage, setPasswordResetMessage] = useState('')
  const [passwordResetSucceeded, setPasswordResetSucceeded] = useState(false)

  const routeState = location.state as { verifiedEmail?: string } | null
  const verifiedEmail = routeState?.verifiedEmail ?? ''

  useEffect(() => {
    if (user) {
      navigate(ROUTES.dashboard, { replace: true })
    }
  }, [navigate, user])

  useEffect(() => {
    if (resendCooldownSeconds <= 0) return

    const timeout = window.setTimeout(() => {
      setResendCooldownSeconds((seconds) => Math.max(0, seconds - 1))
    }, 1000)

    return () => window.clearTimeout(timeout)
  }, [resendCooldownSeconds])

  async function handleSubmit(values: AuthFormValues) {
    setError('')
    try {
      if (mode === 'signup') {
        const createdUser = await signupWithPassword(
          values.email,
          values.password,
          values.displayName?.trim() ?? '',
        )
        setVerificationEmail(createdUser.email)
        setResendSucceeded(false)
        setResendCooldownSeconds(0)
        return
      }
      await signinWithPassword(values.email, values.password)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    }
  }

  function handleModeChange(nextMode: AuthMode) {
    setError('')
    setVerificationEmail('')
    setResendSucceeded(false)
    setResendCooldownSeconds(0)
    setPasswordResetStep('auth')
    setPasswordResetMessage('')
    setPasswordResetSucceeded(false)
    navigate(nextMode === 'signup' ? ROUTES.signup : ROUTES.signin)
  }

  async function handleVerifyEmail(otp: string) {
    setError('')
    try {
      await verifyEmailWithOtp(verificationEmail, otp)
      setVerificationEmail('')
      setResendSucceeded(false)
      setResendCooldownSeconds(0)
      navigate(ROUTES.signin, {
        replace: true,
        state: { verifiedEmail: verificationEmail },
      })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    }
  }

  async function handleResendVerificationOtp() {
    setError('')
    setResendSucceeded(false)
    setResending(true)
    try {
      const cooldownSeconds = await resendVerificationOtp(verificationEmail)
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
    setError('')
    setVerificationEmail('')
    setResendSucceeded(false)
    setResendCooldownSeconds(0)
    setPasswordResetStep('auth')
    setPasswordResetMessage('')
    setPasswordResetSucceeded(false)
    navigate(ROUTES.signin)
  }

  function handleForgotPasswordClick() {
    setError('')
    setPasswordResetMessage('')
    setPasswordResetSucceeded(false)
    setPasswordResetStep('forgotPassword')
  }

  async function handleForgotPassword(values: ForgotPasswordValues) {
    setError('')
    setPasswordResetMessage('')
    try {
      await requestPasswordReset(values.email)
      setPasswordResetMessage(t('auth.resetInstructionsSent'))
      setPasswordResetStep('resetPassword')
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    }
  }

  async function handleResetPassword(values: ResetPasswordValues) {
    setError('')
    try {
      await resetPasswordWithToken(values.token, values.newPassword)
      setPasswordResetStep('auth')
      setPasswordResetMessage('')
      setPasswordResetSucceeded(true)
      navigate(ROUTES.signin, { replace: true })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    }
  }

  return (
    <Layout className="app-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={16} lg={12} xl={10} xxl={8}>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                <Typography.Text className="app-brand">CentralAuth</Typography.Text>
                <LanguageSwitcher />
              </Space>
              {verifiedEmail && !verificationEmail ? (
                <Alert
                  type="success"
                  showIcon
                  message={t('auth.emailVerifiedSignin')}
                />
              ) : null}
              {passwordResetSucceeded ? (
                <Alert
                  type="success"
                  showIcon
                  message={t('auth.passwordResetSucceeded')}
                />
              ) : null}
              {passwordResetStep === 'forgotPassword' ? (
                <ForgotPasswordCard
                  loading={loading}
                  error={error}
                  onBack={handleBackToSignin}
                  onSubmit={handleForgotPassword}
                />
              ) : passwordResetStep === 'resetPassword' ? (
                <ResetPasswordCard
                  loading={loading}
                  error={error}
                  message={passwordResetMessage}
                  onBack={handleBackToSignin}
                  onSubmit={handleResetPassword}
                />
              ) : verificationEmail ? (
                <VerifyEmailCard
                  email={verificationEmail}
                  verifying={loading && !resending}
                  resending={resending}
                  error={error}
                  resendSucceeded={resendSucceeded}
                  resendCooldownSeconds={resendCooldownSeconds}
                  onBack={handleBackToSignin}
                  onResend={handleResendVerificationOtp}
                  onSubmit={handleVerifyEmail}
                />
              ) : (
                <AuthFormCard
                  mode={mode}
                  loading={loading}
                  restoring={restoring}
                  error={error}
                  onForgotPassword={handleForgotPasswordClick}
                  onModeChange={handleModeChange}
                  onSubmit={handleSubmit}
                />
              )}
            </Space>
          </Col>
        </Row>
      </Layout.Content>
    </Layout>
  )
}
