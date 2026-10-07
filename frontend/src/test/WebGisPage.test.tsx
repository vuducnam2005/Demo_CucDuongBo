import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App, message } from 'antd';
import { WebGisPage } from '../pages/WebGisPage';
import * as gisApi from '../services/gisApi';

// Mock gisApi
vi.mock('../services/gisApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/gisApi')>();
  return {
    ...actual,
    fetchGeoData: vi.fn(),
    fetchSpatialClusters: vi.fn(),
    fetchGisBenchmark: vi.fn(),
  };
});

describe('WebGisPage Unit & Integration Tests', () => {
  let queryClient: QueryClient;

  const mockGeoData: gisApi.GeoJsonFeatureCollection = {
    type: 'FeatureCollection',
    features: [
      {
        id: 'road_sign_1001',
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [105.8542, 21.0285],
        },
        properties: {
          dataset_key: 'tbl_road_sign',
          record_key: 'road_sign_1001',
          display_name: 'Biển báo P.102 Cấm đi ngược chiều',
          branch_id: 'cuc_ql_duong_bo_1',
          state_name: 'Đang khai thác',
          route_code: 'QL.1',
        },
      },
      {
        id: 'road_sign_1002',
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [105.8600, 21.0300],
        },
        properties: {
          dataset_key: 'tbl_road_sign',
          record_key: 'road_sign_1002',
          display_name: 'Biển báo W.201 Chỗ ngoặt nguy hiểm',
          branch_id: 'cuc_ql_duong_bo_1',
          state_name: 'Cần bảo trì',
          route_code: 'QL.1',
        },
      },
    ],
  };

  const mockClusterData: gisApi.GeoJsonFeatureCollection = {
    type: 'FeatureCollection',
    features: [
      {
        id: 'cluster_0',
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [105.85, 21.03],
        },
        properties: {
          is_cluster: true,
          point_count: 142,
          dataset_key: 'tbl_road_sign',
          sample_key: 'road_sign_1001',
          sample_name: 'Biển báo P.102',
          display_name: '142 đối tượng (Biển báo P.102...)',
        },
      },
      {
        id: 'cluster_1',
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [105.95, 21.15],
        },
        properties: {
          is_cluster: true,
          point_count: 88,
          dataset_key: 'tbl_road_sign',
          sample_key: 'road_sign_2001',
          sample_name: 'Biển báo R.301',
          display_name: '88 đối tượng (Biển báo R.301...)',
        },
      },
    ],
  };

  const mockBenchmarkData: gisApi.GisBenchmarkResponse = {
    benchmarkTimestamp: '2026-10-06T01:46:17.878Z',
    totalRecordsEvaluated: 414040,
    complianceNoFullLoad: true,
    safetyAssessment:
      'PASS: 100% tuân thủ quy tắc kiến trúc. Triệt để ngăn chặn tình trạng crash trình duyệt do tải đồng thời 222k geometries.',
    datasets: [
      {
        datasetKey: 'tbl_road_sign',
        datasetName: 'Biển báo hiệu đường bộ (QCVN 41)',
        totalDatasetRecords: 222112,
        spatialRecordsWithCoordinates: 55612,
        bboxQueryExecutionTimeMs: 271,
        bboxFeaturesReturned: 500,
        gridClusterExecutionTimeMs: 19282,
        gridClustersReturned: 157,
        fullLoadEstimatedSizeBytes: 166584000,
        actualPayloadSizeBytes: 225000,
        compressionRatioPercent: 99.86,
        verdict: 'PASS: Tối ưu an toàn - Không tải toàn bộ 222112 bản ghi vào browser',
      },
      {
        datasetKey: 'road_sphere_mirror',
        datasetName: 'Cột biển báo & Gương cầu lồi',
        totalDatasetRecords: 191928,
        spatialRecordsWithCoordinates: 52196,
        bboxQueryExecutionTimeMs: 184,
        bboxFeaturesReturned: 500,
        gridClusterExecutionTimeMs: 7517,
        gridClustersReturned: 158,
        fullLoadEstimatedSizeBytes: 143946000,
        actualPayloadSizeBytes: 225000,
        compressionRatioPercent: 99.84,
        verdict: 'PASS: Tối ưu an toàn - Không tải toàn bộ 191928 bản ghi vào browser',
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });

    vi.mocked(gisApi.fetchGeoData).mockResolvedValue(mockGeoData);
    vi.mocked(gisApi.fetchSpatialClusters).mockResolvedValue(mockClusterData);
    vi.mocked(gisApi.fetchGisBenchmark).mockResolvedValue(mockBenchmarkData);
  });

  const renderComponent = (initialEntries = ['/map']) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <ConfigProvider>
          <App>
            <MemoryRouter initialEntries={initialEntries}>
              <WebGisPage />
            </MemoryRouter>
          </App>
        </ConfigProvider>
      </QueryClientProvider>
    );
  };

  it('1. Renders WebGIS title, tags, and safety banner', async () => {
    renderComponent();

    expect(screen.getByText('Bản đồ Số WebGIS')).toBeInTheDocument();
    expect(screen.getByText('PostGIS + OpenLayers')).toBeInTheDocument();
    expect(screen.getByText('EPSG:4326')).toBeInTheDocument();

    // Check safety rule banner
    expect(
      screen.getByText(/Chế độ an toàn: Tải theo BBOX & Lưới gom cụm/i)
    ).toBeInTheDocument();

    // Check map legend panel
    expect(screen.getByText('Chú giải lớp bản đồ:')).toBeInTheDocument();
    expect(screen.getAllByText('Biển báo hiệu đường bộ (222k)').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Cột biển báo & Gương cầu (191k)').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Cầu đường bộ (7.4k)').length).toBeGreaterThan(0);
  });

  it('2. Loads initial spatial cluster data without dumping 222k records', async () => {
    renderComponent();

    await waitFor(() => {
      expect(gisApi.fetchSpatialClusters).toHaveBeenCalled();
    });

    // Verify clusters tag count is rendered
    await waitFor(() => {
      expect(screen.getByText('2 cụm điểm PostGIS')).toBeInTheDocument();
    });
    expect(gisApi.fetchSpatialClusters).toHaveBeenCalledTimes(1);
  });

  it('3. Renders filter inputs (branch, status, route, keyword)', () => {
    renderComponent();

    expect(screen.getByText('Khu vực quản lý')).toBeInTheDocument();
    expect(screen.getByText('Trạng thái')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Tuyến (QL.1, QL.5...)')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Tìm kiếm mã, tên tài sản...')).toBeInTheDocument();
  });

  it('4. Executes GIS benchmark modal and validates safety assessment', async () => {
    renderComponent();

    const benchmarkBtn = screen.getByRole('button', { name: /Đo kiểm GIS/i });
    expect(benchmarkBtn).toBeInTheDocument();
    fireEvent.click(benchmarkBtn);

    await waitFor(() => {
      expect(gisApi.fetchGisBenchmark).toHaveBeenCalledWith('tbl_road_sign', 'road_sphere_mirror');
    });

    // Check modal appears with safety metrics
    await waitFor(() => {
      expect(screen.getByText('Kết quả Đo kiểm Hiệu năng Không gian GIS & Tuân thủ Kiến trúc')).toBeInTheDocument();
      expect(screen.getByText('Tuân thủ Kiến trúc Tuyệt đối')).toBeInTheDocument();
      expect(screen.getByText(/414,040/)).toBeInTheDocument();
      expect(screen.getByText('99.85%')).toBeInTheDocument();
      expect(screen.getByText('Biển báo hiệu đường bộ (QCVN 41)')).toBeInTheDocument();
      expect(screen.getByText('Cột biển báo & Gương cầu lồi')).toBeInTheDocument();
    });
  });

  it('5. Switches clustering mode toggle to request individual points via /geo', async () => {
    renderComponent();

    const switchBtn = screen.getByRole('switch');
    expect(switchBtn).toBeInTheDocument();

    // Toggle off clustering
    fireEvent.click(switchBtn);

    await waitFor(() => {
      expect(gisApi.fetchGeoData).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(screen.getByText('2 đối tượng hiển thị')).toBeInTheDocument();
    });
  });

  it('6. treats a valid empty bbox result as empty state and does not toast an error', async () => {
    vi.mocked(gisApi.fetchSpatialClusters).mockResolvedValue({ type: 'FeatureCollection', features: [] });
    const errorToast = vi.spyOn(message, 'error').mockImplementation(() => ({
      then: () => undefined,
    }) as never);

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Không có đối tượng phù hợp trong khung nhìn và bộ lọc hiện tại.')).toBeInTheDocument();
    });
    expect(errorToast).not.toHaveBeenCalled();
    errorToast.mockRestore();
  });

  it('7. deduplicates repeated layer errors while keeping retry available', async () => {
    vi.mocked(gisApi.fetchSpatialClusters).mockRejectedValue(new Error('internal SQL and node_modules path'));
    const errorToast = vi.spyOn(message, 'error').mockImplementation(() => ({
      then: () => undefined,
    }) as never);

    renderComponent();
    await waitFor(() => {
      expect(screen.getByText('Không thể tải lớp dữ liệu. Vui lòng thử lại.')).toBeInTheDocument();
    });
    expect(screen.queryByText(/internal SQL|node_modules/i)).not.toBeInTheDocument();
    expect(errorToast).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    await waitFor(() => expect(gisApi.fetchSpatialClusters).toHaveBeenCalledTimes(2));
    expect(errorToast).toHaveBeenCalledTimes(1);
    errorToast.mockRestore();
  });

  it('8. aborts an in-flight layer request when a text filter changes', async () => {
    let firstSignal: AbortSignal | undefined;
    let resolveFirst: ((value: gisApi.GeoJsonFeatureCollection) => void) | undefined;
    const firstRequest = new Promise<gisApi.GeoJsonFeatureCollection>((resolve) => {
      resolveFirst = resolve;
    });
    vi.mocked(gisApi.fetchSpatialClusters)
      .mockImplementationOnce((_dataset, _params, options) => {
        firstSignal = options?.signal;
        return firstRequest;
      })
      .mockResolvedValueOnce(mockClusterData);

    renderComponent();
    await waitFor(() => expect(gisApi.fetchSpatialClusters).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByPlaceholderText('Tìm kiếm mã, tên tài sản...'), {
      target: { value: 'QL.1' },
    });

    await waitFor(() => expect(firstSignal?.aborted).toBe(true));
    resolveFirst?.(mockClusterData);
    expect(gisApi.fetchSpatialClusters).toHaveBeenCalledTimes(1);
  });
});
