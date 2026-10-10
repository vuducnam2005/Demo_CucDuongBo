import { useMemo, useState, type CSSProperties } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Empty,
  Progress,
  Row,
  Select,
  Skeleton,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from "antd";
import type { TableColumnsType } from "antd";
import {
  BarChartOutlined,
  CarOutlined,
  ClockCircleOutlined,
  DashboardOutlined,
  EnvironmentOutlined,
  EyeOutlined,
  FieldTimeOutlined,
  RadarChartOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  TruckOutlined,
} from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import {
  fetchTrafficStations,
  fetchTrafficSummary,
} from "../services/trafficApi";
import { getApiErrorMessage } from "../services/api";

const { Title, Text } = Typography;

const panelStyle: CSSProperties = {
  height: "100%",
  border: "1px solid #e4ebf2",
  borderRadius: 12,
  boxShadow: "0 8px 24px rgba(15, 52, 86, 0.06)",
};

const kpiBodyStyle: CSSProperties = {
  display: "flex",
  minHeight: 150,
  flexDirection: "column",
  justifyContent: "space-between",
  padding: 18,
};

const vehicleColors = ["#007d8a", "#1769aa", "#e07a1f", "#2f855a", "#52677d"];

const formatNumber = (value: number) => value.toLocaleString("vi-VN");

const formatDateTime = (value: string) =>
  new Date(value).toLocaleString("vi-VN");

const formatInterval = (start: string, end: string) => {
  const startDate = new Date(start);
  const endDate = new Date(end);
  return `${startDate.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })} – ${endDate.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}`;
};

const getVehicleIcon = (vehicleClass: string) => {
  const normalized = vehicleClass.toLocaleLowerCase("vi-VN");
  if (normalized.includes("máy") || normalized.includes("mô tô"))
    return <ThunderboltOutlined />;
  if (normalized.includes("tải")) return <TruckOutlined />;
  if (normalized.includes("khách") || normalized.includes("buýt"))
    return <TeamOutlined />;
  if (normalized.includes("ô tô") || normalized.includes("con"))
    return <CarOutlined />;
  return <DashboardOutlined />;
};

type IntervalItem = {
  key: string;
  start: string;
  end: string;
  count: number;
  share: number;
  isPeak: boolean;
};

export const TrafficPage = () => {
  const [stationCode, setStationCode] = useState<string | null>(null);
  const [period, setPeriod] = useState<[string, string] | null>(null);
  const [pickerValue, setPickerValue] = useState<[Dayjs, Dayjs] | null>(null);
  const [quickRange, setQuickRange] = useState<string | null>(null);
  const [selectedInterval, setSelectedInterval] = useState<IntervalItem | null>(
    null,
  );
  const stations = useQuery({
    queryKey: ["trafficStations"],
    queryFn: fetchTrafficStations,
  });
  const selected = stationCode ?? stations.data?.[0]?.stationCode;
  const selectedStation = stations.data?.find(
    (station) => station.stationCode === selected,
  );
  const summary = useQuery({
    queryKey: ["trafficSummary", selected, period],
    queryFn: () => fetchTrafficSummary(selected!, period?.[0], period?.[1]),
    enabled: !!selected,
  });

  const summaryData = summary.data;
  const dominantClass = useMemo(
    () =>
      summaryData?.classes.reduce<(typeof summaryData.classes)[number] | null>(
        (largest, item) =>
          !largest || item.count > largest.count ? item : largest,
        null,
      ) ?? null,
    [summaryData],
  );
  const peakInterval = useMemo(
    () =>
      summaryData?.intervals.reduce<
        (typeof summaryData.intervals)[number] | null
      >(
        (largest, item) =>
          !largest || item.count > largest.count ? item : largest,
        null,
      ) ?? null,
    [summaryData],
  );
  const maxInterval = Math.max(
    1,
    ...(summary.data?.intervals.map((item) => item.count) ?? []),
  );
  const intervalRows = useMemo<IntervalItem[]>(
    () =>
      summary.data?.intervals.map((item) => ({
        key: item.start,
        ...item,
        share:
          summary.data!.totalVehicles > 0
            ? (item.count / summary.data!.totalVehicles) * 100
            : 0,
        isPeak: item.start === peakInterval?.start,
      })) ?? [],
    [peakInterval?.start, summary.data],
  );

  const applyQuickRange = (key: string, start: Dayjs, end: Dayjs) => {
    setQuickRange(key);
    setPickerValue([start, end]);
    setPeriod([start.toISOString(), end.toISOString()]);
  };

  const quickRanges = [
    {
      key: "today",
      label: "Hôm nay",
      getRange: () => [dayjs().startOf("day"), dayjs()] as [Dayjs, Dayjs],
    },
    {
      key: "24h",
      label: "24 giờ qua",
      getRange: () => [dayjs().subtract(24, "hour"), dayjs()] as [Dayjs, Dayjs],
    },
    {
      key: "7d",
      label: "7 ngày qua",
      getRange: () => [dayjs().subtract(7, "day"), dayjs()] as [Dayjs, Dayjs],
    },
    {
      key: "30d",
      label: "30 ngày qua",
      getRange: () => [dayjs().subtract(30, "day"), dayjs()] as [Dayjs, Dayjs],
    },
  ];

  const detailColumns: TableColumnsType<IntervalItem> = [
    {
      title: "Thời gian quan sát",
      key: "time",
      width: 260,
      render: (_, item) => (
        <Space direction="vertical" size={1}>
          <Text strong>{formatInterval(item.start, item.end)}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {new Date(item.start).toLocaleDateString("vi-VN")}
          </Text>
        </Space>
      ),
    },
    {
      title: "Lượt xe",
      dataIndex: "count",
      key: "count",
      width: 140,
      align: "right",
      sorter: (a, b) => a.count - b.count,
      render: (value: number) => (
        <Text strong style={{ color: "#0b4f82" }}>
          {formatNumber(value)}
        </Text>
      ),
    },
    {
      title: "Tỷ trọng",
      dataIndex: "share",
      key: "share",
      width: 210,
      render: (value: number) => (
        <Space style={{ width: "100%" }}>
          <Progress
            percent={Math.min(100, value)}
            showInfo={false}
            strokeColor="#1591a3"
            style={{ width: 105, margin: 0 }}
          />
          <Text strong>{value.toFixed(1)}%</Text>
        </Space>
      ),
    },
    {
      title: "Đánh giá",
      key: "assessment",
      width: 170,
      render: (_, item) =>
        item.isPeak ? (
          <Tag color="volcano">🔥 Giờ cao điểm</Tag>
        ) : (
          <Tag color={item.count >= maxInterval * 0.7 ? "gold" : "blue"}>
            {item.count >= maxInterval * 0.7 ? "Lưu lượng cao" : "Ổn định"}
          </Tag>
        ),
    },
    {
      title: "Thao tác",
      key: "action",
      width: 130,
      fixed: "right",
      render: (_, item) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => setSelectedInterval(item)}
        >
          Xem chi tiết
        </Button>
      ),
    },
  ];

  return (
    <section
      aria-label="Lưu lượng phương tiện"
      style={{ minHeight: "calc(100vh - 210px)" }}
    >
      <div
        style={{
          position: "relative",
          marginBottom: 18,
          padding: "22px 24px",
          overflow: "hidden",
          color: "#fff",
          borderRadius: 12,
          background:
            "linear-gradient(120deg, #06345b 0%, #075d78 58%, #0c8390 100%)",
          boxShadow: "0 12px 30px rgba(6, 52, 91, 0.18)",
        }}
      >
        <RadarChartOutlined
          style={{
            position: "absolute",
            right: 26,
            top: -24,
            color: "rgba(255,255,255,0.09)",
            fontSize: 150,
          }}
        />
        <Text
          style={{
            color: "#85e3e7",
            fontSize: 11,
            fontWeight: 700,
            letterSpacing: "0.12em",
          }}
        >
          SMART HIGHWAY ITS
        </Text>
        <Title level={2} style={{ margin: "4px 0 5px", color: "#fff" }}>
          Lưu lượng phương tiện
        </Title>
        <Text style={{ color: "rgba(255,255,255,0.75)" }}>
          Theo dõi cơ cấu phương tiện và diễn biến lưu lượng theo thời gian
          thực.
        </Text>
        {selectedStation && (
          <div style={{ marginTop: 13 }}>
            <Tag color={selectedStation.isDemo ? "orange" : "cyan"}>
              {selectedStation.isDemo
                ? "Số liệu mô phỏng"
                : "Số liệu nhập — chờ đối soát"}
            </Tag>
          </div>
        )}
      </div>

      {stations.isLoading && <Skeleton active />}
      {stations.error && (
        <Alert
          type="error"
          message={getApiErrorMessage(stations.error)}
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      <Card
        style={{ ...panelStyle, height: "auto", marginBottom: 16 }}
        styles={{ body: { padding: 18 } }}
      >
        <Row gutter={[14, 14]} align="bottom">
          <Col xs={24} md={12} xl={7}>
            <Text
              strong
              style={{ display: "block", marginBottom: 7, color: "#35516a" }}
            >
              Trạm đo lưu lượng
            </Text>
            <Select
              aria-label="Trạm đếm xe"
              placeholder="Chọn trạm đếm"
              value={selected}
              onChange={setStationCode}
              style={{ width: "100%" }}
              size="large"
              suffixIcon={<EnvironmentOutlined />}
              options={stations.data?.map((station) => ({
                value: station.stationCode,
                label: station.stationName,
              }))}
            />
          </Col>
          <Col xs={24} md={12} xl={8}>
            <Text
              strong
              style={{ display: "block", marginBottom: 7, color: "#35516a" }}
            >
              Khoảng thời gian phân tích
            </Text>
            <DatePicker.RangePicker
              aria-label="Khoảng thời gian"
              showTime
              format="DD/MM/YYYY HH:mm"
              value={pickerValue}
              style={{ width: "100%" }}
              size="large"
              onChange={(dates) => {
                setQuickRange(null);
                if (dates?.[0] && dates[1]) {
                  setPickerValue([dates[0], dates[1]]);
                  setPeriod([dates[0].toISOString(), dates[1].toISOString()]);
                } else {
                  setPickerValue(null);
                  setPeriod(null);
                }
              }}
            />
          </Col>
          <Col xs={24} xl={9}>
            <Text
              strong
              style={{ display: "block", marginBottom: 7, color: "#35516a" }}
            >
              Chọn nhanh
            </Text>
            <Space wrap size={[6, 8]}>
              {quickRanges.map((item) => (
                <Button
                  key={item.key}
                  type={quickRange === item.key ? "primary" : "default"}
                  onClick={() => {
                    const [start, end] = item.getRange();
                    applyQuickRange(item.key, start, end);
                  }}
                >
                  {item.label}
                </Button>
              ))}
            </Space>
          </Col>
        </Row>
      </Card>

      {!stations.isLoading && stations.data?.length === 0 && (
        <Empty description="Chưa có trạm trong phạm vi đơn vị" />
      )}
      {summary.isLoading && <Skeleton active />}
      {summary.error && (
        <Alert
          type="error"
          message={getApiErrorMessage(summary.error)}
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {summary.data && (
        <>
          <Row gutter={[14, 14]} style={{ marginBottom: 16 }}>
            <Col xs={24} sm={12} xl={6}>
              <Card
                style={{ ...panelStyle, borderTop: "3px solid #0b66a3" }}
                styles={{ body: kpiBodyStyle }}
              >
                <Space style={{ justifyContent: "space-between" }}>
                  <Text type="secondary">TỔNG LƯỢT XE</Text>
                  <BarChartOutlined
                    style={{ color: "#0b66a3", fontSize: 22 }}
                  />
                </Space>
                <Statistic
                  value={summary.data.totalVehicles}
                  valueStyle={{
                    color: "#113b5c",
                    fontWeight: 750,
                    fontSize: 30,
                  }}
                />
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Tổng quan sát trong kỳ đã chọn
                </Text>
              </Card>
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <Card
                style={{ ...panelStyle, borderTop: "3px solid #df6c19" }}
                styles={{ body: kpiBodyStyle }}
              >
                <Space style={{ justifyContent: "space-between" }}>
                  <Text type="secondary">KHUNG GIỜ CAO ĐIỂM</Text>
                  <ClockCircleOutlined
                    style={{ color: "#df6c19", fontSize: 22 }}
                  />
                </Space>
                <div>
                  <Text
                    strong
                    style={{ display: "block", color: "#724016", fontSize: 20 }}
                  >
                    {peakInterval
                      ? formatInterval(peakInterval.start, peakInterval.end)
                      : "Chưa có dữ liệu"}
                  </Text>
                  {peakInterval && (
                    <Text strong style={{ color: "#df6c19" }}>
                      {summary.data.totalVehicles > 0
                        ? (
                            (peakInterval.count / summary.data.totalVehicles) *
                            100
                          ).toFixed(1)
                        : "0.0"}
                      % tổng lưu lượng
                    </Text>
                  )}
                </div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {peakInterval
                    ? `${formatNumber(peakInterval.count)} lượt xe`
                    : "Cần thêm quan sát theo giờ"}
                </Text>
              </Card>
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <Card
                style={{ ...panelStyle, borderTop: "3px solid #07838b" }}
                styles={{ body: kpiBodyStyle }}
              >
                <Space style={{ justifyContent: "space-between" }}>
                  <Text type="secondary">PHƯƠNG TIỆN CHIẾM ƯU THẾ</Text>
                  <CarOutlined style={{ color: "#07838b", fontSize: 22 }} />
                </Space>
                <Text strong style={{ color: "#075e64", fontSize: 21 }}>
                  {dominantClass
                    ? `${dominantClass.vehicleClass} · ${summary.data.totalVehicles > 0 ? ((dominantClass.count / summary.data.totalVehicles) * 100).toFixed(1) : "0.0"}%`
                    : "Chưa phân loại"}
                </Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {dominantClass
                    ? `${formatNumber(dominantClass.count)} lượt ghi nhận`
                    : "Chưa có dữ liệu cơ cấu xe"}
                </Text>
              </Card>
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <Card
                style={{ ...panelStyle, borderTop: "3px solid #2f855a" }}
                styles={{ body: kpiBodyStyle }}
              >
                <Space style={{ justifyContent: "space-between" }}>
                  <Text type="secondary">TRẠM ĐO / TUYẾN ĐƯỜNG</Text>
                  <EnvironmentOutlined
                    style={{ color: "#2f855a", fontSize: 22 }}
                  />
                </Space>
                <div>
                  <Text
                    strong
                    ellipsis={{ tooltip: summary.data.station.stationName }}
                    style={{ display: "block", color: "#245d42", fontSize: 17 }}
                  >
                    {summary.data.station.stationName}
                  </Text>
                  <Text
                    type="secondary"
                    ellipsis={{ tooltip: summary.data.station.routeName }}
                    style={{ display: "block" }}
                  >
                    {summary.data.station.routeName}
                  </Text>
                </div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {summary.data.rangeStart && summary.data.rangeEnd
                    ? `${formatDateTime(summary.data.rangeStart)} – ${formatDateTime(summary.data.rangeEnd)}`
                    : `Nguồn: ${summary.data.station.sourceSystem}`}
                </Text>
              </Card>
            </Col>
          </Row>

          {summary.data.totalVehicles === 0 && !summary.data.density && (
            <Card style={{ ...panelStyle, height: "auto", marginBottom: 16 }}>
              <Empty description="Không có quan sát trong khoảng thời gian này" />
            </Card>
          )}

          {summary.data.density && (
            <Card
              title={
                <Space>
                  <RadarChartOutlined style={{ color: "#087f82" }} />
                  Mật độ xe hiện diện tại thời điểm đo
                </Space>
              }
              style={{ ...panelStyle, height: "auto", marginBottom: 16 }}
            >
              <Row gutter={[18, 18]} align="middle">
                <Col xs={12} md={5}>
                  <Statistic
                    title="Xe/km"
                    value={summary.data.density.vehiclesPerKm}
                    precision={2}
                    valueStyle={{ color: "#087f82" }}
                  />
                </Col>
                <Col xs={12} md={5}>
                  <Statistic
                    title="Xe hiện diện"
                    value={summary.data.density.presentVehicles}
                    valueStyle={{ color: "#0b66a3" }}
                  />
                </Col>
                <Col xs={24} md={7}>
                  <Text type="secondary">
                    <FieldTimeOutlined />{" "}
                    {formatDateTime(summary.data.density.measuredAt)}
                  </Text>
                </Col>
                <Col xs={24} md={7}>
                  <Text type="secondary">
                    <EnvironmentOutlined /> Đoạn đo:{" "}
                    {summary.data.station.segmentLengthKm ?? "Chưa rõ"} km
                  </Text>
                </Col>
              </Row>
              <Space wrap style={{ marginTop: 14 }}>
                {summary.data.density.classes.map((item) => (
                  <Tag key={item.vehicleClass} color="cyan">
                    {item.vehicleClass}: {formatNumber(item.count)}
                  </Tag>
                ))}
              </Space>
            </Card>
          )}

          {summary.data.totalVehicles > 0 && (
            <Row gutter={[14, 14]} style={{ marginBottom: 16 }}>
              <Col xs={24} xl={11}>
                <Card
                  title={
                    <Space>
                      <CarOutlined style={{ color: "#087f82" }} />
                      Phân loại phương tiện
                    </Space>
                  }
                  extra={
                    <Tag color="cyan">
                      {summary.data.classes.length} nhóm xe
                    </Tag>
                  }
                  style={panelStyle}
                >
                  {summary.data.classes.length === 0 ? (
                    <Empty description="Chưa có dữ liệu phân loại" />
                  ) : (
                    summary.data.classes.map((item, index) => {
                      const percentage =
                        summary.data!.totalVehicles > 0
                          ? (item.count / summary.data!.totalVehicles) * 100
                          : 0;
                      const color = vehicleColors[index % vehicleColors.length];
                      return (
                        <div
                          key={item.vehicleClass}
                          style={{
                            marginBottom:
                              index === summary.data!.classes.length - 1
                                ? 0
                                : 19,
                          }}
                        >
                          <div
                            style={{
                              display: "flex",
                              alignItems: "center",
                              justifyContent: "space-between",
                              gap: 12,
                              marginBottom: 7,
                            }}
                          >
                            <Space>
                              <span
                                style={{
                                  display: "grid",
                                  width: 32,
                                  height: 32,
                                  placeItems: "center",
                                  color,
                                  borderRadius: 9,
                                  background: `${color}15`,
                                  fontSize: 17,
                                }}
                              >
                                {getVehicleIcon(item.vehicleClass)}
                              </span>
                              <Text>{item.vehicleClass}</Text>
                            </Space>
                            <Space size={8}>
                              <Text strong>{formatNumber(item.count)}</Text>
                              <Tag color={color}>{percentage.toFixed(1)}%</Tag>
                            </Space>
                          </div>
                          <div
                            role="img"
                            aria-label={`Tỷ lệ ${item.vehicleClass}`}
                          >
                            <Progress
                              percent={Math.min(100, percentage)}
                              showInfo={false}
                              strokeColor={color}
                              trailColor="#edf2f6"
                              size={["100%", 10]}
                            />
                          </div>
                        </div>
                      );
                    })
                  )}
                </Card>
              </Col>
              <Col xs={24} xl={13}>
                <Card
                  title={
                    <Space>
                      <ClockCircleOutlined style={{ color: "#df6c19" }} />
                      Lượt xe theo giờ
                    </Space>
                  }
                  extra={
                    peakInterval && <Tag color="volcano">🔥 Giờ cao điểm</Tag>
                  }
                  style={panelStyle}
                >
                  {summary.data.intervals.length === 0 ? (
                    <Empty description="Chưa có dữ liệu theo giờ" />
                  ) : (
                    summary.data.intervals.slice(0, 8).map((item) => {
                      const isPeak = item.start === peakInterval?.start;
                      return (
                        <div
                          key={item.start}
                          style={{
                            marginBottom: 16,
                            padding: isPeak ? "9px 11px" : "0",
                            borderRadius: 9,
                            background: isPeak ? "#fff7ed" : "transparent",
                          }}
                        >
                          <div
                            style={{
                              display: "flex",
                              alignItems: "center",
                              justifyContent: "space-between",
                              gap: 12,
                              marginBottom: 6,
                            }}
                          >
                            <Space wrap size={7}>
                              <Text strong={isPeak}>
                                {formatInterval(item.start, item.end)}
                              </Text>
                              {isPeak && (
                                <Tag color="volcano">🔥 Giờ cao điểm</Tag>
                              )}
                            </Space>
                            <Text
                              strong
                              style={{ color: isPeak ? "#c6530c" : "#284b67" }}
                            >
                              {formatNumber(item.count)}
                            </Text>
                          </div>
                          <div role="img" aria-label={`Khoảng ${item.start}`}>
                            <Progress
                              percent={(item.count / maxInterval) * 100}
                              showInfo={false}
                              strokeColor={
                                isPeak
                                  ? { from: "#f0a044", to: "#df5c18" }
                                  : { from: "#2e88ba", to: "#13a0a0" }
                              }
                              trailColor="#edf2f6"
                              size={["100%", 10]}
                            />
                          </div>
                        </div>
                      );
                    })
                  )}
                </Card>
              </Col>
            </Row>
          )}

          <Card
            title={
              <Space>
                <BarChartOutlined style={{ color: "#0b66a3" }} />
                Dữ liệu quan sát chi tiết
              </Space>
            }
            extra={
              <Text type="secondary">
                {intervalRows.length} khung thời gian
              </Text>
            }
            style={{ ...panelStyle, height: "auto" }}
            styles={{ body: { padding: "0 0 4px" } }}
          >
            <Table<IntervalItem>
              rowKey="key"
              columns={detailColumns}
              dataSource={intervalRows}
              scroll={{ x: 920 }}
              size="middle"
              locale={{ emptyText: "Chưa có dữ liệu quan sát theo thời gian" }}
              pagination={{
                pageSize: 6,
                showSizeChanger: false,
                showTotal: (total) => `${total} quan sát`,
              }}
            />
          </Card>
        </>
      )}

      <Drawer
        title="Chi tiết quan sát lưu lượng"
        width={440}
        open={!!selectedInterval}
        onClose={() => setSelectedInterval(null)}
      >
        {selectedInterval && (
          <Space direction="vertical" size="large" style={{ width: "100%" }}>
            <div
              style={{
                padding: 18,
                borderRadius: 12,
                color: "#fff",
                background: "linear-gradient(135deg, #0b4f82, #0b7d85)",
              }}
            >
              <Text style={{ color: "rgba(255,255,255,0.7)" }}>
                KHUNG THỜI GIAN
              </Text>
              <Title level={3} style={{ margin: "5px 0 0", color: "#fff" }}>
                {formatInterval(selectedInterval.start, selectedInterval.end)}
              </Title>
              {selectedInterval.isPeak && (
                <Tag color="volcano" style={{ marginTop: 10 }}>
                  🔥 Giờ cao điểm
                </Tag>
              )}
            </div>
            <Row gutter={12}>
              <Col span={12}>
                <Card size="small" style={panelStyle}>
                  <Statistic title="Lượt xe" value={selectedInterval.count} />
                </Card>
              </Col>
              <Col span={12}>
                <Card size="small" style={panelStyle}>
                  <Statistic
                    title="Tỷ trọng"
                    value={selectedInterval.share}
                    precision={1}
                    suffix="%"
                  />
                </Card>
              </Col>
            </Row>
            <Card size="small" title="Thông tin quan sát" style={panelStyle}>
              <Space direction="vertical">
                <Text>
                  <strong>Bắt đầu:</strong>{" "}
                  {formatDateTime(selectedInterval.start)}
                </Text>
                <Text>
                  <strong>Kết thúc:</strong>{" "}
                  {formatDateTime(selectedInterval.end)}
                </Text>
                <Text>
                  <strong>Trạm đo:</strong> {summary.data?.station.stationName}
                </Text>
                <Text>
                  <strong>Tuyến:</strong> {summary.data?.station.routeName}
                </Text>
              </Space>
            </Card>
          </Space>
        )}
      </Drawer>
    </section>
  );
};
