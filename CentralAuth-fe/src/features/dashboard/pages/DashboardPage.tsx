import { Col, Layout, Row, Space, Typography } from 'antd';
import { SessionCard } from '../../auth/components/SessionCard';
import { useAuthSession } from '../../auth/context/useAuthSession';
import { LanguageSwitcher } from '../../../shared/i18n/LanguageSwitcher';
import { useI18n } from '../../../shared/i18n/useI18n';

export function DashboardPage() {
  const { user, tokenPreview, clearSession } = useAuthSession();
  const { t } = useI18n();

  if (!user) return null;

  return (
    <Layout className="app-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={16} lg={12} xl={10} xxl={8}>
            <Space
              direction="vertical"
              size="middle"
              style={{ width: '100%' }}
            >
              <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                <Typography.Text className="app-brand">{t('dashboard.title')}</Typography.Text>
                <LanguageSwitcher />
              </Space>
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
