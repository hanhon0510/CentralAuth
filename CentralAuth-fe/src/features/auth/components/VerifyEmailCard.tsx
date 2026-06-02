import { useEffect } from 'react'
import { MailOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Form, Input, Space, Typography } from 'antd'
import { useI18n } from '../../../shared/i18n/useI18n'

type VerifyEmailValues = {
  email: string
  otp: string
}

type VerifyEmailCardProps = {
  email: string
  emailReadonly?: boolean
  verifying: boolean
  resending: boolean
  error: string
  resendSucceeded: boolean
  resendCooldownSeconds: number
  onBack: () => void
  onResend: (email: string) => Promise<void>
  onSubmit: (otp: string, email: string) => Promise<void>
}

export function VerifyEmailCard({
  email,
  emailReadonly = true,
  verifying,
  resending,
  error,
  resendSucceeded,
  resendCooldownSeconds,
  onBack,
  onResend,
  onSubmit,
}: VerifyEmailCardProps) {
  const [form] = Form.useForm<VerifyEmailValues>()
  const { language, t } = useI18n()

  useEffect(() => {
    form.setFieldValue('email', email)
  }, [email, form])

  useEffect(() => {
    const fieldsWithErrors = form
      .getFieldsError()
      .filter(({ errors }) => errors.length > 0)
      .map(({ name }) => name)

    if (fieldsWithErrors.length > 0) {
      void form.validateFields(fieldsWithErrors).catch(() => undefined)
    }
  }, [form, language])

  async function handleFinish(values: VerifyEmailValues) {
    await onSubmit(values.otp, values.email.trim())
  }

  async function handleResend() {
    const values = await form.validateFields(['email'])
    await onResend(values.email.trim())
  }

  const busy = verifying || resending
  const resendDisabled = busy || resendCooldownSeconds > 0
  const resendLabel =
    resendCooldownSeconds > 0
      ? t('auth.resendOtpIn', { seconds: resendCooldownSeconds })
      : t('auth.resendOtp')

  return (
    <Card title={t('auth.verifyEmail')}>
      <Space orientation="vertical" size="large" style={{ width: '100%' }}>
        {email && emailReadonly ? <Typography.Text type="secondary">{email}</Typography.Text> : null}

        {error ? <Alert type="error" showIcon title={error} /> : null}
        {resendSucceeded ? (
          <Alert type="success" showIcon title={t('auth.resendSent')} />
        ) : null}

        <Form<VerifyEmailValues>
          form={form}
          layout="vertical"
          onFinish={handleFinish}
          requiredMark={false}
        >
          <Form.Item
            label={t('auth.email')}
            name="email"
            rules={[
              { required: true, message: t('auth.validation.email.required') },
              { type: 'email', message: t('auth.validation.email.invalid') },
            ]}
          >
            <Input
              prefix={<MailOutlined />}
              maxLength={320}
              autoComplete="email"
              disabled={emailReadonly}
            />
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

          <Space orientation="vertical" size="small" style={{ width: '100%' }}>
            <Button type="primary" htmlType="submit" loading={verifying} disabled={resending} block>
              {t('auth.verifyEmail')}
            </Button>
            <Button onClick={handleResend} loading={resending} disabled={resendDisabled} block>
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
