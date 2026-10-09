import { apiClient } from './api';

export interface RoadLengthSummary {
  totalRoutes: number;
  totalSegments: number;
  totalLengthKm: number;
  averageLengthKm: number;
  longestRouteCode: string;
  longestRouteLengthKm: number;
}

export interface RouteLengthItem {
  routeCode: string;
  routeName: string;
  lengthKm: number;
  segmentCount: number;
  branchId: string;
  branchName: string;
  surfaceType: string;
  roadClass: string;
  managementUnit: string;
}

export interface BranchDistributionItem {
  branchId: string;
  branchName: string;
  routeCount: number;
  totalLengthKm: number;
  percent: number;
}

export interface SurfaceDistributionItem {
  surfaceType: string;
  totalLengthKm: number;
  percent: number;
}

export interface RoadLengthReportResponse {
  summary: RoadLengthSummary;
  routes: RouteLengthItem[];
  branchDistribution: BranchDistributionItem[];
  surfaceDistribution: SurfaceDistributionItem[];
}

export interface MaintenanceSummary {
  totalProjects: number;
  totalBudgetVnd: number;
  completedCount: number;
  inProgressCount: number;
  plannedCount: number;
  completionRatePercent: number;
}

export interface MaintenanceProjectItem {
  id: string;
  projectCode: string;
  projectName: string;
  branchId: string;
  branchName: string;
  routeCode: string;
  maintenanceType: string;
  budgetVnd: number;
  planYear: number;
  status: string;
  contractor: string;
  startDate: string;
  endDate: string;
}

export interface BranchBudgetSummary {
  branchId: string;
  branchName: string;
  projectCount: number;
  totalBudgetVnd: number;
  percent: number;
}

export interface YearlyBudgetSummary {
  year: number;
  projectCount: number;
  totalBudgetVnd: number;
}

export interface MaintenanceReportResponse {
  summary: MaintenanceSummary;
  projects: MaintenanceProjectItem[];
  branchSummary: BranchBudgetSummary[];
  yearlySummary: YearlyBudgetSummary[];
}

export interface SignCategoryItem {
  categoryCode: string;
  categoryName: string;
  signPrefix: string;
  count: number;
  percent: number;
  sampleSignCode: string;
  description: string;
}

export interface BlackspotItem {
  id: string;
  spotCode: string;
  routeCode: string;
  kmMarker: string;
  branchId: string;
  branchName: string;
  severity: string;
  description: string;
  incidentCount: number;
  fatalityCount: number;
  rectificationStatus: string;
  longitude: number | null;
  latitude: number | null;
}

export interface BranchSignBlackspotItem {
  branchId: string;
  branchName: string;
  signCount: number;
  blackspotCount: number;
}

export interface RoadSignBlackspotReportResponse {
  summary: {
    totalRoadSigns: number;
    totalSignCategories: number;
    totalBlackspots: number;
    highRiskBlackspots: number;
    rectifiedBlackspots: number;
    monitoredBlackspots: number;
  };
  signCategories: SignCategoryItem[];
  blackspots: BlackspotItem[];
  branchStatistics: BranchSignBlackspotItem[];
}

export const fetchRoadLengthReport = async (params?: {
  branch?: string;
  route?: string;
  surfaceType?: string;
}): Promise<RoadLengthReportResponse> => {
  const response = await apiClient.get<RoadLengthReportResponse>('/api/reports/road-lengths', { params });
  return response.data;
};

export const exportRoadLengthReportCsv = async (params?: {
  branch?: string;
  route?: string;
  surfaceType?: string;
}): Promise<Blob> => {
  const response = await apiClient.get('/api/reports/road-lengths/export', {
    params,
    responseType: 'blob',
  });
  return response.data;
};

export const fetchMaintenanceReport = async (params?: {
  branch?: string;
  year?: number;
  type?: string;
  status?: string;
}): Promise<MaintenanceReportResponse> => {
  const response = await apiClient.get<MaintenanceReportResponse>('/api/reports/maintenance', { params });
  return response.data;
};

export const exportMaintenanceReportCsv = async (params?: {
  branch?: string;
  year?: number;
  type?: string;
  status?: string;
}): Promise<Blob> => {
  const response = await apiClient.get('/api/reports/maintenance/export', {
    params,
    responseType: 'blob',
  });
  return response.data;
};

export const fetchRoadSignBlackspotReport = async (params?: {
  branch?: string;
  route?: string;
  category?: string;
}): Promise<RoadSignBlackspotReportResponse> => {
  const response = await apiClient.get<RoadSignBlackspotReportResponse>('/api/reports/road-signs-blackspots', { params });
  return response.data;
};

export const exportRoadSignBlackspotReportCsv = async (params?: {
  branch?: string;
  route?: string;
  category?: string;
}): Promise<Blob> => {
  const response = await apiClient.get('/api/reports/road-signs-blackspots/export', {
    params,
    responseType: 'blob',
  });
  return response.data;
};

export interface IriSummary {
  surveyLengthKm: number;
  totalValidSegments: number;
  averageIri: number;
  medianIri: number;
  standardName: string;
  totalCorrelatedDefects: number;
  poorOrVeryPoorPercentage: number;
}

export interface IriConditionDistribution {
  conditionGroup: string;
  conditionLabel: string;
  count: number;
  percentage: number;
  color: string;
  description: string;
}

export interface IriSegmentItem {
  rank: number;
  roadName: string;
  routeCode: string;
  chainage: string;
  startMeters?: number;
  endMeters?: number;
  iriValue: number;
  speedKmh?: number;
  conditionGroup: string;
  conditionLabel: string;
  defectCount: number;
}

export interface IriRoughnessReportResponse {
  summary: IriSummary;
  distribution: IriConditionDistribution[];
  segments: IriSegmentItem[];
  totalSegments: number;
}

export const fetchIriRoughnessReport = async (params?: {
  route?: string;
  conditionGroup?: string;
}): Promise<IriRoughnessReportResponse> => {
  const response = await apiClient.get<IriRoughnessReportResponse>('/api/reports/iri-roughness', { params });
  return response.data;
};

export const exportIriRoughnessReportCsv = async (params?: {
  route?: string;
  conditionGroup?: string;
}): Promise<Blob> => {
  const response = await apiClient.get('/api/reports/iri-roughness/export', {
    params,
    responseType: 'blob',
  });
  return response.data;
};

