import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { AuthProvider, useAuth } from './AuthContext';
import api from '../services/api';

vi.mock('../services/api', () => ({
  default: {
    post: vi.fn(),
    defaults: { headers: { common: {} } },
    interceptors: { response: { use: vi.fn(() => 1), eject: vi.fn() } },
  },
}));

function wrapper({ children }) {
  return <AuthProvider>{children}</AuthProvider>;
}

beforeEach(() => {
  localStorage.clear();
  api.post.mockReset();
  api.defaults.headers.common = {};
  api.interceptors.response.use.mockClear();
  api.interceptors.response.use.mockReturnValue(1);
  api.interceptors.response.eject.mockClear();
});

describe('AuthContext', () => {
  it('starts unauthenticated when localStorage has no token', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
  });

  it('rehydrates authenticated state from localStorage without calling the API', () => {
    localStorage.setItem('foodrush_token', 'stored-token');
    localStorage.setItem('foodrush_user', JSON.stringify({ id: 1, name: 'Ada', role: 'USER' }));

    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user).toEqual({ id: 1, name: 'Ada', role: 'USER' });
    expect(api.post).not.toHaveBeenCalled();
    expect(api.defaults.headers.common.Authorization).toBe('Bearer stored-token');
  });

  it('login stores token/user, sets the auth header, and marks authenticated', async () => {
    api.post.mockResolvedValueOnce({
      data: { token: 'new-token', userId: 5, name: 'Grace', role: 'ADMIN' },
    });
    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(async () => {
      await result.current.login('grace@foodrush.com', 'password123');
    });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user).toEqual({ id: 5, name: 'Grace', role: 'ADMIN' });
    expect(localStorage.getItem('foodrush_token')).toBe('new-token');
    expect(JSON.parse(localStorage.getItem('foodrush_user'))).toEqual({ id: 5, name: 'Grace', role: 'ADMIN' });
    expect(api.defaults.headers.common.Authorization).toBe('Bearer new-token');
  });

  it('login rejects and leaves state unauthenticated on failure', async () => {
    api.post.mockRejectedValueOnce(new Error('Invalid credentials'));
    const { result } = renderHook(() => useAuth(), { wrapper });

    await expect(
      act(async () => {
        await result.current.login('bad@foodrush.com', 'wrong');
      }),
    ).rejects.toThrow('Invalid credentials');

    expect(result.current.isAuthenticated).toBe(false);
  });

  it('register does not authenticate the user', async () => {
    api.post.mockResolvedValueOnce({ data: { message: 'Registration successful' } });
    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(async () => {
      await result.current.register({ name: 'New', email: 'new@foodrush.com', password: 'password123' });
    });

    expect(result.current.isAuthenticated).toBe(false);
    expect(localStorage.getItem('foodrush_token')).toBeNull();
  });

  it('logout clears state, localStorage, and the auth header', async () => {
    api.post.mockResolvedValueOnce({
      data: { token: 'new-token', userId: 5, name: 'Grace', role: 'ADMIN' },
    });
    const { result } = renderHook(() => useAuth(), { wrapper });
    await act(async () => {
      await result.current.login('grace@foodrush.com', 'password123');
    });

    act(() => {
      result.current.logout();
    });

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
    expect(localStorage.getItem('foodrush_token')).toBeNull();
    expect(api.defaults.headers.common.Authorization).toBeUndefined();
  });

  it('registers exactly one 401 response interceptor that logs the user out', async () => {
    api.post.mockResolvedValueOnce({
      data: { token: 'new-token', userId: 5, name: 'Grace', role: 'ADMIN' },
    });
    const { result } = renderHook(() => useAuth(), { wrapper });
    await act(async () => {
      await result.current.login('grace@foodrush.com', 'password123');
    });

    expect(api.interceptors.response.use).toHaveBeenCalledTimes(1);
    const [, errorHandler] = api.interceptors.response.use.mock.calls[0];

    await act(async () => {
      await errorHandler({ response: { status: 401 } }).catch(() => {});
    });

    expect(result.current.isAuthenticated).toBe(false);
  });

  it('ejects the response interceptor on unmount', () => {
    api.interceptors.response.use.mockReturnValueOnce(42);
    const { unmount } = renderHook(() => useAuth(), { wrapper });

    unmount();

    expect(api.interceptors.response.eject).toHaveBeenCalledWith(42);
  });
});
