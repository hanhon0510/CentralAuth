import { useEffect, useState } from 'react'
import { Col, Layout, Row, Space, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import type { AuthMode } from '../types/auth'
import { AuthFormCard } from '../components/AuthFormCard'
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
  const { user, loading, restoring, signinWithPassword, signupWithPassword } = useAuthSession()
  const [error, setError] = useState('')

  useEffect(() => {
    if (user) {
      navigate(ROUTES.dashboard, { replace: true })
    }
  }, [navigate, user])

  async function handleSubmit(values: AuthFormValues) {
    setError('')
    try {
      if (mode === 'signup') {
        await signupWithPassword(values.email, values.password, values.displayName?.trim() ?? '')
        return
      }
      await signinWithPassword(values.email, values.password)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Request failed')
    }
  }

  function handleModeChange(nextMode: AuthMode) {
    setError('')
    navigate(nextMode === 'signup' ? ROUTES.signup : ROUTES.signin)
  }

  return (
    <Layout className="app-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={16} lg={12} xl={10} xxl={8}>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Typography.Text className="app-brand">CentralAuth</Typography.Text>
              <AuthFormCard
                mode={mode}
                loading={loading}
                restoring={restoring}
                error={error}
                onModeChange={handleModeChange}
                onSubmit={handleSubmit}
              />
            </Space>
          </Col>
        </Row>
      </Layout.Content>
    </Layout>
  )
}
