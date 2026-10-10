import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Col, Input, Modal, Row, Segmented, Select, Space, Spin, Tag, Typography } from 'antd';
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import Feature from 'ol/Feature';
import Point from 'ol/geom/Point';
import OSM from 'ol/source/OSM';
import XYZ from 'ol/source/XYZ';
import 'ol/ol.css';
import { Circle as CircleStyle, Fill, Stroke, Style } from 'ol/style';
import { fromLonLat, transformExtent } from 'ol/proj';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getApiErrorMessage } from '../services/api';
import {
  fetchRoadContext, fetchVroadAsset, fetchVroadAssets, fetchVroadDefect, fetchVroadPoints, resolveVroadDefect, trustedSourceImage,
  type AssetMarker, type AssetPoint, type DefectPoint, type RoadCatalog, type RoadContext,
} from '../services/vroadApi';

const { Title, Text } = Typography;

const RoadCatalogCard = ({ road }: { road: RoadCatalog }) =>
  <Card size="small" title="Danh mục toàn tuyến">
    <Space direction="vertical" size="small">
      <Text>{road.name}</Text>
      {road.lengthKm && <Text>Chiều dài tuyến: {road.lengthKm} km</Text>}
      {road.startChainage && <Text>Lý trình đầu: {road.startChainage}</Text>}
      {road.endChainage && <Text>Lý trình cuối: {road.endChainage}</Text>}
    </Space>
  </Card>;

const VROAD_BASEMAP_OPTIONS = [
  {
    key: 'google_road',
    label: '🗺️ Google Bản đồ',
    createSource: () =>
      new XYZ({
        urls: [
          'https://mt0.google.com/vt/lyrs=m&x={x}&y={y}&z={z}',
          'https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}',
          'https://mt2.google.com/vt/lyrs=m&x={x}&y={y}&z={z}',
          'https://mt3.google.com/vt/lyrs=m&x={x}&y={y}&z={z}',
        ],
        attributions: '© Google Maps',
        maxZoom: 20,
      }),
  },
  {
    key: 'google_hybrid',
    label: '🛰️ Google Vệ tinh',
    createSource: () =>
      new XYZ({
        urls: [
          'https://mt0.google.com/vt/lyrs=y&x={x}&y={y}&z={z}',
          'https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}',
          'https://mt2.google.com/vt/lyrs=y&x={x}&y={y}&z={z}',
          'https://mt3.google.com/vt/lyrs=y&x={x}&y={y}&z={z}',
        ],
        attributions: '© Google Maps',
        maxZoom: 20,
      }),
  },
  {
    key: 'carto_voyager',
    label: '🚗 Carto Giao thông',
    createSource: () =>
      new XYZ({
        url: 'https://{a-c}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png',
        attributions: '© CARTO, © OpenStreetMap',
        maxZoom: 19,
      }),
  },
  {
    key: 'satellite',
    label: '🌍 Esri Vệ tinh',
    createSource: () =>
      new XYZ({
        url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        attributions: '© Esri, Maxar, Earthstar Geographics',
        maxZoom: 19,
      }),
  },
  {
    key: 'osm',
    label: '🌐 OpenStreetMap',
    createSource: () => new OSM(),
  },
];

export const VroadSurveyMapPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const assetRecordId = searchParams.get('assetRecordId');
  const focusedDefectSource = useRef(new VectorSource());
  const defectRecordId = searchParams.get('defectRecordId');
  const root = useRef<HTMLDivElement>(null);
  const map = useRef<Map | null>(null);
  const baseLayerRef = useRef<TileLayer<OSM | XYZ> | null>(null);
  const [basemapKey, setBasemapKey] = useState<string>('google_road');
  const pointsSource = useRef<VectorSource>(new VectorSource());
  const visibleAssetSource = useRef<VectorSource>(new VectorSource());
  const defectLayer = useRef<VectorLayer<VectorSource> | null>(null);
  const assetLayer = useRef<VectorLayer<VectorSource> | null>(null);
  const selectedAssetLayer = useRef<VectorLayer<VectorSource> | null>(null);
  const assetSource = useRef<VectorSource>(new VectorSource());
  const assetRef = useRef<AssetPoint | null>(null);
  const assetsRef = useRef<AssetMarker[]>([]);
  const pointsRef = useRef<DefectPoint[]>([]);
  const clickSequence = useRef(0);
  const loadSequence = useRef(0);
  const [points, setPoints] = useState<DefectPoint[]>([]);
  const [assets, setAssets] = useState<AssetMarker[]>([]);
  const [selected, setSelected] = useState<RoadContext | null>(null);
  const [selectedAsset, setSelectedAsset] = useState<AssetPoint | null>(null);
  const [showRelatedDefects, setShowRelatedDefects] = useState(false);
  const [loadingRelated, setLoadingRelated] = useState(false);
  const [selectedDefect, setSelectedDefect] = useState<DefectPoint | null>(null);
  const [zoomed, setZoomed] = useState<DefectPoint | null>(null);
  const [assetZoomed, setAssetZoomed] = useState(false);
  const [lookup, setLookup] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [truncated, setTruncated] = useState(false);
  const [assetsTruncated, setAssetsTruncated] = useState(false);
  const [mapLayer, setMapLayer] = useState<'all' | 'defects' | 'assets'>('all');
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState('');
  const [image, setImage] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);

  const highlightDefect = useCallback((point: DefectPoint) => {
    const marker = new Feature({ geometry: new Point(fromLonLat([point.longitude, point.latitude])) });
    marker.setId(point.recordId);
    marker.set('focusedDefect', point);
    focusedDefectSource.current.clear();
    focusedDefectSource.current.addFeature(marker);
  }, []);

  const openContext = async (longitude: number, latitude: number, recordId?: number) => {
    focusedDefectSource.current.clear();
    const id = ++clickSequence.current;
    setSelected(null);
    setSelectedAsset(null);
    setShowRelatedDefects(false);
    setLoadingRelated(false);
    setAssetZoomed(false);
    setZoomed(null);
    setIsLoading(true);
    try {
      const context = await fetchRoadContext(longitude, latitude, recordId);
      if (id === clickSequence.current) {
        setSelected(context);
        if (context.defects[0]) highlightDefect(context.defects[0]);
        setError(null);
      }
    } catch (cause) {
      if (id === clickSequence.current) setError(getApiErrorMessage(cause));
    } finally {
      if (id === clickSequence.current) setIsLoading(false);
    }
  };
  const openContextRef = useRef(openContext);
  openContextRef.current = openContext;

  const openAsset = async (recordId: number) => {
    focusedDefectSource.current.clear();
    const id = ++clickSequence.current;
    setSelected(null);
    setSelectedAsset(null);
    setShowRelatedDefects(false);
    setLoadingRelated(false);
    setAssetZoomed(false);
    setZoomed(null);
    setIsLoading(true);
    try {
      const asset = await fetchVroadAsset(recordId);
      if (id === clickSequence.current) { setSelectedAsset(asset); setError(null); }
    } catch (cause) {
      if (id === clickSequence.current) setError(getApiErrorMessage(cause));
    } finally {
      if (id === clickSequence.current) setIsLoading(false);
    }
  };
  const openAssetRef = useRef(openAsset);
  openAssetRef.current = openAsset;

  const openRelated = async () => {
    if (!selectedAsset || loadingRelated) return;
    const requestId = clickSequence.current;
    setLoadingRelated(true);
    try {
      const asset = await fetchVroadAsset(selectedAsset.recordId, true);
      if (requestId === clickSequence.current) {
        setSelectedAsset(asset);
        setShowRelatedDefects(true);
        setError(null);
      }
    } catch (cause) {
      if (requestId === clickSequence.current) setError(getApiErrorMessage(cause));
    } finally {
      if (requestId === clickSequence.current) setLoadingRelated(false);
    }
  };

  const updatePoints = useCallback(async (currentMap: Map) => {
    const id = ++loadSequence.current;
    const extent = currentMap.getView().calculateExtent(currentMap.getSize() || [1100, 580]);
    const bounds = transformExtent(extent, 'EPSG:3857', 'EPSG:4326');
    const bbox: [number, number, number, number] = [
      Math.max(-180, bounds[0]), Math.max(-90, bounds[1]),
      Math.min(180, bounds[2]), Math.min(90, bounds[3]),
    ];
    if (bbox[2] - bbox[0] > 30 || bbox[3] - bbox[1] > 30) return;
    try {
      const [page, assetPage] = await Promise.all([fetchVroadPoints(bbox), fetchVroadAssets(bbox)]);
      if (id !== loadSequence.current) return;
      const features = page.content.map((defect) => {
        const feature = new Feature({ geometry: new Point(fromLonLat([defect.longitude, defect.latitude])) });
        feature.setId(defect.recordId);
        return feature;
      });
      pointsSource.current.clear();
      pointsSource.current.addFeatures(features);
      const assetFeatures = assetPage.content.map((asset) => {
        const feature = new Feature({ geometry: new Point(fromLonLat([asset.longitude, asset.latitude])) });
        feature.set('assetRecordId', asset.recordId);
        return feature;
      });
      visibleAssetSource.current.clear();
      visibleAssetSource.current.addFeatures(assetFeatures);
      pointsRef.current = page.content;
      assetsRef.current = assetPage.content;
      setPoints(page.content);
      setAssets(assetPage.content);
      setTruncated(page.truncated);
      setAssetsTruncated(assetPage.truncated);
      setError(null);
    } catch (cause) {
      if (id === loadSequence.current) setError(getApiErrorMessage(cause));
    }
  }, []);

  useEffect(() => {
    if (!root.current || map.current) return;
    const clickRequest = clickSequence;
    const loadRequest = loadSequence;
    const pointSource = pointsSource.current;
    const assetMarkersSource = visibleAssetSource.current;
    const selectedAssetSource = assetSource.current;
    const visibleAssets = new VectorLayer({
      source: assetMarkersSource,
      style: new Style({ image: new CircleStyle({ radius: 6, fill: new Fill({ color: '#1763c5' }),
        stroke: new Stroke({ color: 'white', width: 2 }) }) }),
    });
    const visibleDefects = new VectorLayer({
      source: pointSource,
      style: new Style({ image: new CircleStyle({ radius: 7, fill: new Fill({ color: '#e5484d' }),
        stroke: new Stroke({ color: 'white', width: 2 }) }) }),
    });
    assetLayer.current = visibleAssets;
    defectLayer.current = visibleDefects;
    const highlightedAsset = new VectorLayer({
      source: selectedAssetSource,
      style: new Style({ image: new CircleStyle({ radius: 10, fill: new Fill({ color: '#1763c5' }),
        stroke: new Stroke({ color: 'white', width: 3 }) }) }),
    });
    selectedAssetLayer.current = highlightedAsset;
    const focusedDefects = new VectorLayer({
      source: focusedDefectSource.current,
      style: new Style({ image: new CircleStyle({ radius: 11, fill: new Fill({ color: '#cf1322' }),
        stroke: new Stroke({ color: '#fff1b8', width: 4 }) }) }),
    });
    const initialBasemap = VROAD_BASEMAP_OPTIONS.find((b) => b.key === basemapKey) || VROAD_BASEMAP_OPTIONS[0];
    const baseLayer = new TileLayer({ source: initialBasemap.createSource() });
    baseLayerRef.current = baseLayer;
    const instance = new Map({
      target: root.current,
      layers: [baseLayer, visibleAssets, visibleDefects, highlightedAsset, focusedDefects],
      view: new View({ center: fromLonLat([109.2, 13.5]), zoom: 10, minZoom: 5, maxZoom: 20 }),
    });
    map.current = instance;
    const resizeObserver = new ResizeObserver(() => {
      instance.updateSize();
    });
    if (root.current) resizeObserver.observe(root.current);
    instance.on('moveend', () => { void updatePoints(instance); });
    instance.on('singleclick', (event) => {
      const hit = instance.forEachFeatureAtPixel(event.pixel, (feature) => feature);
      if (assetRef.current && hit?.get('assetRecordId') === assetRef.current.recordId) {
        ++clickRequest.current;
        setSelected(null);
        setSelectedAsset(assetRef.current);
        setShowRelatedDefects(false);
        setLoadingRelated(false);
        setZoomed(null);
        setError(null);
        setIsLoading(false);
        return;
      }
      const focusedDefect = hit?.get('focusedDefect') as DefectPoint | undefined;
      if (focusedDefect) {
        void openContextRef.current(focusedDefect.longitude, focusedDefect.latitude, focusedDefect.recordId);
        return;
      }
      const assetRecord = hit?.get('assetRecordId');
      if (typeof assetRecord === 'number') {
        void openAssetRef.current(assetRecord);
        return;
      }
      const defect = pointsRef.current.find((point) => point.recordId === hit?.getId());
      if (defect) {
        void openContextRef.current(defect.longitude, defect.latitude, defect.recordId);
      } else {
        ++clickRequest.current;
        focusedDefects.getSource()?.clear();
        setSelected(null);
        setSelectedAsset(null);
        setShowRelatedDefects(false);
        setLoadingRelated(false);
        setAssetZoomed(false);
        setZoomed(null);
        setIsLoading(false);
        setError(null);
      }
    });
    const initial = window.setTimeout(() => { void updatePoints(instance); }, 250);
    return () => {
      ++clickRequest.current;
      ++loadRequest.current;
      window.clearTimeout(initial);
      resizeObserver.disconnect();
      instance.setTarget(undefined);
      map.current = null;
      baseLayerRef.current = null;
      pointSource.clear();
      assetMarkersSource.clear();
      selectedAssetSource.clear();
      focusedDefects.getSource()?.clear();
      assetLayer.current = null;
      defectLayer.current = null;
      selectedAssetLayer.current = null;
    };
  }, [updatePoints]);

  useEffect(() => {
    if (!baseLayerRef.current) return;
    const option = VROAD_BASEMAP_OPTIONS.find((b) => b.key === basemapKey) || VROAD_BASEMAP_OPTIONS[0];
    baseLayerRef.current.setSource(option.createSource());
  }, [basemapKey]);

  useEffect(() => {
    assetLayer.current?.setVisible(mapLayer !== 'defects');
    defectLayer.current?.setVisible(mapLayer !== 'assets');
    selectedAssetLayer.current?.setVisible(mapLayer !== 'defects');
  }, [mapLayer]);

  useEffect(() => {
    const requestId = ++clickSequence.current;
    assetSource.current.clear();
    focusedDefectSource.current.clear();
    assetRef.current = null;
    setSelectedAsset(null);
    setShowRelatedDefects(false);
    setLoadingRelated(false);
    setAssetZoomed(false);
    if (!assetRecordId) {
      setIsLoading(false);
      return;
    }
    setSelected(null);
    setZoomed(null);
    if (!/^[1-9]\d*$/.test(assetRecordId) || !Number.isSafeInteger(Number(assetRecordId))) {
      setIsLoading(false);
      setError('Mã tài sản không hợp lệ.');
      return;
    }
    let active = true;
    setError(null);
    setIsLoading(true);
    void fetchVroadAsset(Number(assetRecordId)).then((asset) => {
      if (!active || requestId !== clickSequence.current) return;
      assetRef.current = asset;
      const marker = new Feature({ geometry: new Point(fromLonLat([asset.longitude, asset.latitude])) });
      marker.set('assetRecordId', asset.recordId);
      assetSource.current.addFeature(marker);
      map.current?.getView().animate({ center: fromLonLat([asset.longitude, asset.latitude]), zoom: 16, duration: 350 });
      setSelectedAsset(asset);
    }).catch((cause) => {
      if (active && requestId === clickSequence.current) setError(getApiErrorMessage(cause));
    }).finally(() => {
      if (active && requestId === clickSequence.current) setIsLoading(false);
    });
    return () => { active = false; };
  }, [assetRecordId]);

  useEffect(() => {
    focusedDefectSource.current.clear();
    if (!defectRecordId || assetRecordId) {
      if (!assetRecordId) setIsLoading(false);
      return;
    }
    const requestId = ++clickSequence.current;
    if (!/^[1-9]\d*$/.test(defectRecordId) || !Number.isSafeInteger(Number(defectRecordId))) {
      setError('Mã hư hỏng không hợp lệ.');
      setIsLoading(false);
      return;
    }
    let active = true;
    setIsLoading(true);
    setError(null);
    void fetchVroadDefect(Number(defectRecordId)).then(async (point) => {
      const context = await fetchRoadContext(point.longitude, point.latitude, point.recordId);
      if (!active || requestId !== clickSequence.current) return;
      setMapLayer('defects');
      map.current?.getView().animate({ center: fromLonLat([point.longitude, point.latitude]), zoom: 17, duration: 350 });
      highlightDefect(point);
      setSelected(context);
      setZoomed(null);
    }).catch((cause) => {
      if (active && requestId === clickSequence.current) setError(getApiErrorMessage(cause));
    }).finally(() => { if (active && requestId === clickSequence.current) setIsLoading(false); });
    return () => { active = false; };
  }, [defectRecordId, assetRecordId, highlightDefect]);

  const saveResolution = async () => {
    if (!selectedDefect || note.trim().length < 5) {
      setError('Nhập nội dung xử lý ít nhất 5 ký tự.');
      return;
    }
    if (image && (image.size > 5 * 1024 * 1024 || !['image/png', 'image/jpeg', 'image/webp'].includes(image.type))) {
      setError('Ảnh minh chứng phải là JPEG, PNG hoặc WebP và không quá 5 MiB.');
      return;
    }
    setSaving(true);
    try {
      await resolveVroadDefect(selectedDefect.recordId, note.trim(), image);
      const context = await fetchRoadContext(selectedDefect.longitude,
        selectedDefect.latitude, selectedDefect.recordId);
      setSelected(context);
      setSelectedDefect(null);
      setImage(null);
      setNote('');
      setError(null);
    } catch (cause) {
      setError(getApiErrorMessage(cause));
    } finally {
      setSaving(false);
    }
  };

  const selectPoint = (point: DefectPoint) => {
    map.current?.getView().animate({ center: fromLonLat([point.longitude, point.latitude]), zoom: 16, duration: 350 });
    void openContext(point.longitude, point.latitude, point.recordId);
  };

  const selectedPoint = selected?.defects[0];
  const assetImageUrl = trustedSourceImage(selectedAsset?.sourceImageUrl || null);
  const relatedDefects = selectedAsset?.relatedDefects || [];

  return <Space direction="vertical" size="large" style={{ width: '100%' }}>
    <div>
      <Title level={2} style={{ marginBottom: 2 }}>Bản đồ hư hỏng VroadAI</Title>
      <Text type="secondary">Chọn điểm đỏ (hư hỏng) hoặc xanh (tài sản) để xem thông tin và ảnh của đúng điểm đó.</Text>
    </div>
    {user?.role === 'ROLE_ADMIN' && <Space><Button onClick={() => navigate('/map/assets')}>Bản đồ tài sản cũ</Button><Button onClick={() => navigate('/cases')}>Hồ sơ đã xử lý</Button></Space>}
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={15}>
        <Card title="Điểm khảo sát KCHT Đường bộ" extra={<Space wrap><Tag color="red">{points.length} hư hỏng</Tag>
          <Tag color="blue">{assets.length} tài sản</Tag>
          {(truncated || assetsTruncated) && <Tag color="orange">Giới hạn 500 điểm mỗi loại — phóng to để xem thêm</Tag>}</Space>}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
            <Segmented aria-label="Lớp bản đồ" value={mapLayer}
              options={[{ label: 'Tất cả', value: 'all' }, { label: 'Hư hỏng', value: 'defects' },
                { label: 'Tài sản', value: 'assets' }]}
              onChange={(value) => {
                setMapLayer(value as 'all' | 'defects' | 'assets');
                ++clickSequence.current;
                setSelected(null);
                setSelectedAsset(null);
                setShowRelatedDefects(false);
                setLoadingRelated(false);
                setZoomed(null);
                setAssetZoomed(false);
                setError(null);
                setIsLoading(false);
              }} />
            <Space size="small">
              <Text type="secondary" style={{ fontSize: 13 }}>Nền bản đồ:</Text>
              <Select
                aria-label="Chọn nền bản đồ"
                value={basemapKey}
                onChange={setBasemapKey}
                style={{ width: 180 }}
                options={VROAD_BASEMAP_OPTIONS.map((opt) => ({
                  value: opt.key,
                  label: opt.label,
                }))}
              />
            </Space>
          </div>
          <div ref={root} data-testid="vroad-map" style={{ height: 570, width: '100%', background: '#edf2f6' }} />
          <Input.Search aria-label="Tìm điểm trong khung bản đồ" placeholder="Tìm mã điểm hoặc tài sản trong khung bản đồ"
            value={lookup} onChange={(event) => setLookup(event.target.value)} enterButton="Xem điểm"
            onSearch={(value) => {
              const found = mapLayer === 'assets' ? undefined : pointsRef.current.find((point) =>
                point.recordKey.toLowerCase() === value.trim().toLowerCase());
              if (found) selectPoint(found);
              else {
                const asset = mapLayer === 'defects' ? undefined : assetsRef.current.find((point) =>
                  point.recordKey.toLowerCase() === value.trim().toLowerCase());
                if (asset) {
                  map.current?.getView().animate({ center: fromLonLat([asset.longitude, asset.latitude]),
                    zoom: 16, duration: 350 });
                  void openAsset(asset.recordId);
                } else setError('Không tìm thấy mã trong khung bản đồ. Hãy di chuyển bản đồ hoặc phóng to.');
              }
            }} style={{ marginTop: 12, maxWidth: 420 }} />
        </Card>
      </Col>
      <Col xs={24} lg={9}>
        <Card title="Thông tin tại tọa độ">
          {isLoading && <Spin />}
          {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
          {!selected && !selectedAsset && !isLoading && <Text type="secondary">Chọn một điểm đánh dấu trên bản đồ để xem đúng đoạn khảo sát và ảnh hư hỏng.</Text>}
          {selectedAsset && <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Text>Tọa độ: {selectedAsset.latitude.toFixed(6)}, {selectedAsset.longitude.toFixed(6)} (WGS84)</Text>
            <Text strong>Tuyến: {selectedAsset.routeName || 'Chưa rõ'}</Text>
            <Text>Lý trình: {selectedAsset.chainage || 'Chưa rõ'} · Chiều tuyến: {selectedAsset.routeSide || 'Chưa rõ'}</Text>
            <Card size="small" title={selectedAsset.assetType || selectedAsset.recordKey}>
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Text>Mã: {selectedAsset.recordKey}</Text>
                <Text>Nhóm tài sản: {selectedAsset.category || 'Chưa phân loại'}</Text>
                <Text>Tình trạng: {selectedAsset.condition || 'Chưa rõ'}</Text>
                <Text>Phía tài sản: {selectedAsset.assetSide || 'Chưa rõ'}</Text>
                <Text>Ngày khảo sát: {selectedAsset.surveyDate || 'Chưa rõ'}</Text>
                {selectedAsset.roadCatalog && <RoadCatalogCard road={selectedAsset.roadCatalog} />}
                {assetImageUrl ? <>
                  <div role="button" tabIndex={0} aria-label={`Phóng to ảnh tài sản ${selectedAsset.recordKey}`}
                    onClick={() => setAssetZoomed(true)}
                    onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault(); setAssetZoomed(true);
                    } }} style={{ position: 'relative', cursor: 'zoom-in', width: '100%' }}>
                    <iframe title={`Ảnh tài sản ${selectedAsset.recordKey}`} src={assetImageUrl}
                      sandbox="allow-scripts allow-same-origin" referrerPolicy="no-referrer" loading="lazy" tabIndex={-1}
                      style={{ width: '100%', height: 240, border: '1px solid #ddd', pointerEvents: 'none' }} />
                    <Tag style={{ position: 'absolute', bottom: 10, right: 8 }}>Nhấn để phóng to</Tag>
                  </div>
                  <a href={assetImageUrl} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">Mở ảnh nguồn</a>
                </> : <Text type="secondary">Chưa có ảnh tài sản.</Text>}
                <Button size="small" onClick={() => navigate('/assets?datasetKey=vroad_assets')}>Xem danh sách tài sản</Button>
                <Button size="small" onClick={() =>
                  navigate(`/documents?asset=${encodeURIComponent(selectedAsset.recordKey)}`)}>
                  Tra cứu hồ sơ tài sản
                </Button>
                {!showRelatedDefects && <Button size="small" loading={loadingRelated} onClick={() => void openRelated()}>
                  Xem hư hỏng gần tài sản
                </Button>}
              </Space>
            </Card>
            {showRelatedDefects && <Card size="small" title="Hư hỏng ghi nhận gần tài sản"
              extra={<Button size="small" onClick={() => setShowRelatedDefects(false)}>Thu gọn</Button>}>
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                {relatedDefects.length === 0 && <Text type="secondary">Chưa ghi nhận hư hỏng trùng tuyến, lý trình và phía tài sản trong phạm vi khảo sát.</Text>}
                {selectedAsset.relatedDefectsTruncated && <Text type="secondary">
                  Hiển thị 30 điểm gần nhất.
                </Text>}
                {relatedDefects.map((defect) => {
                  const defectImageUrl = trustedSourceImage(defect.sourceImageUrl);
                  return <Card key={defect.recordId} size="small" title={defect.defectType || defect.recordKey}>
                    <Space direction="vertical" size="small" style={{ width: '100%' }}>
                      <Text>Mã hư hỏng: {defect.recordKey} · Phía: {defect.defectSide || 'Chưa rõ'}</Text>
                      <Text>Ngày khảo sát: {defect.surveyDate || 'Chưa rõ'}</Text>
                      <Tag color={defect.resolvedAt ? 'green' : 'red'}>
                        {defect.resolvedAt ? 'Đã xử lý xong' : 'Chưa có hồ sơ xử lý'}
                      </Tag>
                      {defectImageUrl ? <>
                        <div role="button" tabIndex={0} aria-label={`Phóng to ảnh hư hỏng ${defect.recordKey}`}
                          onClick={() => setZoomed(defect)}
                          onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault(); setZoomed(defect);
                          } }} style={{ cursor: 'zoom-in', width: '100%' }}>
                          <iframe title={`Ảnh khảo sát ${defect.recordKey}`} src={defectImageUrl}
                            sandbox="allow-scripts allow-same-origin" referrerPolicy="no-referrer"
                            loading="lazy" tabIndex={-1} style={{ width: '100%', height: 240,
                              border: '1px solid #ddd', pointerEvents: 'none' }} />
                        </div>
                        <a href={defectImageUrl} target="_blank" rel="noopener noreferrer"
                          referrerPolicy="no-referrer">Mở ảnh nguồn</a>
                      </> : <Text type="secondary">Chưa có ảnh hư hỏng.</Text>}
                      <Button size="small" onClick={() => selectPoint(defect)}>Xem chi tiết điểm hư hỏng</Button>
                    </Space>
                  </Card>;
                })}
              </Space>
            </Card>}
          </Space>}
          {selected && <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Text>Tọa độ: {selected.latitude.toFixed(6)}, {selected.longitude.toFixed(6)} (WGS84)</Text>
            {selectedPoint ? <>
              <Text strong>Tuyến: {selectedPoint.routeName || 'Chưa rõ'}</Text>
              <Text>Lý trình: {selectedPoint.chainage || 'Chưa rõ'} · Chiều tuyến: {selectedPoint.routeSide || 'Chưa rõ'}</Text>
              <Text>Tài sản khảo sát lân cận: {selected.nearbyAssets}</Text>
              {selected.roadCatalog && <RoadCatalogCard road={selected.roadCatalog} />}
              <Card size="small" title={selectedPoint.defectType || selectedPoint.recordKey}>
                <Space direction="vertical" size="small">
                  <Text>Mã: {selectedPoint.recordKey}; phía hư hỏng: {selectedPoint.defectSide || 'Chưa rõ'}</Text>
                  <Text>Ngày khảo sát: {selectedPoint.surveyDate || 'Chưa rõ'}</Text>
                  {selectedPoint.areaM2 && <Text>Diện tích: {selectedPoint.areaM2} m²</Text>}
                  <Tag color={selectedPoint.resolvedAt ? 'green' : 'red'}>{selectedPoint.resolvedAt ? 'Đã xử lý xong' : 'Chưa có hồ sơ xử lý'}</Tag>
                  {selectedPoint.resolvedBy && <Text>Người xác nhận: {selectedPoint.resolvedBy}</Text>}
                  {trustedSourceImage(selectedPoint.sourceImageUrl) ? <>
                    <div role="button" tabIndex={0} aria-label={`Phóng to ảnh hư hỏng ${selectedPoint.recordKey}`}
                      onClick={() => setZoomed(selectedPoint)}
                      onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setZoomed(selectedPoint); } }}
                      style={{ position: 'relative', cursor: 'zoom-in', width: '100%' }}>
                      <iframe
                      title={`Ảnh khảo sát ${selectedPoint.recordKey}`} src={trustedSourceImage(selectedPoint.sourceImageUrl)!}
                      sandbox="allow-scripts allow-same-origin" referrerPolicy="no-referrer" loading="lazy"
                      tabIndex={-1} style={{ width: '100%', height: 240, border: '1px solid #ddd', pointerEvents: 'none' }} />
                      <Tag style={{ position: 'absolute', bottom: 10, right: 8 }}>Nhấn để phóng to</Tag>
                    </div>
                    <a href={trustedSourceImage(selectedPoint.sourceImageUrl)!} target="_blank"
                      rel="noopener noreferrer" referrerPolicy="no-referrer">Mở ảnh nguồn</a>
                  </> : <Text type="secondary">Chưa có ảnh nguồn</Text>}
                  {selectedPoint.hasEvidence && <Button size="small" onClick={() => navigate(`/cases?record=${selectedPoint.recordId}`)}>Xem ảnh minh chứng</Button>}
                  {(user?.role === 'ROLE_MANAGER' || user?.role === 'ROLE_ADMIN') && !selectedPoint.resolvedAt &&
                    <Button type="primary" size="small" onClick={() => { setError(null); setSelectedDefect(selectedPoint); }}>Đã xử lý xong</Button>}
                </Space>
              </Card>
            </> : <Alert type="warning" message="Không có khảo sát trong 200 m" />}
          </Space>}
        </Card>
      </Col>
    </Row>
    <Modal title={`Ảnh hư hỏng ${zoomed?.recordKey || ''}`} open={!!zoomed} footer={null}
      width="min(96vw, 1200px)" onCancel={() => setZoomed(null)}>
      {zoomed && trustedSourceImage(zoomed.sourceImageUrl) && <iframe title={`Ảnh phóng to ${zoomed.recordKey}`}
        src={trustedSourceImage(zoomed.sourceImageUrl)!} sandbox="allow-scripts allow-same-origin"
        referrerPolicy="no-referrer" style={{ width: '100%', height: '75vh', border: 0 }} />}
    </Modal>
    <Modal title={`Ảnh tài sản ${selectedAsset?.recordKey || ''}`} open={assetZoomed && !!assetImageUrl}
      footer={null} width="min(96vw, 1200px)" onCancel={() => setAssetZoomed(false)}>
      {assetImageUrl && <iframe title="Ảnh tài sản phóng to" src={assetImageUrl}
        sandbox="allow-scripts allow-same-origin" referrerPolicy="no-referrer"
        style={{ width: '100%', height: '75vh', border: 0 }} />}
    </Modal>
    <Modal title="Xác nhận đã xử lý hư hỏng" open={!!selectedDefect} onCancel={() => { setSelectedDefect(null); setError(null); }}
      onOk={() => void saveResolution()} okText="Lưu vào hồ sơ" okButtonProps={{ loading: saving }}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Text>Hư hỏng: {selectedDefect?.recordKey}. Hành động được lưu cùng tài khoản, thời điểm và không thể xác nhận trùng.</Text>
        <Input.TextArea aria-label="Nội dung xử lý" value={note} rows={4} maxLength={2000}
          onChange={(event) => setNote(event.target.value)} placeholder="Mô tả việc đã xử lý (bắt buộc)" />
        <label>Ảnh minh chứng (tùy chọn, JPEG/PNG/WebP tối đa 5 MiB)
          <input aria-label="Ảnh minh chứng" type="file" accept="image/jpeg,image/png,image/webp"
            onChange={(event) => setImage(event.target.files?.[0] || null)} />
        </label>
        {error && <Alert type="error" message={error} />}
      </Space>
    </Modal>
  </Space>;
};
