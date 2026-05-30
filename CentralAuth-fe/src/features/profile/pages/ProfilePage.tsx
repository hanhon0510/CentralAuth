import { DashboardOutlined } from '@ant-design/icons'
import { Button, Col, Layout, Row, Space, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { SessionCard } from '../../auth/components/SessionCard'
import { useAuthSession } from '../../auth/context/useAuthSession'
import { LanguageSwitcher } from '../../../shared/i18n/LanguageSwitcher'
import { useI18n } from '../../../shared/i18n/useI18n'
import { ROUTES } from '../../../shared/constants/routes'

export function ProfilePage() {
  const { roles, user, tokenPreview, loading, signOut, signOutAllDevices } = useAuthSession()
  const { t } = useI18n()
  const navigate = useNavigate()

  if (!user) return null

  return (
    <Layout className="app-shell">
      <Layout.Content className="content-shell">
        <Row justify="center">
          <Col xs={24} sm={22} md={16} lg={12} xl={10} xxl={8}>
            <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
              <Space align="center" wrap style={{ width: '100%', justifyContent: 'space-between' }}>
                <Typography.Text className="app-brand">{t('profile.title')}</Typography.Text>
                <Space wrap>
                  <Button
                    icon={<DashboardOutlined />}
                    onClick={() => navigate(ROUTES.dashboard)}
                  >
                    {t('profile.backToDashboard')}
                  </Button>
                  <LanguageSwitcher />
                </Space>
              </Space>
              <SessionCard
                roles={roles}
                user={user}
                tokenPreview={tokenPreview}
                loading={loading}
                onSignOut={signOut}
                onSignOutAllDevices={signOutAllDevices}
              />
            </Space>
          </Col>
        </Row>
      </Layout.Content>
    </Layout>
  )
}
