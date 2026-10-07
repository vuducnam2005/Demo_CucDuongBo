import React, { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Table,
  TableColumnsType,
  Tag,
  Typography,
  Card,
  Space,
  Button,
  Modal,
  Form,
  Input,
  Select,
  message,
} from 'antd';
import {
  TeamOutlined,
  UserAddOutlined,
  KeyOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import {
  createUser,
  fetchUsers,
  getApiFieldErrors,
  getApiErrorMessage,
  getApiErrorPresentation,
  shouldRetryQuery,
  CreateUserRequest,
  UserSummary,
} from '../services/api';
import {
  TableToolbar,
  TableSkeleton,
  EmptyState,
  StandardPagination,
} from '../components/common';

const { Title, Paragraph } = Typography;

const isCreateUserField = (field: string): field is keyof CreateUserRequest =>
  ['username', 'email', 'password', 'fullName', 'roleCode', 'organizationId', 'branchId'].includes(field);

export const UserManagementPage: React.FC = () => {
  const [searchKeyword, setSearchKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [form] = Form.useForm<CreateUserRequest>();
  const queryClient = useQueryClient();
  const normalizedSearch = searchKeyword.trim();

  const { data, isLoading, refetch, isFetching, isError, error } = useQuery({
    queryKey: ['admin-users', page, pageSize, normalizedSearch],
    queryFn: () =>
      fetchUsers({
        page,
        size: pageSize,
        q: normalizedSearch || undefined,
      }),
    placeholderData: keepPreviousData,
    retry: shouldRetryQuery,
  });

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: (createdUser) => {
      message.success(`Đã tạo tài khoản ${createdUser.username}.`);
      setCreateModalOpen(false);
      form.resetFields();
      setPage(0);
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
    onError: (mutationError) => {
      const fieldErrors = getApiFieldErrors(mutationError);
      if (fieldErrors.length > 0) {
        const formFieldErrors = fieldErrors.flatMap(({ field, message: fieldMessage }) =>
          isCreateUserField(field)
            ? [{ name: field, errors: [fieldMessage] }]
            : []
        );
        form.setFields(formFieldErrors);
      }
      message.error(getApiErrorMessage(mutationError));
    },
  });

  const getRoleTag = (role: string) => {
    switch (role?.toUpperCase()) {
      case 'ROLE_ADMIN':
      case 'ADMIN':
        return <Tag color="red">Quản trị viên (ADMIN)</Tag>;
      case 'ROLE_MANAGER':
      case 'MANAGER':
        return <Tag color="blue">Lãnh đạo (MANAGER)</Tag>;
      case 'ROLE_EDITOR':
      case 'EDITOR':
        return <Tag color="green">Cán bộ (EDITOR)</Tag>;
      case 'ROLE_VIEWER':
      case 'VIEWER':
        return <Tag color="purple">Người xem (VIEWER)</Tag>;
      default:
        return <Tag>{role}</Tag>;
    }
  };

  const columns: TableColumnsType<UserSummary> = [
    {
      title: 'Tên đăng nhập',
      dataIndex: 'username',
      key: 'username',
      render: (text: string) => <strong>{text}</strong>,
    },
    {
      title: 'Họ và tên cán bộ',
      dataIndex: 'fullName',
      key: 'fullName',
    },
    {
      title: 'Thư điện tử',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: 'Vai trò phân quyền',
      dataIndex: 'role',
      key: 'role',
      render: (role: string) => getRoleTag(role),
    },
    {
      title: 'Cơ quan / Đơn vị',
      dataIndex: 'organizationId',
      key: 'organizationId',
      render: (org: string | null | undefined) => org || 'Chưa gán',
    },
    {
      title: 'Trạng thái',
      key: 'status',
      render: (_value: unknown, record: UserSummary) =>
        record.active ? (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            Hoạt động
          </Tag>
        ) : (
          <Tag color="default">Đã khóa</Tag>
        ),
    },
    {
      title: 'Thao tác',
      key: 'action',
      render: () => (
        <Space size="small">
          <Button
            size="small"
            icon={<KeyOutlined />}
            disabled
          >
            Phân quyền (chưa hỗ trợ)
          </Button>
        </Space>
      ),
    },
  ];

  const errorPresentation = getApiErrorPresentation(error);

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Space align="center" size={10}>
          <TeamOutlined style={{ fontSize: 24, color: '#003a8c' }} />
          <div>
            <Title level={4} style={{ margin: 0 }}>
              Quản trị Người dùng & Phân quyền Truy cập
            </Title>
            <Paragraph type="secondary" style={{ margin: 0, fontSize: 13 }}>
              Quản lý danh sách cán bộ, vai trò và phân quyền hạn chế theo ma trận RBAC.
            </Paragraph>
          </div>
        </Space>
      </div>

      <Card variant="borderless" className="kcht-card">
        <TableToolbar
          searchValue={searchKeyword}
          onSearchChange={(value) => {
            setSearchKeyword(value);
            setPage(0);
          }}
          searchPlaceholder="Tìm kiếm cán bộ theo tên, mã hoặc email..."
          onRefresh={() => refetch()}
          isRefreshing={isFetching}
          actionButtons={
            <Button
              type="primary"
              icon={<UserAddOutlined />}
              onClick={() => setCreateModalOpen(true)}
            >
              Thêm người dùng mới
            </Button>
          }
        />

        {isLoading ? (
          <TableSkeleton rows={4} columns={6} />
        ) : isError ? (
          <EmptyState
            title={errorPresentation.title}
            description={errorPresentation.description}
            actionText={errorPresentation.retryable ? 'Thử lại' : undefined}
            onAction={errorPresentation.retryable ? () => void refetch() : undefined}
          />
        ) : !data?.content.length ? (
          <EmptyState
            title={normalizedSearch ? 'Không tìm thấy cán bộ phù hợp' : 'Chưa có tài khoản người dùng'}
            description={
              normalizedSearch
                ? 'Không có tài khoản nào khớp với từ khóa trên toàn hệ thống.'
                : 'Hệ thống chưa có tài khoản để hiển thị.'
            }
            actionText={normalizedSearch ? 'Xóa tìm kiếm' : 'Làm mới'}
            onAction={() => {
              if (normalizedSearch) setSearchKeyword('');
              else void refetch();
            }}
          />
        ) : (
          <>
            <Table<UserSummary>
              dataSource={data.content}
              columns={columns}
              rowKey="id"
              pagination={false}
              loading={isFetching}
              bordered
              size="middle"
            />
            <StandardPagination
              page={data.page}
              size={data.size}
              totalElements={data.totalElements}
              onChange={(newPage, newSize) => {
                setPage(newPage);
                setPageSize(newSize);
              }}
            />
          </>
        )}
      </Card>

      <Modal
        open={createModalOpen}
        destroyOnHidden
        title="Thêm mới tài khoản cán bộ công vụ"
        onCancel={() => {
          if (!createMutation.isPending) setCreateModalOpen(false);
        }}
        onOk={() => {
          if (!createMutation.isPending) form.submit();
        }}
        confirmLoading={createMutation.isPending}
        maskClosable={!createMutation.isPending}
        okText="Tạo tài khoản"
        cancelText="Hủy bỏ"
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
          initialValues={{ roleCode: 'ROLE_VIEWER' }}
          onFinish={(values) => {
            if (!createMutation.isPending) createMutation.mutate(values);
          }}
        >
          <Form.Item
            name="username"
            label="Tên đăng nhập"
            rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}
          >
            <Input placeholder="Ví dụ: nguyen_van_a" />
          </Form.Item>
          <Form.Item
            name="fullName"
            label="Họ và tên"
            rules={[{ required: true, message: 'Vui lòng nhập họ và tên' }]}
          >
            <Input placeholder="Ví dụ: Nguyễn Văn A" />
          </Form.Item>
          <Form.Item
            name="email"
            label="Thư điện tử (Email)"
            rules={[{ required: true, type: 'email', message: 'Vui lòng nhập email hợp lệ' }]}
          >
            <Input placeholder="Ví dụ: anvd@drvn.gov.vn" />
          </Form.Item>
          <Form.Item
            name="password"
            label="Mật khẩu ban đầu"
            rules={[
              { required: true, message: 'Vui lòng nhập mật khẩu ban đầu' },
              { min: 8, message: 'Mật khẩu phải có ít nhất 8 ký tự' },
              {
                pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).+$/,
                message: 'Mật khẩu phải gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt',
              },
            ]}
          >
            <Input.Password autoComplete="new-password" placeholder="Tối thiểu 8 ký tự, gồm hoa/thường/số/ký tự đặc biệt" />
          </Form.Item>
          <Form.Item
            name="roleCode"
            label="Vai trò"
            rules={[{ required: true }]}
          >
            <Select
              options={[
                { label: 'Người xem (ROLE_VIEWER)', value: 'ROLE_VIEWER' },
                { label: 'Cán bộ nghiệp vụ (ROLE_EDITOR)', value: 'ROLE_EDITOR' },
                { label: 'Lãnh đạo / Quản lý (ROLE_MANAGER)', value: 'ROLE_MANAGER' },
                { label: 'Quản trị viên (ROLE_ADMIN)', value: 'ROLE_ADMIN' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
