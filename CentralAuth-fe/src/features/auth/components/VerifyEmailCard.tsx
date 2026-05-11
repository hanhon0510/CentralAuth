import { MailOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Form, Input, Space, Typography } from 'antd'

type VerifyEmailValues = {
  otp: string
}

type VerifyEmailCardProps = {
  email: string
  loading: boolean
  error: string
  onBack: () => void
  onSubmit: (otp: string) => Promise<void>
}

export function VerifyEmailCard({
  email,
  loading,
  error,
  onBack,
  onSubmit,
}: VerifyEmailCardProps) {
  async function handleFinish(values: VerifyEmailValues) {
    await onSubmit(values.otp)
  }

  return (
    <Card title="Verify email">
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Typography.Text type="secondary">{email}</Typography.Text>

        {error ? <Alert type="error" showIcon message={error} /> : null}

        <Form<VerifyEmailValues>
          layout="vertical"
          onFinish={handleFinish}
          requiredMark={false}
        >
          <Form.Item label="Email">
            <Input prefix={<MailOutlined />} value={email} disabled />
          </Form.Item>

          <Form.Item
            label="OTP"
            name="otp"
            rules={[
              { required: true, message: 'Please enter the OTP' },
              { pattern: /^\d{6}$/, message: 'OTP must be 6 digits' },
            ]}
          >
            <Input
              prefix={<SafetyCertificateOutlined />}
              inputMode="numeric"
              maxLength={6}
              autoComplete="one-time-code"
            />
          </Form.Item>

          <Space direction="vertical" size="small" style={{ width: '100%' }}>
            <Button type="primary" htmlType="submit" loading={loading} block>
              Verify email
            </Button>
            <Button onClick={onBack} block>
              Back to sign in
            </Button>
          </Space>
        </Form>
      </Space>
    </Card>
  )
}
