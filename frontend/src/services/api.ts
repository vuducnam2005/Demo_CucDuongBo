import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { normalizePagedResponse, type PagedResponse } from './pagination';

export type { PagedResponse } from './pagination';

// Token storage key
const TOKEN_KEY = 'kcht_access_token';

export const getStoredToken = (): string | null => {
  return localStorage.getItem(TOKEN_KEY);
};

export const setStoredToken = (token: string | null): void => {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
};

// Base Axios instance
export const apiClient = axios.create({
  baseURL: (import.meta.env.VITE_API_BASE_URL as string) || '',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
  withCredentials: true, // Send HttpOnly refresh cookie
});

// Request interceptor to attach JWT token
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getStoredToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value?: unknown) => void;
  reject: (reason?: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

// Response interceptor to handle 401 & token refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // Don't intercept refresh or login calls
    if (
      originalRequest.url?.includes('/api/auth/login') ||
      originalRequest.url?.includes('/api/auth/refresh')
    ) {
      return Promise.reject(error);
    }

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Call refresh endpoint (backend reads HttpOnly cookie or token)
        const response = await axios.post('/api/auth/refresh', {}, { withCredentials: true });
        const newToken = response.data?.accessToken;

        if (newToken) {
          setStoredToken(newToken);
          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
          }
          processQueue(null, newToken);
          return apiClient(originalRequest);
        }
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        setStoredToken(null);
        // Dispatch custom event for AuthContext to detect logout
        window.dispatchEvent(new CustomEvent('auth:expired'));
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

// Common DTOs
export interface UserSummary {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: string;
  roleName: string;
  organizationId?: string | null;
  branchId?: string | null;
  active: boolean;
  permissions: string[];
}

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  fullName: string;
  roleCode: string;
  organizationId?: string;
  branchId?: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken?: string;
  user: UserSummary;
}

export interface DatasetItem {
  datasetKey: string;
  datasetName: string;
  kind: string;
  endpoint: string;
  sourceFile: string;
  totalRecords: number;
  active: boolean;
}

export interface AuditLogItem {
  id: number;
  userId: number;
  username: string;
  action: string;
  entityType: string;
  entityId: string;
  oldValues?: string;
  newValues?: string;
  ipAddress?: string;
  userAgent?: string;
  createdAt: string;
}

export interface HealthResponse {
  status: string;
  components?: Record<string, { status: string; details?: Record<string, unknown> }>;
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isOptionalString = (value: unknown): value is string | null | undefined =>
  value === undefined || value === null || typeof value === 'string';

export const isUserSummary = (value: unknown): value is UserSummary => {
  if (!isRecord(value)) return false;

  return (
    typeof value.id === 'number' &&
    typeof value.username === 'string' &&
    typeof value.email === 'string' &&
    typeof value.fullName === 'string' &&
    typeof value.role === 'string' &&
    typeof value.roleName === 'string' &&
    isOptionalString(value.organizationId) &&
    isOptionalString(value.branchId) &&
    typeof value.active === 'boolean' &&
    Array.isArray(value.permissions) &&
    value.permissions.every((permission) => typeof permission === 'string')
  );
};

export const getHttpStatus = (error: unknown): number | undefined => {
  if (axios.isAxiosError(error)) return error.response?.status;
  if (!isRecord(error) || !isRecord(error.response)) return undefined;
  return typeof error.response.status === 'number' ? error.response.status : undefined;
};

export const getApiErrorMessage = (error: unknown): string => {
  const responseData = axios.isAxiosError(error) && isRecord(error.response?.data)
    ? error.response.data
    : isRecord(error) && isRecord(error.response) && isRecord(error.response.data)
      ? error.response.data
      : undefined;
  const isSafeUserMessage = (value: string): boolean => {
    const normalized = value.trim();
    if (!normalized || normalized.length > 500) return false;
    return !/(?:stack trace|traceback|exception|node_modules|[A-Za-z]:\\|\/workspace\/|select\s+.+\s+from|\binternal\s+(?:sql|query)\b|\b(?:database|jdbc|sqlstate|relation|column|postgres|hibernate)\b|password|secret|jwt|token)/i.test(normalized);
  };

  if (responseData) {
    const responseMessage = responseData.message;
    const details = responseData.errors;
    if (typeof responseMessage === 'string' && isSafeUserMessage(responseMessage)) {
      if (Array.isArray(details)) {
        const validDetails = details.filter(
          (detail): detail is string => typeof detail === 'string' && isSafeUserMessage(detail)
        );
        if (validDetails.length > 0) return `${responseMessage}: ${validDetails.join('; ')}`;
      }
      return responseMessage;
    }
  }
  if (error instanceof Error && isSafeUserMessage(error.message)) return error.message;
  return 'Không thể kết nối tới máy chủ. Vui lòng thử lại.';
};

export interface ApiErrorPresentation {
  status?: number;
  title: string;
  description: string;
  retryable: boolean;
}

export const getApiErrorPresentation = (error: unknown): ApiErrorPresentation => {
  const status = getHttpStatus(error);
  const serverMessage = getApiErrorMessage(error);

  switch (status) {
    case 401:
      return {
        status,
        title: 'Phiên đăng nhập đã hết hạn',
        description: 'Vui lòng đăng nhập lại để tiếp tục sử dụng hệ thống.',
        retryable: false,
      };
    case 403:
      return {
        status,
        title: 'Không có quyền truy cập',
        description: 'Tài khoản hiện tại không được cấp quyền thực hiện yêu cầu này.',
        retryable: false,
      };
    case 404:
      return {
        status,
        title: 'Không tìm thấy dữ liệu',
        description: serverMessage,
        retryable: false,
      };
    case 409:
      return {
        status,
        title: 'Dữ liệu đã thay đổi',
        description: serverMessage,
        retryable: false,
      };
    case 422:
      return {
        status,
        title: 'Dữ liệu chưa đáp ứng quy tắc nghiệp vụ',
        description: serverMessage,
        retryable: false,
      };
    case 500:
      return {
        status,
        title: 'Hệ thống đang gặp sự cố',
        description: 'Máy chủ chưa thể xử lý yêu cầu. Vui lòng thử lại sau.',
        retryable: true,
      };
    default:
      return {
        status,
        title: status ? `Không thể hoàn tất yêu cầu (HTTP ${status})` : 'Không thể kết nối tới máy chủ',
        description: serverMessage,
        retryable: status === undefined || status >= 500,
      };
  }
};

export interface ApiFieldError {
  field: string;
  message: string;
}

export const getApiFieldErrors = (error: unknown): ApiFieldError[] => {
  const responseData = axios.isAxiosError(error) && isRecord(error.response?.data)
    ? error.response.data
    : isRecord(error) && isRecord(error.response) && isRecord(error.response.data)
      ? error.response.data
      : undefined;
  const details = responseData?.errors;
  if (!Array.isArray(details)) return [];

  return details
    .filter((detail): detail is string => typeof detail === 'string')
    .map((detail) => {
      const separator = detail.indexOf(':');
      if (separator <= 0) return null;
      const field = detail.slice(0, separator).trim();
      const message = detail.slice(separator + 1).trim();
      return field && message ? { field, message } : null;
    })
    .filter((detail): detail is ApiFieldError => detail !== null);
};

export const shouldRetryQuery = (failureCount: number, error: unknown): boolean => {
  const status = getHttpStatus(error);
  if (status !== undefined && status < 500) return false;
  return failureCount < 1 && (status === undefined || status >= 500);
};

// API methods
export const fetchHealth = async (): Promise<HealthResponse> => {
  const response = await apiClient.get<HealthResponse>('/actuator/health');
  return response.data;
};

export const fetchDatasets = async (
  page = 0,
  size = 20,
  kind?: string
): Promise<PagedResponse<DatasetItem>> => {
  const response = await apiClient.get<PagedResponse<DatasetItem>>('/api/datasets', {
    params: { page, size, kind },
  });
  return response.data;
};

export const fetchUsers = async ({
  page = 0,
  size = 10,
  q,
}: {
  page?: number;
  size?: number;
  q?: string;
} = {}): Promise<PagedResponse<UserSummary>> => {
  const response = await apiClient.get<unknown>('/api/auth/users', {
    params: { page, size, q: q?.trim() || undefined },
  });
  return normalizePagedResponse(response.data, isUserSummary, 'GET /api/auth/users');
};

export const createUser = async (request: CreateUserRequest): Promise<UserSummary> => {
  const response = await apiClient.post<UserSummary>('/api/auth/users', request);
  if (response.status !== 201) {
    throw new Error(`POST /api/auth/users trả về HTTP ${response.status}, không phải 201.`);
  }
  if (!isUserSummary(response.data)) {
    throw new Error('POST /api/auth/users trả về hồ sơ người dùng không đúng contract.');
  }
  return response.data;
};

export const fetchAuditLogs = async (
  page = 0,
  size = 20,
  username?: string,
  action?: string,
  entityType?: string
): Promise<PagedResponse<AuditLogItem>> => {
  const response = await apiClient.get<PagedResponse<AuditLogItem>>('/api/audit-logs', {
    params: { page, size, username, action, entityType },
  });
  return response.data;
};

export * from './dashboardApi';
export * from './assetApi';
export * from './gisApi';
export * from './reportApi';
export * from './catalogApi';
export * from './documentApi';
