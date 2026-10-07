import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Row,
  Col,
  Card,
  Tree,
  Table,
  Tag,
  Typography,
  Button,
  Space,
  Input,
  Select,
  Drawer,
  Descriptions,
  Tooltip,
  Alert,
  Tabs,
  Badge,
  App,
  Popconfirm,
  List,
  Grid,
} from 'antd';
import {
  CompassOutlined,
  BuildOutlined,
  AlertOutlined,
  DatabaseOutlined,
  ExportOutlined,
  ReloadOutlined,
  SearchOutlined,
  EnvironmentOutlined,
  EyeOutlined,
  CopyOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { ColumnsType } from 'antd/es/table';
import { Key } from 'antd/es/table/interface';
import type { EventDataNode } from 'antd/es/tree';
import { useAuth } from '../context/AuthContext';
import {
  fetchDatasetTree,
  fetchDatasetMetadata,
  fetchDatasetRecords,
  exportDatasetRecords,
  deleteAssetRecord,
  DatasetTreeNode,
  DatasetMetadata,
  RecordItem,
  getApiErrorMessage,
  getApiErrorPresentation,
  shouldRetryQuery,
  JsonObject,
  JsonValue,
} from '../services/api';
import { AssetFormModal } from '../components/assets/AssetFormModal';
import { AssetImportModal } from '../components/assets/AssetImportModal';
import { EmptyState, StandardPagination, TableSkeleton } from '../components/common';

const { Title, Text, Paragraph } = Typography;
const { useBreakpoint } = Grid;

type AttributeResolution =
  | { kind: 'value'; value: string }
  | { kind: 'null' }
  | { kind: 'unmapped' };

interface DynamicColumnConfig {
  fieldName: string;
  label: string;
  width: number;
}

const DYNAMIC_COLUMN_ALLOWLIST: DynamicColumnConfig[] = [
  { fieldName: 'route_code', label: 'Mã tuyến', width: 120 },
  { fieldName: 'route_name', label: 'Tên tuyến', width: 180 },
  { fieldName: 'lytrinh', label: 'Lý trình', width: 140 },
  { fieldName: 'km_from', label: 'Km đầu', width: 110 },
  { fieldName: 'km_to', label: 'Km cuối', width: 110 },
  { fieldName: 'province_name', label: 'Tỉnh / Thành phố', width: 160 },
  { fieldName: 'district_name', label: 'Quận / Huyện', width: 150 },
  { fieldName: 'town_name', label: 'Xã / Phường', width: 150 },
  { fieldName: 'management_agency', label: 'Đơn vị trực tiếp quản lý', width: 190 },
  { fieldName: 'road_class', label: 'Cấp đường', width: 130 },
  { fieldName: 'road_type', label: 'Loại đường', width: 130 },
  { fieldName: 'active_status', label: 'Tình trạng khai thác', width: 160 },
  { fieldName: 'construction_year', label: 'Năm xây dựng', width: 120 },
  { fieldName: 'length', label: 'Chiều dài', width: 120 },
  { fieldName: 'width', label: 'Chiều rộng', width: 120 },
  { fieldName: 'height', label: 'Chiều cao', width: 120 },
  { fieldName: 'type', label: 'Phân loại', width: 150 },
];

const TECHNICAL_STATUS_LABELS: Record<string, { label: string; color: string }> = {
  RAW_STORED: { label: 'Đã lưu dữ liệu thô', color: 'blue' },
  CURATED: { label: 'Đã chuẩn hóa', color: 'green' },
  PARSE_FAILED: { label: 'Lỗi phân tích dữ liệu', color: 'red' },
  SKIPPED: { label: 'Đã bỏ qua', color: 'default' },
  STALE: { label: 'Cần đồng bộ lại', color: 'orange' },
};

const isJsonObject = (value: JsonValue | undefined): value is JsonObject =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const toDisplayString = (value: JsonValue | undefined): string | null => {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'string') return value.replace(/<[^>]*>/g, '').trim() || null;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
};

const resolveAttribute = (payload: JsonObject | null, identify: string): AttributeResolution => {
  if (!payload) return { kind: 'unmapped' };
  if (Object.prototype.hasOwnProperty.call(payload, identify)) {
    const value = toDisplayString(payload[identify]);
    return value === null ? { kind: 'null' } : { kind: 'value', value };
  }

  const rawAttributes = payload.data_;
  if (Array.isArray(rawAttributes)) {
    const found = rawAttributes.find(
      (item) => isJsonObject(item) && item.column_identify === identify
    );
    if (found && isJsonObject(found)) {
      const value = toDisplayString(found.value_display ?? found.column_value);
      return value === null ? { kind: 'null' } : { kind: 'value', value };
    }
  }
  return { kind: 'unmapped' };
};

const renderAttribute = (payload: JsonObject | null, identify: string): React.ReactNode => {
  const resolved = resolveAttribute(payload, identify);
  if (resolved.kind === 'value') return resolved.value;
  if (resolved.kind === 'null') return <Text type="secondary">Chưa có dữ liệu</Text>;
  return (
    <Tooltip title={`API không trả về trường "${identify}" cho bản ghi này.`}>
      <Tag color="warning">Thiếu ánh xạ</Tag>
    </Tooltip>
  );
};

const renderRecordStatus = (record: RecordItem): React.ReactNode => {
  const businessState = toDisplayString(record.payload?.state_name);
  if (businessState) return <Tag color="green">{businessState}</Tag>;

  const rawStatus = record.recordStatus;
  const status = TECHNICAL_STATUS_LABELS[rawStatus] ?? {
    label: 'Trạng thái kỹ thuật chưa định danh',
    color: 'default',
  };
  return (
    <Tooltip title={`Giá trị kỹ thuật: ${rawStatus}`}>
      <Tag color={status.color}>{status.label}</Tag>
    </Tooltip>
  );
};

const getPayloadAttributes = (payload: JsonObject | null): JsonObject[] => {
  const value = payload?.data_;
  return Array.isArray(value) ? value.filter(isJsonObject) : [];
};

const formatDateTime = (isoStr?: string): string => {
  if (!isoStr) return '—';
  try {
    const d = new Date(isoStr);
    if (isNaN(d.getTime())) return isoStr;
    return d.toLocaleString('vi-VN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return isoStr;
  }
};

const resolveBranchName = (code?: string): string => {
  if (!code) return 'Toàn quốc';
  switch (code) {
    case 'kqldb_1':
      return 'Khu QLĐB I';
    case 'kqldb_2':
      return 'Khu QLĐB II';
    case 'kqldb_3':
      return 'Khu QLĐB III';
    case 'kqldb_4':
      return 'Khu QLĐB IV';
    case 'cdb_vn':
      return 'Cục Đường bộ VN';
    default:
      return code.startsWith('sxd_') ? `Sở GTVT ${code.substring(4)}` : code;
  }
};

// Recursive helper for tree lazy loading
function updateTreeData(
  list: DatasetTreeNode[],
  key: string,
  children: DatasetTreeNode[]
): DatasetTreeNode[] {
  return list.map((node) => {
    if (node.key === key) {
      return { ...node, children };
    }
    if (node.children) {
      return { ...node, children: updateTreeData(node.children, key, children) };
    }
    return node;
  });
}

export const AssetListPage: React.FC = () => {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const screens = useBreakpoint();
  const [searchParams, setSearchParams] = useSearchParams();

  // Selected dataset from URL or default to vertical slice tbl_road_sign
  const initialDataset = searchParams.get('datasetKey') || 'tbl_road_sign';
  const initialBranch = searchParams.get('branch') || '';

  const [selectedDatasetKey, setSelectedDatasetKey] = useState<string>(initialDataset);
  const [searchKeyword, setSearchKeyword] = useState<string>('');
  const [branchFilter, setBranchFilter] = useState<string>(initialBranch);
  const [sortField, setSortField] = useState<string>('id');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');
  const [page, setPage] = useState<number>(0);
  const [pageSize, setPageSize] = useState<number>(20);
  const [treeSearch, setTreeSearch] = useState<string>('');

  // Selected rows for bulk actions
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);

  // Record detail drawer state
  const [detailRecord, setDetailRecord] = useState<RecordItem | null>(null);
  const [drawerVisible, setDrawerVisible] = useState<boolean>(false);

  // Tree state for lazy-loading
  const [treeData, setTreeData] = useState<DatasetTreeNode[]>([]);

  // Check role for bulk selection
  const canBulkAction = useMemo(() => {
    return (
      user?.role === 'ROLE_ADMIN' ||
      user?.role === 'ROLE_MANAGER' ||
      user?.role === 'ROLE_EDITOR' ||
      user?.permissions?.includes('asset:update') ||
      user?.permissions?.includes('asset:export')
    );
  }, [user]);

  // Check roles for CRUD operations
  const canCreate = useMemo(() => {
    return (
      user?.role === 'ROLE_ADMIN' ||
      user?.role === 'ROLE_MANAGER' ||
      user?.role === 'ROLE_EDITOR' ||
      user?.permissions?.includes('asset:create') ||
      user?.permissions?.includes('ASSET:CREATE')
    );
  }, [user]);

  const canEdit = useMemo(() => {
    return (
      user?.role === 'ROLE_ADMIN' ||
      user?.role === 'ROLE_MANAGER' ||
      user?.role === 'ROLE_EDITOR' ||
      user?.permissions?.includes('asset:update') ||
      user?.permissions?.includes('ASSET:UPDATE')
    );
  }, [user]);

  const canDelete = useMemo(() => {
    return (
      user?.role === 'ROLE_ADMIN' ||
      user?.role === 'ROLE_MANAGER' ||
      user?.permissions?.includes('asset:delete') ||
      user?.permissions?.includes('ASSET:DELETE')
    );
  }, [user]);

  const canImport = useMemo(() => {
    return (
      user?.role === 'ROLE_ADMIN' ||
      user?.role === 'ROLE_MANAGER' ||
      user?.permissions?.includes('import:create') ||
      user?.permissions?.includes('IMPORT:CREATE')
    );
  }, [user]);

  // CRUD Modal States
  const [formModalVisible, setFormModalVisible] = useState<boolean>(false);
  const [formModalMode, setFormModalMode] = useState<'create' | 'edit'>('create');
  const [selectedRecordForEdit, setSelectedRecordForEdit] = useState<RecordItem | null>(null);
  const [importModalVisible, setImportModalVisible] = useState<boolean>(false);
  const [exporting, setExporting] = useState(false);

  const deleteMutation = useMutation({
    mutationFn: async (record: RecordItem) => {
      const rawVersion = record.payload?.version;
      const version = typeof rawVersion === 'number' ? rawVersion : undefined;
      await deleteAssetRecord(selectedDatasetKey, record.recordKey, version, 'Xóa mềm theo yêu cầu người dùng');
      return record;
    },
    onSuccess: (record) => {
      message.success(`Đã xóa mềm thành công bản ghi: ${record.recordKey}`);
      setSelectedRowKeys((keys) => keys.filter((key) => key !== record.recordKey));
      void queryClient.invalidateQueries({ queryKey: ['datasetRecords', selectedDatasetKey] });
    },
    onError: (error) => {
      const presentation = getApiErrorPresentation(error);
      message.error(`${presentation.title}: ${presentation.description}`);
    },
  });

  // Sync state when URL params change
  useEffect(() => {
    const urlDataset = searchParams.get('datasetKey');
    if (urlDataset && urlDataset !== selectedDatasetKey) {
      setSelectedDatasetKey(urlDataset);
      setPage(0);
      setSelectedRowKeys([]);
    }
    const urlBranch = searchParams.get('branch');
    if (urlBranch !== null && urlBranch !== branchFilter) {
      setBranchFilter(urlBranch);
      setPage(0);
    }
  }, [searchParams, selectedDatasetKey, branchFilter]);

  // 1. Fetch Tree Hierarchy
  const treeQuery = useQuery({
    queryKey: ['datasetTree'],
    queryFn: () => fetchDatasetTree(),
    staleTime: 5 * 60 * 1000,
    retry: shouldRetryQuery,
  });

  useEffect(() => {
    if (treeQuery.data) {
      setTreeData(treeQuery.data);
    }
  }, [treeQuery.data]);

  // Lazy load node children
  const handleLoadTreeData = async ({ key, children }: EventDataNode<DatasetTreeNode>) => {
    if (children && children.length > 0) return;
    try {
      const childNodes = await fetchDatasetTree(key);
      setTreeData((origin) => updateTreeData(origin, key, childNodes));
    } catch {
      message.error('Không thể tải các nhánh con của cây dữ liệu');
    }
  };

  // 2. Fetch Dataset Metadata
  const metadataQuery = useQuery<DatasetMetadata>({
    queryKey: ['datasetMetadata', selectedDatasetKey],
    queryFn: () => fetchDatasetMetadata(selectedDatasetKey),
    staleTime: 60 * 1000,
    retry: shouldRetryQuery,
  });
  const metadata = metadataQuery.data;

  // 3. Fetch Paged Records
  const queryParams = useMemo(() => {
    const p: Record<string, string | number | undefined> = {
      page,
      size: pageSize,
      q: searchKeyword.trim() || undefined,
      sort: `${sortField},${sortOrder}`,
    };
    if (branchFilter) {
      p.filter_branch_id = branchFilter;
    }
    return p;
  }, [page, pageSize, searchKeyword, branchFilter, sortField, sortOrder]);

  const {
    data: recordsData,
    isLoading: loadingRecords,
    refetch: refetchRecords,
    isFetching: fetchingRecords,
    isError: recordsError,
    error: recordsQueryError,
  } = useQuery({
    queryKey: ['datasetRecords', selectedDatasetKey, queryParams],
    queryFn: () => fetchDatasetRecords(selectedDatasetKey, queryParams),
    staleTime: 30 * 1000,
    placeholderData: keepPreviousData,
    retry: shouldRetryQuery,
  });

  // Handle tree selection
  const handleTreeSelect = (selectedKeys: Key[], info: { node: DatasetTreeNode }) => {
    if (selectedKeys.length > 0) {
      const node = info.node as DatasetTreeNode;
      if (node.isLeaf && node.datasetKey) {
        setSelectedDatasetKey(node.datasetKey);
        setPage(0);
        setSelectedRowKeys([]);
        setSearchParams({ datasetKey: node.datasetKey });
      }
    }
  };

  // Quick switcher for vertical slices
  const handleSelectVerticalSlice = (key: string) => {
    setSelectedDatasetKey(key);
    setPage(0);
    setSelectedRowKeys([]);
    setSearchParams({ datasetKey: key });
  };

  // Export records to CSV
  const handleExportCsv = async () => {
    if (exporting) return;
    try {
      setExporting(true);
      message.loading({ content: 'Đang chuẩn bị tệp CSV từ server...', key: 'export' });
      const blob = await exportDatasetRecords(selectedDatasetKey, {
        q: searchKeyword.trim() || undefined,
        sort: `${sortField},${sortOrder}`,
        limit: 2000,
        filter_branch_id: branchFilter || undefined,
      });

      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${selectedDatasetKey}_export.csv`;
      document.body.appendChild(link);
      link.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(link);

      message.success({
        content: `Đã xuất dữ liệu dataset ${selectedDatasetKey} thành công!`,
        key: 'export',
      });
    } catch (err: unknown) {
      message.error({ content: `Lỗi khi xuất tệp CSV: ${getApiErrorMessage(err)}`, key: 'export' });
    } finally {
      setExporting(false);
    }
  };

  // Extract human-readable display name of record
  const getRecordDisplayName = (record: RecordItem): string => {
    const p = record.payload;
    if (!p) return record.recordKey;
    const name = toDisplayString(p.fielddisplay) ?? toDisplayString(p.name) ?? toDisplayString(p.text);
    if (name) return name;
    return record.recordKey;
  };

  // Extract geometry coordinate from record
  const getCoordinates = (record: RecordItem): { lon: number; lat: number } | null => {
    const p = record.payload;
    if (!p) return null;
    const rawLon = p.x_min !== undefined ? p.x_min : p.longitude;
    const rawLat = p.y_min !== undefined ? p.y_min : p.latitude;
    if (rawLon !== undefined && rawLat !== undefined && rawLon !== null && rawLat !== null) {
      const lon = typeof rawLon === 'number' ? rawLon : parseFloat(String(rawLon));
      const lat = typeof rawLat === 'number' ? rawLat : parseFloat(String(rawLat));
      if (!isNaN(lon) && !isNaN(lat)) {
        return { lon, lat };
      }
    }
    return null;
  };

  const renderRecordActions = (record: RecordItem) => {
    const isDeleting = deleteMutation.isPending && deleteMutation.variables?.recordKey === record.recordKey;
    return (
      <Space size="small" wrap={!screens.md}>
        <Button
          type="primary"
          ghost
          size="small"
          icon={<EyeOutlined />}
          onClick={() => {
            setDetailRecord(record);
            setDrawerVisible(true);
          }}
        >
          Chi tiết
        </Button>

        {canEdit && (
          <Button
            size="small"
            icon={<EditOutlined />}
            disabled={deleteMutation.isPending}
            onClick={() => {
              setSelectedRecordForEdit(record);
              setFormModalMode('edit');
              setFormModalVisible(true);
            }}
          >
            Sửa
          </Button>
        )}

        {canDelete && (
          <Popconfirm
            title="Xác nhận xóa mềm bản ghi?"
            description={`Bản ghi ${record.recordKey} sẽ được chuyển sang trạng thái đã xóa mềm.`}
            onConfirm={() => {
              if (!deleteMutation.isPending) deleteMutation.mutate(record);
            }}
            okText="Xóa"
            cancelText="Hủy"
            okButtonProps={{ danger: true, loading: isDeleting, disabled: deleteMutation.isPending && !isDeleting }}
            cancelButtonProps={{ disabled: isDeleting }}
          >
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              loading={isDeleting}
              disabled={deleteMutation.isPending && !isDeleting}
            >
              Xóa
            </Button>
          </Popconfirm>
        )}
      </Space>
    );
  };

  // Generate Dynamic Columns based on dataset key & metadata
  const dynamicColumns: ColumnsType<RecordItem> = (() => {
    const cols: ColumnsType<RecordItem> = [
      {
        title: 'STT',
        key: 'stt',
        width: 60,
        align: 'center',
        render: (_, __, idx) => page * pageSize + idx + 1,
      },
      {
        title: 'Tên / Nhãn Tài sản',
        key: 'fielddisplay',
        width: 240,
        render: (_, record) => {
          const name = getRecordDisplayName(record);
          return (
            <div>
              <Text strong style={{ color: '#003a8c' }}>
                {name}
              </Text>
              <div style={{ fontSize: 11, color: '#8c8c8c' }}>Mã: {record.recordKey}</div>
            </div>
          );
        },
      },
      {
        title: 'Đơn vị Quản lý',
        key: 'branch_id',
        width: 150,
        render: (_, record) => {
          const branchCode = toDisplayString(record.payload?.branch_id);
          return branchCode ? (
            <Tag color="blue">{resolveBranchName(branchCode)}</Tag>
          ) : (
            <Text type="secondary">Chưa gán đơn vị</Text>
          );
        },
      },
      {
        title: 'Trạng thái',
        key: 'state_name',
        width: 120,
        render: (_, record) => {
          return renderRecordStatus(record);
        },
      },
    ];

    // Dataset-specific feature columns (Vertical Slices)
    if (selectedDatasetKey === 'tbl_bridge') {
      cols.push(
        {
          title: 'Lý trình tim cầu',
          key: 'lytrinh',
          width: 140,
          render: (_, record) => renderAttribute(record.payload, 'lytrinh'),
        },
        {
          title: 'Chiều dài (m)',
          key: 'length',
          width: 120,
          align: 'right',
          render: (_, record) => renderAttribute(record.payload, 'length'),
        },
        {
          title: 'Chiều rộng (m)',
          key: 'width',
          width: 120,
          align: 'right',
          render: (_, record) => renderAttribute(record.payload, 'width'),
        },
        {
          title: 'Loại kết cấu',
          key: 'type',
          width: 140,
          render: (_, record) => renderAttribute(record.payload, 'type'),
        }
      );
    } else if (selectedDatasetKey === 'tbl_road_sign') {
      cols.push(
        {
          title: 'Vị trí lý trình',
          key: 'lytrinh',
          width: 150,
          render: (_, record) => renderAttribute(record.payload, 'lytrinh'),
        },
        {
          title: 'Hình dạng biển',
          key: 'shape_sign_id',
          width: 150,
          render: (_, record) => renderAttribute(record.payload, 'shape_sign_id'),
        },
        {
          title: 'Vị trí đặt biển',
          key: 'location_id',
          width: 160,
          render: (_, record) => renderAttribute(record.payload, 'location_id'),
        }
      );
    } else if (selectedDatasetKey === 'mst_national_road') {
      cols.push(
        {
          title: 'Mã số tuyến',
          key: 'road_number',
          width: 120,
          render: (_, record) => renderAttribute(record.payload, 'road_number'),
        },
        {
          title: 'Tên quốc tế',
          key: 'name_en',
          width: 130,
          render: (_, record) => renderAttribute(record.payload, 'name_en'),
        },
        {
          title: 'Chiều dài thực tế',
          key: 'actual_length',
          width: 140,
          align: 'right',
          render: (_, record) => {
            const resolved = resolveAttribute(record.payload, 'actual_length');
            return resolved.kind === 'value' ? `${resolved.value} km` : renderAttribute(record.payload, 'actual_length');
          },
        }
      );
    } else {
      // Dynamic fallback for any other dataset from metadata fields
      const metadataFieldNames = new Set(metadata?.fields?.map((field) => field.fieldName) ?? []);
      const topFields = DYNAMIC_COLUMN_ALLOWLIST.filter((field) => metadataFieldNames.has(field.fieldName)).slice(0, 3);
      topFields.forEach((field) => {
        cols.push({
          title: field.label,
          key: field.fieldName,
          width: field.width,
          render: (_, record) => renderAttribute(record.payload, field.fieldName),
        });
      });
    }

    // Geometry Map Column
    cols.push({
      title: 'Không gian GIS',
      key: 'geometry',
      width: 130,
      align: 'center',
      render: (_, record) => {
        const coords = getCoordinates(record);
        if (coords) {
          return (
            <Tooltip title={`Tọa độ: [${coords.lon.toFixed(4)}, ${coords.lat.toFixed(4)}] - Bấm để mở bản đồ WebGIS`}>
              <Button
                type="link"
                size="small"
                icon={<EnvironmentOutlined style={{ color: '#52c41a' }} />}
                onClick={() =>
                  navigate(`/map?dataset=${selectedDatasetKey}&id=${record.recordKey}&lat=${coords.lat}&lng=${coords.lon}`)
                }
              >
                Mở GIS
              </Button>
            </Tooltip>
          );
        }
        return <Text type="secondary" style={{ fontSize: 11 }}>Không có tọa độ</Text>;
      },
    });

    // Action Column
    cols.push({
      title: 'Thao tác',
      key: 'action',
      width: canEdit || canDelete ? 250 : 110,
      align: 'center',
      fixed: 'right',
      render: (_, record) => renderRecordActions(record),
    });

    return cols;
  })();

  // Filtered tree nodes by search input
  const filteredTreeData = useMemo<DatasetTreeNode[]>(() => {
    if (!treeSearch.trim()) return treeData;
    const term = treeSearch.toLowerCase();
    const filterNodes = (nodes: DatasetTreeNode[]): DatasetTreeNode[] => {
      const res: DatasetTreeNode[] = [];
      for (const n of nodes) {
        const match =
          n.title.toLowerCase().includes(term) ||
          (n.datasetKey && n.datasetKey.toLowerCase().includes(term));
        const filteredChildren = n.children ? filterNodes(n.children) : undefined;
        if (match || (filteredChildren && filteredChildren.length > 0)) {
          res.push({
            ...n,
            children: filteredChildren,
          });
        }
      }
      return res;
    };
    return filterNodes(treeData);
  }, [treeData, treeSearch]);

  // Bulk row selection configuration
  const rowSelection = canBulkAction
    ? {
        selectedRowKeys,
        onChange: (newSelectedRowKeys: Key[]) => {
          setSelectedRowKeys(newSelectedRowKeys);
        },
      }
    : undefined;

  return (
    <div>
      {/* Page Title & Breadcrumb header */}
      <div style={{ marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0, color: '#003a8c' }}>
          Cây Tài sản & Danh mục Hồ sơ KCHT Đường bộ
        </Title>
        <Paragraph type="secondary" style={{ margin: '4px 0 0 0', fontSize: 13 }}>
          Tra cứu phân cấp 658 tập dữ liệu, cấu hình cột động theo metadata, tìm kiếm và phân trang server-side.
        </Paragraph>
      </div>

      <Row gutter={[16, 16]}>
        {/* Left Side: Tree Hierarchy with Lazy-load */}
        <Col xs={24} md={8} lg={6}>
          <Card
            title={
              <Space>
                <DatabaseOutlined style={{ color: '#003a8c' }} />
                <span>Cây Phân Cấp Dữ liệu</span>
              </Space>
            }
            size="small"
            variant="borderless"
            className="kcht-card"
            style={{ borderRadius: 8 }}
          >
            {/* 3 Vertical Slice Shortcut Buttons */}
            <div style={{ marginBottom: 12 }}>
              <Text strong style={{ fontSize: 11, color: '#8c8c8c', display: 'block', marginBottom: 6 }}>
                CHỌN NHANH VERTICAL SLICE:
              </Text>
              <Space direction="vertical" style={{ width: '100%' }} size={6}>
                <Button
                  block
                  size="small"
                  type={selectedDatasetKey === 'tbl_road_sign' ? 'primary' : 'default'}
                  icon={<AlertOutlined />}
                  onClick={() => handleSelectVerticalSlice('tbl_road_sign')}
                  style={{ textAlign: 'left', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
                >
                  <span>Biển báo đường bộ</span>
                  <Badge count="222k" style={{ backgroundColor: '#52c41a' }} />
                </Button>
                <Button
                  block
                  size="small"
                  type={selectedDatasetKey === 'mst_national_road' ? 'primary' : 'default'}
                  icon={<CompassOutlined />}
                  onClick={() => handleSelectVerticalSlice('mst_national_road')}
                  style={{ textAlign: 'left', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
                >
                  <span>Đường quốc lộ</span>
                  <Badge count="169" style={{ backgroundColor: '#fa8c16' }} />
                </Button>
                <Button
                  block
                  size="small"
                  type={selectedDatasetKey === 'tbl_bridge' ? 'primary' : 'default'}
                  icon={<BuildOutlined />}
                  onClick={() => handleSelectVerticalSlice('tbl_bridge')}
                  style={{ textAlign: 'left', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
                >
                  <span>Cầu đường bộ</span>
                  <Badge count="11.6k" style={{ backgroundColor: '#1677ff' }} />
                </Button>
              </Space>
            </div>

            {/* Tree search box */}
            <Input
              placeholder="Tìm kiếm tập dữ liệu..."
              prefix={<SearchOutlined style={{ color: '#8c8c8c' }} />}
              value={treeSearch}
              onChange={(e) => setTreeSearch(e.target.value)}
              size="small"
              allowClear
              style={{ marginBottom: 12, borderRadius: 4 }}
            />

            {/* Ant Design Tree with Lazy Loading */}
            <div style={{ minHeight: 280, maxHeight: 520, overflowY: 'auto' }}>
              {treeQuery.isLoading ? (
                <TableSkeleton rows={6} columns={1} />
              ) : treeQuery.isError ? (
                <EmptyState
                  title={getApiErrorPresentation(treeQuery.error).title}
                  description={getApiErrorPresentation(treeQuery.error).description}
                  actionText={getApiErrorPresentation(treeQuery.error).retryable ? 'Thử lại' : undefined}
                  onAction={getApiErrorPresentation(treeQuery.error).retryable ? () => void treeQuery.refetch() : undefined}
                  style={{ padding: '20px 8px' }}
                />
              ) : filteredTreeData.length === 0 ? (
                <EmptyState
                  title="Không tìm thấy tập dữ liệu"
                  description="Không có nhánh dữ liệu nào khớp với từ khóa đang nhập."
                  actionText="Xóa tìm kiếm"
                  onAction={() => setTreeSearch('')}
                  style={{ padding: '20px 8px' }}
                />
              ) : (
                <Tree<DatasetTreeNode>
                  treeData={filteredTreeData}
                  loadData={handleLoadTreeData}
                  selectedKeys={[selectedDatasetKey]}
                  onSelect={(keys, info) => handleTreeSelect(keys, { node: info.node })}
                  defaultExpandedKeys={['group_roads', 'group_bridges', 'group_traffic_signs']}
                  showLine={{ showLeafIcon: true }}
                  blockNode
                />
              )}
            </div>
          </Card>
        </Col>

        {/* Right Side: Dynamic Asset Table & Metadata Header */}
        <Col xs={24} md={16} lg={18}>
          <Card
            variant="borderless"
            className="kcht-card"
            style={{ borderRadius: 8, marginBottom: 16 }}
            size="small"
          >
            {/* Dataset Information Header */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: 12,
                marginBottom: 16,
                paddingBottom: 12,
                borderBottom: '1px solid #f0f0f0',
              }}
            >
              <div>
                <Space align="center">
                  <Title level={4} style={{ margin: 0, color: '#003a8c' }}>
                    {metadata?.datasetName || selectedDatasetKey}
                  </Title>
                  <Tag color="geekblue">{selectedDatasetKey}</Tag>
                  <Tag color={metadata?.kind === 'asset' ? 'green' : 'orange'}>
                    {metadata?.kind === 'asset' ? 'Tài sản Vật thể' : 'Danh mục Chuẩn'}
                  </Tag>
                  {metadata?.geometryType && metadata.geometryType !== 'NONE' && (
                    <Tag color="cyan" icon={<EnvironmentOutlined />}>
                      PostGIS {metadata.geometryType}
                    </Tag>
                  )}
                </Space>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                  Tệp nguồn: <code>{metadata?.sourceFile || `${selectedDatasetKey}.json`}</code> | Tổng
                  số:{' '}
                  <strong>
                    {recordsData
                      ? `${recordsData.totalElements.toLocaleString('vi-VN')} bản ghi`
                      : metadataQuery.isLoading || loadingRecords
                        ? 'Đang tải...'
                        : 'Chưa xác định'}
                  </strong>
                </div>
              </div>

              <Space>
                {canCreate && (
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => {
                      setSelectedRecordForEdit(null);
                      setFormModalMode('create');
                      setFormModalVisible(true);
                    }}
                    size="small"
                  >
                    Thêm mới
                  </Button>
                )}
                {canImport && (
                  <Button
                    icon={<UploadOutlined />}
                    onClick={() => setImportModalVisible(true)}
                    size="small"
                  >
                    Nhập dữ liệu
                  </Button>
                )}
                <Button
                  icon={<ReloadOutlined spin={fetchingRecords} />}
                  onClick={() => refetchRecords()}
                  loading={fetchingRecords}
                  size="small"
                >
                  Làm mới
                </Button>
                <Button
                  icon={<ExportOutlined />}
                  onClick={() => handleExportCsv()}
                  loading={exporting}
                  disabled={exporting}
                  size="small"
                >
                  Xuất file CSV
                </Button>
                <Button
                  icon={<CompassOutlined />}
                  onClick={() => navigate(`/map?dataset=${selectedDatasetKey}`)}
                  size="small"
                >
                  Mở WebGIS
                </Button>
              </Space>
            </div>

            {/* Filter & Search Toolbar */}
            <Row gutter={[12, 12]} style={{ marginBottom: 16 }} align="middle">
              <Col xs={24} sm={10} md={8}>
                <Input
                  placeholder="Tìm kiếm theo từ khóa (q)..."
                  prefix={<SearchOutlined />}
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  onPressEnter={() => setPage(0)}
                  allowClear
                />
              </Col>
              <Col xs={12} sm={6} md={6}>
                <Select
                  style={{ width: '100%' }}
                  placeholder="Lọc theo Đơn vị"
                  allowClear
                  value={branchFilter || undefined}
                  onChange={(val) => {
                    setBranchFilter(val || '');
                    setPage(0);
                  }}
                  options={[
                    { value: 'kqldb_1', label: 'Khu QLĐB I' },
                    { value: 'kqldb_2', label: 'Khu QLĐB II' },
                    { value: 'kqldb_3', label: 'Khu QLĐB III' },
                    { value: 'kqldb_4', label: 'Khu QLĐB IV' },
                    { value: 'cdb_vn', label: 'Cục Đường bộ VN' },
                  ]}
                />
              </Col>
              <Col xs={12} sm={8} md={6}>
                <Select
                  style={{ width: '100%' }}
                  placeholder="Sắp xếp"
                  value={`${sortField},${sortOrder}`}
                  onChange={(val) => {
                    const [f, o] = val.split(',');
                    setSortField(f);
                    setSortOrder(o as 'asc' | 'desc');
                    setPage(0);
                  }}
                  options={[
                    { value: 'id,asc', label: 'ID tăng dần (Mặc định)' },
                    { value: 'id,desc', label: 'ID giảm dần' },
                    { value: 'record_key,asc', label: 'Mã bản ghi (A-Z)' },
                    { value: 'created_at,desc', label: 'Mới nạp gần đây' },
                  ]}
                />
              </Col>
              <Col xs={24} sm={24} md={4} style={{ textAlign: 'right' }}>
                <Button
                  onClick={() => {
                    setSearchKeyword('');
                    setBranchFilter('');
                    setSortField('id');
                    setSortOrder('asc');
                    setPage(0);
                  }}
                  size="middle"
                >
                  Xóa lọc
                </Button>
              </Col>
            </Row>

            {/* Bulk Selection Notification Bar */}
            {selectedRowKeys.length > 0 && (
              <Alert
                type="info"
                showIcon
                style={{ marginBottom: 12, borderRadius: 6 }}
                message={
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>
                      Đã chọn <strong>{selectedRowKeys.length}</strong> bản ghi trong bảng
                    </span>
                    <Space size={8}>
                      <Button size="small" type="primary" ghost onClick={() => handleExportCsv()}>
                        Xuất {selectedRowKeys.length} dòng đã chọn
                      </Button>
                      <Button
                        size="small"
                        disabled
                      >
                        Đánh dấu kiểm tra (chưa hỗ trợ)
                      </Button>
                      <Button size="small" type="link" onClick={() => setSelectedRowKeys([])}>
                        Bỏ chọn
                      </Button>
                    </Space>
                  </div>
                }
              />
            )}

            {!canBulkAction && (
              <div style={{ marginBottom: 8, fontSize: 12, color: '#8c8c8c' }}>
                * Tài khoản quyền <strong>Người xem (Viewer)</strong>: Thao tác chọn nhiều và chỉnh sửa yêu cầu vai trò <strong>Editor/Manager/Admin</strong>.
              </div>
            )}

            {/* Main Dynamic Table / Mobile Cards */}
            {(loadingRecords || metadataQuery.isLoading) && !recordsData ? (
              <TableSkeleton rows={Math.min(pageSize, 8)} columns={6} />
            ) : recordsError || metadataQuery.isError ? (
              (() => {
                const presentation = getApiErrorPresentation(metadataQuery.error ?? recordsQueryError);
                return (
                  <EmptyState
                    title={presentation.title}
                    description={presentation.description}
                    actionText={presentation.retryable ? 'Thử lại' : undefined}
                    onAction={presentation.retryable ? () => {
                      void metadataQuery.refetch();
                      void refetchRecords();
                    } : undefined}
                  />
                );
              })()
            ) : !recordsData?.content.length ? (
              <EmptyState
                title={searchKeyword.trim() || branchFilter ? 'Không có bản ghi phù hợp' : 'Tập dữ liệu chưa có bản ghi'}
                description={
                  searchKeyword.trim() || branchFilter
                    ? 'Không có bản ghi nào khớp với bộ lọc hiện tại.'
                    : 'API trả về danh sách rỗng cho tập dữ liệu này.'
                }
                actionText={searchKeyword.trim() || branchFilter ? 'Xóa bộ lọc' : 'Làm mới'}
                onAction={() => {
                  if (searchKeyword.trim() || branchFilter) {
                    setSearchKeyword('');
                    setBranchFilter('');
                    setPage(0);
                  } else {
                    void refetchRecords();
                  }
                }}
              />
            ) : (
              <>
                {screens.md ? (
                  <Table<RecordItem>
                    className="kcht-asset-table"
                    dataSource={recordsData.content}
                    columns={dynamicColumns}
                    rowKey="recordKey"
                    rowSelection={rowSelection}
                    loading={fetchingRecords}
                    pagination={false}
                    scroll={{ x: 'max-content' }}
                    sticky={{ offsetHeader: 64 }}
                    tableLayout="fixed"
                    size="middle"
                  />
                ) : (
                  <List
                    className="kcht-asset-mobile-list"
                    loading={fetchingRecords}
                    dataSource={recordsData.content}
                    renderItem={(record, index) => (
                      <List.Item key={record.recordKey}>
                        <Card size="small" style={{ width: '100%' }}>
                          <Space direction="vertical" size={8} style={{ width: '100%' }}>
                            <div>
                              <Text type="secondary">#{page * pageSize + index + 1}</Text>
                              <Title level={5} style={{ margin: '2px 0' }}>{getRecordDisplayName(record)}</Title>
                              <Text code>{record.recordKey}</Text>
                            </div>
                            <Space wrap>
                              {renderRecordStatus(record)}
                              {toDisplayString(record.payload?.branch_id) ? (
                                <Tag color="blue">{resolveBranchName(toDisplayString(record.payload?.branch_id) ?? undefined)}</Tag>
                              ) : (
                                <Tag>Chưa gán đơn vị</Tag>
                              )}
                            </Space>
                            <div className="kcht-asset-mobile-actions">{renderRecordActions(record)}</div>
                          </Space>
                        </Card>
                      </List.Item>
                    )}
                  />
                )}
                <StandardPagination
                  page={recordsData.page}
                  size={recordsData.size}
                  totalElements={recordsData.totalElements}
                  onChange={(nextPage, nextSize) => {
                    setPage(nextSize === pageSize ? nextPage : 0);
                    setPageSize(nextSize);
                    setSelectedRowKeys([]);
                  }}
                />
              </>
            )}
          </Card>
        </Col>
      </Row>

      {/* Record Detail Drawer */}
      <Drawer
        title={
          <div>
            <Space align="center">
              <span style={{ fontSize: 16, fontWeight: 700, color: '#003a8c' }}>
                {detailRecord ? getRecordDisplayName(detailRecord) : 'Chi tiết Bản ghi'}
              </span>
              <Tag color="geekblue">{selectedDatasetKey}</Tag>
            </Space>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>
              Khóa nghiệp vụ: {detailRecord?.recordKey}
            </div>
          </div>
        }
        placement="right"
        width={screens.md ? 680 : '100%'}
        onClose={() => setDrawerVisible(false)}
        open={drawerVisible}
        extra={
          <Space>
            <Button
              icon={<CopyOutlined />}
              onClick={async () => {
                if (!detailRecord) return;
                try {
                  await navigator.clipboard.writeText(JSON.stringify(detailRecord.payload, null, 2));
                  message.success('Đã sao chép toàn bộ JSON vào clipboard');
                } catch {
                  message.error('Không thể sao chép JSON vào clipboard.');
                }
              }}
            >
              Sao chép JSON
            </Button>
          </Space>
        }
      >
        {detailRecord && (
          <Tabs
            defaultActiveKey="attributes"
            items={[
              {
                key: 'attributes',
                label: 'Thuộc tính Nghiệp vụ',
                children: (
                  <div>
                    <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 2 }}>
                      <Descriptions.Item label="Mã bản ghi" span={2}>
                        <Text strong copyable>
                          {detailRecord.recordKey}
                        </Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="Tên hiển thị">
                        {getRecordDisplayName(detailRecord)}
                      </Descriptions.Item>
                      <Descriptions.Item label="Đơn vị quản lý">
                        {toDisplayString(detailRecord.payload?.branch_id) ? (
                          <Tag color="blue">
                            {resolveBranchName(toDisplayString(detailRecord.payload?.branch_id) ?? undefined)}
                          </Tag>
                        ) : (
                          <Text type="secondary">Chưa gán đơn vị</Text>
                        )}
                      </Descriptions.Item>
                      <Descriptions.Item label="Trạng thái">
                        {renderRecordStatus(detailRecord)}
                      </Descriptions.Item>
                      <Descriptions.Item label="Cơ quan thành lập">
                        {toDisplayString(detailRecord.payload?.organization_id) ?? (
                          <Text type="secondary">Chưa có dữ liệu</Text>
                        )}
                      </Descriptions.Item>
                    </Descriptions>

                    {/* Detailed attributes table from data_ */}
                    {getPayloadAttributes(detailRecord.payload).length > 0 && (
                      <div style={{ marginTop: 20 }}>
                        <Title level={5}>Từ điển Thuộc tính Kỹ thuật ({getPayloadAttributes(detailRecord.payload).length} trường)</Title>
                        <Descriptions bordered size="small" column={1}>
                          {getPayloadAttributes(detailRecord.payload).map((attr, idx) => {
                            const cleanVal = toDisplayString(attr.value_display ?? attr.column_value);
                            const label = toDisplayString(attr.column_name) ?? toDisplayString(attr.column_identify);
                            return (
                              <Descriptions.Item
                                key={idx}
                                label={label ?? `Trường ${idx + 1}`}
                              >
                                {cleanVal ?? <Text type="secondary">Chưa có dữ liệu</Text>}
                              </Descriptions.Item>
                            );
                          })}
                        </Descriptions>
                      </div>
                    )}
                  </div>
                ),
              },
              {
                key: 'gis',
                label: 'Không gian & Tọa độ GIS',
                children: (
                  <div>
                    {getCoordinates(detailRecord) ? (
                      <Space direction="vertical" style={{ width: '100%' }} size={16}>
                        <Alert
                          message="Dữ liệu Không gian PostGIS Chuẩn EPSG:4326 (WGS 84)"
                          description={`Kinh độ (Longitude): ${getCoordinates(detailRecord)?.lon} | Vĩ độ (Latitude): ${getCoordinates(detailRecord)?.lat}`}
                          type="success"
                          showIcon
                        />

                        <Descriptions bordered size="small" column={1}>
                          <Descriptions.Item label="Kiểu hình học">
                            <Tag color="cyan">
                              {toDisplayString(detailRecord.payload?.type_geom) ?? metadata?.geometryType ?? 'Chưa xác định'}
                            </Tag>
                          </Descriptions.Item>
                          <Descriptions.Item label="Kinh độ X (Longitude)">
                            {getCoordinates(detailRecord)?.lon}
                          </Descriptions.Item>
                          <Descriptions.Item label="Vĩ độ Y (Latitude)">
                            {getCoordinates(detailRecord)?.lat}
                          </Descriptions.Item>
                          <Descriptions.Item label="Khung bao (BBOX)">
                            <Space wrap>
                              <span>x_min: {renderAttribute(detailRecord.payload, 'x_min')}</span>
                              <span>y_min: {renderAttribute(detailRecord.payload, 'y_min')}</span>
                              <span>x_max: {renderAttribute(detailRecord.payload, 'x_max')}</span>
                              <span>y_max: {renderAttribute(detailRecord.payload, 'y_max')}</span>
                            </Space>
                          </Descriptions.Item>
                        </Descriptions>

                        <Button
                          type="primary"
                          block
                          icon={<CompassOutlined />}
                          onClick={() => {
                            const coords = getCoordinates(detailRecord);
                            navigate(
                              `/map?dataset=${selectedDatasetKey}&id=${detailRecord.recordKey}&lat=${coords?.lat}&lng=${coords?.lon}`
                            );
                          }}
                        >
                          Mở Định vị trên Bản đồ Số WebGIS
                        </Button>
                      </Space>
                    ) : (
                      <Alert
                        message="Không có Tọa độ Không gian"
                        description="Bản ghi này thuộc danh mục nghiệp vụ văn bản hoặc chưa được gắn định vị vệ tinh GPS."
                        type="info"
                        showIcon
                      />
                    )}
                  </div>
                ),
              },
              {
                key: 'rawJson',
                label: 'Dữ liệu Gốc JSON',
                children: (
                  <pre
                    style={{
                      background: '#f5f5f5',
                      padding: 12,
                      borderRadius: 6,
                      fontSize: 12,
                      maxHeight: 450,
                      overflowY: 'auto',
                    }}
                  >
                    {JSON.stringify(detailRecord.payload, null, 2)}
                  </pre>
                ),
              },
              {
                key: 'audit',
                label: 'Kiểm toán & Nguồn gốc',
                children: (
                  <Descriptions bordered size="small" column={1}>
                    <Descriptions.Item label="ID Hệ thống (DB)">{detailRecord.id}</Descriptions.Item>
                    <Descriptions.Item label="Tập dữ liệu gốc">{detailRecord.datasetKey}</Descriptions.Item>
                    <Descriptions.Item label="Trạng thái bản ghi">
                      {renderRecordStatus(detailRecord)}
                    </Descriptions.Item>
                    <Descriptions.Item label="Thời điểm nạp vào CSDL">
                      {formatDateTime(detailRecord.createdAt)}
                    </Descriptions.Item>
                    <Descriptions.Item label="Thời điểm cập nhật mới nhất">
                      {formatDateTime(detailRecord.updatedAt)}
                    </Descriptions.Item>
                  </Descriptions>
                ),
              },
            ]}
          />
        )}
      </Drawer>

      {/* CRUD Form Modal */}
      <AssetFormModal
        visible={formModalVisible}
        mode={formModalMode}
        datasetKey={selectedDatasetKey}
        metadata={metadata}
        initialRecord={selectedRecordForEdit}
        onClose={() => setFormModalVisible(false)}
        onSuccess={() => {
          void queryClient.invalidateQueries({ queryKey: ['datasetRecords', selectedDatasetKey] });
          void queryClient.invalidateQueries({ queryKey: ['datasetMetadata', selectedDatasetKey] });
        }}
      />

      {/* Batch Import Modal */}
      <AssetImportModal
        visible={importModalVisible}
        datasetKey={selectedDatasetKey}
        datasetName={metadata?.datasetName}
        onClose={() => setImportModalVisible(false)}
        onSuccess={() => {
          void queryClient.invalidateQueries({ queryKey: ['datasetRecords', selectedDatasetKey] });
          void queryClient.invalidateQueries({ queryKey: ['datasetMetadata', selectedDatasetKey] });
          void queryClient.invalidateQueries({ queryKey: ['datasetTree'] });
        }}
      />
    </div>
  );
};
