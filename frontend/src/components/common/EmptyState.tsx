import React, { ReactNode } from 'react';
import { Empty, Button, Typography, Space } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';

const { Text, Title } = Typography;

interface EmptyStateProps {
  title?: string;
  description?: string | ReactNode;
  actionText?: string;
  actionIcon?: ReactNode;
  onAction?: () => void;
  extraActions?: ReactNode;
  image?: ReactNode;
  style?: React.CSSProperties;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title = 'Không có dữ liệu',
  description = 'Hiện tại chưa có bản ghi nào hoặc không tìm thấy kết quả phù hợp với tiêu chí lọc.',
  actionText,
  actionIcon = <ReloadOutlined />,
  onAction,
  extraActions,
  image = Empty.PRESENTED_IMAGE_SIMPLE,
  style,
}) => {
  return (
    <div
      style={{
        padding: '32px 16px',
        textAlign: 'center',
        background: '#ffffff',
        borderRadius: 8,
        ...style,
      }}
    >
      <Empty
        image={image}
        description={
          <Space direction="vertical" size={4} style={{ marginTop: 8 }}>
            <Title level={5} style={{ margin: 0, color: '#595959' }}>
              {title}
            </Title>
            {typeof description === 'string' ? (
              <Text type="secondary" style={{ maxWidth: 420, margin: '0 auto', display: 'block' }}>
                {description}
              </Text>
            ) : (
              description
            )}
          </Space>
        }
      >
        {(actionText || extraActions) && (
          <Space style={{ marginTop: 12 }}>
            {actionText && onAction && (
              <Button type="primary" icon={actionIcon} onClick={onAction}>
                {actionText}
              </Button>
            )}
            {extraActions}
          </Space>
        )}
      </Empty>
    </div>
  );
};
