import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import api, { unwrap } from '@/lib/api'
import type { AccountDto, ApiResponse, ReauthDto, SyncStatus } from '@/types'

export function useSyncEmails() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      api.post<ApiResponse<number>>('/accounts/sync').then(unwrap),
    onSuccess: count => {
      toast.success(count > 0 ? `同步完成，新增 ${count} 封邮件` : '已是最新，无新邮件')
      qc.invalidateQueries({ queryKey: ['results'] })
    },
    onError: (err: any) => {
      const msg = err?.response?.data?.error
      toast.error(msg ?? '同步失败，请稍后再试')
    },
  })
}

export function useAccounts() {
  return useQuery({
    queryKey: ['accounts'],
    queryFn: () =>
      api
        .get<ApiResponse<AccountDto[]>>('/accounts')
        .then(r => r.data.data ?? []),
  })
}

export function useSyncStatus() {
  return useQuery({
    queryKey: ['accounts', 'sync-status'],
    queryFn: () =>
      api
        .get<ApiResponse<SyncStatus>>('/accounts/sync-status')
        .then(r => r.data.data ?? 'DONE'),
    refetchInterval: (query) =>
      query.state.data === 'RUNNING' ? 3_000 : false,
  })
}

export function useDeleteAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (accountId: string) => api.delete(`/accounts/${accountId}`),
    onMutate: async accountId => {
      await qc.cancelQueries({ queryKey: ['accounts'] })
      const snapshot = qc.getQueryData<AccountDto[]>(['accounts'])
      qc.setQueryData<AccountDto[]>(['accounts'], prev =>
        prev?.filter(a => a.id !== accountId),
      )
      return { snapshot }
    },
    onSuccess: () => toast.success('账户已移除'),
    onError: (_err, _id, ctx) => {
      if (ctx?.snapshot) qc.setQueryData(['accounts'], ctx.snapshot)
      toast.error('移除账户失败')
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['accounts'] })
    },
  })
}

/** Get the reauth URL for an existing account (uses the existing OAuth login path). */
export function useReauthAccount() {
  return useMutation({
    mutationFn: (accountId: string) =>
      api
        .get<ApiResponse<ReauthDto>>(`/accounts/${accountId}/reauth`)
        .then(unwrap),
  })
}

/** Fetch the Gmail OAuth URL for linking a brand-new account (requires auth). */
export function useGmailLinkUrl() {
  return useMutation({
    mutationFn: () =>
      api
        .post<ApiResponse<string>>('/auth/gmail/link-init')
        .then(unwrap),
  })
}

/** Fetch the Outlook OAuth URL for linking a brand-new account (requires auth). */
export function useMsgraphLinkUrl() {
  return useMutation({
    mutationFn: () =>
      api
        .post<ApiResponse<string>>('/auth/msgraph/link-init')
        .then(unwrap),
  })
}
