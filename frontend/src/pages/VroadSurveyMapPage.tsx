import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Col, Input, Modal, Row, Segmented, Select, Space, Spin, Tag, Typography, Statistic, Tooltip } from 'antd';
import {
  AimOutlined,
  CompassOutlined,
  EnvironmentOutlined,
  InfoCircleOutlined,
  PieChartOutlined,
  RightOutlined,
  ThunderboltOutlined,
  WarningOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
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
  fetchGreenTree, fetchGreenTrees, fetchRoadContext, fetchVroadAsset, fetchVroadAssets, fetchVroadDefect,
  fetchVroadPoints, resolveVroadDefect, trustedSourceImage,
  type AssetMarker, type AssetPoint, type DefectPoint, type GreenTreeMarker, type GreenTreePoint,
  type RoadCatalog, type RoadContext,
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
  const treeRecordId = searchParams.get('treeRecordId');
  const focusedDefectSource = useRef(new VectorSource());
  const defectRecordId = searchParams.get('defectRecordId');
  const root = useRef<HTMLDivElement>(null);
  const map = useRef<Map | null>(null);
  const baseLayerRef = useRef<TileLayer<OSM | XYZ> | null>(null);
  const [basemapKey, setBasemapKey] = useState<string>('google_road');
  const pointsSource = useRef<VectorSource>(new VectorSource());
  const visibleAssetSource = useRef<VectorSource>(new VectorSource());
  const visibleTreeSource = useRef<VectorSource>(new VectorSource());
  const defectLayer = useRef<VectorLayer<VectorSource> | null>(null);
  const assetLayer = useRef<VectorLayer<VectorSource> | null>(null);
  const treeLayer = useRef<VectorLayer<VectorSource> | null>(null);
  const selectedAssetLayer = useRef<VectorLayer<VectorSource> | null>(null);
  const selectedTreeLayer = useRef<VectorLayer<VectorSource> | null>(null);
  const assetSource = useRef<VectorSource>(new VectorSource());
  const treeSource = useRef<VectorSource>(new VectorSource());
  const assetRef = useRef<AssetPoint | null>(null);
  const treeRef = useRef<GreenTreePoint | null>(null);
  const assetsRef = useRef<AssetMarker[]>([]);
  const treesRef = useRef<GreenTreeMarker[]>([]);
  const pointsRef = useRef<DefectPoint[]>([]);
  const clickSequence = useRef(0);
  const loadSequence = useRef(0);
  const [points, setPoints] = useState<DefectPoint[]>([]);
  const [assets, setAssets] = useState<AssetMarker[]>([]);
  const [trees, setTrees] = useState<GreenTreeMarker[]>([]);
  const [selected, setSelected] = useState<RoadContext | null>(null);
  const [selectedAsset, setSelectedAsset] = useState<AssetPoint | null>(null);
  const [selectedTree, setSelectedTree] = useState<GreenTreePoint | null>(null);
  const [showRelatedDefects, setShowRelatedDefects] = useState(false);
  const [loadingRelated, setLoadingRelated] = useState(false);
  const [selectedDefect, setSelectedDefect] = useState<DefectPoint | null>(null);
  const [zoomed, setZoomed] = useState<DefectPoint | null>(null);
  const [assetZoomed, setAssetZoomed] = useState(false);
  const [treeZoomed, setTreeZoomed] = useState(false);
  const [lookup, setLookup] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [truncated, setTruncated] = useState(false);
  const [assetsTruncated, setAssetsTruncated] = useState(false);
  const [treesTruncated, setTreesTruncated] = useState(false);
  const [mapLayer, setMapLayer] = useState<'all' | 'defects' | 'assets' | 'trees'>('all');
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
    assetSource.current.clear();
    treeSource.current.clear();
    assetRef.current = null;
    treeRef.current = null;
    const id = ++clickSequence.current;
    setSelected(null);
    setSelectedAsset(null);
    setSelectedTree(null);
    setShowRelatedDefects(false);
    setLoadingRelated(false);
    setAssetZoomed(false);
    setTreeZoomed(false);
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
    treeSource.current.clear();
    treeRef.current = null;
    const id = ++clickSequence.current;
    setSelected(null);
    setSelectedAsset(null);
    setSelectedTree(null);
    setShowRelatedDefects(false);
    setLoadingRelated(false);
    setAssetZoomed(false);
    setTreeZoomed(false);
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

  const openTree = async (recordId: number) => {
    focusedDefectSource.current.clear();
    assetSource.current.clear();
    assetRef.current = null;
    const id = ++clickSequence.current;
    setSelected(null);
    setSelectedAsset(null);
    setSelectedTree(null);
    setShowRelatedDefects(false);
    setLoadingRelated(false);
    setAssetZoomed(false);
    setTreeZoomed(false);
    setZoomed(null);
    setIsLoading(true);
    try {
      const tree = await fetchGreenTree(recordId);
      if (id === clickSequence.current) {
        treeRef.current = tree;
        treeSource.current.clear();
        const marker = new Feature({ geometry: new Point(fromLonLat([tree.longitude, tree.latitude])) });
        marker.set('treeRecordId', tree.recordId);
        treeSource.current.addFeature(marker);
        setSelectedTree(tree);
        setError(null);
      }
    } catch (cause) {
      if (id === clickSequence.current) setError(getApiErrorMessage(cause));
    } finally {
      if (id === clickSequence.current) setIsLoading(false);
    }
  };
  const openTreeRef = useRef(openTree);
  openTreeRef.current = openTree;

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
      const [page, assetPage, treePage] = await Promise.all([
        fetchVroadPoints(bbox),
        fetchVroadAssets(bbox),
        fetchGreenTrees(bbox),
      ]);
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
      const treeFeatures = treePage.content.map((tree) => {
        const feature = new Feature({ geometry: new Point(fromLonLat([tree.longitude, tree.latitude])) });
        feature.set('treeRecordId', tree.recordId);
        return feature;
      });
      visibleTreeSource.current.clear();
      visibleTreeSource.current.addFeatures(treeFeatures);
      pointsRef.current = page.content;
      assetsRef.current = assetPage.content;
      treesRef.current = treePage.content;
      setPoints(page.content);
      setAssets(assetPage.content);
      setTrees(treePage.content);
      setTruncated(page.truncated);
      setAssetsTruncated(assetPage.truncated);
      setTreesTruncated(treePage.truncated);
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
    const treeMarkersSource = visibleTreeSource.current;
    const selectedAssetSource = assetSource.current;
    const selectedTreeSource = treeSource.current;
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
    const visibleTrees = new VectorLayer({
      source: treeMarkersSource,
      style: new Style({ image: new CircleStyle({ radius: 7, fill: new Fill({ color: '#2f9e44' }),
        stroke: new Stroke({ color: 'white', width: 2 }) }) }),
    });
    assetLayer.current = visibleAssets;
    defectLayer.current = visibleDefects;
    treeLayer.current = visibleTrees;
    const highlightedAsset = new VectorLayer({
      source: selectedAssetSource,
      style: new Style({ image: new CircleStyle({ radius: 10, fill: new Fill({ color: '#1763c5' }),
        stroke: new Stroke({ color: 'white', width: 3 }) }) }),
    });
    selectedAssetLayer.current = highlightedAsset;
    const highlightedTree = new VectorLayer({
      source: selectedTreeSource,
      style: new Style({ image: new CircleStyle({ radius: 11, fill: new Fill({ color: '#237804' }),
        stroke: new Stroke({ color: '#f6ffed', width: 4 }) }) }),
    });
    selectedTreeLayer.current = highlightedTree;
    const focusedDefects = new VectorLayer({
      source: focusedDefectSource.current,
      style: new Style({ image: new CircleStyle({ radius: 11, fill: new Fill({ color: '#cf1322' }),
        stroke: new Stroke({ color: '#fff1b8', width: 4 }) }) }),
    });
    const baseLayer = new TileLayer({ source: VROAD_BASEMAP_OPTIONS[0].createSource() });
    baseLayerRef.current = baseLayer;
    const instance = new Map({
      target: root.current,
      layers: [baseLayer, visibleAssets, visibleTrees, visibleDefects, highlightedAsset, highlightedTree, focusedDefects],
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
        setSelectedTree(null);
        setShowRelatedDefects(false);
        setLoadingRelated(false);
        setZoomed(null);
        setError(null);
        setIsLoading(false);
        return;
      }
      if (treeRef.current && hit?.get('treeRecordId') === treeRef.current.recordId) {
        ++clickRequest.current;
        setSelected(null);
        setSelectedAsset(null);
        setSelectedTree(treeRef.current);
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
      const treeRecord = hit?.get('treeRecordId');
      if (typeof treeRecord === 'number') {
        void openTreeRef.current(treeRecord);
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
        setSelectedTree(null);
        setShowRelatedDefects(false);
        setLoadingRelated(false);
        setAssetZoomed(false);
        setTreeZoomed(false);
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
      treeMarkersSource.clear();
      selectedAssetSource.clear();
      selectedTreeSource.clear();
      focusedDefects.getSource()?.clear();
      assetLayer.current = null;
      defectLayer.current = null;
      treeLayer.current = null;
      selectedAssetLayer.current = null;
      selectedTreeLayer.current = null;
    };
  }, [updatePoints]);

  useEffect(() => {
    if (!baseLayerRef.current) return;
    const option = VROAD_BASEMAP_OPTIONS.find((b) => b.key === basemapKey) || VROAD_BASEMAP_OPTIONS[0];
    baseLayerRef.current.setSource(option.createSource());
  }, [basemapKey]);

  useEffect(() => {
    assetLayer.current?.setVisible(mapLayer === 'all' || mapLayer === 'assets');
    defectLayer.current?.setVisible(mapLayer === 'all' || mapLayer === 'defects');
    treeLayer.current?.setVisible(mapLayer === 'all' || mapLayer === 'trees');
    selectedAssetLayer.current?.setVisible(mapLayer === 'all' || mapLayer === 'assets');
    selectedTreeLayer.current?.setVisible(mapLayer === 'all' || mapLayer === 'trees');
  }, [mapLayer]);

  useEffect(() => {
    const requestId = ++clickSequence.current;
    assetSource.current.clear();
    focusedDefectSource.current.clear();
    assetRef.current = null;
    setSelectedAsset(null);
    setSelectedTree(null);
    setShowRelatedDefects(false);
    setLoadingRelated(false);
    setAssetZoomed(false);
    setTreeZoomed(false);
    if (!assetRecordId) {
      if (!treeRecordId && !defectRecordId) setIsLoading(false);
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
  }, [assetRecordId, treeRecordId, defectRecordId]);

  useEffect(() => {
    treeSource.current.clear();
    treeRef.current = null;
    setSelectedTree(null);
    if (!treeRecordId || assetRecordId || defectRecordId) {
      if (!assetRecordId && !defectRecordId) setIsLoading(false);
      return;
    }
    if (!/^[1-9]\d*$/.test(treeRecordId) || !Number.isSafeInteger(Number(treeRecordId))) {
      setIsLoading(false);
      setError('Mã cây xanh không hợp lệ.');
      return;
    }
    const requestId = ++clickSequence.current;
    let active = true;
    setSelected(null);
    setSelectedAsset(null);
    setMapLayer('trees');
    setError(null);
    setIsLoading(true);
    void fetchGreenTree(Number(treeRecordId)).then((tree) => {
      if (!active || requestId !== clickSequence.current) return;
      treeRef.current = tree;
      const marker = new Feature({ geometry: new Point(fromLonLat([tree.longitude, tree.latitude])) });
      marker.set('treeRecordId', tree.recordId);
      treeSource.current.addFeature(marker);
      map.current?.getView().animate({ center: fromLonLat([tree.longitude, tree.latitude]), zoom: 17, duration: 350 });
      setSelectedTree(tree);
    }).catch((cause) => {
      if (active && requestId === clickSequence.current) setError(getApiErrorMessage(cause));
    }).finally(() => {
      if (active && requestId === clickSequence.current) setIsLoading(false);
    });
    return () => { active = false; };
  }, [treeRecordId, assetRecordId, defectRecordId]);

  useEffect(() => {
    focusedDefectSource.current.clear();
    if (!defectRecordId || assetRecordId || treeRecordId) {
      if (!assetRecordId && !treeRecordId) setIsLoading(false);
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
  }, [defectRecordId, assetRecordId, treeRecordId, highlightDefect]);

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
  const treeImageUrl = trustedSourceImage(selectedTree?.sourceImageUrl || null);
  const relatedDefects = selectedAsset?.relatedDefects || [];

  const resetMapView = () => {
    map.current?.getView().animate({
      center: fromLonLat([109.18, 13.55]),
      zoom: 9,
      duration: 500,
    });
  };

  const defectSummary = points.reduce<{ type: string; count: number }[]>((acc, point) => {
    const type = point.defectType || 'Hư hỏng khác';
    const found = acc.find((item) => item.type === type);
    if (found) {
      found.count += 1;
    } else {
      acc.push({ type, count: 1 });
    }
    return acc;
  }, []).sort((a, b) => b.count - a.count).slice(0, 5);

  return <Space direction="vertical" size="large" style={{ width: '100%' }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
      <div>
        <Space align="center" style={{ marginBottom: 4 }} wrap>
          <Title level={2} style={{ margin: 0 }}>Bản đồ hư hỏng VroadAI</Title>
          <Tag color="geekblue" style={{ borderRadius: 10, padding: '2px 10px', fontSize: 12 }}>WebGIS Trực quan hóa AI</Tag>
        </Space>
        <Text type="secondary" style={{ display: 'block', fontSize: 13 }}>
          Chọn điểm đỏ (hư hỏng), xanh dương (tài sản) hoặc xanh lá (cây xanh) để xem thông tin và ảnh.
        </Text>
      </div>
      {user?.role === 'ROLE_ADMIN' && <Space wrap>
        <Button icon={<CompassOutlined />} onClick={() => navigate('/map/assets')}>Bản đồ tài sản cũ</Button>
        <Button icon={<CheckCircleOutlined />} onClick={() => navigate('/cases')}>Hồ sơ đã xử lý</Button>
      </Space>}
    </div>

    {/* Thẻ chỉ số tổng quan nhanh */}
    <Row gutter={[16, 16]}>
      <Col xs={12} sm={6}>
        <Card size="small" style={{ borderRadius: 10, background: '#fff', border: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
          <Statistic
            title={<Text type="secondary" style={{ fontSize: 12 }}>Hư hỏng phát hiện</Text>}
            value={points.length}
            prefix={<WarningOutlined style={{ color: '#ff4d4f' }} />}
            suffix={<Text type="secondary" style={{ fontSize: 12 }}>vị trí</Text>}
            valueStyle={{ fontSize: 20, fontWeight: 600, color: '#cf1322' }}
          />
        </Card>
      </Col>
      <Col xs={12} sm={6}>
        <Card size="small" style={{ borderRadius: 10, background: '#fff', border: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
          <Statistic
            title={<Text type="secondary" style={{ fontSize: 12 }}>Tài sản khảo sát</Text>}
            value={assets.length}
            prefix={<EnvironmentOutlined style={{ color: '#1677ff' }} />}
            suffix={<Text type="secondary" style={{ fontSize: 12 }}>điểm</Text>}
            valueStyle={{ fontSize: 20, fontWeight: 600, color: '#0958d9' }}
          />
        </Card>
      </Col>
      <Col xs={12} sm={6}>
        <Card size="small" style={{ borderRadius: 10, background: '#fff', border: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
          <Statistic
            title={<Text type="secondary" style={{ fontSize: 12 }}>Cây xanh</Text>}
            value={trees.length}
            prefix={<CompassOutlined style={{ color: '#52c41a' }} />}
            suffix={<Text type="secondary" style={{ fontSize: 12 }}>điểm</Text>}
            valueStyle={{ fontSize: 20, fontWeight: 600, color: '#237804' }}
          />
        </Card>
      </Col>
      <Col xs={12} sm={6}>
        <Card size="small" style={{ borderRadius: 10, background: '#fff', border: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
          <Statistic
            title={<Text type="secondary" style={{ fontSize: 12 }}>Trạng thái tải dữ liệu</Text>}
            value={truncated || assetsTruncated ? 'Giới hạn 500' : 'Đầy đủ'}
            prefix={<ThunderboltOutlined style={{ color: '#fa8c16' }} />}
            suffix={<Text type="secondary" style={{ fontSize: 12 }}>điểm/lớp</Text>}
            valueStyle={{ fontSize: 18, fontWeight: 600, color: '#d46b08' }}
          />
        </Card>
      </Col>
    </Row>

    <Row gutter={[16, 16]}>
      <Col xs={24} lg={15}>
        <Card
          title={
            <Space>
              <CompassOutlined style={{ color: '#1677ff', fontSize: 16 }} />
              <span style={{ fontWeight: 600 }}>Điểm khảo sát KCHT Đường bộ</span>
            </Space>
          }
          extra={
            <Space wrap>
              <Tag color="red" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 500 }}>
                {points.length} hư hỏng
              </Tag>
              <Tag color="blue" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 500 }}>
                {assets.length} tài sản
              </Tag>
              <Tag color="green" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 500 }}>
                {trees.length} cây xanh
              </Tag>
              {(truncated || assetsTruncated || treesTruncated) && (
                <Tag color="orange" style={{ borderRadius: 12, padding: '2px 10px' }}>
                  Giới hạn 500 điểm mỗi loại — phóng to để xem thêm
                </Tag>
              )}
            </Space>
          }
          style={{ borderRadius: 10, border: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginBottom: 12 }}>
            <Segmented
              aria-label="Lớp bản đồ"
              value={mapLayer}
              options={[
                { label: 'Tất cả', value: 'all' },
                { label: 'Hư hỏng', value: 'defects' },
                { label: 'Tài sản', value: 'assets' },
                { label: 'Cây xanh', value: 'trees' },
              ]}
              onChange={(value) => {
                setMapLayer(value as 'all' | 'defects' | 'assets' | 'trees');
                ++clickSequence.current;
                setSelected(null);
                setSelectedAsset(null);
                setSelectedTree(null);
                setShowRelatedDefects(false);
                setLoadingRelated(false);
                setZoomed(null);
                setAssetZoomed(false);
                setTreeZoomed(false);
                setError(null);
                setIsLoading(false);
              }}
            />
            <Space size="small" wrap>
              <Text type="secondary" style={{ fontSize: 13 }}>Nền bản đồ:</Text>
              <Select
                aria-label="Chọn nền bản đồ"
                value={basemapKey}
                onChange={setBasemapKey}
                style={{ width: 175 }}
                options={VROAD_BASEMAP_OPTIONS.map((opt) => ({
                  value: opt.key,
                  label: opt.label,
                }))}
              />
              <Tooltip title="Khôi phục toàn cảnh tuyến đường khảo sát">
                <Button icon={<AimOutlined />} onClick={resetMapView}>Toàn cảnh</Button>
              </Tooltip>
            </Space>
          </div>

          <div style={{ position: 'relative', borderRadius: 8, overflow: 'hidden', border: '1px solid #e2e8f0' }}>
            <div ref={root} data-testid="vroad-map" style={{ height: 600, width: '100%', background: '#edf2f6' }} />

            {/* Bảng chú giải Map Legend */}
            <div style={{
              position: 'absolute',
              bottom: 12,
              left: 12,
              background: 'rgba(255, 255, 255, 0.94)',
              backdropFilter: 'blur(8px)',
              padding: '6px 12px',
              borderRadius: 6,
              boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
              border: '1px solid rgba(0,0,0,0.06)',
              display: 'flex',
              gap: 14,
              alignItems: 'center',
              fontSize: 12,
              zIndex: 10,
            }}>
              <Space size={6}>
                <span style={{ display: 'inline-block', width: 9, height: 9, borderRadius: '50%', background: '#ff4d4f' }} />
                <span>Hư hỏng VroadAI</span>
              </Space>
              <Space size={6}>
                <span style={{ display: 'inline-block', width: 9, height: 9, borderRadius: '50%', background: '#1677ff' }} />
                <span>Tài sản KCHT</span>
              </Space>
              <Space size={6}>
                <span style={{ display: 'inline-block', width: 9, height: 9, borderRadius: '50%', background: '#2f9e44' }} />
                <span>Cây xanh</span>
              </Space>
            </div>
          </div>

          <div style={{ marginTop: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
            <Input.Search
              aria-label="Tìm điểm trong khung bản đồ"
              placeholder="Tìm mã hư hỏng, tài sản hoặc cây xanh"
              value={lookup}
              onChange={(event) => setLookup(event.target.value)}
              enterButton="Xem điểm"
              onSearch={(value) => {
                const found = mapLayer === 'assets' || mapLayer === 'trees' ? undefined : pointsRef.current.find((point) =>
                  point.recordKey.toLowerCase() === value.trim().toLowerCase());
                if (found) selectPoint(found);
                else {
                  const asset = mapLayer === 'defects' || mapLayer === 'trees' ? undefined : assetsRef.current.find((point) =>
                    point.recordKey.toLowerCase() === value.trim().toLowerCase());
                  if (asset) {
                    map.current?.getView().animate({ center: fromLonLat([asset.longitude, asset.latitude]),
                      zoom: 16, duration: 350 });
                    void openAsset(asset.recordId);
                  } else {
                    const tree = mapLayer === 'defects' || mapLayer === 'assets' ? undefined : treesRef.current.find((point) =>
                      point.recordKey.toLowerCase() === value.trim().toLowerCase());
                    if (tree) {
                      map.current?.getView().animate({ center: fromLonLat([tree.longitude, tree.latitude]),
                        zoom: 17, duration: 350 });
                      void openTree(tree.recordId);
                    } else setError('Không tìm thấy mã trong khung bản đồ. Hãy di chuyển bản đồ hoặc phóng to.');
                  }
                }
              }}
              style={{ maxWidth: 440 }}
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
              💡 Nhấp vào điểm trên bản đồ hoặc nhập mã để định vị tức thì
            </Text>
          </div>
        </Card>
      </Col>

      <Col xs={24} lg={9}>
        <Card
          title={
            <Space>
              <InfoCircleOutlined style={{ color: '#1677ff' }} />
              <span style={{ fontWeight: 600 }}>Thông tin tại tọa độ</span>
            </Space>
          }
          style={{ borderRadius: 10, border: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,0.02)', minHeight: 720 }}
        >
          {isLoading && (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin size="large" />
              <div style={{ marginTop: 12, color: '#8c8c8c' }}>Đang tải dữ liệu khảo sát...</div>
            </div>
          )}
          {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}

          {!selected && !selectedAsset && !selectedTree && !isLoading && (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div style={{ padding: '12px 14px', borderRadius: 8, background: '#f0f7ff', border: '1px solid #bae0ff' }}>
                <Space align="start">
                  <InfoCircleOutlined style={{ color: '#1677ff', marginTop: 3 }} />
                  <Text type="secondary" style={{ fontSize: 13, color: '#003a8c' }}>
                    Chọn một điểm đánh dấu trên bản đồ để xem đúng đoạn khảo sát và ảnh hư hỏng.
                  </Text>
                </Space>
              </div>

              {/* Phân loại hư hỏng */}
              <Card
                size="small"
                title={
                  <Space>
                    <PieChartOutlined style={{ color: '#ff4d4f' }} />
                    <span style={{ fontSize: 13, fontWeight: 600 }}>Thống kê nhóm hư hỏng</span>
                  </Space>
                }
                style={{ borderRadius: 8 }}
              >
                <Space direction="vertical" size="small" style={{ width: '100%' }}>
                  {defectSummary.length > 0 ? (
                    defectSummary.map((item) => (
                      <div
                        key={item.type}
                        style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px 0', borderBottom: '1px dashed #f0f0f0' }}
                      >
                        <Text style={{ fontSize: 13 }}>{item.type}</Text>
                        <Tag color="red" style={{ borderRadius: 10, margin: 0 }}>{item.count} điểm</Tag>
                      </div>
                    ))
                  ) : (
                    <Text type="secondary" style={{ fontSize: 12 }}>Đang cập nhật phân loại...</Text>
                  )}
                </Space>
              </Card>

              {/* Danh sách điểm hư hỏng truy cập nhanh */}
              {points.length > 0 && (
                <Card
                  size="small"
                  title={
                    <Space>
                      <EnvironmentOutlined style={{ color: '#fa8c16' }} />
                      <span style={{ fontSize: 13, fontWeight: 600 }}>Điểm khảo sát nổi bật (Truy cập nhanh)</span>
                    </Space>
                  }
                  style={{ borderRadius: 8 }}
                >
                  <Space direction="vertical" size="small" style={{ width: '100%' }}>
                    {points.slice(0, 4).map((point) => (
                      <div
                        key={point.recordId}
                        onClick={() => selectPoint(point)}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(e) => { if (e.key === 'Enter') selectPoint(point); }}
                        style={{
                          padding: '8px 12px',
                          borderRadius: 6,
                          background: '#fafafa',
                          border: '1px solid #f0f0f0',
                          cursor: 'pointer',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          transition: 'all 0.2s ease',
                        }}
                        onMouseEnter={(e) => { e.currentTarget.style.background = '#e6f4ff'; e.currentTarget.style.borderColor = '#91caff'; }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = '#fafafa'; e.currentTarget.style.borderColor = '#f0f0f0'; }}
                      >
                        <div>
                          <Text strong style={{ fontSize: 13, color: '#1677ff' }}>{point.recordKey}</Text>
                          <div style={{ fontSize: 12, color: '#8c8c8c' }}>{point.defectType || 'Hư hỏng'} · {point.routeName || 'QL.1'}</div>
                        </div>
                        <Button size="small" type="link" icon={<RightOutlined />}>Xem</Button>
                      </div>
                    ))}
                  </Space>
                </Card>
              )}
            </Space>
          )}

          {selectedTree && <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <div style={{ background: '#f6ffed', padding: '10px 14px', borderRadius: 8, border: '1px solid #b7eb8f' }}>
              <Text strong style={{ color: '#237804' }}>📍 Tọa độ cây xanh (WGS84):</Text>
              <div><Text copyable style={{ fontSize: 13 }}>{selectedTree.latitude.toFixed(6)}, {selectedTree.longitude.toFixed(6)}</Text></div>
            </div>
            <div style={{ background: '#fafafa', padding: '10px 14px', borderRadius: 8, border: '1px solid #f0f0f0' }}>
              <Text strong>Tuyến: {selectedTree.routeName || 'Chưa rõ'}</Text>
              <div><Text>Lý trình: {selectedTree.chainage || 'Chưa rõ'}</Text></div>
            </div>
            <Card size="small" title={<Space><Tag color="green">Cây xanh</Tag><span>{selectedTree.name || selectedTree.recordKey}</span></Space>} style={{ borderRadius: 8 }}>
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Text>Mã cây: {selectedTree.recordKey}</Text>
                <Text>Loài cây: {selectedTree.speciesName || 'Chưa xác định'}</Text>
                {selectedTree.scientificName && <Text>Tên khoa học: <i>{selectedTree.scientificName}</i></Text>}
                <Text>Tình trạng sinh trưởng: {selectedTree.healthStatus || 'Chưa cập nhật'}</Text>
                <Text>Mức độ rủi ro: {selectedTree.riskLevel || 'Chưa đánh giá'}</Text>
                {(selectedTree.diameterCm || selectedTree.heightM) && <Text>
                  Kích thước: {selectedTree.diameterCm ? `đường kính ${selectedTree.diameterCm} cm` : ''}
                  {selectedTree.diameterCm && selectedTree.heightM ? ' · ' : ''}
                  {selectedTree.heightM ? `cao ${selectedTree.heightM} m` : ''}
                </Text>}
                {treeImageUrl ? <>
                  <div role="button" tabIndex={0} aria-label={`Phóng to ảnh cây xanh ${selectedTree.recordKey}`}
                    onClick={() => setTreeZoomed(true)}
                    onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault(); setTreeZoomed(true);
                    } }} style={{ position: 'relative', cursor: 'zoom-in', width: '100%', borderRadius: 8, overflow: 'hidden', border: '1px solid #d9d9d9' }}>
                    <iframe title={`Ảnh cây xanh ${selectedTree.recordKey}`} src={treeImageUrl}
                      sandbox="allow-scripts allow-same-origin" referrerPolicy="no-referrer" loading="lazy" tabIndex={-1}
                      style={{ width: '100%', height: 240, border: 0, pointerEvents: 'none' }} />
                    <Tag color="green" style={{ position: 'absolute', bottom: 10, right: 8 }}>🔍 Nhấn để phóng to</Tag>
                  </div>
                  <a href={treeImageUrl} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">Mở ảnh nguồn</a>
                </> : <Text type="secondary">Chưa có ảnh cây xanh.</Text>}
                <Button size="small" onClick={() => navigate('/green-trees')}>Xem danh sách cây xanh</Button>
              </Space>
            </Card>
          </Space>}

          {selectedAsset && <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <div style={{ background: '#e6f4ff', padding: '10px 14px', borderRadius: 8, border: '1px solid #91caff' }}>
              <Text strong style={{ color: '#0958d9' }}>📍 Tọa độ tài sản (WGS84):</Text>
              <div><Text copyable style={{ fontSize: 13 }}>{selectedAsset.latitude.toFixed(6)}, {selectedAsset.longitude.toFixed(6)}</Text></div>
            </div>
            <div style={{ background: '#fafafa', padding: '10px 14px', borderRadius: 8, border: '1px solid #f0f0f0' }}>
              <Text strong>Tuyến: {selectedAsset.routeName || 'Chưa rõ'}</Text>
              <div><Text>Lý trình: {selectedAsset.chainage || 'Chưa rõ'} · Chiều tuyến: {selectedAsset.routeSide || 'Chưa rõ'}</Text></div>
            </div>
            <Card size="small" title={<Space><Tag color="blue">Tài sản</Tag><span>{selectedAsset.assetType || selectedAsset.recordKey}</span></Space>} style={{ borderRadius: 8 }}>
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
                    } }} style={{ position: 'relative', cursor: 'zoom-in', width: '100%', borderRadius: 8, overflow: 'hidden', border: '1px solid #d9d9d9' }}>
                    <iframe title={`Ảnh tài sản ${selectedAsset.recordKey}`} src={assetImageUrl}
                      sandbox="allow-scripts allow-same-origin" referrerPolicy="no-referrer" loading="lazy" tabIndex={-1}
                      style={{ width: '100%', height: 240, border: 0, pointerEvents: 'none' }} />
                    <Tag color="blue" style={{ position: 'absolute', bottom: 10, right: 8 }}>🔍 Nhấn để phóng to</Tag>
                  </div>
                  <a href={assetImageUrl} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">Mở ảnh nguồn</a>
                </> : <Text type="secondary">Chưa có ảnh tài sản.</Text>}
                <Space wrap>
                  <Button size="small" onClick={() => navigate('/assets?datasetKey=vroad_assets')}>Xem danh sách tài sản</Button>
                  <Button size="small" onClick={() =>
                    navigate(`/documents?asset=${encodeURIComponent(selectedAsset.recordKey)}`)}>
                    Tra cứu hồ sơ tài sản
                  </Button>
                  {!showRelatedDefects && <Button size="small" loading={loadingRelated} onClick={() => void openRelated()}>
                    Xem hư hỏng gần tài sản
                  </Button>}
                </Space>
              </Space>
            </Card>
            {showRelatedDefects && <Card size="small" title="Hư hỏng ghi nhận gần tài sản"
              extra={<Button size="small" onClick={() => setShowRelatedDefects(false)}>Thu gọn</Button>} style={{ borderRadius: 8 }}>
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                {relatedDefects.length === 0 && <Text type="secondary">Chưa ghi nhận hư hỏng trùng tuyến, lý trình và phía tài sản trong phạm vi khảo sát.</Text>}
                {selectedAsset.relatedDefectsTruncated && <Text type="secondary">
                  Hiển thị 30 điểm gần nhất.
                </Text>}
                {relatedDefects.map((defect) => {
                  const defectImageUrl = trustedSourceImage(defect.sourceImageUrl);
                  return <Card key={defect.recordId} size="small" title={defect.defectType || defect.recordKey} style={{ borderRadius: 6 }}>
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
                          } }} style={{ cursor: 'zoom-in', width: '100%', borderRadius: 6, overflow: 'hidden', border: '1px solid #d9d9d9' }}>
                          <iframe title={`Ảnh khảo sát ${defect.recordKey}`} src={defectImageUrl}
                            sandbox="allow-scripts allow-same-origin" referrerPolicy="no-referrer"
                            loading="lazy" tabIndex={-1} style={{ width: '100%', height: 240,
                              border: 0, pointerEvents: 'none' }} />
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
            <div style={{ background: '#f6ffed', padding: '10px 14px', borderRadius: 8, border: '1px solid #b7eb8f' }}>
              <Text strong style={{ color: '#389e0d' }}>📍 Tọa độ khảo sát (WGS84):</Text>
              <div><Text copyable style={{ fontSize: 13 }}>{selected.latitude.toFixed(6)}, {selected.longitude.toFixed(6)}</Text></div>
            </div>
            {selectedPoint ? <>
              <div style={{ background: '#fafafa', padding: '10px 14px', borderRadius: 8, border: '1px solid #f0f0f0' }}>
                <Text strong>Tuyến: {selectedPoint.routeName || 'Chưa rõ'}</Text>
                <div><Text>Lý trình: {selectedPoint.chainage || 'Chưa rõ'} · Chiều tuyến: {selectedPoint.routeSide || 'Chưa rõ'}</Text></div>
                <div><Text type="secondary">Tài sản khảo sát lân cận: {selected.nearbyAssets}</Text></div>
              </div>
              {selected.roadCatalog && <RoadCatalogCard road={selected.roadCatalog} />}
              <Card size="small" title={<Space><Tag color="red">Hư hỏng</Tag><span>{selectedPoint.defectType || selectedPoint.recordKey}</span></Space>} style={{ borderRadius: 8 }}>
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
                      style={{ position: 'relative', cursor: 'zoom-in', width: '100%', borderRadius: 8, overflow: 'hidden', border: '1px solid #d9d9d9' }}>
                      <iframe
                      title={`Ảnh khảo sát ${selectedPoint.recordKey}`} src={trustedSourceImage(selectedPoint.sourceImageUrl)!}
                      sandbox="allow-scripts allow-same-origin" referrerPolicy="no-referrer" loading="lazy"
                      tabIndex={-1} style={{ width: '100%', height: 240, border: 0, pointerEvents: 'none' }} />
                      <Tag color="blue" style={{ position: 'absolute', bottom: 10, right: 8 }}>🔍 Nhấn để phóng to</Tag>
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
    <Modal title={`Ảnh cây xanh ${selectedTree?.recordKey || ''}`} open={treeZoomed && !!treeImageUrl}
      footer={null} width="min(96vw, 1200px)" onCancel={() => setTreeZoomed(false)}>
      {treeImageUrl && <iframe title="Ảnh cây xanh phóng to" src={treeImageUrl}
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
