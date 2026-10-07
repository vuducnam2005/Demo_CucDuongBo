import React from 'react';
import { Pagination } from 'antd';

interface StandardPaginationProps {
  page: number; // 0-indexed (backend format)
  size: number;
  totalElements: number;
  onChange: (page: number, size: number) => void;
  pageSizeOptions?: string[];
  showSizeChanger?: boolean;
  style?: React.CSSProperties;
}

export const StandardPagination: React.FC<StandardPaginationProps> = ({
  page,
  size,
  totalElements,
  onChange,
  pageSizeOptions = ['10', '20', '50', '100'],
  showSizeChanger = true,
  style,
}) => {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'flex-end',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 12,
        padding: '16px 0',
        ...style,
      }}
    >
      <Pagination
        current={page + 1} // Convert 0-indexed to 1-indexed for Antd
        pageSize={size}
        total={totalElements}
        pageSizeOptions={pageSizeOptions}
        showSizeChanger={showSizeChanger}
        showQuickJumper
        showTotal={(total, range) => (
          <span style={{ color: '#595959' }}>
            Hiển thị <strong>{range[0]}</strong> - <strong>{range[1]}</strong> trong tổng số{' '}
            <strong>{total.toLocaleString('vi-VN')}</strong> bản ghi
          </span>
        )}
        onChange={(newPage, newSize) => {
          // Pass back 0-indexed page to match backend API convention
          onChange(newPage - 1, newSize);
        }}
      />
    </div>
  );
};
