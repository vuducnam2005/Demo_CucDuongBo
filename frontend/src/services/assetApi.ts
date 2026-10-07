import { apiClient, PagedResponse } from './api';
import { normalizePagedResponse } from './pagination';

export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonObject | JsonValue[];
export interface JsonObject {
  [key: string]: JsonValue;
}

export interface DatasetTreeNode {
  key: string;
  title: string;
  datasetKey?: string;
  totalRecords?: number;
  kind?: string;
  icon?: string;
  isLeaf: boolean;
  children?: DatasetTreeNode[];
}

export interface DatasetField {
  fieldName: string;
  displayName: string;
  dataType: string;
  searchable: boolean;
  filterable: boolean;
  unit?: string;
}

export interface DatasetMetadata {
  datasetKey: string;
  datasetName: string;
  kind: string;
  endpoint: string;
  sourceFile: string;
  totalRecords: number;
  geometryType: string;
  fields: DatasetField[];
}

export interface RecordItem {
  id: number;
  datasetKey: string;
  recordKey: string;
  payload: JsonObject | null;
  recordStatus: string;
  createdAt: string;
  updatedAt: string;
}

export interface AssetCreateUpdatePayload {
  name: string;
  routeCode?: string;
  routeName?: string;
  kmFrom?: number;
  kmTo?: number;
  lytrinh?: string;
  provinceId?: string;
  provinceName?: string;
  districtName?: string;
  townName?: string;
  roadClass?: string;
  roadType?: string;
  organizationId?: string;
  branchId?: string;
  branchName?: string;
  managementAgency?: string;
  state?: string;
  stateName?: string;
  levelState?: string;
  activeStatus?: string;
  maintainValue?: number;
  constructionYear?: number;
  parentId?: string;
  attributes?: JsonObject;
  version?: number;
}

export interface ImportPreviewResponse {
  datasetCode: string;
  totalRows: number;
  validRows: number;
  errorRows: number;
  previewRows: JsonObject[];
  errors: Array<{ rowNumber: number; field: string; message: string }>;
  canProceed: boolean;
}

export interface ImportExecutionResponse {
  datasetCode: string;
  dryRun: boolean;
  totalProcessed: number;
  insertedCount: number;
  updatedCount: number;
  failedCount: number;
  rolledBack: boolean;
  message: string;
  errorMessages: string[];
}

export const fetchDatasetTree = async (parent?: string): Promise<DatasetTreeNode[]> => {
  const response = await apiClient.get<DatasetTreeNode[]>('/api/datasets/tree', {
    params: { parent },
  });
  return response.data;
};

export const fetchDatasetMetadata = async (datasetKey: string): Promise<DatasetMetadata> => {
  const response = await apiClient.get<DatasetMetadata>(`/api/datasets/${datasetKey}/metadata`);
  return response.data;
};

export const fetchDatasetRecords = async (
  datasetKey: string,
  params: Record<string, string | number | boolean | undefined> = {}
): Promise<PagedResponse<RecordItem>> => {
  const response = await apiClient.get<unknown>(`/api/datasets/${datasetKey}/records`, {
    params,
  });
  return normalizePagedResponse(response.data, isRecordItem, `GET /api/datasets/${datasetKey}/records`);
};

export const fetchRecordById = async (
  datasetKey: string,
  idOrKey: string
): Promise<RecordItem> => {
  const response = await apiClient.get<RecordItem>(`/api/datasets/${datasetKey}/records/${idOrKey}`);
  return response.data;
};

export const exportDatasetRecords = async (
  datasetKey: string,
  params: Record<string, string | number | boolean | undefined> = {}
): Promise<Blob> => {
  const response = await apiClient.get(`/api/datasets/${datasetKey}/export`, {
    params,
    responseType: 'blob',
  });
  return response.data;
};

const isJsonObject = (value: unknown): value is JsonObject =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isRecordItem = (value: unknown): value is RecordItem => {
  if (!isJsonObject(value)) return false;
  return (
    typeof value.id === 'number' &&
    typeof value.datasetKey === 'string' &&
    typeof value.recordKey === 'string' &&
    (value.payload === null || isJsonObject(value.payload)) &&
    typeof value.recordStatus === 'string' &&
    typeof value.createdAt === 'string' &&
    typeof value.updatedAt === 'string'
  );
};

export const createAssetRecord = async (
  datasetKey: string,
  data: AssetCreateUpdatePayload
): Promise<RecordItem> => {
  const response = await apiClient.post<RecordItem>(`/api/datasets/${datasetKey}/records`, data);
  return response.data;
};

export const updateAssetRecord = async (
  datasetKey: string,
  idOrKey: string,
  data: AssetCreateUpdatePayload
): Promise<RecordItem> => {
  const response = await apiClient.put<RecordItem>(`/api/datasets/${datasetKey}/records/${idOrKey}`, data);
  return response.data;
};

export const deleteAssetRecord = async (
  datasetKey: string,
  idOrKey: string,
  version?: number,
  reason?: string
): Promise<void> => {
  await apiClient.delete(`/api/datasets/${datasetKey}/records/${idOrKey}`, {
    params: { version, reason },
    data: { version, reason },
  });
};

export const previewImportDataset = async (
  datasetKey: string,
  content: string,
  format: 'CSV' | 'JSON' = 'CSV'
): Promise<ImportPreviewResponse> => {
  const response = await apiClient.post<ImportPreviewResponse>(`/api/datasets/${datasetKey}/import/preview`, {
    content,
    format,
  });
  return response.data;
};

export const executeImportDataset = async (
  datasetKey: string,
  req: {
    content: string;
    format?: string;
    dryRun?: boolean;
    batchSize?: number;
  }
): Promise<ImportExecutionResponse> => {
  const response = await apiClient.post<ImportExecutionResponse>(`/api/datasets/${datasetKey}/import`, {
    content: req.content,
    format: req.format || 'CSV',
    dryRun: req.dryRun ?? false,
    batchSize: req.batchSize || 500,
  });
  return response.data;
};
