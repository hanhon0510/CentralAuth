import type { PropsWithChildren } from 'react'
import { Col, Layout, Row, Space, Typography } from 'antd'
import { LanguageSwitcher } from '../../../shared/i18n/LanguageSwitcher'

export function AuthPageLayout({ children }: PropsWithChildren) {
  return (
    <Layout className="app-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={16} lg={12} xl={10} xxl={8}>
            <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
              <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                <Typography.Text className="app-brand">CentralAuth</Typography.Text>
                <LanguageSwitcher />
              </Space>
              {children}
            </Space>
          </Col>
        </Row>
      </Layout.Content>
    </Layout>
  )
}
