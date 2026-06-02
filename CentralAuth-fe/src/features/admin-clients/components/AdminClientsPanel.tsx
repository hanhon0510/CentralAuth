import { AppstoreAddOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Form, Input, Modal, Space, Switch, Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { createAdminClient, fetchAdminClients, updateAdminClient, updateAdminClientActive } from '../api/adminClientsApi'
import { hasDuplicateLines, parseLines } from '../lib/clientMetadataForm'
import type { AdminClient, CreateClientPayload, UpdateClientPayload } from '../types/adminClients'
import { useI18n } from '../../../shared/i18n/useI18n'

type AdminClientsPanelProps = {
  token: string
}

type AdminClientFormValues = {
  active: boolean
  allowedOriginsText: string
  clientId?: string
  clientName: string
  logoutUrisText: string
  redirectUrisText: string
}

export function AdminClientsPanel({ token }: AdminClientsPanelProps) {
  const { t } = useI18n()
  const [form] = Form.useForm<AdminClientFormValues>()
  const [clients, setClients] = useState<AdminClient[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [togglingClientId, setTogglingClientId] = useState('')
  const [error, setError] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editingClient, setEditingClient] = useState<AdminClient | null>(null)

  const loadClients = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setClients(await fetchAdminClients(token))
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    } finally {
      setLoading(false)
    }
  }, [t, token])

  useEffect(() => {
    let cancelled = false

    async function fetchInitialClients() {
      try {
        const initialClients = await fetchAdminClients(token)
        if (!cancelled) {
          setClients(initialClients)
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

    void fetchInitialClients()
    return () => {
      cancelled = true
    }
  }, [t, token])

  function openCreateModal() {
    setEditingClient(null)
    setError('')
    form.setFieldsValue({
      active: true,
      allowedOriginsText: '',
      clientId: '',
      clientName: '',
      logoutUrisText: '',
      redirectUrisText: '',
    })
    setModalOpen(true)
  }

  function openEditModal(client: AdminClient) {
    setEditingClient(client)
    setError('')
    form.setFieldsValue({
      active: client.active,
      allowedOriginsText: client.allowedOrigins.join('\n'),
      clientId: client.clientId,
      clientName: client.clientName,
      logoutUrisText: client.logoutUris.join('\n'),
      redirectUrisText: client.redirectUris.join('\n'),
    })
    setModalOpen(true)
  }

  async function handleSave(values: AdminClientFormValues) {
    const redirectUris = parseLines(values.redirectUrisText)
    const allowedOrigins = parseLines(values.allowedOriginsText)
    const logoutUris = parseLines(values.logoutUrisText)

    if (redirectUris.length === 0) {
      form.setFields([{ name: 'redirectUrisText', errors: [t('adminClients.validation.redirectUris.required')] }])
      return
    }

    if (allowedOrigins.length === 0) {
      form.setFields([{ name: 'allowedOriginsText', errors: [t('adminClients.validation.allowedOrigins.required')] }])
      return
    }

    if (hasDuplicateLines(redirectUris)) {
      form.setFields([{ name: 'redirectUrisText', errors: [t('adminClients.validation.duplicateRedirectUris')] }])
      return
    }

    if (hasDuplicateLines(allowedOrigins)) {
      form.setFields([{ name: 'allowedOriginsText', errors: [t('adminClients.validation.duplicateOrigins')] }])
      return
    }

    if (hasDuplicateLines(logoutUris)) {
      form.setFields([{ name: 'logoutUrisText', errors: [t('adminClients.validation.duplicateLogoutUris')] }])
      return
    }

    setSaving(true)
    setError('')
    try {
      const payload: UpdateClientPayload = {
        active: values.active,
        allowedOrigins,
        clientName: values.clientName.trim(),
        logoutUris,
        redirectUris,
      }
      const savedClient = editingClient
        ? await updateAdminClient(token, editingClient.clientId, payload)
        : await createAdminClient(token, {
          ...payload,
          clientId: values.clientId?.trim() ?? '',
        } satisfies CreateClientPayload)

      setClients((currentClients) => upsertClient(currentClients, savedClient))
      setModalOpen(false)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    } finally {
      setSaving(false)
    }
  }

  async function handleActiveChange(client: AdminClient, active: boolean) {
    setTogglingClientId(client.clientId)
    setError('')
    try {
      const updatedClient = await updateAdminClientActive(token, client.clientId, active)
      setClients((currentClients) => upsertClient(currentClients, updatedClient))
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : t('common.requestFailed'))
    } finally {
      setTogglingClientId('')
    }
  }

  const columns: TableProps<AdminClient>['columns'] = [
    {
      title: t('adminClients.clientId'),
      dataIndex: 'clientId',
      key: 'clientId',
      width: 220,
      render: (value: string) => <Typography.Text code copyable>{value}</Typography.Text>,
    },
    {
      title: t('adminClients.clientName'),
      dataIndex: 'clientName',
      key: 'clientName',
      width: 220,
    },
    {
      title: t('adminClients.active'),
      dataIndex: 'active',
      key: 'active',
      width: 150,
      render: (value: boolean, record) => (
        <Space>
          <Switch
            checked={value}
            loading={togglingClientId === record.clientId}
            onChange={(nextActive) => void handleActiveChange(record, nextActive)}
            size="small"
          />
          <Tag color={value ? 'green' : 'default'}>{value ? t('adminClients.active') : t('adminClients.inactive')}</Tag>
        </Space>
      ),
    },
    {
      title: t('adminClients.redirectUris'),
      dataIndex: 'redirectUris',
      key: 'redirectUris',
      width: 360,
      render: (values: string[]) => <MetadataList values={values} />,
    },
    {
      title: t('adminClients.allowedOrigins'),
      dataIndex: 'allowedOrigins',
      key: 'allowedOrigins',
      width: 300,
      render: (values: string[]) => <MetadataList values={values} />,
    },
    {
      title: t('adminClients.logoutUris'),
      dataIndex: 'logoutUris',
      key: 'logoutUris',
      width: 360,
      render: (values: string[]) => <MetadataList values={values} />,
    },
    {
      title: t('adminClients.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: t('adminClients.actions'),
      key: 'actions',
      fixed: 'right',
      width: 120,
      render: (_, record) => (
        <Button icon={<EditOutlined />} onClick={() => openEditModal(record)}>
          {t('adminClients.edit')}
        </Button>
      ),
    },
  ]

  return (
    <Card
      title={t('adminClients.title')}
      extra={
        <Space className="admin-clients-toolbar" wrap>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadClients()}>
            {t('adminClients.refresh')}
          </Button>
          <Button type="primary" icon={<AppstoreAddOutlined />} onClick={openCreateModal}>
            {t('adminClients.create')}
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {error ? <Alert type="error" showIcon title={error} /> : null}

        <Table
          rowKey="clientId"
          loading={loading}
          columns={columns}
          dataSource={clients}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          scroll={{ x: 1920 }}
          title={() => (
            <Space>
              <AppstoreAddOutlined />
              <Typography.Text>{t('adminClients.tableTitle')}</Typography.Text>
            </Space>
          )}
        />
      </Space>

      <Modal
        open={modalOpen}
        title={editingClient ? t('adminClients.editTitle') : t('adminClients.createTitle')}
        okText={t('adminClients.save')}
        cancelText={t('adminClients.cancel')}
        confirmLoading={saving}
        onOk={() => form.submit()}
        onCancel={() => setModalOpen(false)}
      >
        <Form form={form} layout="vertical" onFinish={(values) => void handleSave(values)}>
          {!editingClient ? (
            <Form.Item
              name="clientId"
              label={t('adminClients.clientId')}
              rules={[{ required: true, message: t('adminClients.validation.clientId.required') }]}
            >
              <Input placeholder={t('adminClients.clientIdPlaceholder')} />
            </Form.Item>
          ) : null}

          <Form.Item
            name="clientName"
            label={t('adminClients.clientName')}
            rules={[{ required: true, message: t('adminClients.validation.clientName.required') }]}
          >
            <Input placeholder={t('adminClients.clientNamePlaceholder')} />
          </Form.Item>

          <Form.Item
            name="redirectUrisText"
            label={t('adminClients.redirectUris')}
            rules={[{ required: true, message: t('adminClients.validation.redirectUris.required') }]}
          >
            <Input.TextArea
              className="admin-clients-textarea"
              placeholder={t('adminClients.redirectUrisPlaceholder')}
              autoSize={{ minRows: 3, maxRows: 6 }}
            />
          </Form.Item>

          <Form.Item
            name="allowedOriginsText"
            label={t('adminClients.allowedOrigins')}
            rules={[{ required: true, message: t('adminClients.validation.allowedOrigins.required') }]}
          >
            <Input.TextArea
              className="admin-clients-textarea"
              placeholder={t('adminClients.allowedOriginsPlaceholder')}
              autoSize={{ minRows: 2, maxRows: 5 }}
            />
          </Form.Item>

          <Form.Item name="logoutUrisText" label={t('adminClients.logoutUris')}>
            <Input.TextArea
              className="admin-clients-textarea"
              placeholder={t('adminClients.logoutUrisPlaceholder')}
              autoSize={{ minRows: 2, maxRows: 5 }}
            />
          </Form.Item>

          <Form.Item name="active" label={t('adminClients.active')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

function MetadataList({ values }: { values: string[] }) {
  if (values.length === 0) {
    return '-'
  }

  return (
    <Space wrap size={[0, 4]} className="admin-clients-metadata-list">
      {values.map((value) => (
        <Tag key={value} className="admin-clients-metadata-tag">
          <Typography.Text copyable={{ text: value }} className="admin-clients-metadata-text">
            {value}
          </Typography.Text>
        </Tag>
      ))}
    </Space>
  )
}

function upsertClient(clients: AdminClient[], savedClient: AdminClient) {
  if (clients.some((client) => client.clientId === savedClient.clientId)) {
    return clients.map((client) => (client.clientId === savedClient.clientId ? savedClient : client))
  }

  return [savedClient, ...clients]
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
