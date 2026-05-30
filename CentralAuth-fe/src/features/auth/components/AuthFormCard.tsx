import { useEffect } from 'react';
import { LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Segmented,
  Space,
} from 'antd';
import type { AuthMode } from '../types/auth';
import { useI18n } from '../../../shared/i18n/useI18n';

type AuthFormValues = {
  email: string;
  password: string;
  displayName?: string;
};

type AuthFormCardProps = {
  mode: AuthMode;
  title?: string;
  loading: boolean;
  restoring: boolean;
  submitDisabled?: boolean;
  error: string;
  onForgotPassword: () => void;
  onModeChange: (mode: AuthMode) => void;
  onSubmit: (values: AuthFormValues) => Promise<void>;
};

export function AuthFormCard({
  mode,
  title,
  loading,
  restoring,
  submitDisabled = false,
  error,
  onForgotPassword,
  onModeChange,
  onSubmit,
}: AuthFormCardProps) {
  const [form] = Form.useForm<AuthFormValues>();
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
    <Card title={title ?? t('auth.emailAccess')}>
      <Space orientation="vertical" size="large" style={{ width: '100%' }}>
        <Segmented<AuthMode>
          block
          value={mode}
          options={[
            { label: t('auth.signin'), value: 'signin' },
            { label: t('auth.signup'), value: 'signup' },
          ]}
          onChange={onModeChange}
        />

        {error ? <Alert type="error" showIcon message={error} /> : null}

        <Form<AuthFormValues>
          form={form}
          layout="vertical"
          onFinish={onSubmit}
          requiredMark={false}
        >
          {mode === 'signup' ? (
            <Form.Item
              label={t('auth.displayName')}
              name="displayName"
              rules={[{ max: 120, message: t('auth.validation.displayName.max') }]}
            >
              <Input
                prefix={<UserOutlined />}
                autoComplete="name"
                placeholder={t('auth.displayName.placeholder')}
              />
            </Form.Item>
          ) : null}

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

          <Form.Item
            label={t('auth.password')}
            name="password"
            rules={[
              { required: true, message: t('auth.validation.password.required') },
              {
                min: mode === 'signup' ? 8 : 1,
                message:
                  mode === 'signup'
                    ? t('auth.validation.password.min')
                    : t('auth.validation.password.required'),
              },
              { max: 120, message: t('auth.validation.password.max') },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              autoComplete={
                mode === 'signup' ? 'new-password' : 'current-password'
              }
            />
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            disabled={restoring || submitDisabled}
            block
          >
            {mode === 'signup' ? t('auth.createAccount') : t('auth.signin')}
          </Button>

          {mode === 'signin' ? (
            <Button type="link" block onClick={onForgotPassword}>
              {t('auth.forgotPassword')}
            </Button>
          ) : null}
        </Form>
      </Space>
    </Card>
  );
}
