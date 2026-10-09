import { apiClient } from './api';

export interface OperationsCount {
  code: string;
  label: string;
  count: number;
}

export interface OperationsDay {
  day: string;
  count: number;
}

export interface OperationsSummary {
  totalUsers: number;
  activeUsers: number;
  datasetCount: number;
  rawRecords: number;
  storedDocuments: number;
  stagedBatches: number;
  pendingRecords: number;
  invalidRecords: number;
  resolvedCases: number;
  simulatedTrafficObservations: number;
  importedTrafficObservations: number;
  trafficDensitySnapshots: number;
  lastRawImportAt: string | null;
  calculatedAt: string;
  datasets: OperationsCount[];
  activeRoles: OperationsCount[];
  documentBranches: OperationsCount[];
  auditActivity: OperationsDay[];
  trafficVehiclesByClass: OperationsCount[];
}

export const fetchOperationsSummary = async (): Promise<OperationsSummary> =>
  (await apiClient.get<OperationsSummary>('/api/admin/operations/summary')).data;
