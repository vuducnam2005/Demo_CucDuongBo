import { apiClient } from './api';

export interface InboundRecord {
  id: number;
  sourceRecordKey: string | null;
  recordKind: string | null;
  status: 'PENDING' | 'INVALID';
  validationErrors: string[];
}

export interface InboundBatch {
  id: number;
  requestKey: string;
  schemaVersion: string;
  status: 'STAGED';
  receivedAt: number;
  pending: number;
  invalid: number;
}

export interface InboundBatchDetail {
  batch: InboundBatch;
  records: InboundRecord[];
}

export interface ProposedInboundRequest {
  requestKey: string;
  schemaVersion: 'demo-proposal-v1';
  records: Record<string, unknown>[];
}

export const fetchInboundBatches = async (page = 0): Promise<InboundBatch[]> =>
  (await apiClient.get<InboundBatch[]>('/api/vroad/inbound/batches', { params: { page, size: 20 } })).data;

export const fetchInboundDetail = async (id: number): Promise<InboundBatchDetail> =>
  (await apiClient.get<InboundBatchDetail>(`/api/vroad/inbound/batches/${id}`)).data;

export const stageInboundBatch = async (data: ProposedInboundRequest): Promise<InboundBatchDetail> =>
  (await apiClient.post<InboundBatchDetail>('/api/vroad/inbound/batches', data)).data;
