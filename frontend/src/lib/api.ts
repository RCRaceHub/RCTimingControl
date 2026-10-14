import axios from 'axios';
import { getAccessToken, clearAccessToken, refreshAccessToken } from './auth';

/** The shared axios instance: sends the refresh cookie, adds the bearer token, and renews it once on a 401. */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  withCredentials: true, // sends HttpOnly refresh cookie
});

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// An expired access token is renewed once and the request retried. The auth endpoints themselves are
// left alone: a wrong password is a 401 the login form must show, not a reason to refresh.
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const isAuthCall = String(originalRequest?.url ?? '').includes('/api/v1/auth/');
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !isAuthCall) {
      originalRequest._retry = true;
      try {
        await refreshAccessToken();
        return api(originalRequest);
      } catch {
        clearAccessToken();
        // A full load, not a router navigation: every page and open socket starts clean after sign-out
        const from = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = `/login?from=${from}`;
      }
    }
    return Promise.reject(error);
  }
);

export default api;

export interface AboutDto {
  version: string;
  buildTime: string;
  /** URLs other devices on the venue network can open */
  addresses: string[];
}

export async function getAboutInfo(): Promise<AboutDto> {
  const { data } = await api.get<AboutDto>('/api/v1/about');
  return data;
}
