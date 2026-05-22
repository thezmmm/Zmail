import { useInfiniteQuery } from '@tanstack/react-query'
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
            sort: 'processedAt,desc',
            ...(category && { category }),
          },
        })
        .then(r => r.data.data!),
    initialPageParam: 0,
    getNextPageParam: page => (page.last ? undefined : page.number + 1),
  })
}