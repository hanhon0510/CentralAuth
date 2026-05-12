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

export type AuthFormValues = {
  email: string
  password: string
  displayName?: string
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
  } = useAuthSession()
  const [error, setError] = useState('')
  const [verificationEmail, setVerificationEmail] = useState('')
  const [resendSucceeded, setResendSucceeded] = useState(false)
  const [resendCooldownSeconds, setResendCooldownSeconds] = useState(0)
  const [resending, setResending] = useState(false)

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
    navigate(ROUTES.signin)
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
              {verificationEmail ? (
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
