import { apiClient } from './api';

export interface DashboardSummary {
  totalAssets: number;
  totalDatasets: number;
  physicalAssetDatasets: number;
  moduleDatasets: number;
  totalBridges: number;
  totalRoadSigns: number;
  totalNationalRoadRoutes: number;
  totalNationalRoadLengthKm: number;
  totalDocuments: number;
  sourceDataset: string;
  filter: string;
  lastUpdated: string;
}

export interface DashboardBranchStat {
  branchId: string;
  branchName: string;
  totalAssets: number;
  bridgeCount: number;
  roadSignCount: number;
  nationalRoadCount: number;
  sourceDataset: string;
  filter: string;
  lastUpdated: string;
}

export interface DashboardDatasetStat {
  datasetKey: string;
  datasetName: string;
  kind: string;
  totalRecords: number;
  sourceFile: string;
  sourceDataset: string;
  filter: string;
  lastUpdated: string;
}

export interface BranchSignCount {
  branchId: string;
  branchName: string;
  count: number;
  percentage: number;
}

export interface SignCategory {
  category: string;
  count: number;
}

export interface DashboardRoadSignStat {
  totalSigns: number;
  byBranch: BranchSignCount[];
  byShape: SignCategory[];
  sourceDataset: string;
  filter: string;
  lastUpdated: string;
}

export interface RouteLength {
  routeCode: string;
  routeName: string;
  lengthKm: number;
}

export interface LengthDistribution {
  rangeLabel: string;
  routeCount: number;
  totalKm: number;
}

export interface DashboardRoadLengthStat {
  totalRoutes: number;
  totalLengthKm: number;
  averageLengthKm: number;
  longestRoutes: RouteLength[];
  distribution: LengthDistribution[];
  sourceDataset: string;
  filter: string;
  lastUpdated: string;
}

export interface DashboardRecentAsset {
  datasetKey: string;
  datasetName: string;
  recordKey: string;
  assetName: string;
  branchId: string;
  branchName: string;
  importedAt: string;
  sourceDataset: string;
  detailUrl: string;
}

export const fetchDashboardSummary = async (): Promise<DashboardSummary> => {
  const response = await apiClient.get<DashboardSummary>('/api/dashboard/summary');
  return response.data;
};

export const fetchDashboardBranches = async (): Promise<DashboardBranchStat[]> => {
  const response = await apiClient.get<DashboardBranchStat[]>('/api/dashboard/stats/branches');
  return response.data;
};

export const fetchDashboardDatasets = async (): Promise<DashboardDatasetStat[]> => {
  const response = await apiClient.get<DashboardDatasetStat[]>('/api/dashboard/stats/datasets');
  return response.data;
};

export const fetchDashboardRoadSigns = async (): Promise<DashboardRoadSignStat> => {
  const response = await apiClient.get<DashboardRoadSignStat>('/api/dashboard/stats/road-signs');
  return response.data;
};

export const fetchDashboardRoadLengths = async (): Promise<DashboardRoadLengthStat> => {
  const response = await apiClient.get<DashboardRoadLengthStat>('/api/dashboard/stats/road-lengths');
  return response.data;
};

export const fetchDashboardRecentAssets = async (limit = 10): Promise<DashboardRecentAsset[]> => {
  const response = await apiClient.get<DashboardRecentAsset[]>('/api/dashboard/stats/recent-assets', {
    params: { limit },
  });
  return response.data;
};
