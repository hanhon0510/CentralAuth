import { useEffect } from 'react';
import { ArrowLeftOutlined, MailOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Space } from 'antd';
import { useI18n } from '../../../shared/i18n/useI18n';

type ForgotPasswordValues = {
  email: string;
};

type ForgotPasswordCardProps = {
  loading: boolean;
  error: string;
  onBack: () => void;
  onSubmit: (values: ForgotPasswordValues) => Promise<void>;
};

export function ForgotPasswordCard({
  loading,
  error,
  onBack,
  onSubmit,
}: ForgotPasswordCardProps) {
  const [form] = Form.useForm<ForgotPasswordValues>();
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
    <Card title={t('auth.forgotPassword')}>
      <Space orientation="vertical" size="large" style={{ width: '100%' }}>
        {error ? <Alert type="error" showIcon title={error} /> : null}

        <Form<ForgotPasswordValues>
          form={form}
          layout="vertical"
          onFinish={onSubmit}
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
            />
          </Form.Item>

          <Button type="primary" htmlType="submit" loading={loading} block>
            {t('auth.sendResetInstructions')}
          </Button>
        </Form>

        <Button icon={<ArrowLeftOutlined />} onClick={onBack} block>
          {t('auth.backToSignin')}
        </Button>
      </Space>
    </Card>
  );
}
