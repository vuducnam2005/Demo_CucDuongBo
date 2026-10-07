import React from 'react';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { HomeOutlined } from '@ant-design/icons';

export const NotFoundPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div style={{ padding: '40px 16px', display: 'flex', justifyContent: 'center' }}>
      <Result
        status="404"
        title="404 - Trang không tồn tại"
        subTitle="Đường dẫn bạn yêu cầu không tồn tại hoặc đã được thay đổi trong hệ thống KCHT ĐB."
        extra={
          <Button type="primary" icon={<HomeOutlined />} onClick={() => navigate('/dashboard')}>
            Về Bảng điều hành
          </Button>
        }
      />
    </div>
  );
};
