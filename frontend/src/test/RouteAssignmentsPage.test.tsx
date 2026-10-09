import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouteAssignmentsPage } from '../pages/RouteAssignmentsPage';
import { fetchUsers } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { fetchAssignedRouteOptions, fetchRouteAssignments, revokeRouteAssignment, saveRouteAssignment } from '../services/routeAssignmentApi';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../services/routeAssignmentApi', () => ({
  fetchAssignedRouteOptions: vi.fn(), fetchRouteAssignments: vi.fn(), revokeRouteAssignment: vi.fn(), saveRouteAssignment: vi.fn(),
}));
vi.mock('../services/api', async (original) => ({ ...(await original<typeof import('../services/api')>()), fetchUsers: vi.fn() }));

const assignment = { id: 9, userId: 2, username: 'editor_demo', fullName: 'Cán bộ demo', branchId: 'kqldb_1',
  routeName: 'Tuyến khảo sát kiểm thử INC', chainageFromM: null, chainageToM: null, purpose: 'DEMO' as const,
  assignedAt: '2026-10-08T09:00:00+07:00' };

const renderPage = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <App><RouteAssignmentsPage /></App></QueryClientProvider>);

describe('Phân tuyến tài khoản', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue({ user: { id: 1, username: 'admin', email: '', fullName: 'Admin', role: 'ROLE_ADMIN',
      roleName: 'Quản trị', active: true, permissions: ['*'] }, token: 'test', isAuthenticated: true, isLoading: false,
      login: vi.fn(), logout: vi.fn(), hasRole: vi.fn(() => true), hasPermission: vi.fn(() => true), refreshUserProfile: vi.fn() });
    vi.mocked(fetchUsers).mockResolvedValue({ content: [{ id: 2, username: 'editor_demo', fullName: 'Cán bộ demo', email: '',
      role: 'ROLE_EDITOR', roleName: 'Nhập liệu', branchId: 'kqldb_1', active: true, permissions: [] }], totalElements: 1,
      totalPages: 1, page: 0, size: 100, first: true, last: true });
    vi.mocked(fetchRouteAssignments).mockResolvedValue({ content: [assignment], totalElements: 1, page: 0, size: 20 });
    vi.mocked(fetchAssignedRouteOptions).mockResolvedValue([{ routeName: assignment.routeName, records: 3 }]);
    vi.mocked(saveRouteAssignment).mockResolvedValue(assignment);
    vi.mocked(revokeRouteAssignment).mockResolvedValue({ data: undefined } as never);
  });

  it('hiển thị tuyến theo nguồn, phạm vi DEMO và quyền nội bộ', async () => {
    renderPage();
    expect(await screen.findByText('editor_demo')).toBeInTheDocument();
    expect(screen.getByText('Dữ liệu nguồn thật, phạm vi quản lý DEMO')).toBeInTheDocument();
    expect(screen.getByText('Toàn tuyến khảo sát')).toBeInTheDocument();
    expect(screen.getByText(assignment.routeName)).toBeInTheDocument();
  });

  it('sửa phân tuyến giữ đúng tài khoản và tên tuyến', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /^Sửa$/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Lưu phân tuyến' }));
    await waitFor(() => expect(saveRouteAssignment).toHaveBeenCalledWith({ userId: 2, routeName: assignment.routeName,
      chainageFromM: null, chainageToM: null }, expect.anything()));
  });

  it('không thu hồi quyền trước khi xác nhận', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /^Thu hồi$/ }));
    expect(revokeRouteAssignment).not.toHaveBeenCalled();
    expect(await screen.findByText('Không xóa hay thay đổi dữ liệu nguồn.')).toBeInTheDocument();
    const confirmations = screen.getAllByRole('button', { name: /^Thu hồi$/ });
    fireEvent.click(confirmations[confirmations.length - 1]);
    await waitFor(() => expect(revokeRouteAssignment).toHaveBeenCalledWith(9, expect.anything()));
  });
});
