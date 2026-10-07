import { Component, ErrorInfo, ReactNode } from 'react';
import { Result, Button, Typography, Card } from 'antd';
import { ReloadOutlined, HomeOutlined } from '@ant-design/icons';
import { reportClientError } from '../../services/clientLogger';

const { Paragraph, Text } = Typography;

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
  errorId: string | null;
}

let errorSequence = 0;

const createSafeErrorId = (): string => {
  errorSequence += 1;
  return `ui-${Date.now().toString(36)}-${errorSequence.toString(36)}`;
};

export class ErrorBoundary extends Component<Props, State> {
  public override state: State = {
    hasError: false,
    error: null,
    errorInfo: null,
    errorId: null,
  };

  public static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error, errorId: createSafeErrorId() };
  }

  public override componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    const errorId = this.state.errorId ?? createSafeErrorId();
    this.setState({ errorInfo, errorId });
    reportClientError(error, {
      source: 'ErrorBoundary',
      errorId,
      hasComponentStack: Boolean(errorInfo.componentStack),
    });
  }

  private handleReset = (): void => {
    this.setState({ hasError: false, error: null, errorInfo: null, errorId: null });
    window.location.reload();
  };

  private handleGoHome = (): void => {
    this.setState({ hasError: false, error: null, errorInfo: null, errorId: null });
    window.location.href = '/dashboard';
  };

  public override render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div style={{ padding: '40px 24px', maxWidth: 800, margin: '0 auto' }}>
          <Result
            status="500"
            title="Đã xảy ra lỗi giao diện"
            subTitle="Hệ thống gặp sự cố không mong muốn trong khi kết xuất thành phần này. Vui lòng tải lại trang hoặc quay về Bảng điều hành."
            extra={[
              <Button type="primary" key="reload" icon={<ReloadOutlined />} onClick={this.handleReset}>
                Tải lại trang
              </Button>,
              <Button key="home" icon={<HomeOutlined />} onClick={this.handleGoHome}>
                Về Bảng điều hành
              </Button>,
            ]}
          >
            {this.state.errorId && (
              <Typography.Text type="secondary" data-testid="error-correlation-id">
                Mã lỗi: {this.state.errorId}
              </Typography.Text>
            )}
            {import.meta.env.DEV && this.state.error && (
              <Card
                size="small"
                title="Chi tiết lỗi kỹ thuật"
                style={{ textAlign: 'left', marginTop: 16, background: '#fff1f0', borderColor: '#ffa39e' }}
              >
                <Paragraph>
                  <Text strong type="danger">
                    {this.state.error.name}: {this.state.error.message}
                  </Text>
                </Paragraph>
                {this.state.errorInfo?.componentStack && (
                  <pre
                    style={{
                      maxHeight: 180,
                      overflow: 'auto',
                      fontSize: 12,
                      background: '#fff',
                      padding: 8,
                      borderRadius: 4,
                    }}
                  >
                    {this.state.errorInfo.componentStack}
                  </pre>
                )}
              </Card>
            )}
          </Result>
        </div>
      );
    }

    return this.props.children;
  }
}
