import React, { useState } from 'react';
import {
  Row,
  Col,
  Card,
  Statistic,
  Typography,
  Button,
  Space,
  Tag,
  Tabs,
  Table,
  Progress,
  Badge,
  Tooltip,
  Alert,
} from 'antd';
import {
  CompassOutlined,
  BuildOutlined,
  AlertOutlined,
  FolderOpenOutlined,
  ArrowRightOutlined,
  DatabaseOutlined,
  SecurityScanOutlined,
  SyncOutlined,
  FilterOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined,
  AppstoreOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ColumnsType } from 'antd/es/table';
import { useAuth } from '../context/AuthContext';
import {
  fetchDashboardSummary,
  fetchDashboardBranches,
  fetchDashboardDatasets,
  fetchDashboardRoadSigns,
  fetchDashboardRoadLengths,
  fetchDashboardRecentAssets,
  DashboardBranchStat,
  DashboardDatasetStat,
  RouteLength,
  LengthDistribution,
  DashboardRecentAsset,
  BranchSignCount,
  SignCategory,
  getApiErrorPresentation,
  shouldRetryQuery,
} from '../services/api';
import { QueryState, TableSkeleton } from '../components/common';

const { Title, Paragraph, Text } = Typography;

const formatDateTime = (isoStr?: string): string => {
  if (!isoStr) return 'Vừa cập nhật';
  try {
    const d = new Date(isoStr);
    if (isNaN(d.getTime())) return isoStr;
    return d.toLocaleString('vi-VN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return isoStr;
  }
};

const formatNumber = (val?: number | null): string => {
  if (val === undefined || val === null) return 'Chưa có dữ liệu';
  return val.toLocaleString('vi-VN');
};

// Reusable Metric Card guaranteeing compliance with:
// 1. Source dataset
// 2. Filter applied
// 3. Last updated timestamp
// 4. Link to detail list
interface DashboardKpiCardProps {
  title: string;
  value?: number;
  suffix?: string;
  subText?: React.ReactNode;
  icon: React.ReactNode;
  color: string;
  bgColor: string;
  sourceDataset: string;
  filterApplied: string;
  lastUpdated?: string;
  detailUrl: string;
  detailText?: string;
  loading?: boolean;
}

const DashboardKpiCard: React.FC<DashboardKpiCardProps> = ({
  title,
  value,
  suffix,
  subText,
  icon,
  color,
  bgColor,
  sourceDataset,
  filterApplied,
  lastUpdated,
  detailUrl,
  detailText = 'Xem chi tiết',
  loading,
}) => {
  const navigate = useNavigate();

  return (
    <Card
      variant="borderless"
      className="kcht-card"
      style={{
        background: bgColor,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        borderRadius: 8,
        border: '1px solid #e8e8e8',
      }}
      styles={{ body: { padding: '16px 20px', flex: 1, display: 'flex', flexDirection: 'column' } }}
      loading={loading}
    >
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <Text strong style={{ fontSize: 13, color: '#595959', textTransform: 'uppercase', letterSpacing: 0.5 }}>
            {title}
          </Text>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 8,
              background: '#ffffff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 18,
              color: color,
              boxShadow: '0 2px 6px rgba(0,0,0,0.06)',
            }}
          >
            {icon}
          </div>
        </div>

        <div style={{ margin: '10px 0 6px 0' }}>
          <span style={{ fontSize: 28, fontWeight: 700, color: color, fontFamily: 'monospace' }}>
            {formatNumber(value)}
          </span>
          {suffix && (
            <span style={{ marginLeft: 6, fontSize: 13, color: '#8c8c8c', fontWeight: 500 }}>
              {suffix}
            </span>
          )}
        </div>

        {subText && (
          <div style={{ fontSize: 12, color: '#595959', marginBottom: 12 }}>
            {subText}
          </div>
        )}
      </div>

      <div style={{ marginTop: 'auto', paddingTop: 10, borderTop: '1px dashed #e8e8e8' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 8 }}>
          <Tooltip title={`Tập dữ liệu gốc: ${sourceDataset}`}>
            <Tag
              color="blue"
              icon={<DatabaseOutlined />}
              style={{ fontSize: 10, margin: 0, padding: '0 4px', maxWidth: 150, overflow: 'hidden', textOverflow: 'ellipsis' }}
            >
              Nguồn: {sourceDataset.split(',')[0]}
            </Tag>
          </Tooltip>
          <Tooltip title={`Bộ lọc áp dụng: ${filterApplied}`}>
            <Tag
              color="default"
              icon={<FilterOutlined />}
              style={{ fontSize: 10, margin: 0, padding: '0 4px', maxWidth: 150, overflow: 'hidden', textOverflow: 'ellipsis' }}
            >
              Lọc: {filterApplied}
            </Tag>
          </Tooltip>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: 11, color: '#8c8c8c', display: 'flex', alignItems: 'center', gap: 4 }}>
            <ClockCircleOutlined style={{ fontSize: 10 }} />
            {formatDateTime(lastUpdated)}
          </span>
          <Button
            type="link"
            size="small"
            style={{ padding: 0, fontSize: 12, fontWeight: 600, color: color }}
            onClick={() => navigate(detailUrl)}
          >
            {detailText} <ArrowRightOutlined />
          </Button>
        </div>
      </div>
    </Card>
  );
};

// Reusable Section Header with Metadata
interface SectionCardWrapperProps {
  title: React.ReactNode;
  sourceDataset: string;
  filterApplied: string;
  lastUpdated?: string;
  detailUrl: string;
  detailText?: string;
  extra?: React.ReactNode;
  children: React.ReactNode;
}

const SectionCardWrapper: React.FC<SectionCardWrapperProps> = ({
  title,
  sourceDataset,
  filterApplied,
  lastUpdated,
  detailUrl,
  detailText = 'Xem toàn bộ danh sách',
  extra,
  children,
}) => {
  const navigate = useNavigate();

  return (
    <Card
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <span>{title}</span>
          <Tag color="cyan" icon={<DatabaseOutlined />} style={{ fontSize: 11 }}>
            Nguồn: {sourceDataset}
          </Tag>
          <Tag color="geekblue" icon={<FilterOutlined />} style={{ fontSize: 11 }}>
            Lọc: {filterApplied}
          </Tag>
        </div>
      }
      extra={
        <Space>
          <span style={{ fontSize: 11, color: '#8c8c8c' }}>
            <ClockCircleOutlined style={{ marginRight: 4 }} />
            Cập nhật: {formatDateTime(lastUpdated)}
          </span>
          <Button
            type="primary"
            size="small"
            ghost
            onClick={() => navigate(detailUrl)}
          >
            {detailText} <ArrowRightOutlined />
          </Button>
          {extra}
        </Space>
      }
      variant="borderless"
      className="kcht-card"
      style={{ marginBottom: 20, borderRadius: 8 }}
    >
      {children}
    </Card>
  );
};

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<string>('branches');

  // Server-side Aggregates via TanStack Query
  const {
    data: summary,
    isLoading: loadingSummary,
    isError: hasSummaryError,
    error: summaryError,
    refetch: refetchSummary,
    isFetching: fetchingSummary,
  } = useQuery({
    queryKey: ['dashboardSummary'],
    queryFn: fetchDashboardSummary,
    staleTime: 60000,
    retry: shouldRetryQuery,
  });

  const {
    data: branches,
    isLoading: loadingBranches,
    isError: hasBranchesError,
    error: branchesError,
    refetch: refetchBranches,
    isFetching: fetchingBranches,
  } = useQuery({
    queryKey: ['dashboardBranches'],
    queryFn: fetchDashboardBranches,
    staleTime: 60000,
    retry: shouldRetryQuery,
  });

  const {
    data: datasets,
    isLoading: loadingDatasets,
    isError: hasDatasetsError,
    error: datasetsError,
    refetch: refetchDatasets,
    isFetching: fetchingDatasets,
  } = useQuery({
    queryKey: ['dashboardDatasets'],
    queryFn: fetchDashboardDatasets,
    staleTime: 60000,
    retry: shouldRetryQuery,
  });

  const {
    data: roadSigns,
    isLoading: loadingRoadSigns,
    isError: hasRoadSignsError,
    error: roadSignsError,
    refetch: refetchRoadSigns,
    isFetching: fetchingRoadSigns,
  } = useQuery({
    queryKey: ['dashboardRoadSigns'],
    queryFn: fetchDashboardRoadSigns,
    staleTime: 60000,
    retry: shouldRetryQuery,
  });

  const {
    data: roadLengths,
    isLoading: loadingRoadLengths,
    isError: hasRoadLengthsError,
    error: roadLengthsError,
    refetch: refetchRoadLengths,
    isFetching: fetchingRoadLengths,
  } = useQuery({
    queryKey: ['dashboardRoadLengths'],
    queryFn: fetchDashboardRoadLengths,
    staleTime: 60000,
    retry: shouldRetryQuery,
  });

  const {
    data: recentAssets,
    isLoading: loadingRecentAssets,
    isError: hasRecentAssetsError,
    error: recentAssetsError,
    refetch: refetchRecentAssets,
    isFetching: fetchingRecentAssets,
  } = useQuery({
    queryKey: ['dashboardRecentAssets'],
    queryFn: () => fetchDashboardRecentAssets(10),
    staleTime: 30000,
    retry: shouldRetryQuery,
  });

  const isRefreshingAny =
    fetchingSummary ||
    fetchingBranches ||
    fetchingDatasets ||
    fetchingRoadSigns ||
    fetchingRoadLengths ||
    fetchingRecentAssets;
  const summaryErrorPresentation = hasSummaryError ? getApiErrorPresentation(summaryError) : null;

  const handleRefreshAll = () => {
    void refetchSummary();
    void refetchBranches();
    void refetchDatasets();
    void refetchRoadSigns();
    void refetchRoadLengths();
    void refetchRecentAssets();
  };

  // Branch Table Columns
  const branchColumns: ColumnsType<DashboardBranchStat> = [
    {
      title: 'STT',
      key: 'index',
      width: 60,
      align: 'center',
      render: (_, __, idx) => idx + 1,
    },
    {
      title: 'Đơn vị / Chi nhánh Quản lý',
      dataIndex: 'branchName',
      key: 'branchName',
      render: (name: string, record) => (
        <div>
          <Text strong>{name}</Text>
          <div style={{ fontSize: 11, color: '#8c8c8c' }}>Mã: {record.branchId}</div>
        </div>
      ),
    },
    {
      title: 'Cầu đường bộ',
      dataIndex: 'bridgeCount',
      key: 'bridgeCount',
      align: 'right',
      sorter: (a, b) => a.bridgeCount - b.bridgeCount,
      render: (count: number) => (
        <span style={{ fontWeight: 600, color: '#003a8c' }}>{formatNumber(count)}</span>
      ),
    },
    {
      title: 'Biển báo',
      dataIndex: 'roadSignCount',
      key: 'roadSignCount',
      align: 'right',
      sorter: (a, b) => a.roadSignCount - b.roadSignCount,
      render: (count: number) => (
        <span style={{ fontWeight: 600, color: '#52c41a' }}>{formatNumber(count)}</span>
      ),
    },
    {
      title: 'Tổng Tài sản KCHT',
      dataIndex: 'totalAssets',
      key: 'totalAssets',
      align: 'right',
      sorter: (a, b) => a.totalAssets - b.totalAssets,
      render: (total: number) => (
        <span style={{ fontWeight: 700, color: '#1677ff' }}>{formatNumber(total)}</span>
      ),
    },
    {
      title: 'Tỷ trọng Toàn quốc',
      key: 'ratio',
      width: 180,
      render: (_, record) => {
        const total = summary?.totalAssets || 830836;
        const percent = total > 0 ? ((record.totalAssets / total) * 100).toFixed(1) : '0';
        return (
          <Tooltip title={`${record.totalAssets.toLocaleString()} / ${total.toLocaleString()} tài sản`}>
            <Progress
              percent={parseFloat(percent)}
              size="small"
              strokeColor="#003a8c"
              format={(p) => `${p}%`}
            />
          </Tooltip>
        );
      },
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 140,
      align: 'center',
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          onClick={() => navigate(`/assets?branch=${record.branchId}`)}
        >
          Tra cứu <ArrowRightOutlined />
        </Button>
      ),
    },
  ];

  // Longest Routes Columns
  const routeColumns: ColumnsType<RouteLength> = [
    {
      title: 'Hạng',
      key: 'rank',
      width: 60,
      align: 'center',
      render: (_, __, idx) => (
        <Badge
          count={idx + 1}
          style={{
            backgroundColor: idx === 0 ? '#f5222d' : idx === 1 ? '#fa8c16' : idx === 2 ? '#52c41a' : '#bfbfbf',
          }}
        />
      ),
    },
    {
      title: 'Mã Tuyến',
      dataIndex: 'routeCode',
      key: 'routeCode',
      width: 130,
      render: (code: string) => <Tag color="blue">{code}</Tag>,
    },
    {
      title: 'Tên Tuyến Quốc lộ',
      dataIndex: 'routeName',
      key: 'routeName',
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: 'Chiều dài thực tế',
      dataIndex: 'lengthKm',
      key: 'lengthKm',
      align: 'right',
      render: (km: number) => (
        <span style={{ fontWeight: 700, color: '#fa8c16' }}>
          {km.toLocaleString('vi-VN', { minimumFractionDigits: 1, maximumFractionDigits: 2 })} km
        </span>
      ),
    },
    {
      title: 'Tỷ lệ Mạng lưới',
      key: 'percentage',
      width: 160,
      render: (_, record) => {
        const totalKm = roadLengths?.totalLengthKm;
        if (totalKm === undefined || totalKm <= 0) {
          return <Text type="secondary">Chưa có dữ liệu tổng</Text>;
        }
        const percent = ((record.lengthKm / totalKm) * 100).toFixed(1);
        return <Progress percent={parseFloat(percent)} size="small" strokeColor="#fa8c16" />;
      },
    },
  ];

  // Top Datasets Columns
  const datasetColumns: ColumnsType<DashboardDatasetStat> = [
    {
      title: 'STT',
      key: 'index',
      width: 50,
      align: 'center',
      render: (_, __, idx) => idx + 1,
    },
    {
      title: 'Mã Tập Dữ liệu',
      dataIndex: 'datasetKey',
      key: 'datasetKey',
      render: (key: string) => <Tag color="geekblue">{key}</Tag>,
    },
    {
      title: 'Tên Tập Dữ liệu',
      dataIndex: 'datasetName',
      key: 'datasetName',
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: 'Phân loại',
      dataIndex: 'kind',
      key: 'kind',
      width: 140,
      render: (kind: string) => (
        <Tag color={kind === 'physical_asset' ? 'green' : 'default'}>
          {kind === 'physical_asset' ? 'Tài sản Vật thể' : 'Danh mục Chuẩn'}
        </Tag>
      ),
    },
    {
      title: 'Số Bản ghi Server',
      dataIndex: 'totalRecords',
      key: 'totalRecords',
      align: 'right',
      render: (records: number) => (
        <span style={{ fontWeight: 700, color: '#003a8c' }}>{formatNumber(records)}</span>
      ),
    },
    {
      title: 'Hành động',
      key: 'action',
      width: 120,
      align: 'center',
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          onClick={() => navigate(`/assets?datasetKey=${record.datasetKey}`)}
        >
          Tra cứu <ArrowRightOutlined />
        </Button>
      ),
    },
  ];

  // Recent Assets Columns
  const recentColumns: ColumnsType<DashboardRecentAsset> = [
    {
      title: 'Tên Tài sản KCHT',
      dataIndex: 'assetName',
      key: 'assetName',
      render: (name: string, record) => (
        <div>
          <Text strong>{name || 'Tài sản KCHT'}</Text>
          <div style={{ fontSize: 11, color: '#8c8c8c' }}>Mã: {record.recordKey}</div>
        </div>
      ),
    },
    {
      title: 'Tập Dữ liệu',
      dataIndex: 'datasetName',
      key: 'datasetName',
      render: (name: string, record) => (
        <div>
          <Text>{name}</Text>
          <div style={{ fontSize: 11, color: '#8c8c8c' }}>{record.datasetKey}</div>
        </div>
      ),
    },
    {
      title: 'Đơn vị Quản lý',
      dataIndex: 'branchName',
      key: 'branchName',
      render: (name: string) => <Tag color="blue">{name}</Tag>,
    },
    {
      title: 'Thời điểm Nạp',
      dataIndex: 'importedAt',
      key: 'importedAt',
      render: (iso: string) => (
        <span style={{ fontSize: 12, color: '#595959' }}>{formatDateTime(iso)}</span>
      ),
    },
    {
      title: 'Chi tiết',
      key: 'action',
      width: 100,
      align: 'center',
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          onClick={() => navigate(record.detailUrl || `/assets?datasetKey=${record.datasetKey}`)}
        >
          Xem &rarr;
        </Button>
      ),
    },
  ];

  return (
    <div>
      {/* Top Banner & Header */}
      <div
        style={{
          marginBottom: 20,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div>
          <Title level={3} style={{ margin: 0, color: '#003a8c' }}>
            Bảng Điều hành & Giám sát KCHT Đường bộ
          </Title>
          <Paragraph type="secondary" style={{ margin: '4px 0 0 0', fontSize: 13 }}>
            Xin chào, <strong>{user?.fullName || user?.username}</strong>. Tổng hợp dữ liệu kết cấu hạ
            tầng giao thông đường bộ quốc gia theo thời gian thực từ 658 tập dữ liệu và 1,1 triệu bản
            ghi.
          </Paragraph>
        </div>
        <Space>
          <Button
            icon={<SyncOutlined spin={isRefreshingAny} />}
            onClick={handleRefreshAll}
            loading={isRefreshingAny}
          >
            Làm mới Dữ liệu
          </Button>
          <Tag color="blue" icon={<SecurityScanOutlined />}>
            Phiên làm việc an toàn
          </Tag>
          <Tag color="cyan" icon={<EnvironmentOutlined />}>
            PostGIS EPSG:4326
          </Tag>
        </Space>
      </div>

      {/* Aggregate Notice */}
      <Alert
        message="Hệ thống Tổng hợp Server-side Aggregates"
        description="Mọi chỉ số, thống kê phân bổ và chiều dài được tính toán tối ưu tại cơ sở dữ liệu PostgreSQL + PostGIS (Materialized Views & Indexes), không tính toán hàng trăm nghìn dòng trên trình duyệt."
        type="info"
        showIcon
        closable
        style={{ marginBottom: 20, borderRadius: 6 }}
      />

      {summaryErrorPresentation && (
        <Alert
          type="error"
          showIcon
          message={summaryErrorPresentation.title}
          description={summaryErrorPresentation.description}
          action={summaryErrorPresentation.retryable ? (
            <Button size="small" onClick={() => void refetchSummary()}>
              Thử lại
            </Button>
          ) : undefined}
          style={{ marginBottom: 20, borderRadius: 6 }}
        />
      )}

      {/* 6 Executive KPI Cards with required metadata (source, filter, timestamp, link) */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <DashboardKpiCard
            title="Tổng Tài sản KCHT"
            value={summary?.totalAssets}
            suffix="tài sản"
            subText="57 danh mục vật thể kỹ thuật"
            icon={<DatabaseOutlined />}
            color="#003a8c"
            bgColor="#f0f5ff"
            sourceDataset={summary?.sourceDataset || 'raw_dataset_record, dataset_registry'}
            filterApplied={summary?.filter || 'Toàn quốc - Đang khai thác'}
            lastUpdated={summary?.lastUpdated}
            detailUrl="/assets"
            detailText="Tra cứu"
            loading={loadingSummary}
          />
        </Col>

        <Col xs={24} sm={12} lg={8} xl={4}>
          <DashboardKpiCard
            title="Cầu Đường bộ"
            value={summary?.totalBridges}
            suffix="cầu"
            subText="Toàn mạng lưới quốc lộ & tỉnh lộ"
            icon={<BuildOutlined />}
            color="#1677ff"
            bgColor="#e6f4ff"
            sourceDataset="tbl_bridge"
            filterApplied="4 Khu QLĐB & các Sở GTVT"
            lastUpdated={summary?.lastUpdated}
            detailUrl="/assets?datasetKey=tbl_bridge"
            detailText="Tra cứu cầu"
            loading={loadingSummary}
          />
        </Col>

        <Col xs={24} sm={12} lg={8} xl={4}>
          <DashboardKpiCard
            title="Biển báo Đường bộ"
            value={summary?.totalRoadSigns}
            suffix="biển"
            subText="QCVN 41:2019/BGTVT"
            icon={<AlertOutlined />}
            color="#52c41a"
            bgColor="#f6ffed"
            sourceDataset="tbl_road_sign"
            filterApplied="QCVN 41:2019/BGTVT"
            lastUpdated={summary?.lastUpdated}
            detailUrl="/assets?datasetKey=tbl_road_sign"
            detailText="Tra cứu biển báo"
            loading={loadingSummary}
          />
        </Col>

        <Col xs={24} sm={12} lg={8} xl={4}>
          <DashboardKpiCard
            title="Tuyến Quốc lộ Chính"
            value={summary?.totalNationalRoadRoutes}
            suffix="tuyến"
            subText={summary?.totalNationalRoadLengthKm == null
              ? 'Tổng chiều dài: Chưa có dữ liệu'
              : `Tổng: ${formatNumber(Math.round(summary.totalNationalRoadLengthKm))} km`}
            icon={<CompassOutlined />}
            color="#fa8c16"
            bgColor="#fff7e6"
            sourceDataset="mst_national_road"
            filterApplied="Tuyến Quốc lộ chính thức"
            lastUpdated={summary?.lastUpdated}
            detailUrl="/reports"
            detailText="Thống kê tuyến"
            loading={loadingSummary}
          />
        </Col>

        <Col xs={24} sm={12} lg={8} xl={4}>
          <DashboardKpiCard
            title="Kho Dữ liệu & Danh mục"
            value={summary?.totalDatasets}
            suffix="tập"
            subText="57 vật thể | 601 danh mục"
            icon={<AppstoreOutlined />}
            color="#13c2c2"
            bgColor="#e6fffb"
            sourceDataset="dataset_registry"
            filterApplied="Toàn bộ danh mục hệ thống"
            lastUpdated={summary?.lastUpdated}
            detailUrl="/catalogs"
            detailText="Xem danh mục"
            loading={loadingSummary}
          />
        </Col>

        <Col xs={24} sm={12} lg={8} xl={4}>
          <DashboardKpiCard
            title="Hồ sơ Kỹ thuật (S3)"
            value={summary?.totalDocuments}
            suffix="hồ sơ"
            subText="Bản vẽ hoàn công MinIO"
            icon={<FolderOpenOutlined />}
            color="#722ed1"
            bgColor="#f9f0ff"
            sourceDataset="document_metadata (MinIO)"
            filterApplied="Hồ sơ hoàn công & cầu lớn"
            lastUpdated={summary?.lastUpdated}
            detailUrl="/documents"
            detailText="Mở kho tài liệu"
            loading={loadingSummary}
          />
        </Col>
      </Row>

      {/* Main Tabs for Detailed Analytics */}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        type="card"
        style={{ marginBottom: 24 }}
        items={[
          {
            key: 'branches',
            label: (
              <span>
                <EnvironmentOutlined /> Phân bổ theo Đơn vị / Chi nhánh (Khu QLĐB & Sở GTVT)
              </span>
            ),
            children: (
              <SectionCardWrapper
                title="Bảng Phân bổ Tài sản theo Đơn vị & Chi nhánh Quản lý"
                sourceDataset="mv_dashboard_branch_stats, tbl_bridge, tbl_road_sign, raw_dataset_record"
                filterApplied="Group by branch_id (4 Khu QLĐB & các Sở GTVT địa phương)"
                lastUpdated={branches?.[0]?.lastUpdated || summary?.lastUpdated}
                detailUrl="/assets"
                detailText="Tra cứu toàn bộ tài sản"
              >
                <QueryState
                  isLoading={loadingBranches}
                  isError={hasBranchesError}
                  error={branchesError}
                  isEmpty={(branches?.length ?? 0) === 0}
                  loading={<TableSkeleton rows={8} columns={6} />}
                  emptyTitle="Chưa có dữ liệu phân bổ theo đơn vị"
                  emptyDescription="Hệ thống chưa trả về thống kê tài sản theo đơn vị quản lý."
                  onRetry={() => void refetchBranches()}
                >
                  <Table<DashboardBranchStat>
                    dataSource={branches ?? []}
                    columns={branchColumns}
                    rowKey="branchId"
                    pagination={{ pageSize: 10, showSizeChanger: true }}
                    size="middle"
                  />
                </QueryState>
              </SectionCardWrapper>
            ),
          },
          {
            key: 'roadSigns',
            label: (
              <span>
                <AlertOutlined /> Chuyên đề Biển báo Giao thông (QCVN 41:2019)
              </span>
            ),
            children: (
              <div>
                <SectionCardWrapper
                  title="Thống kê Toàn diện Biển báo Giao thông Đường bộ"
                  sourceDataset={roadSigns?.sourceDataset || 'tbl_road_sign, raw_dataset_record'}
                  filterApplied={roadSigns?.filter || 'QCVN 41:2019/BGTVT - Phân loại theo Khu QLĐB & Nhóm biển báo'}
                  lastUpdated={roadSigns?.lastUpdated}
                  detailUrl="/assets?datasetKey=tbl_road_sign"
                  detailText="Tra cứu danh sách biển báo"
                >
                  <QueryState
                    isLoading={loadingRoadSigns}
                    isError={hasRoadSignsError}
                    error={roadSignsError}
                    isEmpty={
                      (roadSigns?.byBranch.length ?? 0) === 0
                      && (roadSigns?.byShape.length ?? 0) === 0
                    }
                    loading={<TableSkeleton rows={6} columns={4} />}
                    emptyTitle="Chưa có thống kê biển báo"
                    emptyDescription="Hệ thống chưa trả về phân bổ biển báo theo khu vực hoặc nhóm quy chuẩn."
                    onRetry={() => void refetchRoadSigns()}
                  >
                  <Row gutter={[20, 20]}>
                    {/* Branch Sign Distribution */}
                    <Col xs={24} md={12}>
                      <Card
                        title={
                          <Space>
                            <EnvironmentOutlined style={{ color: '#003a8c' }} />
                            <span>Phân bổ theo 4 Khu Quản lý Đường bộ & Địa phương</span>
                          </Space>
                        }
                        size="small"
                        variant="outlined"
                      >
                        <Space direction="vertical" style={{ width: '100%' }} size={14}>
                          {roadSigns?.byBranch?.map((b: BranchSignCount) => (
                            <div key={b.branchId}>
                              <div
                                style={{
                                  display: 'flex',
                                  justifyContent: 'space-between',
                                  marginBottom: 4,
                                }}
                              >
                                <Text strong>{b.branchName}</Text>
                                <span style={{ fontWeight: 600, color: '#003a8c' }}>
                                  {formatNumber(b.count)} biển ({b.percentage}%)
                                </span>
                              </div>
                              <Progress
                                percent={b.percentage}
                                strokeColor={
                                  b.branchId.startsWith('kqldb_1')
                                    ? '#1677ff'
                                    : b.branchId.startsWith('kqldb_2')
                                    ? '#52c41a'
                                    : b.branchId.startsWith('kqldb_3')
                                    ? '#fa8c16'
                                    : b.branchId.startsWith('kqldb_4')
                                    ? '#722ed1'
                                    : '#8c8c8c'
                                }
                                size="small"
                              />
                            </div>
                          ))}
                        </Space>
                      </Card>
                    </Col>

                    {/* Shape / Standard Category */}
                    <Col xs={24} md={12}>
                      <Card
                        title={
                          <Space>
                            <AlertOutlined style={{ color: '#52c41a' }} />
                            <span>Phân loại Nhóm Biển theo Quy chuẩn Kỹ thuật QCVN 41</span>
                          </Space>
                        }
                        size="small"
                        variant="outlined"
                      >
                        <Row gutter={[12, 12]}>
                          {roadSigns?.byShape?.map((s: SignCategory) => (
                            <Col span={12} key={s.category}>
                              <Card
                                size="small"
                                style={{
                                  background: '#fafafa',
                                  borderLeft: '4px solid #52c41a',
                                  borderRadius: 4,
                                }}
                              >
                                <Statistic
                                  title={s.category}
                                  value={s.count}
                                  valueStyle={{ fontSize: 20, color: '#237804', fontWeight: 700 }}
                                  suffix="biển"
                                />
                              </Card>
                            </Col>
                          ))}
                        </Row>

                        <div style={{ marginTop: 16 }}>
                          <Alert
                            message="Quy chuẩn Áp dụng: QCVN 41:2019/BGTVT"
                            description="Dữ liệu biển báo phân định rõ các nhóm: Biển nguy hiểm, Biển cấm, Biển hiệu lệnh, Biển chỉ dẫn và Biển phụ theo tọa độ GIS PostGIS EPSG:4326."
                            type="success"
                            showIcon
                          />
                        </div>
                      </Card>
                    </Col>
                    </Row>
                  </QueryState>
                  </SectionCardWrapper>
                </div>
              ),
          },
          {
            key: 'roadLengths',
            label: (
              <span>
                <CompassOutlined /> Thống kê Chiều dài Tuyến Quốc lộ (mst_national_road)
              </span>
            ),
            children: (
              <div>
                <SectionCardWrapper
                  title="Thống kê Chiều dài Mạng lưới Tuyến Quốc lộ Toàn quốc"
                  sourceDataset={roadLengths?.sourceDataset || 'mst_national_road, view_dashboard_national_road_stats'}
                  filterApplied={roadLengths?.filter || 'Toàn bộ 168 tuyến Quốc lộ chính'}
                  lastUpdated={roadLengths?.lastUpdated}
                  detailUrl="/reports"
                  detailText="Xem báo cáo chi tiết mạng lưới"
                >
                  <QueryState
                    isLoading={loadingRoadLengths}
                    isError={hasRoadLengthsError}
                    error={roadLengthsError}
                    isEmpty={
                      (roadLengths?.longestRoutes.length ?? 0) === 0
                      && (roadLengths?.distribution.length ?? 0) === 0
                    }
                    loading={<TableSkeleton rows={6} columns={4} />}
                    emptyTitle="Chưa có thống kê chiều dài tuyến"
                    emptyDescription="Hệ thống chưa trả về dữ liệu chiều dài hoặc phân bố cự ly tuyến quốc lộ."
                    onRetry={() => void refetchRoadLengths()}
                  >
                  <Row gutter={[20, 20]}>
                    {/* Longest Routes Table */}
                    <Col xs={24} lg={14}>
                      <Card
                        title="Top Tuyến Quốc lộ có Chiều dài lớn nhất Việt Nam"
                        size="small"
                        variant="outlined"
                      >
                        <Table<RouteLength>
                          dataSource={roadLengths?.longestRoutes || []}
                          columns={routeColumns}
                          rowKey="routeCode"
                          pagination={false}
                          size="small"
                        />
                      </Card>
                    </Col>

                    {/* Distribution by Length Range */}
                    <Col xs={24} lg={10}>
                      <Card
                        title="Phân bố Tuyến theo Cự ly Chiều dài"
                        size="small"
                        variant="outlined"
                        style={{ height: '100%' }}
                      >
                        <Space direction="vertical" style={{ width: '100%' }} size={16}>
                          {roadLengths?.distribution?.map((d: LengthDistribution) => {
                            const totalRoutes = roadLengths?.totalRoutes;
                            const percent =
                              totalRoutes !== undefined && totalRoutes > 0
                                ? ((d.routeCount / totalRoutes) * 100).toFixed(1)
                                : undefined;
                            return (
                              <div key={d.rangeLabel}>
                                <div
                                  style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    marginBottom: 4,
                                  }}
                                >
                                  <Text strong>Cự ly {d.rangeLabel}</Text>
                                  <span style={{ fontSize: 13, color: '#fa8c16', fontWeight: 600 }}>
                                    {d.routeCount} tuyến ({formatNumber(Math.round(d.totalKm))} km)
                                  </span>
                                </div>
                                <Progress
                                  percent={percent === undefined ? 0 : parseFloat(percent)}
                                  size="small"
                                  strokeColor="#fa8c16"
                                  format={() => percent === undefined
                                    ? 'Chưa có dữ liệu tổng'
                                    : `${percent}% (${d.routeCount} tuyến)`}
                                />
                              </div>
                            );
                          })}
                        </Space>
                      </Card>
                    </Col>
                  </Row>
                  </QueryState>
                </SectionCardWrapper>
              </div>
            ),
          },
          {
            key: 'datasetsAndRecent',
            label: (
              <span>
                <DatabaseOutlined /> Top Tập Dữ liệu & Tài sản Mới Cập nhật
              </span>
            ),
            children: (
              <Row gutter={[20, 20]}>
                {/* Top 10 Datasets */}
                <Col xs={24} lg={12}>
                  <SectionCardWrapper
                    title="Top 10 Tập Dữ liệu Quy mô lớn nhất"
                    sourceDataset="dataset_registry"
                    filterApplied="Top 10 Physical Asset Datasets"
                    lastUpdated={datasets?.[0]?.lastUpdated}
                    detailUrl="/assets"
                    detailText="Tra cứu 658 tập dữ liệu"
                  >
                    <QueryState
                      isLoading={loadingDatasets}
                      isError={hasDatasetsError}
                      error={datasetsError}
                      isEmpty={(datasets?.length ?? 0) === 0}
                      loading={<TableSkeleton rows={8} columns={5} />}
                      emptyTitle="Chưa có thống kê tập dữ liệu"
                      emptyDescription="Hệ thống chưa trả về danh sách tập dữ liệu có quy mô lớn nhất."
                      onRetry={() => void refetchDatasets()}
                    >
                      <Table<DashboardDatasetStat>
                        dataSource={datasets ?? []}
                        columns={datasetColumns}
                        rowKey="datasetKey"
                        pagination={false}
                        size="small"
                      />
                    </QueryState>
                  </SectionCardWrapper>
                </Col>

                {/* Recently Updated Assets Feed */}
                <Col xs={24} lg={12}>
                  <SectionCardWrapper
                    title="Nhật ký Nạp & Cập nhật Tài sản Gần nhất"
                    sourceDataset="raw_dataset_record, dataset_registry"
                    filterApplied="10 bản ghi nạp gần nhất có timestamp"
                    lastUpdated={recentAssets?.[0]?.importedAt}
                    detailUrl="/assets"
                    detailText="Xem bảng tra cứu"
                  >
                    <QueryState
                      isLoading={loadingRecentAssets}
                      isError={hasRecentAssetsError}
                      error={recentAssetsError}
                      isEmpty={(recentAssets?.length ?? 0) === 0}
                      loading={<TableSkeleton rows={8} columns={5} />}
                      emptyTitle="Chưa có tài sản cập nhật gần đây"
                      emptyDescription="Hệ thống chưa có bản ghi nạp hoặc cập nhật gần đây để hiển thị."
                      onRetry={() => void refetchRecentAssets()}
                    >
                      <Table<DashboardRecentAsset>
                        dataSource={recentAssets ?? []}
                        columns={recentColumns}
                        rowKey="recordKey"
                        pagination={false}
                        size="small"
                      />
                    </QueryState>
                  </SectionCardWrapper>
                </Col>
              </Row>
            ),
          },
        ]}
      />

      {/* Quick shortcuts footer */}
      <Card
        title="Lối tắt Nghiệp vụ & Khai thác Nhanh"
        variant="borderless"
        className="kcht-card"
        style={{ marginBottom: 24, borderRadius: 8 }}
        size="small"
      >
        <Row gutter={[12, 12]}>
          <Col xs={24} sm={12} md={6}>
            <Button
              block
              icon={<DatabaseOutlined />}
              onClick={() => navigate('/assets')}
              style={{ height: 44, textAlign: 'left', fontWeight: 500 }}
            >
              Tra cứu 658 Tập Dữ liệu
            </Button>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Button
              block
              icon={<CompassOutlined />}
              onClick={() => navigate('/map')}
              style={{ height: 44, textAlign: 'left', fontWeight: 500 }}
            >
              Bản đồ số WebGIS PostGIS
            </Button>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Button
              block
              icon={<FolderOpenOutlined />}
              onClick={() => navigate('/documents')}
              style={{ height: 44, textAlign: 'left', fontWeight: 500 }}
            >
              Kho tài liệu hoàn công S3 MinIO
            </Button>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Button
              block
              icon={<BuildOutlined />}
              onClick={() => navigate('/reports')}
              style={{ height: 44, textAlign: 'left', fontWeight: 500 }}
            >
              Báo cáo & Thống kê chiều dài
            </Button>
          </Col>
        </Row>
      </Card>
    </div>
  );
};
