import axios from 'axios'
import { getToken, setToken, clearToken, isTokenExpiringSoon } from './auth'
import type { ApiResponse } from '@/types'

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1',
})

api.interceptors.request.use(async config => {
  // Proactively refresh JWT when it expires within 5 minutes
  if (getToken() && isTokenExpiringSoon()) {
    try {
      const res = await axios.post<ApiResponse<string>>(
        `${config.baseURL}/auth/token/refresh`,
        null,
        { headers: { Authorization: `Bearer ${getToken()}` } },
      )
      if (res.data.data) setToken(res.data.data)
    } catch {
      // If refresh fails, let the request proceed — the 401 interceptor handles expiry
    }
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
      window.location.href = '/login'
    }
    return Promise.reject(err)
  },
)

export default api