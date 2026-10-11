import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Alert, ConfigProvider, App as AntdApp } from 'antd';
import viVN from 'antd/locale/vi_VN';
import { appTheme } from './theme/themeConfig';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ProtectedRoute, PublicOnlyRoute } from './components/RouteGuard';
import { MainLayout } from './layouts/MainLayout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { RegionDashboardPage } from './pages/RegionDashboardPage';
import { AssetListPage } from './pages/AssetListPage';
import { WebGisPage } from './pages/WebGisPage';
import { VroadSurveyMapPage } from './pages/VroadSurveyMapPage';
import { VroadCasesPage } from './pages/VroadCasesPage';
import { TrafficPage } from './pages/TrafficPage';
import { TrafficImportPage } from './pages/TrafficImportPage';
import { VroadInboundPage } from './pages/VroadInboundPage';
import { ReportIndexPage } from './pages/ReportIndexPage';
import { DocumentExplorerPage } from './pages/DocumentExplorerPage';
import { CatalogListPage } from './pages/CatalogListPage';
import { UserManagementPage } from './pages/UserManagementPage';
import { RouteAssignmentsPage } from './pages/RouteAssignmentsPage';
import { OperationsDashboardPage } from './pages/OperationsDashboardPage';
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

const clearSessionQueries = () => queryClient.clear();

const DashboardLanding = () => {
  const { user } = useAuth();
  if (user?.role === 'ROLE_ADMIN') return <DashboardPage />;
  if (user?.branchId) return <RegionDashboardPage />;
  return <Alert type="warning" showIcon message="Tài khoản chưa được gán đơn vị"
    description="Chỉ tài khoản Cục mới có quyền toàn quốc. Liên hệ quản trị để cấp phạm vi đơn vị trước khi tra cứu dữ liệu." />;
};

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider locale={viVN} theme={appTheme}>
        <AntdApp>
          <AuthProvider onSessionChange={clearSessionQueries}>
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
                  <Route path="dashboard" element={<DashboardLanding />} />
                  <Route path="assets" element={<AssetListPage />} />
                  <Route path="green-trees" element={
                    <AssetListPage
                      fixedDatasetKey="green_trees"
                      pageTitle="Quản lý Cây xanh"
                      pageDescription="Quản lý riêng hồ sơ cây xanh dọc tuyến; dữ liệu cây không còn hiển thị trong mục Tài sản."
                      createButtonLabel="Thêm cây xanh"
                      emptyTitle="Chưa có dữ liệu cây xanh"
                      emptyDescription="Hãy thêm mới hoặc nhập tệp CSV/JSON để bắt đầu quản lý cây xanh."
                      showMapAction={false}
                    />
                  } />
                  <Route path="other-assets" element={
                    <AssetListPage
                      fixedDatasetKey="other_assets"
                      pageTitle="Quản lý Tài sản khác"
                      pageDescription="Quản lý các tài sản chưa thuộc những nhóm chuyên biệt trong hệ thống."
                      createButtonLabel="Thêm tài sản khác"
                      emptyTitle="Chưa có dữ liệu tài sản khác"
                      emptyDescription="Phân hệ đã sẵn sàng đầy đủ chức năng. Hãy thêm mới hoặc nhập dữ liệu để bắt đầu."
                      showMapAction={false}
                    />
                  } />
                  <Route path="map" element={<VroadSurveyMapPage />} />
                  <Route path="map/assets" element={<ProtectedRoute requiredRoles={['ROLE_ADMIN']}><WebGisPage /></ProtectedRoute>} />
                  <Route path="cases" element={<VroadCasesPage />} />
                  <Route path="traffic" element={<TrafficPage />} />
                  <Route path="reports" element={<ReportIndexPage />} />
                  <Route path="documents" element={<DocumentExplorerPage />} />
                  <Route path="catalogs" element={<CatalogListPage />} />

                  {/* Admin Protected Routes (Requires ROLE_ADMIN) */}
                  <Route path="admin/operations" element={
                    <ProtectedRoute requiredRoles={['ROLE_ADMIN']}><OperationsDashboardPage /></ProtectedRoute>
                  } />
                  <Route path="admin/routes" element={
                    <ProtectedRoute requiredRoles={['ROLE_ADMIN']}><RouteAssignmentsPage /></ProtectedRoute>
                  } />
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
                  <Route path="admin/vroad-inbound" element={
                    <ProtectedRoute requiredRoles={['ROLE_ADMIN']}><VroadInboundPage /></ProtectedRoute>
                  } />
                  <Route path="admin/traffic-import" element={
                    <ProtectedRoute requiredRoles={['ROLE_ADMIN']}><TrafficImportPage /></ProtectedRoute>
                  } />
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
