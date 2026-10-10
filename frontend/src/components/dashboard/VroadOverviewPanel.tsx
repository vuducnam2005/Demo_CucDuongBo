import { useMemo, useState, type CSSProperties, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Col,
  Row,
  Skeleton,
  Tooltip,
  Typography,
} from "antd";
import {
  AppstoreOutlined,
  ArrowRightOutlined,
  CheckCircleOutlined,
  EnvironmentOutlined,
  LineChartOutlined,
  ReloadOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { getApiErrorMessage } from "../../services/api";
import {
  fetchSurveyOverview,
  type SurveyOverview,
} from "../../services/vroadApi";

const { Title, Text } = Typography;

type Count = { label: string; count: number };

const numberFormatter = new Intl.NumberFormat("vi-VN");
const donutColors = [
  "#0b66c3",
  "#f47b20",
  "#14a38b",
  "#e0a106",
  "#7557c8",
  "#d94f8a",
];

const getPercentage = (value: number, total: number) =>
  total > 0 ? (value / total) * 100 : 0;

const ChartTooltip = ({
  label,
  count,
  total,
  unit,
}: Count & { total: number; unit: string }) => (
  <div className="vroad-tooltip-content">
    <strong>{label}</strong>
    <span>
      {numberFormatter.format(count)} {unit}
    </span>
    <small>
      {getPercentage(count, total).toLocaleString("vi-VN", {
        maximumFractionDigits: 1,
      })}
      % tổng số
    </small>
  </div>
);

const DefectBarChart = ({ rows }: { rows: Count[] }) => {
  const visibleRows = useMemo(
    () => [...rows].sort((a, b) => b.count - a.count).slice(0, 8),
    [rows],
  );
  const largest = Math.max(1, ...visibleRows.map((item) => item.count));
  const total = rows.reduce((sum, item) => sum + item.count, 0);

  return (
    <Card className="vroad-chart-card" variant="borderless">
      <div className="vroad-chart-heading">
        <div>
          <Text className="vroad-chart-eyebrow">Mức độ phổ biến</Text>
          <Title level={4}>Điểm hư hỏng theo loại</Title>
        </div>
        <span className="vroad-chart-count">Top {visibleRows.length}</span>
      </div>

      <span
        className="kcht-visually-hidden"
        role="img"
        aria-label="Biểu đồ Loại hư hỏng"
      >
        {visibleRows.map((item) => `${item.label}: ${item.count}`).join("; ")}
      </span>

      {visibleRows.length === 0 ? (
        <div className="vroad-empty-chart">Chưa có dữ liệu hư hỏng.</div>
      ) : (
        <div className="vroad-bar-chart">
          {visibleRows.map((item, index) => {
            const width = Math.max(2, (item.count / largest) * 100);
            const percentage = getPercentage(item.count, total);
            return (
              <Tooltip
                key={item.label}
                title={<ChartTooltip {...item} total={total} unit="điểm" />}
                trigger={["hover", "focus", "click"]}
                placement="top"
              >
                <button
                  type="button"
                  className="vroad-bar-item"
                  aria-label={`${item.label}: ${numberFormatter.format(item.count)} điểm, ${percentage.toLocaleString("vi-VN", { maximumFractionDigits: 1 })}% tổng số`}
                >
                  <span className="vroad-bar-rank">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <span className="vroad-bar-label" title={item.label}>
                    {item.label}
                  </span>
                  <span className="vroad-bar-track" aria-hidden="true">
                    <span
                      className="vroad-bar-fill"
                      style={
                        { "--vroad-bar-width": `${width}%` } as CSSProperties
                      }
                    />
                  </span>
                  <span className="vroad-bar-percent">
                    {percentage.toLocaleString("vi-VN", {
                      maximumFractionDigits: 1,
                    })}
                    %
                  </span>
                </button>
              </Tooltip>
            );
          })}
        </div>
      )}
    </Card>
  );
};

const AssetDonutChart = ({ rows }: { rows: Count[] }) => {
  const sortedRows = useMemo(
    () => [...rows].sort((a, b) => b.count - a.count),
    [rows],
  );
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const [pinnedIndex, setPinnedIndex] = useState<number | null>(null);
  const activeIndex = hoveredIndex ?? pinnedIndex;
  const activeItem = activeIndex === null ? null : sortedRows[activeIndex];
  const total = sortedRows.reduce((sum, item) => sum + item.count, 0);
  const radius = 70;
  const circumference = 2 * Math.PI * radius;
  let consumedLength = 0;

  return (
    <Card className="vroad-chart-card vroad-donut-card" variant="borderless">
      <div className="vroad-chart-heading">
        <div>
          <Text className="vroad-chart-eyebrow">Cơ cấu dữ liệu</Text>
          <Title level={4}>Nhóm tài sản khảo sát</Title>
        </div>
        <span className="vroad-chart-count">{sortedRows.length} nhóm</span>
      </div>

      <span
        className="kcht-visually-hidden"
        role="img"
        aria-label="Biểu đồ Nhóm tài sản"
      >
        {sortedRows.map((item) => `${item.label}: ${item.count}`).join("; ")}
      </span>

      {sortedRows.length === 0 ? (
        <div className="vroad-empty-chart">Chưa có dữ liệu tài sản.</div>
      ) : (
        <div className="vroad-donut-layout">
          <div className="vroad-donut-visual">
            <svg
              className={activeIndex === null ? "" : "has-active"}
              viewBox="0 0 200 200"
              aria-hidden="true"
            >
              <circle
                className="vroad-donut-track"
                cx="100"
                cy="100"
                r={radius}
              />
              {sortedRows.map((item, index) => {
                const segmentLength =
                  (getPercentage(item.count, total) / 100) * circumference;
                const dashLength = Math.max(0, segmentLength - 2.5);
                const dashOffset = -consumedLength;
                consumedLength += segmentLength;
                const isActive = activeIndex === index;
                return (
                  <Tooltip
                    key={item.label}
                    title={
                      <ChartTooltip {...item} total={total} unit="tài sản" />
                    }
                    trigger={["hover", "focus", "click"]}
                  >
                    <circle
                      className={`vroad-donut-segment${isActive ? " is-active" : ""}`}
                      cx="100"
                      cy="100"
                      r={radius}
                      fill="none"
                      stroke={donutColors[index % donutColors.length]}
                      strokeDasharray={`${dashLength} ${circumference - dashLength}`}
                      strokeDashoffset={dashOffset}
                      transform="rotate(-90 100 100)"
                      tabIndex={0}
                      onMouseEnter={() => setHoveredIndex(index)}
                      onMouseLeave={() => setHoveredIndex(null)}
                      onFocus={() => setHoveredIndex(index)}
                      onBlur={() => setHoveredIndex(null)}
                      onClick={() =>
                        setPinnedIndex((current) =>
                          current === index ? null : index,
                        )
                      }
                    />
                  </Tooltip>
                );
              })}
            </svg>

            <div className="vroad-donut-center" aria-live="polite">
              <span>{activeItem?.label || "Tổng tài sản"}</span>
              <strong>
                {numberFormatter.format(activeItem?.count ?? total)}
              </strong>
              <small>
                {activeItem
                  ? `${getPercentage(activeItem.count, total).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}% tổng số`
                  : "tài sản khảo sát"}
              </small>
            </div>
          </div>

          <div className="vroad-donut-legend">
            {sortedRows.map((item, index) => (
              <Tooltip
                key={item.label}
                title={<ChartTooltip {...item} total={total} unit="tài sản" />}
                trigger={["hover", "focus", "click"]}
              >
                <button
                  type="button"
                  className={`vroad-legend-item${activeIndex === index ? " is-active" : ""}`}
                  onMouseEnter={() => setHoveredIndex(index)}
                  onMouseLeave={() => setHoveredIndex(null)}
                  onFocus={() => setHoveredIndex(index)}
                  onBlur={() => setHoveredIndex(null)}
                  onClick={() =>
                    setPinnedIndex((current) =>
                      current === index ? null : index,
                    )
                  }
                >
                  <span
                    className="vroad-legend-swatch"
                    style={
                      {
                        "--vroad-series-color":
                          donutColors[index % donutColors.length],
                      } as CSSProperties
                    }
                  />
                  <span className="vroad-legend-label" title={item.label}>
                    {item.label}
                  </span>
                  <strong>
                    {getPercentage(item.count, total).toLocaleString("vi-VN", {
                      maximumFractionDigits: 1,
                    })}
                    %
                  </strong>
                </button>
              </Tooltip>
            ))}
          </div>
        </div>
      )}
    </Card>
  );
};

interface SurveyKpiProps {
  label: string;
  value: number;
  note: string;
  icon: ReactNode;
  tone: "blue" | "orange" | "teal" | "green";
  actionLabel: string;
  onAction: () => void;
}

const SurveyKpi = ({
  label,
  value,
  note,
  icon,
  tone,
  actionLabel,
  onAction,
}: SurveyKpiProps) => (
  <Card className={`vroad-kpi vroad-kpi--${tone}`} variant="borderless">
    <div className="vroad-kpi-topline">
      <div className="vroad-kpi-icon">{icon}</div>
      <span className="vroad-kpi-status">Dữ liệu trực tiếp</span>
    </div>
    <Text className="vroad-kpi-label">{label}</Text>
    <div className="vroad-kpi-value">{numberFormatter.format(value)}</div>
    <div className="vroad-kpi-footer">
      <Text>{note}</Text>
      <Button type="link" size="small" onClick={onAction}>
        {actionLabel} <ArrowRightOutlined />
      </Button>
    </div>
  </Card>
);

export const VroadOverviewPanel = () => {
  const navigate = useNavigate();
  const { data, isLoading, isFetching, error, refetch } =
    useQuery<SurveyOverview>({
      queryKey: ["vroadOverview"],
      queryFn: fetchSurveyOverview,
      staleTime: 60000,
    });

  return (
    <section className="vroad-overview" aria-label="Thống kê khảo sát đường bộ">
      <div className="vroad-overview-header">
        <div>
          <div className="vroad-section-kicker">
            <span className="vroad-live-dot" />
            Khảo sát hiện trường VroadAI
          </div>
          <Title level={3}>Tình hình khảo sát đường bộ</Title>
          <Text>
            Theo dõi tài sản, điểm hư hỏng và hồ sơ xử lý từ dữ liệu nhập để đối
            soát.
          </Text>
        </div>
        <div className="vroad-overview-actions">
          <Button
            icon={<ReloadOutlined spin={isFetching} />}
            onClick={() => void refetch()}
            loading={isFetching}
          >
            Đồng bộ
          </Button>
          <Button
            type="primary"
            icon={<EnvironmentOutlined />}
            onClick={() => navigate("/map")}
          >
            Xem bản đồ
          </Button>
        </div>
      </div>

      {error && (
        <Alert
          type="error"
          showIcon
          message={getApiErrorMessage(error)}
          action={<Button onClick={() => void refetch()}>Thử lại</Button>}
          className="vroad-overview-alert"
        />
      )}

      {isLoading && (
        <Skeleton
          active
          className="vroad-overview-skeleton"
          paragraph={{ rows: 8 }}
        />
      )}

      {data && (
        <>
          <Row gutter={[16, 16]} className="vroad-kpi-grid">
            <Col xs={24} sm={12} xl={6}>
              <SurveyKpi
                label="Tài sản khảo sát"
                value={data.assets}
                note="Bản ghi đã nhập để đối soát"
                icon={<AppstoreOutlined />}
                tone="blue"
                actionLabel="Trên bản đồ"
                onAction={() => navigate("/map")}
              />
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <SurveyKpi
                label="Điểm hư hỏng"
                value={data.defects}
                note="Đã phân loại theo hiện trạng"
                icon={<WarningOutlined />}
                tone="orange"
                actionLabel="Xem điểm nóng"
                onAction={() => navigate("/map")}
              />
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <SurveyKpi
                label="Đoạn đánh giá IRI"
                value={data.iriSegments}
                note="Theo dõi độ bằng phẳng mặt đường"
                icon={<LineChartOutlined />}
                tone="teal"
                actionLabel="Mở báo cáo"
                onAction={() => navigate("/reports")}
              />
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <SurveyKpi
                label="Hồ sơ đã xử lý"
                value={data.resolvedCases}
                note="Hồ sơ demo có bằng chứng xử lý"
                icon={<CheckCircleOutlined />}
                tone="green"
                actionLabel="Xem hồ sơ"
                onAction={() => navigate("/cases")}
              />
            </Col>
          </Row>

          <Row gutter={[16, 16]} className="vroad-chart-grid">
            <Col xs={24} xl={14}>
              <DefectBarChart rows={data.defectTypes} />
            </Col>
            <Col xs={24} xl={10}>
              <AssetDonutChart rows={data.assetCategories} />
            </Col>
          </Row>
        </>
      )}
    </section>
  );
};
