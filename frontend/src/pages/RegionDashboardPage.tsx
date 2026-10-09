import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Card, Col, Drawer, Input, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { apiClient, getApiErrorMessage } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

interface RegionSummary {
  branchId: string;
  totalRecords: number;
  totalDatasets: number;
  demoRecords: number;
  sourceStatus: string;
}

interface RegionAsset {
  id: number;
  datasetKey: string;
  recordKey: string;
  displayName: string;
  routeName: string | null;
  recordStatus: string;
  isDemo: boolean;
}

interface RegionPage {
  content: RegionAsset[];
  totalElements: number;
  page: number;
  size: number;
}

interface ReviewItem {
  status: string;
  version: number;
  reason: string | null;
  submittedAt: string | null;
  reviewedAt: string | null;
}

const reviewLabels: Record<string, string> = {
  'CHƯA_GỬI': 'Chưa gửi kiểm tra',
  IN_REVIEW: 'Đang chờ kiểm tra',
  APPROVED: 'Đã kiểm tra nội bộ',
  RETURNED: 'Trả lại để bổ sung',
};

const columns: TableColumnsType<RegionAsset> = [
  { title: 'Mã bản ghi', dataIndex: 'recordKey', key: 'recordKey', ellipsis: true },
  { title: 'Tên tài sản', dataIndex: 'displayName', key: 'displayName', ellipsis: true },
  { title: 'Phân loại', key: 'origin', render: (_, record) => <Tag color={record.isDemo ? 'orange' : 'blue'}>{record.isDemo ? 'MÔ PHỎNG' : 'Dữ liệu nguồn'}</Tag> },
  { title: 'Tuyến (theo nguồn)', dataIndex: 'routeName', key: 'routeName', render: (name: string | null) => name || 'Chưa gán' },
  { title: 'Trạng thái nhập', dataIndex: 'recordStatus', key: 'recordStatus', render: (status: string) => <Tag>{status}</Tag> },
];

export const RegionDashboardPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  const summary = useQuery({
    queryKey: ['region-summary', user?.branchId, user?.id],
    queryFn: async () => (await apiClient.get<RegionSummary>('/api/region/summary')).data,
  });
  const records = useQuery({
    queryKey: ['region-assets', user?.branchId, user?.id, page, keyword],
    queryFn: async () => (await apiClient.get<RegionPage>('/api/region/assets', {
      params: { page, size: 20, q: keyword || undefined },
    })).data,
  });
  const detail = useQuery({
    queryKey: ['region-asset', user?.branchId, user?.id, selectedId],
    queryFn: async () => (await apiClient.get<RegionAsset>(`/api/region/assets/${selectedId}`)).data,
    enabled: selectedId !== null,
  });
  const review = useQuery({
    queryKey: ['region-review', user?.branchId, user?.id, selectedId],
    queryFn: async () => (await apiClient.get<ReviewItem>(`/api/region/assets/${selectedId}/review`)).data,
    enabled: selectedId !== null && !!detail.data && ['ROLE_EDITOR', 'ROLE_MANAGER'].includes(user?.role || ''),
  });
  const reviewMutation = useMutation({
    mutationFn: async ({ id, decision, explanation }: { id: number; decision?: 'APPROVED' | 'RETURNED'; explanation?: string }) => {
      const path = `/api/region/assets/${id}/${decision ? 'decision' : 'submit'}`;
      return (await apiClient.post<ReviewItem>(path, decision ? { decision, reason: explanation } : undefined)).data;
    },
    onSuccess: () => {
      setActionError(null);
      setReason('');
      void queryClient.invalidateQueries({ queryKey: ['region-review'] });
      message.success('Đã cập nhật quy trình kiểm tra bản ghi mô phỏng.');
    },
    onError: (error: unknown) => setActionError(getApiErrorMessage(error)),
  });

  const selectRecord = (id: number) => {
    setReason('');
    setActionError(null);
    setSelectedId(id);
  };

  const returnRecord = () => {
    if (selectedId === null) return;
    if (!reason.trim()) {
      setActionError('Vui lòng nhập lý do trả lại.');
      return;
    }
    reviewMutation.mutate({ id: selectedId, decision: 'RETURNED', explanation: reason.trim() });
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Typography.Title level={2} style={{ marginBottom: 4 }}>Bảng điều hành đơn vị</Typography.Title>
        <Typography.Text type="secondary">Phạm vi tài khoản: {user?.branchId || 'Chưa được gán'}</Typography.Text>
      </div>
      <Alert type="info" showIcon message="Phân tuyến thử nghiệm trên dữ liệu nguồn"
        description="Tuyến được quản trị cấp riêng cho từng tài khoản, không phải xác nhận địa bàn quản lý chính thức. Người xem chỉ nhận bản ghi đã kiểm tra nội bộ; dữ liệu thô và bản ghi mô phỏng vẫn tách biệt." />
      {summary.isError && <Alert type="error" showIcon message="Không tải được số liệu đơn vị" />}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card><Statistic title="Bản ghi nguồn trong phạm vi" value={Math.max(0, (summary.data?.totalRecords || 0) - (summary.data?.demoRecords || 0))} loading={summary.isLoading} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="Bản ghi MÔ PHỎNG" value={summary.data?.demoRecords || 0} loading={summary.isLoading} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="Nhóm dữ liệu trong phạm vi" value={summary.data?.totalDatasets || 0} loading={summary.isLoading} /></Card>
        </Col>
      </Row>
      <Card title="Tra cứu tài sản trong phạm vi" extra={<Input.Search placeholder="Mã, tên hoặc tuyến" allowClear
        onSearch={(value) => { setPage(0); setKeyword(value.trim()); }} style={{ width: 245 }} />}>
        {records.isError && <Alert type="error" showIcon message="Không tải được danh sách tài sản" style={{ marginBottom: 16 }} />}
        <Table<RegionAsset> rowKey="id" columns={columns} dataSource={records.data?.content || []}
          loading={records.isLoading} scroll={{ x: 780 }} onRow={(record) => ({ onClick: () => selectRecord(record.id), style: { cursor: 'pointer' } })}
          pagination={{ current: page + 1, pageSize: 20, total: records.data?.totalElements || 0, showSizeChanger: false,
            showTotal: (total) => `${total} bản ghi`, onChange: (nextPage) => setPage(nextPage - 1) }} />
      </Card>
      <Drawer title="Chi tiết trong phạm vi đơn vị" open={selectedId !== null} onClose={() => setSelectedId(null)} width={440}>
        {detail.isLoading && <Typography.Text>Đang tải...</Typography.Text>}
        {detail.isError && <Alert type="error" message="Không thể xem bản ghi này" />}
        {detail.data && <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Text strong>{detail.data.displayName}</Typography.Text>
          <Tag color={detail.data.isDemo ? 'orange' : 'blue'}>{detail.data.isDemo ? 'MÔ PHỎNG — không phải dữ liệu nguồn' : 'Dữ liệu nguồn'}</Tag>
          <Typography.Text>Mã: {detail.data.recordKey}</Typography.Text>
          <Typography.Text>Nhóm: {detail.data.datasetKey}</Typography.Text>
          <Typography.Text>Tuyến: {detail.data.routeName || 'Chưa gán'}</Typography.Text>
          <Typography.Text>Trạng thái nhập: {detail.data.recordStatus}</Typography.Text>
          {['vroad_assets', 'vroad_defects'].includes(detail.data.datasetKey) && <Button onClick={() => navigate(
            detail.data.datasetKey === 'vroad_assets' ? `/map?assetRecordId=${detail.data.id}` : `/map?defectRecordId=${detail.data.id}`
          )}>Định vị bản ghi trên bản đồ</Button>}
          {detail.data.datasetKey === 'vroad_iri' && <Alert type="info" message="Đoạn IRI chỉ có lý trình; chưa có hình tuyến để định vị chính xác." />}
          {['ROLE_EDITOR', 'ROLE_MANAGER'].includes(user?.role || '') && <Card size="small" title={detail.data.isDemo ? 'Kiểm tra bản ghi mô phỏng' : 'Kiểm duyệt dữ liệu nguồn (nội bộ demo)'}>
            {review.isLoading && <Typography.Text>Đang tải trạng thái...</Typography.Text>}
            {review.isError && <Alert type="error" message="Không tải được trạng thái kiểm tra" />}
            {review.data && <Space direction="vertical" style={{ width: '100%' }}>
              <Tag color={review.data.status === 'APPROVED' ? 'green' : review.data.status === 'RETURNED' ? 'red' : 'gold'}>
                {reviewLabels[review.data.status] || review.data.status}
              </Tag>
              {review.data.reason && <Typography.Text>Lý do/ghi chú: {review.data.reason}</Typography.Text>}
              {user?.role === 'ROLE_EDITOR' && ['CHƯA_GỬI', 'RETURNED'].includes(review.data.status) && selectedId !== null &&
                <Button type="primary" loading={reviewMutation.isPending} onClick={() => reviewMutation.mutate({ id: selectedId })}>
                  Gửi quản lý kiểm tra
                </Button>}
              {user?.role === 'ROLE_MANAGER' && review.data.status === 'IN_REVIEW' && selectedId !== null && <>
                <Input.TextArea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000}
                  placeholder="Lý do trả lại (bắt buộc khi trả về)" rows={3} aria-label="Lý do trả lại" />
                <Space>
                  <Button type="primary" loading={reviewMutation.isPending} onClick={() => reviewMutation.mutate({ id: selectedId, decision: 'APPROVED' })}>Xác nhận kiểm tra</Button>
                  <Button danger loading={reviewMutation.isPending} onClick={returnRecord}>Trả lại</Button>
                </Space>
              </>}
              {actionError && <Alert type="error" message={actionError} showIcon />}
            </Space>}
          </Card>}
        </Space>}
      </Drawer>
    </Space>
  );
};
