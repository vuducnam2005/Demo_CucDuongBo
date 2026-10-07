import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ProtectedRoute } from '../components/RouteGuard';
import * as authApi from '../services/authApi';

vi.mock('../services/authApi', () => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
  getMeApi: vi.fn(),
}));

describe('RouteGuard Unit Tests', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    vi.mocked(authApi.getMeApi).mockRejectedValue({ response: { status: 401 } });
  });

  it('redirects unauthenticated user from protected route to /login', async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/dashboard']}>
          <Routes>
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <div>Secret Dashboard Content</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div>Login Page Target</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(await screen.findByText('Login Page Target')).toBeInTheDocument();
    expect(screen.queryByText('Secret Dashboard Content')).not.toBeInTheDocument();
  });

  it('allows access to protected route when user is authenticated', async () => {
    const viewerUser = {
      id: 4,
      username: 'viewer_demo',
      email: 'viewer@drvn.gov.vn',
      fullName: 'Người xem',
      role: 'ROLE_VIEWER',
      roleName: 'Người xem',
      active: true,
      permissions: ['asset:read'],
    };

    localStorage.setItem('kcht_access_token', 'valid_token');
    localStorage.setItem('kcht_user_profile', JSON.stringify(viewerUser));
    vi.mocked(authApi.getMeApi).mockResolvedValue(viewerUser);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/dashboard']}>
          <Routes>
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <div>Authenticated Dashboard View</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(await screen.findByText('Authenticated Dashboard View')).toBeInTheDocument();
  });

  it('blocks access and displays 403 Forbidden when user does not have required role', async () => {
    const viewerUser = {
      id: 4,
      username: 'viewer_demo',
      email: 'viewer@drvn.gov.vn',
      fullName: 'Người xem',
      role: 'ROLE_VIEWER',
      roleName: 'Người xem',
      active: true,
      permissions: ['asset:read'],
    };

    localStorage.setItem('kcht_access_token', 'valid_token');
    localStorage.setItem('kcht_user_profile', JSON.stringify(viewerUser));
    vi.mocked(authApi.getMeApi).mockResolvedValue(viewerUser);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/admin/users']}>
          <Routes>
            <Route
              path="/admin/users"
              element={
                <ProtectedRoute requiredRoles={['ROLE_ADMIN']}>
                  <div>Admin Secret Area</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(await screen.findByText(/403 - Không có quyền truy cập/i)).toBeInTheDocument();
    expect(screen.queryByText('Admin Secret Area')).not.toBeInTheDocument();
  });
});
