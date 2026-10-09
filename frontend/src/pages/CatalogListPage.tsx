import React, { useState, useMemo } from 'react';
import {
  Card,
  Typography,
  Space,
  Row,
  Col,
  Table,
  Tag,
  Button,
  Input,
  Modal,
  Form,
  InputNumber,
  Switch,
  message,
  Popconfirm,
  Tooltip,
  List,
  Badge,
  Tabs,
  Statistic,
  Popover,
} from 'antd';
import {
  AppstoreOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SearchOutlined,
  ReloadOutlined,
  LockOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  EnvironmentOutlined,
  CarOutlined,
  SafetyOutlined,
  TeamOutlined,
  SettingOutlined,
  DatabaseOutlined,
  FolderOpenOutlined,
  SafetyCertificateOutlined,
  ApartmentOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchAllCatalogs,
  fetchCatalogItems,
  createCatalogItem,
  updateCatalogItem,
  deleteCatalogItem,
  CatalogSummary,
  ReferenceCatalogItem,
} from '../services/catalogApi';
import { getApiErrorMessage, shouldRetryQuery } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { EmptyState, TableSkeleton } from '../components/common';

const { Title, Paragraph, Text } = Typography;

// Helper to render icon by group or string
const renderCatalogIcon = (groupKey?: string, iconName?: string) => {
  const iconProps = { style: { fontSize: 16 } };
  if (iconName === 'environment' || groupKey === 'administrative') return <EnvironmentOutlined {...iconProps} style={{ fontSize: 16, color: '#1890ff' }} />;
  if (iconName === 'road' || groupKey === 'infrastructure') return <CarOutlined {...iconProps} style={{ fontSize: 16, color: '#52c41a' }} />;
  if (iconName === 'safety' || groupKey === 'traffic_safety') return <SafetyOutlined {...iconProps} style={{ fontSize: 16, color: '#fa8c16' }} />;
  if (iconName === 'team' || groupKey === 'organization') return <TeamOutlined {...iconProps} style={{ fontSize: 16, color: '#722ed1' }} />;
  if (iconName === 'setting' || groupKey === 'technical') return <SettingOutlined {...iconProps} style={{ fontSize: 16, color: '#13c2c2' }} />;
  return <DatabaseOutlined {...iconProps} style={{ fontSize: 16, color: '#8c8c8c' }} />;
};

export const CatalogListPage: React.FC = () => {
  const { hasRole } = useAuth();
  const canEdit = hasRole(['ADMIN', 'MANAGER']);
  const queryClient = useQueryClient();

  const [selectedCatalog, setSelectedCatalog] = useState<string>('c_tinhthanhpho');
  const [activeTabGroup, setActiveTabGroup] = useState<string>('all');
  const [catalogSearch, setCatalogSearch] = useState<string>('');
  const [itemKeyword, setItemKeyword] = useState<string>('');
  const [page, setPage] = useState<number>(0);
  const pageSize = 15;

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<ReferenceCatalogItem | null>(null);
  const [form] = Form.useForm();

  // Queries
  const catalogsQuery = useQuery<CatalogSummary[]>({
    queryKey: ['referenceCatalogs'],
    queryFn: fetchAllCatalogs,
    retry: shouldRetryQuery,
  });

  const itemsQuery = useQuery({
    queryKey: ['catalogItems', selectedCatalog, page, itemKeyword],
    queryFn: () => fetchCatalogItems(selectedCatalog, { page, size: pageSize, q: itemKeyword }),
    enabled: Boolean(selectedCatalog),
    retry: shouldRetryQuery,
  });

  // Mutations
  const saveMutation = useMutation({
    mutationFn: async (values: ReferenceCatalogItem) => {
      if (editingItem) {
        return updateCatalogItem(selectedCatalog, editingItem.itemCode, values);
      }
      return createCatalogItem(selectedCatalog, values);
    },
    onSuccess: () => {
      message.success(editingItem ? 'Cập nhật danh mục thành công' : 'Thêm mới phần tử thành công');
      setIsModalOpen(false);
      form.resetFields();
      setEditingItem(null);
      void queryClient.invalidateQueries({ queryKey: ['catalogItems', selectedCatalog] });
      void queryClient.invalidateQueries({ queryKey: ['referenceCatalogs'] });
    },
    onError: (err: unknown) => {
      message.error(getApiErrorMessage(err));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (itemCode: string) => {
      return deleteCatalogItem(selectedCatalog, itemCode);
    },
    onSuccess: () => {
      message.success('Đã xóa phần tử danh mục thành công');
      void queryClient.invalidateQueries({ queryKey: ['catalogItems', selectedCatalog] });
      void queryClient.invalidateQueries({ queryKey: ['referenceCatalogs'] });
    },
    onError: (err: unknown) => {
      message.error(getApiErrorMessage(err));
    },
  });

  const catalogList = useMemo(() => catalogsQuery.data || [], [catalogsQuery.data]);

  // Aggregate KPI metrics
  const totalCatalogs = catalogList.length;
  const totalRecords = useMemo(
    () => catalogList.reduce((sum, item) => sum + (item.itemCount || 0), 0),
    [catalogList]
  );
  const groupStats = useMemo(() => {
    const counts: Record<string, number> = {
      administrative: 0,
      infrastructure: 0,
      traffic_safety: 0,
      organization: 0,
      technical: 0,
      other: 0,
    };
    catalogList.forEach((c) => {
      const g = c.groupKey || 'other';
      counts[g] = (counts[g] || 0) + 1;
    });
    return counts;
  }, [catalogList]);

  // Filter catalogs for left list based on Tab and Search query
  const filteredCatalogs = useMemo(() => {
    return catalogList.filter((c) => {
      const matchesTab = activeTabGroup === 'all' || c.groupKey === activeTabGroup;
      const searchLower = catalogSearch.trim().toLowerCase();
      const matchesSearch =
        !searchLower ||
        c.catalogName.toLowerCase().includes(searchLower) ||
        c.catalogCode.toLowerCase().includes(searchLower) ||
        (c.description && c.description.toLowerCase().includes(searchLower));
      return matchesTab && matchesSearch;
    });
  }, [catalogList, activeTabGroup, catalogSearch]);

  const currentCatalog = useMemo(() => {
    return (
      catalogList.find((c) => c.catalogCode === selectedCatalog) || {
        catalogCode: selectedCatalog,
        catalogName: selectedCatalog,
        description: 'Dữ liệu danh mục tham chiếu chuẩn hóa',
        itemCount: 0,
        sourceType: 'system',
        isEditable: true,
        groupKey: 'other',
        groupName: 'Danh mục khác',
      }
    );
  }, [catalogList, selectedCatalog]);

  const handleOpenCreateModal = () => {
    setEditingItem(null);
    form.resetFields();
    form.setFieldsValue({
      catalogCode: selectedCatalog,
      active: true,
      sortOrder: (itemsQuery.data?.totalElements || 0) + 1,
    });
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (item: ReferenceCatalogItem) => {
    setEditingItem(item);
    form.setFieldsValue({
      catalogCode: item.catalogCode,
      itemCode: item.itemCode,
      itemName: item.itemName,
      parentCode: item.parentCode,
      sortOrder: item.sortOrder ?? 0,
      active: item.active !== false,
      extraJson: item.extraAttributes ? JSON.stringify(item.extraAttributes, null, 2) : '',
    });
    setIsModalOpen(true);
  };

  const handleModalSubmit = async () => {
    try {
      const values = await form.validateFields();
      let extraAttributes = {};
      if (values.extraJson && values.extraJson.trim()) {
        try {
          extraAttributes = JSON.parse(values.extraJson);
        } catch {
          message.error('Trường thuộc tính bổ sung phải là chuỗi JSON hợp lệ');
          return;
        }
      }

      const itemPayload: ReferenceCatalogItem = {
        catalogCode: selectedCatalog,
        itemCode: values.itemCode,
        itemName: values.itemName,
        parentCode: values.parentCode || null,
        sortOrder: values.sortOrder || 0,
        active: values.active,
        extraAttributes,
      };

      saveMutation.mutate(itemPayload);
    } catch (validationError: unknown) {
      if (!validationError || typeof validationError !== 'object' || !('errorFields' in validationError)) {
        message.error('Không thể kiểm tra dữ liệu biểu mẫu. Vui lòng thử lại.');
      }
    }
  };

  // Tab definitions with counts
  const tabItems = [
    {
      key: 'all',
      label: (
        <span>
          <AppstoreOutlined style={{ marginRight: 6 }} />
          Tất cả <Tag style={{ marginLeft: 4, borderRadius: 10 }}>{totalCatalogs}</Tag>
        </span>
      ),
    },
    {
      key: 'administrative',
      label: (
        <span>
          <EnvironmentOutlined style={{ marginRight: 6, color: '#1890ff' }} />
          Địa giới <Tag color="blue" style={{ marginLeft: 4, borderRadius: 10 }}>{groupStats.administrative}</Tag>
        </span>
      ),
    },
    {
      key: 'infrastructure',
      label: (
        <span>
          <CarOutlined style={{ marginRight: 6, color: '#52c41a' }} />
          Hạ tầng đường <Tag color="green" style={{ marginLeft: 4, borderRadius: 10 }}>{groupStats.infrastructure}</Tag>
        </span>
      ),
    },
    {
      key: 'traffic_safety',
      label: (
        <span>
          <SafetyOutlined style={{ marginRight: 6, color: '#fa8c16' }} />
          Báo hiệu & ATGT <Tag color="orange" style={{ marginLeft: 4, borderRadius: 10 }}>{groupStats.traffic_safety}</Tag>
        </span>
      ),
    },
    {
      key: 'organization',
      label: (
        <span>
          <TeamOutlined style={{ marginRight: 6, color: '#722ed1' }} />
          Đơn vị quản lý <Tag color="purple" style={{ marginLeft: 4, borderRadius: 10 }}>{groupStats.organization}</Tag>
        </span>
      ),
    },
    {
      key: 'technical',
      label: (
        <span>
          <SettingOutlined style={{ marginRight: 6, color: '#13c2c2' }} />
          Kỹ thuật & Phụ trợ <Tag color="cyan" style={{ marginLeft: 4, borderRadius: 10 }}>{groupStats.technical}</Tag>
        </span>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 1. Executive Page Header & Subtitle */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <Space align="center" size={12}>
            <div
              style={{
                width: 44,
                height: 44,
                borderRadius: 10,
                background: 'linear-gradient(135deg, #003a8c 0%, #1677ff 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#fff',
                fontSize: 22,
                boxShadow: '0 4px 12px rgba(0, 58, 140, 0.25)',
              }}
            >
              <FolderOpenOutlined />
            </div>
            <div>
              <Title level={4} style={{ margin: 0, color: '#002140', fontWeight: 700 }}>
                Danh mục Chuẩn Ngành Đường bộ
              </Title>
              <Space size={8} wrap style={{ marginTop: 2 }}>
                <Paragraph type="secondary" style={{ margin: 0, fontSize: 13 }}>
                  Hệ thống phân loại chuẩn hóa quốc gia: Địa giới hành chính, cấp đường, kết cấu, biển báo, đơn vị quản lý.
                </Paragraph>
                <Tag color="geekblue" style={{ fontSize: 11, borderRadius: 4 }}>
                  QCVN 41:2019/BGTVT
                </Tag>
                <Tag color="cyan" style={{ fontSize: 11, borderRadius: 4 }}>
                  TCVN 4054:2005
                </Tag>
              </Space>
            </div>
          </Space>
        </div>

        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              void catalogsQuery.refetch();
              void itemsQuery.refetch();
            }}
            loading={catalogsQuery.isFetching || itemsQuery.isFetching}
          >
            Làm mới toàn bộ
          </Button>
        </Space>
      </div>

      {/* 2. Top KPI Stat Cards */}
      <Row gutter={[16, 16]}>
        <Col xs={12} sm={6}>
          <Card size="small" className="catalog-kpi-card" variant="borderless">
            <Statistic
              title={<span style={{ fontSize: 13, color: '#595959' }}>Tổng số danh mục</span>}
              value={totalCatalogs}
              prefix={<ApartmentOutlined style={{ color: '#1677ff', marginRight: 8 }} />}
              suffix={<span style={{ fontSize: 13, color: '#8c8c8c' }}>bộ chuẩn</span>}
              valueStyle={{ fontWeight: 700, color: '#003a8c' }}
            />
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card size="small" className="catalog-kpi-card" variant="borderless">
            <Statistic
              title={<span style={{ fontSize: 13, color: '#595959' }}>Tổng số bản ghi chuẩn</span>}
              value={totalRecords}
              formatter={(val) => Number(val).toLocaleString('vi-VN')}
              prefix={<DatabaseOutlined style={{ color: '#52c41a', marginRight: 8 }} />}
              suffix={<span style={{ fontSize: 13, color: '#8c8c8c' }}>mục</span>}
              valueStyle={{ fontWeight: 700, color: '#237804' }}
            />
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card size="small" className="catalog-kpi-card" variant="borderless">
            <Statistic
              title={<span style={{ fontSize: 13, color: '#595959' }}>Nhóm danh mục nghiệp vụ</span>}
              value={5}
              prefix={<FolderOpenOutlined style={{ color: '#fa8c16', marginRight: 8 }} />}
              suffix={<span style={{ fontSize: 13, color: '#8c8c8c' }}>nhóm</span>}
              valueStyle={{ fontWeight: 700, color: '#d46b08' }}
            />
          </Card>
        </Col>

        <Col xs={12} sm={6}>
          <Card size="small" className="catalog-kpi-card" variant="borderless">
            <Statistic
              title={<span style={{ fontSize: 13, color: '#595959' }}>Trạng thái đồng bộ</span>}
              value="100%"
              prefix={<SafetyCertificateOutlined style={{ color: '#722ed1', marginRight: 8 }} />}
              suffix={<span style={{ fontSize: 13, color: '#52c41a' }}>Chuẩn hóa</span>}
              valueStyle={{ fontWeight: 700, color: '#531dab' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 3. Category Filter Tabs */}
      <Card size="small" variant="borderless" style={{ borderRadius: 10, paddingBottom: 0 }}>
        <Tabs
          activeKey={activeTabGroup}
          onChange={(key) => setActiveTabGroup(key)}
          items={tabItems}
          style={{ marginBottom: -8 }}
        />
      </Card>

      {/* 4. Main Two-Column Layout */}
      <Row gutter={[16, 16]}>
        {/* Left Column: Catalog Groups & List */}
        <Col xs={24} md={8}>
          <Card
            title={
              <Space>
                <span>Danh mục phân loại</span>
                <Badge
                  count={filteredCatalogs.length}
                  overflowCount={999}
                  style={{ backgroundColor: '#1677ff' }}
                />
              </Space>
            }
            size="small"
            className="catalog-sidebar-card"
            variant="borderless"
          >
            <Input
              placeholder="Tìm theo tên danh mục, mã chuẩn..."
              prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
              value={catalogSearch}
              onChange={(e) => setCatalogSearch(e.target.value)}
              allowClear
              style={{ marginBottom: 12, borderRadius: 6 }}
            />

            {catalogsQuery.isLoading ? (
              <TableSkeleton rows={8} columns={1} />
            ) : catalogsQuery.isError ? (
              <EmptyState
                title="Không thể tải danh mục"
                description={getApiErrorMessage(catalogsQuery.error)}
                actionText="Thử lại"
                onAction={() => void catalogsQuery.refetch()}
              />
            ) : filteredCatalogs.length === 0 ? (
              <EmptyState
                title={catalogSearch ? 'Không tìm thấy danh mục' : 'Chưa có danh mục trong nhóm'}
                description={
                  catalogSearch
                    ? 'Không có danh mục nào khớp với từ khóa tìm kiếm.'
                    : 'Nhóm danh mục này chưa có dữ liệu nào được đăng ký.'
                }
                actionText={catalogSearch ? 'Xóa từ khóa' : undefined}
                onAction={() => setCatalogSearch('')}
              />
            ) : (
              <List
                dataSource={filteredCatalogs}
                style={{ maxHeight: 600, overflowY: 'auto', paddingRight: 4 }}
                renderItem={(item) => {
                  const isSelected = selectedCatalog === item.catalogCode;
                  return (
                    <div
                      key={item.catalogCode}
                      className={`catalog-item-row ${isSelected ? 'active' : ''}`}
                      onClick={() => {
                        setSelectedCatalog(item.catalogCode);
                        setPage(0);
                        setItemKeyword('');
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', minWidth: 0, flex: 1 }}>
                        <div className="catalog-icon">
                          {renderCatalogIcon(item.groupKey, item.icon)}
                        </div>
                        <div style={{ minWidth: 0, flex: 1, paddingRight: 8 }}>
                          <div
                            style={{
                              fontWeight: isSelected ? 600 : 500,
                              color: isSelected ? '#1677ff' : '#262626',
                              fontSize: 13,
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                            }}
                          >
                            {item.catalogName}
                          </div>
                          <Tooltip title={`Mã hệ thống: ${item.catalogCode} — ${item.description || ''}`}>
                            <div
                              style={{
                                fontSize: 11,
                                color: '#8c8c8c',
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                              }}
                            >
                              {item.description ? item.description : item.catalogCode}
                            </div>
                          </Tooltip>
                        </div>
                      </div>

                      <Tag
                        color={isSelected ? 'blue' : 'default'}
                        style={{
                          borderRadius: 12,
                          fontSize: 11,
                          fontWeight: 600,
                          paddingInline: 8,
                          margin: 0,
                          flexShrink: 0,
                        }}
                      >
                        {item.itemCount.toLocaleString()} mục
                      </Tag>
                    </div>
                  );
                }}
              />
            )}
          </Card>
        </Col>

        {/* Right Column: Catalog Detail Table */}
        <Col xs={24} md={16}>
          <Card
            title={
              <Space direction="vertical" size={4} style={{ width: '100%', paddingBlock: 4 }}>
                <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
                  <Title level={5} style={{ margin: 0, color: '#002140', fontWeight: 600 }}>
                    {currentCatalog.catalogName}
                  </Title>
                  <Tag color="geekblue" style={{ borderRadius: 4 }}>
                    {currentCatalog.groupName || 'Danh mục chuẩn'}
                  </Tag>
                  <Tooltip title="Mã khóa kỹ thuật truy vấn dữ liệu">
                    <Tag color="default" style={{ borderRadius: 4, fontFamily: 'monospace' }}>
                      {currentCatalog.catalogCode}
                    </Tag>
                  </Tooltip>
                  {currentCatalog.isEditable ? (
                    <Tag color="success" style={{ borderRadius: 4 }}>Hiệu chỉnh được</Tag>
                  ) : (
                    <Tag color="blue" style={{ borderRadius: 4 }}>CSDL gốc</Tag>
                  )}
                </div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {currentCatalog.description || 'Dữ liệu danh mục tham chiếu chuẩn hóa ngành đường bộ'}
                </Text>
              </Space>
            }
            extra={
              <Space>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => void itemsQuery.refetch()}
                  loading={itemsQuery.isFetching}
                >
                  Làm mới
                </Button>
                {canEdit ? (
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={handleOpenCreateModal}
                  >
                    Thêm mục mới
                  </Button>
                ) : (
                  <Tooltip title="Chỉ Quản trị viên (ADMIN) hoặc Cán bộ quản lý (MANAGER) mới có quyền sửa đổi danh mục">
                    <Button disabled icon={<LockOutlined />}>
                      Thêm mục mới
                    </Button>
                  </Tooltip>
                )}
              </Space>
            }
            variant="borderless"
            className="catalog-detail-card"
          >
            {/* Filter toolbar inside catalog */}
            <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
              <Input
                placeholder="Tìm kiếm theo mã hoặc tên phần tử..."
                prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                value={itemKeyword}
                onChange={(e) => {
                  setItemKeyword(e.target.value);
                  setPage(0);
                }}
                allowClear
                style={{ maxWidth: 360, borderRadius: 6 }}
              />

              <Text type="secondary" style={{ fontSize: 12 }}>
                Đang hiển thị {itemsQuery.data?.totalElements || 0} bản ghi chuẩn
              </Text>
            </div>

            {/* Table */}
            {itemsQuery.isLoading ? (
              <TableSkeleton rows={8} columns={6} />
            ) : itemsQuery.isError ? (
              <EmptyState
                title="Không thể tải phần tử danh mục"
                description={getApiErrorMessage(itemsQuery.error)}
                actionText="Thử lại"
                onAction={() => void itemsQuery.refetch()}
              />
            ) : !itemsQuery.data?.content.length ? (
              <EmptyState
                title={itemKeyword ? 'Không tìm thấy phần tử phù hợp' : 'Danh mục chưa có bản ghi'}
                description={
                  itemKeyword
                    ? 'Không có mã hoặc tên phần tử nào khớp với từ khóa tìm kiếm.'
                    : 'Danh mục hiện tại chưa có phần tử dữ liệu. Bấm "Thêm mục mới" để khởi tạo bản ghi.'
                }
                actionText={itemKeyword ? 'Xóa từ khóa tìm kiếm' : canEdit ? 'Thêm mục mới ngay' : undefined}
                onAction={() => (itemKeyword ? setItemKeyword('') : handleOpenCreateModal())}
              />
            ) : (
              <Table
                dataSource={itemsQuery.data?.content || []}
                rowKey="itemCode"
                loading={itemsQuery.isLoading}
                bordered
                size="middle"
                pagination={{
                  current: page + 1,
                  pageSize,
                  total: itemsQuery.data?.totalElements || 0,
                  onChange: (p) => setPage(p - 1),
                  showTotal: (total) => `Tổng số ${total.toLocaleString()} phần tử`,
                  showSizeChanger: false,
                }}
                columns={[
                  {
                    title: 'Mã phần tử',
                    dataIndex: 'itemCode',
                    key: 'itemCode',
                    width: 140,
                    render: (t: string) => (
                      <Tag color="geekblue" style={{ fontWeight: 600, fontFamily: 'monospace' }}>
                        {t}
                      </Tag>
                    ),
                  },
                  {
                    title: 'Tên hiển thị chuẩn',
                    dataIndex: 'itemName',
                    key: 'itemName',
                    render: (t: string) => <strong style={{ color: '#002140' }}>{t}</strong>,
                  },
                  {
                    title: 'Mã cha',
                    dataIndex: 'parentCode',
                    key: 'parentCode',
                    width: 120,
                    render: (t: string) => (t ? <Tag style={{ borderRadius: 4 }}>{t}</Tag> : <Text type="secondary">—</Text>),
                  },
                  {
                    title: 'Thứ tự',
                    dataIndex: 'sortOrder',
                    key: 'sortOrder',
                    align: 'center',
                    width: 80,
                  },
                  {
                    title: 'Trạng thái',
                    dataIndex: 'active',
                    key: 'active',
                    align: 'center',
                    width: 120,
                    render: (v: boolean) =>
                      v !== false ? (
                        <Tag icon={<CheckCircleOutlined />} color="success" style={{ borderRadius: 10 }}>
                          Hiệu lực
                        </Tag>
                      ) : (
                        <Tag icon={<CloseCircleOutlined />} color="error" style={{ borderRadius: 10 }}>
                          Khóa
                        </Tag>
                      ),
                  },
                  {
                    title: 'Thuộc tính mở rộng',
                    key: 'extraAttributes',
                    width: 130,
                    align: 'center',
                    render: (_t, r) => {
                      const hasExtra = r.extraAttributes && Object.keys(r.extraAttributes).length > 0;
                      if (!hasExtra) return <Text type="secondary">—</Text>;
                      return (
                        <Popover
                          title="Thuộc tính JSON mở rộng"
                          content={
                            <pre
                              style={{
                                maxHeight: 200,
                                maxWidth: 300,
                                overflow: 'auto',
                                fontSize: 11,
                                background: '#f5f5f5',
                                padding: 8,
                                borderRadius: 4,
                                margin: 0,
                              }}
                            >
                              {JSON.stringify(r.extraAttributes, null, 2)}
                            </pre>
                          }
                          trigger="click"
                        >
                          <Button size="small" type="link" icon={<FileTextOutlined />}>
                            Chi tiết
                          </Button>
                        </Popover>
                      );
                    },
                  },
                  {
                    title: 'Thao tác',
                    key: 'actions',
                    align: 'center',
                    width: 100,
                    render: (_t, r) => (
                      <Space size="small">
                        {canEdit ? (
                          <>
                            <Tooltip title="Chỉnh sửa phần tử">
                              <Button
                                size="small"
                                type="text"
                                icon={<EditOutlined style={{ color: '#1677ff' }} />}
                                disabled={saveMutation.isPending || deleteMutation.isPending}
                                onClick={() => handleOpenEditModal(r)}
                              />
                            </Tooltip>
                            <Popconfirm
                              title="Xác nhận xóa phần tử danh mục này?"
                              description="Hành động này sẽ cập nhật trạng thái xóa khỏi danh mục chuẩn."
                              onConfirm={() => {
                                if (!deleteMutation.isPending) deleteMutation.mutate(r.itemCode);
                              }}
                              okText="Xóa"
                              cancelText="Hủy"
                              okButtonProps={{
                                danger: true,
                                loading: deleteMutation.isPending && deleteMutation.variables === r.itemCode,
                              }}
                            >
                              <Tooltip title="Xóa phần tử">
                                <Button
                                  size="small"
                                  type="text"
                                  danger
                                  icon={<DeleteOutlined />}
                                  loading={deleteMutation.isPending && deleteMutation.variables === r.itemCode}
                                  disabled={deleteMutation.isPending && deleteMutation.variables !== r.itemCode}
                                />
                              </Tooltip>
                            </Popconfirm>
                          </>
                        ) : (
                          <Tooltip title="Chỉ Quản trị viên mới có quyền sửa đổi">
                            <LockOutlined style={{ color: '#bfbfbf' }} />
                          </Tooltip>
                        )}
                      </Space>
                    ),
                  },
                ]}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* CREATE / EDIT MODAL */}
      <Modal
        title={
          <Space>
            {editingItem ? <EditOutlined style={{ color: '#1677ff' }} /> : <PlusOutlined style={{ color: '#52c41a' }} />}
            <span>
              {editingItem
                ? `Chỉnh sửa phần tử [${editingItem.itemCode}]`
                : `Thêm phần tử vào danh mục [${currentCatalog.catalogName}]`}
            </span>
          </Space>
        }
        open={isModalOpen}
        onOk={handleModalSubmit}
        onCancel={() => {
          if (!saveMutation.isPending) setIsModalOpen(false);
        }}
        confirmLoading={saveMutation.isPending}
        okText={editingItem ? 'Lưu thay đổi' : 'Thêm mới'}
        cancelText="Hủy"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" initialValues={{ active: true, sortOrder: 1 }}>
          <Form.Item
            name="itemCode"
            label="Mã phần tử (itemCode)"
            rules={[{ required: true, message: 'Vui lòng nhập mã phần tử' }]}
            help="Mã định danh duy nhất (VD: 01, W.205, CT, NH...)"
          >
            <Input disabled={Boolean(editingItem)} placeholder="VD: 01, W.205, CT..." />
          </Form.Item>

          <Form.Item
            name="itemName"
            label="Tên hiển thị chuẩn (itemName)"
            rules={[{ required: true, message: 'Vui lòng nhập tên phần tử' }]}
          >
            <Input placeholder="Nhập tên tiếng Việt hiển thị chuẩn..." />
          </Form.Item>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="parentCode" label="Mã phần tử cha (nếu có)">
                <Input placeholder="Mã nhóm phân cấp trên..." allowClear />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="sortOrder" label="Thứ tự hiển thị">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="active" label="Trạng thái hiệu lực" valuePropName="checked">
            <Switch checkedChildren="Hiệu lực" unCheckedChildren="Khóa" />
          </Form.Item>

          <Form.Item
            name="extraJson"
            label="Thuộc tính bổ sung (JSON format)"
            help="Định dạng JSON mở rộng dành cho quy chuẩn, màu sắc, tọa độ, thông số kỹ thuật..."
          >
            <Input.TextArea
              rows={3}
              placeholder='{"standard": "QCVN 41:2019", "group": "Cảnh báo nguy hiểm"}'
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default CatalogListPage;
