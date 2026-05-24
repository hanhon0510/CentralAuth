import { LoginOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Col, Layout, Row, Space, Spin, Typography } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { exchangeDemoClientCode } from '../api/demoClientAuthApi'
import {
  callbackRedirectUri,
  centralLoginUrl,
  clearCallbackState,
  generateCallbackState,
  readCallbackState,
  storeCallbackState,
  storeClientToken,
  validateCallbackState,
} from '../demoAuth'
import type { DemoClient } from '../demoClients'

type DemoClientCallbackPageProps = {
  client: DemoClient
}

export function DemoClientCallbackPage({ client }: DemoClientCallbackPageProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const exchangeStartedRef = useRef(false)
  const callbackRequest = useMemo(() => {
    const params = new URLSearchParams(location.search)
    const code = params.get('code') ?? ''
    const returnedState = params.get('state')
    const expectedState = readCallbackState(client)

    if (!code) {
      return {
        code: '',
        error: 'CentralAuth did not return an authorization code.',
      }
    }

    if (!validateCallbackState(expectedState, returnedState)) {
      return {
        code: '',
        error: 'The callback state did not match this demo client login attempt.',
      }
    }

    return { code, error: '' }
  }, [client, location.search])

  useEffect(() => {
    if (exchangeStartedRef.current || callbackRequest.error) {
      return
    }
    exchangeStartedRef.current = true

    exchangeDemoClientCode(client, callbackRequest.code, callbackRedirectUri(client))
      .then((response) => {
        storeClientToken(client, response.token)
        clearCallbackState(client)
        navigate(client.protectedPath, { replace: true })
      })
      .catch((requestError) => {
        setError(requestError instanceof Error ? requestError.message : 'Code exchange failed.')
      })
  }, [callbackRequest, client, navigate])

  function handleRetryLogin() {
    const state = generateCallbackState()
    storeCallbackState(client, state)
    window.location.assign(centralLoginUrl(client, window.location.origin, state))
  }

  return (
    <Layout className="app-shell demo-client-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={16} lg={12} xl={10}>
            <Card className="demo-client-panel">
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Typography.Text className="app-brand">{client.name} callback</Typography.Text>
                {callbackRequest.error || error ? (
                  <>
                    <Alert type="error" showIcon message={callbackRequest.error || error} />
                    <Button type="primary" icon={<LoginOutlined />} onClick={handleRetryLogin}>
                      Login with CentralAuth
                    </Button>
                  </>
                ) : (
                  <Space align="center">
                    <Spin />
                    <Typography.Text>Exchanging CentralAuth code...</Typography.Text>
                  </Space>
                )}
              </Space>
            </Card>
          </Col>
        </Row>
      </Layout.Content>
    </Layout>
  )
}
