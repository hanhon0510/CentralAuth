import { useEffect, useState } from 'react'
import { Alert, Col, Layout, Row, Space, Typography } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import type { AuthMode } from '../types/auth'
import { AuthFormCard } from '../components/AuthFormCard'
import { VerifyEmailCard } from '../components/VerifyEmailCard'
import { useAuthSession } from '../context/useAuthSession'
import { ROUTES } from '../../../shared/constants/routes'

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
  const {
    user,
    loading,
    restoring,
    signinWithPassword,
    signupWithPassword,
    verifyEmailWithOtp,
  } = useAuthSession()
  const [error, setError] = useState('')
  const [verificationEmail, setVerificationEmail] = useState('')

  const routeState = location.state as { verifiedEmail?: string } | null
  const verifiedEmail = routeState?.verifiedEmail ?? ''

  useEffect(() => {
    if (user) {
      navigate(ROUTES.dashboard, { replace: true })
    }
  }, [navigate, user])

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
        return
      }
      await signinWithPassword(values.email, values.password)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Request failed')
    }
  }

  function handleModeChange(nextMode: AuthMode) {
    setError('')
    setVerificationEmail('')
    navigate(nextMode === 'signup' ? ROUTES.signup : ROUTES.signin)
  }

  async function handleVerifyEmail(otp: string) {
    setError('')
    try {
      await verifyEmailWithOtp(verificationEmail, otp)
      setVerificationEmail('')
      navigate(ROUTES.signin, {
        replace: true,
        state: { verifiedEmail: verificationEmail },
      })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Request failed')
    }
  }

  function handleBackToSignin() {
    setError('')
    setVerificationEmail('')
    navigate(ROUTES.signin)
  }

  return (
    <Layout className="app-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={16} lg={12} xl={10} xxl={8}>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Typography.Text className="app-brand">CentralAuth</Typography.Text>
              {verifiedEmail && !verificationEmail ? (
                <Alert
                  type="success"
                  showIcon
                  message="Email verified. Sign in to continue."
                />
              ) : null}
              {verificationEmail ? (
                <VerifyEmailCard
                  email={verificationEmail}
                  loading={loading}
                  error={error}
                  onBack={handleBackToSignin}
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
