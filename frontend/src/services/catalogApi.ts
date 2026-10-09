import { apiClient } from './api';

export interface CatalogSummary {
  catalogCode: string;
  catalogName: string;
  description: string;
  itemCount: number;
  sourceType: string;
  isEditable: boolean;
  groupKey?: string;
  groupName?: string;
  icon?: string;
}


export interface ReferenceCatalogItem {
  catalogCode: string;
  itemCode: string;
  itemName: string;
  parentCode?: string | null;
  sortOrder?: number;
  active?: boolean;
  extraAttributes?: Record<string, unknown>;
}

export interface PagedCatalogItems {
  content: ReferenceCatalogItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const fetchAllCatalogs = async (): Promise<CatalogSummary[]> => {
  const response = await apiClient.get<CatalogSummary[]>('/api/catalogs');
  return response.data;
};

export const fetchCatalogItems = async (
  catalogCode: string,
  params?: { page?: number; size?: number; keyword?: string; q?: string; parentCode?: string }
): Promise<PagedCatalogItems> => {
  const queryParams = {
    page: params?.page ?? 0,
    size: params?.size ?? 20,
    q: params?.q ?? params?.keyword ?? '',
    parentCode: params?.parentCode,
  };
  const response = await apiClient.get<PagedCatalogItems>(`/api/catalogs/${encodeURIComponent(catalogCode)}`, {
    params: queryParams,
  });
  return response.data;
};

export const fetchCatalogItem = async (
  catalogCode: string,
  itemCode: string
): Promise<ReferenceCatalogItem> => {
  const response = await apiClient.get<ReferenceCatalogItem>(
    `/api/catalogs/${encodeURIComponent(catalogCode)}/${encodeURIComponent(itemCode)}`
  );
  return response.data;
};

export const createCatalogItem = async (
  catalogCode: string,
  data: ReferenceCatalogItem
): Promise<ReferenceCatalogItem> => {
  const response = await apiClient.post<ReferenceCatalogItem>(
    `/api/catalogs/${encodeURIComponent(catalogCode)}`,
    data
  );
  return response.data;
};

export const updateCatalogItem = async (
  catalogCode: string,
  itemCode: string,
  data: ReferenceCatalogItem
): Promise<ReferenceCatalogItem> => {
  const response = await apiClient.put<ReferenceCatalogItem>(
    `/api/catalogs/${encodeURIComponent(catalogCode)}/${encodeURIComponent(itemCode)}`,
    data
  );
  return response.data;
};

export const deleteCatalogItem = async (
  catalogCode: string,
  itemCode: string
): Promise<void> => {
  await apiClient.delete(`/api/catalogs/${encodeURIComponent(catalogCode)}/${encodeURIComponent(itemCode)}`);
};
