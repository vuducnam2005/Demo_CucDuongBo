import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { AuthProvider } from '../context/AuthContext';
import { ReportIndexPage } from '../pages/ReportIndexPage';
import { CatalogListPage } from '../pages/CatalogListPage';
import { DocumentExplorerPage } from '../pages/DocumentExplorerPage';
import * as reportApi from '../services/reportApi';
import * as catalogApi from '../services/catalogApi';
import * as documentApi from '../services/documentApi';

vi.mock('../services/reportApi', () => ({
  fetchRoadLengthReport: vi.fn(),
  exportRoadLengthReportCsv: vi.fn(),
  fetchMaintenanceReport: vi.fn(),
  exportMaintenanceReportCsv: vi.fn(),
  fetchRoadSignBlackspotReport: vi.fn(),
  exportRoadSignBlackspotReportCsv: vi.fn(),
}));

vi.mock('../services/catalogApi', () => ({
  fetchAllCatalogs: vi.fn(),
  fetchCatalogItems: vi.fn(),
  fetchCatalogItem: vi.fn(),
  createCatalogItem: vi.fn(),
  updateCatalogItem: vi.fn(),
  deleteCatalogItem: vi.fn(),
}));

vi.mock('../services/documentApi', () => ({
  fetchDocumentFolders: vi.fn(),
  createDocumentFolder: vi.fn(),
  fetchDocuments: vi.fn(),
  fetchDocumentById: vi.fn(),
  downloadDocumentFile: vi.fn(),
  uploadDocumentFile: vi.fn(),
  deleteDocumentFile: vi.fn(),
}));

describe('Phase 10 Frontend Pages Integration Tests', () => {
  let queryClient: QueryClient;

  const mockAdminUser = {
    id: 1,
    username: 'admin',
    email: 'admin@drvn.gov.vn',
    fullName: 'Quản trị viên Hệ thống',
    role: 'ROLE_ADMIN',
    roleName: 'Quản trị hệ thống',
    permissions: ['*'],
  };

  const defaultRoadLengthData = {
    summary: {
      totalRoutes: 168,
      totalSegments: 168,
      totalLengthKm: 27469.3,
      averageLengthKm: 163.5,
      longestRouteCode: 'QL.1',
      longestRouteLengthKm: 2395.0,
    },
    routes: [
      {
        routeCode: 'QL.1',
        routeName: 'Quốc lộ 1',
        lengthKm: 2395.0,
        segmentCount: 1,
        branchId: 'cdb_vn',
        branchName: 'Cục Đường bộ Việt Nam',
        surfaceType: 'Bê tông nhựa cấp cao A1',
        roadClass: 'Cấp I - II đồng bằng',
        managementUnit: 'Chi cục Quản lý đường bộ cdb_vn',
      },
    ],
    branchDistribution: [
      {
        branchId: 'cdb_vn',
        branchName: 'Cục Đường bộ Việt Nam',
        routeCount: 168,
        totalLengthKm: 27469.3,
        percent: 100.0,
      },
    ],
    surfaceDistribution: [
      {
        surfaceType: 'Bê tông nhựa cấp cao A1',
        totalLengthKm: 27469.3,
        percent: 100.0,
      },
    ],
  };

  const renderWithProviders = (ui: React.ReactElement) => {
    return render(
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <ConfigProvider>
            <AuthProvider>{ui}</AuthProvider>
          </ConfigProvider>
        </QueryClientProvider>
      </MemoryRouter>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    localStorage.setItem('kcht_user_profile', JSON.stringify(mockAdminUser));
    localStorage.setItem('kcht_access_token', 'mock_admin_token');

    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });

    vi.mocked(reportApi.fetchRoadLengthReport).mockResolvedValue(defaultRoadLengthData);
  });

  describe('ReportIndexPage', () => {
    it('renders report navigation tabs and road length metrics correctly', async () => {
      renderWithProviders(<ReportIndexPage />);

      expect(screen.getByText(/Báo cáo & Thống kê Kết cấu Hạ tầng/i)).toBeInTheDocument();
      expect(screen.getByText(/Chiều dài mạng lưới đường bộ/i)).toBeInTheDocument();
      expect(screen.getByText(/Kế hoạch & Thực hiện bảo trì/i)).toBeInTheDocument();
      expect(screen.getByText(/Biển báo QCVN 41 & Điểm đen TNGT/i)).toBeInTheDocument();

      const routeTitle = await screen.findByText('Tổng số tuyến quốc lộ');
      expect(routeTitle).toBeInTheDocument();
      const count168 = await screen.findAllByText('168');
      expect(count168.length).toBeGreaterThan(0);
      expect(await screen.findByText('Quốc lộ 1')).toBeInTheDocument();
    });

    it('switches to Maintenance report tab and displays maintenance data', async () => {
      vi.mocked(reportApi.fetchMaintenanceReport).mockResolvedValue({
        summary: {
          totalProjects: 14,
          totalBudgetVnd: 235000000000,
          completedCount: 8,
          inProgressCount: 4,
          plannedCount: 2,
          completionRatePercent: 57.1,
        },
        projects: [
          {
            id: 'm_01',
            projectCode: 'BT-2026-01',
            projectName: 'Sửa chữa đột xuất mặt đường QL.1 đoạn Km120 - Km135',
            branchId: 'cuc_ql_duong_bo_1',
            branchName: 'Khu QLĐB I',
            routeCode: 'QL.1',
            maintenanceType: 'Sửa chữa định kỳ',
            budgetVnd: 28500000000,
            planYear: 2026,
            status: 'Đang thi công',
            contractor: 'Công ty CP Đầu tư Xây dựng 703',
            startDate: '2026-02-15',
            endDate: '2026-08-30',
          },
        ],
        branchSummary: [],
        yearlySummary: [],
      });

      renderWithProviders(<ReportIndexPage />);

      const maintTab = screen.getByText(/Kế hoạch & Thực hiện bảo trì/i);
      fireEvent.click(maintTab);

      expect(await screen.findByText('Tổng số công trình / dự án')).toBeInTheDocument();
      expect(await screen.findByText('14')).toBeInTheDocument();
      expect(await screen.findByText(/Sửa chữa đột xuất mặt đường QL.1/i)).toBeInTheDocument();
    });

    it('renders report fields using the backend contract, including zero and vi-VN values', async () => {
      vi.mocked(reportApi.fetchRoadSignBlackspotReport).mockResolvedValue({
        summary: {
          totalRoadSigns: 222112,
          totalSignCategories: 5,
          totalBlackspots: 1,
          highRiskBlackspots: 0,
          rectifiedBlackspots: 0,
          monitoredBlackspots: 1,
        },
        signCategories: [
          {
            categoryCode: 'P',
            categoryName: 'Biển cấm',
            signPrefix: 'P.',
            count: 1_000_000,
            percent: 0,
            sampleSignCode: 'P.102',
            description: 'Mô tả tiếng Việt',
          },
        ],
        blackspots: [
          {
            id: 'bs-1',
            spotCode: 'DDB-01',
            routeCode: 'QL.1',
            kmMarker: 'Km 12+345',
            branchId: 'cuc_ql_duong_bo_1',
            branchName: 'Khu QLĐB I',
            severity: 'Nguy hiểm',
            description: 'Mặt đường trơn',
            incidentCount: 0,
            fatalityCount: 0,
            rectificationStatus: 'Đang theo dõi',
            longitude: null,
            latitude: null,
          },
        ],
        branchStatistics: [
          { branchId: 'cuc_ql_duong_bo_1', branchName: 'Khu QLĐB I', signCount: 1_000_000, blackspotCount: 1 },
        ],
      });

      renderWithProviders(<ReportIndexPage />);
      fireEvent.click(screen.getByRole('tab', { name: /Biển báo QCVN 41 & Điểm đen TNGT/i }));
      await vi.waitFor(() => expect(reportApi.fetchRoadSignBlackspotReport).toHaveBeenCalled());

      const signTotalTitle = await screen.findByText('Tổng số biển báo hiệu QCVN 41');
      expect(signTotalTitle.closest('.ant-card')).toHaveTextContent('222.112');
      expect(await screen.findByText('P.102')).toBeInTheDocument();
      expect(await screen.findByText('Km 12+345')).toBeInTheDocument();
      expect(await screen.findByText('Mặt đường trơn')).toBeInTheDocument();
      expect(await screen.findByText(/0 vụ/)).toBeInTheDocument();
    });
  });

  describe('CatalogListPage', () => {
    it('renders reference catalogs summary and items table', async () => {
      vi.mocked(catalogApi.fetchAllCatalogs).mockResolvedValue([
        {
          catalogCode: 'c_tinhthanhpho',
          catalogName: 'Danh mục Tỉnh / Thành phố',
          description: 'Danh mục 63 đơn vị hành chính cấp tỉnh',
          itemCount: 63,
          sourceType: 'raw_dataset_record',
          isEditable: true,
        },
        {
          catalogCode: 'capduong',
          catalogName: 'Cấp kỹ thuật đường bộ',
          description: 'Phân cấp thiết kế đường bộ TCVN',
          itemCount: 8,
          sourceType: 'reference_catalog',
          isEditable: true,
        },
      ]);

      vi.mocked(catalogApi.fetchCatalogItems).mockResolvedValue({
        content: [
          {
            catalogCode: 'c_tinhthanhpho',
            itemCode: '01',
            itemName: 'Thành phố Hà Nội',
            parentCode: null,
            sortOrder: 1,
            active: true,
            extraAttributes: {},
          },
          {
            catalogCode: 'c_tinhthanhpho',
            itemCode: '79',
            itemName: 'Thành phố Hồ Chí Minh',
            parentCode: null,
            sortOrder: 2,
            active: true,
            extraAttributes: {},
          },
        ],
        page: 0,
        size: 15,
        totalElements: 63,
        totalPages: 5,
      });

      renderWithProviders(<CatalogListPage />);

      expect(await screen.findByText('Danh mục Chuẩn Ngành Đường bộ')).toBeInTheDocument();
      const catalogNames = await screen.findAllByText('Danh mục Tỉnh / Thành phố');
      expect(catalogNames.length).toBeGreaterThan(0);
      expect(await screen.findByText('Thành phố Hà Nội')).toBeInTheDocument();
      expect(await screen.findByText('Thành phố Hồ Chí Minh')).toBeInTheDocument();
    });
  });

  describe('DocumentExplorerPage', () => {
    it('renders folder tree and documents list with download action', async () => {
      vi.mocked(documentApi.fetchDocumentFolders).mockResolvedValue([
        {
          id: '1',
          folderCode: 'cdb_vn',
          folderName: 'Hồ sơ Cục Đường bộ',
          parentId: '#',
          documentCount: 15,
        },
      ]);

      vi.mocked(documentApi.fetchDocuments).mockResolvedValue({
        content: [
          {
            id: 'doc_01',
            fileEntryId: 'fe_01',
            fileName: 'Ho_so_thiet_ke_cau_Bai_Chay.pdf',
            fileExtension: 'pdf',
            mimeType: 'application/pdf',
            fileSize: 1048576,
            groupId: 'cdb_vn',
            groupName: 'Hồ sơ Cục Đường bộ',
            objectName: 'Cầu Bãi Cháy',
            tableName: 'asset_record',
            uploader: 'admin',
            createdAt: '2026-03-15T08:00:00Z',
          },
        ],
        page: 0,
        size: 15,
        totalElements: 1,
        totalPages: 1,
      });

      renderWithProviders(<DocumentExplorerPage />);

      expect(await screen.findByText('Quản lý Hồ sơ & Tài liệu Kỹ thuật KCHT')).toBeInTheDocument();
      expect(await screen.findByText('Hồ sơ Cục Đường bộ')).toBeInTheDocument();
      expect(await screen.findByText('Ho_so_thiet_ke_cau_Bai_Chay.pdf')).toBeInTheDocument();
      expect(await screen.findByText('1.0 MB')).toBeInTheDocument();
    });
  });
});
