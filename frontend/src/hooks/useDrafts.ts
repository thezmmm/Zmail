import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
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
      await qc.cancelQueries({ queryKey: ['results', id] })
      const snapshot = qc.getQueryData(['drafts', 'pending'])
      const resultSnapshot = qc.getQueryData<ProcessingResult>(['results', id])
      qc.setQueryData(['drafts', 'pending'], (old: { pages: { content: ProcessingResult[] }[] }) => ({
        ...old,
        pages: old?.pages.map(p => ({
          ...p,
          content: p.content.filter(r => r.id !== id),
        })),
      }))
      qc.setQueryData<ProcessingResult>(['results', id], old =>
        old ? { ...old, draftStatus: 'SENT' } : old,
      )
      return { snapshot, resultSnapshot }
    },
    onSuccess: (data, id) => {
      qc.setQueryData<ProcessingResult>(['results', id], data)
      toast.success('草稿已发送')
    },
    onError: (_err, id, ctx) => {
      if (ctx?.snapshot) qc.setQueryData(['drafts', 'pending'], ctx.snapshot)
      if (ctx?.resultSnapshot) qc.setQueryData(['results', id], ctx.resultSnapshot)
      toast.error('发送失败，请重试')
    },
    onSettled: (_data, _err, id) => {
      qc.invalidateQueries({ queryKey: ['drafts'] })
      qc.invalidateQueries({ queryKey: ['results', id] })
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
      await qc.cancelQueries({ queryKey: ['results', id] })
      const snapshot = qc.getQueryData(['drafts', 'pending'])
      const resultSnapshot = qc.getQueryData<ProcessingResult>(['results', id])
      qc.setQueryData(['drafts', 'pending'], (old: { pages: { content: ProcessingResult[] }[] }) => ({
        ...old,
        pages: old?.pages.map(p => ({
          ...p,
          content: p.content.filter(r => r.id !== id),
        })),
      }))
      qc.setQueryData<ProcessingResult>(['results', id], old =>
        old ? { ...old, draftStatus: 'REJECTED' } : old,
      )
      return { snapshot, resultSnapshot }
    },
    onSuccess: (data, id) => {
      qc.setQueryData<ProcessingResult>(['results', id], data)
      toast.success('草稿已拒绝')
    },
    onError: (_err, id, ctx) => {
      if (ctx?.snapshot) qc.setQueryData(['drafts', 'pending'], ctx.snapshot)
      if (ctx?.resultSnapshot) qc.setQueryData(['results', id], ctx.resultSnapshot)
      toast.error('操作失败，请重试')
    },
    onSettled: (_data, _err, id) => {
      qc.invalidateQueries({ queryKey: ['drafts'] })
      qc.invalidateQueries({ queryKey: ['results', id] })
    },
  })
}
