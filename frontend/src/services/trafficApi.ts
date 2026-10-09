import { apiClient } from './api';

export interface TrafficStation {
  stationCode: string;
  stationName: string;
  routeName: string;
  isDemo: boolean;
  sourceSystem: string;
  segmentLengthKm: number | null;
}

export interface TrafficDensity {
  measuredAt: string;
  presentVehicles: number;
  vehiclesPerKm: number;
  classes: { vehicleClass: string; count: number }[];
}

export interface TrafficSummary {
  station: TrafficStation;
  totalVehicles: number;
  rangeStart: string | null;
  rangeEnd: string | null;
  classes: { vehicleClass: string; count: number }[];
  intervals: { start: string; end: string; count: number }[];
  density: TrafficDensity | null;
}

export const fetchTrafficStations = async () =>
  (await apiClient.get<TrafficStation[]>('/api/vroad/traffic/stations')).data;

export const fetchTrafficSummary = async (stationCode: string, from?: string, to?: string) =>
  (await apiClient.get<TrafficSummary>('/api/vroad/traffic/summary', {
    params: { stationCode, from, to },
  })).data;

export interface TrafficImportRequest {
  requestKey: string;
  sourceSystem: string;
  station: { code: string; name: string; routeName: string; branchId: string;
    segmentLengthKm: number | null };
  counts: { eventKey: string; direction: string; lane: number; windowStart: string;
    windowEnd: string; vehicleClass: string; vehicleCount: number }[];
  snapshots: { eventKey: string; direction: string; measuredAt: string;
    vehicleClass: string; presentVehicles: number }[];
}

export interface TrafficImportResult {
  batchId: number;
  requestKey: string;
  stationCode: string;
  importedCounts: number;
  importedSnapshots: number;
}

export const submitTrafficImport = async (request: TrafficImportRequest) =>
  (await apiClient.post<TrafficImportResult>('/api/vroad/traffic/imports', request)).data;
