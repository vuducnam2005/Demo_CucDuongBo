import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { MainLayout } from '../layouts/MainLayout';
import { useAuth } from '../context/AuthContext';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

const adminUser = {
  id: 1,
  username: 'admin',
  email: 'admin@drvn.gov.vn',
  fullName: 'Quản trị viên Hệ thống',
  role: 'ROLE_ADMIN',
  roleName: 'Quản trị hệ thống',
  active: true,
  permissions: ['*'],
};

const managerUser = {
  id: 2,
  username: 'manager',
  email: 'manager@drvn.gov.vn',
  fullName: 'Lãnh đạo đơn vị',
  role: 'ROLE_MANAGER',
  roleName: 'Lãnh đạo',
  active: true,
  permissions: ['asset:read'],
};

const setAuthenticatedUser = (user: typeof adminUser | typeof managerUser) => {
  vi.mocked(useAuth).mockReturnValue({
    user,
    token: 'test-token',
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
    hasRole: (roles) => {
      const accepted = Array.isArray(roles) ? roles : [roles];
      return accepted.some((role) => role.toUpperCase() === user.role.toUpperCase());
    },
    hasPermission: vi.fn(() => false),
    refreshUserProfile: vi.fn(),
  });
};

const renderLayout = (initialRoute: string) => render(
  <ConfigProvider theme={{ hashed: false }}>
    <App>
      <MemoryRouter initialEntries={[initialRoute]}>
        <Routes>
          <Route path="/" element={<MainLayout />}>
            <Route path="dashboard" element={<div>Nội dung dashboard</div>} />
            <Route path="admin/users" element={<div>Nội dung quản lý người dùng</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </App>
  </ConfigProvider>
);

describe('MainLayout route, breadcrumb, and permission synchronization', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: query.includes('min-width'),
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }),
    });
  });

  it('does not render the administration group for a manager', () => {
    setAuthenticatedUser(managerUser);
    renderLayout('/dashboard');

    expect(screen.getByText('Nội dung dashboard')).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: /Quản trị hệ thống/i })).not.toBeInTheDocument();
    expect(screen.queryByText('Quản lý người dùng')).not.toBeInTheDocument();
  });

  it('restores selected and expanded menu state after refreshing an admin URL', () => {
    setAuthenticatedUser(adminUser);
    const { container } = renderLayout('/admin/users');

    expect(screen.getByText('Nội dung quản lý người dùng')).toBeInTheDocument();

    const selectedItem = container.querySelector('.ant-menu-item-selected');
    expect(selectedItem).not.toBeNull();
    expect(within(selectedItem as HTMLElement).getByText('Quản lý người dùng')).toBeInTheDocument();

    const adminGroup = container.querySelector('.ant-menu-submenu-open');
    expect(adminGroup).not.toBeNull();
    expect(within(adminGroup as HTMLElement).getByText('Quản trị hệ thống')).toBeInTheDocument();
  });

  it('shows the full admin breadcrumb without creating a broken /admin link', () => {
    setAuthenticatedUser(adminUser);
    const { container } = renderLayout('/admin/users');

    const breadcrumb = container.querySelector('.ant-breadcrumb');
    expect(breadcrumb).not.toBeNull();
    expect(within(breadcrumb as HTMLElement).getByText('Trang chủ')).toHaveAttribute('href', '/dashboard');
    expect(within(breadcrumb as HTMLElement).getByText('Quản trị hệ thống')).not.toHaveAttribute('href');
    expect(within(breadcrumb as HTMLElement).getByText('Quản lý người dùng')).not.toHaveAttribute('href');
    expect(container.querySelector('a[href="/admin"]')).not.toBeInTheDocument();
  });
});
