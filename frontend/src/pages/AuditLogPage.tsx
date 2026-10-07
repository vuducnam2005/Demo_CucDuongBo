import React, { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Table,
  Tag,
  Typography,
  Card,
  Space,
  Button,
  Modal,
  Descriptions,
} from 'antd';
import {
  AuditOutlined,
  EyeOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { fetchAuditLogs, AuditLogItem, shouldRetryQuery } from '../services/api';
import { TableToolbar, TableSkeleton, StandardPagination, QueryState } from '../components/common';

const { Title, Paragraph, Text } = Typography;

export const AuditLogPage: React.FC = () => {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [usernameFilter, setUsernameFilter] = useState('');
  const [selectedLog, setSelectedLog] = useState<AuditLogItem | null>(null);

  const { data, isLoading, refetch, isFetching, isError, error } = useQuery({
    queryKey: ['admin-audit-logs', page, pageSize, usernameFilter],
    queryFn: () => fetchAuditLogs(page, pageSize, usernameFilter || undefined),
    placeholderData: keepPreviousData,
    retry: shouldRetryQuery,
  });

  const getActionTag = (action: string) => {
    if (action.includes('LOGIN')) {
      return <Tag color="blue">{action}</Tag>;
    }
    if (action.includes('CREATE') || action.includes('INSERT')) {
      return <Tag color="green">{action}</Tag>;
    }
    if (action.includes('UPDATE')) {
      return <Tag color="orange">{action}</Tag>;
    }
    if (action.includes('DELETE')) {
      return <Tag color="red">{action}</Tag>;
    }
    return <Tag>{action}</Tag>;
  };

  const columns: TableColumnsType<AuditLogItem> = [
    {
      title: 'Thời gian',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (dateStr: string) => {
        try {
          const d = new Date(dateStr);
          return (
            <Space direction="horizontal" size={4}>
              <ClockCircleOutlined style={{ color: '#8c8c8c' }} />
              <span style={{ fontSize: 13 }}>{d.toLocaleString('vi-VN')}</span>
            </Space>
          );
        } catch {
          return dateStr;
        }
      },
    },
    {
      title: 'Tài khoản',
      dataIndex: 'username',
      key: 'username',
      width: 140,
      render: (user: string) => <strong>{user || 'Hệ thống'}</strong>,
    },
    {
      title: 'Hành động',
      dataIndex: 'action',
      key: 'action',
      width: 160,
      render: (act: string) => getActionTag(act),
    },
    {
      title: 'Đối tượng (Entity)',
      key: 'entity',
      render: (_value: unknown, record: AuditLogItem) => (
        <span>
          <Text strong>{record.entityType || '-'}</Text>
          {record.entityId && <Text type="secondary"> (#{record.entityId})</Text>}
        </span>
      ),
    },
    {
      title: 'Địa chỉ IP',
      dataIndex: 'ipAddress',
      key: 'ipAddress',
      width: 140,
      render: (ip: string) => <Text code>{ip || '-'}</Text>,
    },
    {
      title: 'Chi tiết',
      key: 'view',
      width: 100,
      render: (_value: unknown, record: AuditLogItem) => (
        <Button
          size="small"
          icon={<EyeOutlined />}
          onClick={() => setSelectedLog(record)}
        >
          Xem
        </Button>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Space align="center" size={10}>
          <AuditOutlined style={{ fontSize: 24, color: '#003a8c' }} />
          <div>
            <Title level={4} style={{ margin: 0 }}>
              Nhật ký Kiểm toán Hệ thống (Audit Log)
            </Title>
            <Paragraph type="secondary" style={{ margin: 0, fontSize: 13 }}>
              Ghi vết toàn bộ hành động đăng nhập, sửa đổi dữ liệu và thay đổi phân quyền để đảm bảo an toàn thông tin.
            </Paragraph>
          </div>
        </Space>
      </div>

      <Card variant="borderless" className="kcht-card">
        <TableToolbar
          searchValue={usernameFilter}
          onSearchChange={(val) => {
            setUsernameFilter(val);
            setPage(0);
          }}
          searchPlaceholder="Lọc theo tên tài khoản..."
          onRefresh={() => refetch()}
          isRefreshing={isFetching}
        />

        <QueryState
          isLoading={isLoading}
          isError={isError}
          error={error}
          isEmpty={!data?.content.length}
          loading={<TableSkeleton rows={pageSize > 10 ? 10 : pageSize} columns={6} />}
          emptyTitle={usernameFilter ? 'Không tìm thấy sự kiện phù hợp' : 'Chưa có bản ghi nhật ký kiểm toán nào'}
          emptyDescription={
            usernameFilter
              ? 'Không có sự kiện kiểm toán nào khớp với tài khoản đang lọc.'
              : 'Mọi thao tác quản trị và thay đổi cấu hình sẽ được ghi nhận tại đây.'
          }
          emptyActionText={usernameFilter ? 'Xóa bộ lọc' : 'Làm mới'}
          onEmptyAction={() => {
            if (usernameFilter) setUsernameFilter('');
            else void refetch();
          }}
          onRetry={() => void refetch()}
        >
          {data && (
          <>
            <Table<AuditLogItem>
              dataSource={data.content}
              columns={columns}
              rowKey="id"
              pagination={false}
              bordered
              size="middle"
            />
            <StandardPagination
              page={page}
              size={pageSize}
              totalElements={data.totalElements || 0}
              onChange={(newPage, newSize) => {
                setPage(newPage);
                setPageSize(newSize);
              }}
            />
          </>
          )}
        </QueryState>
      </Card>

      {/* Detail Modal */}
      <Modal
        open={Boolean(selectedLog)}
        title="Chi tiết sự kiện kiểm toán"
        onCancel={() => setSelectedLog(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setSelectedLog(null)}>
            Đóng
          </Button>,
        ]}
        width={650}
      >
        {selectedLog && (
          <Descriptions bordered column={1} size="small" style={{ marginTop: 12 }}>
            <Descriptions.Item label="Thời gian">
              {new Date(selectedLog.createdAt).toLocaleString('vi-VN')}
            </Descriptions.Item>
            <Descriptions.Item label="Tài khoản thực hiện">{selectedLog.username}</Descriptions.Item>
            <Descriptions.Item label="Hành động">{getActionTag(selectedLog.action)}</Descriptions.Item>
            <Descriptions.Item label="Loại đối tượng">{selectedLog.entityType || '-'}</Descriptions.Item>
            <Descriptions.Item label="Mã đối tượng">{selectedLog.entityId || '-'}</Descriptions.Item>
            <Descriptions.Item label="Địa chỉ IP">{selectedLog.ipAddress || '-'}</Descriptions.Item>
            <Descriptions.Item label="User Agent">
              <Text style={{ fontSize: 12 }}>{selectedLog.userAgent || '-'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Giá trị cũ (Old values)">
              <pre style={{ margin: 0, maxHeight: 120, overflow: 'auto', background: '#f5f5f5', padding: 8, fontSize: 12 }}>
                {selectedLog.oldValues || '(Không có)'}
              </pre>
            </Descriptions.Item>
            <Descriptions.Item label="Giá trị mới (New values)">
              <pre style={{ margin: 0, maxHeight: 120, overflow: 'auto', background: '#f5f5f5', padding: 8, fontSize: 12 }}>
                {selectedLog.newValues || '(Không có)'}
              </pre>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};
