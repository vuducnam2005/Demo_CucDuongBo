import React, { useState } from 'react';
import {
  Card,
  Form,
  Input,
  Button,
  Typography,
  Alert,
  Space,
  message,
} from 'antd';
import {
  UserOutlined,
  LockOutlined,
  CheckCircleOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LoginParams } from '../services/authApi';
import { getApiErrorMessage, getHttpStatus } from '../services/api';
import { VroadBrand } from '../components/brand/VroadBrand';

const { Title, Text } = Typography;

export const LoginPage: React.FC = () => {
  const [form] = Form.useForm<LoginParams>();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const handleFinish = async (values: LoginParams) => {
    setErrorMessage(null);
    setSubmitting(true);
    try {
      await login(values);
      message.success('Đăng nhập thành công!');
      const redirect = searchParams.get('redirect');
      const target = redirect ? decodeURIComponent(redirect) : '/dashboard';
      navigate(target, { replace: true });
    } catch (err: unknown) {
      const status = getHttpStatus(err);
      if (status === 429) {
        setErrorMessage('Đã vượt quá số lần thử đăng nhập cho phép. Vui lòng thử lại sau.');
      } else if (status === 401) {
        setErrorMessage('Tên đăng nhập hoặc mật khẩu không chính xác.');
      } else if (err instanceof Error && err.message === 'Network Error') {
        setErrorMessage('Không thể kết nối đến máy chủ xác thực. Vui lòng kiểm tra kết nối mạng.');
      } else {
        setErrorMessage(getApiErrorMessage(err));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'radial-gradient(circle at 50% 20%, #002766 0%, #001529 60%, #000c17 100%)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        padding: '32px 16px',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Decorative Glow Element */}
      <div
        style={{
          position: 'absolute',
          top: '15%',
          left: '50%',
          transform: 'translateX(-50%)',
          width: 500,
          height: 500,
          background: 'radial-gradient(circle, rgba(22, 119, 255, 0.15) 0%, rgba(0, 0, 0, 0) 70%)',
          pointerEvents: 'none',
        }}
      />

      <div style={{ maxWidth: 460, width: '100%', marginBottom: 28, textAlign: 'center', position: 'relative', zIndex: 1 }}>
        <Space direction="vertical" size={6} style={{ width: '100%' }}>
          <div
            style={{
              display: 'inline-flex',
              padding: '8px 20px',
              borderRadius: 12,
              background: 'rgba(255, 255, 255, 0.08)',
              backdropFilter: 'blur(12px)',
              border: '1px solid rgba(255, 255, 255, 0.15)',
              boxShadow: '0 4px 20px rgba(0, 0, 0, 0.2)',
              marginBottom: 12,
            }}
          >
            <VroadBrand inverse size="large" showSubtitle={false} />
          </div>
          <div style={{ color: '#69b1ff', fontSize: 12, fontWeight: 600, letterSpacing: 1.5, textTransform: 'uppercase' }}>
            Cổng Thông Tin Điều Hành & Giám Sát
          </div>
          <Title level={3} style={{ color: '#ffffff', margin: 0, fontWeight: 700, letterSpacing: 0.5 }}>
            QUẢN LÝ KẾT CẤU HẠ TẦNG ĐƯỜNG BỘ
          </Title>
        </Space>
      </div>

      <Card
        style={{
          maxWidth: 440,
          width: '100%',
          borderRadius: 16,
          background: '#ffffff',
          boxShadow: '0 20px 40px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.1)',
          position: 'relative',
          zIndex: 1,
        }}
        bodyStyle={{ padding: '32px 28px' }}
      >
        <div style={{ marginBottom: 24, textAlign: 'center' }}>
          <Title level={4} style={{ margin: 0, color: '#141414', fontWeight: 600 }}>
            Đăng nhập tài khoản
          </Title>
          <Text type="secondary" style={{ fontSize: 13, marginTop: 4, display: 'block' }}>
            Hệ thống Quản lý Kỹ thuật & Giám sát Hạ tầng
          </Text>
        </div>

        {errorMessage && (
          <Alert
            type="error"
            showIcon
            message={errorMessage}
            style={{ marginBottom: 20, borderRadius: 8 }}
            closable
            onClose={() => setErrorMessage(null)}
          />
        )}

        <Form form={form} layout="vertical" onFinish={handleFinish} requiredMark={false}>
          <Form.Item
            name="username"
            label={<Text strong style={{ fontSize: 13 }}>Tên đăng nhập</Text>}
            rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}
            style={{ marginBottom: 18 }}
          >
            <Input
              prefix={<UserOutlined style={{ color: '#8c8c8c' }} />}
              placeholder="Nhập tên đăng nhập"
              size="large"
              autoFocus
              style={{ borderRadius: 8 }}
            />
          </Form.Item>

          <Form.Item
            name="password"
            label={<Text strong style={{ fontSize: 13 }}>Mật khẩu</Text>}
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}
            style={{ marginBottom: 24 }}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: '#8c8c8c' }} />}
              placeholder="Nhập mật khẩu"
              size="large"
              style={{ borderRadius: 8 }}
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 8 }}>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={submitting}
              icon={<CheckCircleOutlined />}
              style={{
                height: 44,
                borderRadius: 8,
                fontSize: 15,
                fontWeight: 600,
                background: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
                boxShadow: '0 4px 12px rgba(22, 119, 255, 0.35)',
                border: 'none',
              }}
            >
              Đăng nhập
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <div style={{ marginTop: 28, textAlign: 'center', position: 'relative', zIndex: 1 }}>
        <Space size={6} style={{ color: 'rgba(255, 255, 255, 0.45)', fontSize: 12 }}>
          <SafetyCertificateOutlined />
          <span>Hệ thống Quản lý Dữ liệu Kết cấu Hạ tầng Giao thông Đường bộ</span>
        </Space>
      </div>
    </div>
  );
};
