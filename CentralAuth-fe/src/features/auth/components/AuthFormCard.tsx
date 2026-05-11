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

type AuthFormValues = {
  email: string;
  password: string;
  displayName?: string;
};

type AuthFormCardProps = {
  mode: AuthMode;
  loading: boolean;
  restoring: boolean;
  error: string;
  onModeChange: (mode: AuthMode) => void;
  onSubmit: (values: AuthFormValues) => Promise<void>;
};

export function AuthFormCard({
  mode,
  loading,
  restoring,
  error,
  onModeChange,
  onSubmit,
}: AuthFormCardProps) {
  return (
    <Card title="Email access">
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Segmented<AuthMode>
          block
          value={mode}
          options={[
            { label: 'Sign in', value: 'signin' },
            { label: 'Sign up', value: 'signup' },
          ]}
          onChange={onModeChange}
        />

        {error ? <Alert type="error" showIcon message={error} /> : null}

        <Form<AuthFormValues>
          layout="vertical"
          onFinish={onSubmit}
          requiredMark={false}
        >
          {mode === 'signup' ? (
            <Form.Item
              label="Display name"
              name="displayName"
              rules={[{ max: 120 }]}
            >
              <Input
                prefix={<UserOutlined />}
                autoComplete="name"
                placeholder="Your name"
              />
            </Form.Item>
          ) : null}

          <Form.Item
            label="Email"
            name="email"
            rules={[
              { required: true, message: 'Please enter your email' },
              { type: 'email' },
            ]}
          >
            <Input
              prefix={<MailOutlined />}
              maxLength={320}
              autoComplete="email"
            />
          </Form.Item>

          <Form.Item
            label="Password"
            name="password"
            rules={[
              { required: true, message: 'Please enter your password' },
              { min: mode === 'signup' ? 8 : 1 },
              { max: 120 },
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
            disabled={restoring}
            block
          >
            {mode === 'signup' ? 'Create account' : 'Sign in'}
          </Button>
        </Form>
      </Space>
    </Card>
  );
}
