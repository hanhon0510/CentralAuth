import { useEffect } from 'react';
import { ArrowLeftOutlined, KeyOutlined, LockOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Space } from 'antd';
import { useI18n } from '../../../shared/i18n/useI18n';

type ResetPasswordValues = {
  token: string;
  newPassword: string;
};

type ResetPasswordCardProps = {
  loading: boolean;
  error: string;
  message: string;
  onBack: () => void;
  onSubmit: (values: ResetPasswordValues) => Promise<void>;
};

export function ResetPasswordCard({
  loading,
  error,
  message,
  onBack,
  onSubmit,
}: ResetPasswordCardProps) {
  const [form] = Form.useForm<ResetPasswordValues>();
  const { language, t } = useI18n();

  useEffect(() => {
    const fieldsWithErrors = form
      .getFieldsError()
      .filter(({ errors }) => errors.length > 0)
      .map(({ name }) => name);

    if (fieldsWithErrors.length > 0) {
      void form.validateFields(fieldsWithErrors).catch(() => undefined);
    }
  }, [form, language]);

  return (
    <Card title={t('auth.resetPassword')}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {message ? <Alert type="success" showIcon message={message} /> : null}
        {error ? <Alert type="error" showIcon message={error} /> : null}

        <Form<ResetPasswordValues>
          form={form}
          layout="vertical"
          onFinish={onSubmit}
          requiredMark={false}
        >
          <Form.Item
            label={t('auth.resetToken')}
            name="token"
            rules={[{ required: true, message: t('auth.validation.resetToken.required') }]}
          >
            <Input prefix={<KeyOutlined />} autoComplete="one-time-code" />
          </Form.Item>

          <Form.Item
            label={t('auth.newPassword')}
            name="newPassword"
            rules={[
              { required: true, message: t('auth.validation.password.required') },
              { min: 8, message: t('auth.validation.password.min') },
              { max: 120, message: t('auth.validation.password.max') },
            ]}
          >
            <Input.Password prefix={<LockOutlined />} autoComplete="new-password" />
          </Form.Item>

          <Button type="primary" htmlType="submit" loading={loading} block>
            {t('auth.resetPassword')}
          </Button>
        </Form>

        <Button icon={<ArrowLeftOutlined />} onClick={onBack} block>
          {t('auth.backToSignin')}
        </Button>
      </Space>
    </Card>
  );
}
