import { useMemo, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Input, Space, Tag, Typography, message } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { getApiErrorMessage } from '../services/api';
import { submitTrafficImport, type TrafficImportRequest } from '../services/trafficApi';

const { Title, Text } = Typography;

const template: TrafficImportRequest = {
  requestKey: 'replace-with-unique-batch-id', sourceSystem: 'LOCAL_UPLOAD',
  station: { code: 'EXAMPLE-TRAFFIC-01', name: 'Điểm đo minh họa',
    routeName: 'Tuyến minh họa', branchId: '<ma-don-vi>', segmentLengthKm: 2.5 },
  counts: [{ eventKey: 'replace-with-unique-count-id', direction: 'Bắc', lane: 1,
    windowStart: '2026-10-07T08:00:00+07:00', windowEnd: '2026-10-07T08:15:00+07:00',
    vehicleClass: 'Ô tô con', vehicleCount: 16 }],
  snapshots: [{ eventKey: 'replace-with-unique-snapshot-id', direction: 'Bắc',
    measuredAt: '2026-10-07T08:10:00+07:00', vehicleClass: 'Ô tô con', presentVehicles: 10 }],
};

const downloadTemplate = () => {
  const blob = new Blob([JSON.stringify(template, null, 2)], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'mau-giao-thong-de-xuat.json';
  anchor.click();
  URL.revokeObjectURL(url);
};

export const TrafficImportPage = () => {
  const client = useQueryClient();
  const navigate = useNavigate();
  const [payload, setPayload] = useState('');
  const filePicker = useRef<HTMLInputElement>(null);
  const [fileName, setFileName] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [imported, setImported] = useState<{ counts: number; snapshots: number } | null>(null);
  const preview = useMemo(() => {
    try {
      const candidate = JSON.parse(payload) as Partial<TrafficImportRequest>;
      if (!candidate || typeof candidate !== 'object' || Array.isArray(candidate)) return null;
      return { requestKey: typeof candidate.requestKey === 'string' ? candidate.requestKey : '',
        stationCode: candidate.station && typeof candidate.station.code === 'string'
          ? candidate.station.code : '',
        counts: Array.isArray(candidate.counts) ? candidate.counts.length : 0,
        snapshots: Array.isArray(candidate.snapshots) ? candidate.snapshots.length : 0 };
    } catch { return null; }
  }, [payload]);
  const importMutation = useMutation({ mutationFn: submitTrafficImport,
    onSuccess: (result) => {
      setValidationError(null);
      setImported({ counts: result.importedCounts, snapshots: result.importedSnapshots });
      message.success('Đã ghi lô quan sát vào bản demo');
      void client.invalidateQueries({ queryKey: ['trafficStations'] });
      void client.invalidateQueries({ queryKey: ['trafficSummary'] });
      void client.invalidateQueries({ queryKey: ['operationsSummary'] });
    },
    onError: (error) => setValidationError(getApiErrorMessage(error)),
  });

  const chooseFile = async (file?: File) => {
    if (!file) return;
    setFileName(null);
    setPayload('');
    setImported(null);
    if (file.size > 1_000_000) {
      setValidationError('Tệp JSON vượt giới hạn 1 MB');
      return;
    }
    try {
      const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer());
      setPayload(text);
      setFileName(file.name);
      setValidationError(null);
    } catch {
      setValidationError('Không đọc được tệp JSON UTF-8');
    }
  };

  const submit = () => {
    try {
      const parsed: unknown = JSON.parse(payload);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error();
      const request = parsed as Partial<TrafficImportRequest>;
      if (typeof request.requestKey !== 'string' || typeof request.sourceSystem !== 'string'
          || typeof request.station?.code !== 'string' || !Array.isArray(request.counts)
          || !Array.isArray(request.snapshots) || request.station.code.startsWith('EXAMPLE-')) {
        throw new Error();
      }
      setValidationError(null);
      setImported(null);
      importMutation.mutate(request as TrafficImportRequest);
    } catch {
      setValidationError('Hãy chọn JSON hợp lệ và thay các mã minh họa bằng mã nguồn thật.');
    }
  };

  return <Space direction="vertical" size="large" style={{ width: '100%' }}>
    <Title level={2}>Nhập quan sát giao thông</Title>
    <Text type="secondary">Dữ liệu nhập chỉ lưu trong bản demo, chờ đối soát trước khi sử dụng.</Text>
    <Card title="Tệp số liệu JSON" extra={<Button onClick={downloadTemplate}>Tải mẫu đề xuất</Button>}>
      <Space wrap>
        <Button icon={<UploadOutlined />} onClick={() => filePicker.current?.click()}>Chọn tệp JSON</Button>
        <Text type="secondary">{fileName ?? 'Hoặc dán nội dung trực tiếp bên dưới'}</Text>
      </Space>
      <input ref={filePicker} aria-label="Chọn tệp JSON" type="file" accept=".json,application/json"
        style={{ display: 'none' }}
        onChange={(event) => { void chooseFile(event.target.files?.[0]); event.target.value = ''; }} />
      <Input.TextArea aria-label="Nội dung giao thông JSON" rows={9} maxLength={1_000_000}
        value={payload} onChange={(event) => {
          setPayload(event.target.value); setFileName(null); setImported(null);
        }} placeholder="Dán JSON UTF-8 hoặc chọn tệp" style={{ marginTop: 16, fontFamily: 'monospace' }} />
      {preview && <Space wrap style={{ marginTop: 14 }}>
        {preview.requestKey && <Tag color="blue">Lô: {preview.requestKey}</Tag>}
        {preview.stationCode && <Tag color="geekblue">Trạm: {preview.stationCode}</Tag>}
        <Tag>{preview.counts} phép đếm</Tag>
        <Tag>{preview.snapshots} lát cắt</Tag>
      </Space>}
      {validationError && <Alert type="error" showIcon message={validationError} style={{ marginTop: 14 }} />}
      {imported && <Alert type="success" showIcon style={{ marginTop: 14 }}
        message={`Đã nhập ${imported.counts} phép đếm và ${imported.snapshots} lát cắt`}
        action={<Button onClick={() => navigate('/traffic')}>Xem biểu đồ</Button>} />}
      <Button type="primary" style={{ marginTop: 16 }} loading={importMutation.isPending}
        disabled={!payload.trim()} onClick={submit}>Nhập vào bản demo</Button>
    </Card>
  </Space>;
};
