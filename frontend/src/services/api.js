// ============================================
// api.js - Axios Instance with Auth Interceptor
// ============================================
// Creates a reusable Axios instance that auto-attaches
// the JWT token to every request.
// Reference: axios.create(), interceptors - reference-javascript.md
// ============================================

import axios from 'axios';

// Use a relative base path in dev so Vite's /api proxy forwards to the
// running backend (see vite.config.js). Override with VITE_API_URL in other
// environments (e.g. production or Docker).
const API = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
});

// Attach JWT token to every request
API.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Single-flight token refresh: when any request returns 401, attempt one
// refresh with the stored refresh token, then retry the original request.
// Concurrent 401s share the same in-flight refresh promise.
let refreshPromise = null;

API.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    const isUnauthorized = error.response?.status === 401;
    const isRefreshCall = originalRequest?.url?.includes('/auth/refresh');
    // Don't attempt a token refresh for login/register endpoints — that would swallow 
    // the real error (e.g. "Invalid email or password").
    const isAuthCall = originalRequest?.url?.includes('/auth/login') || originalRequest?.url?.includes('/auth/register');
    const alreadyRetried = originalRequest?._retry;

    if (!isUnauthorized || isRefreshCall || isAuthCall || alreadyRetried) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      if (!refreshPromise) {
        const storedRefreshToken = localStorage.getItem('refreshToken');
        if (!storedRefreshToken) {
          window.dispatchEvent(new Event('auth:logout'));
          throw new Error('No refresh token available');
        }
        refreshPromise = API.post('/auth/refresh', { refreshToken: storedRefreshToken })
          .then((res) => res.data.data);
      }

      const data = await refreshPromise;
      localStorage.setItem('token', data.accessToken);
      localStorage.setItem('refreshToken', data.refreshToken);

      originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
      return API(originalRequest);
    } catch (refreshError) {
      // Refresh failed — force logout state.
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      window.dispatchEvent(new Event('auth:logout'));
      return Promise.reject(refreshError);
    } finally {
      refreshPromise = null;
    }
  }
);

export default API;
