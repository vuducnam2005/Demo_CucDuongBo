import { useEffect, useState } from 'react';
import { Alert, Button, Card, Drawer, Space, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { apiClient, getApiErrorMessage } from '../services/api';
import { fetchVroadCases, type CaseItem, type CasePage } from '../services/vroadApi';

const { Title, Text } = Typography;

export const VroadCasesPage = () => {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const recordQuery = params.get('record');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<CasePage | null>(null);
  const [selected, setSelected] = useState<CaseItem | null>(null);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    fetchVroadCases(page).then((data) => {
      if (!active) return;
      setResult(data);
      setError(null);
      const recordId = Number(recordQuery);
      if (recordId) setSelected(data.content.find((item) => item.recordId === recordId) || null);
    }).catch((cause) => { if (active) setError(getApiErrorMessage(cause)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [page, recordQuery]);

  useEffect(() => {
    let active = true;
    let temporaryUrl: string | null = null;
    setImageUrl(null);
    if (selected?.hasEvidence) {
      apiClient.get<Blob>(`/api/vroad/defects/${selected.recordId}/evidence`, { responseType: 'blob' })
        .then((response) => {
          if (!active) return;
          temporaryUrl = URL.createObjectURL(response.data);
          setImageUrl(temporaryUrl);
        }).catch((cause) => { if (active) setError(getApiErrorMessage(cause)); });
    }
    return () => { active = false; if (temporaryUrl) URL.revokeObjectURL(temporaryUrl); };
  }, [selected]);

  const columns: TableColumnsType<CaseItem> = [
    { title: 'Mã hư hỏng', dataIndex: 'recordKey', key: 'recordKey' },
    { title: 'Tuyến', dataIndex: 'routeName', key: 'routeName', ellipsis: true },
    { title: 'Lý trình', dataIndex: 'chainage', key: 'chainage' },
    { title: 'Người xử lý', dataIndex: 'resolvedBy', key: 'resolvedBy' },
    { title: 'Thời điểm xác nhận', dataIndex: 'resolvedAt', key: 'resolvedAt', render: (value: string) => new Date(value).toLocaleString('vi-VN') },
    { title: 'Trạng thái', key: 'status', render: () => <Tag color="green">Đã xử lý xong</Tag> },
  ];

  return <Space direction="vertical" size="large" style={{ width: '100%' }}>
    <div><Title level={2} style={{ marginBottom: 2 }}>Hồ sơ hư hỏng đã xử lý</Title>
      <Text type="secondary">Lưu trong PostgreSQL demo; dữ liệu khảo sát nguồn không bị sửa hay xóa.</Text></div>
    <Space><Button onClick={() => navigate('/map')}>Trở lại bản đồ</Button></Space>
    {error && <Alert type="error" message={error} showIcon />}
    <Card><Table<CaseItem> rowKey="recordId" columns={columns} dataSource={result?.content || []}
      loading={loading} scroll={{ x: 920 }} onRow={(item) => ({ onClick: () => setSelected(item), style: { cursor: 'pointer' } })}
      pagination={{ current: page + 1, pageSize: 20, total: result?.totalElements || 0, showSizeChanger: false,
        onChange: (number) => setPage(number - 1) }} /></Card>
    <Drawer title="Hồ sơ đã xử lý xong" open={!!selected} width={480} onClose={() => setSelected(null)}>
      {selected && <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Text strong>{selected.recordKey}</Text>
        <Button onClick={() => navigate(`/map?defectRecordId=${selected.recordId}`)}>Định vị hư hỏng trên bản đồ</Button>
        <Text>Tuyến: {selected.routeName}</Text>
        <Text>Lý trình: {selected.chainage || 'Không rõ'}</Text>
        <Text>Loại hư hỏng: {selected.defectType || 'Không rõ'}</Text>
        <Text>Đã xử lý xong bởi: {selected.resolvedBy}</Text>
        <Text>Thời điểm: {new Date(selected.resolvedAt).toLocaleString('vi-VN')}</Text>
        <Card size="small" title="Nội dung xử lý"><Text>{selected.resolutionNote}</Text></Card>
        {selected.hasEvidence ? imageUrl ? <img src={imageUrl} alt="Ảnh minh chứng sau xử lý" style={{ width: '100%', objectFit: 'contain' }} />
          : <Text>Đang tải ảnh minh chứng...</Text> : <Text type="secondary">Không có ảnh minh chứng.</Text>}
      </Space>}
    </Drawer>
  </Space>;
};
