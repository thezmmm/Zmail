import { useQuery } from '@tanstack/react-query'
import api, { unwrap } from '@/lib/api'
import type { ApiResponse, SimilarEmailResult } from '@/types'

export function useEmailSearch(query: string) {
  return useQuery({
    queryKey: ['email-search', query],
    queryFn: () =>
      api
        .get<ApiResponse<SimilarEmailResult[]>>('/results/search', {
          params: { q: query, topK: 10 },
        })
        .then(unwrap),
    enabled: query.trim().length > 0,
    staleTime: 30_000,
    retry: 0,
  })
}
