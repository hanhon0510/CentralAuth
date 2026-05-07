import { CheckCircleTwoTone } from '@ant-design/icons'
import { Button, Card, Descriptions, Space, Tag, Typography } from 'antd'
import type { User } from '../types/auth'

type SessionCardProps = {
  tokenPreview: string
  user: User
  onSignOut: () => void
}

export function SessionCard({ tokenPreview, user, onSignOut }: SessionCardProps) {
  return (
    <Card title="Current session">
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Space align="center">
          <Typography.Title level={4} style={{ margin: 0 }}>
            {user.displayName || user.email}
          </Typography.Title>
          <Tag color="success" icon={<CheckCircleTwoTone twoToneColor="#52c41a" />}>
            Signed in
          </Tag>
        </Space>

        <Typography.Text type="secondary">{user.email}</Typography.Text>

        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="User ID">{user.id}</Descriptions.Item>
          <Descriptions.Item label="Email verified">
            {user.emailVerified ? 'Yes' : 'No'}
          </Descriptions.Item>
          <Descriptions.Item label="Token">{tokenPreview}</Descriptions.Item>
        </Descriptions>

        <Button onClick={onSignOut}>Sign out</Button>
      </Space>
    </Card>
  )
}
