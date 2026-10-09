import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Form, InputNumber, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { fetchUsers, getApiErrorMessage } from '../services/api';
import { useAuth } from '../context/AuthContext';
import {
  fetchAssignedRouteOptions, fetchRouteAssignments, revokeRouteAssignment, saveRouteAssignment,
  type RouteAssignment, type RouteAssignmentRequest,
} from '../services/routeAssignmentApi';

export const RouteAssignmentsPage = () => {
  const { user } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<RouteAssignmentRequest>();
  const [page, setPage] = useState(0);
  const [routeSearch, setRouteSearch] = useState('');
  const [userSearch, setUserSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const assignments = useQuery({
    queryKey: ['route-assignments', user?.id, page],
    queryFn: () => fetchRouteAssignments(page),
  });
  const users = useQuery({
    queryKey: ['route-assignment-users', userSearch],
    queryFn: () => fetchUsers({ page: 0, size: 100, q: userSearch }),
  });
  const routes = useQuery({
    queryKey: ['route-assignment-options', user?.id, routeSearch],
    queryFn: () => fetchAssignedRouteOptions(routeSearch),
  });
  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['route-assignments'] });
    await queryClient.invalidateQueries({ queryKey: ['region-summary'] });
    await queryClient.invalidateQueries({ queryKey: ['region-assets'] });
  };
  const save = useMutation({
    mutationFn: saveRouteAssignment,
    onSuccess: async () => {
      setError(null);
      form.resetFields();
      await refresh();
      void message.success('Đã lưu phân tuyến thử nghiệm.');
    },
    onError: (cause) => setError(getApiErrorMessage(cause)),
  });
  const revoke = useMutation({
    mutationFn: revokeRouteAssignment,
    onSuccess: async () => { setError(null); await refresh(); void message.success('Đã thu hồi quyền theo tuyến.'); },
    onError: (cause) => setError(getApiErrorMessage(cause)),
  });
  const columns: TableColumnsType<RouteAssignment> = [
    { title: 'Tài khoản', key: 'username', width: 180, render: (_, item) => <Space direction="vertical" size={0}>
      <Typography.Text strong>{item.username}</Typography.Text><Typography.Text type="secondary">{item.fullName}</Typography.Text>
    </Space> },
    { title: 'Đơn vị', dataIndex: 'branchId', width: 115 },
    { title: 'Tuyến theo nguồn (giữ nguyên chiều)', dataIndex: 'routeName', ellipsis: true },
    { title: 'Khoảng lý trình', key: 'chainage', width: 180, render: (_, item) =>
      item.chainageFromM === null && item.chainageToM === null ? 'Toàn tuyến khảo sát' :
        `${item.chainageFromM?.toLocaleString('vi-VN') ?? 'Đầu tuyến'} → ${item.chainageToM?.toLocaleString('vi-VN') ?? 'Cuối tuyến'} (m)` },
    { title: 'Phạm vi', key: 'purpose', width: 100, render: () => <Tag color="orange">DEMO</Tag> },
    { title: 'Thao tác', key: 'actions', width: 160, render: (_, item) => <Space>
      <Button size="small" onClick={() => form.setFieldsValue({ userId: item.userId, routeName: item.routeName,
        chainageFromM: item.chainageFromM, chainageToM: item.chainageToM })}>Sửa</Button>
      <Popconfirm title="Thu hồi quyền theo tuyến này?" description="Không xóa hay thay đổi dữ liệu nguồn."
        okText="Thu hồi" cancelText="Giữ lại" onConfirm={() => revoke.mutate(item.id)}>
        <Button danger size="small" loading={revoke.isPending && revoke.variables === item.id}>Thu hồi</Button>
      </Popconfirm>
    </Space> },
  ];
  return <Space direction="vertical" size="large" style={{ width: '100%' }}>
    <div><Typography.Title level={2}>Phân tuyến tài khoản</Typography.Title>
      <Typography.Text type="secondary">Cấp quyền thử nghiệm theo tuyến và khoảng lý trình, có nhật ký và kiểm soát trên máy chủ.</Typography.Text></div>
    <Alert showIcon type="info" message="Dữ liệu nguồn thật, phạm vi quản lý DEMO"
      description="Phân tuyến ở đây phục vụ nghiệm thu, không xác nhận địa bàn chính thức. Tuyến INC/DEC được giữ riêng; khoảng lý trình tính bằng mét. Người xem chỉ truy cập bản ghi đã kiểm duyệt nội bộ." />
    {(error || users.isError || routes.isError || assignments.isError) && <Alert type="error" showIcon
      message={error || 'Không tải được dữ liệu phân tuyến. Vui lòng thử lại.'} />}
    <Card title="Cấp hoặc cập nhật quyền theo tuyến">
      <Form form={form} layout="vertical" onFinish={(request) => save.mutate(request)}>
        <Form.Item name="userId" label="Tài khoản đã được gán đơn vị" rules={[{ required: true, message: 'Chọn tài khoản.' }]}>
          <Select showSearch filterOption={false} onSearch={setUserSearch} loading={users.isFetching}
            placeholder="Tìm tên đăng nhập hoặc họ tên" options={(users.data?.content || [])
              .filter((account) => account.active && account.branchId && account.role !== 'ROLE_ADMIN')
              .map((account) => ({ value: account.id, label: `${account.username} — ${account.fullName} (${account.branchId})` }))} />
        </Form.Item>
        <Form.Item name="routeName" label="Tuyến đúng theo dữ liệu khảo sát" rules={[{ required: true, message: 'Chọn tuyến nguồn.' }]}>
          <Select showSearch filterOption={false} onSearch={setRouteSearch} loading={routes.isFetching}
            placeholder="Tìm mã tuyến; không gộp chiều tăng và giảm" options={(routes.data || [])
              .map((option) => ({ value: option.routeName, label: `${option.routeName} — ${option.records} bản ghi` }))} />
        </Form.Item>
        <Space align="start" wrap>
          <Form.Item name="chainageFromM" label="Lý trình bắt đầu (m), tùy chọn">
            <InputNumber min={0} max={999999999999} precision={3} style={{ width: 240 }} />
          </Form.Item>
          <Form.Item name="chainageToM" label="Lý trình kết thúc (m), tùy chọn" dependencies={['chainageFromM']}
            rules={[({ getFieldValue }) => ({ validator: (_, value: number | null | undefined) => {
              const start = getFieldValue('chainageFromM') as number | null | undefined;
              return value != null && start != null && value < start ? Promise.reject(new Error('Lý trình kết thúc phải lớn hơn hoặc bằng bắt đầu.')) : Promise.resolve();
            } })]}>
            <InputNumber min={0} max={999999999999} precision={3} style={{ width: 240 }} />
          </Form.Item>
        </Space>
        <Form.Item><Space><Button type="primary" htmlType="submit" loading={save.isPending}>Lưu phân tuyến</Button>
          <Button onClick={() => form.resetFields()}>Xóa lựa chọn</Button></Space></Form.Item>
      </Form>
    </Card>
    <Card title="Quyền theo tuyến hiện có" extra={<Button onClick={() => void assignments.refetch()}>Làm mới</Button>}>
      <Table<RouteAssignment> rowKey="id" columns={columns} dataSource={assignments.data?.content || []}
        loading={assignments.isLoading} scroll={{ x: 1100 }} pagination={{ current: page + 1, pageSize: 20,
          total: assignments.data?.totalElements || 0, showSizeChanger: false, onChange: (current) => setPage(current - 1),
          showTotal: (total) => `${total} phân tuyến` }} />
    </Card>
  </Space>;
};
