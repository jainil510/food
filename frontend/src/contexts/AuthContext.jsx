import { createContext, useContext, useCallback, useEffect, useState } from 'react';
import api from '../services/api';

const AuthContext = createContext(undefined);

const TOKEN_KEY = 'foodrush_token';
const USER_KEY = 'foodrush_user';

function readStoredAuth() {
  const token = localStorage.getItem(TOKEN_KEY);
  const rawUser = localStorage.getItem(USER_KEY);
  if (!token || !rawUser) {
    return { token: null, user: null };
  }
  try {
    return { token, user: JSON.parse(rawUser) };
  } catch {
    return { token: null, user: null };
  }
}

function setAuthHeader(token) {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete api.defaults.headers.common.Authorization;
  }
}

export function AuthProvider({ children }) {
  const [state, setState] = useState(() => {
    const { token, user } = readStoredAuth();
    if (token) {
      setAuthHeader(token);
    }
    return { token, user, isAuthenticated: Boolean(token && user) };
  });

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setAuthHeader(null);
    setState({ token: null, user: null, isAuthenticated: false });
  }, []);

  useEffect(() => {
    const interceptorId = api.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error?.response?.status === 401) {
          logout();
        }
        return Promise.reject(error);
      },
    );
    return () => {
      api.interceptors.response.eject(interceptorId);
    };
  }, [logout]);

  const login = useCallback(async (email, password) => {
    const response = await api.post('/auth/login', { email, password });
    const { token, userId, name, role } = response.data;
    const user = { id: userId, name, role };
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    setAuthHeader(token);
    setState({ token, user, isAuthenticated: true });
    return user;
  }, []);

  const register = useCallback(async (userData) => {
    const response = await api.post('/auth/register', userData);
    return response.data;
  }, []);

  const value = { ...state, login, register, logout };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
