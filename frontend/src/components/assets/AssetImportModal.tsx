import React, { useState } from 'react';
import {
  Modal,
  Input,
  Select,
  Button,
  Space,
  Typography,
  Alert,
  Table,
  Tag,
  Row,
  Col,
  InputNumber,
  Switch,
  Upload,
  App,
} from 'antd';
import {
  UploadOutlined,
  EyeOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  RollbackOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import {
  ImportPreviewResponse,
  ImportExecutionResponse,
  previewImportDataset,
  executeImportDataset,
  getApiErrorMessage,
} from '../../services/api';

const { Text } = Typography;
const { TextArea } = Input;

interface AssetImportModalProps {
  visible: boolean;
  datasetKey: string;
  datasetName?: string;
  onClose: () => void;
  onSuccess: () => void;
}

export const AssetImportModal: React.FC<AssetImportModalProps> = ({
  visible,
  datasetKey,
  datasetName,
  onClose,
  onSuccess,
}) => {
  const { message, notification } = App.useApp();
  const [format, setFormat] = useState<'CSV' | 'JSON'>('CSV');
  const [content, setContent] = useState<string>('');
  const [batchSize, setBatchSize] = useState<number>(500);
  const [dryRunOnly, setDryRunOnly] = useState<boolean>(false);

  const [previewing, setPreviewing] = useState<boolean>(false);
  const [previewData, setPreviewData] = useState<ImportPreviewResponse | null>(null);

  const [executing, setExecuting] = useState<boolean>(false);
  const [executionResult, setExecutionResult] = useState<ImportExecutionResponse | null>(null);

  // Sample templates for user convenience
  const sampleCsv = `name,route_code,route_name,km_from,km_to,branch_id,state\nBiển báo P.102 Cấm đi ngược chiều,QL.1,Quốc lộ 1,128.500,128.550,kqldb_1,Approved\nBiển báo W.201a Chỗ ngoặt nguy hiểm,QL.1,Quốc lộ 1,129.200,129.250,kqldb_1,Approved`;
  const sampleJson = `[\n  {\n    "name": "Biển báo P.102 Cấm đi ngược chiều",\n    "route_code": "QL.1",\n    "km_from": 128.5,\n    "km_to": 128.55,\n    "branch_id": "kqldb_1"\n  }\n]`;

  const handlePreview = async () => {
    if (!content.trim()) {
      message.warning('Vui lòng nhập hoặc tải lên nội dung tệp CSV hoặc JSON');
      return;
    }

    try {
      setPreviewing(true);
      setExecutionResult(null);
      const res = await previewImportDataset(datasetKey, content.trim(), format);
      setPreviewData(res);
      if (res.canProceed) {
        message.success(`Kiểm tra dữ liệu thành công: ${res.validRows}/${res.totalRows} dòng hợp lệ.`);
      } else {
        message.error(`Dữ liệu có ${res.errorRows} dòng vi phạm quy tắc. Vui lòng kiểm tra danh sách lỗi bên dưới.`);
      }
    } catch (err: unknown) {
      notification.error({
        message: 'Lỗi kiểm tra tệp nạp (Dry-run Error)',
        description: getApiErrorMessage(err),
      });
    } finally {
      setPreviewing(false);
    }
  };

  const handleExecute = async () => {
    if (!content.trim()) {
      message.warning('Vui lòng nhập nội dung tệp nạp');
      return;
    }

    try {
      setExecuting(true);
      const res = await executeImportDataset(datasetKey, {
        content: content.trim(),
        format,
        dryRun: dryRunOnly,
        batchSize,
      });
      setExecutionResult(res);

      if (res.rolledBack) {
        notification.error({
          message: 'Lô giao dịch đã bị Rollback an toàn',
          description: res.message,
          icon: <RollbackOutlined style={{ color: '#ff4d4f' }} />,
          duration: 10,
        });
      } else if (res.dryRun) {
        message.info(res.message);
      } else {
        message.success(res.message);
        onSuccess();
      }
    } catch (err: unknown) {
      notification.error({
        message: 'Lỗi thực thi nạp dữ liệu',
        description: getApiErrorMessage(err),
      });
    } finally {
      setExecuting(false);
    }
  };

  const handleFileUpload = (file: File) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const text = e.target?.result as string;
      setContent(text);
      if (file.name.toLowerCase().endsWith('.json')) {
        setFormat('JSON');
      } else {
        setFormat('CSV');
      }
      message.success(`Đã đọc tệp ${file.name} (${text.length} ký tự)`);
    };
    reader.readAsText(file);
    return false; // Prevent auto upload
  };

  const previewColumns = previewData?.previewRows?.[0]
    ? Object.keys(previewData.previewRows[0]).map((key) => ({
        title: key,
        dataIndex: key,
        key,
        ellipsis: true,
        render: (val: unknown) => {
          if (val === null || val === undefined) return 'Chưa có dữ liệu';
          return typeof val === 'object' ? JSON.stringify(val) : String(val);
        },
      }))
    : [];

  return (
    <Modal
      title={
        <Space align="center">
          <UploadOutlined style={{ color: '#1677ff' }} />
          <span>
            Nhập Dữ liệu CSV / JSON cho [<strong style={{ color: '#003a8c' }}>{datasetName || datasetKey}</strong>]
          </span>
        </Space>
      }
      open={visible}
      onCancel={onClose}
      width={860}
      footer={[
        <Button key="close" onClick={onClose}>
          Đóng
        </Button>,
        <Button
          key="preview"
          icon={<EyeOutlined />}
          onClick={handlePreview}
          loading={previewing}
        >
          Kiểm tra & Xem trước
        </Button>,
        <Button
          key="submit"
          type="primary"
          icon={dryRunOnly ? <EyeOutlined /> : <CheckCircleOutlined />}
          onClick={handleExecute}
          loading={executing}
          disabled={!content.trim() || (previewData !== null && !previewData.canProceed && !dryRunOnly)}
        >
          {dryRunOnly ? 'Chạy thử (Dry-run)' : 'Thực hiện Nạp Chính thức'}
        </Button>,
      ]}
      destroyOnHidden
    >
      <Alert
        message="Nguyên tắc Nạp An toàn & Toàn vẹn Dữ liệu"
        description="Nạp dữ liệu tác động trực tiếp vào bảng curated asset_record theo các batch độc lập. Nếu phát hiện vi phạm ràng buộc dữ liệu tại bất kỳ batch nào, toàn bộ batch đó sẽ được rollback tự động để bảo đảm zero data loss."
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Row gutter={[16, 12]} style={{ marginBottom: 12 }} align="middle">
        <Col span={6}>
          <Text strong>Định dạng dữ liệu: </Text>
          <Select
            value={format}
            onChange={(val) => setFormat(val)}
            style={{ width: '100%', marginTop: 4 }}
            options={[
              { value: 'CSV', label: 'CSV (Dấu phẩy phân cách)' },
              { value: 'JSON', label: 'JSON (Mảng đối tượng)' },
            ]}
          />
        </Col>
        <Col span={6}>
          <Text strong>Kích thước Lô (Batch size): </Text>
          <InputNumber
            value={batchSize}
            onChange={(val) => setBatchSize(val || 500)}
            min={10}
            max={2000}
            step={100}
            style={{ width: '100%', marginTop: 4 }}
          />
        </Col>
        <Col span={6}>
          <Text strong>Chế độ Chạy thử: </Text>
          <div style={{ marginTop: 6 }}>
            <Switch
              checked={dryRunOnly}
              onChange={(checked) => setDryRunOnly(checked)}
              checkedChildren="Dry-run"
              unCheckedChildren="Live DB"
            />
          </div>
        </Col>
        <Col span={6}>
          <Upload beforeUpload={handleFileUpload} showUploadList={false} accept=".csv,.txt,.json">
            <Button icon={<FileTextOutlined />} style={{ marginTop: 20, width: '100%' }}>
              Chọn tệp từ máy tính
            </Button>
          </Upload>
        </Col>
      </Row>

      <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text strong>Nội dung tệp dữ liệu ({format}):</Text>
        <Space>
          <Button
            type="link"
            size="small"
            onClick={() => setContent(format === 'CSV' ? sampleCsv : sampleJson)}
          >
            Chèn dữ liệu mẫu
          </Button>
          <Button type="link" size="small" danger onClick={() => setContent('')}>
            Xóa trắng
          </Button>
        </Space>
      </div>

      <TextArea
        rows={7}
        value={content}
        onChange={(e) => {
          setContent(e.target.value);
          setPreviewData(null);
        }}
        placeholder={format === 'CSV' ? sampleCsv : sampleJson}
        style={{ fontFamily: 'monospace', fontSize: 12, marginBottom: 16 }}
      />

      {/* Execution Result Banner */}
      {executionResult && (
        <Alert
          message={executionResult.message}
          type={executionResult.rolledBack ? 'error' : executionResult.dryRun ? 'info' : 'success'}
          showIcon
          style={{ marginBottom: 16 }}
          description={
            <Row gutter={16} style={{ marginTop: 8 }}>
              <Col span={6}>Tổng xử lý: <strong>{executionResult.totalProcessed}</strong></Col>
              <Col span={6}>Thêm mới: <strong style={{ color: '#52c41a' }}>{executionResult.insertedCount}</strong></Col>
              <Col span={6}>Cập nhật: <strong style={{ color: '#1677ff' }}>{executionResult.updatedCount}</strong></Col>
              <Col span={6}>Lỗi/Rollback: <strong style={{ color: '#ff4d4f' }}>{executionResult.failedCount}</strong></Col>
            </Row>
          }
        />
      )}

      {/* Preview & Dry-run Result Section */}
      {previewData && (
        <div style={{ marginTop: 12 }}>
          <Row gutter={16} style={{ marginBottom: 12 }}>
            <Col span={8}>
              <Tag color="blue" style={{ padding: '4px 8px', fontSize: 13 }}>
                Tổng số dòng phân tích: <strong>{previewData.totalRows}</strong>
              </Tag>
            </Col>
            <Col span={8}>
              <Tag color="green" icon={<CheckCircleOutlined />} style={{ padding: '4px 8px', fontSize: 13 }}>
                Dòng hợp lệ: <strong>{previewData.validRows}</strong>
              </Tag>
            </Col>
            <Col span={8}>
              <Tag
                color={previewData.errorRows > 0 ? 'red' : 'default'}
                icon={<CloseCircleOutlined />}
                style={{ padding: '4px 8px', fontSize: 13 }}
              >
                Dòng lỗi: <strong>{previewData.errorRows}</strong>
              </Tag>
            </Col>
          </Row>

          {previewData.errors.length > 0 && (
            <div style={{ marginBottom: 16 }}>
              <Text strong type="danger">
                Chi tiết các dòng vi phạm kiểm tra tính hợp lệ:
              </Text>
              <Table
                size="small"
                dataSource={previewData.errors.map((err, idx) => ({ ...err, key: idx }))}
                columns={[
                  { title: 'Dòng', dataIndex: 'rowNumber', key: 'rowNumber', width: 80 },
                  { title: 'Trường vi phạm', dataIndex: 'field', key: 'field', width: 140 },
                  { title: 'Nội dung lỗi', dataIndex: 'message', key: 'message' },
                ]}
                pagination={{ pageSize: 5, size: 'small' }}
                style={{ marginTop: 8 }}
              />
            </div>
          )}

          {previewData.previewRows.length > 0 && (
            <div>
              <Text strong style={{ color: '#003a8c' }}>
                Bản xem trước dữ liệu (10 dòng đầu tiên):
              </Text>
              <Table
                size="small"
                dataSource={previewData.previewRows.map((r, idx) => ({ ...r, key: idx }))}
                columns={previewColumns}
                pagination={false}
                scroll={{ x: 'max-content' }}
                style={{ marginTop: 8 }}
              />
            </div>
          )}
        </div>
      )}
    </Modal>
  );
};
