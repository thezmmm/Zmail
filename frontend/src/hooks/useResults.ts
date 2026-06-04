import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api, { unwrap } from '@/lib/api'
import type { ApiResponse, Category, PagedResponse, ProcessingResult } from '@/types'

export function useResults(category?: Category) {
  return useInfiniteQuery({
    queryKey: ['results', category ?? 'all'],
    queryFn: ({ pageParam }) =>
      api
        .get<ApiResponse<PagedResponse<ProcessingResult>>>('/results', {
          params: {
            page: pageParam,
            size: 20,
            sort: 'receivedAt,desc',
            ...(category && { category }),
          },
        })
        .then(unwrap),
    initialPageParam: 0,
    getNextPageParam: page => (page.last ? undefined : page.number + 1),
  })
}

export function useAnalyzeResult(resultId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      api
        .post<ApiResponse<ProcessingResult>>(`/results/${resultId}/analyze`)
        .then(unwrap),
    onSuccess: data => {
      qc.setQueryData(['results', resultId], data)
    },
    onSettled: () => {
      // Refresh paginated list regardless of outcome — server may have processed
      // the request even if the response timed out on the client
      qc.invalidateQueries({ queryKey: ['results'] })
    },
  })
}

export function useGenerateDraft(resultId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      api
        .post<ApiResponse<ProcessingResult>>(`/results/${resultId}/draft`)
        .then(unwrap),
    onSuccess: data => {
      qc.setQueryData(['results', resultId], data)
      qc.invalidateQueries({ queryKey: ['drafts'] })
    },
  })
}