import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
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
            ...(category && { category }),
          },
        })
        .then(r => r.data.data!),
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
        .then(r => r.data.data!),
    onSuccess: data => {
      qc.setQueryData(['results', resultId], data)
    },
  })
}

export function useGenerateDraft(resultId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      api
        .post<ApiResponse<ProcessingResult>>(`/results/${resultId}/draft`)
        .then(r => r.data.data!),
    onSuccess: data => {
      qc.setQueryData(['results', resultId], data)
      qc.invalidateQueries({ queryKey: ['drafts'] })
    },
  })
}