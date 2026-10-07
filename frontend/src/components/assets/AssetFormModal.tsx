import React, { useEffect, useState } from 'react';
import {
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  Row,
  Col,
  Alert,
  Typography,
  Space,
  Tag,
  Divider,
  App,
} from 'antd';
import {
  SafetyCertificateOutlined,
  LockOutlined,
  EnvironmentOutlined,
} from '@ant-design/icons';
import {
  DatasetMetadata,
  RecordItem,
  AssetCreateUpdatePayload,
  JsonObject,
  JsonValue,
  createAssetRecord,
  updateAssetRecord,
  getApiErrorMessage,
} from '../../services/api';

const { Text } = Typography;

interface AssetFormValues {
  name: string;
  routeCode?: string;
  routeName?: string;
  kmFrom?: number;
  kmTo?: number;
  lytrinh?: string;
  branchId?: string;
  branchName?: string;
  provinceName?: string;
  districtName?: string;
  townName?: string;
  roadClass?: string;
  roadType?: string;
  state?: string;
  activeStatus?: string;
  maintainValue?: number;
  constructionYear?: number;
  parentId?: string;
  shapeSignId?: string;
  locationId?: string;
  bridgeLength?: number;
  bridgeWidth?: number;
  bridgeType?: string;
}

const isJsonObject = (value: JsonValue | undefined): value is JsonObject =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const asFormString = (value: JsonValue | undefined): string | undefined => {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return undefined;
};

const asFormNumber = (value: JsonValue | undefined): number | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value !== 'string' || value.trim() === '') return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
};

const isFormValidationError = (error: unknown): error is { errorFields: unknown[] } =>
  typeof error === 'object' && error !== null && 'errorFields' in error;

interface AssetFormModalProps {
  visible: boolean;
  mode: 'create' | 'edit';
  datasetKey: string;
  metadata?: DatasetMetadata | null;
  initialRecord?: RecordItem | null;
  onClose: () => void;
  onSuccess: (savedRecord: RecordItem) => void;
}

export const AssetFormModal: React.FC<AssetFormModalProps> = ({
  visible,
  mode,
  datasetKey,
  metadata,
  initialRecord,
  onClose,
  onSuccess,
}) => {
  const { message, notification } = App.useApp();
  const [form] = Form.useForm<AssetFormValues>();
  const [submitting, setSubmitting] = useState<boolean>(false);

  // Extract initial values when editing
  useEffect(() => {
    if (visible) {
      form.resetFields();
      if (mode === 'edit' && initialRecord) {
        const p = initialRecord.payload ?? {};
        const attributes = isJsonObject(p.attributes) ? p.attributes : {};

        form.setFieldsValue({
          name: asFormString(p.name) ?? asFormString(p.fielddisplay) ?? initialRecord.recordKey,
          routeCode: asFormString(p.route_code),
          routeName: asFormString(p.route_name),
          kmFrom: asFormNumber(p.km_from),
          kmTo: asFormNumber(p.km_to),
          lytrinh: asFormString(p.lytrinh),
          branchId: asFormString(p.branch_id),
          branchName: asFormString(p.branch_name),
          provinceName: asFormString(p.province_name),
          districtName: asFormString(p.district_name),
          townName: asFormString(p.town_name),
          roadClass: asFormString(p.road_class),
          roadType: asFormString(p.road_type),
          state: asFormString(p.state),
          activeStatus: asFormString(p.active_status),
          maintainValue: asFormNumber(p.maintain_value),
          constructionYear: asFormNumber(p.construction_year),
          parentId: asFormString(p.parent_id),
          // Override attributes for specific datasets
          shapeSignId: asFormString(attributes.shape_sign_id) ?? asFormString(p.shape_sign_id),
          locationId: asFormString(attributes.location_id) ?? asFormString(p.location_id),
          bridgeLength: asFormNumber(attributes.length) ?? asFormNumber(p.length),
          bridgeWidth: asFormNumber(attributes.width) ?? asFormNumber(p.width),
          bridgeType: asFormString(attributes.type) ?? asFormString(p.type),
        });
      } else {
        // Create mode default values
        form.setFieldsValue({
          branchId: 'kqldb_1',
          roadType: 'Quốc lộ',
          state: 'Approved',
          activeStatus: 'Đang khai thác',
        });
      }
    }
  }, [visible, mode, initialRecord, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      // Package dataset-specific attributes
      const attributes: JsonObject = {};
      if (values.shapeSignId) attributes.shape_sign_id = values.shapeSignId;
      if (values.locationId) attributes.location_id = values.locationId;
      if (values.bridgeLength !== undefined) attributes.length = values.bridgeLength;
      if (values.bridgeWidth !== undefined) attributes.width = values.bridgeWidth;
      if (values.bridgeType) attributes.type = values.bridgeType;

      const stateName = values.state === 'Approved'
        ? 'Đã duyệt'
        : values.state === 'Pending'
          ? 'Chờ duyệt'
          : values.state === 'Draft'
            ? 'Dự thảo'
            : undefined;

      const payload: AssetCreateUpdatePayload = {
        name: values.name.trim(),
        routeCode: values.routeCode || undefined,
        routeName: values.routeName || undefined,
        kmFrom: values.kmFrom !== undefined ? Number(values.kmFrom) : undefined,
        kmTo: values.kmTo !== undefined ? Number(values.kmTo) : undefined,
        lytrinh: values.lytrinh || undefined,
        branchId: values.branchId || undefined,
        provinceName: values.provinceName || undefined,
        districtName: values.districtName || undefined,
        townName: values.townName || undefined,
        roadClass: values.roadClass || undefined,
        roadType: values.roadType || undefined,
        state: values.state || undefined,
        stateName,
        activeStatus: values.activeStatus || undefined,
        maintainValue: values.maintainValue !== undefined ? Number(values.maintainValue) : undefined,
        constructionYear: values.constructionYear ? Number(values.constructionYear) : undefined,
        parentId: values.parentId || undefined,
        attributes: Object.keys(attributes).length > 0 ? attributes : undefined,
      };

      if (mode === 'edit' && initialRecord) {
        // Optimistic locking version
        const version = asFormNumber(initialRecord.payload?.version);
        if (version === undefined) {
          notification.error({
            message: 'Không thể cập nhật bản ghi',
            description: 'API không trả về phiên bản khóa lạc quan. Vui lòng tải lại dữ liệu trước khi sửa.',
          });
          return;
        }
        payload.version = version;

        const updated = await updateAssetRecord(datasetKey, initialRecord.recordKey, payload);
        message.success(`Đã cập nhật thành công bản ghi: ${updated.recordKey}`);
        onSuccess(updated);
      } else {
        const created = await createAssetRecord(datasetKey, payload);
        message.success(`Đã thêm mới thành công tài sản: ${created.recordKey}`);
        onSuccess(created);
      }
      onClose();
    } catch (err: unknown) {
      if (isFormValidationError(err)) {
        // Client form validation errors
        return;
      }

      const status = typeof err === 'object' && err !== null && 'response' in err
        ? (err as { response?: { status?: number } }).response?.status
        : undefined;
      if (status === 409) {
        notification.error({
          message: 'Xung đột phiên bản (Optimistic Lock Conflict)',
          description:
            getApiErrorMessage(err) ||
            'Bản ghi đã được sửa đổi bởi người dùng khác. Vui lòng tải lại trang và thử lại.',
          duration: 8,
        });
      } else if (status === 403) {
        notification.error({
          message: 'Từ chối quyền truy cập (403 Forbidden)',
          description: getApiErrorMessage(err),
        });
      } else {
        notification.error({
          message: mode === 'create' ? 'Lỗi tạo mới tài sản' : 'Lỗi cập nhật tài sản',
          description: getApiErrorMessage(err),
        });
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={
        <Space align="center">
          <SafetyCertificateOutlined style={{ color: '#1677ff' }} />
          <span>
            {mode === 'create' ? 'Thêm mới Bản ghi Tài sản' : 'Chỉnh sửa Bản ghi Tài sản'} [
            <strong style={{ color: '#003a8c' }}>{metadata?.datasetName || datasetKey}</strong>]
          </span>
        </Space>
      }
      open={visible}
      onCancel={onClose}
      onOk={handleSubmit}
      okText={mode === 'create' ? 'Tạo mới' : 'Lưu thay đổi'}
      cancelText="Hủy"
      confirmLoading={submitting}
      width={780}
      destroyOnHidden
      maskClosable={false}
    >
      {/* Notice about system protection and geometry rules */}
      <Alert
        message="Bảo vệ Dữ liệu Hệ thống & Hình học Không gian"
        description="Các trường hệ thống (ID, mã định danh, nguồn thô raw, khóa phiên bản version) và tọa độ GIS được bảo vệ tự động, tuyệt đối không chỉnh sửa thủ công để bảo đảm toàn vẹn dữ liệu."
        type="info"
        showIcon
        icon={<LockOutlined />}
        style={{ marginBottom: 16 }}
      />

      {mode === 'edit' && initialRecord && (
        <div style={{ marginBottom: 16, padding: '8px 12px', background: '#f5f5f5', borderRadius: 6 }}>
          <Row gutter={[12, 6]}>
            <Col span={12}>
              <Text type="secondary">Mã bản ghi (record_key): </Text>
              <Text strong>{initialRecord.recordKey}</Text>
            </Col>
            <Col span={12}>
              <Text type="secondary">Phiên bản Khóa lạc quan (Version): </Text>
              {asFormNumber(initialRecord.payload?.version) !== undefined ? (
                <Tag color="purple">v{asFormNumber(initialRecord.payload?.version)}</Tag>
              ) : (
                <Tag color="error">Thiếu dữ liệu version</Tag>
              )}
            </Col>
            <Col span={12}>
              <Text type="secondary">Tập dữ liệu: </Text>
              <Tag color="blue">{datasetKey}</Tag>
            </Col>
            <Col span={12}>
              <Text type="secondary">Hình học: </Text>
              <Tag color="cyan" icon={<EnvironmentOutlined />}>
                PostGIS (Bảo toàn)
              </Tag>
            </Col>
          </Row>
        </div>
      )}

      <Form
        form={form}
        layout="vertical"
        initialValues={{
          branchId: 'kqldb_1',
          roadType: 'Quốc lộ',
          state: 'Approved',
          activeStatus: 'Đang khai thác',
        }}
      >
        {/* Core Identity & Name */}
        <Row gutter={16}>
          <Col span={24}>
            <Form.Item
              name="name"
              label="Tên Công trình / Tài sản Hạ tầng"
              rules={[
                { required: true, message: 'Vui lòng nhập tên công trình/tài sản' },
                { max: 500, message: 'Tên không được vượt quá 500 ký tự' },
              ]}
            >
              <Input placeholder="Ví dụ: Biển báo P.102 Cấm đi ngược chiều, Cầu Thăng Long..." />
            </Form.Item>
          </Col>
        </Row>

        {/* Route and Linear Referencing (LRS) */}
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item
              name="routeCode"
              label="Mã Tuyến đường"
              rules={[{ max: 100, message: 'Tối đa 100 ký tự' }]}
            >
              <Input placeholder="Ví dụ: QL.1, QL.2, QL.5..." />
            </Form.Item>
          </Col>
          <Col span={16}>
            <Form.Item
              name="routeName"
              label="Tên Tuyến đường"
              rules={[{ max: 255, message: 'Tối đa 255 ký tự' }]}
            >
              <Input placeholder="Ví dụ: Quốc lộ 1 đoạn Hà Nội - Lạng Sơn..." />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={8}>
            <Form.Item
              name="kmFrom"
              label="Lý trình Từ (Km số)"
              rules={[
                { type: 'number', min: 0, message: 'Lý trình phải >= 0' },
              ]}
            >
              <InputNumber<number>
                style={{ width: '100%' }}
                placeholder="128.500"
                precision={3}
                step={0.1}
                min={0}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item
              name="kmTo"
              label="Lý trình Đến (Km số)"
              dependencies={['kmFrom']}
              rules={[
                { type: 'number', min: 0, message: 'Lý trình phải >= 0' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    const kmFrom = getFieldValue('kmFrom');
                    if (value !== undefined && kmFrom !== undefined && value < kmFrom) {
                      return Promise.reject(new Error('Lý trình đến phải >= Lý trình từ'));
                    }
                    return Promise.resolve();
                  },
                }),
              ]}
            >
              <InputNumber
                style={{ width: '100%' }}
                placeholder="129.000"
                precision={3}
                step={0.1}
                min={0}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="lytrinh" label="Chuỗi Lý trình">
              <Input placeholder="Ví dụ: Km 128 + 500" />
            </Form.Item>
          </Col>
        </Row>

        {/* Administration and Management Scope */}
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item
              name="branchId"
              label="Đơn vị Quản lý (Khu QLĐB)"
              rules={[{ required: true, message: 'Vui lòng chọn đơn vị quản lý' }]}
            >
              <Select
                options={[
                  { value: 'kqldb_1', label: 'Khu Quản lý đường bộ I' },
                  { value: 'kqldb_2', label: 'Khu Quản lý đường bộ II' },
                  { value: 'kqldb_3', label: 'Khu Quản lý đường bộ III' },
                  { value: 'kqldb_4', label: 'Khu Quản lý đường bộ IV' },
                  { value: 'cdb_vn', label: 'Cục Đường bộ Việt Nam' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="provinceName" label="Tỉnh / Thành phố">
              <Input placeholder="Ví dụ: Hà Nội, Hải Phòng..." />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="districtName" label="Quận / Huyện">
              <Input placeholder="Ví dụ: Thường Tín, Ba Vì..." />
            </Form.Item>
          </Col>
        </Row>

        {/* Operational Status & Technical Properties */}
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name="state" label="Trạng thái Phê duyệt">
              <Select
                options={[
                  { value: 'Approved', label: 'Đã duyệt' },
                  { value: 'Pending', label: 'Chờ duyệt' },
                  { value: 'Drafting', label: 'Dự thảo' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="activeStatus" label="Tình trạng Khai thác">
              <Select
                options={[
                  { value: 'Đang khai thác', label: 'Đang khai thác' },
                  { value: 'Chờ sửa chữa', label: 'Chờ sửa chữa' },
                  { value: 'Hư hỏng nặng', label: 'Hư hỏng nặng' },
                  { value: 'Tháo dỡ/Thu hồi', label: 'Tháo dỡ/Thu hồi' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="roadType" label="Loại đường">
              <Select
                options={[
                  { value: 'Quốc lộ', label: 'Quốc lộ' },
                  { value: 'Đường cao tốc', label: 'Đường cao tốc' },
                  { value: 'Đường gom', label: 'Đường gom' },
                  { value: 'Tỉnh lộ', label: 'Tỉnh lộ' },
                ]}
              />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name="maintainValue" label="Kinh phí Bảo trì (VNĐ)">
              <InputNumber<number>
                style={{ width: '100%' }}
                formatter={(val) => `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                parser={(val) => {
                  const normalized = (val ?? '').replace(/\$\s?|(,*)/g, '');
                  return normalized === '' ? Number.NaN : Number(normalized);
                }}
                placeholder="25,000,000"
                min={0}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="constructionYear" label="Năm Xây dựng / Đưa vào SD">
              <InputNumber
                style={{ width: '100%' }}
                min={1900}
                max={new Date().getFullYear()}
                placeholder="2020"
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="roadClass" label="Cấp kỹ thuật đường">
              <Input placeholder="Ví dụ: Cấp I - II, Cấp III..." />
            </Form.Item>
          </Col>
        </Row>

        {/* Dataset-specific Schema Overrides */}
        {datasetKey === 'tbl_road_sign' && (
          <>
            <Divider orientation="left" plain>
              <Text strong style={{ fontSize: 13, color: '#1677ff' }}>
                Thuộc tính Đặc thù Biển báo (QCVN 41:2019/BGTVT)
              </Text>
            </Divider>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="shapeSignId" label="Hình dạng Biển báo">
                  <Select
                    placeholder="Chọn hình dạng biển"
                    allowClear
                    options={[
                      { value: '1', label: 'Hình tròn (Biển cấm, Biển hiệu lệnh)' },
                      { value: '2', label: 'Hình tam giác đều (Biển cảnh báo)' },
                      { value: '3', label: 'Hình vuông / Chữ nhật (Biển chỉ dẫn)' },
                      { value: '4', label: 'Biển phụ' },
                    ]}
                  />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="locationId" label="Vị trí Đặt biển">
                  <Select
                    placeholder="Chọn vị trí lắp đặt"
                    allowClear
                    options={[
                      { value: '1', label: 'Lề đường bên phải' },
                      { value: '2', label: 'Dải phân cách giữa' },
                      { value: '3', label: 'Cần vươn (Overhead mast)' },
                      { value: '4', label: 'Giá long môn (Gantry)' },
                    ]}
                  />
                </Form.Item>
              </Col>
            </Row>
          </>
        )}

        {datasetKey === 'tbl_bridge' && (
          <>
            <Divider orientation="left" plain>
              <Text strong style={{ fontSize: 13, color: '#1677ff' }}>
                Thông số Kỹ thuật Cầu đường bộ (BMS)
              </Text>
            </Divider>
            <Row gutter={16}>
              <Col span={8}>
                <Form.Item name="bridgeLength" label="Chiều dài Cầu (m)">
                  <InputNumber style={{ width: '100%' }} precision={2} min={0} placeholder="Ví dụ: 120.5" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="bridgeWidth" label="Chiều rộng Cầu (m)">
                  <InputNumber style={{ width: '100%' }} precision={2} min={0} placeholder="Ví dụ: 16.0" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="bridgeType" label="Loại Kết cấu Nhịp">
                  <Select
                    placeholder="Chọn kết cấu"
                    allowClear
                    options={[
                      { value: 'BTCT DƯL', label: 'Bê tông cốt thép dự ứng lực' },
                      { value: 'Thép', label: 'Dầm thép' },
                      { value: 'Vòm BTCT', label: 'Vòm bê tông cốt thép' },
                      { value: 'Dây văng', label: 'Cầu dây văng' },
                    ]}
                  />
                </Form.Item>
              </Col>
            </Row>
          </>
        )}
      </Form>
    </Modal>
  );
};
