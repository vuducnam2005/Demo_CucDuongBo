import { apiClient } from './api';

export interface DefectPoint {
  recordId: number;
  recordKey: string;
  longitude: number;
  latitude: number;
  routeName: string;
  routeSide: string | null;
  defectSide: string | null;
  chainage: string | null;
  defectType: string | null;
  areaM2: string | null;
  surveyDate: string | null;
  sourceImageUrl: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolutionNote: string | null;
  hasEvidence: boolean;
}

export interface RoadContext {
  longitude: number;
  latitude: number;
  radiusMeters: number;
  routeName: string | null;
  nearestChainage: string | null;
  nearbyAssets: number;
  defects: DefectPoint[];
  roadCatalog: RoadCatalog | null;
}

export interface RoadCatalog {
  recordKey: string;
  name: string;
  lengthKm: string | null;
  startChainage: string | null;
  endChainage: string | null;
}

export interface AssetPoint {
  recordId: number;
  recordKey: string;
  longitude: number;
  latitude: number;
  routeName: string | null;
  chainage: string | null;
  routeSide: string | null;
  category: string | null;
  assetType: string | null;
  condition: string | null;
  assetSide: string | null;
  surveyDate: string | null;
  sourceImageUrl: string | null;
  roadCatalog: RoadCatalog | null;
  relatedDefects: DefectPoint[];
  relatedDefectsTruncated: boolean;
}

export interface AssetMarker {
  recordId: number;
  recordKey: string;
  longitude: number;
  latitude: number;
}

export const fetchVroadAssets = async (bbox: [number, number, number, number]) =>
  (await apiClient.get<{ content: AssetMarker[]; truncated: boolean }>('/api/vroad/map/assets', {
    params: { minLon: bbox[0], minLat: bbox[1], maxLon: bbox[2], maxLat: bbox[3], limit: 500 },
  })).data;

export const fetchVroadAsset = async (id: number, includeRelated = false): Promise<AssetPoint> =>
  (await apiClient.get<AssetPoint>(`/api/vroad/assets/${id}/map`, { params: { includeRelated } })).data;

export const fetchVroadDefect = async (id: number): Promise<DefectPoint> =>
  (await apiClient.get<DefectPoint>(`/api/vroad/defects/${id}/map`)).data;

export interface CaseItem {
  recordId: number;
  recordKey: string;
  routeName: string;
  chainage: string | null;
  defectType: string | null;
  resolutionNote: string;
  resolvedBy: string;
  resolvedAt: string;
  hasEvidence: boolean;
}

export interface CasePage {
  content: CaseItem[];
  totalElements: number;
  page: number;
  size: number;
}

export interface SurveyOverview {
  assets: number;
  defects: number;
  iriSegments: number;
  resolvedCases: number;
  defectTypes: { label: string; count: number }[];
  assetCategories: { label: string; count: number }[];
}

export const fetchSurveyOverview = async () =>
  (await apiClient.get<SurveyOverview>('/api/vroad/dashboard')).data;

export const trustedSourceImage = (url: string | null): string | null => {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' && parsed.hostname === 'platform.vroad.vn' && !parsed.port && !parsed.username && !parsed.password
      ? parsed.href : null;
  } catch {
    return null;
  }
};

export const fetchVroadPoints = async (bbox: [number, number, number, number]) =>
  (await apiClient.get<{ content: DefectPoint[]; truncated: boolean }>('/api/vroad/map/points', {
    params: { minLon: bbox[0], minLat: bbox[1], maxLon: bbox[2], maxLat: bbox[3], limit: 500 },
  })).data;

export const fetchRoadContext = async (longitude: number, latitude: number, recordId?: number) =>
  (await apiClient.get<RoadContext>('/api/vroad/map/near', {
    params: { lon: longitude, lat: latitude, radiusMeters: 200, recordId },
  })).data;

export const resolveVroadDefect = async (id: number, note: string, image: File | null) => {
  const form = new FormData();
  form.append('note', note);
  if (image) form.append('evidence', image);
  return (await apiClient.post<CaseItem>(`/api/vroad/defects/${id}/resolve`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })).data;
};

export const fetchVroadCases = async (page: number, size = 20) =>
  (await apiClient.get<CasePage>('/api/vroad/cases', { params: { page, size } })).data;
