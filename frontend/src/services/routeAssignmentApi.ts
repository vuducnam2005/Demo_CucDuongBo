import { apiClient } from './api';

export interface RouteAssignment {
  id: number;
  userId: number;
  username: string;
  fullName: string;
  branchId: string;
  routeName: string;
  chainageFromM: number | null;
  chainageToM: number | null;
  purpose: 'DEMO';
  assignedAt: string;
}

export interface RouteAssignmentRequest {
  userId: number;
  routeName: string;
  chainageFromM?: number | null;
  chainageToM?: number | null;
}

export interface RouteAssignmentPage {
  content: RouteAssignment[];
  totalElements: number;
  page: number;
  size: number;
}

export const fetchRouteAssignments = async (page = 0, size = 20) =>
  (await apiClient.get<RouteAssignmentPage>('/api/vroad/route-assignments', { params: { page, size } })).data;

export const fetchAssignedRouteOptions = async (q = '') =>
  (await apiClient.get<{ routeName: string; records: number }[]>('/api/vroad/route-assignments/routes', { params: { q } })).data;

export const saveRouteAssignment = async (request: RouteAssignmentRequest) =>
  (await apiClient.post<RouteAssignment>('/api/vroad/route-assignments', request)).data;

export const revokeRouteAssignment = async (id: number) =>
  apiClient.delete(`/api/vroad/route-assignments/${id}`);
