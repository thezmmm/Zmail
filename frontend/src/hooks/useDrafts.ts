import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import api, { unwrap } from '@/lib/api'
import type { ApiResponse, CreateDraftRequest, Draft, PagedResponse } from '@/types'

export function usePendingDrafts() {
  return useInfiniteQuery({
    queryKey: ['drafts', 'pending'],
    queryFn: ({ pageParam }) =>
      api
        .get<ApiResponse<PagedResponse<Draft>>>('/drafts/pending', {
          params: { page: pageParam, size: 20 },
        })
        .then(unwrap),
    initialPageParam: 0,
    getNextPageParam: page => (page.last ? undefined : page.number + 1),
  })
}

export function useCreateDraft() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateDraftRequest) =>
      api
        .post<ApiResponse<Draft>>('/drafts', req)
        .then(unwrap),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['drafts'] })
      toast.success('草稿已保存')
    },
    onError: () => toast.error('保存草稿失败'),
  })
}

export function useUpdateDraft() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body, subject }: { id: string; body?: string; subject?: string }) =>
      api
        .patch<ApiResponse<Draft>>(`/drafts/${id}`, { body, subject })
        .then(unwrap),
    onSuccess: (data) => {
      qc.setQueryData<Draft>(['draft', data.id], data)
    },
    onError: () => toast.error('保存草稿失败'),
  })
}

export function useApproveDraft() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body?: string }) =>
      api
        .post<ApiResponse<Draft>>(`/drafts/${id}/approve`, body != null ? { body } : null)
        .then(unwrap),
    onMutate: async ({ id }) => {
      await qc.cancelQueries({ queryKey: ['drafts', 'pending'] })
      const snapshot = qc.getQueryData(['drafts', 'pending'])
      qc.setQueryData(['drafts', 'pending'], (old: { pages: { content: Draft[] }[] }) => ({
        ...old,
        pages: old?.pages.map(p => ({
          ...p,
          content: p.content.filter(d => d.id !== id),
        })),
      }))
      return { snapshot }
    },
    onSuccess: () => toast.success('草稿已发送'),
    onError: (_err, _vars, ctx) => {
      if (ctx?.snapshot) qc.setQueryData(['drafts', 'pending'], ctx.snapshot)
      toast.error('发送失败，请重试')
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['drafts'] }),
  })
}

export function useRejectDraft() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      api
        .post<ApiResponse<Draft>>(`/drafts/${id}/reject`)
        .then(unwrap),
    onMutate: async id => {
      await qc.cancelQueries({ queryKey: ['drafts', 'pending'] })
      const snapshot = qc.getQueryData(['drafts', 'pending'])
      qc.setQueryData(['drafts', 'pending'], (old: { pages: { content: Draft[] }[] }) => ({
        ...old,
        pages: old?.pages.map(p => ({
          ...p,
          content: p.content.filter(d => d.id !== id),
        })),
      }))
      return { snapshot }
    },
    onSuccess: () => toast.success('草稿已拒绝'),
    onError: (_err, _id, ctx) => {
      if (ctx?.snapshot) qc.setQueryData(['drafts', 'pending'], ctx.snapshot)
      toast.error('操作失败，请重试')
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['drafts'] }),
  })
}
