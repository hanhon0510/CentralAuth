import { ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Form, Input, InputNumber, Select, Space, Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { fetchAuditLogs } from '../api/auditApi'
import type { AuditLog, AuditLogFilters } from '../types/audit'
import { useI18n } from '../../../shared/i18n/useI18n'

type AuditLogPanelProps = {
  token: string
}

type AuditLogFormValues = {
  email?: string
  eventType?: string
  limit?: number
  userId?: string
}

const defaultFilters: AuditLogFilters = {
  limit: 50,
}

const eventTypes = [
  'USER_REGISTERED',
  'USER_VERIFIED',
  'LOGIN_SUCCEEDED',
  'LOGIN_FAILED',
  'USER_LOGGED_OUT',
  'PASSWORD_RESET_REQUESTED',
  'PASSWORD_CHANGED',
  'ADMIN_USER_STATUS_CHANGED',
]

export function AuditLogPanel({ token }: AuditLogPanelProps) {
  const { t } = useI18n()
  const [form] = Form.useForm<AuditLogFormValues>()
  const [logs, setLogs] = useState<AuditLog[]>([])
  const [filters, setFilters] = useState<AuditLogFilters>(defaultFilters)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadLogs = useCallback(
    async (nextFilters: AuditLogFilters) => {
      setLoading(true)
      setError('')
      try {
        setLogs(await fetchAuditLogs(token, nextFilters))
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

    async function fetchInitialLogs() {
      try {
        const initialLogs = await fetchAuditLogs(token, defaultFilters)
        if (!cancelled) {
          setLogs(initialLogs)
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

    void fetchInitialLogs()
    return () => {
      cancelled = true
    }
  }, [t, token])

  function handleFinish(values: AuditLogFormValues) {
    const nextFilters = {
      email: values.email,
      eventType: values.eventType,
      limit: values.limit ?? defaultFilters.limit,
      userId: values.userId,
    }
    setFilters(nextFilters)
    void loadLogs(nextFilters)
  }

  const columns: TableProps<AuditLog>['columns'] = [
    {
      title: t('audit.occurredAt'),
      dataIndex: 'occurredAt',
      key: 'occurredAt',
      width: 210,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: t('audit.eventType'),
      dataIndex: 'eventType',
      key: 'eventType',
      width: 210,
      render: (value: string) => <Tag color={eventTypeColor(value)}>{value}</Tag>,
    },
    {
      title: t('audit.email'),
      dataIndex: 'email',
      key: 'email',
      width: 220,
      render: (value: string | null) => value ?? '-',
    },
    {
      title: t('audit.clientIp'),
      dataIndex: 'clientIp',
      key: 'clientIp',
      width: 150,
      render: (value: string | null) => value ?? '-',
    },
    {
      title: t('audit.reason'),
      dataIndex: 'reason',
      key: 'reason',
      width: 190,
      render: (value: string | null) => value ?? '-',
    },
    {
      title: t('audit.userId'),
      dataIndex: 'userId',
      key: 'userId',
      width: 270,
      render: (value: string | null) => (
        value ? <Typography.Text code copyable>{value}</Typography.Text> : '-'
      ),
    },
  ]

  return (
    <Card
      title={t('audit.title')}
      extra={
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadLogs(filters)}>
          {t('audit.refresh')}
        </Button>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {error ? <Alert type="error" showIcon title={error} /> : null}

        <Form
          form={form}
          layout="vertical"
          initialValues={defaultFilters}
          onFinish={handleFinish}
        >
          <Space className="audit-filter-row" align="end" wrap>
            <Form.Item name="eventType" label={t('audit.eventType')} className="audit-filter-item">
              <Select
                allowClear
                options={eventTypes.map((eventType) => ({ label: eventType, value: eventType }))}
                placeholder={t('audit.anyEventType')}
              />
            </Form.Item>
            <Form.Item name="email" label={t('audit.email')} className="audit-filter-item">
              <Input allowClear placeholder={t('audit.emailPlaceholder')} />
            </Form.Item>
            <Form.Item name="userId" label={t('audit.userId')} className="audit-filter-item audit-filter-item-wide">
              <Input allowClear placeholder={t('audit.userIdPlaceholder')} />
            </Form.Item>
            <Form.Item name="limit" label={t('audit.limit')} className="audit-filter-item-small">
              <InputNumber min={1} max={200} />
            </Form.Item>
            <Form.Item className="audit-filter-action">
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
                {t('audit.applyFilters')}
              </Button>
            </Form.Item>
          </Space>
        </Form>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={logs}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          scroll={{ x: 1250 }}
          expandable={{
            expandedRowRender: (record) => (
              <pre className="audit-payload">{formatPayload(record.payloadJson)}</pre>
            ),
          }}
        />
      </Space>
    </Card>
  )
}

function eventTypeColor(eventType: string) {
  if (eventType.includes('FAILED')) return 'red'
  if (eventType.includes('PASSWORD')) return 'orange'
  if (eventType.includes('LOGIN')) return 'blue'
  return 'green'
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

function formatPayload(payloadJson: string) {
  try {
    return JSON.stringify(JSON.parse(payloadJson), null, 2)
  } catch {
    return payloadJson
  }
}
