import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Col, DatePicker, Empty, Row, Select, Skeleton, Statistic, Tag, Typography } from 'antd';
import { fetchTrafficStations, fetchTrafficSummary } from '../services/trafficApi';
import { getApiErrorMessage } from '../services/api';

const { Title, Text } = Typography;

export const TrafficPage = () => {
  const [stationCode, setStationCode] = useState<string | null>(null);
  const [period, setPeriod] = useState<[string, string] | null>(null);
  const stations = useQuery({ queryKey: ['trafficStations'], queryFn: fetchTrafficStations });
  const selected = stationCode ?? stations.data?.[0]?.stationCode;
  const selectedStation = stations.data?.find((station) => station.stationCode === selected);
  const summary = useQuery({ queryKey: ['trafficSummary', selected, period],
    queryFn: () => fetchTrafficSummary(selected!, period?.[0], period?.[1]), enabled: !!selected });
  const maxClass = Math.max(1, ...(summary.data?.classes.map((item) => item.count) ?? []));
  const maxInterval = Math.max(1, ...(summary.data?.intervals.map((item) => item.count) ?? []));

  return <section aria-label="Lưu lượng phương tiện">
    <Title level={2}>Lưu lượng phương tiện</Title>
    {selectedStation && <Tag color={selectedStation.isDemo ? 'orange' : 'blue'}>
      {selectedStation.isDemo ? 'Số liệu mô phỏng' : 'Số liệu nhập — chờ đối soát'}
    </Tag>}
    {stations.isLoading && <Skeleton active />}
    {stations.error && <Alert type="error" message={getApiErrorMessage(stations.error)} />}
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginBottom: 18 }}>
      <Select aria-label="Trạm đếm xe" placeholder="Chọn trạm đếm" value={selected}
        onChange={setStationCode} style={{ width: 320 }}
        options={stations.data?.map((station) => ({ value: station.stationCode, label: station.stationName }))} />
      <DatePicker.RangePicker aria-label="Khoảng thời gian" showTime format="DD/MM/YYYY HH:mm"
        onChange={(dates) => setPeriod(dates?.[0] && dates[1]
          ? [dates[0].toISOString(), dates[1].toISOString()] : null)} />
    </div>
    {!stations.isLoading && stations.data?.length === 0 && <Empty description="Chưa có trạm trong phạm vi đơn vị" />}
    {summary.isLoading && <Skeleton active />}
    {summary.error && <Alert type="error" message={getApiErrorMessage(summary.error)} />}
    {summary.data && <>
      <Row gutter={[14, 14]} style={{ marginBottom: 14 }}>
        <Col xs={24} md={8}><Card><Statistic title="Tổng lượt xe" value={summary.data.totalVehicles} /></Card></Col>
        <Col xs={24} md={16}><Card><Text strong>{summary.data.station.stationName}</Text><br />
          <Text type="secondary">{summary.data.station.routeName}</Text>
          {summary.data.rangeStart && summary.data.rangeEnd && <Text type="secondary" style={{ display: 'block' }}>
            Từ {new Date(summary.data.rangeStart).toLocaleString('vi-VN')} đến {new Date(summary.data.rangeEnd).toLocaleString('vi-VN')}
          </Text>}</Card></Col>
      </Row>
      {summary.data.totalVehicles === 0 && !summary.data.density &&
        <Empty description="Không có quan sát trong khoảng thời gian này" />}
      {summary.data.density && <Card title="Mật độ xe hiện diện tại thời điểm đo" style={{ marginBottom: 14 }}>
        <Row gutter={[14, 14]} align="middle">
          <Col xs={24} md={8}><Statistic title="Xe/km" value={summary.data.density.vehiclesPerKm}
            precision={2} /></Col>
          <Col xs={24} md={8}><Statistic title="Xe hiện diện" value={summary.data.density.presentVehicles} /></Col>
          <Col xs={24} md={8}><Text type="secondary">{new Date(summary.data.density.measuredAt).toLocaleString('vi-VN')}
            {' · '}{summary.data.station.segmentLengthKm} km</Text></Col>
        </Row>
        <div style={{ marginTop: 10 }}>{summary.data.density.classes.map((item) =>
          <Tag key={item.vehicleClass}>{item.vehicleClass}: {item.count.toLocaleString('vi-VN')}</Tag>)}</div>
      </Card>}
      {summary.data.totalVehicles > 0 && <Row gutter={[14, 14]}>
        <Col xs={24} lg={12}><Card title="Phân loại phương tiện">
          {summary.data.classes.map((item) => <div key={item.vehicleClass} style={{ marginBottom: 15 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}><Text>{item.vehicleClass}</Text>
              <Text strong>{item.count.toLocaleString('vi-VN')}</Text></div>
            <div role="img" aria-label={`Tỷ lệ ${item.vehicleClass}`} style={{ height: 10, borderRadius: 5, background: '#edf2fa' }}>
              <div style={{ width: `${item.count / maxClass * 100}%`, height: '100%', background: '#3177b8', borderRadius: 5 }} /></div>
          </div>)}
        </Card></Col>
        <Col xs={24} lg={12}><Card title="Lượt xe theo giờ">
          {summary.data.intervals.map((item) => <div key={item.start} style={{ marginBottom: 15 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <Text>{new Date(item.start).toLocaleString('vi-VN')} – {new Date(item.end).toLocaleTimeString('vi-VN')}</Text>
              <Text strong>{item.count.toLocaleString('vi-VN')}</Text></div>
            <div role="img" aria-label={`Khoảng ${item.start}`} style={{ height: 10, borderRadius: 5, background: '#edf2fa' }}>
              <div style={{ width: `${item.count / maxInterval * 100}%`, height: '100%', background: '#e29148', borderRadius: 5 }} /></div>
          </div>)}
        </Card></Col>
      </Row>}
    </>}
  </section>;
};
