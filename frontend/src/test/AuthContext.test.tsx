import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient } from '@tanstack/react-query';
import * as authApi from '../services/authApi';
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
      <span data-testid="auth-loading">{auth.isLoading ? 'LOADING' : 'READY'}</span>
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
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('clears account-specific query data when the session expires', () => {
    vi.spyOn(authApi, 'getMeApi').mockImplementation(() => new Promise(() => {}));
    const client = new QueryClient();
    client.setQueryData(['private-region-records'], ['previous-account-only']);
    render(<AuthProvider onSessionChange={() => client.clear()}><TestAuthConsumer /></AuthProvider>);
    expect(client.getQueryData(['private-region-records'])).toEqual(['previous-account-only']);
    fireEvent(window, new Event('auth:expired'));
    expect(client.getQueryData(['private-region-records'])).toBeUndefined();
    expect(screen.getByTestId('auth-status')).toHaveTextContent('GUEST');
  });

  it('initializes with unauthenticated guest state when storage is empty', async () => {
    const getMeSpy = vi.spyOn(authApi, 'getMeApi');

    render(
      <AuthProvider>
        <TestAuthConsumer />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId('auth-loading')).toHaveTextContent('READY'));
    expect(getMeSpy).not.toHaveBeenCalled();
    expect(screen.getByTestId('auth-status')).toHaveTextContent('GUEST');
    expect(screen.getByTestId('is-admin')).toHaveTextContent('NO_ADMIN');
  });

  it('recovers a known refresh session when the access token is missing', async () => {
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
    localStorage.setItem('kcht_refresh_session', 'active');
    vi.spyOn(authApi, 'getMeApi').mockImplementation(async () => {
      localStorage.setItem('kcht_access_token', 'refreshed_token');
      return viewerUser;
    });

    render(
      <AuthProvider>
        <TestAuthConsumer />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId('auth-status')).toHaveTextContent('LOGGED_IN'));
    expect(authApi.getMeApi).toHaveBeenCalledTimes(1);
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
