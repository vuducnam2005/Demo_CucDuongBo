import React, { useState } from 'react';
import {
  Card,
  Typography,
  Space,
  Row,
  Col,
  Select,
  Button,
  Table,
  Tag,
  Tabs,
  Statistic,
  Input,
  message,
  Progress,
} from 'antd';
import {
  BarChartOutlined,
  DownloadOutlined,
  ReloadOutlined,
  SearchOutlined,
  WarningOutlined,
  SafetyCertificateOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ThunderboltOutlined,
  AuditOutlined,
  DashboardOutlined,
  RiseOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  fetchRoadLengthReport,
  exportRoadLengthReportCsv,
  fetchMaintenanceReport,
  exportMaintenanceReportCsv,
  fetchRoadSignBlackspotReport,
  exportRoadSignBlackspotReportCsv,
  fetchIriRoughnessReport,
  exportIriRoughnessReportCsv,
  RoadLengthReportResponse,
  MaintenanceReportResponse,
  RoadSignBlackspotReportResponse,
  IriRoughnessReportResponse,
} from '../services/reportApi';
import { getApiErrorMessage, shouldRetryQuery } from '../services/api';
import { QueryState, TableSkeleton } from '../components/common';

const { Title, Text, Paragraph } = Typography;

const formatReportNumber = (
  value: number | null | undefined,
  options: Intl.NumberFormatOptions = {},
): string => value == null ? '—' : value.toLocaleString('vi-VN', options);

export const ReportIndexPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState('road-length');

  // Filters for Road Length
  const [rlBranch, setRlBranch] = useState<string>('');
  const [rlRoute, setRlRoute] = useState<string>('');
  const [rlSurface, setRlSurface] = useState<string>('');

  // Filters for Maintenance
  const [mtBranch, setMtBranch] = useState<string>('');
  const [mtYear, setMtYear] = useState<number | undefined>(undefined);
  const [mtType, setMtType] = useState<string>('');
  const [mtStatus, setMtStatus] = useState<string>('');

  // Filters for Signs & Blackspots
  const [sbBranch, setSbBranch] = useState<string>('');
  const [sbRoute, setSbRoute] = useState<string>('');
  const [sbCategory, setSbCategory] = useState<string>('');

  // Filters for Road Roughness IRI (HDM-4)
  const [iriRoute, setIriRoute] = useState<string>('');
  const [iriCondition, setIriCondition] = useState<string>('');

  const [exporting, setExporting] = useState(false);

  // Queries
  const roadLengthQuery = useQuery<RoadLengthReportResponse>({
    queryKey: ['reportRoadLength', rlBranch, rlRoute, rlSurface],
    queryFn: () => fetchRoadLengthReport({ branch: rlBranch, route: rlRoute, surfaceType: rlSurface }),
    enabled: activeTab === 'road-length',
    retry: shouldRetryQuery,
  });

  const maintenanceQuery = useQuery<MaintenanceReportResponse>({
    queryKey: ['reportMaintenance', mtBranch, mtYear, mtType, mtStatus],
    queryFn: () => fetchMaintenanceReport({ branch: mtBranch, year: mtYear, type: mtType, status: mtStatus }),
    enabled: activeTab === 'maintenance',
    retry: shouldRetryQuery,
  });

  const signsQuery = useQuery<RoadSignBlackspotReportResponse>({
    queryKey: ['reportRoadSigns', sbBranch, sbRoute, sbCategory],
    queryFn: () => fetchRoadSignBlackspotReport({ branch: sbBranch, route: sbRoute, category: sbCategory }),
    enabled: activeTab === 'signs-blackspots',
    retry: shouldRetryQuery,
  });

  const iriQuery = useQuery<IriRoughnessReportResponse>({
    queryKey: ['reportIriRoughness', iriRoute, iriCondition],
    queryFn: () => fetchIriRoughnessReport({ route: iriRoute, conditionGroup: iriCondition }),
    enabled: activeTab === 'iri-roughness',
    retry: shouldRetryQuery,
  });

  // Export handlers
  const handleExportRoadLength = async () => {
    if (exporting) return;
    try {
      setExporting(true);
      const blob = await exportRoadLengthReportCsv({ branch: rlBranch, route: rlRoute, surfaceType: rlSurface });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Bao_cao_chieu_dai_duong_${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(a);
      a.click();
      a.parentNode?.removeChild(a);
      window.URL.revokeObjectURL(url);
      message.success('Đã tải xuống báo cáo chiều dài đường thành công (CSV UTF-8 BOM)');
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const handleExportMaintenance = async () => {
    if (exporting) return;
    try {
      setExporting(true);
      const blob = await exportMaintenanceReportCsv({ branch: mtBranch, year: mtYear, type: mtType, status: mtStatus });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Bao_cao_bao_tri_kcht_${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(a);
      a.click();
      a.parentNode?.removeChild(a);
      window.URL.revokeObjectURL(url);
      message.success('Đã tải xuống báo cáo kế hoạch bảo trì thành công (CSV UTF-8 BOM)');
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const handleExportSigns = async () => {
    if (exporting) return;
    try {
      setExporting(true);
      const blob = await exportRoadSignBlackspotReportCsv({ branch: sbBranch, route: sbRoute, category: sbCategory });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Bao_cao_bien_bao_diem_den_${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(a);
      a.click();
      a.parentNode?.removeChild(a);
      window.URL.revokeObjectURL(url);
      message.success('Đã tải xuống thống kê biển báo và điểm đen thành công (CSV UTF-8 BOM)');
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const handleExportIri = async () => {
    if (exporting) return;
    try {
      setExporting(true);
      const blob = await exportIriRoughnessReportCsv({ route: iriRoute, conditionGroup: iriCondition });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Bao_cao_khao_sat_IRI_HDM4_${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(a);
      a.click();
      a.parentNode?.removeChild(a);
      window.URL.revokeObjectURL(url);
      message.success('Đã tải xuống báo cáo khảo sát độ gồ ghề mặt đường IRI thành công (CSV UTF-8 BOM)');
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const branchOptions = [
    { label: 'Tất cả khu vực', value: '' },
    { label: 'Khu Quản lý đường bộ I (Bắc Bộ)', value: 'cuc_ql_duong_bo_1' },
    { label: 'Khu Quản lý đường bộ II (Bắc Trung Bộ)', value: 'cuc_ql_duong_bo_2' },
    { label: 'Khu Quản lý đường bộ III (Duyên hải Miền Trung & Tây Nguyên)', value: 'cuc_ql_duong_bo_3' },
    { label: 'Khu Quản lý đường bộ IV (Nam Bộ)', value: 'cuc_ql_duong_bo_4' },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Space align="center" size={10}>
          <BarChartOutlined style={{ fontSize: 24, color: '#003a8c' }} />
          <div>
            <Title level={4} style={{ margin: 0 }}>
              Báo cáo & Thống kê Kết cấu Hạ tầng Giao thông Đường bộ
            </Title>
            <Paragraph type="secondary" style={{ margin: 0, fontSize: 13 }}>
              Trung tâm tổng hợp dữ liệu chiều dài mạng lưới quốc lộ, kế hoạch kinh phí bảo trì và thống kê an toàn giao thông.
            </Paragraph>
          </div>
        </Space>
      </div>

      <Card variant="borderless" className="kcht-card" style={{ marginBottom: 16 }}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'road-length',
              label: (
                <span>
                  <AuditOutlined /> Chiều dài mạng lưới đường bộ
                </span>
              ),
            },
            {
              key: 'maintenance',
              label: (
                <span>
                  <ThunderboltOutlined /> Kế hoạch & Thực hiện bảo trì
                </span>
              ),
            },
            {
              key: 'signs-blackspots',
              label: (
                <span>
                  <WarningOutlined /> Biển báo QCVN 41 & Điểm đen TNGT
                </span>
              ),
            },
            {
              key: 'iri-roughness',
              label: (
                <span>
                  <DashboardOutlined /> Khảo sát Độ gồ ghề mặt đường & IRI (HDM-4)
                </span>
              ),
            },
          ]}
        />

        {/* TAB 1: ROAD LENGTH REPORT */}
        {activeTab === 'road-length' && (
          <div>
            <Row gutter={[12, 12]} align="middle" style={{ marginBottom: 16 }}>
              <Col xs={24} sm={7}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Khu vực quản lý:</div>
                <Select
                  style={{ width: '100%' }}
                  value={rlBranch}
                  onChange={setRlBranch}
                  options={branchOptions}
                />
              </Col>
              <Col xs={24} sm={6}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Tuyến đường:</div>
                <Input
                  placeholder="Mã tuyến (VD: QL.1, QL.5)..."
                  value={rlRoute}
                  onChange={(e) => setRlRoute(e.target.value)}
                  prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                  allowClear
                />
              </Col>
              <Col xs={24} sm={5}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Loại mặt đường:</div>
                <Select
                  style={{ width: '100%' }}
                  value={rlSurface}
                  onChange={setRlSurface}
                  options={[
                    { label: 'Tất cả loại mặt', value: '' },
                    { label: 'Bê tông nhựa cấp cao A1', value: 'Bê tông nhựa cấp cao A1' },
                    { label: 'Bê tông nhựa thảm', value: 'Bê tông nhựa thảm' },
                    { label: 'Bê tông nhựa', value: 'Bê tông nhựa' },
                  ]}
                />
              </Col>
              <Col xs={24} sm={6} style={{ display: 'flex', gap: 8, alignItems: 'flex-end', paddingTop: 18 }}>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => roadLengthQuery.refetch()}
                  loading={roadLengthQuery.isFetching}
                >
                  Làm mới
                </Button>
                <Button
                  type="primary"
                  icon={<DownloadOutlined />}
                  onClick={handleExportRoadLength}
                  loading={exporting}
                >
                  Xuất CSV (BOM)
                </Button>
              </Col>
            </Row>

            <QueryState
              isLoading={roadLengthQuery.isLoading}
              isError={roadLengthQuery.isError}
              error={roadLengthQuery.error}
              isEmpty={(roadLengthQuery.data?.routes.length ?? 0) === 0}
              loading={<TableSkeleton rows={8} columns={6} />}
              emptyTitle={rlBranch || rlRoute || rlSurface ? 'Không có tuyến phù hợp bộ lọc' : 'Chưa có dữ liệu chiều dài tuyến'}
              emptyDescription={rlBranch || rlRoute || rlSurface
                ? 'Hãy thay đổi hoặc xóa bớt điều kiện lọc để xem dữ liệu.'
                : 'Hệ thống chưa có dữ liệu mạng lưới tuyến quốc lộ để lập báo cáo.'}
              emptyActionText={rlBranch || rlRoute || rlSurface ? 'Xóa bộ lọc' : undefined}
              onEmptyAction={() => {
                setRlBranch('');
                setRlRoute('');
                setRlSurface('');
              }}
              onRetry={() => void roadLengthQuery.refetch()}
            >
            <>
            {/* Summary Statistics */}
            {roadLengthQuery.data?.summary && (
              <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Tổng số tuyến quốc lộ"
                      value={formatReportNumber(roadLengthQuery.data.summary.totalRoutes)}
                      suffix="tuyến"
                      valueStyle={{ color: '#003a8c', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Tổng chiều dài quản lý"
                      value={formatReportNumber(roadLengthQuery.data.summary.totalLengthKm, { maximumFractionDigits: 3 })}
                      suffix="km"
                      valueStyle={{ color: '#389e0d', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Chiều dài trung bình"
                      value={formatReportNumber(roadLengthQuery.data.summary.averageLengthKm, { maximumFractionDigits: 3 })}
                      suffix="km/tuyến"
                      valueStyle={{ color: '#d46b08', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Tuyến dài nhất"
                      value={`${roadLengthQuery.data.summary.longestRouteCode} (${formatReportNumber(roadLengthQuery.data.summary.longestRouteLengthKm, { maximumFractionDigits: 3 })} km)`}
                      valueStyle={{ color: '#0958d9', fontSize: 16, fontWeight: 600 }}
                    />
                  </Card>
                </Col>
              </Row>
            )}

            {/* Breakdown tables */}
            {roadLengthQuery.data && (
              <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                <Col xs={24} md={12}>
                  <Card title="Phân bổ theo Khu vực quản lý đường bộ" size="small">
                    <Table
                      dataSource={roadLengthQuery.data.branchDistribution}
                      rowKey="branchId"
                      pagination={false}
                      size="small"
                      columns={[
                        { title: 'Khu vực quản lý', dataIndex: 'branchName', key: 'branchName' },
                        { title: 'Số tuyến', dataIndex: 'routeCount', key: 'routeCount', align: 'right' },
                        {
                          title: 'Chiều dài (km)',
                          dataIndex: 'totalLengthKm',
                          key: 'totalLengthKm',
                          align: 'right',
                          render: (v: number) => formatReportNumber(v, { maximumFractionDigits: 3 }),
                        },
                        {
                          title: 'Tỷ trọng',
                          dataIndex: 'percent',
                          key: 'percent',
                          render: (p: number) => (
                            <Progress
                              percent={p}
                              size="small"
                              status="active"
                              format={(value) => `${formatReportNumber(value, { maximumFractionDigits: 1 })}%`}
                            />
                          ),
                        },
                      ]}
                    />
                  </Card>
                </Col>
                <Col xs={24} md={12}>
                  <Card title="Phân bổ theo Loại kết cấu mặt đường" size="small">
                    <Table
                      dataSource={roadLengthQuery.data.surfaceDistribution}
                      rowKey="surfaceType"
                      pagination={false}
                      size="small"
                      columns={[
                        { title: 'Loại mặt đường', dataIndex: 'surfaceType', key: 'surfaceType', render: (t: string) => <Tag color="blue">{t}</Tag> },
                        {
                          title: 'Chiều dài (km)',
                          dataIndex: 'totalLengthKm',
                          key: 'totalLengthKm',
                          align: 'right',
                          render: (v: number) => v.toLocaleString('vi-VN'),
                        },
                        {
                          title: 'Tỷ trọng',
                          dataIndex: 'percent',
                          key: 'percent',
                          render: (p: number) => (
                            <Progress
                              percent={p}
                              size="small"
                              strokeColor="#52c41a"
                              format={(value) => `${formatReportNumber(value, { maximumFractionDigits: 1 })}%`}
                            />
                          ),
                        },
                      ]}
                    />
                  </Card>
                </Col>
              </Row>
            )}

            {/* Detailed Routes Table */}
            <Card title="Danh sách chi tiết mạng lưới tuyến quốc lộ" size="small">
              <Table
                dataSource={roadLengthQuery.data?.routes || []}
                rowKey="routeCode"
                bordered
                size="middle"
                pagination={{ pageSize: 10, showSizeChanger: true }}
                columns={[
                  {
                    title: 'STT',
                    key: 'stt',
                    width: 60,
                    align: 'center',
                    render: (_t, _r, idx) => idx + 1,
                  },
                  {
                    title: 'Mã tuyến',
                    dataIndex: 'routeCode',
                    key: 'routeCode',
                    width: 120,
                    render: (t: string) => <Tag color="geekblue" style={{ fontWeight: 600 }}>{t}</Tag>,
                  },
                  {
                    title: 'Tên tuyến đường',
                    dataIndex: 'routeName',
                    key: 'routeName',
                    render: (t: string) => <strong>{t}</strong>,
                  },
                  {
                    title: 'Chiều dài (km)',
                    dataIndex: 'lengthKm',
                    key: 'lengthKm',
                    align: 'right',
                    width: 130,
                     render: (v: number) => <Text strong style={{ color: '#0958d9' }}>{formatReportNumber(v, { maximumFractionDigits: 3 })}</Text>,
                  },
                  {
                    title: 'Đơn vị quản lý',
                    dataIndex: 'branchName',
                    key: 'branchName',
                  },
                  {
                    title: 'Loại mặt đường',
                    dataIndex: 'surfaceType',
                    key: 'surfaceType',
                    render: (t: string) => <Tag>{t}</Tag>,
                  },
                  {
                    title: 'Cấp kỹ thuật',
                    dataIndex: 'roadClass',
                    key: 'roadClass',
                  },
                ]}
              />
            </Card>
            </>
            </QueryState>
          </div>
        )}

        {/* TAB 2: MAINTENANCE REPORT */}
        {activeTab === 'maintenance' && (
          <div>
            <Row gutter={[12, 12]} align="middle" style={{ marginBottom: 16 }}>
              <Col xs={24} sm={6}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Khu vực:</div>
                <Select
                  style={{ width: '100%' }}
                  value={mtBranch}
                  onChange={setMtBranch}
                  options={branchOptions}
                />
              </Col>
              <Col xs={24} sm={4}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Năm kế hoạch:</div>
                <Select
                  style={{ width: '100%' }}
                  value={mtYear || ''}
                  onChange={(v) => setMtYear(v ? Number(v) : undefined)}
                  options={[
                    { label: 'Tất cả năm', value: '' },
                    { label: 'Năm 2026', value: 2026 },
                    { label: 'Năm 2025', value: 2025 },
                  ]}
                />
              </Col>
              <Col xs={24} sm={5}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Loại hình bảo trì:</div>
                <Select
                  style={{ width: '100%' }}
                  value={mtType}
                  onChange={setMtType}
                  options={[
                    { label: 'Tất cả loại hình', value: '' },
                    { label: 'Sửa chữa định kỳ', value: 'Sửa chữa định kỳ' },
                    { label: 'Bảo trì thường xuyên', value: 'Bảo trì thường xuyên' },
                    { label: 'Sửa chữa đột xuất', value: 'Sửa chữa đột xuất' },
                  ]}
                />
              </Col>
              <Col xs={24} sm={4}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Trạng thái:</div>
                <Select
                  style={{ width: '100%' }}
                  value={mtStatus}
                  onChange={setMtStatus}
                  options={[
                    { label: 'Tất cả trạng thái', value: '' },
                    { label: 'Hoàn thành', value: 'Hoàn thành' },
                    { label: 'Đang thi công', value: 'Đang thi công' },
                    { label: 'Chuẩn bị đầu tư', value: 'Chuẩn bị đầu tư' },
                  ]}
                />
              </Col>
              <Col xs={24} sm={5} style={{ display: 'flex', gap: 8, alignItems: 'flex-end', paddingTop: 18 }}>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => maintenanceQuery.refetch()}
                  loading={maintenanceQuery.isFetching}
                >
                  Làm mới
                </Button>
                <Button
                  type="primary"
                  icon={<DownloadOutlined />}
                  onClick={handleExportMaintenance}
                  loading={exporting}
                >
                  Xuất CSV (BOM)
                </Button>
              </Col>
            </Row>

            <QueryState
              isLoading={maintenanceQuery.isLoading}
              isError={maintenanceQuery.isError}
              error={maintenanceQuery.error}
              isEmpty={(maintenanceQuery.data?.projects.length ?? 0) === 0}
              loading={<TableSkeleton rows={8} columns={7} />}
              emptyTitle={mtBranch || mtYear || mtType || mtStatus ? 'Không có dự án phù hợp bộ lọc' : 'Chưa có dữ liệu bảo trì'}
              emptyDescription={mtBranch || mtYear || mtType || mtStatus
                ? 'Hãy thay đổi hoặc xóa bớt điều kiện lọc để xem dữ liệu.'
                : 'Hệ thống chưa có dự án sửa chữa, bảo trì để lập báo cáo.'}
              emptyActionText={mtBranch || mtYear || mtType || mtStatus ? 'Xóa bộ lọc' : undefined}
              onEmptyAction={() => {
                setMtBranch('');
                setMtYear(undefined);
                setMtType('');
                setMtStatus('');
              }}
              onRetry={() => void maintenanceQuery.refetch()}
            >
            <>
            {/* Summary Statistics */}
            {maintenanceQuery.data?.summary && (
              <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Tổng số công trình / dự án"
                      value={maintenanceQuery.data.summary.totalProjects}
                      suffix="dự án"
                      valueStyle={{ color: '#003a8c', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Tổng kinh phí bảo trì"
                       value={formatReportNumber(maintenanceQuery.data.summary.totalBudgetVnd / 1e9, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}
                      suffix="Tỷ VNĐ"
                      valueStyle={{ color: '#cf1322', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Đã hoàn thành / Thi công"
                      value={`${maintenanceQuery.data.summary.completedCount} / ${maintenanceQuery.data.summary.inProgressCount}`}
                      valueStyle={{ color: '#389e0d', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Tỷ lệ hoàn thành tiến độ"
                       value={formatReportNumber(maintenanceQuery.data.summary.completionRatePercent, { maximumFractionDigits: 1 })}
                      suffix="%"
                      valueStyle={{ color: '#0958d9', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
              </Row>
            )}

            {/* Projects Table */}
            <Card title="Danh mục các công trình sửa chữa, bảo trì đường bộ" size="small">
              <Table
                dataSource={maintenanceQuery.data?.projects || []}
                rowKey="projectCode"
                bordered
                size="middle"
                pagination={{ pageSize: 10, showSizeChanger: true }}
                columns={[
                  {
                    title: 'Mã dự án',
                    dataIndex: 'projectCode',
                    key: 'projectCode',
                    width: 120,
                    render: (t: string) => <Tag color="purple">{t}</Tag>,
                  },
                  {
                    title: 'Tên công trình / Hạng mục',
                    dataIndex: 'projectName',
                    key: 'projectName',
                    render: (t: string) => <strong>{t}</strong>,
                  },
                  {
                    title: 'Tuyến',
                    dataIndex: 'routeCode',
                    key: 'routeCode',
                    width: 90,
                    render: (t: string) => <Tag color="blue">{t}</Tag>,
                  },
                  {
                    title: 'Khu vực',
                    dataIndex: 'branchName',
                    key: 'branchName',
                  },
                  {
                    title: 'Loại hình',
                    dataIndex: 'maintenanceType',
                    key: 'maintenanceType',
                  },
                  {
                    title: 'Kinh phí (VNĐ)',
                    dataIndex: 'budgetVnd',
                    key: 'budgetVnd',
                    align: 'right',
                     render: (v: number) => <Text strong>{formatReportNumber(v)}</Text>,
                  },
                  {
                    title: 'Năm KH',
                    dataIndex: 'planYear',
                    key: 'planYear',
                    align: 'center',
                    width: 90,
                  },
                  {
                    title: 'Trạng thái',
                    dataIndex: 'status',
                    key: 'status',
                    width: 130,
                    render: (st: string) => {
                      let color = 'default';
                      if (st === 'Hoàn thành') color = 'success';
                      if (st === 'Đang thi công') color = 'processing';
                      if (st === 'Chuẩn bị đầu tư') color = 'warning';
                      return <Tag color={color}>{st}</Tag>;
                    },
                  },
                  {
                    title: 'Nhà thầu',
                    dataIndex: 'contractor',
                    key: 'contractor',
                  },
                ]}
              />
            </Card>
            </>
            </QueryState>
          </div>
        )}

        {/* TAB 3: ROAD SIGNS & BLACKSPOTS */}
        {activeTab === 'signs-blackspots' && (
          <div>
            <Row gutter={[12, 12]} align="middle" style={{ marginBottom: 16 }}>
              <Col xs={24} sm={7}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Khu vực quản lý:</div>
                <Select
                  style={{ width: '100%' }}
                  value={sbBranch}
                  onChange={setSbBranch}
                  options={branchOptions}
                />
              </Col>
              <Col xs={24} sm={6}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Tuyến đường:</div>
                <Input
                  placeholder="Mã tuyến (VD: QL.1, QL.6)..."
                  value={sbRoute}
                  onChange={(e) => setSbRoute(e.target.value)}
                  allowClear
                />
              </Col>
              <Col xs={24} sm={5}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Mức độ điểm đen:</div>
                <Select
                  style={{ width: '100%' }}
                  value={sbCategory}
                  onChange={setSbCategory}
                  options={[
                    { label: 'Tất cả mức độ', value: '' },
                    { label: 'Rất nguy hiểm (Điểm đen)', value: 'Điểm đen' },
                    { label: 'Nguy hiểm (Điểm tiềm ẩn)', value: 'Điểm tiềm ẩn' },
                  ]}
                />
              </Col>
              <Col xs={24} sm={6} style={{ display: 'flex', gap: 8, alignItems: 'flex-end', paddingTop: 18 }}>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => signsQuery.refetch()}
                  loading={signsQuery.isFetching}
                >
                  Làm mới
                </Button>
                <Button
                  type="primary"
                  icon={<DownloadOutlined />}
                  onClick={handleExportSigns}
                  loading={exporting}
                >
                  Xuất CSV (BOM)
                </Button>
              </Col>
            </Row>

            <QueryState
              isLoading={signsQuery.isLoading}
              isError={signsQuery.isError}
              error={signsQuery.error}
              isEmpty={
                (signsQuery.data?.signCategories.length ?? 0) === 0
                && (signsQuery.data?.blackspots.length ?? 0) === 0
              }
              loading={<TableSkeleton rows={8} columns={6} />}
              emptyTitle={sbBranch || sbRoute || sbCategory ? 'Không có dữ liệu phù hợp bộ lọc' : 'Chưa có dữ liệu biển báo và điểm đen'}
              emptyDescription={sbBranch || sbRoute || sbCategory
                ? 'Hãy thay đổi hoặc xóa bớt điều kiện lọc để xem dữ liệu.'
                : 'Hệ thống chưa có dữ liệu biển báo hoặc điểm đen để lập báo cáo.'}
              emptyActionText={sbBranch || sbRoute || sbCategory ? 'Xóa bộ lọc' : undefined}
              onEmptyAction={() => {
                setSbBranch('');
                setSbRoute('');
                setSbCategory('');
              }}
              onRetry={() => void signsQuery.refetch()}
            >
            <>
            {/* Overview Summary */}
            {signsQuery.data?.summary && (
              <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Tổng số biển báo hiệu QCVN 41"
                      value={formatReportNumber(signsQuery.data.summary.totalRoadSigns)}
                      suffix="biển"
                      valueStyle={{ color: '#003a8c', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Tổng số điểm đen / tiềm ẩn"
                      value={signsQuery.data.summary.totalBlackspots}
                      suffix="vị trí"
                      valueStyle={{ color: '#cf1322', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Điểm đen rất nguy hiểm"
                      value={signsQuery.data.summary.highRiskBlackspots}
                      suffix="vị trí"
                      valueStyle={{ color: '#d4380d', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
                <Col xs={12} sm={6}>
                  <Card size="small" variant="outlined" className="kcht-metric-card">
                    <Statistic
                      title="Đã xử lý / Đang theo dõi"
                      value={`${signsQuery.data.summary.rectifiedBlackspots} / ${signsQuery.data.summary.monitoredBlackspots}`}
                      valueStyle={{ color: '#389e0d', fontWeight: 600 }}
                    />
                  </Card>
                </Col>
              </Row>
            )}

            {/* QCVN 41 Sign Categories */}
            <Card title="Phân loại biển báo hiệu đường bộ theo QCVN 41:2019/BGTVT" size="small" style={{ marginBottom: 16 }}>
              <Table
                dataSource={signsQuery.data?.signCategories || []}
                rowKey="categoryCode"
                pagination={false}
                size="middle"
                columns={[
                  {
                    title: 'Mã nhóm',
                    dataIndex: 'categoryCode',
                    key: 'categoryCode',
                    width: 100,
                    render: (t: string) => <Tag color="volcano" style={{ fontWeight: 600 }}>{t}</Tag>,
                  },
                  {
                    title: 'Tên nhóm biển báo',
                    dataIndex: 'categoryName',
                    key: 'categoryName',
                    render: (t: string) => <strong>{t}</strong>,
                  },
                  {
                    title: 'Số lượng biển',
                    dataIndex: 'count',
                    key: 'count',
                    align: 'right',
                    width: 140,
                    render: (v: number) => <Text strong>{formatReportNumber(v)}</Text>,
                  },
                  {
                    title: 'Tỷ trọng',
                    dataIndex: 'percent',
                    key: 'percent',
                    width: 180,
                    render: (p: number) => (
                      <Progress
                        percent={p}
                        size="small"
                        format={(value) => `${formatReportNumber(value, { maximumFractionDigits: 1 })}%`}
                      />
                    ),
                  },
                  {
                    title: 'Biển tiêu biểu',
                    dataIndex: 'sampleSignCode',
                    key: 'sampleSignCode',
                    render: (t: string) => <Tag color="blue">{t}</Tag>,
                  },
                  {
                    title: 'Ý nghĩa quy chuẩn',
                    dataIndex: 'description',
                    key: 'description',
                  },
                ]}
              />
            </Card>

            {/* Blackspots List */}
            <Card title="Danh sách các điểm đen và vị trí tiềm ẩn tai nạn giao thông" size="small">
              <Table
                dataSource={signsQuery.data?.blackspots || []}
                rowKey="id"
                loading={signsQuery.isLoading}
                bordered
                size="middle"
                pagination={{ pageSize: 10, showSizeChanger: true }}
                columns={[
                  {
                    title: 'Mã vị trí',
                    dataIndex: 'spotCode',
                    key: 'spotCode',
                    width: 130,
                    render: (t: string) => <Tag color="red" style={{ fontWeight: 600 }}>{t}</Tag>,
                  },
                  {
                    title: 'Tuyến',
                    dataIndex: 'routeCode',
                    key: 'routeCode',
                    width: 90,
                    render: (t: string) => <Tag color="blue">{t}</Tag>,
                  },
                  {
                    title: 'Lý trình / Địa danh',
                    dataIndex: 'kmMarker',
                    key: 'kmMarker',
                    render: (t: string) => <strong>{t}</strong>,
                  },
                  {
                    title: 'Mức độ rủi ro',
                    dataIndex: 'severity',
                    key: 'severity',
                    render: (t: string) => (
                      <Tag color={t.includes('Rất nguy hiểm') ? 'error' : 'warning'}>{t}</Tag>
                    ),
                  },
                  {
                    title: 'Nguyên nhân kỹ thuật',
                    dataIndex: 'description',
                    key: 'description',
                  },
                  {
                    title: 'TNGT 12 tháng qua',
                    key: 'tngt',
                    align: 'center',
                    width: 150,
                    render: (_t, r) => (
                      <span>
                        <Text type="danger" strong>{r.incidentCount} vụ</Text> / {r.fatalityCount} chết
                      </span>
                    ),
                  },
                  {
                    title: 'Trạng thái xử lý',
                    dataIndex: 'rectificationStatus',
                    key: 'rectificationStatus',
                    width: 130,
                    render: (st: string) => {
                      if (st === 'Đã xử lý') return <Tag icon={<CheckCircleOutlined />} color="success">{st}</Tag>;
                      if (st === 'Đang xử lý') return <Tag icon={<ClockCircleOutlined />} color="processing">{st}</Tag>;
                      return <Tag icon={<SafetyCertificateOutlined />} color="default">{st}</Tag>;
                    },
                  },
                ]}
              />
            </Card>
            </>
            </QueryState>
          </div>
        )}

        {/* TAB 4: ROAD ROUGHNESS IRI & HDM-4 REPORT */}
        {activeTab === 'iri-roughness' && (
          <div>
            <Row gutter={[12, 12]} align="middle" style={{ marginBottom: 16 }}>
              <Col xs={24} sm={10}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Tuyến / Đoạn đường khảo sát:</div>
                <Input
                  placeholder="Mã hoặc tên tuyến (VD: QL1, QL53, Km1282)..."
                  value={iriRoute}
                  onChange={(e) => setIriRoute(e.target.value)}
                  prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                  allowClear
                />
              </Col>
              <Col xs={24} sm={8}>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Phân loại chất lượng HDM-4:</div>
                <Select
                  style={{ width: '100%' }}
                  value={iriCondition}
                  onChange={setIriCondition}
                  options={[
                    { label: 'Tất cả mức độ', value: '' },
                    { label: 'Rất kém (IRI ≥ 6.0 m/km)', value: 'very_poor' },
                    { label: 'Kém (4.0 ≤ IRI < 6.0 m/km)', value: 'poor' },
                    { label: 'Trung bình (2.0 ≤ IRI < 4.0 m/km)', value: 'fair' },
                    { label: 'Tốt (IRI < 2.0 m/km)', value: 'good' },
                  ]}
                />
              </Col>
              <Col xs={24} sm={6} style={{ display: 'flex', gap: 8, alignItems: 'flex-end', paddingTop: 18 }}>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => iriQuery.refetch()}
                  loading={iriQuery.isFetching}
                >
                  Làm mới
                </Button>
                <Button
                  type="primary"
                  icon={<DownloadOutlined />}
                  onClick={handleExportIri}
                  loading={exporting}
                >
                  Xuất CSV
                </Button>
              </Col>
            </Row>

            <QueryState
              isLoading={iriQuery.isLoading}
              isError={iriQuery.isError}
              error={iriQuery.error}
              isEmpty={(iriQuery.data?.segments.length ?? 0) === 0}
              loading={<TableSkeleton rows={8} columns={6} />}
              emptyTitle="Không có đoạn khảo sát phù hợp"
              emptyDescription="Hãy thay đổi bộ lọc tuyến hoặc mức độ chất lượng."
              emptyActionText={iriRoute || iriCondition ? 'Xóa bộ lọc' : undefined}
              onEmptyAction={() => {
                setIriRoute('');
                setIriCondition('');
              }}
              onRetry={() => iriQuery.refetch()}
            >
              {iriQuery.data && (
                <>
                  {/* Summary KPI Cards */}
                  <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                    <Col xs={24} sm={6}>
                      <Card size="small" variant="borderless" style={{ background: '#f0f5ff', borderRadius: 8 }}>
                        <Statistic
                          title="Chiều dài khảo sát"
                          value={iriQuery.data.summary.surveyLengthKm}
                          suffix="km"
                          valueStyle={{ color: '#1677ff', fontWeight: 700 }}
                          prefix={<AuditOutlined />}
                        />
                        <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                          {iriQuery.data.summary.totalValidSegments} đoạn đo kiểm 100m chuẩn
                        </div>
                      </Card>
                    </Col>
                    <Col xs={24} sm={6}>
                      <Card size="small" variant="borderless" style={{ background: '#f6ffed', borderRadius: 8 }}>
                        <Statistic
                          title="IRI trung bình mạng lưới"
                          value={iriQuery.data.summary.averageIri}
                          suffix="m/km"
                          valueStyle={{ color: '#52c41a', fontWeight: 700 }}
                          prefix={<DashboardOutlined />}
                        />
                        <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                          Trung vị (p50): {iriQuery.data.summary.medianIri} m/km
                        </div>
                      </Card>
                    </Col>
                    <Col xs={24} sm={6}>
                      <Card size="small" variant="borderless" style={{ background: '#fff2e8', borderRadius: 8 }}>
                        <Statistic
                          title="Tỷ lệ Kém & Rất kém"
                          value={iriQuery.data.summary.poorOrVeryPoorPercentage}
                          suffix="%"
                          valueStyle={{ color: '#fa541c', fontWeight: 700 }}
                          prefix={<WarningOutlined />}
                        />
                        <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                          Cần ưu tiên bảo trì sửa chữa
                        </div>
                      </Card>
                    </Col>
                    <Col xs={24} sm={6}>
                      <Card size="small" variant="borderless" style={{ background: '#fff0f6', borderRadius: 8 }}>
                        <Statistic
                          title="Hư hỏng tương quan"
                          value={iriQuery.data.summary.totalCorrelatedDefects}
                          suffix="điểm"
                          valueStyle={{ color: '#eb2f96', fontWeight: 700 }}
                          prefix={<ThunderboltOutlined />}
                        />
                        <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                          Vết nứt, ổ gà, lún hằn vệt bánh
                        </div>
                      </Card>
                    </Col>
                  </Row>

                  {/* HDM-4 Standards Distribution */}
                  <Card
                    title={
                      <Space>
                        <RiseOutlined style={{ color: '#1677ff' }} />
                        <span>Phân bổ chất lượng mặt đường theo tiêu chuẩn quốc tế HDM-4 (World Bank / PIARC)</span>
                      </Space>
                    }
                    size="small"
                    style={{ marginBottom: 16 }}
                  >
                    <Row gutter={[16, 16]}>
                      {iriQuery.data.distribution.map((item) => (
                        <Col xs={24} sm={6} key={item.conditionGroup}>
                          <Card size="small" style={{ borderLeft: `4px solid ${item.color}`, background: '#fafafa' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                              <Text strong style={{ color: item.color }}>{item.conditionLabel}</Text>
                              <Tag color={item.color}>{item.count} đoạn</Tag>
                            </div>
                            <Progress
                              percent={item.percentage}
                              strokeColor={item.color}
                              size="small"
                              style={{ marginTop: 8 }}
                            />
                            <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 4 }}>
                              {item.description}
                            </div>
                          </Card>
                        </Col>
                      ))}
                    </Row>
                  </Card>

                  {/* Detail Segments Table (Top worst road sections) */}
                  <Card
                    title={
                      <Space>
                        <span>Danh mục đoạn khảo sát IRI & Xếp hạng nguy cấp bảo trì</span>
                        <Tag color="geekblue">{iriQuery.data.segments.length} đoạn hiển thị</Tag>
                      </Space>
                    }
                    size="small"
                  >
                    <Table
                      dataSource={iriQuery.data.segments}
                      rowKey={(r) => `${r.roadName}_${r.chainage}_${r.rank}`}
                      bordered
                      size="small"
                      pagination={{ pageSize: 15, showTotal: (t) => `Tổng số ${t} đoạn khảo sát` }}
                      columns={[
                        {
                          title: 'Xếp hạng',
                          dataIndex: 'rank',
                          key: 'rank',
                          width: 85,
                          align: 'center',
                          render: (r: number) => (
                            <Tag color={r <= 10 ? 'red' : r <= 30 ? 'orange' : 'default'} style={{ fontWeight: 600 }}>
                              #{r}
                            </Tag>
                          ),
                        },
                        {
                          title: 'Tuyến đường / Đoạn khảo sát',
                          dataIndex: 'roadName',
                          key: 'roadName',
                          render: (t: string) => <strong style={{ color: '#002140' }}>{t}</strong>,
                        },
                        {
                          title: 'Lý trình',
                          dataIndex: 'chainage',
                          key: 'chainage',
                          width: 110,
                          align: 'center',
                          render: (c: string) => <Tag color="blue">{c}</Tag>,
                        },
                        {
                          title: 'Phạm vi đo (m)',
                          key: 'range',
                          width: 140,
                          align: 'center',
                          render: (_t, r) => (
                            <span style={{ fontSize: 12, fontFamily: 'monospace' }}>
                              {r.startMeters != null && r.endMeters != null
                                ? `${r.startMeters.toLocaleString()} - ${r.endMeters.toLocaleString()}`
                                : '—'}
                            </span>
                          ),
                        },
                        {
                          title: 'Chỉ số IRI (m/km)',
                          dataIndex: 'iriValue',
                          key: 'iriValue',
                          width: 140,
                          align: 'right',
                          sorter: (a, b) => a.iriValue - b.iriValue,
                          defaultSortOrder: 'descend',
                          render: (val: number) => {
                            return (
                              <Tag
                                color={val >= 6.0 ? 'error' : val >= 4.0 ? 'warning' : 'success'}
                                style={{ fontWeight: 700, fontSize: 13 }}
                              >
                                {val.toFixed(3)}
                              </Tag>
                            );
                          },
                        },
                        {
                          title: 'Đánh giá HDM-4',
                          dataIndex: 'conditionLabel',
                          key: 'conditionLabel',
                          width: 130,
                          align: 'center',
                          render: (lbl: string, r) => {
                            const color =
                              r.conditionGroup === 'very_poor'
                                ? 'error'
                                : r.conditionGroup === 'poor'
                                ? 'warning'
                                : r.conditionGroup === 'fair'
                                ? 'processing'
                                : 'success';
                            return <Tag color={color}>{lbl}</Tag>;
                          },
                        },
                        {
                          title: 'Tốc độ',
                          dataIndex: 'speedKmh',
                          key: 'speedKmh',
                          width: 100,
                          align: 'center',
                          render: (spd?: number) => (spd != null ? `${spd.toFixed(1)} km/h` : '—'),
                        },
                        {
                          title: 'Hư hỏng tương quan',
                          dataIndex: 'defectCount',
                          key: 'defectCount',
                          width: 150,
                          align: 'center',
                          render: (cnt: number) =>
                            cnt > 0 ? (
                              <Tag color="volcano" icon={<WarningOutlined />}>
                                {cnt} lỗi/km
                              </Tag>
                            ) : (
                              <Text type="secondary">0</Text>
                            ),
                        },
                      ]}
                    />
                  </Card>
                </>
              )}
            </QueryState>
          </div>
        )}
      </Card>
    </div>
  );
};
export default ReportIndexPage;
