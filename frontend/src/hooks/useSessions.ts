import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import api from '@/lib/api'
import type { AgentMessage, AgentSession, ApiResponse } from '@/types'

export function useSessions() {
  return useQuery({
    queryKey: ['sessions'],
    queryFn: () =>
      api
        .get<ApiResponse<AgentSession[]>>('/agent/sessions')
        .then(r => r.data.data ?? []),
  })
}

export function useMessages(sessionId: string | null) {
  return useQuery({
    queryKey: ['messages', sessionId],
    queryFn: () =>
      api
        .get<ApiResponse<AgentMessage[]>>(`/agent/sessions/${sessionId}/messages`)
        .then(r => r.data.data ?? []),
    enabled: !!sessionId,
  })
}

export function useCreateSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      api
        .post<ApiResponse<AgentSession>>('/agent/sessions')
        .then(r => r.data.data!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sessions'] })
    },
    onError: () => toast.error('创建会话失败'),
  })
}

export function useDeleteSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: string) =>
      api.delete(`/agent/sessions/${sessionId}`).then(() => undefined),
    onMutate: async sessionId => {
      await qc.cancelQueries({ queryKey: ['sessions'] })
      const snapshot = qc.getQueryData<AgentSession[]>(['sessions'])
      qc.setQueryData<AgentSession[]>(['sessions'], prev =>
        prev?.filter(s => s.id !== sessionId),
      )
      return { snapshot }
    },
    onSuccess: () => toast.success('会话已删除'),
    onError: (_err, _id, ctx) => {
      if (ctx?.snapshot) qc.setQueryData(['sessions'], ctx.snapshot)
      toast.error('删除会话失败')
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['sessions'] })
    },
  })
}
