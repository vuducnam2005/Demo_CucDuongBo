import { apiClient } from './api';

export interface GeoJsonGeometry {
  type: string;
  coordinates: unknown;
}

export interface GeoJsonFeature {
  id: string;
  type: 'Feature';
  geometry: GeoJsonGeometry;
  properties: {
    dataset_key?: string;
    record_key?: string;
    display_name?: string;
    branch_id?: string;
    state_name?: string;
    state?: string;
    route_code?: string;
    route_name?: string;
    is_cluster?: boolean;
    point_count?: number;
    sample_key?: string;
    sample_name?: string;
    bbox_min_lon?: number;
    bbox_min_lat?: number;
    bbox_max_lon?: number;
    bbox_max_lat?: number;
    [key: string]: string | number | boolean | null | undefined;
  };
}

export interface GeoJsonFeatureCollection {
  type: 'FeatureCollection';
  features: GeoJsonFeature[];
}

export interface GisQueryParams {
  bbox?: string;
  minLon?: number;
  minLat?: number;
  maxLon?: number;
  maxLat?: number;
  branch?: string;
  status?: string;
  route?: string;
  q?: string;
  limit?: number;
}

export interface GisRequestOptions {
  signal?: AbortSignal;
}

export const isPointFeature = (feature: GeoJsonFeature): feature is GeoJsonFeature & {
  geometry: { type: 'Point'; coordinates: [number, number] };
} => {
  if (feature.type !== 'Feature' || feature.geometry?.type !== 'Point') return false;
  const coordinates = feature.geometry.coordinates;
  return Array.isArray(coordinates)
    && coordinates.length >= 2
    && typeof coordinates[0] === 'number'
    && Number.isFinite(coordinates[0])
    && typeof coordinates[1] === 'number'
    && Number.isFinite(coordinates[1])
    && coordinates[0] >= -180
    && coordinates[0] <= 180
    && coordinates[1] >= -90
    && coordinates[1] <= 90;
};

export interface GisClusterParams extends GisQueryParams {
  gridSize?: number;
  zoom?: number;
}

export interface GisBenchmarkItem {
  datasetKey: string;
  datasetName: string;
  totalDatasetRecords: number;
  spatialRecordsWithCoordinates: number;
  bboxQueryExecutionTimeMs: number;
  bboxFeaturesReturned: number;
  gridClusterExecutionTimeMs: number;
  gridClustersReturned: number;
  fullLoadEstimatedSizeBytes: number;
  actualPayloadSizeBytes: number;
  compressionRatioPercent: number;
  verdict: string;
}

export interface GisBenchmarkResponse {
  benchmarkTimestamp: string;
  totalRecordsEvaluated: number;
  datasets: GisBenchmarkItem[];
  safetyAssessment: string;
  complianceNoFullLoad: boolean;
}

/**
 * Lấy dữ liệu vector không gian GeoJSON theo Bounding Box (BBOX) và bộ lọc nghiệp vụ.
 * Giới hạn tối đa limit bản ghi để đảm bảo an toàn bộ nhớ browser.
 */
export const fetchGeoData = async (
  datasetKey: string,
  params: GisQueryParams,
  options?: GisRequestOptions,
): Promise<GeoJsonFeatureCollection> => {
  const response = await apiClient.get<GeoJsonFeatureCollection>(
    `/api/datasets/${datasetKey}/geo`,
    // Spatial joins/clustering have a separate SLO from ordinary CRUD requests.
    { params, signal: options?.signal, timeout: 45000 }
  );
  return response.data;
};

/**
 * Lấy các cụm điểm không gian (Spatial Grid Clusters) tính toán trực tiếp từ PostGIS.
 * Cho phép hiển thị tập dữ liệu lên đến hàng trăm nghìn đối tượng (như 222k biển báo)
 * ở cấp độ vĩ mô mà không làm quá tải DOM và browser.
 */
export const fetchSpatialClusters = async (
  datasetKey: string,
  params: GisClusterParams,
  options?: GisRequestOptions,
): Promise<GeoJsonFeatureCollection> => {
  const response = await apiClient.get<GeoJsonFeatureCollection>(
    `/api/datasets/${datasetKey}/clusters`,
    { params, signal: options?.signal, timeout: 45000 }
  );
  return response.data;
};

/**
 * Chạy bài đo kiểm hiệu năng GIS và kiểm tra tuân thủ quy tắc không tải tràn 222k geometries.
 */
export const fetchGisBenchmark = async (
  dataset1 = 'tbl_road_sign',
  dataset2 = 'road_sphere_mirror'
): Promise<GisBenchmarkResponse> => {
  const response = await apiClient.get<GisBenchmarkResponse>(
    '/api/datasets/gis/benchmark',
    { params: { dataset1, dataset2 } }
  );
  return response.data;
};
