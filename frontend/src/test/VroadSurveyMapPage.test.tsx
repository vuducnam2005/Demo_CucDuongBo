import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App } from 'antd';
import { VroadSurveyMapPage } from '../pages/VroadSurveyMapPage';
import * as vroadApi from '../services/vroadApi';
import * as authContext from '../context/AuthContext';

vi.mock('../services/vroadApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/vroadApi')>();
  return {
    ...actual,
    fetchVroadPoints: vi.fn(),
    fetchVroadAssets: vi.fn(),
    fetchVroadAsset: vi.fn(),
    fetchVroadDefect: vi.fn(),
    fetchRoadContext: vi.fn(),
  };
});

describe('VroadSurveyMapPage Tests', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    vi.spyOn(authContext, 'useAuth').mockReturnValue({
      user: {
        id: 1,
        username: 'admin',
        fullName: 'Admin User',
        role: 'ROLE_ADMIN',
        branchId: null,
      },
      isAuthenticated: true,
      loading: false,
      login: vi.fn(),
      logout: vi.fn(),
      hasRole: () => true,
    } as unknown as ReturnType<typeof authContext.useAuth>);

    vi.mocked(vroadApi.fetchVroadPoints).mockResolvedValue({ content: [], truncated: false });
    vi.mocked(vroadApi.fetchVroadAssets).mockResolvedValue({ content: [], truncated: false });
  });

  const renderComponent = (initialEntries = ['/map']) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <ConfigProvider>
          <App>
            <MemoryRouter initialEntries={initialEntries}>
              <VroadSurveyMapPage />
            </MemoryRouter>
          </App>
        </ConfigProvider>
      </QueryClientProvider>
    );
  };

  it('renders map container, layer controls, and basemap selector with default Google Maps', async () => {
    renderComponent();

    expect(screen.getByText('Bản đồ hư hỏng VroadAI')).toBeInTheDocument();
    expect(screen.getByTestId('vroad-map')).toBeInTheDocument();
    expect(screen.getByText('Nền bản đồ:')).toBeInTheDocument();
    expect(screen.getAllByLabelText('Chọn nền bản đồ').length).toBeGreaterThan(0);
    expect(screen.getByText(/Google Bản đồ/)).toBeInTheDocument();
  });

  it('renders right panel when asset is loaded', async () => {
    vi.mocked(vroadApi.fetchVroadAsset).mockResolvedValue({
      recordId: 3,
      recordKey: 'MAST-038736',
      assetType: 'Cây',
      category: 'Làm đẹp',
      latitude: 11.216864,
      longitude: 108.642033,
      routeName: 'QL.1',
      chainage: '1622+400',
      routeSide: 'RHS',
      condition: 'Tốt',
      assetSide: 'LHS',
      surveyDate: '2026-08-15',
      sourceImageUrl: null,
      roadCatalog: null,
      relatedDefects: [],
      relatedDefectsTruncated: false,
    });

    renderComponent(['/map?assetRecordId=3']);

    await waitFor(() => {
      expect(vroadApi.fetchVroadAsset).toHaveBeenCalledWith(3);
    });

    await waitFor(() => {
      expect(screen.getByText('Thông tin tại tọa độ')).toBeInTheDocument();
    });
  });
});
