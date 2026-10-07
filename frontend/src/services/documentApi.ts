import { apiClient } from './api';

export interface DocumentFolder {
  id: string;
  folderCode: string;
  folderName: string;
  parentId: string;
  documentCount: number;
}

export interface DocumentItem {
  id: string;
  fileEntryId: string;
  fileName: string;
  fileExtension: string;
  mimeType: string;
  fileSize: number | null;
  groupId: string | null;
  groupName: string | null;
  objectName: string | null;
  tableName: string | null;
  uploader: string | null;
  createdAt: string | null;
}

export interface PagedDocuments {
  content: DocumentItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const fetchDocumentFolders = async (): Promise<DocumentFolder[]> => {
  const response = await apiClient.get<DocumentFolder[]>('/api/documents/folders');
  return response.data;
};

export const createDocumentFolder = async (
  folderName: string,
  parentId?: string
): Promise<DocumentFolder> => {
  const response = await apiClient.post<DocumentFolder>('/api/documents/folders', null, {
    params: { folderName, parentId },
  });
  return response.data;
};

export const fetchDocuments = async (params?: {
  folderId?: string;
  search?: string;
  extension?: string;
  page?: number;
  size?: number;
}): Promise<PagedDocuments> => {
  const response = await apiClient.get<PagedDocuments>('/api/documents', { params });
  return response.data;
};

export const fetchDocumentById = async (id: string): Promise<DocumentItem> => {
  const response = await apiClient.get<DocumentItem>(`/api/documents/${encodeURIComponent(id)}`);
  return response.data;
};

export const downloadDocumentFile = async (id: string, fileName?: string): Promise<void> => {
  const response = await apiClient.get(`/api/documents/${encodeURIComponent(id)}/file`, {
    responseType: 'blob',
  });
  const url = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', fileName || `document_${id}.pdf`);
  document.body.appendChild(link);
  link.click();
  link.parentNode?.removeChild(link);
  window.URL.revokeObjectURL(url);
};

export const uploadDocumentFile = async (
  file: File,
  folderId?: string,
  assetRecordId?: string,
  branchId?: string
): Promise<DocumentItem> => {
  const formData = new FormData();
  formData.append('file', file);
  if (folderId) formData.append('folderId', folderId);
  if (assetRecordId) formData.append('assetRecordId', assetRecordId);
  if (branchId) formData.append('branchId', branchId);

  const response = await apiClient.post<DocumentItem>('/api/documents/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return response.data;
};

export const deleteDocumentFile = async (id: string): Promise<void> => {
  await apiClient.delete(`/api/documents/${encodeURIComponent(id)}`);
};
