import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import type {
  AuthMode,
  CentralLoginContext,
  CentralLoginRedirectResponse,
  CentralLoginRequestContext,
} from '../types/auth'
import { AuthFormCard } from '../components/AuthFormCard'
import { useAuthSession } from '../context/useAuthSession'
import { ROUTES } from '../../../shared/constants/routes'
import { useI18n } from '../../../shared/i18n/useI18n'
import {
  continueCentralLogin,
  getCentralLoginContext,
} from '../api/authApi'
import { AuthPageLayout } from '../components/AuthPageLayout'
import { authPathWithSearch } from '../lib/authNavigation'

export type AuthFormValues = {
  email: string
  password: string
  displayName?: string
}

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
  } = useAuthSession()
  const [error, setError] = useState('')
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

  const routeState = location.state as {
    verifiedEmail?: string
    passwordResetSucceeded?: boolean
  } | null
  const verifiedEmail = routeState?.verifiedEmail ?? ''
  const passwordResetSucceeded = routeState?.passwordResetSucceeded ?? false

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

  async function handleSubmit(values: AuthFormValues) {
    setError('')
    try {
      if (mode === 'signup') {
        const createdUser = await signupWithPassword(
          values.email,
          values.password,
          values.displayName?.trim() ?? '',
        )
        navigate(
          authPathWithSearch(ROUTES.verifyEmail, location.search, { email: createdUser.email }),
        )
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
    navigate(authPathForMode(nextMode, location.search))
  }

  function handleForgotPasswordClick() {
    setError('')
    navigate(authPathWithSearch(ROUTES.forgotPassword, location.search))
  }

  const formError = error || centralLoginError
  const formTitle = centralLoginContext
    ? t('auth.centralLogin.signinTitle', { clientName: centralLoginContext.clientName })
    : undefined
  const centralLoginBusy = centralLoginLoading
  const submitDisabled = centralLoginBusy || Boolean(centralLoginError)

  return (
    <AuthPageLayout>
      {verifiedEmail ? (
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
    </AuthPageLayout>
  )
}
