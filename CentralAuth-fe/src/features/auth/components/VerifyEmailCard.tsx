import { MailOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Form, Input, Space, Typography } from 'antd'
import { useI18n } from '../../../shared/i18n/useI18n'

type VerifyEmailValues = {
  otp: string
}

type VerifyEmailCardProps = {
  email: string
  verifying: boolean
  resending: boolean
  error: string
  resendMessage: string
  resendCooldownSeconds: number
  onBack: () => void
  onResend: () => Promise<void>
  onSubmit: (otp: string) => Promise<void>
}

export function VerifyEmailCard({
  email,
  verifying,
  resending,
  error,
  resendMessage,
  resendCooldownSeconds,
  onBack,
  onResend,
  onSubmit,
}: VerifyEmailCardProps) {
  const { t } = useI18n()

  async function handleFinish(values: VerifyEmailValues) {
    await onSubmit(values.otp)
  }

  const busy = verifying || resending
  const resendDisabled = busy || resendCooldownSeconds > 0
  const resendLabel =
    resendCooldownSeconds > 0
      ? t('auth.resendOtpIn', { seconds: resendCooldownSeconds })
      : t('auth.resendOtp')

  return (
    <Card title={t('auth.verifyEmail')}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Typography.Text type="secondary">{email}</Typography.Text>

        {error ? <Alert type="error" showIcon message={error} /> : null}
        {resendMessage ? <Alert type="success" showIcon message={resendMessage} /> : null}

        <Form<VerifyEmailValues>
          layout="vertical"
          onFinish={handleFinish}
          requiredMark={false}
        >
          <Form.Item label={t('auth.email')}>
            <Input prefix={<MailOutlined />} value={email} disabled />
          </Form.Item>

          <Form.Item
            label={t('auth.otp')}
            name="otp"
            rules={[
              { required: true, message: t('auth.validation.otp.required') },
              { pattern: /^\d{6}$/, message: t('auth.validation.otp.pattern') },
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
            <Button type="primary" htmlType="submit" loading={verifying} disabled={resending} block>
              {t('auth.verifyEmail')}
            </Button>
            <Button onClick={onResend} loading={resending} disabled={resendDisabled} block>
              {resendLabel}
            </Button>
            <Button onClick={onBack} disabled={busy} block>
              {t('auth.backToSignin')}
            </Button>
          </Space>
        </Form>
      </Space>
    </Card>
  )
}
