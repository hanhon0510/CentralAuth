import { Col, Layout, Row, Space, Typography } from 'antd';
import { SessionCard } from '../../auth/components/SessionCard';
import { useAuthSession } from '../../auth/context/useAuthSession';
import { LanguageSwitcher } from '../../../shared/i18n/LanguageSwitcher';
import { useI18n } from '../../../shared/i18n/useI18n';
import { AuditLogPanel } from '../../audit/components/AuditLogPanel';
import { AdminUsersPanel } from '../../admin-users/components/AdminUsersPanel';
import { AdminClientsPanel } from '../../admin-clients/components/AdminClientsPanel';

export function DashboardPage() {
  const { isAdmin, roles, token, user, tokenPreview, loading, signOut, signOutAllDevices } = useAuthSession();
  const { t } = useI18n();

  if (!user) return null;

  return (
    <Layout className="app-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col
            xs={24}
            sm={22}
            md={isAdmin ? 22 : 16}
            lg={isAdmin ? 20 : 12}
            xl={isAdmin ? 18 : 10}
            xxl={isAdmin ? 16 : 8}
          >
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
                roles={roles}
                user={user}
                tokenPreview={tokenPreview}
                loading={loading}
                onSignOut={signOut}
                onSignOutAllDevices={signOutAllDevices}
              />
              {isAdmin ? <AdminUsersPanel token={token} /> : null}
              {isAdmin ? <AdminClientsPanel token={token} /> : null}
              {isAdmin ? <AuditLogPanel token={token} /> : null}
            </Space>
          </Col>
        </Row>
      </Layout.Content>
    </Layout>
  );
}
