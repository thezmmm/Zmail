import axios from 'axios'
import { getToken, setToken, clearToken, isTokenExpiringSoon } from './auth'
import type { ApiResponse } from '@/types'

/** Unwrap the payload from an ApiResponse, throwing if the backend returned null data. */
export function unwrap<T>(r: { data: ApiResponse<T> }): T {
  if (r.data.data == null) throw new Error(r.data.error ?? 'Unexpected empty response')
  return r.data.data
}

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1',
})

const baseUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1'

// Shared promise so concurrent requests near expiry only fire one refresh call.
let refreshPromise: Promise<void> | null = null

/** Proactively refresh the JWT when it's within 5 minutes of expiry. Shared by axios and raw-fetch callers (e.g. SSE). */
export async function refreshTokenIfNeeded(): Promise<void> {
  if (!getToken() || !isTokenExpiringSoon()) return
  if (!refreshPromise) {
    refreshPromise = axios
      .post<ApiResponse<string>>(
        `${baseUrl}/auth/token/refresh`,
        null,
        { headers: { Authorization: `Bearer ${getToken()}` } },
      )
      .then(res => { if (res.data.data) setToken(res.data.data) })
      .catch(() => { /* let the 401 handling at the call site take over */ })
      .finally(() => { refreshPromise = null })
  }
  await refreshPromise
}

/** Clear the stored token and send the user back to /login — same effect as the axios 401 interceptor below. */
export function handleUnauthorized(): void {
  clearToken()
  if (typeof window !== 'undefined') window.location.href = '/login'
}

api.interceptors.request.use(async config => {
  await refreshTokenIfNeeded()
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) handleUnauthorized()
    return Promise.reject(err)
  },
)

export default api