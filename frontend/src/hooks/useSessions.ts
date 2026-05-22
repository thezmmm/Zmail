import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
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
  })
}
