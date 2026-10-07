import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App } from 'antd';
import { AuthProvider } from '../context/AuthContext';
import { AssetListPage } from '../pages/AssetListPage';
import * as api from '../services/api';
import * as authApi from '../services/authApi';

vi.mock('../services/authApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/authApi')>();
  return {
    ...actual,
    getMeApi: vi.fn(),
  };
});

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>();
  return {
    ...actual,
    fetchDatasetTree: vi.fn(),
    fetchDatasetMetadata: vi.fn(),
    fetchDatasetRecords: vi.fn(),
    exportDatasetRecords: vi.fn(),
    deleteAssetRecord: vi.fn(),
  };
});

describe('AssetListPage Unit & Integration Tests', () => {
  let queryClient: QueryClient;

  const setDesktopViewport = () => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: query.includes('min-width'),
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }),
    });
  };

  const setMobileViewport = () => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }),
    });
  };

  const mockAdminUser = {
    id: 1,
    username: 'admin',
    email: 'admin@drvn.gov.vn',
    fullName: 'Quản trị viên Hệ thống',
    role: 'ROLE_ADMIN',
    roleName: 'Quản trị hệ thống',
    permissions: ['*'],
  };

  const mockViewerUser = {
    id: 4,
    username: 'viewer',
    email: 'viewer@drvn.gov.vn',
    fullName: 'Cán bộ Tra cứu',
    role: 'ROLE_VIEWER',
    roleName: 'Cán bộ Tra cứu',
    permissions: ['READ_ALL'],
  };

  const mockTreeNodes: api.DatasetTreeNode[] = [
    {
      key: 'group_core',
      title: 'Hạ tầng Đường bộ Trọng yếu (5 danh mục)',
      isLeaf: false,
      children: [
        {
          key: 'tbl_road_sign',
          title: 'Biển báo hiệu đường bộ (222,112)',
          datasetKey: 'tbl_road_sign',
          totalRecords: 222112,
          kind: 'physical_asset',
          icon: 'AlertOutlined',
          isLeaf: true,
        },
        {
          key: 'mst_national_road',
          title: 'Mạng lưới Quốc lộ (169 tuyến)',
          datasetKey: 'mst_national_road',
          totalRecords: 169,
          kind: 'physical_asset',
          icon: 'CompassOutlined',
          isLeaf: true,
        },
        {
          key: 'tbl_bridge',
          title: 'Cầu đường bộ (11,631 cầu)',
          datasetKey: 'tbl_bridge',
          totalRecords: 11631,
          kind: 'physical_asset',
          icon: 'BuildOutlined',
          isLeaf: true,
        },
      ],
    },
    {
      key: 'group_all_assets',
      title: 'Tất cả 57 danh mục Tài sản Vật lý',
      isLeaf: false,
    },
  ];

  const mockBridgeMetadata: api.DatasetMetadata = {
    datasetKey: 'tbl_bridge',
    datasetName: 'Cầu đường bộ',
    kind: 'physical_asset',
    endpoint: '/asset/get-asset-is-same-tableid',
    sourceFile: 'tbl_bridge.json',
    totalRecords: 11631,
    geometryType: 'POINT',
    fields: [
      { fieldName: 'name', displayName: 'Tên cầu', dataType: 'VARCHAR', searchable: true, filterable: false },
      { fieldName: 'lytrinh', displayName: 'Lý trình tim cầu', dataType: 'VARCHAR', searchable: true, filterable: false },
      { fieldName: 'length', displayName: 'Chiều dài (m)', dataType: 'NUMERIC', searchable: false, filterable: false },
      { fieldName: 'width', displayName: 'Chiều rộng (m)', dataType: 'NUMERIC', searchable: false, filterable: false },
    ],
  };

  const mockBridgeRecords: api.PagedResponse<api.RecordItem> = {
    content: [
      {
        id: 101,
        datasetKey: 'tbl_bridge',
        recordKey: 'BRIDGE_THANG_LONG',
        recordStatus: 'ACTIVE',
        createdAt: '2026-10-05T00:00:00Z',
        updatedAt: '2026-10-05T00:00:00Z',
        payload: {
          fielddisplay: 'Cầu Thăng Long',
          branch_id: 'kqldb_1',
          state_name: 'Đang khai thác',
          lytrinh: 'Km 6+500',
          length: '3200',
          width: '21',
          x_min: 105.783,
          y_min: 21.092,
          data_: [
            { column_name: 'Tên cầu', column_identify: 'name', column_value: 'Cầu Thăng Long', value_display: 'Cầu Thăng Long' },
            { column_name: 'Lý trình', column_identify: 'lytrinh', column_value: 'Km 6+500', value_display: 'Km 6+500' },
            { column_name: 'Chiều dài', column_identify: 'length', column_value: '3200', value_display: '3200 m' },
          ],
        },
      },
      {
        id: 102,
        datasetKey: 'tbl_bridge',
        recordKey: 'BRIDGE_NHAT_TAN',
        recordStatus: 'ACTIVE',
        createdAt: '2026-10-05T00:00:00Z',
        updatedAt: '2026-10-05T00:00:00Z',
        payload: {
          fielddisplay: 'Cầu Nhật Tân',
          branch_id: 'kqldb_1',
          state_name: 'Đang khai thác',
          lytrinh: 'Km 3+200',
          length: '3750',
          width: '33.2',
          x_min: 105.819,
          y_min: 21.095,
          data_: [
            { column_name: 'Tên cầu', column_identify: 'name', column_value: 'Cầu Nhật Tân', value_display: 'Cầu Nhật Tân' },
            { column_name: 'Lý trình', column_identify: 'lytrinh', column_value: 'Km 3+200', value_display: 'Km 3+200' },
          ],
        },
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    first: true,
    last: true,
  };

  const renderWithProviders = (currentUser = mockAdminUser, initialRoute = '/assets?datasetKey=tbl_bridge') => {
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

  beforeEach(() => {
    setDesktopViewport();
    localStorage.clear();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0 },
      },
    });
    vi.clearAllMocks();

    vi.mocked(api.fetchDatasetTree).mockResolvedValue(mockTreeNodes);
    vi.mocked(api.fetchDatasetMetadata).mockResolvedValue(mockBridgeMetadata);
    vi.mocked(api.fetchDatasetRecords).mockResolvedValue(mockBridgeRecords);
    vi.mocked(api.exportDatasetRecords).mockResolvedValue(new Blob(['test,csv'], { type: 'text/csv' }));
    vi.mocked(api.deleteAssetRecord).mockResolvedValue(undefined);
    vi.mocked(authApi.getMeApi).mockImplementation(async () =>
      JSON.parse(localStorage.getItem('kcht_user_profile') || JSON.stringify(mockAdminUser)) as api.UserSummary
    );
  });

  it('renders tree navigation and vertical slice shortcut buttons', async () => {
    renderWithProviders();

    expect(screen.getByText('Cây Phân Cấp Dữ liệu')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Tìm kiếm tập dữ liệu...')).toBeInTheDocument();

    // Vertical slice buttons
    expect(screen.getByText(/Biển báo đường bộ/)).toBeInTheDocument();
    expect(screen.getByText(/Đường quốc lộ/)).toBeInTheDocument();
    expect(screen.getByText(/Cầu đường bộ/)).toBeInTheDocument();

    await waitFor(() => {
      expect(api.fetchDatasetTree).toHaveBeenCalledTimes(1);
    });
  });

  it('renders records table with dynamic columns for tbl_bridge', async () => {
    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText('Cầu Thăng Long')).toBeInTheDocument();
      expect(screen.getByText('Cầu Nhật Tân')).toBeInTheDocument();
    });

    // Check dynamic columns for bridge
    expect(screen.getAllByText('Lý trình tim cầu').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Chiều dài (m)').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Chiều rộng (m)').length).toBeGreaterThan(0);

    // Check values
    expect(screen.getByText('Km 6+500')).toBeInTheDocument();
    expect(screen.getByText('3200')).toBeInTheDocument();
  });

  it('keeps the action column fixed on desktop so row actions remain reachable', async () => {
    const { container } = renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText('Cầu Thăng Long')).toBeInTheDocument();
      expect(screen.getByText('Cầu Nhật Tân')).toBeInTheDocument();
    });

    const actionHeader = screen.getByRole('columnheader', { name: 'Thao tác' });
    expect(actionHeader).toHaveClass('ant-table-cell-fix-right');
    expect(container.querySelector('.kcht-asset-table table')).toHaveStyle({ width: 'max-content' });
  });

  it('uses mobile cards instead of forcing the wide asset table on a narrow viewport', async () => {
    setMobileViewport();
    const { container } = renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText('Cầu Thăng Long')).toBeInTheDocument();
    });

    expect(container.querySelector('.kcht-asset-mobile-list')).toBeInTheDocument();
    expect(container.querySelector('.kcht-asset-table')).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Chi tiết/i })).toHaveLength(2);
  });

  it('distinguishes an explicit null value from a field missing in the API contract', async () => {
    vi.mocked(api.fetchDatasetRecords).mockResolvedValue({
      ...mockBridgeRecords,
      content: [
        {
          ...mockBridgeRecords.content[0],
          payload: {
            ...mockBridgeRecords.content[0].payload,
            width: null,
          },
        },
      ],
      totalElements: 1,
    });

    renderWithProviders();

    const row = await screen.findByRole('row', { name: /Cầu Thăng Long/ });
    expect(within(row).getByText('Chưa có dữ liệu')).toBeInTheDocument();
    expect(within(row).getByText('Thiếu ánh xạ')).toBeInTheDocument();
  });

  it('localizes a technical record status while retaining the raw value in a tooltip', async () => {
    vi.mocked(api.fetchDatasetRecords).mockResolvedValue({
      ...mockBridgeRecords,
      content: [
        {
          ...mockBridgeRecords.content[0],
          recordStatus: 'RAW_STORED',
          payload: {
            ...mockBridgeRecords.content[0].payload,
            state_name: null,
          },
        },
      ],
      totalElements: 1,
    });

    renderWithProviders();

    const localizedStatus = await screen.findByText('Đã lưu dữ liệu thô');
    fireEvent.mouseOver(localizedStatus);

    await waitFor(() => {
      expect(screen.getByRole('tooltip')).toHaveTextContent('Giá trị kỹ thuật: RAW_STORED');
    });
  });

  it('enables bulk row selection for ROLE_ADMIN and shows selection bar', async () => {
    renderWithProviders(mockAdminUser);

    await waitFor(() => {
      expect(screen.getByText('Cầu Thăng Long')).toBeInTheDocument();
    });

    // Find table checkboxes
    const selectAllCheckbox = screen.getByRole('checkbox', { name: /Select all/i });
    expect(selectAllCheckbox).toBeInTheDocument();

    // Select all rows
    fireEvent.click(selectAllCheckbox);

    await waitFor(() => {
      expect(screen.getByText(/Đã chọn/)).toBeInTheDocument();
      expect(screen.getByText(/Xuất 2 dòng đã chọn/)).toBeInTheDocument();
    });
  });

  it('disables bulk selection for ROLE_VIEWER (read-only)', async () => {
    renderWithProviders(mockViewerUser);

    await waitFor(() => {
      expect(screen.getByText('Cầu Thăng Long')).toBeInTheDocument();
    });

    // When role is viewer, rowSelection is undefined so there are no row checkboxes
    const checkboxes = screen.queryAllByRole('checkbox');
    expect(checkboxes.length).toBe(0);
  });

  it('opens record detail drawer with attributes and coordinates on "Chi tiết" click', async () => {
    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText('Cầu Thăng Long')).toBeInTheDocument();
    });

    // Find "Chi tiết" buttons
    const detailButtons = screen.getAllByRole('button', { name: /Chi tiết/i });
    expect(detailButtons.length).toBeGreaterThan(0);

    // Click first detail button
    fireEvent.click(detailButtons[0]);

    // Drawer should open and display record details
    await waitFor(() => {
      expect(screen.getByText('Khóa nghiệp vụ: BRIDGE_THANG_LONG')).toBeInTheDocument();
      expect(screen.getByText('Thuộc tính Nghiệp vụ')).toBeInTheDocument();
      expect(screen.getByText('Không gian & Tọa độ GIS')).toBeInTheDocument();
      expect(screen.getByText('Dữ liệu Gốc JSON')).toBeInTheDocument();
      expect(screen.getByText('Kiểm toán & Nguồn gốc')).toBeInTheDocument();
    });

    // Switch to GIS tab and verify coordinates displayed
    const gisTab = screen.getByText('Không gian & Tọa độ GIS');
    fireEvent.click(gisTab);

    await waitFor(() => {
      expect(screen.getByText(/EPSG:4326/)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Mở Định vị trên Bản đồ Số WebGIS/i })).toBeInTheDocument();
    });
  });

  it('triggers CSV export when export button is clicked', async () => {
    const originalCreateObjectURL = window.URL.createObjectURL;
    const originalRevokeObjectURL = window.URL.revokeObjectURL;
    window.URL.createObjectURL = vi.fn().mockReturnValue('blob:mock-url');
    window.URL.revokeObjectURL = vi.fn();

    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText('Cầu Thăng Long')).toBeInTheDocument();
    });
    const exportBtn = screen.getByRole('button', { name: /Xuất file CSV/i });
    fireEvent.click(exportBtn);

    await waitFor(() => {
      expect(api.exportDatasetRecords).toHaveBeenCalledWith(
        'tbl_bridge',
        expect.objectContaining({ limit: 2000 })
      );
    });

    window.URL.createObjectURL = originalCreateObjectURL;
    window.URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('prevents duplicate delete actions while pending and invalidates the record cache after success', async () => {
    let resolveDelete: (() => void) | undefined;
    vi.mocked(api.deleteAssetRecord).mockReset().mockImplementation(
      () => new Promise<void>((resolve) => {
        resolveDelete = resolve;
      })
    );
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText('Cầu Thăng Long')).toBeInTheDocument();
    });

    const getRowDeleteButtons = () => Array.from(
      document.querySelectorAll<HTMLButtonElement>('.kcht-asset-table button.ant-btn-dangerous')
    );
    await waitFor(() => expect(getRowDeleteButtons()).toHaveLength(2));
    fireEvent.click(getRowDeleteButtons()[0]);
    const confirmButton = await waitFor(() => {
      const button = document.querySelector<HTMLButtonElement>('.ant-popconfirm-buttons .ant-btn-primary');
      expect(button).not.toBeNull();
      return button as HTMLButtonElement;
    });
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(api.deleteAssetRecord).toHaveBeenCalledTimes(1);
      const deleteButtons = getRowDeleteButtons();
      expect(deleteButtons[1]).toBeDisabled();
    });

    fireEvent.click(getRowDeleteButtons()[1]);
    expect(api.deleteAssetRecord).toHaveBeenCalledTimes(1);

    resolveDelete?.();
    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['datasetRecords', 'tbl_bridge'] });
    });
  });
});
