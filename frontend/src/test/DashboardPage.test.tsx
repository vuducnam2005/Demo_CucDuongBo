import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ConfigProvider } from "antd";
import { AuthProvider } from "../context/AuthContext";
import { DashboardPage } from "../pages/DashboardPage";
import * as api from "../services/api";
import * as vroadApi from "../services/vroadApi";

vi.mock("../services/vroadApi", () => ({ fetchSurveyOverview: vi.fn() }));

vi.mock("../services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/api")>();
  return {
    ...actual,
    fetchDashboardSummary: vi.fn(),
    fetchDashboardBranches: vi.fn(),
    fetchDashboardDatasets: vi.fn(),
    fetchDashboardRoadSigns: vi.fn(),
    fetchDashboardRoadLengths: vi.fn(),
    fetchDashboardRecentAssets: vi.fn(),
  };
});

describe("DashboardPage Unit & Integration Tests", () => {
  let queryClient: QueryClient;

  const mockUser = {
    id: 1,
    username: "admin",
    email: "admin@drvn.gov.vn",
    fullName: "Quản trị viên Hệ thống",
    role: "ROLE_ADMIN",
    roleName: "Quản trị hệ thống",
    permissions: ["*"],
  };

  const mockSummary = {
    totalAssets: 830836,
    totalDatasets: 658,
    physicalAssetDatasets: 57,
    moduleDatasets: 601,
    totalBridges: 11631,
    totalRoadSigns: 222112,
    totalNationalRoadRoutes: 168,
    totalNationalRoadLengthKm: 27469.26,
    totalDocuments: 309,
    sourceDataset: "raw_dataset_record, dataset_registry",
    filter: "Toàn quốc - Đang khai thác",
    lastUpdated: "2026-10-06T00:54:39Z",
  };

  const mockBranches = [
    {
      branchId: "kqldb_1",
      branchName: "Khu Quản lý Đường bộ I",
      totalAssets: 117143,
      bridgeCount: 843,
      roadSignCount: 33076,
      nationalRoadCount: 28,
      sourceDataset: "mv_dashboard_branch_stats",
      filter: "Theo Đơn vị / Chi nhánh quản lý",
      lastUpdated: "2026-10-06T00:54:39Z",
    },
    {
      branchId: "kqldb_2",
      branchName: "Khu Quản lý Đường bộ II",
      totalAssets: 135420,
      bridgeCount: 797,
      roadSignCount: 34861,
      nationalRoadCount: 32,
      sourceDataset: "mv_dashboard_branch_stats",
      filter: "Theo Đơn vị / Chi nhánh quản lý",
      lastUpdated: "2026-10-06T00:54:39Z",
    },
  ];

  const mockDatasets = [
    {
      datasetKey: "tbl_road_sign",
      datasetName: "Biển báo đường bộ",
      kind: "physical_asset",
      totalRecords: 222112,
      sourceFile: "tbl_road_sign.json",
      sourceDataset: "dataset_registry",
      filter: "Top 10 Physical Asset Datasets",
      lastUpdated: "2026-10-06T00:54:39Z",
    },
    {
      datasetKey: "tbl_bridge",
      datasetName: "Cầu đường bộ",
      kind: "physical_asset",
      totalRecords: 11631,
      sourceFile: "tbl_bridge.json",
      sourceDataset: "dataset_registry",
      filter: "Top 10 Physical Asset Datasets",
      lastUpdated: "2026-10-06T00:54:39Z",
    },
  ];

  const mockRoadSigns = {
    totalSigns: 222112,
    byBranch: [
      {
        branchId: "kqldb_2",
        branchName: "Khu Quản lý Đường bộ II",
        count: 34861,
        percentage: 15.7,
      },
      {
        branchId: "kqldb_1",
        branchName: "Khu Quản lý Đường bộ I",
        count: 33076,
        percentage: 14.9,
      },
    ],
    byShape: [
      { category: "Biển tam giác (Nguy hiểm)", count: 85400 },
      { category: "Biển tròn (Cấm & Hiệu lệnh)", count: 68200 },
    ],
    sourceDataset: "tbl_road_sign",
    filter: "Toàn quốc (Theo QCVN 41:2019/BGTVT)",
    lastUpdated: "2026-10-06T00:54:39Z",
  };

  const mockRoadLengths = {
    totalRoutes: 168,
    totalLengthKm: 27469.26,
    averageLengthKm: 163.5,
    longestRoutes: [
      {
        routeCode: "QL.1",
        routeName: "Quốc lộ 1 (Lạng Sơn - Cà Mau)",
        lengthKm: 3878.94,
      },
      {
        routeCode: "QL.HCM",
        routeName: "Đường Hồ Chí Minh",
        lengthKm: 2661.92,
      },
    ],
    distribution: [
      { rangeLabel: "< 50 km", routeCount: 52, totalKm: 1450.5 },
      { rangeLabel: "> 500 km", routeCount: 8, totalKm: 11200.0 },
    ],
    sourceDataset: "mst_national_road (data_->actual_length)",
    filter: "Toàn bộ 168 tuyến Quốc lộ chính",
    lastUpdated: "2026-10-06T00:54:39Z",
  };

  const mockRecentAssets = [
    {
      datasetKey: "tbl_bridge",
      datasetName: "Cầu đường bộ",
      recordKey: "bridge-1001",
      assetName: "Cầu Bãi Cháy",
      branchId: "kqldb_1",
      branchName: "Khu Quản lý Đường bộ I",
      importedAt: "2026-10-06T00:50:00Z",
      sourceDataset: "raw_dataset_record",
      detailUrl: "/assets?datasetKey=tbl_bridge",
    },
  ];

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem("kcht_access_token", "valid_token");
    localStorage.setItem("kcht_user_profile", JSON.stringify(mockUser));

    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });

    vi.mocked(api.fetchDashboardSummary).mockResolvedValue(mockSummary);
    vi.mocked(api.fetchDashboardBranches).mockResolvedValue(mockBranches);
    vi.mocked(api.fetchDashboardDatasets).mockResolvedValue(mockDatasets);
    vi.mocked(api.fetchDashboardRoadSigns).mockResolvedValue(mockRoadSigns);
    vi.mocked(api.fetchDashboardRoadLengths).mockResolvedValue(mockRoadLengths);
    vi.mocked(api.fetchDashboardRecentAssets).mockResolvedValue(
      mockRecentAssets,
    );
    vi.mocked(vroadApi.fetchSurveyOverview).mockResolvedValue({
      assets: 2614,
      defects: 3404,
      iriSegments: 221,
      resolvedCases: 3,
      defectTypes: [{ label: "Nứt vỡ", count: 663 }],
      assetCategories: [{ label: "Mặt đường", count: 1634 }],
    });
  });

  const renderDashboard = () => {
    return render(
      <QueryClientProvider client={queryClient}>
        <ConfigProvider theme={{ hashed: false }}>
          <AuthProvider>
            <MemoryRouter initialEntries={["/dashboard"]}>
              <DashboardPage />
            </MemoryRouter>
          </AuthProvider>
        </ConfigProvider>
      </QueryClientProvider>,
    );
  };

  it("renders the live survey charts without hardcoded national totals", async () => {
    renderDashboard();

    expect(
      screen.getByText(/Bảng Điều hành & Giám sát KCHT Đường bộ/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/Quản trị viên Hệ thống/i)).toBeInTheDocument();
    expect(screen.queryByText(/1,1 triệu bản ghi/)).not.toBeInTheDocument();
    expect(
      await screen.findByRole("img", { name: "Biểu đồ Loại hư hỏng" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "Biểu đồ Nhóm tài sản" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Nứt vỡ")).toBeInTheDocument();

    fireEvent.mouseEnter(
      screen.getByRole("button", { name: /Nứt vỡ: 663 điểm/i }),
    );
    expect(await screen.findByText("663 điểm")).toBeInTheDocument();
    expect(screen.getByText("100% tổng số")).toBeInTheDocument();
  });

  it("renders all 6 executive KPI cards without technical source notes", async () => {
    renderDashboard();

    // 1. Total Assets Card
    await waitFor(() => {
      expect(screen.getByText("830.836")).toBeInTheDocument();
    });
    expect(
      screen.getAllByText("Tổng Tài sản KCHT").length,
    ).toBeGreaterThanOrEqual(1);

    // 2. Bridges Card
    expect(screen.getByText("Cầu Đường bộ")).toBeInTheDocument();
    expect(screen.getByText("11.631")).toBeInTheDocument();

    // 3. Road Signs Card
    expect(screen.getByText("Biển báo Đường bộ")).toBeInTheDocument();
    expect(screen.getByText("222.112")).toBeInTheDocument();

    // 4. National Roads Card
    expect(screen.getByText("Tuyến Quốc lộ Chính")).toBeInTheDocument();
    expect(screen.getByText("168")).toBeInTheDocument();

    // 5. Datasets Card
    expect(screen.getByText("Kho Dữ liệu & Danh mục")).toBeInTheDocument();
    expect(screen.getByText("658")).toBeInTheDocument();

    // 6. Documents Card
    expect(screen.getByText("Hồ sơ Kỹ thuật (S3)")).toBeInTheDocument();
    expect(screen.getByText("309")).toBeInTheDocument();

    expect(screen.queryByText(/^Nguồn:/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Lọc:/i)).not.toBeInTheDocument();
  });

  it("renders branch distribution table with aggregated metrics", async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText("Khu Quản lý Đường bộ I")).toBeInTheDocument();
      expect(screen.getByText("Khu Quản lý Đường bộ II")).toBeInTheDocument();
    });

    expect(screen.getByText("117.143")).toBeInTheDocument();
    expect(screen.getByText("135.420")).toBeInTheDocument();
  });

  it("switches to Road Signs tab and displays QCVN 41 categories and branch breakdowns", async () => {
    renderDashboard();

    await waitFor(() => {
      expect(
        screen.getByText(/Chuyên đề Biển báo Giao thông/i),
      ).toBeInTheDocument();
    });

    const roadSignTab = screen.getByText(/Chuyên đề Biển báo Giao thông/i);
    fireEvent.click(roadSignTab);

    await waitFor(() => {
      expect(
        screen.getByText(/Thống kê Toàn diện Biển báo Giao thông Đường bộ/i),
      ).toBeInTheDocument();
      expect(screen.getByText(/Biển tam giác/i)).toBeInTheDocument();
      expect(screen.getByText(/Biển tròn/i)).toBeInTheDocument();
    });
  });

  it("switches to Road Lengths tab and displays longest routes and distance distribution", async () => {
    renderDashboard();

    await waitFor(() => {
      expect(
        screen.getByText(/Thống kê Chiều dài Tuyến Quốc lộ/i),
      ).toBeInTheDocument();
    });

    const roadLengthTab = screen.getByText(/Thống kê Chiều dài Tuyến Quốc lộ/i);
    fireEvent.click(roadLengthTab);

    await waitFor(() => {
      expect(
        screen.getByText(
          /Thống kê Chiều dài Mạng lưới Tuyến Quốc lộ Toàn quốc/i,
        ),
      ).toBeInTheDocument();
      expect(
        screen.getByText("Quốc lộ 1 (Lạng Sơn - Cà Mau)"),
      ).toBeInTheDocument();
      expect(screen.getByText("Đường Hồ Chí Minh")).toBeInTheDocument();
      expect(screen.getByText(/Cự ly < 50 km/i)).toBeInTheDocument();
    });
  });

  it("switches to Datasets & Recent Assets tab and displays recent feed", async () => {
    renderDashboard();

    await waitFor(() => {
      expect(
        screen.getByText(/Top Tập Dữ liệu & Tài sản Mới Cập nhật/i),
      ).toBeInTheDocument();
    });

    const datasetsTab = screen.getByText(
      /Top Tập Dữ liệu & Tài sản Mới Cập nhật/i,
    );
    fireEvent.click(datasetsTab);

    await waitFor(() => {
      expect(screen.getByText("Cầu Bãi Cháy")).toBeInTheDocument();
      expect(
        screen.getByText("Top 10 Tập Dữ liệu Quy mô lớn nhất"),
      ).toBeInTheDocument();
    });
  });

  it('triggers refresh when "Làm mới Dữ liệu" button is clicked', async () => {
    renderDashboard();

    const refreshBtn = screen.getByRole("button", { name: /làm mới dữ liệu/i });
    expect(refreshBtn).toBeInTheDocument();

    fireEvent.click(refreshBtn);

    expect(api.fetchDashboardSummary).toHaveBeenCalled();
    expect(api.fetchDashboardBranches).toHaveBeenCalled();
    expect(api.fetchDashboardDatasets).toHaveBeenCalled();
    expect(api.fetchDashboardRoadSigns).toHaveBeenCalled();
    expect(api.fetchDashboardRoadLengths).toHaveBeenCalled();
    expect(api.fetchDashboardRecentAssets).toHaveBeenCalled();
  });
});
