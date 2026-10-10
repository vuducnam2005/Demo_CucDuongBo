import { useEffect, useMemo, useState, type CSSProperties } from "react";
import {
  Alert,
  Avatar,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Image,
  Input,
  Progress,
  Row,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from "antd";
import type { TableColumnsType } from "antd";
import {
  AimOutlined,
  ArrowLeftOutlined,
  CalendarOutlined,
  CameraOutlined,
  CheckCircleFilled,
  EnvironmentOutlined,
  EyeOutlined,
  FileDoneOutlined,
  ReloadOutlined,
  SearchOutlined,
  UserOutlined,
} from "@ant-design/icons";
import type { Dayjs } from "dayjs";
import { useSearchParams, useNavigate } from "react-router-dom";
import { apiClient, getApiErrorMessage } from "../services/api";
import {
  fetchVroadCases,
  type CaseItem,
  type CasePage,
} from "../services/vroadApi";

const { Title, Text, Link } = Typography;

const panelStyle: CSSProperties = {
  border: "1px solid #e3eaf1",
  borderRadius: 12,
  boxShadow: "0 8px 24px rgba(17, 53, 82, 0.06)",
};

const kpiBodyStyle: CSSProperties = {
  display: "flex",
  minHeight: 142,
  flexDirection: "column",
  justifyContent: "space-between",
  padding: 18,
};

const formatDateTime = (value: string) =>
  new Date(value).toLocaleString("vi-VN");

export const VroadCasesPage = () => {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const recordQuery = params.get("record");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<CasePage | null>(null);
  const [selected, setSelected] = useState<CaseItem | null>(null);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [imageLoading, setImageLoading] = useState(false);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [confirmedRange, setConfirmedRange] = useState<[Dayjs, Dayjs] | null>(
    null,
  );
  const [refreshKey, setRefreshKey] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    fetchVroadCases(page)
      .then((data) => {
        if (!active) return;
        setResult(data);
        setError(null);
        const recordId = Number(recordQuery);
        if (recordId)
          setSelected(
            data.content.find((item) => item.recordId === recordId) || null,
          );
      })
      .catch((cause) => {
        if (active) setError(getApiErrorMessage(cause));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, recordQuery, refreshKey]);

  useEffect(() => {
    let active = true;
    let temporaryUrl: string | null = null;
    setImageUrl(null);
    setPreviewVisible(false);
    if (selected?.hasEvidence) {
      setImageLoading(true);
      apiClient
        .get<Blob>(`/api/vroad/defects/${selected.recordId}/evidence`, {
          responseType: "blob",
        })
        .then((response) => {
          if (!active) return;
          temporaryUrl = URL.createObjectURL(response.data);
          setImageUrl(temporaryUrl);
        })
        .catch((cause) => {
          if (active) setError(getApiErrorMessage(cause));
        })
        .finally(() => {
          if (active) setImageLoading(false);
        });
    } else {
      setImageLoading(false);
    }
    return () => {
      active = false;
      if (temporaryUrl) URL.revokeObjectURL(temporaryUrl);
    };
  }, [selected]);

  const evidenceCount =
    result?.content.filter((item) => item.hasEvidence).length ?? 0;
  const evidenceRate = result?.content.length
    ? (evidenceCount / result.content.length) * 100
    : 0;
  const dominantRoute = useMemo(() => {
    const counts = new Map<string, number>();
    result?.content.forEach((item) =>
      counts.set(item.routeName, (counts.get(item.routeName) ?? 0) + 1),
    );
    return [...counts.entries()].sort((a, b) => b[1] - a[1])[0] ?? null;
  }, [result]);
  const filteredCases = useMemo(() => {
    const keyword = searchTerm.trim().toLocaleLowerCase("vi-VN");
    return (result?.content ?? []).filter((item) => {
      const matchesKeyword =
        !keyword ||
        item.recordKey.toLocaleLowerCase("vi-VN").includes(keyword) ||
        item.routeName.toLocaleLowerCase("vi-VN").includes(keyword);
      const resolvedAt = new Date(item.resolvedAt).getTime();
      const matchesDate =
        !confirmedRange ||
        (resolvedAt >= confirmedRange[0].startOf("day").valueOf() &&
          resolvedAt <= confirmedRange[1].endOf("day").valueOf());
      return matchesKeyword && matchesDate;
    });
  }, [confirmedRange, result, searchTerm]);

  const columns: TableColumnsType<CaseItem> = [
    {
      title: "Mã hư hỏng",
      dataIndex: "recordKey",
      key: "recordKey",
      width: 150,
      fixed: "left",
      render: (value: string, item) => (
        <Link
          onClick={(event) => {
            event.stopPropagation();
            setSelected(item);
          }}
        >
          <Tag
            color="blue"
            style={{ margin: 0, cursor: "pointer", fontWeight: 650 }}
          >
            {value}
          </Tag>
        </Link>
      ),
    },
    {
      title: "Tuyến",
      dataIndex: "routeName",
      key: "routeName",
      ellipsis: true,
      width: 235,
    },
    {
      title: "Lý trình",
      dataIndex: "chainage",
      key: "chainage",
      width: 130,
      render: (value: string | null) => (
        <Space size={6}>
          <EnvironmentOutlined style={{ color: "#0b6f87" }} />
          {value || "Không rõ"}
        </Space>
      ),
    },
    {
      title: "Người xử lý",
      dataIndex: "resolvedBy",
      key: "resolvedBy",
      width: 210,
      render: (value: string) => (
        <Space>
          <Avatar
            size={30}
            icon={<UserOutlined />}
            style={{ color: "#0b5c83", background: "#e8f4fa" }}
          />
          <Text>{value}</Text>
        </Space>
      ),
    },
    {
      title: "Thời điểm xác nhận",
      dataIndex: "resolvedAt",
      key: "resolvedAt",
      width: 190,
      sorter: (a, b) =>
        new Date(a.resolvedAt).getTime() - new Date(b.resolvedAt).getTime(),
      render: (value: string) => (
        <Space size={6}>
          <CalendarOutlined style={{ color: "#74879a" }} />
          {formatDateTime(value)}
        </Space>
      ),
    },
    {
      title: "Trạng thái",
      key: "status",
      width: 145,
      render: () => (
        <Tag icon={<CheckCircleFilled />} color="success">
          Đã xử lý xong
        </Tag>
      ),
    },
    {
      title: "Thao tác",
      key: "actions",
      width: 260,
      fixed: "right",
      render: (_, item) => (
        <Space size={6}>
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={(event) => {
              event.stopPropagation();
              setSelected(item);
            }}
          >
            Xem chi tiết
          </Button>
          <Button
            size="small"
            type="primary"
            ghost
            icon={<AimOutlined />}
            onClick={(event) => {
              event.stopPropagation();
              navigate(`/map?defectRecordId=${item.recordId}`);
            }}
          >
            Định vị WebGIS
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ minHeight: "calc(100vh - 210px)" }}>
      <div
        style={{
          position: "relative",
          marginBottom: 18,
          padding: "22px 24px",
          overflow: "hidden",
          color: "#fff",
          borderRadius: 12,
          background:
            "linear-gradient(120deg, #082f52 0%, #075b72 62%, #128278 100%)",
          boxShadow: "0 12px 30px rgba(8, 47, 82, 0.18)",
        }}
      >
        <FileDoneOutlined
          style={{
            position: "absolute",
            right: 34,
            top: -24,
            color: "rgba(255,255,255,0.09)",
            fontSize: 150,
          }}
        />
        <Text
          style={{
            color: "#82e1d6",
            fontSize: 11,
            fontWeight: 700,
            letterSpacing: "0.12em",
          }}
        >
          DIGITAL ACCEPTANCE RECORDS
        </Text>
        <Title level={2} style={{ margin: "4px 0 5px", color: "#fff" }}>
          Hồ sơ hư hỏng đã xử lý
        </Title>
        <Text style={{ color: "rgba(255,255,255,0.75)" }}>
          Lưu trong PostgreSQL demo; dữ liệu khảo sát nguồn không bị sửa hay
          xóa.
        </Text>
      </div>

      <Row gutter={[14, 14]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={8}>
          <Card
            style={{
              ...panelStyle,
              height: "100%",
              borderTop: "3px solid #0b66a3",
            }}
            styles={{ body: kpiBodyStyle }}
          >
            <Space style={{ justifyContent: "space-between" }}>
              <Text type="secondary">HỒ SƠ ĐÃ NGHIỆM THU</Text>
              <FileDoneOutlined style={{ color: "#0b66a3", fontSize: 23 }} />
            </Space>
            <Text strong style={{ color: "#123f61", fontSize: 31 }}>
              {(result?.totalElements ?? 0).toLocaleString("vi-VN")}
            </Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Tổng số hồ sơ hoàn thành trong hệ thống
            </Text>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card
            style={{
              ...panelStyle,
              height: "100%",
              borderTop: "3px solid #07838b",
            }}
            styles={{ body: kpiBodyStyle }}
          >
            <Space style={{ justifyContent: "space-between" }}>
              <Text type="secondary">CÓ ẢNH MINH CHỨNG</Text>
              <CameraOutlined style={{ color: "#07838b", fontSize: 23 }} />
            </Space>
            <Space align="baseline">
              <Text strong style={{ color: "#075f65", fontSize: 31 }}>
                {evidenceRate.toFixed(0)}%
              </Text>
              <Text type="secondary">
                {evidenceCount}/{result?.content.length ?? 0} hồ sơ trang này
              </Text>
            </Space>
            <Progress
              percent={evidenceRate}
              showInfo={false}
              strokeColor="#07838b"
              trailColor="#e9f2f3"
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card
            style={{
              ...panelStyle,
              height: "100%",
              borderTop: "3px solid #df7a19",
            }}
            styles={{ body: kpiBodyStyle }}
          >
            <Space style={{ justifyContent: "space-between" }}>
              <Text type="secondary">TUYẾN TẬP TRUNG BẢO TRÌ</Text>
              <EnvironmentOutlined style={{ color: "#df7a19", fontSize: 23 }} />
            </Space>
            <Text
              strong
              ellipsis={{ tooltip: dominantRoute?.[0] }}
              style={{ color: "#714216", fontSize: 20 }}
            >
              {dominantRoute?.[0] ?? "Chưa có dữ liệu"}
            </Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {dominantRoute
                ? `${dominantRoute[1]} hồ sơ trên trang hiện tại`
                : "Chưa xác định tuyến trọng điểm"}
            </Text>
          </Card>
        </Col>
      </Row>

      <Card
        style={{ ...panelStyle, marginBottom: 16 }}
        styles={{ body: { padding: 16 } }}
      >
        <Row gutter={[10, 10]} align="middle">
          <Col xs={24} md={11} xl={9}>
            <Input
              allowClear
              size="large"
              prefix={<SearchOutlined style={{ color: "#8091a1" }} />}
              placeholder="Tìm mã hồ sơ (DEF-...) hoặc tên tuyến"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
            />
          </Col>
          <Col xs={24} md={9} xl={7}>
            <DatePicker.RangePicker
              size="large"
              value={confirmedRange}
              style={{ width: "100%" }}
              placeholder={["Từ ngày xác nhận", "Đến ngày"]}
              onChange={(dates) =>
                setConfirmedRange(
                  dates?.[0] && dates[1] ? [dates[0], dates[1]] : null,
                )
              }
            />
          </Col>
          <Col xs={24} md={4} xl={8}>
            <Space wrap style={{ width: "100%", justifyContent: "flex-end" }}>
              <Button
                size="large"
                icon={<ReloadOutlined />}
                loading={loading}
                onClick={() => setRefreshKey((value) => value + 1)}
              >
                Làm mới
              </Button>
              <Button
                size="large"
                type="primary"
                icon={<ArrowLeftOutlined />}
                onClick={() => navigate("/map")}
              >
                Trở lại bản đồ
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {error && (
        <Alert
          type="error"
          message={error}
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Card
        title={
          <Space>
            <FileDoneOutlined style={{ color: "#0b66a3" }} />
            Danh sách biên bản nghiệm thu
          </Space>
        }
        extra={<Tag color="blue">{filteredCases.length} hồ sơ hiển thị</Tag>}
        style={panelStyle}
        styles={{ body: { padding: "0 0 4px" } }}
      >
        <Table<CaseItem>
          rowKey="recordId"
          columns={columns}
          dataSource={filteredCases}
          loading={loading}
          scroll={{ x: 1320 }}
          size="middle"
          locale={{
            emptyText: <Empty description="Không tìm thấy hồ sơ phù hợp" />,
          }}
          onRow={(item) => ({
            onClick: () => setSelected(item),
            style: { cursor: "pointer" },
          })}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total: result?.totalElements || 0,
            showSizeChanger: false,
            showTotal: (total) => `${total.toLocaleString("vi-VN")} hồ sơ`,
            onChange: (number) => setPage(number - 1),
          }}
        />
      </Card>

      <Drawer
        title="Hồ sơ đã xử lý xong"
        open={!!selected}
        width={560}
        onClose={() => setSelected(null)}
      >
        {selected && (
          <Space direction="vertical" size="large" style={{ width: "100%" }}>
            <div
              style={{
                padding: 20,
                color: "#fff",
                borderRadius: 12,
                background: "linear-gradient(135deg, #083f68, #087d7b)",
                boxShadow: "0 10px 22px rgba(8,63,104,0.16)",
              }}
            >
              <Space
                style={{ width: "100%", justifyContent: "space-between" }}
                wrap
              >
                <div>
                  <Text
                    style={{ color: "rgba(255,255,255,0.7)", fontSize: 11 }}
                  >
                    MÃ BIÊN BẢN NGHIỆM THU
                  </Text>
                  <Title level={3} style={{ margin: "4px 0 0", color: "#fff" }}>
                    {selected.recordKey}
                  </Title>
                </div>
                <Tag icon={<CheckCircleFilled />} color="success">
                  Đã xử lý xong
                </Tag>
              </Space>
              <Space wrap style={{ marginTop: 14 }}>
                <Tag color="cyan">
                  {selected.defectType || "Chưa phân loại"}
                </Tag>
                {selected.hasEvidence && (
                  <Tag color="blue" icon={<CameraOutlined />}>
                    Có ảnh thực địa
                  </Tag>
                )}
              </Space>
            </div>

            <Descriptions
              title="Thông số kỹ thuật"
              bordered
              column={1}
              size="small"
              labelStyle={{ width: 145, color: "#607488", fontWeight: 600 }}
            >
              <Descriptions.Item label="Tuyến đường">
                {selected.routeName}
              </Descriptions.Item>
              <Descriptions.Item label="Lý trình">
                {selected.chainage || "Không rõ"}
              </Descriptions.Item>
              <Descriptions.Item label="Loại hư hỏng">
                {selected.defectType || "Không rõ"}
              </Descriptions.Item>
              <Descriptions.Item label="Xác nhận lúc">
                {formatDateTime(selected.resolvedAt)}
              </Descriptions.Item>
            </Descriptions>

            <Card
              size="small"
              title={
                <Space>
                  <UserOutlined style={{ color: "#0b66a3" }} />
                  Thông tin nghiệm thu
                </Space>
              }
              style={panelStyle}
            >
              <Space align="center">
                <Avatar
                  size={42}
                  icon={<UserOutlined />}
                  style={{ color: "#0b5c83", background: "#e7f2f8" }}
                />
                <div>
                  <Text style={{ display: "block" }}>
                    <strong>Đã xử lý xong bởi:</strong> {selected.resolvedBy}
                  </Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Hồ sơ đã được ghi nhận vào hệ thống
                  </Text>
                </div>
              </Space>
            </Card>

            <Card
              size="small"
              title="Nội dung xử lý"
              style={{ ...panelStyle, background: "#f7fafc" }}
            >
              <Text style={{ lineHeight: 1.75 }}>
                {selected.resolutionNote}
              </Text>
            </Card>

            <Card
              size="small"
              title={
                <Space>
                  <CameraOutlined style={{ color: "#07838b" }} />
                  Ảnh minh chứng thực địa
                </Space>
              }
              extra={
                imageUrl && (
                  <Button
                    type="link"
                    icon={<EyeOutlined />}
                    onClick={() => setPreviewVisible(true)}
                  >
                    Phóng to ảnh
                  </Button>
                )
              }
              style={panelStyle}
              styles={{ body: { padding: 12 } }}
            >
              {selected.hasEvidence ? (
                imageLoading ? (
                  <div
                    style={{
                      display: "grid",
                      minHeight: 220,
                      placeItems: "center",
                    }}
                  >
                    <Spin tip="Đang tải ảnh minh chứng..." />
                  </div>
                ) : imageUrl ? (
                  <div
                    style={{
                      overflow: "hidden",
                      borderRadius: 10,
                      background: "#eef3f6",
                    }}
                  >
                    <Image
                      src={imageUrl}
                      alt="Ảnh minh chứng sau xử lý"
                      width="100%"
                      style={{
                        display: "block",
                        maxHeight: 380,
                        objectFit: "contain",
                      }}
                      preview={{
                        visible: previewVisible,
                        onVisibleChange: setPreviewVisible,
                      }}
                    />
                  </div>
                ) : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="Không tải được ảnh minh chứng."
                  />
                )
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="Không có ảnh minh chứng."
                />
              )}
            </Card>

            <Button
              type="primary"
              size="large"
              block
              icon={<AimOutlined />}
              onClick={() =>
                navigate(`/map?defectRecordId=${selected.recordId}`)
              }
            >
              Định vị hư hỏng trên bản đồ
            </Button>
          </Space>
        )}
      </Drawer>
    </div>
  );
};
