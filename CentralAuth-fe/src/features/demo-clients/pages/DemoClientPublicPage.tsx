import { ArrowRightOutlined, LoginOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Col, Layout, Row, Space, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import {
  callbackRedirectUri,
  centralLoginUrl,
  generateCallbackState,
  storeCallbackState,
} from '../demoAuth'
import type { DemoClient } from '../demoClients'

type DemoClientPublicPageProps = {
  client: DemoClient
}

export function DemoClientPublicPage({ client }: DemoClientPublicPageProps) {
  const navigate = useNavigate()

  function handleLogin() {
    const state = generateCallbackState()
    storeCallbackState(client, state)
    window.location.assign(centralLoginUrl(client, window.location.origin, state))
  }

  return (
    <Layout className="app-shell demo-client-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={18} lg={14} xl={12} xxl={10}>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Typography.Text className="app-brand">{client.name}</Typography.Text>
              <Card className="demo-client-panel">
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <Space direction="vertical" size={4}>
                    <Typography.Title level={2} style={{ margin: 0 }}>
                      {client.name}
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
                      {client.description}
                    </Typography.Paragraph>
                  </Space>

                  <Alert
                    type="info"
                    showIcon
                    title="This page is public. The protected page requires a client token issued by CentralAuth."
                  />

                  <div className="demo-client-meta">
                    <span>Client ID</span>
                    <strong>{client.clientId}</strong>
                    <span>Callback</span>
                    <strong>{callbackRedirectUri(client)}</strong>
                  </div>

                  <Space wrap>
                    <Button type="primary" icon={<LoginOutlined />} onClick={handleLogin}>
                      Login with CentralAuth
                    </Button>
                    <Button
                      icon={<ArrowRightOutlined />}
                      onClick={() => navigate(client.protectedPath)}
                    >
                      Open protected page
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
