import React, { ReactNode } from 'react';
import { Modal, Typography } from 'antd';
import { ExclamationCircleOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface ConfirmModalProps {
  open: boolean;
  title: string;
  content: string | ReactNode;
  onConfirm: () => void | Promise<void>;
  onCancel: () => void;
  confirmLoading?: boolean;
  okText?: string;
  cancelText?: string;
  danger?: boolean;
}

export const ConfirmModal: React.FC<ConfirmModalProps> = ({
  open,
  title,
  content,
  onConfirm,
  onCancel,
  confirmLoading = false,
  okText = 'Xác nhận',
  cancelText = 'Hủy bỏ',
  danger = true,
}) => {
  return (
    <Modal
      open={open}
      title={
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <ExclamationCircleOutlined style={{ color: danger ? '#ff4d4f' : '#faad14', fontSize: 20 }} />
          <span>{title}</span>
        </span>
      }
      onOk={onConfirm}
      onCancel={onCancel}
      confirmLoading={confirmLoading}
      okText={okText}
      cancelText={cancelText}
      okButtonProps={{ danger }}
      destroyOnHidden
    >
      <div style={{ padding: '12px 0' }}>
        {typeof content === 'string' ? <Text>{content}</Text> : content}
      </div>
    </Modal>
  );
};
