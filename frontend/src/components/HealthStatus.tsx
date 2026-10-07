import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Badge, Card, Space, Typography, Button, Spin } from 'antd';
import { ReloadOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { fetchHealth } from '../services/api';

const { Text } = Typography;

export const HealthStatus: React.FC = () => {
  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['system-health'],
    queryFn: fetchHealth,
    refetchInterval: 15000,
  });

  const isUp = data?.status === 'UP';

  return (
    <Card
      title="Trạng thái Kết nối Hệ thống (System Health)"
      size="small"
      extra={
        <Button
          type="text"
          icon={<ReloadOutlined spin={isFetching} />}
          onClick={() => refetch()}
        >
          Làm mới
        </Button>
      }
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        {isLoading ? (
          <Spin tip="Đang kiểm tra kết nối Backend..." />
        ) : isError ? (
          <Space>
            <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 18 }} />
            <Text type="danger">
              Không thể kết nối đến Backend (`/actuator/health`). Vui lòng kiểm tra dịch vụ.
            </Text>
          </Space>
        ) : (
          <Space direction="vertical">
            <Space>
              <Badge status={isUp ? 'success' : 'error'} />
              <Text strong>Backend Service:</Text>
              <Text style={{ color: isUp ? '#52c41a' : '#ff4d4f' }}>
                {isUp ? 'HOẠT ĐỘNG (UP)' : 'GIÁN ĐOẠN'}
              </Text>
              {isUp && <CheckCircleOutlined style={{ color: '#52c41a' }} />}
            </Space>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Endpoint: `/actuator/health` | Tự động đồng bộ mỗi 15 giây
            </Text>
          </Space>
        )}
      </Space>
    </Card>
  );
};
