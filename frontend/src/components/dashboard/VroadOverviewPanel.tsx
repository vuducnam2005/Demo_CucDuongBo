import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Row, Skeleton, Statistic, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { getApiErrorMessage } from '../../services/api';
import { fetchSurveyOverview, type SurveyOverview } from '../../services/vroadApi';

const { Title, Text } = Typography;

type Count = { label: string; count: number };

const BreakdownChart = ({ title, rows, color }: { title: string; rows: Count[]; color: string }) => {
  const largest = Math.max(1, ...rows.map((item) => item.count));
  return <Card title={title} size="small" style={{ height: '100%' }}>
    {rows.length === 0 ? <Text type="secondary">Chưa có dữ liệu.</Text> :
      <div role="img" aria-label={`Biểu đồ ${title}`}>
        {rows.map((item) => <div key={item.label} style={{ marginBottom: 14 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
            <Text ellipsis title={item.label}>{item.label}</Text>
            <Text strong>{item.count.toLocaleString('vi-VN')}</Text>
          </div>
          <div style={{ height: 10, borderRadius: 5, background: '#edf2f8', overflow: 'hidden', marginTop: 5 }}>
            <div style={{ width: `${Math.max(1, item.count / largest * 100)}%`, height: '100%', background: color,
              borderRadius: 5, transition: 'width 350ms ease' }} />
          </div>
        </div>)}
      </div>}
  </Card>;
};

export const VroadOverviewPanel = () => {
  const navigate = useNavigate();
  const { data, isLoading, error, refetch } = useQuery<SurveyOverview>({
    queryKey: ['vroadOverview'], queryFn: fetchSurveyOverview, staleTime: 60000,
  });

  return <section aria-label="Thống kê khảo sát đường bộ" style={{ marginBottom: 24 }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
      <div><Title level={4} style={{ marginBottom: 0 }}>Tình hình khảo sát đường bộ</Title>
        <Text type="secondary">Số liệu đã nhập để đối soát; hồ sơ chưa duyệt không phải tài sản đã công bố.</Text></div>
      <Button onClick={() => navigate('/map')}>Xem bản đồ</Button>
    </div>
    {error && <Alert type="error" message={getApiErrorMessage(error)}
      action={<Button onClick={() => void refetch()}>Thử lại</Button>} style={{ marginTop: 16 }} />}
    {isLoading && <Skeleton active style={{ marginTop: 16 }} />}
    {data && <>
      <Row gutter={[12, 12]} style={{ marginTop: 16, marginBottom: 16 }}>
        {([
          ['Tài sản khảo sát', data.assets], ['Điểm hư hỏng', data.defects],
          ['Đoạn IRI', data.iriSegments], ['Hồ sơ xử lý (demo)', data.resolvedCases],
        ] as const).map(([label, count]) => <Col key={label} xs={12} lg={6}>
          <Card size="small"><Statistic title={label} value={count} groupSeparator="." /></Card>
        </Col>)}
      </Row>
      <Row gutter={[12, 12]}>
        <Col xs={24} lg={12}><BreakdownChart title="Loại hư hỏng" rows={data.defectTypes} color="#e06360" /></Col>
        <Col xs={24} lg={12}><BreakdownChart title="Nhóm tài sản" rows={data.assetCategories} color="#3177b8" /></Col>
      </Row>
    </>}
  </section>;
};
