import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api, { unwrap } from '@/lib/api'
import type { ApiResponse, Category, PagedResponse, ProcessingResult } from '@/types'

/**
 * Page-based (not infinite-scroll) email list. There's no standalone "sync" button —
 * the backend syncs/backfills just enough to satisfy whichever page is requested here.
 */
export function useResults(category: Category | undefined, page: number) {
  return useQuery({
    queryKey: ['results', category ?? 'all', page],
    queryFn: () =>
      api
        .get<ApiResponse<PagedResponse<ProcessingResult>>>('/results', {
          params: {
            page,
            size: 20,
            sort: 'receivedAt,desc',
            ...(category && { category }),
          },
        })
        .then(unwrap),
    placeholderData: prev => prev,
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
      qc.invalidateQueries({ queryKey: ['draft', 'by-result', resultId] })
    },
  })
}