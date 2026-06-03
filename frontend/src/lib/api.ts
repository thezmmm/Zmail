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

// Shared promise so concurrent requests near expiry only fire one refresh call.
let refreshPromise: Promise<void> | null = null

api.interceptors.request.use(async config => {
  // Proactively refresh JWT when it expires within 5 minutes
  if (getToken() && isTokenExpiringSoon()) {
    if (!refreshPromise) {
      refreshPromise = axios
        .post<ApiResponse<string>>(
          `${config.baseURL}/auth/token/refresh`,
          null,
          { headers: { Authorization: `Bearer ${getToken()}` } },
        )
        .then(res => { if (res.data.data) setToken(res.data.data) })
        .catch(() => { /* let the 401 interceptor handle a failed refresh */ })
        .finally(() => { refreshPromise = null })
    }
    await refreshPromise
  }

  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      clearToken()
      if (typeof window !== 'undefined') window.location.href = '/login'
    }
    return Promise.reject(err)
  },
)

export default api