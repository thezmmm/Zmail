import { useState, useEffect, useCallback } from 'react'
import api, { unwrap } from '@/lib/api'
import type { ApiResponse, DailyDigest } from '@/types'

export function useDigest() {
  const [digest, setDigest] = useState<DailyDigest | null>(null)
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.get<ApiResponse<DailyDigest>>('/digest/today')
      .then(r => setDigest(unwrap(r)))
      .catch(err => {
        if (err.response?.status !== 404) setError('加载日报失败')
      })
      .finally(() => setLoading(false))
  }, [])

  const generate = useCallback(async (force = false) => {
    setGenerating(true)
    setError(null)
    try {
      const r = await api.post<ApiResponse<DailyDigest>>(
        '/digest/generate',
        null,
        { params: { force } },
      )
      setDigest(unwrap(r))
    } catch {
      setError('生成日报失败，请稍后重试')
    } finally {
      setGenerating(false)
    }
  }, [])

  return { digest, loading, generating, error, generate }
}
