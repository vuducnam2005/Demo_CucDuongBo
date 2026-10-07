import React, { useState } from 'react';
import {
  Card,
  Typography,
  Space,
  Row,
  Col,
  List,
  Tag,
  Button,
  Table,
  Input,
  Select,
  Modal,
  Upload,
  message,
  Popconfirm,
  Tooltip,
  Badge,
} from 'antd';
import {
  FolderOpenOutlined,
  FolderFilled,
  DownloadOutlined,
  CloudUploadOutlined,
  PlusOutlined,
  DeleteOutlined,
  SearchOutlined,
  ReloadOutlined,
  FilePdfOutlined,
  FileExcelOutlined,
  FileWordOutlined,
  FileImageOutlined,
  FileZipOutlined,
  FileTextOutlined,
  InboxOutlined,
  LockOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchDocumentFolders,
  createDocumentFolder,
  fetchDocuments,
  downloadDocumentFile,
  uploadDocumentFile,
  deleteDocumentFile,
  DocumentFolder,
  DocumentItem,
} from '../services/documentApi';
import { getApiErrorMessage, shouldRetryQuery } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { EmptyState, TableSkeleton } from '../components/common';

const { Title, Paragraph, Text } = Typography;
const { Dragger } = Upload;

export const DocumentExplorerPage: React.FC = () => {
  const { hasRole, hasPermission } = useAuth();
  const canUpload = hasRole(['ROLE_EDITOR', 'ROLE_MANAGER', 'ROLE_ADMIN']) || hasPermission('DOCUMENT', 'create');
  const canManage = hasRole(['ROLE_MANAGER', 'ROLE_ADMIN']) || hasPermission('DOCUMENT', 'delete');
  const queryClient = useQueryClient();

  const [selectedFolderId, setSelectedFolderId] = useState<string>('');
  const [searchKeyword, setSearchKeyword] = useState<string>('');
  const [extensionFilter, setExtensionFilter] = useState<string>('');
  const [page, setPage] = useState<number>(0);
  const pageSize = 15;

  // Folder modal
  const [isFolderModalOpen, setIsFolderModalOpen] = useState(false);
  const [folderNameInput, setFolderNameInput] = useState('');

  // Upload modal
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadFolderId, setUploadFolderId] = useState<string>('');
  const [uploadAssetId, setUploadAssetId] = useState<string>('');
  const [uploadBranchId, setUploadBranchId] = useState<string>('');
  const [isUploading, setIsUploading] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  // Queries
  const foldersQuery = useQuery<DocumentFolder[]>({
    queryKey: ['documentFolders'],
    queryFn: fetchDocumentFolders,
    retry: shouldRetryQuery,
  });

  const documentsQuery = useQuery({
    queryKey: ['documents', selectedFolderId, searchKeyword, extensionFilter, page],
    queryFn: () =>
      fetchDocuments({
        folderId: selectedFolderId || undefined,
        search: searchKeyword || undefined,
        extension: extensionFilter || undefined,
        page,
        size: pageSize,
      }),
    retry: shouldRetryQuery,
  });

  // Mutations
  const createFolderMutation = useMutation({
    mutationFn: (name: string) => createDocumentFolder(name, selectedFolderId || undefined),
    onSuccess: (newFolder) => {
      message.success(`Đã tạo thư mục "${newFolder.folderName}" thành công`);
      setIsFolderModalOpen(false);
      setFolderNameInput('');
      void queryClient.invalidateQueries({ queryKey: ['documentFolders'] });
    },
    onError: (err: unknown) => {
      message.error(getApiErrorMessage(err));
    },
  });

  const deleteDocMutation = useMutation({
    mutationFn: (id: string) => deleteDocumentFile(id),
    onSuccess: () => {
      message.success('Đã xóa tài liệu khỏi hệ thống');
      void queryClient.invalidateQueries({ queryKey: ['documents'] });
      void queryClient.invalidateQueries({ queryKey: ['documentFolders'] });
    },
    onError: (err: unknown) => {
      message.error(getApiErrorMessage(err));
    },
  });

  const handleDownload = async (doc: DocumentItem) => {
    const documentId = doc.fileEntryId || doc.id;
    if (downloadingId) return;
    try {
      setDownloadingId(documentId);
      message.loading({ content: 'Đang tải tệp tin...', key: 'dl' });
      await downloadDocumentFile(documentId, doc.fileName);
      message.success({ content: 'Tải tệp tin thành công', key: 'dl' });
    } catch (error: unknown) {
      message.error({ content: getApiErrorMessage(error), key: 'dl' });
    } finally {
      setDownloadingId(null);
    }
  };

  const handleUploadSubmit = async () => {
    if (!selectedFile) {
      message.warning('Vui lòng chọn tệp tin cần tải lên');
      return;
    }

    try {
      setIsUploading(true);
      await uploadDocumentFile(
        selectedFile,
        uploadFolderId || selectedFolderId || undefined,
        uploadAssetId || undefined,
        uploadBranchId || undefined
      );
      message.success(`Tải lên tệp "${selectedFile.name}" thành công`);
      setIsUploadModalOpen(false);
      setSelectedFile(null);
      setUploadAssetId('');
      void queryClient.invalidateQueries({ queryKey: ['documents'] });
      void queryClient.invalidateQueries({ queryKey: ['documentFolders'] });
    } catch (err: unknown) {
      message.error(getApiErrorMessage(err));
    } finally {
      setIsUploading(false);
    }
  };

  const getFileIcon = (ext: string) => {
    const e = ext.toLowerCase();
    if (e === 'pdf') return <FilePdfOutlined style={{ color: '#ff4d4f', fontSize: 18 }} />;
    if (e.includes('xls')) return <FileExcelOutlined style={{ color: '#52c41a', fontSize: 18 }} />;
    if (e.includes('doc')) return <FileWordOutlined style={{ color: '#1677ff', fontSize: 18 }} />;
    if (['jpg', 'jpeg', 'png', 'svg'].includes(e)) return <FileImageOutlined style={{ color: '#fa8c16', fontSize: 18 }} />;
    if (['zip', 'rar', '7z'].includes(e)) return <FileZipOutlined style={{ color: '#722ed1', fontSize: 18 }} />;
    return <FileTextOutlined style={{ color: '#8c8c8c', fontSize: 18 }} />;
  };

  const formatFileSize = (bytes?: number | null) => {
    if (!bytes || bytes <= 0) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const folders = foldersQuery.data || [];
  const selectedFolder = folders.find((f) => f.id === selectedFolderId || f.folderCode === selectedFolderId);

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Space align="center" size={10}>
          <FolderOpenOutlined style={{ fontSize: 24, color: '#003a8c' }} />
          <div>
            <Title level={4} style={{ margin: 0 }}>
              Quản lý Hồ sơ & Tài liệu Kỹ thuật KCHT
            </Title>
            <Paragraph type="secondary" style={{ margin: 0, fontSize: 13 }}>
              Hệ thống lưu trữ phân cấp tệp hồ sơ công trình trên kho đối tượng MinIO / S3 (`kcht-documents`).
            </Paragraph>
          </div>
        </Space>
      </div>

      <Row gutter={[16, 16]}>
        {/* Left: Folder Hierarchy */}
        <Col xs={24} md={8}>
          <Card
            title={
              <Space>
                <span>Cây thư mục tài liệu</span>
                <Badge count={folders.length} overflowCount={999} style={{ backgroundColor: '#52c41a' }} />
              </Space>
            }
            extra={
              canManage ? (
                <Button
                  size="small"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setIsFolderModalOpen(true)}
                >
                  Tạo thư mục
                </Button>
              ) : null
            }
            variant="borderless"
            className="kcht-card"
            size="small"
          >
            {/* All Files Option */}
            <div
              onClick={() => {
                setSelectedFolderId('');
                setPage(0);
              }}
              style={{
                cursor: 'pointer',
                background: selectedFolderId === '' ? '#e6f4ff' : 'transparent',
                borderLeft: selectedFolderId === '' ? '3px solid #1677ff' : '3px solid transparent',
                padding: '8px 12px',
                borderRadius: 4,
                marginBottom: 6,
                fontWeight: selectedFolderId === '' ? 600 : 400,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <Space>
                <FolderFilled style={{ color: selectedFolderId === '' ? '#1677ff' : '#faad14', fontSize: 16 }} />
                <span>Tất cả tài liệu hệ thống</span>
              </Space>
              <Tag color="blue">{documentsQuery.data?.totalElements || 0}</Tag>
            </div>

            {foldersQuery.isLoading ? (
              <TableSkeleton rows={7} columns={1} />
            ) : foldersQuery.isError ? (
              <EmptyState
                title="Không thể tải cây thư mục"
                description={getApiErrorMessage(foldersQuery.error)}
                actionText="Thử lại"
                onAction={() => void foldersQuery.refetch()}
              />
            ) : folders.length === 0 ? (
              <EmptyState
                title="Chưa có thư mục tài liệu"
                description="Hệ thống chưa có thư mục hồ sơ nào để hiển thị."
                actionText="Làm mới"
                onAction={() => void foldersQuery.refetch()}
              />
            ) : (
              <List
                dataSource={folders}
                style={{ maxHeight: 620, overflowY: 'auto' }}
                renderItem={(folder) => {
                const isSelected = selectedFolderId === folder.id || selectedFolderId === folder.folderCode;
                return (
                  <List.Item
                    onClick={() => {
                      setSelectedFolderId(folder.folderCode || folder.id);
                      setPage(0);
                    }}
                    style={{
                      cursor: 'pointer',
                      background: isSelected ? '#e6f4ff' : 'transparent',
                      borderLeft: isSelected ? '3px solid #1677ff' : '3px solid transparent',
                      padding: '8px 12px',
                      borderRadius: 4,
                      marginBottom: 4,
                    }}
                  >
                    <Space direction="horizontal" size={8}>
                      <FolderFilled style={{ color: isSelected ? '#1677ff' : '#faad14', fontSize: 16 }} />
                      <span style={{ fontWeight: isSelected ? 600 : 400 }}>{folder.folderName}</span>
                    </Space>
                    <Tag color={folder.documentCount > 0 ? 'blue' : 'default'}>{folder.documentCount}</Tag>
                  </List.Item>
                );
                }}
              />
            )}
          </Card>
        </Col>

        {/* Right: Files in Selected Folder */}
        <Col xs={24} md={16}>
          <Card
            title={
              <Space direction="vertical" size={2}>
                <Space>
                  <Title level={5} style={{ margin: 0 }}>
                    {selectedFolder ? selectedFolder.folderName : 'Tất cả tài liệu'}
                  </Title>
                  {selectedFolder && <Tag color="geekblue">{selectedFolder.folderCode}</Tag>}
                </Space>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Kho lưu trữ đối tượng MinIO / S3 • Phân quyền RBAC bảo mật
                </Text>
              </Space>
            }
            extra={
              <Space>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => documentsQuery.refetch()}
                  loading={documentsQuery.isFetching}
                >
                  Làm mới
                </Button>
                {canUpload ? (
                  <Button
                    type="primary"
                    icon={<CloudUploadOutlined />}
                    onClick={() => {
                      setUploadFolderId(selectedFolderId);
                      setIsUploadModalOpen(true);
                    }}
                  >
                    Tải lên tài liệu
                  </Button>
                ) : (
                  <Tooltip title="Tài khoản cần quyền DOCUMENT:CREATE để tải tệp lên">
                    <Button disabled icon={<LockOutlined />}>
                      Tải lên tài liệu
                    </Button>
                  </Tooltip>
                )}
              </Space>
            }
            variant="borderless"
            className="kcht-card"
          >
            {/* Search and Filter toolbar */}
            <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
              <Col xs={24} sm={14}>
                <Input
                  placeholder="Tìm kiếm tài liệu theo tên, công trình, người nạp..."
                  prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
                  value={searchKeyword}
                  onChange={(e) => {
                    setSearchKeyword(e.target.value);
                    setPage(0);
                  }}
                  allowClear
                />
              </Col>
              <Col xs={24} sm={10}>
                <Select
                  style={{ width: '100%' }}
                  placeholder="Định dạng tệp"
                  value={extensionFilter}
                  onChange={(v) => {
                    setExtensionFilter(v);
                    setPage(0);
                  }}
                  options={[
                    { label: 'Tất cả định dạng', value: '' },
                    { label: 'Tệp PDF (.pdf)', value: 'pdf' },
                    { label: 'Bản vẽ kỹ thuật (.dwg)', value: 'dwg' },
                    { label: 'Tệp bảng tính (.xlsx, .xls)', value: 'xlsx' },
                    { label: 'Văn bản Word (.docx, .doc)', value: 'docx' },
                    { label: 'Hình ảnh (.jpg, .png)', value: 'png' },
                  ]}
                />
              </Col>
            </Row>

            {/* Table */}
            {documentsQuery.isLoading ? (
              <TableSkeleton rows={8} columns={5} />
            ) : documentsQuery.isError ? (
              <EmptyState
                title="Không thể tải danh sách tài liệu"
                description={getApiErrorMessage(documentsQuery.error)}
                actionText="Thử lại"
                onAction={() => void documentsQuery.refetch()}
              />
            ) : !documentsQuery.data?.content.length ? (
              <EmptyState
                title={searchKeyword || extensionFilter ? 'Không tìm thấy tài liệu phù hợp' : 'Thư mục chưa có tài liệu'}
                description={
                  searchKeyword || extensionFilter
                    ? 'Không có tài liệu nào khớp với bộ lọc hiện tại.'
                    : 'Thư mục đang chọn chưa có tài liệu nào được đính kèm.'
                }
                actionText={searchKeyword || extensionFilter ? 'Xóa bộ lọc' : 'Làm mới'}
                onAction={() => {
                  if (searchKeyword || extensionFilter) {
                    setSearchKeyword('');
                    setExtensionFilter('');
                    setPage(0);
                  } else {
                    void documentsQuery.refetch();
                  }
                }}
              />
            ) : (
              <Table
              dataSource={documentsQuery.data?.content || []}
              rowKey="id"
              loading={documentsQuery.isLoading}
              bordered
              size="middle"
              pagination={{
                current: page + 1,
                pageSize,
                total: documentsQuery.data?.totalElements || 0,
                onChange: (p) => setPage(p - 1),
                showTotal: (total) => `Tổng số ${total} hồ sơ tài liệu`,
              }}
              columns={[
                {
                  title: 'Tên tệp hồ sơ',
                  dataIndex: 'fileName',
                  key: 'fileName',
                  render: (name: string, r) => (
                    <Space align="center" size={8}>
                      {getFileIcon(r.fileExtension || '')}
                      <Space direction="vertical" size={0}>
                        <Text strong style={{ fontSize: 13 }}>{name}</Text>
                        <Text type="secondary" style={{ fontSize: 11 }}>
                          {r.objectName ? `Liên kết: ${r.objectName}` : (r.groupName || 'Hồ sơ chung')}
                        </Text>
                      </Space>
                    </Space>
                  ),
                },
                {
                  title: 'Định dạng',
                  dataIndex: 'fileExtension',
                  key: 'fileExtension',
                  width: 100,
                  align: 'center',
                  render: (ext: string) => (
                    <Tag color="geekblue" style={{ textTransform: 'uppercase', fontWeight: 600 }}>
                      {ext || 'FILE'}
                    </Tag>
                  ),
                },
                {
                  title: 'Kích thước',
                  dataIndex: 'fileSize',
                  key: 'fileSize',
                  width: 100,
                  align: 'right',
                  render: (sz: number) => formatFileSize(sz),
                },
                {
                  title: 'Người tạo',
                  dataIndex: 'uploader',
                  key: 'uploader',
                  width: 130,
                  render: (u: string) => u || 'Hệ thống',
                },
                {
                  title: 'Thao tác',
                  key: 'actions',
                  width: 110,
                  align: 'center',
                  render: (_t, r) => (
                    <Space size="small">
                      <Tooltip title="Tải xuống tệp tin">
                        <Button
                          size="small"
                          type="primary"
                          ghost
                          icon={<DownloadOutlined />}
                          loading={downloadingId === (r.fileEntryId || r.id)}
                          disabled={downloadingId !== null && downloadingId !== (r.fileEntryId || r.id)}
                          onClick={() => handleDownload(r)}
                        />
                      </Tooltip>
                      {canManage && (
                        <Popconfirm
                          title="Xác nhận xóa tài liệu này?"
                          description="Tệp tin sẽ bị xóa khỏi kho lưu trữ và CSDL."
                          onConfirm={() => {
                            if (!deleteDocMutation.isPending) deleteDocMutation.mutate(r.fileEntryId || r.id);
                          }}
                          okText="Xóa"
                          cancelText="Hủy"
                          okButtonProps={{
                            danger: true,
                            loading: deleteDocMutation.isPending && deleteDocMutation.variables === (r.fileEntryId || r.id),
                          }}
                        >
                          <Button
                            size="small"
                            type="text"
                            danger
                            icon={<DeleteOutlined />}
                            loading={deleteDocMutation.isPending && deleteDocMutation.variables === (r.fileEntryId || r.id)}
                            disabled={deleteDocMutation.isPending && deleteDocMutation.variables !== (r.fileEntryId || r.id)}
                          />
                        </Popconfirm>
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

      {/* CREATE FOLDER MODAL */}
      <Modal
        title="Tạo thư mục tài liệu mới"
        open={isFolderModalOpen}
        onOk={() => {
          if (!folderNameInput.trim()) {
            message.warning('Vui lòng nhập tên thư mục');
            return;
          }
          createFolderMutation.mutate(folderNameInput.trim());
        }}
        onCancel={() => {
          if (!createFolderMutation.isPending) setIsFolderModalOpen(false);
        }}
        confirmLoading={createFolderMutation.isPending}
        okText="Tạo thư mục"
        cancelText="Hủy"
      >
        <div style={{ marginTop: 12 }}>
          <div style={{ fontSize: 13, marginBottom: 6, fontWeight: 500 }}>Tên thư mục:</div>
          <Input
            placeholder="VD: Hồ sơ hoàn công dự án 2026..."
            value={folderNameInput}
            onChange={(e) => setFolderNameInput(e.target.value)}
          />
        </div>
      </Modal>

      {/* UPLOAD DOCUMENT MODAL */}
      <Modal
        title="Tải lên tài liệu kỹ thuật mới (Tối đa 25MB)"
        open={isUploadModalOpen}
        onOk={() => {
          if (!isUploading) void handleUploadSubmit();
        }}
        onCancel={() => {
          if (!isUploading) {
            setIsUploadModalOpen(false);
            setSelectedFile(null);
          }
        }}
        confirmLoading={isUploading}
        okText="Tải lên hệ thống"
        cancelText="Hủy"
        maskClosable={!isUploading}
        destroyOnHidden
      >
        <div style={{ marginTop: 12 }}>
          <Dragger
            maxCount={1}
            beforeUpload={(file) => {
              if (file.size > 25 * 1024 * 1024) {
                message.error('Kích thước tệp vượt quá 25MB!');
                return Upload.LIST_IGNORE;
              }
              setSelectedFile(file);
              return false; // Prevent auto-upload
            }}
            onRemove={() => setSelectedFile(null)}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined style={{ color: '#1677ff', fontSize: 42 }} />
            </p>
            <p className="ant-upload-text">Nhấp hoặc kéo thả tệp vào khu vực này để tải lên</p>
            <p className="ant-upload-hint">
              Hỗ trợ tệp PDF, Word, Excel, CAD (DWG), JPG, PNG, ZIP. Dung lượng tối đa 25MB.
            </p>
          </Dragger>

          <div style={{ marginTop: 16 }}>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Thư mục lưu trữ:</div>
            <Select
              style={{ width: '100%' }}
              value={uploadFolderId}
              onChange={setUploadFolderId}
              options={[
                { label: 'Hồ sơ chung (Mặc định)', value: '' },
                ...folders.map((f) => ({ label: f.folderName, value: f.folderCode || f.id })),
              ]}
            />
          </div>

          <div style={{ marginTop: 12 }}>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Mã công trình / Tài sản liên kết:</div>
            <Input
              placeholder="VD: bridge_01, QL.1-Km120..."
              value={uploadAssetId}
              onChange={(e) => setUploadAssetId(e.target.value)}
            />
          </div>

          <div style={{ marginTop: 12 }}>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Khu vực quản lý:</div>
            <Select
              style={{ width: '100%' }}
              value={uploadBranchId}
              onChange={setUploadBranchId}
              options={[
                { label: 'Cục Đường bộ Việt Nam', value: 'cdb_vn' },
                { label: 'Khu QLĐB I', value: 'kqldb_1' },
                { label: 'Khu QLĐB II', value: 'kqldb_2' },
                { label: 'Khu QLĐB III', value: 'kqldb_3' },
                { label: 'Khu QLĐB IV', value: 'kqldb_4' },
              ]}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
};
export default DocumentExplorerPage;
