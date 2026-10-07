import { apiClient, setStoredToken, AuthResponse, UserSummary } from './api';

export interface LoginParams {
  username: string;
  password: string;
}

export const loginApi = async (params: LoginParams): Promise<AuthResponse> => {
  const response = await apiClient.post<AuthResponse>('/api/auth/login', params);
  if (response.data?.accessToken) {
    setStoredToken(response.data.accessToken);
  }
  return response.data;
};

export const logoutApi = async (): Promise<void> => {
  try {
    await apiClient.post('/api/auth/logout', {});
  } finally {
    setStoredToken(null);
  }
};

export const getMeApi = async (): Promise<UserSummary> => {
  const response = await apiClient.get<UserSummary>('/api/auth/me');
  return response.data;
};

export const refreshApi = async (): Promise<AuthResponse> => {
  const response = await apiClient.post<AuthResponse>('/api/auth/refresh', {});
  if (response.data?.accessToken) {
    setStoredToken(response.data.accessToken);
  }
  return response.data;
};
