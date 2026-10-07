import React from 'react';
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuthProvider, useAuth } from '../context/AuthContext';

// Helper component to test useAuth hooks
const TestAuthConsumer: React.FC<{
  onState?: (state: ReturnType<typeof useAuth>) => void;
}> = ({ onState }) => {
  const auth = useAuth();
  if (onState) onState(auth);

  return (
    <div>
      <span data-testid="auth-status">{auth.isAuthenticated ? 'LOGGED_IN' : 'GUEST'}</span>
      <span data-testid="user-role">{auth.user?.role || 'NONE'}</span>
      <span data-testid="is-admin">{auth.hasRole('ROLE_ADMIN') ? 'YES_ADMIN' : 'NO_ADMIN'}</span>
      <span data-testid="is-editor">{auth.hasRole(['ROLE_EDITOR', 'ROLE_MANAGER']) ? 'YES_EDITOR' : 'NO_EDITOR'}</span>
      <span data-testid="has-asset-read">{auth.hasPermission('asset', 'read') ? 'YES_READ' : 'NO_READ'}</span>
      <span data-testid="has-user-manage">{auth.hasPermission('user', 'manage') ? 'YES_MANAGE' : 'NO_MANAGE'}</span>
    </div>
  );
};

describe('AuthContext RBAC & Permissions Unit Tests', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('initializes with unauthenticated guest state when storage is empty', async () => {
    render(
      <AuthProvider>
        <TestAuthConsumer />
      </AuthProvider>
    );

    expect(screen.getByTestId('auth-status')).toHaveTextContent('GUEST');
    expect(screen.getByTestId('is-admin')).toHaveTextContent('NO_ADMIN');
  });

  it('correctly validates ADMIN role and wildcard permissions', async () => {
    const adminUser = {
      id: 1,
      username: 'admin',
      email: 'admin@drvn.gov.vn',
      fullName: 'Quản trị viên Hệ thống',
      role: 'ROLE_ADMIN',
      roleName: 'Quản trị viên',
      permissions: ['*'],
    };

    localStorage.setItem('kcht_access_token', 'mock_admin_token');
    localStorage.setItem('kcht_user_profile', JSON.stringify(adminUser));

    render(
      <AuthProvider>
        <TestAuthConsumer />
      </AuthProvider>
    );

    expect(screen.getByTestId('is-admin')).toHaveTextContent('YES_ADMIN');
    expect(screen.getByTestId('has-asset-read')).toHaveTextContent('YES_READ');
    expect(screen.getByTestId('has-user-manage')).toHaveTextContent('YES_MANAGE');
  });

  it('correctly validates EDITOR permissions without granting admin permissions', async () => {
    const editorUser = {
      id: 3,
      username: 'editor_demo',
      email: 'editor@drvn.gov.vn',
      fullName: 'Cán bộ Kỹ thuật',
      role: 'ROLE_EDITOR',
      roleName: 'Cán bộ nghiệp vụ',
      permissions: ['asset:read', 'asset:create', 'asset:update'],
    };

    localStorage.setItem('kcht_access_token', 'mock_editor_token');
    localStorage.setItem('kcht_user_profile', JSON.stringify(editorUser));

    render(
      <AuthProvider>
        <TestAuthConsumer />
      </AuthProvider>
    );

    expect(screen.getByTestId('is-admin')).toHaveTextContent('NO_ADMIN');
    expect(screen.getByTestId('is-editor')).toHaveTextContent('YES_EDITOR');
    expect(screen.getByTestId('has-asset-read')).toHaveTextContent('YES_READ');
    expect(screen.getByTestId('has-user-manage')).toHaveTextContent('NO_MANAGE');
  });
});
