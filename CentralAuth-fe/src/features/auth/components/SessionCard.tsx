import { CheckCircleTwoTone, DisconnectOutlined, LogoutOutlined } from '@ant-design/icons'
import { Button, Card, Descriptions, Space, Tag, Typography } from 'antd'
import type { User } from '../types/auth'
import { useI18n } from '../../../shared/i18n/useI18n'

type SessionCardProps = {
  roles: string[]
  tokenPreview: string
  user: User
  loading: boolean
  onSignOut: () => Promise<void>
  onSignOutAllDevices: () => Promise<void>
}

export function SessionCard({ roles, tokenPreview, user, loading, onSignOut, onSignOutAllDevices }: SessionCardProps) {
  const { t } = useI18n()

  return (
    <Card title={t('session.current')}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Space align="center">
          <Typography.Title level={4} style={{ margin: 0 }}>
            {user.displayName || user.email}
          </Typography.Title>
          <Tag color="success" icon={<CheckCircleTwoTone twoToneColor="#52c41a" />}>
            {t('session.signedIn')}
          </Tag>
        </Space>

        <Typography.Text type="secondary">{user.email}</Typography.Text>

        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label={t('session.userId')}>{user.id}</Descriptions.Item>
          <Descriptions.Item label={t('session.emailVerified')}>
            {user.emailVerified ? t('common.yes') : t('common.no')}
          </Descriptions.Item>
          <Descriptions.Item label={t('session.roles')}>
            {roles.length ? (
              <Space wrap>
                {roles.map((role) => (
                  <Tag key={role} color={role === 'ROLE_ADMIN' ? 'geekblue' : 'default'}>
                    {role}
                  </Tag>
                ))}
              </Space>
            ) : (
              t('session.noRoles')
            )}
          </Descriptions.Item>
          <Descriptions.Item label={t('session.token')}>{tokenPreview}</Descriptions.Item>
        </Descriptions>

        <Space wrap>
          <Button icon={<LogoutOutlined />} loading={loading} onClick={() => void onSignOut().catch(() => undefined)}>
            {t('session.signOut')}
          </Button>
          <Button
            danger
            icon={<DisconnectOutlined />}
            loading={loading}
            onClick={() => void onSignOutAllDevices().catch(() => undefined)}
          >
            {t('session.signOutAllDevices')}
          </Button>
        </Space>
      </Space>
    </Card>
  )
}
