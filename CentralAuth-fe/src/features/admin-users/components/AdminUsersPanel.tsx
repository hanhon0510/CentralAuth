import { ReloadOutlined, SearchOutlined, UserSwitchOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Form, Input, InputNumber, Select, Space, Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { fetchAdminUsers, updateAdminUserStatus } from '../api/adminUsersApi'
import { accountStatuses } from '../types/adminUsers'
import type { AccountStatus, AdminUser, AdminUserFilters } from '../types/adminUsers'
import { useI18n } from '../../../shared/i18n/useI18n'

type AdminUsersPanelProps = {
  token: string
}

type AdminUsersFormValues = {
  email?: string
  limit?: number
  status?: AccountStatus
}

const defaultFilters: AdminUserFilters = {
  limit: 50,
}

export function AdminUsersPanel({ token }: AdminUsersPanelProps) {
  const { t } = useI18n()
  const [form] = Form.useForm<AdminUsersFormValues>()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [filters, setFilters] = useState<AdminUserFilters>(defaultFilters)
  const [loading, setLoading] = useState(true)
  const [updatingUserId, setUpdatingUserId] = useState('')
  const [error, setError] = useState('')

  const loadUsers = useCallback(
    async (nextFilters: AdminUserFilters) => {
      setLoading(true)
      setError('')
      try {
        setUsers(await fetchAdminUsers(token, nextFilters))
      } catch (requestError) {
        setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
      } finally {
        setLoading(false)
      }
    },
    [t, token],
  )

  useEffect(() => {
    let cancelled = false

    async function fetchInitialUsers() {
      try {
        const initialUsers = await fetchAdminUsers(token, defaultFilters)
        if (!cancelled) {
          setUsers(initialUsers)
        }
      } catch (requestError) {
        if (!cancelled) {
          setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void fetchInitialUsers()
    return () => {
      cancelled = true
    }
  }, [t, token])

  function handleFinish(values: AdminUsersFormValues) {
    const nextFilters = {
      email: values.email,
      limit: values.limit ?? defaultFilters.limit,
      status: values.status,
    }
    setFilters(nextFilters)
    void loadUsers(nextFilters)
  }

  async function handleStatusChange(user: AdminUser, nextStatus: AccountStatus) {
    if (user.accountStatus === nextStatus) {
      return
    }

    setUpdatingUserId(user.id)
    setError('')
    try {
      const updatedUser = await updateAdminUserStatus(token, user.id, nextStatus)
      setUsers((currentUsers) => currentUsers.map((currentUser) => (
        currentUser.id === updatedUser.id ? updatedUser : currentUser
      )))
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    } finally {
      setUpdatingUserId('')
    }
  }

  const columns: TableProps<AdminUser>['columns'] = [
    {
      title: t('adminUsers.email'),
      dataIndex: 'email',
      key: 'email',
      width: 240,
      render: (value: string, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{value}</Typography.Text>
          <Typography.Text type="secondary">{record.displayName ?? t('adminUsers.noDisplayName')}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('adminUsers.status'),
      dataIndex: 'accountStatus',
      key: 'accountStatus',
      width: 170,
      render: (value: AccountStatus) => <Tag color={statusColor(value)}>{value}</Tag>,
    },
    {
      title: t('adminUsers.roles'),
      dataIndex: 'roles',
      key: 'roles',
      width: 220,
      render: (roles: string[]) => (
        roles.length > 0
          ? roles.map((role) => <Tag key={role} color={role === 'ROLE_ADMIN' ? 'geekblue' : 'default'}>{role}</Tag>)
          : '-'
      ),
    },
    {
      title: t('adminUsers.emailVerified'),
      dataIndex: 'emailVerified',
      key: 'emailVerified',
      width: 150,
      render: (value: boolean) => value ? t('common.yes') : t('common.no'),
    },
    {
      title: t('adminUsers.enabled'),
      dataIndex: 'enabled',
      key: 'enabled',
      width: 120,
      render: (value: boolean) => value ? t('common.yes') : t('common.no'),
    },
    {
      title: t('adminUsers.userId'),
      dataIndex: 'id',
      key: 'id',
      width: 270,
      render: (value: string) => <Typography.Text code copyable>{value}</Typography.Text>,
    },
    {
      title: t('adminUsers.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: t('adminUsers.changeStatus'),
      key: 'changeStatus',
      fixed: 'right',
      width: 180,
      render: (_, record) => (
        <Select
          aria-label={t('adminUsers.changeStatus')}
          disabled={updatingUserId === record.id}
          loading={updatingUserId === record.id}
          onChange={(nextStatus) => void handleStatusChange(record, nextStatus)}
          options={accountStatuses.map((status) => ({ label: status, value: status }))}
          value={record.accountStatus}
          style={{ width: 150 }}
        />
      ),
    },
  ]

  return (
    <Card
      title={t('adminUsers.title')}
      extra={
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadUsers(filters)}>
          {t('adminUsers.refresh')}
        </Button>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {error ? <Alert type="error" showIcon message={error} /> : null}

        <Form
          form={form}
          layout="vertical"
          initialValues={defaultFilters}
          onFinish={handleFinish}
        >
          <Space className="admin-users-filter-row" align="end" wrap>
            <Form.Item name="status" label={t('adminUsers.status')} className="admin-users-filter-item">
              <Select
                allowClear
                options={accountStatuses.map((status) => ({ label: status, value: status }))}
                placeholder={t('adminUsers.anyStatus')}
              />
            </Form.Item>
            <Form.Item name="email" label={t('adminUsers.email')} className="admin-users-filter-item">
              <Input allowClear placeholder={t('adminUsers.emailPlaceholder')} />
            </Form.Item>
            <Form.Item name="limit" label={t('adminUsers.limit')} className="admin-users-filter-item-small">
              <InputNumber min={1} max={200} />
            </Form.Item>
            <Form.Item className="admin-users-filter-action">
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
                {t('adminUsers.applyFilters')}
              </Button>
            </Form.Item>
          </Space>
        </Form>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={users}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          scroll={{ x: 1540 }}
          title={() => (
            <Space>
              <UserSwitchOutlined />
              <Typography.Text>{t('adminUsers.tableTitle')}</Typography.Text>
            </Space>
          )}
        />
      </Space>
    </Card>
  )
}

function statusColor(status: AccountStatus) {
  switch (status) {
    case 'ACTIVE':
      return 'green'
    case 'LOCKED':
      return 'red'
    case 'UNVERIFIED':
      return 'orange'
    case 'DISABLED':
      return 'default'
  }
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(date)
}
