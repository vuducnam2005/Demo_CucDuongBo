import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TrafficPage } from '../pages/TrafficPage';
import { fetchTrafficStations, fetchTrafficSummary } from '../services/trafficApi';

vi.mock('../services/trafficApi', () => ({
  fetchTrafficStations: vi.fn(),
  fetchTrafficSummary: vi.fn(),
}));

const renderPage = () => render(<QueryClientProvider client={new QueryClient({
  defaultOptions: { queries: { retry: false } },
})}><TrafficPage /></QueryClientProvider>);

describe('TrafficPage', () => {
  it('shows station counts and real classification from the API response', async () => {
    vi.mocked(fetchTrafficStations).mockResolvedValue([{ stationCode: 'DEMO-TRAFFIC-01',
      stationName: 'Trạm demo', routeName: 'Tuyến demo', isDemo: true,
      sourceSystem: 'DEMO', segmentLengthKm: null }]);
    vi.mocked(fetchTrafficSummary).mockResolvedValue({
      station: { stationCode: 'DEMO-TRAFFIC-01', stationName: 'Trạm demo',
        routeName: 'Tuyến demo', isDemo: true, sourceSystem: 'DEMO', segmentLengthKm: null },
      totalVehicles: 78, classes: [{ vehicleClass: 'Ô tô con', count: 78 }],
      rangeStart: '2026-10-07T08:00:00+07:00', rangeEnd: '2026-10-07T09:00:00+07:00',
      intervals: [{ start: '2026-10-07T08:00:00+07:00',
        end: '2026-10-07T08:15:00+07:00', count: 78 }],
      density: null,
    });
    renderPage();
    expect(await screen.findByText('Ô tô con')).toBeInTheDocument();
    expect(screen.getAllByText('78').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Số liệu mô phỏng')).toBeInTheDocument();
    expect(fetchTrafficSummary).toHaveBeenCalledWith('DEMO-TRAFFIC-01', undefined, undefined);
  });

  it('does not request counts when the branch has no station', async () => {
    vi.mocked(fetchTrafficStations).mockResolvedValue([]);
    vi.mocked(fetchTrafficSummary).mockClear();
    renderPage();
    expect(await screen.findByText('Chưa có trạm trong phạm vi đơn vị')).toBeInTheDocument();
    expect(fetchTrafficSummary).not.toHaveBeenCalled();
  });

  it('shows measured occupancy separately from counts for an imported station', async () => {
    const station = { stationCode: 'SURVEY-12', stationName: 'Điểm khảo sát',
      routeName: 'Tuyến 12', isDemo: false, sourceSystem: 'UPLOADED', segmentLengthKm: 2 };
    vi.mocked(fetchTrafficStations).mockResolvedValue([station]);
    vi.mocked(fetchTrafficSummary).mockResolvedValue({ station, totalVehicles: 0,
      rangeStart: '2026-10-07T08:00:00+07:00', rangeEnd: '2026-10-07T09:00:00+07:00',
      classes: [], intervals: [], density: { measuredAt: '2026-10-07T08:30:00+07:00',
        presentVehicles: 10, vehiclesPerKm: 5, classes: [{ vehicleClass: 'Ô tô con', count: 10 }] } });
    renderPage();
    expect(await screen.findByText('Số liệu nhập — chờ đối soát')).toBeInTheDocument();
    expect(await screen.findByText('Xe/km')).toBeInTheDocument();
    expect(screen.getByText('Ô tô con: 10')).toBeInTheDocument();
  });
});
