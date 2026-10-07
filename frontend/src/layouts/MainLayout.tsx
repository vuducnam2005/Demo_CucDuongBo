import React, { useState, useMemo } from 'react';
import {
  Layout,
  Menu,
  Typography,
  Space,
  Button,
  Avatar,
  Dropdown,
  Badge,
  Popover,
  Modal,
  Tag,
  Descriptions,
  Breadcrumb,
  theme,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  DashboardOutlined,
  TableOutlined,
  CompassOutlined,
  BarChartOutlined,
  FolderOpenOutlined,
  AppstoreOutlined,
  SettingOutlined,
  TeamOutlined,
  AuditOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BellOutlined,
  UserOutlined,
  LogoutOutlined,
  IdcardOutlined,
  SafetyCertificateOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ErrorBoundary } from '../components/common/ErrorBoundary';

const { Header, Content, Footer, Sider } = Layout;
const { Title, Text } = Typography;

const routeBreadcrumbMap: Record<string, string[]> = {
  '/dashboard': ['Bảng điều hành'],
  '/assets': ['Danh mục tài sản'],
  '/map': ['Bản đồ số WebGIS'],
  '/reports': ['Báo cáo & Thống kê'],
  '/documents': ['Hồ sơ & Tài liệu'],
  '/catalogs': ['Danh mục chuẩn'],
  '/admin/users': ['Quản trị hệ thống', 'Quản lý người dùng'],
  '/admin/audit-logs': ['Quản trị hệ thống', 'Nhật ký kiểm toán'],
};

const menuRouteKeys = [
  '/dashboard',
  '/assets',
  '/map',
  '/reports',
  '/documents',
  '/catalogs',
  '/admin/users',
  '/admin/audit-logs',
];

export const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [manualOpenKeys, setManualOpenKeys] = useState<string[]>([]);
  const [profileModalVisible, setProfileModalVisible] = useState(false);
  const { user, logout, hasRole } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  // Role display badge color and label
  const roleDisplay = useMemo(() => {
    switch (user?.role?.toUpperCase()) {
      case 'ROLE_ADMIN':
      case 'ADMIN':
        return { label: 'Quản trị viên', color: 'red' };
      case 'ROLE_MANAGER':
      case 'MANAGER':
        return { label: 'Lãnh đạo', color: 'blue' };
      case 'ROLE_EDITOR':
      case 'EDITOR':
        return { label: 'Cán bộ', color: 'green' };
      case 'ROLE_VIEWER':
      case 'VIEWER':
        return { label: 'Người xem', color: 'purple' };
      default:
        return { label: user?.roleName || 'Người dùng', color: 'default' };
    }
  }, [user]);

  // Dynamic hierarchical menu items based on user role
  const menuItems = useMemo(() => {
    const items: MenuProps['items'] = [
      {
        key: '/dashboard',
        icon: <DashboardOutlined />,
        label: 'Bảng điều hành',
      },
      {
        key: '/assets',
        icon: <TableOutlined />,
        label: 'Danh mục tài sản',
      },
      {
        key: '/map',
        icon: <CompassOutlined />,
        label: 'Bản đồ số WebGIS',
      },
      {
        key: '/reports',
        icon: <BarChartOutlined />,
        label: 'Báo cáo & Thống kê',
      },
      {
        key: '/documents',
        icon: <FolderOpenOutlined />,
        label: 'Hồ sơ tài liệu',
      },
      {
        key: '/catalogs',
        icon: <AppstoreOutlined />,
        label: 'Danh mục chuẩn',
      },
    ];

    // Admin section visible only for users with admin role
    if (hasRole('ROLE_ADMIN')) {
      items.push({
        key: 'admin-group',
        icon: <SettingOutlined />,
        label: 'Quản trị hệ thống',
        children: [
          {
            key: '/admin/users',
            icon: <TeamOutlined />,
            label: 'Quản lý người dùng',
          },
          {
            key: '/admin/audit-logs',
            icon: <AuditOutlined />,
            label: 'Nhật ký kiểm toán',
          },
        ],
      });
    }

    return items;
  }, [hasRole]);

  // Calculate dynamic breadcrumb items
  const breadcrumbItems = useMemo(() => {
    const trail = routeBreadcrumbMap[location.pathname] ?? ['Trang hiện tại'];
    return [
      {
        title: <Link to="/dashboard">Trang chủ</Link>,
      },
      ...trail.map((title) => ({ title: <span>{title}</span> })),
    ];
  }, [location.pathname]);

  const selectedMenuKey = useMemo(
    () => menuRouteKeys.find((key) => location.pathname === key || location.pathname.startsWith(`${key}/`)) ?? '',
    [location.pathname]
  );

  const menuOpenKeys = location.pathname.startsWith('/admin')
    ? Array.from(new Set([...manualOpenKeys, 'admin-group']))
    : manualOpenKeys.filter((key) => key !== 'admin-group');

  const notificationContent = (
    <div style={{ width: 320, padding: '12px 16px', color: '#595959' }}>
      Chưa có thông báo hệ thống mới.
    </div>
  );

  const userDropdownItems = [
    {
      key: 'profile',
      icon: <IdcardOutlined />,
      label: 'Thông tin tài khoản',
      onClick: () => setProfileModalVisible(true),
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Đăng xuất',
      danger: true,
      onClick: async () => {
        await logout();
        navigate('/login', { replace: true });
      },
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* Sidebar Navigation */}
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={(value) => setCollapsed(value)}
        trigger={null}
        theme="dark"
        width={250}
        breakpoint="lg"
        collapsedWidth={0}
        onBreakpoint={(broken) => setCollapsed(broken)}
        style={{
          overflow: 'auto',
          height: '100vh',
          position: 'sticky',
          top: 0,
          left: 0,
          zIndex: 20,
        }}
      >
        {/* Brand Header */}
        <div className="kcht-logo-wrapper">
          <Space direction="horizontal" align="center" size={10}>
            <SafetyCertificateOutlined style={{ fontSize: 26, color: '#4096ff' }} />
            {!collapsed && (
              <div>
                <Title level={5} style={{ color: '#fff', margin: 0, fontSize: 14, fontWeight: 700, letterSpacing: 0.5 }}>
                  KCHT ĐƯỜNG BỘ
                </Title>
                <div style={{ color: 'rgba(255, 255, 255, 0.65)', fontSize: 10, lineHeight: 1 }}>
                  CỤC ĐƯỜNG BỘ VIỆT NAM
                </div>
              </div>
            )}
          </Space>
        </div>

        {/* Navigation Menu */}
        <Menu
          theme="dark"
          selectedKeys={selectedMenuKey ? [selectedMenuKey] : []}
          openKeys={menuOpenKeys}
          onOpenChange={(keys) => setManualOpenKeys(keys.map(String))}
          mode="inline"
          items={menuItems}
          onClick={({ key }) => {
            if (key !== 'admin-group') {
              navigate(key);
            }
          }}
          style={{ borderRight: 0 }}
        />
      </Sider>

      {/* Main Layout Area */}
      <Layout>
        {/* Top Header Bar */}
        <Header className="kcht-header" style={{ padding: '0 20px', background: colorBgContainer }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', height: '100%' }}>
            {/* Left Header: Collapse Toggle & Breadcrumb */}
            <Space size={16} align="center">
              <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setCollapsed(!collapsed)}
                style={{ fontSize: 16, width: 40, height: 40 }}
                title={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'}
              />
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                <span className="kcht-header-system-title" style={{ fontSize: 15, fontWeight: 600, color: '#141414' }}>
                  Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ
                </span>
                <Breadcrumb items={breadcrumbItems} style={{ fontSize: 12, lineHeight: 1 }} />
              </div>
            </Space>

            {/* Right Header: Notification & User Profile */}
            <Space size={16} align="center">
              {/* Notification Popover */}
              <Popover content={notificationContent} trigger="click" placement="bottomRight">
                <Badge count={0} size="small" offset={[-2, 4]}>
                  <Button
                    type="text"
                    shape="circle"
                    icon={<BellOutlined style={{ fontSize: 18 }} />}
                  />
                </Badge>
              </Popover>

              {/* User Profile Dropdown */}
              <Dropdown menu={{ items: userDropdownItems }} placement="bottomRight" arrow>
                <Space style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 6 }}>
                  <Avatar
                    style={{ backgroundColor: '#003a8c', verticalAlign: 'middle' }}
                    icon={<UserOutlined />}
                  >
                    {user?.fullName?.charAt(0) || user?.username?.charAt(0) || 'U'}
                  </Avatar>
                  <div className="kcht-header-user-meta" style={{ display: 'flex', flexDirection: 'column', textAlign: 'left', lineHeight: 1.2 }}>
                    <Text strong style={{ fontSize: 13 }}>
                      {user?.fullName || user?.username || 'Người dùng'}
                    </Text>
                    <Space size={4}>
                      <Tag color={roleDisplay.color} style={{ margin: 0, fontSize: 10, lineHeight: '14px', padding: '0 4px' }}>
                        {roleDisplay.label}
                      </Tag>
                    </Space>
                  </div>
                </Space>
              </Dropdown>
            </Space>
          </div>
        </Header>

        {/* Content Container with Error Boundary */}
        <Content className="kcht-content-container">
          <ErrorBoundary>
            <div
              style={{
                background: colorBgContainer,
                borderRadius: borderRadiusLG,
                padding: 24,
                minHeight: '100%',
                boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
              }}
            >
              <Outlet />
            </div>
          </ErrorBoundary>
        </Content>

        {/* Standard Government Footer */}
        <Footer style={{ textAlign: 'center', fontSize: 12, color: '#8c8c8c', background: '#f0f2f5' }}>
          <div>
            © 2026 <strong>Cục Đường bộ Việt Nam</strong> - Bộ Giao thông Vận tải. Hệ thống Quản trị & Khai thác KCHT Đường bộ Quốc gia.
          </div>
          <div style={{ marginTop: 2, fontSize: 11 }}>
            Phiên bản 1.0.0 (Giai đoạn 6 - Frontend Shell & Giao diện chung) | Giám sát bởi Trung tâm CNTT Đường bộ
          </div>
        </Footer>
      </Layout>

      {/* User Profile Modal */}
      <Modal
        open={profileModalVisible}
        title={
          <Space>
            <InfoCircleOutlined style={{ color: '#1677ff' }} />
            <span>Thông tin tài khoản công vụ</span>
          </Space>
        }
        onCancel={() => setProfileModalVisible(false)}
        footer={[
          <Button key="close" type="primary" onClick={() => setProfileModalVisible(false)}>
            Đóng
          </Button>,
        ]}
      >
        <Descriptions bordered column={1} size="small" style={{ marginTop: 16 }}>
          <Descriptions.Item label="Họ và tên">
            <strong>{user?.fullName}</strong>
          </Descriptions.Item>
          <Descriptions.Item label="Tên đăng nhập">{user?.username}</Descriptions.Item>
          <Descriptions.Item label="Thư điện tử">{user?.email}</Descriptions.Item>
          <Descriptions.Item label="Vai trò hệ thống">
            <Tag color={roleDisplay.color}>{roleDisplay.label} ({user?.role})</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Cơ quan / Đơn vị">{user?.organizationId || 'Cục Đường bộ Việt Nam'}</Descriptions.Item>
          <Descriptions.Item label="Chi nhánh">{user?.branchId || 'Trụ sở chính (Hà Nội)'}</Descriptions.Item>
          <Descriptions.Item label="Danh sách quyền được cấp">
            <div style={{ maxHeight: 150, overflowY: 'auto', display: 'flex', flexWrap: 'wrap', gap: 4 }}>
              {user?.permissions && user.permissions.length > 0 ? (
                user.permissions.map((perm) => (
                  <Tag key={perm} color="blue" style={{ fontSize: 11 }}>
                    {perm}
                  </Tag>
                ))
              ) : (
                <Text type="secondary">Chưa gán quyền cụ thể</Text>
              )}
            </div>
          </Descriptions.Item>
        </Descriptions>
      </Modal>
    </Layout>
  );
};
