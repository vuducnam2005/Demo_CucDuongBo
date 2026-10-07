import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { UserSummary, AuthResponse, getHttpStatus, getStoredToken, setStoredToken } from '../services/api';
import { loginApi, logoutApi, getMeApi, LoginParams } from '../services/authApi';

interface AuthContextType {
  user: UserSummary | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (params: LoginParams) => Promise<AuthResponse>;
  logout: () => Promise<void>;
  hasRole: (roles: string | string[]) => boolean;
  hasPermission: (resource: string, action: string) => boolean;
  refreshUserProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const PROFILE_CACHE_KEY = 'kcht_user_profile';
export const PROFILE_CACHE_VERSION_KEY = 'kcht_profile_cache_version';
export const CURRENT_PROFILE_CACHE_VERSION = 'v2_utf8';

export const isProfileValid = (candidate: unknown): candidate is UserSummary => {
  if (!candidate || typeof candidate !== 'object') return false;
  const u = candidate as Record<string, unknown>;
  if (!u.id || !u.username) return false;
  // Kiểm tra chuỗi bị lỗi encoding (dấu ? thay thế cho tiếng Việt như Qu???n tr??? vi??n)
  if (typeof u.fullName === 'string' && u.fullName.includes('?')) {
    return false;
  }
  if (typeof u.roleName === 'string' && u.roleName.includes('?')) {
    return false;
  }
  return true;
};

export const getInitialCachedUser = (): UserSummary | null => {
  try {
    const cachedUser = localStorage.getItem(PROFILE_CACHE_KEY);
    if (!cachedUser) return null;

    const parsed = JSON.parse(cachedUser);
    if (!isProfileValid(parsed)) {
      localStorage.removeItem(PROFILE_CACHE_KEY);
      localStorage.removeItem(PROFILE_CACHE_VERSION_KEY);
      return null;
    }
    if (localStorage.getItem(PROFILE_CACHE_VERSION_KEY) !== CURRENT_PROFILE_CACHE_VERSION) {
      localStorage.setItem(PROFILE_CACHE_VERSION_KEY, CURRENT_PROFILE_CACHE_VERSION);
    }
    return parsed;
  } catch {
    localStorage.removeItem(PROFILE_CACHE_KEY);
    localStorage.removeItem(PROFILE_CACHE_VERSION_KEY);
    return null;
  }
};

const saveStoredProfile = (profile: UserSummary) => {
  if (isProfileValid(profile)) {
    localStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify(profile));
    localStorage.setItem(PROFILE_CACHE_VERSION_KEY, CURRENT_PROFILE_CACHE_VERSION);
  }
};

const clearStoredProfile = () => {
  localStorage.removeItem(PROFILE_CACHE_KEY);
  localStorage.removeItem(PROFILE_CACHE_VERSION_KEY);
};

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserSummary | null>(getInitialCachedUser);
  const [token, setToken] = useState<string | null>(getStoredToken());
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const refreshUserProfile = useCallback(async () => {
    try {
      const profile = await getMeApi();
      setUser(profile);
      saveStoredProfile(profile);
    } catch (err: unknown) {
      // Only invalidate session on explicit 401 Unauthorized, not on network hiccups
      if (getHttpStatus(err) === 401) {
        setUser(null);
        setToken(null);
        setStoredToken(null);
        clearStoredProfile();
      }
    }
  }, []);

  // Check auth session on startup
  useEffect(() => {
    const initAuth = async () => {
      const currentToken = getStoredToken();
      if (currentToken) {
        setToken(currentToken);
        await refreshUserProfile();
      } else {
        // Try to recover via HttpOnly refresh cookie if available
        try {
          await refreshUserProfile();
          setToken(getStoredToken());
        } catch {
          setUser(null);
          setToken(null);
        }
      }
      setIsLoading(false);
    };

    initAuth();

    // Listen to token expiration event from axios interceptor
    const handleExpired = () => {
      setUser(null);
      setToken(null);
      setStoredToken(null);
      clearStoredProfile();
    };

    window.addEventListener('auth:expired', handleExpired);
    return () => {
      window.removeEventListener('auth:expired', handleExpired);
    };
  }, [refreshUserProfile]);

  const login = async (params: LoginParams): Promise<AuthResponse> => {
    const data = await loginApi(params);
    setToken(data.accessToken);
    setUser(data.user);
    saveStoredProfile(data.user);
    return data;
  };

  const logout = async (): Promise<void> => {
    try {
      await logoutApi();
    } catch {
      // Ignore network errors on logout
    } finally {
      setUser(null);
      setToken(null);
      setStoredToken(null);
      clearStoredProfile();
    }
  };

  const hasRole = useCallback(
    (roles: string | string[]): boolean => {
      if (!user || !user.role) return false;
      const roleList = Array.isArray(roles) ? roles : [roles];
      // Normalize role names (e.g. ROLE_ADMIN vs admin)
      return roleList.some(
        (r) =>
          r.toUpperCase() === user.role.toUpperCase() ||
          `ROLE_${r.toUpperCase()}` === user.role.toUpperCase() ||
          r.toUpperCase() === `ROLE_${user.role.toUpperCase()}`
      );
    },
    [user]
  );

  const hasPermission = useCallback(
    (resource: string, action: string): boolean => {
      if (!user) return false;
      if (user.role === 'ROLE_ADMIN' || user.role === 'admin') return true;

      const target = `${resource}:${action}`.toLowerCase();
      const wildcard = `${resource}:all`.toLowerCase();

      return Boolean(
        user.permissions &&
          user.permissions.some((p) => {
            const normalized = p.toLowerCase();
            return normalized === target || normalized === wildcard || normalized === '*';
          })
      );
    },
    [user]
  );

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: Boolean(user && token),
        isLoading,
        login,
        logout,
        hasRole,
        hasPermission,
        refreshUserProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
