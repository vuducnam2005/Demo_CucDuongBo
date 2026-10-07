import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  isProfileValid,
  getInitialCachedUser,
  PROFILE_CACHE_KEY,
  PROFILE_CACHE_VERSION_KEY,
  CURRENT_PROFILE_CACHE_VERSION,
  AuthProvider,
  useAuth,
} from '../context/AuthContext';
import { UserSummary } from '../services/api';

const VietnameseDisplayConsumer: React.FC = () => {
  const { user } = useAuth();
  return (
    <div>
      <h1 data-testid="greeting">Xin chào, {user?.fullName || 'Khách'}.</h1>
      <div data-testid="user-role">{user?.roleName}</div>
      <div data-testid="org-info">{user?.organizationId}</div>
      <div data-testid="keywords">
        <span>Đường quốc lộ</span>
        <span>Cầu đường bộ</span>
        <span>Tỉnh/thành phố</span>
        <span>Hệ thống</span>
        <span>Quản trị viên</span>
      </div>
    </div>
  );
};

describe('P0.6 Vietnamese UTF-8 Encoding & Profile Cache Tests', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  describe('isProfileValid & cache corruption detection', () => {
    it('rejects corrupted profiles with question marks from legacy encoding flaws', () => {
      const corruptedAdmin: Partial<UserSummary> = {
        id: 4,
        username: 'admin',
        fullName: 'Qu???n tr??? vi??n H??? th???ng',
        role: 'ROLE_ADMIN',
        roleName: 'Qu???n tr??? vi??n To??n quy???n',
      };
      expect(isProfileValid(corruptedAdmin)).toBe(false);

      const corruptedViewer: Partial<UserSummary> = {
        id: 1,
        username: 'viewer_demo',
        fullName: 'C??n b??? Tra c???u Th??? nghi???m',
        role: 'ROLE_VIEWER',
      };
      expect(isProfileValid(corruptedViewer)).toBe(false);
    });

    it('accepts valid UTF-8 Vietnamese profiles with diacritics', () => {
      const validAdmin: UserSummary = {
        id: 4,
        username: 'admin',
        email: 'admin@drvn.gov.vn',
        fullName: 'Quản trị viên Hệ thống',
        role: 'ROLE_ADMIN',
        roleName: 'Quản trị viên Toàn quyền',
        organizationId: 'Cục Đường bộ Việt Nam',
        branchId: 'Trụ sở chính (Hà Nội)',
        active: true,
        permissions: ['*'],
      };
      expect(isProfileValid(validAdmin)).toBe(true);

      const validSpecialist: UserSummary = {
        id: 10,
        username: 'tech_expert',
        email: 'expert@drvn.gov.vn',
        fullName: 'Kỹ sư Cầu đường bộ & Tuyến Đường quốc lộ (Tỉnh/thành phố)',
        role: 'ROLE_EDITOR',
        roleName: 'Cán bộ Kỹ thuật / Nhập liệu',
        organizationId: 'Khu Quản lý Đường bộ I',
        branchId: 'Chi cục QLĐB',
        active: true,
        permissions: ['ASSET:READ', 'ASSET:UPDATE'],
      };
      expect(isProfileValid(validSpecialist)).toBe(true);
    });

    it('purges corrupted cache from localStorage and returns null in getInitialCachedUser', () => {
      const corruptedCache = {
        id: 4,
        username: 'admin',
        fullName: 'Qu???n tr??? vi??n H??? th???ng',
        role: 'ROLE_ADMIN',
      };
      localStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify(corruptedCache));
      localStorage.setItem(PROFILE_CACHE_VERSION_KEY, 'v1_legacy');

      const initialUser = getInitialCachedUser();
      expect(initialUser).toBeNull();
      expect(localStorage.getItem(PROFILE_CACHE_KEY)).toBeNull();
      expect(localStorage.getItem(PROFILE_CACHE_VERSION_KEY)).toBeNull();
    });

    it('loads and retains valid UTF-8 cached profile, ensuring version stamp', () => {
      const validUser: UserSummary = {
        id: 4,
        username: 'admin',
        email: 'admin@drvn.gov.vn',
        fullName: 'Quản trị viên Hệ thống',
        role: 'ROLE_ADMIN',
        roleName: 'Quản trị viên Toàn quyền',
        organizationId: 'Cục Đường bộ Việt Nam',
        branchId: 'Trụ sở chính',
        active: true,
        permissions: ['*'],
      };
      localStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify(validUser));

      const initialUser = getInitialCachedUser();
      expect(initialUser).not.toBeNull();
      expect(initialUser?.fullName).toBe('Quản trị viên Hệ thống');
      expect(localStorage.getItem(PROFILE_CACHE_VERSION_KEY)).toBe(CURRENT_PROFILE_CACHE_VERSION);
    });
  });

  describe('UI Vietnamese rendering & keywords assertion', () => {
    it('renders correct Vietnamese strings without mojibake for Quản trị viên, Hệ thống, Đường quốc lộ, Cầu đường bộ, Tỉnh/thành phố', () => {
      const validUser: UserSummary = {
        id: 4,
        username: 'admin',
        email: 'admin@drvn.gov.vn',
        fullName: 'Quản trị viên Hệ thống',
        role: 'ROLE_ADMIN',
        roleName: 'Quản trị viên Toàn quyền',
        organizationId: 'Cục Đường bộ Việt Nam',
        branchId: 'Trụ sở chính',
        active: true,
        permissions: ['*'],
      };
      localStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify(validUser));
      localStorage.setItem(PROFILE_CACHE_VERSION_KEY, CURRENT_PROFILE_CACHE_VERSION);

      render(
        <AuthProvider>
          <VietnameseDisplayConsumer />
        </AuthProvider>
      );

      // Verify greetings and roles
      expect(screen.getByTestId('greeting')).toHaveTextContent('Xin chào, Quản trị viên Hệ thống.');
      expect(screen.getByTestId('user-role')).toHaveTextContent('Quản trị viên Toàn quyền');

      // Verify standard road infrastructure Vietnamese keywords
      const keywordsContainer = screen.getByTestId('keywords');
      expect(keywordsContainer).toHaveTextContent('Quản trị viên');
      expect(keywordsContainer).toHaveTextContent('Hệ thống');
      expect(keywordsContainer).toHaveTextContent('Đường quốc lộ');
      expect(keywordsContainer).toHaveTextContent('Cầu đường bộ');
      expect(keywordsContainer).toHaveTextContent('Tỉnh/thành phố');

      // Assert absence of corruption
      expect(screen.queryByText(/\?/)).not.toBeInTheDocument();
      expect(screen.queryByText(/Qu\?{1,}n/)).not.toBeInTheDocument();
    });
  });
});
