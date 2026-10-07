import React, { ReactNode } from 'react';
import { Input, Button, Space, Tooltip } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';

interface TableToolbarProps {
  searchValue?: string;
  onSearchChange?: (value: string) => void;
  searchPlaceholder?: string;
  onRefresh?: () => void;
  isRefreshing?: boolean;
  filterControls?: ReactNode;
  actionButtons?: ReactNode;
  extraLeft?: ReactNode;
}

export const TableToolbar: React.FC<TableToolbarProps> = ({
  searchValue,
  onSearchChange,
  searchPlaceholder = 'Tìm kiếm theo từ khóa...',
  onRefresh,
  isRefreshing = false,
  filterControls,
  actionButtons,
  extraLeft,
}) => {
  return (
    <div className="kcht-table-toolbar">
      <div className="kcht-table-toolbar-left">
        {onSearchChange !== undefined && (
          <Input
            placeholder={searchPlaceholder}
            prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
            allowClear
            value={searchValue}
            onChange={(e) => onSearchChange(e.target.value)}
            style={{ width: 280, maxWidth: '100%' }}
          />
        )}
        {filterControls}
        {extraLeft}
      </div>

      <div className="kcht-table-toolbar-right">
        {onRefresh && (
          <Tooltip title="Làm mới dữ liệu">
            <Button
              icon={<ReloadOutlined spin={isRefreshing} />}
              onClick={onRefresh}
              disabled={isRefreshing}
            >
              Làm mới
            </Button>
          </Tooltip>
        )}
        {actionButtons && <Space>{actionButtons}</Space>}
      </div>
    </div>
  );
};
