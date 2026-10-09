import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import { OperationsDashboardPage } from '../pages/OperationsDashboardPage';
import * as operationsApi from '../services/operationsApi';

vi.mock('../services/operationsApi', () => ({ fetchOperationsSummary: vi.fn() }));

const renderDashboard = () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<MemoryRouter><QueryClientProvider client={client}><ConfigProvider>
    <OperationsDashboardPage />
  </ConfigProvider></QueryClientProvider></MemoryRouter>);
};

describe('OperationsDashboardPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders live counts, scoped documents and seven days of audit activity', async () => {
    vi.mocked(operationsApi.fetchOperationsSummary).mockResolvedValue({
      totalUsers: 4,
      activeUsers: 3,
      datasetCount: 1,
      rawRecords: 33,
      storedDocuments: 2,
      stagedBatches: 1,
      pendingRecords: 3,
      invalidRecords: 1,
      resolvedCases: 1,
      simulatedTrafficObservations: 32,
      importedTrafficObservations: 2,
      trafficDensitySnapshots: 1,
      lastRawImportAt: null,
      calculatedAt: '2026-10-07T12:00:00+07:00',
      datasets: [{ code: 'tbl_sign', label: 'Biển báo', count: 33 }],
      activeRoles: [{ code: 'ROLE_ADMIN', label: 'Quản trị viên', count: 3 }],
      documentBranches: [{ code: 'kqldb_1', label: 'Khu QLĐB I', count: 2 }],
      trafficVehiclesByClass: [{ code: 'DEMO:Xe tải', label: 'Xe tải · mô phỏng', count: 50 },
        { code: 'IMPORTED:Ô tô con', label: 'Ô tô con · nhập', count: 16 }],
      auditActivity: Array.from({ length: 7 }, (_, index) => ({
        day: `2026-10-${String(index + 1).padStart(2, '0')}`, count: index,
      })),
    });

    renderDashboard();
    expect(await screen.findByRole('heading', { name: 'Vận hành hệ thống' })).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: 'Biển báo: 33' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Khu QLĐB I: 2' })).toBeInTheDocument();
    expect(screen.getAllByRole('img', { name: /thao tác$/ })).toHaveLength(7);
    expect(screen.getByText('32 quan sát giao thông mô phỏng')).toBeInTheDocument();
    expect(screen.getByText('2 quan sát giao thông nhập')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Ô tô con · nhập: 16' })).toBeInTheDocument();
    expect(screen.getByText('Lô tiếp nhận chờ đối soát')).toBeInTheDocument();
  });

  it('shows a useful error state when the summary endpoint fails', async () => {
    vi.mocked(operationsApi.fetchOperationsSummary).mockRejectedValue(new Error('Unavailable'));
    renderDashboard();
    expect(await screen.findByText('Không thể tải tình trạng hệ thống', {}, { timeout: 4000 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
  });
});
