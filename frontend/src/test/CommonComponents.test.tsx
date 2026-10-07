import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import {
  TableToolbar,
  EmptyState,
  StandardPagination,
  TableSkeleton,
  CardSkeleton,
  ErrorBoundary,
} from '../components/common';

const renderWithConfig = (ui: React.ReactElement) => {
  return render(
    <ConfigProvider theme={{ hashed: false }}>
      {ui}
    </ConfigProvider>
  );
};

describe('Common Components Unit Tests', () => {
  describe('TableToolbar', () => {
    it('renders search input and responds to input changes', () => {
      const handleSearch = vi.fn();
      renderWithConfig(
        <TableToolbar
          searchValue="test-query"
          onSearchChange={handleSearch}
          searchPlaceholder="Tìm kiếm..."
        />
      );

      const input = screen.getByPlaceholderText('Tìm kiếm...');
      expect(input).toBeInTheDocument();
      expect(input).toHaveValue('test-query');

      fireEvent.change(input, { target: { value: 'cầu bãi cháy' } });
      expect(handleSearch).toHaveBeenCalledWith('cầu bãi cháy');
    });

    it('renders refresh button and calls onRefresh when clicked', () => {
      const handleRefresh = vi.fn();
      renderWithConfig(<TableToolbar onRefresh={handleRefresh} isRefreshing={false} />);

      const refreshBtn = screen.getByRole('button', { name: /làm mới/i });
      expect(refreshBtn).toBeInTheDocument();
      fireEvent.click(refreshBtn);
      expect(handleRefresh).toHaveBeenCalledTimes(1);
    });

    it('disables refresh button when isRefreshing is true', () => {
      const handleRefresh = vi.fn();
      renderWithConfig(<TableToolbar onRefresh={handleRefresh} isRefreshing={true} />);

      const refreshBtn = screen.getByRole('button', { name: /làm mới/i });
      expect(refreshBtn).toBeDisabled();
    });
  });

  describe('EmptyState', () => {
    it('renders default empty state text and action button', () => {
      const handleAction = vi.fn();
      renderWithConfig(
        <EmptyState
          title="Không tìm thấy dữ liệu"
          description="Vui lòng thử lại với từ khóa khác"
          actionText="Tải lại"
          onAction={handleAction}
        />
      );

      expect(screen.getByText('Không tìm thấy dữ liệu')).toBeInTheDocument();
      expect(screen.getByText('Vui lòng thử lại với từ khóa khác')).toBeInTheDocument();

      const actionBtn = screen.getByRole('button', { name: /tải lại/i });
      expect(actionBtn).toBeInTheDocument();
      fireEvent.click(actionBtn);
      expect(handleAction).toHaveBeenCalledTimes(1);
    });
  });

  describe('StandardPagination', () => {
    it('renders pagination with Vietnamese range text', () => {
      const handleChange = vi.fn();
      renderWithConfig(
        <StandardPagination
          page={0} // 0-indexed page 1
          size={10}
          totalElements={55}
          onChange={handleChange}
        />
      );

      expect(screen.getByText(/trong tổng số/i)).toBeInTheDocument();
      expect(screen.getByText('55')).toBeInTheDocument();
    });
  });

  describe('LoadingSkeleton', () => {
    it('renders table and card skeletons without crashing', () => {
      const { container: tableContainer } = renderWithConfig(<TableSkeleton rows={3} columns={3} />);
      expect(tableContainer.querySelector('.ant-skeleton')).toBeInTheDocument();

      const { container: cardContainer } = renderWithConfig(<CardSkeleton count={2} />);
      expect(cardContainer.querySelectorAll('.ant-card').length).toBe(2);
    });
  });

  describe('ErrorBoundary', () => {
    it('catches rendering errors and displays 500 error screen with recovery buttons', () => {
      const ProblematicComponent = () => {
        throw new Error('Test crash in child component');
      };

      const originalConsoleError = console.error;
      console.error = vi.fn();

      renderWithConfig(
        <ErrorBoundary>
          <ProblematicComponent />
        </ErrorBoundary>
      );

      expect(screen.getByText('Đã xảy ra lỗi giao diện')).toBeInTheDocument();
      expect(screen.getByText(/Error: Test crash in child component/i)).toBeInTheDocument();
      expect(screen.getByTestId('error-correlation-id')).toHaveTextContent(/^Mã lỗi: ui-/);
      expect(screen.getByRole('button', { name: /tải lại trang/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /về bảng điều hành/i })).toBeInTheDocument();

      console.error = originalConsoleError;
    });

    it('does not render internal exception details in production', () => {
      vi.stubEnv('DEV', false);
      vi.stubEnv('PROD', true);
      vi.stubEnv('MODE', 'production');
      const ProblematicComponent = () => {
        throw new Error('DB password and /workspace/node_modules/internal.js:42');
      };
      const originalConsoleError = console.error;
      console.error = vi.fn();

      renderWithConfig(
        <ErrorBoundary>
          <ProblematicComponent />
        </ErrorBoundary>
      );

      expect(screen.getByText('Đã xảy ra lỗi giao diện')).toBeInTheDocument();
      expect(screen.queryByText(/DB password|node_modules|internal\.js/i)).not.toBeInTheDocument();
      expect(screen.getByTestId('error-correlation-id')).toHaveTextContent(/^Mã lỗi: ui-/);
      expect(console.error).toHaveBeenCalled();
      const safeLoggerCalls = vi.mocked(console.error).mock.calls.filter(([label]) => label === 'Client error');
      expect(JSON.stringify(safeLoggerCalls)).not.toMatch(/DB password|node_modules|internal\.js/i);

      console.error = originalConsoleError;
      vi.unstubAllEnvs();
    });
  });
});
