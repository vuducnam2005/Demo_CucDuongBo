import { apiClient } from './api';

export interface DocumentFolder {
  id: string;
  folderCode: string;
  folderName: string;
  parentId: string;
  documentCount: number;
  branchId: string;
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

export interface DocumentVersion {
  versionNumber: number;
  fileName: string;
  fileSize: number;
  uploadedBy: string | null;
  changeNote: string | null;
  uploadedAt: string;
}

export const fetchDocumentFolders = async (): Promise<DocumentFolder[]> => {
  const response = await apiClient.get<DocumentFolder[]>('/api/local-documents/folders');
  return response.data;
};

export const fetchDocumentBranches = async (): Promise<string[]> => {
  const response = await apiClient.get<string[]>('/api/local-documents/branches');
  return response.data;
};

export const createDocumentFolder = async (
  folderName: string,
  parentId?: string,
  branchId?: string
): Promise<DocumentFolder> => {
  const response = await apiClient.post<DocumentFolder>('/api/local-documents/folders', null, {
    params: { folderName, parentId, branchId },
  });
  return response.data;
};

export const renameDocumentFolder = async (id: string, name: string): Promise<DocumentFolder> => {
  const response = await apiClient.patch<DocumentFolder>(
    `/api/local-documents/folders/${encodeURIComponent(id)}`, { name }
  );
  return response.data;
};

export const deleteDocumentFolder = async (id: string): Promise<void> => {
  await apiClient.delete(`/api/local-documents/folders/${encodeURIComponent(id)}`);
};

export const fetchDocuments = async (params?: {
  folderId?: string;
  search?: string;
  extension?: string;
  page?: number;
  size?: number;
}): Promise<PagedDocuments> => {
  const response = await apiClient.get<PagedDocuments>('/api/local-documents', {
    params: { ...params, q: params?.search, search: undefined },
  });
  return response.data;
};

export const fetchDocumentById = async (id: string): Promise<DocumentItem> => {
  const response = await apiClient.get<DocumentItem>(`/api/local-documents/${encodeURIComponent(id)}`);
  return response.data;
};

export const fetchDocumentVersions = async (id: string): Promise<DocumentVersion[]> => {
  const response = await apiClient.get<DocumentVersion[]>(`/api/local-documents/${encodeURIComponent(id)}/versions`);
  return response.data;
};

export const uploadDocumentVersion = async (id: string, file: File, note: string): Promise<DocumentVersion> => {
  const form = new FormData();
  form.append('file', file);
  if (note.trim()) form.append('note', note.trim());
  const response = await apiClient.post<DocumentVersion>(
    `/api/local-documents/${encodeURIComponent(id)}/versions`, form,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  );
  return response.data;
};

export const downloadDocumentFile = async (
  id: string, fileName?: string, versionNumber?: number
): Promise<void> => {
  const path = `/api/local-documents/${encodeURIComponent(id)}`;
  const response = await apiClient.get(
    versionNumber === undefined ? `${path}/file` : `${path}/versions/${versionNumber}/file`, {
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

  const response = await apiClient.post<DocumentItem>('/api/local-documents/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return response.data;
};

export const deleteDocumentFile = async (id: string): Promise<void> => {
  await apiClient.delete(`/api/local-documents/${encodeURIComponent(id)}`);
};

export const renameDocumentFile = async (id: string, name: string): Promise<DocumentItem> => {
  const response = await apiClient.patch<DocumentItem>(`/api/local-documents/${encodeURIComponent(id)}`, { name });
  return response.data;
};
