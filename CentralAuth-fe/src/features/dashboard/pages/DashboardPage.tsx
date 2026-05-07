import { Col, Layout, Row, Space, Typography } from 'antd';
import { SessionCard } from '../../auth/components/SessionCard';
import { useAuthSession } from '../../auth/context/useAuthSession';

export function DashboardPage() {
  const { user, tokenPreview, clearSession } = useAuthSession();

  if (!user) return null;

  return (
    <Layout className="app-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={16} lg={12} xl={10} xxl={8}>
            <Space
              orientation="vertical"
              size="middle"
              style={{ width: '100%' }}
            >
              <Typography.Text className="app-brand">
                CentralAuth Dashboard
              </Typography.Text>
              <SessionCard
                user={user}
                tokenPreview={tokenPreview}
                onSignOut={clearSession}
              />
            </Space>
          </Col>
        </Row>
      </Layout.Content>
    </Layout>
  );
}
