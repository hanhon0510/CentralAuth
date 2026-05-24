import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Col, Layout, Row, Space, Typography } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import type {
  AuthMode,
  CentralLoginContext,
  CentralLoginRedirectResponse,
  CentralLoginRequestContext,
} from '../types/auth'
import { AuthFormCard } from '../components/AuthFormCard'
import { VerifyEmailCard } from '../components/VerifyEmailCard'
import { useAuthSession } from '../context/useAuthSession'
import { ROUTES } from '../../../shared/constants/routes'
import { LanguageSwitcher } from '../../../shared/i18n/LanguageSwitcher'
import { useI18n } from '../../../shared/i18n/useI18n'
import { ApiRequestError } from '../../../shared/lib/http'
import { ForgotPasswordCard } from '../components/ForgotPasswordCard'
import { ResetPasswordCard } from '../components/ResetPasswordCard'
import {
  continueCentralLogin,
  getCentralLoginContext,
} from '../api/authApi'

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

type ParsedCentralLoginRequest =
  | { requested: false; context?: undefined }
  | { requested: true; context?: CentralLoginRequestContext }

type CentralLoginLookupResult = {
  key: string
  context: CentralLoginContext | null
  error: string
}

function parseCentralLoginRequest(search: string): ParsedCentralLoginRequest {
  const params = new URLSearchParams(search)
  const requested =
    params.has('client_id') || params.has('redirect_uri') || params.has('state')

  if (!requested) {
    return { requested: false }
  }

  const clientId = params.get('client_id')?.trim() ?? ''
  const redirectUri = params.get('redirect_uri')?.trim() ?? ''

  if (!clientId || !redirectUri) {
    return { requested: true }
  }

  return {
    requested: true,
    context: {
      clientId,
      redirectUri,
      state: params.get('state'),
    },
  }
}

function authPathForMode(mode: AuthMode, search: string) {
  return `${mode === 'signup' ? ROUTES.signup : ROUTES.signin}${search}`
}

function toCentralLoginRequestContext(context: CentralLoginContext): CentralLoginRequestContext {
  return {
    clientId: context.clientId,
    redirectUri: context.redirectUri,
    state: context.state,
    loginState: context.loginState,
  }
}

function centralLoginKey(context: CentralLoginRequestContext | CentralLoginContext) {
  return `${context.clientId}|${context.redirectUri}|${context.state ?? ''}`
}

function buildClientRedirectUrl(response: CentralLoginRedirectResponse) {
  return response.redirectUrl
}

export function AuthPage({ mode }: AuthPageProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useI18n()
  const {
    token,
    user,
    loading,
    restoring,
    signinWithPassword,
    signinWithCentralLogin,
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
  const [centralLoginLookup, setCentralLoginLookup] = useState<CentralLoginLookupResult>({
    key: '',
    context: null,
    error: '',
  })
  const redirectingToClientRef = useRef(false)
  const continuedCentralLoginKeyRef = useRef('')

  const centralLoginRequest = useMemo(
    () => parseCentralLoginRequest(location.search),
    [location.search],
  )

  const requestedCentralLoginKey = centralLoginRequest.context
    ? centralLoginKey(centralLoginRequest.context)
    : ''
  const centralLoginContext =
    centralLoginLookup.key === requestedCentralLoginKey ? centralLoginLookup.context : null
  const centralLoginError = !centralLoginRequest.requested
    ? ''
    : !centralLoginRequest.context
      ? t('auth.centralLogin.invalidRequest')
      : centralLoginLookup.key === requestedCentralLoginKey
        ? centralLoginLookup.error
        : ''
  const centralLoginLoading =
    centralLoginRequest.requested &&
    Boolean(centralLoginRequest.context) &&
    centralLoginLookup.key !== requestedCentralLoginKey
  const centralLoginRequestKey = centralLoginContext
    ? centralLoginKey(centralLoginContext)
    : ''

  const routeState = location.state as { verifiedEmail?: string } | null
  const verifiedEmail = routeState?.verifiedEmail ?? ''

  useEffect(() => {
    if (user && !centralLoginRequest.requested) {
      navigate(ROUTES.dashboard, { replace: true })
    }
  }, [centralLoginRequest.requested, navigate, user])

  useEffect(() => {
    if (!centralLoginRequest.context) {
      return
    }

    let cancelled = false
    getCentralLoginContext(centralLoginRequest.context)
      .then((context) => {
        if (!cancelled) {
          setCentralLoginLookup({
            key: requestedCentralLoginKey,
            context,
            error: '',
          })
        }
      })
      .catch((requestError) => {
        if (!cancelled) {
          setCentralLoginLookup({
            key: requestedCentralLoginKey,
            context: null,
            error: requestError instanceof Error ? requestError.message : t('common.requestFailed'),
          })
        }
      })

    return () => {
      cancelled = true
    }
  }, [centralLoginRequest.context, requestedCentralLoginKey, t])

  useEffect(() => {
    if (
      !user ||
      !token ||
      !centralLoginContext ||
      centralLoginError ||
      centralLoginLoading ||
      redirectingToClientRef.current ||
      continuedCentralLoginKeyRef.current === centralLoginRequestKey
    ) {
      return
    }

    let cancelled = false
    continuedCentralLoginKeyRef.current = centralLoginRequestKey

    continueCentralLogin(token, toCentralLoginRequestContext(centralLoginContext))
      .then((response) => {
        if (!cancelled) {
          redirectingToClientRef.current = true
          window.location.assign(buildClientRedirectUrl(response))
        }
      })
      .catch((requestError) => {
        if (!cancelled) {
          continuedCentralLoginKeyRef.current = ''
          setCentralLoginLookup({
            key: centralLoginRequestKey,
            context: centralLoginContext,
            error: requestError instanceof Error ? requestError.message : t('common.requestFailed'),
          })
        }
      })

    return () => {
      cancelled = true
    }
  }, [
    centralLoginContext,
    centralLoginError,
    centralLoginLoading,
    centralLoginRequestKey,
    t,
    token,
    user,
  ])

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
      if (centralLoginContext) {
        const redirectResponse = await signinWithCentralLogin(
          values.email,
          values.password,
          toCentralLoginRequestContext(centralLoginContext),
        )
        redirectingToClientRef.current = true
        window.location.assign(buildClientRedirectUrl(redirectResponse))
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
    navigate(authPathForMode(nextMode, location.search))
  }

  async function handleVerifyEmail(otp: string) {
    setError('')
    try {
      await verifyEmailWithOtp(verificationEmail, otp)
      setVerificationEmail('')
      setResendSucceeded(false)
      setResendCooldownSeconds(0)
      navigate(authPathForMode('signin', location.search), {
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
    navigate(authPathForMode('signin', location.search))
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
      navigate(authPathForMode('signin', location.search), { replace: true })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    }
  }

  const formError = error || centralLoginError
  const formTitle = centralLoginContext
    ? t('auth.centralLogin.signinTitle', { clientName: centralLoginContext.clientName })
    : undefined
  const centralLoginBusy = centralLoginLoading
  const submitDisabled = centralLoginBusy || Boolean(centralLoginError)

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
                  title={formTitle}
                  loading={loading || centralLoginBusy}
                  restoring={restoring}
                  submitDisabled={submitDisabled}
                  error={formError}
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
