import { HomeOutlined, LoginOutlined, LogoutOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Col, Layout, Row, Space, Spin, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getDemoClientUser } from '../api/demoClientAuthApi'
import {
  centralLoginUrl,
  clearClientSession,
  clearClientToken,
  generateCallbackState,
  readClientToken,
  storeCallbackState,
} from '../demoAuth'
import type { DemoClient } from '../demoClients'
import type { User } from '../../auth/types/auth'

type DemoClientProtectedPageProps = {
  client: DemoClient
}

export function DemoClientProtectedPage({ client }: DemoClientProtectedPageProps) {
  const navigate = useNavigate()
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState(() => readClientToken(client) ?? '')
  const [loading, setLoading] = useState(Boolean(token))
  const [error, setError] = useState('')

  useEffect(() => {
    if (!token) {
      return
    }

    let cancelled = false
    getDemoClientUser(token)
      .then((currentUser) => {
        if (!cancelled) {
          setUser(currentUser)
          setError('')
        }
      })
      .catch((requestError) => {
        if (!cancelled) {
          clearClientToken(client)
          setError(requestError instanceof Error ? requestError.message : 'Unable to load current user.')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [client, token])

  function handleLogin() {
    const state = generateCallbackState()
    storeCallbackState(client, state)
    window.location.assign(centralLoginUrl(client, window.location.origin, state))
  }

  function handleLogout() {
    clearClientSession(client)
    setToken('')
    setUser(null)
    setError('')
  }

  const visibleError = error || (!token ? 'No client token is stored for this demo app.' : '')

  return (
    <Layout className="app-shell demo-client-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={18} lg={14} xl={12} xxl={10}>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Typography.Text className="app-brand">{client.name} protected</Typography.Text>
              <Card className="demo-client-panel">
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <Space direction="vertical" size={4}>
                    <Typography.Title level={2} style={{ margin: 0 }}>
                      Protected {client.name}
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
                      This page only renders user data after this client exchanges a CentralAuth
                      authorization code for its own access token.
                    </Typography.Paragraph>
                  </Space>

                  {loading ? (
                    <Space align="center">
                      <Spin />
                      <Typography.Text>Checking client token...</Typography.Text>
                    </Space>
                  ) : user ? (
                    <>
                      <Alert
                        type="success"
                        showIcon
                        message={`Authenticated as ${user.email}`}
                      />
                      <div className="demo-client-meta">
                        <span>Client audience</span>
                        <strong>{client.clientId}</strong>
                        <span>User ID</span>
                        <strong>{user.id}</strong>
                        <span>Email verified</span>
                        <strong>{user.emailVerified ? 'Yes' : 'No'}</strong>
                      </div>
                    </>
                  ) : (
                    <Alert type="warning" showIcon message={visibleError} />
                  )}

                  <Space wrap>
                    <Button type="primary" icon={<LoginOutlined />} onClick={handleLogin}>
                      Login with CentralAuth
                    </Button>
                    <Button icon={<ReloadOutlined />} onClick={() => window.location.reload()}>
                      Reload
                    </Button>
                    {user ? (
                      <Button icon={<LogoutOutlined />} onClick={handleLogout}>
                        Clear client session
                      </Button>
                    ) : null}
                    <Button icon={<HomeOutlined />} onClick={() => navigate(client.publicPath)}>
                      Public page
                    </Button>
                  </Space>
                </Space>
              </Card>
            </Space>
          </Col>
        </Row>
      </Layout.Content>
    </Layout>
  )
}
