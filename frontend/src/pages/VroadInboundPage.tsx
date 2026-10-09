import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  Drawer,
  Input,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  ApiOutlined,
  LinkOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { getApiErrorMessage } from '../services/api';
import {
  fetchInboundBatches,
  fetchInboundDetail,
  stageInboundBatch,
  type InboundBatch,
  type InboundRecord,
  type ProposedInboundRequest,
} from '../services/vroadInboundApi';

const { Title, Text, Paragraph } = Typography;

export const VroadInboundPage = () => {
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [payload, setPayload] = useState(() => JSON.stringify({
    requestKey: `demo-ui-${Date.now()}`, schemaVersion: 'demo-proposal-v1',
    records: [{ sourceId: 'DEMO-DEF-001', kind: 'DEFECT', routeName: 'Tuyến minh họa' }],
  }, null, 2));

  const batches = useQuery({
    queryKey: ['inbound-batches', page],
    queryFn: () => fetchInboundBatches(page),
  });

  const detail = useQuery({
    queryKey: ['inbound-detail', selectedId],
    queryFn: () => fetchInboundDetail(selectedId!),
    enabled: selectedId !== null,
  });

  const stage = useMutation({
    mutationFn: stageInboundBatch,
    onSuccess: (result) => {
      setSelectedId(result.batch.id);
      message.success('Đã đưa lô dữ liệu vào vùng chờ kiểm tra');
      void client.invalidateQueries({ queryKey: ['inbound-batches'] });
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });

  const submit = () => {
    try {
      const parsed: unknown = JSON.parse(payload);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('JSON không hợp lệ');
      stage.mutate(parsed as ProposedInboundRequest);
    } catch {
      message.error('Cần JSON hợp lệ gồm mã lô, phiên bản và danh sách bản ghi');
    }
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={2} style={{ marginBottom: 4 }}>
          Tích hợp VroadAI
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Bản demo chỉ lưu lô dữ liệu nhận thủ công; chưa kết nối với VroadAI.
          Chỉ tiếp nhận JSON do quản trị viên chủ động gửi tới bản demo.
        </Paragraph>
      </div>

      <Alert
        type="warning"
        showIcon
        message="Chưa kết nối với VroadAI"
        description="Chưa có hợp đồng API hoặc quyền truy cập nguồn. Lô nhập chỉ lưu trong vùng chờ của bản demo; chưa tự tạo tài sản và không gọi hệ thống ngoài."
      />

      <Card title={<Space><ApiOutlined /><span>API tiếp nhận bản demo</span></Space>}
        extra={<Button href="/swagger-ui/index.html" target="_blank" rel="noopener noreferrer">
          Swagger UI <LinkOutlined />
        </Button>}>
        <Space direction="vertical">
          <Text>Chỉ quản trị viên có quyền gửi lô JSON tới API nội bộ. Dữ liệu được kiểm tra và lưu ở vùng chờ.</Text>
          <Text code>POST /api/vroad/inbound/batches · demo-proposal-v1</Text>
          <a href="/v3/api-docs" target="_blank" rel="noopener noreferrer">Xem đặc tả OpenAPI JSON</a>
        </Space>
      </Card>
      <Card
        title={
          <Space>
            <FileTextOutlined style={{ color: '#fa8c16' }} />
            <span>Tiếp nhận Gói Dữ liệu Khảo sát (JSON Payload)</span>
          </Space>
        }
      >
        <Input.TextArea
          aria-label="Nội dung lô JSON"
          rows={6}
          value={payload}
          style={{ fontFamily: 'monospace' }}
          onChange={(event) => setPayload(event.target.value)}
        />
        <Button
          type="primary"
          loading={stage.isPending}
          onClick={submit}
          style={{ marginTop: 12 }}
        >
          Kiểm tra và lưu chờ
        </Button>
      </Card>

      <Card title="Lịch sử lô dữ liệu chờ kiểm tra">
        {batches.error && <Alert type="error" message={getApiErrorMessage(batches.error)} />}
        <Table<InboundBatch>
          rowKey="id"
          dataSource={batches.data ?? []}
          loading={batches.isLoading}
          pagination={false}
          scroll={{ x: 720 }}
          onRow={(row) => ({
            onClick: () => setSelectedId(row.id),
            style: { cursor: 'pointer' },
          })}
          columns={[
            { title: 'Mã gói dữ liệu', dataIndex: 'requestKey' },
            {
              title: 'Thời gian tiếp nhận',
              dataIndex: 'receivedAt',
              render: (value: number) => new Date(value).toLocaleString('vi-VN'),
            },
            {
              title: 'Chờ đối soát',
              dataIndex: 'pending',
              render: (value: number) => <Tag color="gold">{value} bản ghi</Tag>,
            },
            {
              title: 'Cần hiệu chỉnh',
              dataIndex: 'invalid',
              render: (value: number) => (
                <Tag color={value ? 'red' : 'default'}>{value} bản ghi</Tag>
              ),
            },
            {
              title: 'Trạng thái',
              render: () => <Tag color="blue">Đã đưa vào vùng chờ</Tag>,
            },
          ]}
        />
        <Space style={{ marginTop: 12 }}>
          <Button disabled={page === 0} onClick={() => setPage(page - 1)}>
            Trang trước
          </Button>
          <Text>Trang {page + 1}</Text>
          <Button
            disabled={(batches.data?.length ?? 0) < 20}
            onClick={() => setPage(page + 1)}
          >
            Trang sau
          </Button>
        </Space>
      </Card>

      <Drawer
        title="Chi tiết Tiếp nhận Gói Dữ liệu Khảo sát"
        open={selectedId !== null}
        width={760}
        onClose={() => setSelectedId(null)}
      >
        {detail.error && <Alert type="error" message={getApiErrorMessage(detail.error)} />}
        {detail.data && (
          <>
            <Descriptions bordered size="small" column={2} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Mã gói">{detail.data.batch.requestKey}</Descriptions.Item>
              <Descriptions.Item label="Phiên bản chuẩn">{detail.data.batch.schemaVersion}</Descriptions.Item>
              <Descriptions.Item label="Chờ đối soát">
                <Tag color="gold">{detail.data.batch.pending}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Cần hiệu chỉnh">
                <Tag color={detail.data.batch.invalid ? 'red' : 'default'}>{detail.data.batch.invalid}</Tag>
              </Descriptions.Item>
            </Descriptions>

            <Divider style={{ margin: '16px 0' }}>Danh sách Bản ghi trong Gói</Divider>

            <Table<InboundRecord>
              rowKey="id"
              size="small"
              pagination={{ pageSize: 10 }}
              dataSource={detail.data.records}
              columns={[
                { title: 'Mã nguồn', dataIndex: 'sourceRecordKey' },
                { title: 'Phân loại', dataIndex: 'recordKind' },
                {
                  title: 'Kết quả kiểm tra',
                  dataIndex: 'status',
                  render: (value: string) => (
                    <Tag color={value === 'PENDING' ? 'gold' : 'red'}>
                      {value === 'PENDING' ? 'Chờ đối soát' : 'Không hợp lệ'}
                    </Tag>
                  ),
                },
                {
                  title: 'Ghi chú kiểm tra',
                  dataIndex: 'validationErrors',
                  render: (errors: string[]) => errors.join('; ') || 'Chưa phát hiện lỗi cấu trúc',
                },
              ]}
            />
          </>
        )}
      </Drawer>
    </Space>
  );
};
