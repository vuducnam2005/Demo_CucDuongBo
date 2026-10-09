import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RegionDashboardPage } from '../pages/RegionDashboardPage';
import { apiClient } from '../services/api';
import { useAuth } from '../context/AuthContext';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));

const record = {
  id: 42, datasetKey: 'demo_region_assets', recordKey: 'DEMO-REGION-001',
  displayName: 'Biển minh họa', routeName: null, recordStatus: 'RAW_STORED', isDemo: true,
};

const setUser = (role: 'ROLE_EDITOR' | 'ROLE_MANAGER') => {
  vi.mocked(useAuth).mockReturnValue({
    user: { id: 1, username: 'demo', email: '', fullName: 'Demo', role, roleName: 'Demo',
      branchId: 'kqldb_1', active: true, permissions: [] },
    token: 'test', isAuthenticated: true, isLoading: false, login: vi.fn(), logout: vi.fn(),
    hasRole: vi.fn(() => false), hasPermission: vi.fn(() => false), refreshUserProfile: vi.fn(),
  });
};

const renderRegion = (reviewStatus: string) => {
  const get = vi.spyOn(apiClient, 'get').mockImplementation(async (url) => {
    const path = String(url);
    if (path.endsWith('/summary')) return { data: { totalRecords: 2, demoRecords: 2, totalDatasets: 1 } } as never;
    if (path.endsWith('/review')) return { data: { status: reviewStatus, reason: null } } as never;
    if (path.endsWith('/42')) return { data: record } as never;
    return { data: { content: [record], totalElements: 1 } } as never;
  });
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { status: 'IN_REVIEW' } });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={queryClient}><App><MemoryRouter><RegionDashboardPage /></MemoryRouter></App></QueryClientProvider>);
  return { get, post };
};

describe('RegionDashboardPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('separates Excel and demo counts and lets an editor submit a demo record', async () => {
    setUser('ROLE_EDITOR');
    const { post } = renderRegion('CHƯA_GỬI');
    expect(await screen.findByText('DEMO-REGION-001')).toBeInTheDocument();
    expect(screen.getByText('Bản ghi nguồn trong phạm vi')).toBeInTheDocument();
    expect(screen.getByText('Bản ghi MÔ PHỎNG')).toBeInTheDocument();
    fireEvent.click(screen.getByText('DEMO-REGION-001'));
    fireEvent.click(await screen.findByRole('button', { name: 'Gửi quản lý kiểm tra' }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/region/assets/42/submit', undefined));
  });

  it('requires a reason before a manager returns a record', async () => {
    setUser('ROLE_MANAGER');
    const { post } = renderRegion('IN_REVIEW');
    fireEvent.click(await screen.findByText('DEMO-REGION-001'));
    fireEvent.click(await screen.findByRole('button', { name: 'Trả lại' }));
    expect(await screen.findByText('Vui lòng nhập lý do trả lại.')).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', { name: 'Lý do trả lại' }), { target: { value: 'Thiếu minh chứng' } });
    fireEvent.click(screen.getByRole('button', { name: 'Trả lại' }));
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/region/assets/42/decision',
      { decision: 'RETURNED', reason: 'Thiếu minh chứng' }));
  });
});
