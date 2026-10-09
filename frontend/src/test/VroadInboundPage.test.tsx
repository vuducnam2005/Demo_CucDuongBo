import { describe, beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from 'antd';
import { VroadInboundPage } from '../pages/VroadInboundPage';
import {
  fetchInboundBatches,
  fetchInboundDetail,
  stageInboundBatch,
} from '../services/vroadInboundApi';

vi.mock('../services/vroadInboundApi', () => ({
  fetchInboundBatches: vi.fn(),
  fetchInboundDetail: vi.fn(),
  stageInboundBatch: vi.fn(),
}));

const batch = {
  id: 11,
  requestKey: 'demo-batch-011',
  schemaVersion: 'demo-proposal-v1' as const,
  status: 'STAGED' as const,
  receivedAt: Date.now(),
  pending: 0,
  invalid: 1,
};

const renderPage = () =>
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false } },
        })
      }
    >
      <App>
        <VroadInboundPage />
      </App>
    </QueryClientProvider>
  );

describe('VroadInboundPage', () => {
  beforeEach(() => {
    vi.mocked(fetchInboundBatches).mockResolvedValue([batch]);
    vi.mocked(fetchInboundDetail).mockResolvedValue({
      batch,
      records: [
        {
          id: 1,
          sourceRecordKey: 'DEMO-001',
          recordKind: 'DEFECT',
          status: 'INVALID',
          validationErrors: ['Thiếu tọa độ'],
        },
      ],
    });
    vi.mocked(stageInboundBatch).mockClear();
  });

  it('renders saved batches and line-level validation errors', async () => {
    renderPage();
    fireEvent.click(await screen.findByText('demo-batch-011'));
    expect(await screen.findByText('Thiếu tọa độ')).toBeInTheDocument();
    expect(screen.getByText(/chưa tự tạo tài sản/)).toBeInTheDocument();
    expect(screen.getByText('API tiếp nhận bản demo')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Swagger UI/i })).toHaveAttribute('href', '/swagger-ui/index.html');
    expect(screen.getByText('Chưa kết nối với VroadAI')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Kích hoạt Đồng bộ/i })).not.toBeInTheDocument();
  });

  it('accepts only JSON input before calling staging API', async () => {
    renderPage();
    fireEvent.change(screen.getByRole('textbox', { name: 'Nội dung lô JSON' }), {
      target: { value: '{broken' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra và lưu chờ' }));
    expect(stageInboundBatch).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', { name: 'Nội dung lô JSON' }), {
      target: {
        value: JSON.stringify({
          requestKey: 'demo-123',
          schemaVersion: 'demo-proposal-v1',
          records: [],
        }),
      },
    });
    vi.mocked(stageInboundBatch).mockResolvedValue({ batch, records: [] });
    fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra và lưu chờ' }));
    await waitFor(() =>
      expect(stageInboundBatch).toHaveBeenCalledWith(
        expect.objectContaining({ requestKey: 'demo-123' }),
        expect.any(Object)
      )
    );
  });
});
