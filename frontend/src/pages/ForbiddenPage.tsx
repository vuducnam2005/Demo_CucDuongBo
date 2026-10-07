import React from 'react';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { HomeOutlined } from '@ant-design/icons';

export const ForbiddenPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div style={{ padding: '40px 16px', display: 'flex', justifyContent: 'center' }}>
      <Result
        status="403"
        title="403 - Không có quyền truy cập"
        subTitle="Tài khoản của bạn không được phân quyền để truy cập chức năng này. Vui lòng liên hệ Quản trị viên hệ thống Cục Đường bộ Việt Nam để được cấp quyền."
        extra={
          <Button type="primary" icon={<HomeOutlined />} onClick={() => navigate('/dashboard')}>
            Về Bảng điều hành
          </Button>
        }
      />
    </div>
  );
};
