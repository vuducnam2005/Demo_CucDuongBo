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
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LoginParams } from '../services/authApi';
import { getApiErrorMessage, getHttpStatus } from '../services/api';
import { VroadBrand } from '../components/brand/VroadBrand';

const { Title, Text, Paragraph } = Typography;

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
        setErrorMessage(
          'Tài khoản tạm thời bị khóa do nhập sai mật khẩu quá 5 lần liên tiếp. Vui lòng thử lại sau 15 phút.'
        );
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
        background: 'linear-gradient(135deg, #001529 0%, #002766 50%, #003a8c 100%)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        padding: '24px 16px',
      }}
    >
      <div style={{ maxWidth: 440, width: '100%', marginBottom: 24, textAlign: 'center' }}>
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <div
            style={{
              display: 'inline-flex',
              padding: 12,
              borderRadius: '50%',
              background: 'rgba(255, 255, 255, 0.1)',
              backdropFilter: 'blur(8px)',
              marginBottom: 8,
            }}
          >
            <VroadBrand inverse />
          </div>
          <Title level={3} style={{ color: '#ffffff', margin: 0, fontWeight: 700 }}>
            QUẢN LÝ HẠ TẦNG ĐƯỜNG BỘ
          </Title>
          <Text style={{ color: 'rgba(255, 255, 255, 0.75)', fontSize: 14 }}>
            Bản demo độc lập — không phải trang đăng nhập của Cục Đường bộ Việt Nam
          </Text>
        </Space>
      </div>

      <Card
        style={{
          maxWidth: 440,
          width: '100%',
          borderRadius: 12,
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.25)',
          border: '1px solid rgba(255, 255, 255, 0.15)',
        }}
      >
        <div style={{ marginBottom: 20 }}>
          <Title level={4} style={{ margin: 0 }}>
            Đăng nhập hệ thống
          </Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            Sử dụng tài khoản demo do quản trị viên cục bộ cấp
          </Text>
        </div>

        {errorMessage && (
          <Alert
            type="error"
            showIcon
            message={errorMessage}
            style={{ marginBottom: 20, borderRadius: 6 }}
            closable
            onClose={() => setErrorMessage(null)}
          />
        )}

        <Form form={form} layout="vertical" onFinish={handleFinish}>
          <Form.Item
            name="username"
            label="Tên đăng nhập"
            rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}
          >
            <Input
              prefix={<UserOutlined style={{ color: '#bfbfbf' }} />}
              placeholder="Ví dụ: admin hoặc manager_demo"
              size="large"
              autoFocus
            />
          </Form.Item>

          <Form.Item
            name="password"
            label="Mật khẩu"
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: '#bfbfbf' }} />}
              placeholder="Nhập mật khẩu"
              size="large"
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 12 }}>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={submitting}
              icon={<CheckCircleOutlined />}
            >
              Đăng nhập
            </Button>
          </Form.Item>
        </Form>

        <Text type="secondary" style={{ display: 'block', marginTop: 16, textAlign: 'center' }}>
          Vui lòng sử dụng tài khoản được cấp bởi quản trị viên hệ thống.
        </Text>
      </Card>

      <div style={{ marginTop: 24, textAlign: 'center', maxWidth: 440 }}>
        <Paragraph style={{ color: 'rgba(255, 255, 255, 0.65)', fontSize: 12, margin: 0 }}>
          Đây là môi trường thử nghiệm độc lập. Dữ liệu nguồn chưa được duyệt QC và không được
          coi là số liệu công bố chính thức.
        </Paragraph>
      </div>
    </div>
  );
};
