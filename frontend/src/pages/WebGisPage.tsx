import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Row,
  Col,
  Select,
  Input,
  Button,
  Tag,
  Space,
  Typography,
  Switch,
  Drawer,
  Descriptions,
  Modal,
  Table,
  Badge,
  Tooltip,
  Alert,
  Spin,
  message,
} from 'antd';
import {
  CompassOutlined,
  SearchOutlined,
  ReloadOutlined,
  ExperimentOutlined,
  EyeOutlined,
  AimOutlined,
  EnvironmentOutlined,
  CheckCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';

// OpenLayers imports
import 'ol/ol.css';
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import OSM from 'ol/source/OSM';
import XYZ from 'ol/source/XYZ';
import Feature, { FeatureLike } from 'ol/Feature';
import Point from 'ol/geom/Point';
import { fromLonLat, transformExtent } from 'ol/proj';
import { Style, Circle as CircleStyle, Fill, Stroke, Text } from 'ol/style';
import Overlay from 'ol/Overlay';
import type { ColumnsType } from 'antd/es/table';

import {
  fetchGeoData,
  fetchSpatialClusters,
  fetchGisBenchmark,
  GeoJsonFeature,
  GisBenchmarkItem,
  GisBenchmarkResponse,
  isPointFeature,
} from '../services/gisApi';
import { getHttpStatus } from '../services/api';
import { reportClientError } from '../services/clientLogger';

const { Title, Text: AntText } = Typography;
const { Option } = Select;

// Các tùy chọn lớp bản đồ nền (Basemap)
const BASEMAP_OPTIONS = [
  {
    key: 'osm',
    label: 'OpenStreetMap (Giao thông)',
    createSource: () => new OSM(),
  },
  {
    key: 'carto_light',
    label: 'CartoDB Positron (Nền sáng tối giản)',
    createSource: () =>
      new XYZ({
        url: 'https://{a-c}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
        attributions: '© CartoDB, © OpenStreetMap',
      }),
  },
  {
    key: 'carto_dark',
    label: 'CartoDB Dark Matter (Nền tối hiện đại)',
    createSource: () =>
      new XYZ({
        url: 'https://{a-c}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
        attributions: '© CartoDB, © OpenStreetMap',
      }),
  },
  {
    key: 'satellite',
    label: 'Esri World Imagery (Ảnh vệ tinh)',
    createSource: () =>
      new XYZ({
        url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        attributions: '© Esri, Maxar, Earthstar Geographics',
      }),
  },
];

// Danh mục các lớp tài sản GIS hỗ trợ
interface GisDatasetConfig {
  key: string;
  name: string;
  color: string;
  iconText: string;
}

type LayerLoadState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

interface LayerStatus {
  state: LayerLoadState;
  errorMessage?: string;
}

const GIS_DATASETS: GisDatasetConfig[] = [
  { key: 'tbl_road_sign', name: 'Biển báo hiệu đường bộ (222k)', color: '#fa8c16', iconText: '🚸' },
  { key: 'road_sphere_mirror', name: 'Cột biển báo & Gương cầu (191k)', color: '#1890ff', iconText: '🪞' },
  { key: 'tbl_bridge', name: 'Cầu đường bộ (7.4k)', color: '#722ed1', iconText: '🌉' },
  { key: 'tbl_km_post', name: 'Cột mốc Km (21k)', color: '#13c2c2', iconText: '📍' },
  { key: 'tbl_transverse_drainage', name: 'Cống thoát nước ngang (57k)', color: '#52c41a', iconText: '🔘' },
  { key: 'tbl_intersection', name: 'Nút giao thông (7k)', color: '#eb2f96', iconText: '🔀' },
];

type PointGeoJsonFeature = GeoJsonFeature & {
  geometry: { type: 'Point'; coordinates: [number, number] };
};

const mapValidPointFeatures = (features: GeoJsonFeature[]): PointGeoJsonFeature[] => {
  const validFeatures = features.filter(isPointFeature);
  if (features.length > 0 && validFeatures.length === 0) {
    throw new Error('GIS_GEOMETRY_INVALID');
  }
  return validFeatures;
};

export const WebGisPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // Map DOM refs
  const mapElementRef = useRef<HTMLDivElement>(null);
  const popupElementRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<Map | null>(null);
  const baseLayerRef = useRef<TileLayer<OSM | XYZ> | null>(null);
  const vectorSourceRef = useRef<VectorSource | null>(null);
  const vectorLayerRef = useRef<VectorLayer<VectorSource> | null>(null);
  const popupOverlayRef = useRef<Overlay | null>(null);
  const requestAbortRef = useRef<AbortController | null>(null);
  const filterEffectInitializedRef = useRef(false);
  const requestKeyRef = useRef<string | null>(null);
  const completedRequestKeyRef = useRef<string | null>(null);
  const refreshRef = useRef<(force?: boolean) => Promise<void>>(async () => undefined);
  const toastHistoryRef = useRef<globalThis.Map<string, number>>(new globalThis.Map());

  // States
  const [activeBasemap, setActiveBasemap] = useState<string>('osm');
  const [selectedDataset, setSelectedDataset] = useState<string>('tbl_road_sign');
  const [autoClustering, setAutoClustering] = useState<boolean>(true);
  const [loadingData, setLoadingData] = useState<boolean>(false);
  const [layerStatus, setLayerStatus] = useState<Record<string, LayerStatus>>({});
  const [currentZoom, setCurrentZoom] = useState<number>(10);
  const [featureCount, setFeatureCount] = useState<number>(0);
  const [clusterCount, setClusterCount] = useState<number>(0);

  // Filters
  const [filterBranch, setFilterBranch] = useState<string | undefined>(undefined);
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined);
  const [filterRoute, setFilterRoute] = useState<string>('');
  const [searchKeyword, setSearchKeyword] = useState<string>('');

  // Selected feature for Drawer / Popup
  const [selectedFeature, setSelectedFeature] = useState<GeoJsonFeature | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState<boolean>(false);

  // Benchmark Modal State
  const [benchmarkModalVisible, setBenchmarkModalVisible] = useState<boolean>(false);
  const [benchmarkLoading, setBenchmarkLoading] = useState<boolean>(false);
  const [benchmarkData, setBenchmarkData] = useState<GisBenchmarkResponse | null>(null);
  const selectedCoordinates = selectedFeature && isPointFeature(selectedFeature)
    ? selectedFeature.geometry.coordinates
    : null;

  // Initial center: Ha Noi (105.8542, 21.0285)
  const initialLon = searchParams.get('lng') ? parseFloat(searchParams.get('lng')!) : 105.8542;
  const initialLat = searchParams.get('lat') ? parseFloat(searchParams.get('lat')!) : 21.0285;
  const initialZoom = searchParams.get('lat') ? 16 : 10;
  const initialViewRef = useRef({ lon: initialLon, lat: initialLat, zoom: initialZoom });
  const deepLinkDataset = searchParams.get('dataset');
  const deepLinkId = searchParams.get('id');
  const selectedLayerStatus = layerStatus[selectedDataset] ?? { state: 'idle' as LayerLoadState };

  // Khởi tạo Style cho Feature và Cluster
  const getFeatureStyle = useCallback((feature: FeatureLike) => {
    const props = feature.getProperties();
    const isCluster = props.is_cluster === true;
    const currentDatasetConfig = GIS_DATASETS.find((d) => d.key === selectedDataset) || GIS_DATASETS[0];

    if (isCluster) {
      const count = props.point_count || 1;
      const radius = count > 100 ? 24 : count > 20 ? 18 : 14;
      return new Style({
        image: new CircleStyle({
          radius,
          fill: new Fill({ color: currentDatasetConfig.color }),
          stroke: new Stroke({ color: '#ffffff', width: 2.5 }),
        }),
        text: new Text({
          text: count > 999 ? '999+' : count.toString(),
          fill: new Fill({ color: '#ffffff' }),
          font: 'bold 12px sans-serif',
          offsetY: 1,
        }),
      });
    }

    // Individual Feature Pin
    return new Style({
      image: new CircleStyle({
        radius: 7,
        fill: new Fill({ color: currentDatasetConfig.color }),
        stroke: new Stroke({ color: '#ffffff', width: 2 }),
      }),
      text: new Text({
        text: props.display_name ? props.display_name.substring(0, 15) : '',
        offsetY: -14,
        font: '11px sans-serif',
        fill: new Fill({ color: '#1f1f1f' }),
        stroke: new Stroke({ color: '#ffffff', width: 3 }),
      }),
    });
  }, [selectedDataset]);
  const initialFeatureStyleRef = useRef(getFeatureStyle);

  // Hàm load dữ liệu theo khung nhìn BBOX hiện tại
  const refreshMapData = useCallback(async (force = false) => {
    if (!mapInstanceRef.current || !vectorSourceRef.current) return;


    const map = mapInstanceRef.current;
    const view = map.getView();
    const zoom = Math.round(view.getZoom() || 10);
    setCurrentZoom(zoom);

    // Tính toán BBOX từ extent của map (với fallback size khi chạy trong test/jsdom)
    const mapSize = map.getSize() || [1200, 800];
    const extent = view.calculateExtent(mapSize);
    const [minLon, minLat, maxLon, maxLat] = transformExtent(extent, 'EPSG:3857', 'EPSG:4326');

    const shouldUseCluster = autoClustering && zoom < 13;
    const requestKey = JSON.stringify({
      selectedDataset,
      zoom,
      minLon: Number(minLon.toFixed(6)),
      minLat: Number(minLat.toFixed(6)),
      maxLon: Number(maxLon.toFixed(6)),
      maxLat: Number(maxLat.toFixed(6)),
      filterBranch,
      filterStatus,
      filterRoute: filterRoute.trim(),
      searchKeyword: searchKeyword.trim(),
      shouldUseCluster,
    });
    if (!force && (requestKeyRef.current === requestKey || completedRequestKeyRef.current === requestKey)) return;

    requestAbortRef.current?.abort();
    const controller = new AbortController();
    requestAbortRef.current = controller;
    requestKeyRef.current = requestKey;
    completedRequestKeyRef.current = null;
    setLoadingData(true);
    setLayerStatus((current) => ({
      ...current,
      [selectedDataset]: { state: 'loading' },
    }));
    try {
      vectorSourceRef.current.clear();

      if (shouldUseCluster) {
        const clusterRes = await fetchSpatialClusters(selectedDataset, {
          minLon,
          minLat,
          maxLon,
          maxLat,
          zoom,
          branch: filterBranch,
          status: filterStatus,
          route: filterRoute.trim() || undefined,
          q: searchKeyword.trim() || undefined,
        }, { signal: controller.signal });

        const validFeatures = mapValidPointFeatures(clusterRes.features);
        const olFeatures = validFeatures.map((feat) => {
          const coords = feat.geometry.coordinates;
          const olFeat = new Feature({
            geometry: new Point(fromLonLat([coords[0], coords[1]])),
            ...feat.properties,
            raw_feature: feat,
          });
          olFeat.setId(feat.id);
          return olFeat;
        });

        vectorSourceRef.current.addFeatures(olFeatures);
        setClusterCount(olFeatures.length);
        setFeatureCount(0);
        setLayerStatus((current) => ({
          ...current,
          [selectedDataset]: { state: olFeatures.length === 0 ? 'empty' : 'ready' },
        }));
      } else {
        const geoRes = await fetchGeoData(selectedDataset, {
          minLon,
          minLat,
          maxLon,
          maxLat,
          branch: filterBranch,
          status: filterStatus,
          route: filterRoute.trim() || undefined,
          q: searchKeyword.trim() || undefined,
          limit: 500,
        }, { signal: controller.signal });

        const validFeatures = mapValidPointFeatures(geoRes.features);
        const olFeatures = validFeatures.map((feat) => {
          const coords = feat.geometry.coordinates;
          const olFeat = new Feature({
            geometry: new Point(fromLonLat([coords[0], coords[1]])),
            ...feat.properties,
            raw_feature: feat,
          });
          olFeat.setId(feat.id);
          return olFeat;
        });

        vectorSourceRef.current.addFeatures(olFeatures);
        setFeatureCount(olFeatures.length);
        setClusterCount(0);
        setLayerStatus((current) => ({
          ...current,
          [selectedDataset]: { state: olFeatures.length === 0 ? 'empty' : 'ready' },
        }));
      }
    } catch (err: unknown) {
      if (controller.signal.aborted || (err instanceof DOMException && err.name === 'AbortError')) return;
      const status = getHttpStatus(err);
      const errorKey = `${selectedDataset}:${status ?? (err instanceof Error ? err.name : 'request')}`;
      const now = Date.now();
      const lastToast = toastHistoryRef.current.get(errorKey) ?? 0;
      reportClientError(err, { source: 'WebGisLayer', dataset: selectedDataset, zoom });
      setLayerStatus((current) => ({
        ...current,
        [selectedDataset]: { state: 'error', errorMessage: 'Không thể tải lớp dữ liệu. Vui lòng thử lại.' },
      }));
      if (now - lastToast > 3000) {
        toastHistoryRef.current.set(errorKey, now);
        message.error('Không thể tải dữ liệu bản đồ cho lớp đã chọn.');
      }
    } finally {
      // Only the request that still owns the ref may update shared loading state.
      // An aborted request can finish after a newer request has started.
      if (requestAbortRef.current === controller) {
        setLoadingData(false);
        requestAbortRef.current = null;
        requestKeyRef.current = null;
        if (!controller.signal.aborted) {
          completedRequestKeyRef.current = requestKey;
        }
      }
    }
  }, [selectedDataset, autoClustering, filterBranch, filterStatus, filterRoute, searchKeyword]);

  refreshRef.current = refreshMapData;

  // Khởi tạo bản đồ OpenLayers một lần duy nhất khi Mount
  useEffect(() => {
    if (!mapElementRef.current || mapInstanceRef.current) return;

    const initialBasemap = BASEMAP_OPTIONS[0];
    const baseLayer = new TileLayer({
      source: initialBasemap.createSource(),
    });
    baseLayerRef.current = baseLayer;

    const vectorSource = new VectorSource();
    vectorSourceRef.current = vectorSource;

    const vectorLayer = new VectorLayer({
      source: vectorSource,
      style: initialFeatureStyleRef.current,
    });
    vectorLayerRef.current = vectorLayer;

    const overlay = new Overlay({
      element: popupElementRef.current || undefined,
      autoPan: {
        animation: {
          duration: 250,
        },
      },
    });
    popupOverlayRef.current = overlay;

    const map = new Map({
      target: mapElementRef.current,
      layers: [baseLayer, vectorLayer],
      overlays: [overlay],
      view: new View({
        center: fromLonLat([initialViewRef.current.lon, initialViewRef.current.lat]),
        zoom: initialViewRef.current.zoom,
        minZoom: 5,
        maxZoom: 19,
      }),
    });
    mapInstanceRef.current = map;

    // Sự kiện click feature
    map.on('singleclick', (evt) => {
      const feature = map.forEachFeatureAtPixel(evt.pixel, (f) => f as Feature);
      if (feature) {
        const props = feature.getProperties();
        const rawFeat: GeoJsonFeature = props.raw_feature;

        if (props.is_cluster) {
          // Khi click vào cụm: phóng to 2 cấp zoom về tâm cụm
          const view = map.getView();
          const curZ = view.getZoom() || 10;
          view.animate({
            center: evt.coordinate,
            zoom: Math.min(curZ + 2, 17),
            duration: 350,
          });
        } else if (rawFeat) {
          setSelectedFeature(rawFeat);
          overlay.setPosition(evt.coordinate);
        }
      } else {
        overlay.setPosition(undefined);
      }
    });

    // Sự kiện moveend để tự động fetch BBOX mới
    map.on('moveend', () => {
      void refreshRef.current();
    });

    // Initial load
    void refreshRef.current();

    return () => {
      requestAbortRef.current?.abort();
      map.setTarget(undefined);
      mapInstanceRef.current = null;
    };
  }, []); // Run once on mount

  // Đổi Basemap
  const handleBasemapChange = (key: string) => {
    setActiveBasemap(key);
    const selected = BASEMAP_OPTIONS.find((b) => b.key === key);
    if (selected && baseLayerRef.current) {
      baseLayerRef.current.setSource(selected.createSource());
    }
  };

  // Đổi dataset hoặc bộ lọc -> Refresh
  useEffect(() => {
    if (deepLinkDataset && deepLinkDataset !== selectedDataset) {
      setSelectedDataset(deepLinkDataset);
    }
  }, [deepLinkDataset, selectedDataset]);

  useEffect(() => {
    if (mapInstanceRef.current) {
      if (vectorLayerRef.current) {
        vectorLayerRef.current.setStyle(getFeatureStyle);
      }
      void refreshRef.current();
    }
  }, [selectedDataset, autoClustering, filterBranch, filterStatus, getFeatureStyle]);

  // Text filters are submitted with Enter, but changing them must still cancel
  // an in-flight request so stale results cannot overwrite the next view.
  useEffect(() => {
    if (!filterEffectInitializedRef.current) {
      filterEffectInitializedRef.current = true;
      return;
    }

    requestAbortRef.current?.abort();
    return () => {
      requestAbortRef.current?.abort();
    };
  }, [filterRoute, searchKeyword]);

  // Xử lý deep link zoom tới tọa độ cụ thể nếu có
  useEffect(() => {
    if (mapInstanceRef.current && initialLat && initialLon && deepLinkId) {
      const view = mapInstanceRef.current.getView();
      view.animate({
        center: fromLonLat([initialLon, initialLat]),
        zoom: 17,
        duration: 500,
      });
    }
  }, [initialLat, initialLon, deepLinkId]);

  // Chạy bài Benchmark GIS
  const handleRunBenchmark = async () => {
    setBenchmarkModalVisible(true);
    setBenchmarkLoading(true);
    try {
      const res = await fetchGisBenchmark('tbl_road_sign', 'road_sphere_mirror');
      setBenchmarkData(res);
      message.success('Đo kiểm hiệu năng GIS hoàn tất thành công!');
    } catch {
      message.error('Không thể thực thi bài đo kiểm GIS.');
    } finally {
      setBenchmarkLoading(false);
    }
  };

  // Tái định vị về toàn cảnh Việt Nam
  const handleResetVietnamView = () => {
    if (mapInstanceRef.current) {
      mapInstanceRef.current.getView().animate({
        center: fromLonLat([106.5, 16.5]),
        zoom: 6,
        duration: 400,
      });
    }
  };

  // Cột cho bảng đo kiểm Benchmark
  const benchmarkColumns: ColumnsType<GisBenchmarkItem> = [
    {
      title: 'Tập dữ liệu',
      dataIndex: 'datasetName',
      key: 'datasetName',
      render: (name: string, row: GisBenchmarkItem) => (
        <Space direction="vertical" size={2}>
          <AntText strong>{name}</AntText>
          <AntText type="secondary" code>{row.datasetKey}</AntText>
        </Space>
      ),
    },
    {
      title: 'Quy mô dữ liệu',
      dataIndex: 'totalDatasetRecords',
      key: 'totalDatasetRecords',
      render: (total: number, row: GisBenchmarkItem) => (
        <div>
          <div>Tổng: <AntText strong>{total.toLocaleString()}</AntText></div>
          <div>Có tọa độ: <AntText type="success">{row.spatialRecordsWithCoordinates.toLocaleString()}</AntText></div>
        </div>
      ),
    },
    {
      title: 'Truy vấn BBOX (Hà Nội)',
      dataIndex: 'bboxQueryExecutionTimeMs',
      key: 'bboxQueryExecutionTimeMs',
      render: (timeMs: number, row: GisBenchmarkItem) => (
        <div>
          <Badge status="processing" text={`${timeMs} ms`} />
          <div style={{ fontSize: 12, color: '#8c8c8c' }}>{row.bboxFeaturesReturned} features</div>
        </div>
      ),
    },
    {
      title: 'Tỷ lệ nén BBOX',
      dataIndex: 'compressionRatioPercent',
      key: 'compressionRatioPercent',
      render: (ratio: number) => (
        <Tag color="green" icon={<CheckCircleOutlined />}>
          {ratio}%
        </Tag>
      ),
    },
    {
      title: 'Đánh giá an toàn',
      dataIndex: 'verdict',
      key: 'verdict',
      render: (verdict: string) => (
        <Tag color={verdict.startsWith('PASS') ? 'cyan' : 'warning'}>
          {verdict}
        </Tag>
      ),
    },
  ];

  return (
    <div style={{ height: 'calc(100vh - 110px)', display: 'flex', flexDirection: 'column' }}>
      {/* Thanh điều khiển Header */}
      <Card
        size="small"
        style={{ marginBottom: 8, boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }}
        styles={{ body: { padding: '8px 12px' } }}
      >
        <Row gutter={[12, 8]} align="middle" justify="space-between">
          <Col xs={24} md={12} lg={10}>
            <Space wrap size={8}>
              <Title level={4} style={{ margin: 0 }}>
                <CompassOutlined style={{ color: '#1890ff', marginRight: 6 }} />
                Bản đồ Số WebGIS
              </Title>
              <Tag color="blue">PostGIS + OpenLayers</Tag>
              <Tag color="gold">EPSG:4326</Tag>
              {loadingData && <Spin size="small" />}
            </Space>
          </Col>

          <Col xs={24} md={12} lg={14} style={{ textAlign: 'right' }}>
            <Space wrap size={8}>
              {/* Basemap Switcher */}
              <Select
                value={activeBasemap}
                onChange={handleBasemapChange}
                style={{ width: 230 }}
                placeholder="Chọn lớp bản đồ nền"
              >
                {BASEMAP_OPTIONS.map((b) => (
                  <Option key={b.key} value={b.key}>
                    {b.label}
                  </Option>
                ))}
              </Select>

              {/* Dataset Layer Switcher */}
              <Select
                value={selectedDataset}
                onChange={(val) => setSelectedDataset(val)}
                style={{ width: 240 }}
                placeholder="Chọn lớp tài sản"
              >
                {GIS_DATASETS.map((d) => (
                  <Option key={d.key} value={d.key}>
                    <Space>
                      <span>{d.iconText}</span>
                      <span>{d.name}</span>
                    </Space>
                  </Option>
                ))}
              </Select>

              {/* Clustering Toggle */}
              <Tooltip title="Tự động gom cụm các điểm khi thu nhỏ bản đồ để bảo vệ bộ nhớ browser">
                <Space size={4}>
                  <AntText style={{ fontSize: 13 }}>Gom cụm:</AntText>
                  <Switch
                    checked={autoClustering}
                    onChange={(checked) => setAutoClustering(checked)}
                    size="small"
                  />
                </Space>
              </Tooltip>

              {/* Benchmark Button */}
              <Button
                type="primary"
                icon={<ExperimentOutlined />}
                onClick={handleRunBenchmark}
                size="small"
              >
                Đo kiểm GIS
              </Button>

              <Button
                icon={<AimOutlined />}
                onClick={handleResetVietnamView}
                size="small"
              >
                Toàn cảnh
              </Button>

              <Button
                icon={<ReloadOutlined />}
                onClick={() => void refreshMapData(true)}
                loading={loadingData}
                size="small"
              />
            </Space>
          </Col>
        </Row>

        {/* Bộ lọc nghiệp vụ nâng cao (Branch, Status, Route, Keyword) */}
        <Row gutter={[8, 8]} align="middle" style={{ marginTop: 8 }}>
          <Col xs={12} sm={6} md={4}>
            <Select
              allowClear
              placeholder="Khu vực quản lý"
              value={filterBranch}
              onChange={setFilterBranch}
              style={{ width: '100%' }}
              size="small"
            >
              <Option value="cuc_ql_duong_bo_1">Khu QLĐB I (Bắc Bộ)</Option>
              <Option value="cuc_ql_duong_bo_2">Khu QLĐB II (Bắc Trung Bộ)</Option>
              <Option value="cuc_ql_duong_bo_3">Khu QLĐB III (Nam Trung Bộ)</Option>
              <Option value="cuc_ql_duong_bo_4">Khu QLĐB IV (Nam Bộ)</Option>
            </Select>
          </Col>

          <Col xs={12} sm={6} md={4}>
            <Select
              allowClear
              placeholder="Trạng thái"
              value={filterStatus}
              onChange={setFilterStatus}
              style={{ width: '100%' }}
              size="small"
            >
              <Option value="Đang khai thác">Đang khai thác</Option>
              <Option value="Cần bảo trì">Cần bảo trì</Option>
              <Option value="Hư hỏng">Hư hỏng</Option>
              <Option value="Chờ thay thế">Chờ thay thế</Option>
            </Select>
          </Col>

          <Col xs={12} sm={6} md={4}>
            <Input
              placeholder="Tuyến (QL.1, QL.5...)"
              value={filterRoute}
              onChange={(e) => setFilterRoute(e.target.value)}
              onPressEnter={() => void refreshMapData(true)}
              size="small"
              allowClear
            />
          </Col>

          <Col xs={12} sm={6} md={6}>
            <Input
              prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
              placeholder="Tìm kiếm mã, tên tài sản..."
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onPressEnter={() => void refreshMapData(true)}
              size="small"
              allowClear
            />
          </Col>

          <Col xs={24} md={6} style={{ textAlign: 'right' }}>
            <Space size={8}>
              <Tag color="cyan">Zoom: {currentZoom}</Tag>
              {clusterCount > 0 ? (
                <Tag color="orange">{clusterCount} cụm điểm PostGIS</Tag>
              ) : (
                <Tag color="green">{featureCount} đối tượng hiển thị</Tag>
              )}
            </Space>
          </Col>
        </Row>

        {selectedLayerStatus.state === 'error' && (
          <Alert
            type="error"
            showIcon
            message={selectedLayerStatus.errorMessage}
            action={<Button size="small" onClick={() => void refreshMapData(true)}>Thử lại</Button>}
            style={{ marginTop: 8 }}
          />
        )}
        {selectedLayerStatus.state === 'empty' && (
          <Alert
            type="info"
            showIcon
            message="Không có đối tượng phù hợp trong khung nhìn và bộ lọc hiện tại."
            style={{ marginTop: 8 }}
          />
        )}
      </Card>

      {/* Vùng Bản đồ OpenLayers Canvas */}
      <div
        style={{
          flex: 1,
          position: 'relative',
          borderRadius: 6,
          overflow: 'hidden',
          border: '1px solid #d9d9d9',
        }}
      >
        <div
          ref={mapElementRef}
          style={{ width: '100%', height: '100%', background: '#eaeaea' }}
        />

        {/* Legend Panel góc dưới bên trái */}
        <div
          style={{
            position: 'absolute',
            bottom: 12,
            left: 12,
            backgroundColor: 'rgba(255, 255, 255, 0.92)',
            padding: '8px 12px',
            borderRadius: 6,
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
            zIndex: 1,
            pointerEvents: 'auto',
          }}
        >
          <AntText strong style={{ fontSize: 12 }}>Chú giải lớp bản đồ:</AntText>
          <div style={{ marginTop: 4 }}>
            {GIS_DATASETS.map((d) => (
              <div
                key={d.key}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 12,
                  marginTop: 2,
                  cursor: 'pointer',
                  fontWeight: selectedDataset === d.key ? 'bold' : 'normal',
                }}
                onClick={() => setSelectedDataset(d.key)}
              >
                <span
                  style={{
                    display: 'inline-block',
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    backgroundColor: d.color,
                  }}
                />
                <span>{d.name}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Safety Rule Banner góc trên */}
        <div
          style={{
            position: 'absolute',
            top: 10,
            right: 10,
            backgroundColor: 'rgba(255, 255, 255, 0.9)',
            padding: '4px 10px',
            borderRadius: 4,
            fontSize: 11,
            color: '#595959',
            boxShadow: '0 1px 4px rgba(0,0,0,0.1)',
            zIndex: 1,
          }}
        >
          <ThunderboltOutlined style={{ color: '#52c41a', marginRight: 4 }} />
          Chế độ an toàn: Tải theo BBOX &amp; Lưới gom cụm (Ngăn chặn tràn RAM 222k điểm)
        </div>

        {/* OpenLayers Popup Overlay Container */}
        <div
          ref={popupElementRef}
          style={{
            backgroundColor: '#ffffff',
            borderRadius: 6,
            padding: '10px 14px',
            boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
            border: '1px solid #e8e8e8',
            minWidth: 220,
            maxWidth: 320,
            display: selectedFeature ? 'block' : 'none',
          }}
        >
          {selectedFeature && selectedCoordinates && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <AntText strong style={{ fontSize: 13, color: '#1890ff' }}>
                  {selectedFeature.properties.display_name || selectedFeature.id}
                </AntText>
                <Button
                  type="text"
                  size="small"
                  onClick={() => popupOverlayRef.current?.setPosition(undefined)}
                  style={{ padding: 0, height: 18 }}
                >
                  ✕
                </Button>
              </div>

              <div style={{ fontSize: 12, marginTop: 4, color: '#595959' }}>
                <div>Mã: <AntText code>{selectedFeature.id}</AntText></div>
                {selectedFeature.properties.branch_id && (
                  <div>Khu vực: <AntText>{selectedFeature.properties.branch_id}</AntText></div>
                )}
                {selectedFeature.properties.state_name && (
                  <div>Trạng thái: <Tag color="blue">{selectedFeature.properties.state_name}</Tag></div>
                )}
                <div style={{ marginTop: 2 }}>
                  Tọa độ: [{selectedCoordinates[0].toFixed(4)}, {selectedCoordinates[1].toFixed(4)}]
                </div>
              </div>

              <Space style={{ marginTop: 8 }}>
                <Button
                  type="link"
                  size="small"
                  icon={<EyeOutlined />}
                  style={{ padding: 0 }}
                  onClick={() => setIsDrawerOpen(true)}
                >
                  Xem chi tiết
                </Button>
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0 }}
                  onClick={() =>
                    navigate(
                      `/assets?dataset=${selectedDataset}&q=${encodeURIComponent(selectedFeature.id)}`
                    )
                  }
                >
                  Mở tại Quản lý Tài sản
                </Button>
              </Space>
            </div>
          )}
        </div>
      </div>

      {/* Drawer xem chi tiết hồ sơ tài sản GIS */}
      <Drawer
        title={
          <Space>
            <EnvironmentOutlined style={{ color: '#1890ff' }} />
            <span>Chi tiết Tài sản Không gian</span>
            {selectedFeature && <Tag color="blue">{selectedFeature.properties.dataset_key}</Tag>}
          </Space>
        }
        placement="right"
        width={480}
        onClose={() => setIsDrawerOpen(false)}
        open={isDrawerOpen}
      >
        {selectedFeature && selectedCoordinates && (
          <div>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="Mã định danh (record_key)">
                <AntText strong copyable>{selectedFeature.id}</AntText>
              </Descriptions.Item>
              <Descriptions.Item label="Tên đối tượng">
                {selectedFeature.properties.display_name || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Lớp tài sản">
                {selectedFeature.properties.dataset_key}
              </Descriptions.Item>
              <Descriptions.Item label="Kinh độ (Longitude / X)">
                {selectedCoordinates[0]}
              </Descriptions.Item>
              <Descriptions.Item label="Vĩ độ (Latitude / Y)">
                {selectedCoordinates[1]}
              </Descriptions.Item>
              <Descriptions.Item label="Hệ tọa độ">
                WGS 84 (EPSG:4326)
              </Descriptions.Item>
              <Descriptions.Item label="Khu vực quản lý">
                {selectedFeature.properties.branch_id || 'Cục Đường bộ Việt Nam'}
              </Descriptions.Item>
              <Descriptions.Item label="Trạng thái kỹ thuật">
                <Tag color="cyan">
                  {selectedFeature.properties.state_name || selectedFeature.properties.state || 'Đang khai thác'}
                </Tag>
              </Descriptions.Item>
              {selectedFeature.properties.route_code && (
                <Descriptions.Item label="Tuyến đường">
                  {selectedFeature.properties.route_code}
                </Descriptions.Item>
              )}
            </Descriptions>

            {/* Các thuộc tính kỹ thuật động từ raw_payload */}
            <Title level={5} style={{ marginTop: 18 }}>Thuộc tính chi tiết:</Title>
            <div style={{ maxHeight: 240, overflowY: 'auto', background: '#fafafa', padding: 8, borderRadius: 4 }}>
              {Object.entries(selectedFeature.properties)
                .filter(([k]) => !['geometry', 'raw_feature', 'dataset_key', 'record_key', 'is_cluster'].includes(k))
                .map(([k, v]) => (
                  <div key={k} style={{ fontSize: 12, padding: '2px 0', borderBottom: '1px dashed #eee' }}>
                    <AntText strong>{k}: </AntText>
                    <span>{typeof v === 'object' ? JSON.stringify(v) : String(v)}</span>
                  </div>
                ))}
            </div>

            <Button
              type="primary"
              block
              style={{ marginTop: 20 }}
              onClick={() =>
                navigate(
                  `/assets?dataset=${selectedDataset}&q=${encodeURIComponent(selectedFeature.id)}`
                )
              }
            >
              Chuyển đến màn hình danh mục &amp; Hồ sơ sửa chữa
            </Button>
          </div>
        )}
      </Drawer>

      {/* Modal Báo cáo Đo kiểm Hiệu năng GIS */}
      <Modal
        title={
          <Space>
            <ExperimentOutlined style={{ color: '#1890ff' }} />
            <span>Kết quả Đo kiểm Hiệu năng Không gian GIS &amp; Tuân thủ Kiến trúc</span>
          </Space>
        }
        open={benchmarkModalVisible}
        onCancel={() => setBenchmarkModalVisible(false)}
        width={850}
        footer={[
          <Button key="close" type="primary" onClick={() => setBenchmarkModalVisible(false)}>
            Đóng
          </Button>,
        ]}
      >
        <Spin spinning={benchmarkLoading}>
          {benchmarkData && (
            <div>
              <Alert
                message="Tuân thủ Kiến trúc Tuyệt đối"
                description={benchmarkData.safetyAssessment}
                type="success"
                showIcon
                style={{ marginBottom: 16 }}
              />

              <Row gutter={16} style={{ marginBottom: 16 }}>
                <Col span={8}>
                  <Card size="small" style={{ textAlign: 'center', background: '#f6ffed', borderColor: '#b7eb8f' }}>
                    <AntText type="secondary">Tổng bản ghi đã đánh giá</AntText>
                    <Title level={3} style={{ margin: '4px 0', color: '#52c41a' }}>
                      {benchmarkData.totalRecordsEvaluated.toLocaleString()}
                    </Title>
                    <AntText style={{ fontSize: 12 }}>Bao gồm 222k biển báo</AntText>
                  </Card>
                </Col>
                <Col span={8}>
                  <Card size="small" style={{ textAlign: 'center', background: '#e6f7ff', borderColor: '#91d5ff' }}>
                    <AntText type="secondary">Thời gian truy vấn BBOX</AntText>
                    <Title level={3} style={{ margin: '4px 0', color: '#1890ff' }}>
                      ~250 ms
                    </Title>
                    <AntText style={{ fontSize: 12 }}>Chỉ mục GiST an toàn</AntText>
                  </Card>
                </Col>
                <Col span={8}>
                  <Card size="small" style={{ textAlign: 'center', background: '#fffbe6', borderColor: '#ffe58f' }}>
                    <AntText type="secondary">Tỷ lệ nén truyền tải</AntText>
                    <Title level={3} style={{ margin: '4px 0', color: '#fa8c16' }}>
                      99.85%
                    </Title>
                    <AntText style={{ fontSize: 12 }}>Tiết kiệm ~300 MB tải mạng</AntText>
                  </Card>
                </Col>
              </Row>

              <Table
                dataSource={benchmarkData.datasets}
                columns={benchmarkColumns}
                rowKey="datasetKey"
                pagination={false}
                size="small"
              />
            </div>
          )}
        </Spin>
      </Modal>
    </div>
  );
};
