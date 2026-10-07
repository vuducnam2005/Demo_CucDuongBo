import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App as AntdApp } from 'antd';
import viVN from 'antd/locale/vi_VN';
import { appTheme } from './theme/themeConfig';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute, PublicOnlyRoute } from './components/RouteGuard';
import { MainLayout } from './layouts/MainLayout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { AssetListPage } from './pages/AssetListPage';
import { WebGisPage } from './pages/WebGisPage';
import { ReportIndexPage } from './pages/ReportIndexPage';
import { DocumentExplorerPage } from './pages/DocumentExplorerPage';
import { CatalogListPage } from './pages/CatalogListPage';
import { UserManagementPage } from './pages/UserManagementPage';
import { AuditLogPage } from './pages/AuditLogPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { shouldRetryQuery } from './services/api';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: shouldRetryQuery,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: false,
    },
  },
});

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider locale={viVN} theme={appTheme}>
        <AntdApp>
          <AuthProvider>
            <BrowserRouter>
              <Routes>
                {/* Public Route: Login */}
                <Route
                  path="/login"
                  element={
                    <PublicOnlyRoute>
                      <LoginPage />
                    </PublicOnlyRoute>
                  }
                />

                {/* Protected Shell Layout Routes */}
                <Route
                  path="/"
                  element={
                    <ProtectedRoute>
                      <MainLayout />
                    </ProtectedRoute>
                  }
                >
                  <Route index element={<Navigate to="/dashboard" replace />} />
                  <Route path="dashboard" element={<DashboardPage />} />
                  <Route path="assets" element={<AssetListPage />} />
                  <Route path="map" element={<WebGisPage />} />
                  <Route path="reports" element={<ReportIndexPage />} />
                  <Route path="documents" element={<DocumentExplorerPage />} />
                  <Route path="catalogs" element={<CatalogListPage />} />

                  {/* Admin Protected Routes (Requires ROLE_ADMIN) */}
                  <Route
                    path="admin/users"
                    element={
                      <ProtectedRoute requiredRoles={['ROLE_ADMIN']}>
                        <UserManagementPage />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="admin/audit-logs"
                    element={
                      <ProtectedRoute requiredRoles={['ROLE_ADMIN']}>
                        <AuditLogPage />
                      </ProtectedRoute>
                    }
                  />
                </Route>

                {/* 404 Catch-All */}
                <Route path="*" element={<NotFoundPage />} />
              </Routes>
            </BrowserRouter>
          </AuthProvider>
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>
  );
};
