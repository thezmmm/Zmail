import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import type { ApiResponse, PagedResponse, ProcessingResult } from '@/types'

export function usePendingDrafts() {
  return useInfiniteQuery({
    queryKey: ['drafts', 'pending'],
    queryFn: ({ pageParam }) =>
      api
        .get<ApiResponse<PagedResponse<ProcessingResult>>>('/drafts/pending', {
          params: { page: pageParam, size: 20 },
        })
        .then(r => r.data.data!),
    initialPageParam: 0,
    getNextPageParam: page => (page.last ? undefined : page.number + 1),
  })
}

export function useApproveDraft() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      api
        .post<ApiResponse<ProcessingResult>>(`/drafts/${id}/approve`)
        .then(r => r.data.data!),
    onMutate: async id => {
      await qc.cancelQueries({ queryKey: ['drafts', 'pending'] })
      const snapshot = qc.getQueryData(['drafts', 'pending'])
      qc.setQueryData(['drafts', 'pending'], (old: { pages: { content: ProcessingResult[] }[] }) => ({
        ...old,
        pages: old?.pages.map(p => ({
          ...p,
          content: p.content.filter(r => r.id !== id),
        })),
      }))
      return { snapshot }
    },
    onError: (_err, _id, ctx) => {
      if (ctx?.snapshot) qc.setQueryData(['drafts', 'pending'], ctx.snapshot)
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['drafts'] })
    },
  })
}

export function useRejectDraft() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      api
        .post<ApiResponse<ProcessingResult>>(`/drafts/${id}/reject`)
        .then(r => r.data.data!),
    onMutate: async id => {
      await qc.cancelQueries({ queryKey: ['drafts', 'pending'] })
      const snapshot = qc.getQueryData(['drafts', 'pending'])
      qc.setQueryData(['drafts', 'pending'], (old: { pages: { content: ProcessingResult[] }[] }) => ({
        ...old,
        pages: old?.pages.map(p => ({
          ...p,
          content: p.content.filter(r => r.id !== id),
        })),
      }))
      return { snapshot }
    },
    onError: (_err, _id, ctx) => {
      if (ctx?.snapshot) qc.setQueryData(['drafts', 'pending'], ctx.snapshot)
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['drafts'] })
    },
  })
}
