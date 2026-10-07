import React, { useState } from 'react';
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

export const CatalogListPage: React.FC = () => {
  const { hasRole } = useAuth();
  const canEdit = hasRole(['ADMIN', 'MANAGER']);
  const queryClient = useQueryClient();

  const [selectedCatalog, setSelectedCatalog] = useState<string>('c_tinhthanhpho');
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
    queryFn: () => fetchCatalogItems(selectedCatalog, { page, size: pageSize, keyword: itemKeyword }),
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

  // Filter catalogs for left list
  const catalogList = catalogsQuery.data || [];
  const filteredCatalogs = catalogList.filter(
    (c) =>
      c.catalogName.toLowerCase().includes(catalogSearch.toLowerCase()) ||
      c.catalogCode.toLowerCase().includes(catalogSearch.toLowerCase())
  );

  const currentCatalog = catalogList.find((c) => c.catalogCode === selectedCatalog) || {
    catalogCode: selectedCatalog,
    catalogName: selectedCatalog,
    description: '',
    itemCount: 0,
    sourceType: 'system',
    isEditable: true,
  };

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

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Space align="center" size={10}>
          <AppstoreOutlined style={{ fontSize: 24, color: '#003a8c' }} />
          <div>
            <Title level={4} style={{ margin: 0 }}>
              Danh mục Chuẩn Ngành Đường bộ
            </Title>
            <Paragraph type="secondary" style={{ margin: 0, fontSize: 13 }}>
              Hệ thống bảng mã danh mục tham chiếu dùng chung: Tỉnh/thành phố, cấp đường, loại mặt đường, quy chuẩn biển báo.
            </Paragraph>
          </div>
        </Space>
      </div>

      <Row gutter={[16, 16]}>
        {/* Left Column: Catalog Groups */}
        <Col xs={24} md={8}>
          <Card
            title={
              <Space>
                <span>Hệ thống danh mục</span>
                <Badge count={catalogList.length} overflowCount={999} style={{ backgroundColor: '#108ee9' }} />
              </Space>
            }
            size="small"
            className="kcht-card"
            variant="borderless"
          >
            <Input
              placeholder="Lọc danh mục..."
              prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
              value={catalogSearch}
              onChange={(e) => setCatalogSearch(e.target.value)}
              allowClear
              style={{ marginBottom: 12 }}
            />

            {catalogsQuery.isLoading ? (
              <TableSkeleton rows={8} columns={1} />
            ) : catalogsQuery.isError ? (
              <EmptyState
                title="Không thể tải hệ thống danh mục"
                description={getApiErrorMessage(catalogsQuery.error)}
                actionText="Thử lại"
                onAction={() => void catalogsQuery.refetch()}
              />
            ) : filteredCatalogs.length === 0 ? (
              <EmptyState
                title={catalogSearch ? 'Không tìm thấy danh mục phù hợp' : 'Chưa có danh mục tham chiếu'}
                description={catalogSearch ? 'Không có danh mục nào khớp với từ khóa đang nhập.' : 'API trả về danh sách danh mục rỗng.'}
                actionText={catalogSearch ? 'Xóa tìm kiếm' : 'Làm mới'}
                onAction={() => catalogSearch ? setCatalogSearch('') : void catalogsQuery.refetch()}
              />
            ) : (
              <List
                dataSource={filteredCatalogs}
                style={{ maxHeight: 620, overflowY: 'auto' }}
                renderItem={(item) => (
                <List.Item
                  onClick={() => {
                    setSelectedCatalog(item.catalogCode);
                    setPage(0);
                    setItemKeyword('');
                  }}
                  style={{
                    cursor: 'pointer',
                    background: selectedCatalog === item.catalogCode ? '#e6f4ff' : 'transparent',
                    borderLeft: selectedCatalog === item.catalogCode ? '3px solid #1677ff' : '3px solid transparent',
                    padding: '8px 12px',
                    borderRadius: 4,
                    marginBottom: 4,
                  }}
                >
                  <List.Item.Meta
                    title={
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontWeight: selectedCatalog === item.catalogCode ? 600 : 400 }}>
                          {item.catalogName}
                        </span>
                        <Tag color="blue">{item.itemCount}</Tag>
                      </div>
                    }
                    description={
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {item.catalogCode}
                      </Text>
                    }
                  />
                </List.Item>
                )}
              />
            )}
          </Card>
        </Col>

        {/* Right Column: Catalog Items Detail Table */}
        <Col xs={24} md={16}>
          <Card
            title={
              <Space direction="vertical" size={2}>
                <Space>
                  <Title level={5} style={{ margin: 0 }}>
                    {currentCatalog.catalogName}
                  </Title>
                  <Tag color="geekblue">{currentCatalog.catalogCode}</Tag>
                </Space>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {currentCatalog.description || 'Dữ liệu danh mục tham chiếu chuẩn hóa'}
                </Text>
              </Space>
            }
            extra={
              <Space>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => itemsQuery.refetch()}
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
                  <Tooltip title="Chỉ Quản trị viên (ADMIN) hoặc Quản lý (MANAGER) mới có quyền sửa đổi danh mục">
                    <Button disabled icon={<LockOutlined />}>
                      Thêm mục mới
                    </Button>
                  </Tooltip>
                )}
              </Space>
            }
            variant="borderless"
            className="kcht-card"
          >
            {/* Filter toolbar */}
            <div style={{ marginBottom: 16 }}>
              <Input
                placeholder="Tìm kiếm theo mã hoặc tên phần tử..."
                prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                value={itemKeyword}
                onChange={(e) => {
                  setItemKeyword(e.target.value);
                  setPage(0);
                }}
                allowClear
                style={{ maxWidth: 350 }}
              />
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
                title={itemKeyword ? 'Không tìm thấy phần tử phù hợp' : 'Danh mục chưa có phần tử'}
                description={itemKeyword ? 'Không có mã hoặc tên phần tử nào khớp với từ khóa.' : 'Danh mục đang chọn chưa có dữ liệu.'}
                actionText={itemKeyword ? 'Xóa tìm kiếm' : 'Làm mới'}
                onAction={() => itemKeyword ? setItemKeyword('') : void itemsQuery.refetch()}
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
                showTotal: (total) => `Tổng số ${total} phần tử`,
              }}
              columns={[
                {
                  title: 'Mã phần tử',
                  dataIndex: 'itemCode',
                  key: 'itemCode',
                  width: 140,
                  render: (t: string) => <Tag color="geekblue" style={{ fontWeight: 600 }}>{t}</Tag>,
                },
                {
                  title: 'Tên hiển thị',
                  dataIndex: 'itemName',
                  key: 'itemName',
                  render: (t: string) => <strong>{t}</strong>,
                },
                {
                  title: 'Mã cha',
                  dataIndex: 'parentCode',
                  key: 'parentCode',
                  width: 110,
                  render: (t: string) => (t ? <Tag>{t}</Tag> : <Text type="secondary">—</Text>),
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
                  width: 110,
                  render: (v: boolean) =>
                    v !== false ? (
                      <Tag icon={<CheckCircleOutlined />} color="success">Hiệu lực</Tag>
                    ) : (
                      <Tag icon={<CloseCircleOutlined />} color="error">Hết hiệu lực</Tag>
                    ),
                },
                {
                  title: 'Thao tác',
                  key: 'actions',
                  align: 'center',
                  width: 120,
                  render: (_t, r) => (
                    <Space size="small">
                      {canEdit ? (
                        <>
                          <Button
                            size="small"
                            type="text"
                            icon={<EditOutlined style={{ color: '#1677ff' }} />}
                            disabled={saveMutation.isPending || deleteMutation.isPending}
                            onClick={() => handleOpenEditModal(r)}
                          />
                          <Popconfirm
                            title="Xác nhận xóa phần tử danh mục này?"
                            onConfirm={() => {
                              if (!deleteMutation.isPending) deleteMutation.mutate(r.itemCode);
                            }}
                            okText="Xóa"
                            cancelText="Hủy"
                            okButtonProps={{ danger: true, loading: deleteMutation.isPending && deleteMutation.variables === r.itemCode }}
                          >
                            <Button
                              size="small"
                              type="text"
                              danger
                              icon={<DeleteOutlined />}
                              loading={deleteMutation.isPending && deleteMutation.variables === r.itemCode}
                              disabled={deleteMutation.isPending && deleteMutation.variables !== r.itemCode}
                            />
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
        title={editingItem ? `Chỉnh sửa phần tử [${editingItem.itemCode}]` : `Thêm phần tử vào danh mục [${selectedCatalog}]`}
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
          >
            <Input disabled={Boolean(editingItem)} placeholder="VD: 01, W.205, CT..." />
          </Form.Item>

          <Form.Item
            name="itemName"
            label="Tên hiển thị (itemName)"
            rules={[{ required: true, message: 'Vui lòng nhập tên phần tử' }]}
          >
            <Input placeholder="Tên tiếng Việt hiển thị..." />
          </Form.Item>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="parentCode" label="Mã phần tử cha (nếu có)">
                <Input placeholder="Mã nhóm cấp trên..." allowClear />
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

          <Form.Item name="extraJson" label="Thuộc tính bổ sung (JSON format)">
            <Input.TextArea
              rows={3}
              placeholder='{"color": "red", "shape": "circle", "standard": "QCVN 41:2019"}'
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
export default CatalogListPage;
