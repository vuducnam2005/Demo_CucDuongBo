import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { TrafficImportPage } from '../pages/TrafficImportPage';
import { submitTrafficImport } from '../services/trafficApi';

vi.mock('../services/trafficApi', () => ({ submitTrafficImport: vi.fn() }));

const renderPage = () => render(<MemoryRouter><QueryClientProvider client={new QueryClient({
  defaultOptions: { mutations: { retry: false } },
})}><TrafficImportPage /></QueryClientProvider></MemoryRouter>);

describe('TrafficImportPage', () => {
  it('requires valid JSON and refuses the placeholder station', () => {
    vi.mocked(submitTrafficImport).mockClear();
    renderPage();
    const input = screen.getByLabelText('Nội dung giao thông JSON');
    fireEvent.change(input, { target: { value: '{invalid' } });
    fireEvent.click(screen.getByRole('button', { name: 'Nhập vào bản demo' }));
    expect(screen.getByText(/Hãy chọn JSON hợp lệ/)).toBeInTheDocument();
    fireEvent.change(input, { target: { value: JSON.stringify({ requestKey: 'test-batch-1',
      sourceSystem: 'LOCAL_UPLOAD', station: { code: 'EXAMPLE-TRAFFIC-01' },
      counts: [{}], snapshots: [] }) } });
    fireEvent.click(screen.getByRole('button', { name: 'Nhập vào bản demo' }));
    expect(submitTrafficImport).not.toHaveBeenCalled();
  });

  it('imports an explicit JSON payload and shows a link to the charts', async () => {
    vi.mocked(submitTrafficImport).mockResolvedValue({ batchId: 9, requestKey: 'test-batch-2',
      stationCode: 'SITE-01', importedCounts: 1, importedSnapshots: 1 });
    renderPage();
    fireEvent.change(screen.getByLabelText('Nội dung giao thông JSON'), {
      target: { value: JSON.stringify({ requestKey: 'test-batch-2', sourceSystem: 'LOCAL_UPLOAD',
        station: { code: 'SITE-01', name: 'Trạm', routeName: 'Tuyến', branchId: 'kqldb_1',
          segmentLengthKm: 2.5 }, counts: [{}], snapshots: [{}] }) },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Nhập vào bản demo' }));
    expect(await screen.findByText('Đã nhập 1 phép đếm và 1 lát cắt')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Xem biểu đồ' })).toBeInTheDocument();
    expect(submitTrafficImport).toHaveBeenCalledTimes(1);
  });
});
