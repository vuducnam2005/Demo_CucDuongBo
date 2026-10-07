import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App } from 'antd';
import { AuthProvider } from '../context/AuthContext';
import { AssetListPage } from '../pages/AssetListPage';
import * as api from '../services/api';
import * as authApi from '../services/authApi';

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>();
  return {
    ...actual,
    fetchDatasetTree: vi.fn(),
    fetchDatasetMetadata: vi.fn(),
    fetchDatasetRecords: vi.fn(),
    exportDatasetRecords: vi.fn(),
    createAssetRecord: vi.fn(),
    updateAssetRecord: vi.fn(),
    deleteAssetRecord: vi.fn(),
    previewImportDataset: vi.fn(),
    executeImportDataset: vi.fn(),
  };
});

vi.mock('../services/authApi', () => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
  getMeApi: vi.fn(),
}));

describe('Phase 11 CRUD & Administration Frontend Integration Tests', () => {
  let queryClient: QueryClient;

  const mockAdminUser = {
    id: 1,
    username: 'admin',
    email: 'admin@drvn.gov.vn',
    fullName: 'Quản trị viên Hệ thống',
    role: 'ROLE_ADMIN',
    roleName: 'Quản trị hệ thống',
    active: true,
    permissions: ['*'],
  };

  const mockViewerUser = {
    id: 4,
    username: 'viewer',
    email: 'viewer@drvn.gov.vn',
    fullName: 'Cán bộ Tra cứu',
    role: 'ROLE_VIEWER',
    roleName: 'Cán bộ Tra cứu',
    active: true,
    permissions: ['ASSET:READ', 'ASSET:EXPORT'],
  };

  const mockTreeNodes: api.DatasetTreeNode[] = [
    {
      key: 'group_core',
      title: 'Hạ tầng Đường bộ Trọng yếu',
      isLeaf: false,
      children: [
        {
          key: 'tbl_road_sign',
          title: 'Biển báo hiệu đường bộ',
          datasetKey: 'tbl_road_sign',
          totalRecords: 222112,
          kind: 'asset',
          isLeaf: true,
        },
      ],
    },
  ];

  const mockMetadata: api.DatasetMetadata = {
    datasetKey: 'tbl_road_sign',
    datasetName: 'Biển báo hiệu đường bộ',
    kind: 'asset',
    endpoint: '/api/datasets/tbl_road_sign',
    sourceFile: 'tbl_road_sign.json',
    totalRecords: 222112,
    geometryType: 'POINT',
    fields: [
      { fieldName: 'name', displayName: 'Tên biển', dataType: 'VARCHAR', searchable: true, filterable: true },
      { fieldName: 'route_code', displayName: 'Tuyến', dataType: 'VARCHAR', searchable: true, filterable: true },
      { fieldName: 'lytrinh', displayName: 'Lý trình', dataType: 'VARCHAR', searchable: true, filterable: false },
    ],
  };

  const mockRecords: api.PagedResponse<api.RecordItem> = {
    content: [
      {
        id: 101,
        datasetKey: 'tbl_road_sign',
        recordKey: 'sign_101',
        recordStatus: 'Đang khai thác',
        createdAt: '2026-10-01T08:00:00Z',
        updatedAt: '2026-10-01T08:00:00Z',
        payload: {
          id: 'sign_101',
          name: 'Biển P.102 Cấm đi ngược chiều',
          route_code: 'QL.1',
          branch_id: 'kqldb_1',
          state_name: 'Đã duyệt',
          version: 1,
        },
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  };

  beforeEach(() => {
    localStorage.clear();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, staleTime: 0 },
      },
    });
    vi.clearAllMocks();

    vi.mocked(api.fetchDatasetTree).mockResolvedValue(mockTreeNodes);
    vi.mocked(api.fetchDatasetMetadata).mockResolvedValue(mockMetadata);
    vi.mocked(api.fetchDatasetRecords).mockResolvedValue(mockRecords);
  });

  const renderWithRole = (currentUser = mockAdminUser, initialRoute = '/assets?datasetKey=tbl_road_sign') => {
    vi.mocked(authApi.getMeApi).mockResolvedValue(currentUser);
    localStorage.setItem('kcht_access_token', 'valid_token');
    localStorage.setItem('kcht_user_profile', JSON.stringify(currentUser));
    return render(
      <QueryClientProvider client={queryClient}>
        <ConfigProvider theme={{ hashed: false }}>
          <App>
            <AuthProvider>
              <MemoryRouter initialEntries={[initialRoute]}>
                <AssetListPage />
              </MemoryRouter>
            </AuthProvider>
          </App>
        </ConfigProvider>
      </QueryClientProvider>
    );
  };

  it('1. Renders "Thêm mới" and "Nhập dữ liệu" buttons for ROLE_ADMIN', async () => {
    renderWithRole(mockAdminUser);

    await waitFor(() => {
      expect(screen.getByText('Biển báo hiệu đường bộ')).toBeInTheDocument();
    });

    // Check presence of admin action buttons
    expect(screen.getByRole('button', { name: /Thêm mới/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Nhập dữ liệu/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Xuất file CSV/i })).toBeInTheDocument();
  });

  it('2. Hides "Thêm mới", "Nhập dữ liệu", and row Edit/Delete buttons for ROLE_VIEWER (read-only)', async () => {
    renderWithRole(mockViewerUser);

    await waitFor(() => {
      expect(screen.getByText('Biển báo hiệu đường bộ')).toBeInTheDocument();
    });

    // Viewer must NOT see create or import buttons
    expect(screen.queryByRole('button', { name: /Thêm mới/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Nhập dữ liệu/i })).not.toBeInTheDocument();

    // Viewer can only see Chi tiết, not Sửa or Xóa
    expect(screen.getByRole('button', { name: /Chi tiết/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Sửa$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Xóa$/ })).not.toBeInTheDocument();
  });

  it('3. Opens AssetFormModal when "Thêm mới" button is clicked by ROLE_ADMIN', async () => {
    renderWithRole(mockAdminUser);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Thêm mới/i })).toBeInTheDocument();
    });

    const createBtn = screen.getByRole('button', { name: /Thêm mới/i });
    fireEvent.click(createBtn);

    // Modal title should appear
    await waitFor(() => {
      expect(screen.getByText(/Thêm mới Bản ghi Tài sản/i)).toBeInTheDocument();
      expect(screen.getByText(/Bảo vệ Dữ liệu Hệ thống & Hình học Không gian/i)).toBeInTheDocument();
    });
  });

  it('4. Opens AssetImportModal when "Nhập dữ liệu" is clicked and executes dry-run preview', async () => {
    vi.mocked(api.previewImportDataset).mockResolvedValue({
      datasetCode: 'tbl_road_sign',
      totalRows: 5,
      validRows: 5,
      errorRows: 0,
      previewRows: [{ name: 'Biển P.102', route_code: 'QL.1' }],
      errors: [],
      canProceed: true,
    });

    renderWithRole(mockAdminUser);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Nhập dữ liệu/i })).toBeInTheDocument();
    });

    const importBtn = screen.getByRole('button', { name: /Nhập dữ liệu/i });
    fireEvent.click(importBtn);

    await waitFor(() => {
      expect(screen.getByText(/Nhập Dữ liệu CSV \/ JSON cho/i)).toBeInTheDocument();
    });

    // Click "Chèn dữ liệu mẫu"
    const sampleBtn = screen.getByRole('button', { name: /Chèn dữ liệu mẫu/i });
    fireEvent.click(sampleBtn);

    // Click "Kiểm tra & Xem trước"
    const previewBtn = screen.getByRole('button', { name: /Kiểm tra & Xem trước/i });
    fireEvent.click(previewBtn);

    await waitFor(() => {
      expect(api.previewImportDataset).toHaveBeenCalledWith('tbl_road_sign', expect.any(String), 'CSV');
      expect(screen.getByText(/Dòng hợp lệ:/i)).toBeInTheDocument();
    });
  });
});
