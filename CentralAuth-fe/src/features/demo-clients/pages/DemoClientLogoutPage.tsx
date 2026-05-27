import { CheckCircleOutlined, HomeOutlined } from '@ant-design/icons'
import { Button, Card, Col, Layout, Result, Row, Space, Typography } from 'antd'
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { clearClientSession } from '../demoAuth'
import type { DemoClient } from '../demoClients'

type DemoClientLogoutPageProps = {
  client: DemoClient
}

export function DemoClientLogoutPage({ client }: DemoClientLogoutPageProps) {
  const navigate = useNavigate()

  useEffect(() => {
    clearClientSession(client)
  }, [client])

  return (
    <Layout className="app-shell demo-client-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={16} lg={12} xl={10}>
            <Card className="demo-client-panel">
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Typography.Text className="app-brand">{client.name} logout</Typography.Text>
                <Result
                  icon={<CheckCircleOutlined />}
                  status="success"
                  title="Signed out"
                  subTitle={`${client.name} local session has been cleared.`}
                />
                <Button icon={<HomeOutlined />} onClick={() => navigate(client.publicPath)}>
                  Public page
                </Button>
              </Space>
            </Card>
          </Col>
        </Row>
      </Layout.Content>
    </Layout>
  )
}
