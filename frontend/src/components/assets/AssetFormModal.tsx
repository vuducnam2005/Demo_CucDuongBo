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
import { applyCoordinatePatch } from './greenTreeForm';

const { Text } = Typography;
const { TextArea } = Input;

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
  treeCode?: string;
  speciesName?: string;
  scientificName?: string;
  diameterCm?: number;
  heightM?: number;
  canopyDiameterM?: number;
  plantingYear?: number;
  healthStatus?: string;
  riskLevel?: string;
  longitude?: number;
  latitude?: number;
  notes?: string;
  assetCategory?: string;
  assetTypeName?: string;
  description?: string;
  quantity?: number;
  unit?: string;
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
          treeCode: asFormString(attributes.tree_code) ?? asFormString(p.tree_code),
          speciesName: asFormString(attributes.species_name) ?? asFormString(p.species_name),
          scientificName: asFormString(attributes.scientific_name) ?? asFormString(p.scientific_name),
          diameterCm: asFormNumber(attributes.diameter_cm) ?? asFormNumber(p.diameter_cm),
          heightM: asFormNumber(attributes.height_m) ?? asFormNumber(p.height_m),
          canopyDiameterM: asFormNumber(attributes.canopy_diameter_m) ?? asFormNumber(p.canopy_diameter_m),
          plantingYear: asFormNumber(attributes.planting_year) ?? asFormNumber(p.planting_year),
          healthStatus: asFormString(attributes.health_status) ?? asFormString(p.health_status),
          riskLevel: asFormString(attributes.risk_level) ?? asFormString(p.risk_level),
          longitude: asFormNumber(attributes.longitude) ?? asFormNumber(p.longitude),
          latitude: asFormNumber(attributes.latitude) ?? asFormNumber(p.latitude),
          notes: asFormString(attributes.notes) ?? asFormString(p.notes),
          assetCategory: asFormString(attributes.asset_category) ?? asFormString(p.asset_category),
          assetTypeName: asFormString(attributes.asset_type_name) ?? asFormString(p.asset_type_name),
          description: asFormString(attributes.description) ?? asFormString(p.description),
          quantity: asFormNumber(attributes.quantity) ?? asFormNumber(p.quantity),
          unit: asFormString(attributes.unit) ?? asFormString(p.unit),
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
      const currentAttributes = isJsonObject(initialRecord?.payload?.attributes)
        ? initialRecord.payload.attributes
        : {};
      const attributes: JsonObject = { ...currentAttributes };
      const setAttribute = (key: string, value: JsonValue | undefined) => {
        if (value === undefined || value === null || value === '') delete attributes[key];
        else attributes[key] = value;
      };
      setAttribute('shape_sign_id', values.shapeSignId);
      setAttribute('location_id', values.locationId);
      setAttribute('length', values.bridgeLength);
      setAttribute('width', values.bridgeWidth);
      setAttribute('type', values.bridgeType);
      setAttribute('tree_code', values.treeCode);
      setAttribute('species_name', values.speciesName);
      setAttribute('scientific_name', values.scientificName);
      setAttribute('diameter_cm', values.diameterCm);
      setAttribute('height_m', values.heightM);
      setAttribute('canopy_diameter_m', values.canopyDiameterM);
      setAttribute('planting_year', values.plantingYear);
      setAttribute('health_status', values.healthStatus);
      setAttribute('risk_level', values.riskLevel);
      const initialPayload = initialRecord?.payload ?? {};
      const hadCoordinates = initialPayload.longitude !== undefined
        || initialPayload.latitude !== undefined
        || currentAttributes.longitude !== undefined
        || currentAttributes.latitude !== undefined;
      applyCoordinatePatch(attributes, {
        datasetKey,
        mode,
        hadCoordinates,
        longitude: values.longitude,
        latitude: values.latitude,
      });
      setAttribute('notes', values.notes);
      setAttribute('asset_category', values.assetCategory);
      setAttribute('asset_type_name', values.assetTypeName);
      setAttribute('description', values.description);
      setAttribute('quantity', values.quantity);
      setAttribute('unit', values.unit);

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
        message.success(`Đã thêm mới thành công bản ghi: ${created.recordKey}`);
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
            {mode === 'create' ? 'Thêm mới Bản ghi' : 'Chỉnh sửa Bản ghi'} [
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
              label={datasetKey === 'green_trees' ? 'Tên cây / Nhãn quản lý' : 'Tên Công trình / Tài sản Hạ tầng'}
              rules={[
                { required: true, message: 'Vui lòng nhập tên công trình/tài sản' },
                { max: 500, message: 'Tên không được vượt quá 500 ký tự' },
              ]}
            >
              <Input placeholder={datasetKey === 'green_trees'
                ? 'Ví dụ: Cây sao đen số 01 - Km 12+300'
                : 'Ví dụ: Biển báo P.102 Cấm đi ngược chiều, Cầu Thăng Long...'} />
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

        {datasetKey === 'green_trees' && (
          <>
            <Divider orientation="left" plain>
              <Text strong style={{ fontSize: 13, color: '#237804' }}>
                Thông tin chuyên ngành cây xanh
              </Text>
            </Divider>
            <Row gutter={16}>
              <Col span={8}>
                <Form.Item name="treeCode" label="Mã cây">
                  <Input placeholder="Ví dụ: CX-QL1-0001" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="speciesName" label="Tên loài cây">
                  <Input placeholder="Ví dụ: Sao đen" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="scientificName" label="Tên khoa học">
                  <Input placeholder="Ví dụ: Hopea odorata" />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={16}>
              <Col span={6}>
                <Form.Item name="diameterCm" label="Đường kính thân (cm)">
                  <InputNumber style={{ width: '100%' }} min={0} precision={1} />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item name="heightM" label="Chiều cao (m)">
                  <InputNumber style={{ width: '100%' }} min={0} precision={1} />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item name="canopyDiameterM" label="Đường kính tán (m)">
                  <InputNumber style={{ width: '100%' }} min={0} precision={1} />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item name="plantingYear" label="Năm trồng">
                  <InputNumber style={{ width: '100%' }} min={1900} max={new Date().getFullYear()} />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={16}>
              <Col span={8}>
                <Form.Item name="healthStatus" label="Tình trạng sinh trưởng">
                  <Select allowClear options={[
                    { value: 'Tốt', label: 'Tốt' },
                    { value: 'Trung bình', label: 'Trung bình' },
                    { value: 'Suy yếu', label: 'Suy yếu' },
                    { value: 'Chết / cần thay thế', label: 'Chết / cần thay thế' },
                  ]} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="riskLevel" label="Mức độ rủi ro">
                  <Select allowClear options={[
                    { value: 'Thấp', label: 'Thấp' },
                    { value: 'Trung bình', label: 'Trung bình' },
                    { value: 'Cao', label: 'Cao' },
                    { value: 'Khẩn cấp', label: 'Khẩn cấp' },
                  ]} />
                </Form.Item>
              </Col>
              <Col span={4}>
                <Form.Item name="longitude" label="Kinh độ">
                  <InputNumber style={{ width: '100%' }} min={-180} max={180} precision={7} />
                </Form.Item>
              </Col>
              <Col span={4}>
                <Form.Item name="latitude" label="Vĩ độ">
                  <InputNumber style={{ width: '100%' }} min={-90} max={90} precision={7} />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="notes" label="Ghi chú chăm sóc / xử lý">
              <TextArea rows={3} maxLength={2000} showCount />
            </Form.Item>
          </>
        )}

        {datasetKey === 'other_assets' && (
          <>
            <Divider orientation="left" plain>
              <Text strong style={{ fontSize: 13, color: '#1677ff' }}>
                Phân loại tài sản khác
              </Text>
            </Divider>
            <Row gutter={16}>
              <Col span={8}>
                <Form.Item name="assetCategory" label="Nhóm tài sản">
                  <Input placeholder="Ví dụ: Thiết bị phụ trợ" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="assetTypeName" label="Loại tài sản">
                  <Input placeholder="Ví dụ: Tủ điều khiển" />
                </Form.Item>
              </Col>
              <Col span={4}>
                <Form.Item name="quantity" label="Số lượng">
                  <InputNumber style={{ width: '100%' }} min={0} precision={2} />
                </Form.Item>
              </Col>
              <Col span={4}>
                <Form.Item name="unit" label="Đơn vị tính">
                  <Input placeholder="bộ, chiếc..." />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="description" label="Mô tả tài sản">
              <TextArea rows={3} maxLength={2000} showCount />
            </Form.Item>
            <Form.Item name="notes" label="Ghi chú quản lý">
              <TextArea rows={3} maxLength={2000} showCount />
            </Form.Item>
          </>
        )}
      </Form>
    </Modal>
  );
};
