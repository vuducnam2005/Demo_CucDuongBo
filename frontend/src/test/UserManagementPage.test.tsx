import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { UserManagementPage } from '../pages/UserManagementPage';
import * as api from '../services/api';
import { normalizePagedResponse } from '../services/pagination';

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api');
  return {
    ...actual,
    fetchUsers: vi.fn(),
    createUser: vi.fn(),
  };
});

class HttpError extends Error {
  response: { status: number; data?: unknown };

  constructor(status: number, message: string, data?: unknown) {
    super(message);
    this.response = { status, data };
  }
}

const adminUser: api.UserSummary = {
  id: 1,
  username: 'admin',
  email: 'admin@drvn.gov.vn',
  fullName: 'Quản trị viên Hệ thống',
  role: 'ROLE_ADMIN',
  roleName: 'Quản trị hệ thống',
  organizationId: 'moc_dbvn',
  branchId: null,
  active: true,
  permissions: ['USER:MANAGE_USERS'],
};

const viewerUser: api.UserSummary = {
  id: 2,
  username: 'viewer_demo',
  email: 'viewer@drvn.gov.vn',
  fullName: 'Cán bộ Tra cứu',
  role: 'ROLE_VIEWER',
  roleName: 'Người xem',
  organizationId: 'moc_dbvn',
  branchId: 'kqldb_1',
  active: false,
  permissions: ['ASSET:READ'],
};

const pageResponse = (content: api.UserSummary[], page = 0, totalElements = content.length) => ({
  content,
  page,
  size: 10,
  totalElements,
  totalPages: totalElements === 0 ? 0 : Math.ceil(totalElements / 10),
  first: page === 0,
  last: totalElements === 0 || page >= Math.ceil(totalElements / 10) - 1,
});

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { gcTime: 0 },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <UserManagementPage />
      </ConfigProvider>
    </QueryClientProvider>
  );
};

describe('UserManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.fetchUsers).mockResolvedValue(pageResponse([adminUser, viewerUser], 0, 12));
  });

  it('đọc content từ paged response và không truyền object phân trang vào bảng', async () => {
    renderPage();

    expect(await screen.findByText('admin')).toBeInTheDocument();
    expect(screen.getByText('viewer_demo')).toBeInTheDocument();
    expect(screen.getByText('Hoạt động')).toBeInTheDocument();
    expect(screen.getByText('Đã khóa')).toBeInTheDocument();
    expect(api.fetchUsers).toHaveBeenCalledWith({ page: 0, size: 10, q: undefined });
  });

  it('đồng bộ phân trang và tìm kiếm toàn hệ thống vào query backend', async () => {
    vi.mocked(api.fetchUsers).mockImplementation(async ({ page = 0, q } = {}) => {
      if (q) return pageResponse([viewerUser], 0, 1);
      return pageResponse(page === 1 ? [viewerUser] : [adminUser], page, 12);
    });

    const { container } = renderPage();
    expect(await screen.findByText('admin')).toBeInTheDocument();

    const secondPage = container.querySelector('.ant-pagination-item-2');
    expect(secondPage).not.toBeNull();
    fireEvent.click(secondPage!);
    await waitFor(() =>
      expect(api.fetchUsers).toHaveBeenCalledWith({ page: 1, size: 10, q: undefined })
    );

    fireEvent.change(screen.getByPlaceholderText('Tìm kiếm cán bộ theo tên, mã hoặc email...'), {
      target: { value: 'viewer' },
    });
    await waitFor(() =>
      expect(api.fetchUsers).toHaveBeenCalledWith({ page: 0, size: 10, q: 'viewer' })
    );
    expect(await screen.findByText('viewer_demo')).toBeInTheDocument();
  });

  it('hiển thị trạng thái rỗng đúng theo kết quả tìm kiếm server-side', async () => {
    vi.mocked(api.fetchUsers).mockResolvedValue(pageResponse([], 0, 0));
    renderPage();

    expect(await screen.findByText('Chưa có tài khoản người dùng')).toBeInTheDocument();
  });

  it.each([
    [401, 'Phiên đăng nhập đã hết hạn', 'Vui lòng đăng nhập lại để tiếp tục sử dụng hệ thống.'],
    [403, 'Không có quyền truy cập', 'Tài khoản hiện tại không được cấp quyền thực hiện yêu cầu này.'],
  ])('không retry HTTP %s và hiển thị lỗi có ích', async (status, title, description) => {
    vi.mocked(api.fetchUsers).mockRejectedValue(new HttpError(status, `HTTP ${status}`));
    renderPage();

    expect(await screen.findByText(title)).toBeInTheDocument();
    expect(screen.getByText(description)).toBeInTheDocument();
    expect(api.fetchUsers).toHaveBeenCalledTimes(1);
  });

  it('retry một lần với lỗi 5xx rồi hiển thị lỗi, không lặp vô hạn', async () => {
    vi.mocked(api.fetchUsers).mockRejectedValue(new HttpError(500, 'Máy chủ tạm thời không phản hồi'));
    renderPage();

    expect(await screen.findByText('Hệ thống đang gặp sự cố', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByText('Máy chủ chưa thể xử lý yêu cầu. Vui lòng thử lại sau.')).toBeInTheDocument();
    expect(api.fetchUsers).toHaveBeenCalledTimes(2);
  });

  it('gọi API tạo người dùng thật và giữ nguyên dữ liệu form khi API thất bại', async () => {
    vi.mocked(api.createUser).mockRejectedValue(new HttpError(500, 'Không thể tạo tài khoản'));
    renderPage();
    await screen.findByText('admin');

    fireEvent.click(screen.getByRole('button', { name: /Thêm người dùng mới/i }));
    fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: 'new_user' } });
    fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: 'Người dùng mới' } });
    fireEvent.change(screen.getByLabelText('Thư điện tử (Email)'), {
      target: { value: 'new_user@drvn.gov.vn' },
    });
    fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), {
      target: { value: 'Secret@2026' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));

    await waitFor(() => expect(api.createUser).toHaveBeenCalledTimes(1));
    expect(screen.getByLabelText('Tên đăng nhập')).toHaveValue('new_user');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('gửi đúng DTO tạo user, chỉ đóng modal sau khi API thành công', async () => {
    const createdUser = { ...adminUser, id: 99, username: 'new_user' };
    vi.mocked(api.createUser).mockResolvedValue(createdUser);
    renderPage();
    await screen.findByText('admin');

    fireEvent.click(screen.getByRole('button', { name: /Thêm người dùng mới/i }));
    fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: 'new_user' } });
    fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: 'Người dùng mới' } });
    fireEvent.change(screen.getByLabelText('Thư điện tử (Email)'), {
      target: { value: 'new_user@drvn.gov.vn' },
    });
    fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), {
      target: { value: 'Secret@2026' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));

    await waitFor(() => expect(vi.mocked(api.createUser).mock.calls[0]?.[0]).toEqual({
      username: 'new_user',
      fullName: 'Người dùng mới',
      email: 'new_user@drvn.gov.vn',
      password: 'Secret@2026',
      roleCode: 'ROLE_VIEWER',
    }));
    await waitFor(() => expect(screen.getByText('Đã tạo tài khoản new_user.')).toBeInTheDocument());
    expect(screen.getByLabelText('Tên đăng nhập')).toHaveValue('');
  });

  it('chống submit lặp khi request tạo user đang chờ', async () => {
    let resolveMutation: ((value: api.UserSummary) => void) | undefined;
    vi.mocked(api.createUser).mockImplementation(
      () => new Promise((resolve) => {
        resolveMutation = resolve;
      })
    );
    renderPage();
    await screen.findByText('admin');

    fireEvent.click(screen.getByRole('button', { name: /Thêm người dùng mới/i }));
    fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: 'pending_user' } });
    fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: 'Pending User' } });
    fireEvent.change(screen.getByLabelText('Thư điện tử (Email)'), {
      target: { value: 'pending_user@drvn.gov.vn' },
    });
    fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), {
      target: { value: 'Secret@2026' },
    });
    const submitButton = screen.getByRole('button', { name: 'Tạo tài khoản' });
    fireEvent.click(submitButton);
    fireEvent.click(submitButton);

    await waitFor(() => expect(api.createUser).toHaveBeenCalledTimes(1));
    resolveMutation?.({ ...adminUser, id: 100, username: 'pending_user' });
    await waitFor(() => expect(screen.getByText('Đã tạo tài khoản pending_user.')).toBeInTheDocument());
  });

  it('gắn lỗi validation từ backend vào đúng trường và giữ modal mở', async () => {
    vi.mocked(api.createUser).mockRejectedValue(new HttpError(400, 'Dữ liệu yêu cầu không hợp lệ', {
      message: 'Dữ liệu yêu cầu không hợp lệ',
      errors: ['password: Mật khẩu phải gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt'],
    }));
    renderPage();
    await screen.findByText('admin');

    fireEvent.click(screen.getByRole('button', { name: /Thêm người dùng mới/i }));
    fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: 'new_user' } });
    fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: 'Người dùng mới' } });
    fireEvent.change(screen.getByLabelText('Thư điện tử (Email)'), {
      target: { value: 'new_user@drvn.gov.vn' },
    });
    fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), { target: { value: 'Weakpass1!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));

    expect(await screen.findByText('Mật khẩu phải gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('không gọi thông báo thành công giả khi bấm Phân quyền chưa hỗ trợ', async () => {
    renderPage();
    await screen.findByText('admin');

    const permissionButtons = screen.getAllByRole('button', { name: /Phân quyền \(chưa hỗ trợ\)/i });
    expect(permissionButtons.length).toBe(2);
    permissionButtons.forEach((button) => expect(button).toBeDisabled());
  });
});

describe('normalizePagedResponse', () => {
  it('từ chối response dạng mảng thay vì paged object', () => {
    expect(() => normalizePagedResponse([adminUser], api.isUserSummary, 'users')).toThrow(
      'phải trả về một đối tượng phân trang'
    );
  });
});
