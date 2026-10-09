import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Empty, Row, Space, Spin, Statistic, Tag, Typography } from 'antd';
import { ArrowRightOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { getApiErrorMessage, shouldRetryQuery } from '../services/api';
import { fetchOperationsSummary, type OperationsCount } from '../services/operationsApi';

const { Title, Text } = Typography;

const CountsChart = ({ values, color }: { values: OperationsCount[]; color: string }) => {
  if (!values.length) return <Empty description="Chưa có số liệu trong phạm vi này" />;
  const peak = Math.max(1, ...values.map((value) => value.count));
  return <div style={{ maxHeight: 350, overflowY: 'auto', paddingRight: 10 }}>
    {values.map((value) => <div key={value.code} style={{ marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 6 }}>
        <Text ellipsis={{ tooltip: value.label }}>{value.label}</Text>
        <Text strong>{value.count.toLocaleString('vi-VN')}</Text>
      </div>
      <div role="img" aria-label={`${value.label}: ${value.count.toLocaleString('vi-VN')}`}
        style={{ background: '#eff2f7', height: 10, borderRadius: 8 }}>
        <div style={{ width: `${value.count / peak * 100}%`, height: '100%', borderRadius: 8,
          background: color, transition: 'width 400ms ease-out' }} />
      </div>
    </div>)}
  </div>;
};

export const OperationsDashboardPage = () => {
  const navigate = useNavigate();
  const summary = useQuery({ queryKey: ['operationsSummary'], queryFn: fetchOperationsSummary,
    retry: shouldRetryQuery });
  const data = summary.data;
  const peak = Math.max(1, ...(data?.auditActivity.map((day) => day.count) || []));

  return <Space direction="vertical" size="large" style={{ width: '100%' }}>
    <div style={{ padding: '26px 30px', borderRadius: 16,
      background: 'linear-gradient(125deg, #123055, #215488)', color: 'white' }}>
      <Space style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap' }}>
        <div>
          <Title level={2} style={{ color: 'white', marginBottom: 4 }}>Vận hành hệ thống</Title>
          <Text style={{ color: '#d6e8ff' }}>Tổng hợp trực tiếp từ dữ liệu đang lưu trên bản demo.</Text>
        </div>
        <Button icon={<ReloadOutlined />} loading={summary.isFetching}
          onClick={() => void summary.refetch()}>Làm mới</Button>
      </Space>
      {data && <Text style={{ display: 'block', color: '#d6e8ff', marginTop: 14 }}>
        Cập nhật: {new Date(data.calculatedAt).toLocaleString('vi-VN')}
      </Text>}
    </div>
    {summary.isLoading && <Spin tip="Đang tổng hợp hệ thống"><div style={{ minHeight: 200 }} /></Spin>}
    {summary.isError && <Alert type="error" showIcon message="Không thể tải tình trạng hệ thống"
      description={getApiErrorMessage(summary.error)} action={<Button onClick={() => void summary.refetch()}>Thử lại</Button>} />}
    {data && <>
      <Row gutter={[16, 16]}>
        <Col xs={12} lg={8} xl={4}><Card><Statistic title="Bản ghi" value={data.rawRecords} /></Card></Col>
        <Col xs={12} lg={8} xl={4}><Card><Statistic title="Tập dữ liệu" value={data.datasetCount} /></Card></Col>
        <Col xs={12} lg={8} xl={4}><Card><Statistic title="Tài khoản hoạt động" value={data.activeUsers}
          suffix={<Text type="secondary" style={{ fontSize: 14 }}>/ {data.totalUsers}</Text>} /></Card></Col>
        <Col xs={12} lg={8} xl={4}><Card><Statistic title="Hồ sơ tài liệu" value={data.storedDocuments} /></Card></Col>
        <Col xs={12} lg={8} xl={4}><Card><Statistic title="Hư hỏng đã xử lý" value={data.resolvedCases} /></Card></Col>
        <Col xs={12} lg={8} xl={4}><Card><Statistic title="Lô tiếp nhận chờ đối soát" value={data.stagedBatches} /></Card></Col>
      </Row>
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}><Card title="Bản ghi theo tập dữ liệu"
          extra={<Button type="link" onClick={() => navigate('/assets')}>Danh mục <ArrowRightOutlined /></Button>}>
          <CountsChart values={data.datasets} color="#215488" />
        </Card></Col>
        <Col xs={24} xl={12}><Card title="Nhật ký thao tác trong 7 ngày"
          extra={<Button type="link" onClick={() => navigate('/admin/audit-logs')}>Xem nhật ký <ArrowRightOutlined /></Button>}>
          <Row gutter={12} align="bottom" style={{ minHeight: 220 }}>
            {data.auditActivity.map((day) => <Col key={day.day} flex="1 1 0" style={{ textAlign: 'center' }}>
              <Text strong>{day.count}</Text>
              <div role="img" aria-label={`${day.day}: ${day.count} thao tác`}
                style={{ height: 160, display: 'flex', alignItems: 'end', background: '#f1f4f8',
                  borderRadius: 6, margin: '6px auto', maxWidth: 55 }}>
                <div style={{ width: '100%', height: `${day.count / peak * 100}%`, background: '#e08d47',
                  minHeight: day.count ? 4 : 0, borderRadius: 6, transition: 'height 400ms ease-out' }} />
              </div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {day.day.slice(8, 10)}/{day.day.slice(5, 7)}
              </Text>
            </Col>)}
          </Row>
        </Card></Col>
      </Row>
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}><Card title="Hồ sơ theo đơn vị"
          extra={<Button type="link" onClick={() => navigate('/documents')}>Quản lý hồ sơ <ArrowRightOutlined /></Button>}>
          <CountsChart values={data.documentBranches} color="#158a7a" />
        </Card></Col>
        <Col xs={24} xl={12}><Card title="Quản trị và tiếp nhận dữ liệu">
          <CountsChart values={data.activeRoles} color="#7965b0" />
          <Space wrap style={{ marginTop: 12 }}>
            <Tag color="processing">{data.pendingRecords} bản ghi chờ rà soát</Tag>
            <Tag color={data.invalidRecords ? 'warning' : 'default'}>{data.invalidRecords} bản ghi cần sửa</Tag>
            <Tag color="orange">{data.simulatedTrafficObservations} quan sát giao thông mô phỏng</Tag>
            <Tag color="processing">{data.importedTrafficObservations} quan sát giao thông nhập</Tag>
            <Tag color="cyan">{data.trafficDensitySnapshots} lát cắt mật độ</Tag>
          </Space>
          <div style={{ marginTop: 16 }}>
            <Button onClick={() => navigate('/admin/vroad-inbound')}>Kiểm tra lô tiếp nhận</Button>
            <Button type="link" onClick={() => navigate('/admin/users')}>Tài khoản</Button>
          </div>
          {data.lastRawImportAt && <Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
            Nạp bản ghi gần nhất: {new Date(data.lastRawImportAt).toLocaleString('vi-VN')}
          </Text>}
        </Card></Col>
      </Row>
      <Card title="Lượt xe theo nhóm phương tiện"
        extra={<Button type="link" onClick={() => navigate('/traffic')}>Xem theo trạm <ArrowRightOutlined /></Button>}>
        <CountsChart values={data.trafficVehiclesByClass} color="#d78434" />
      </Card>
    </>}
  </Space>;
};
