import { useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import type { ApiResponse } from '@/types'

interface ActionParams {
  emailProviderId: string
  accountId: string
}

function makeAction(action: 'archive' | 'flag' | 'read') {
  return ({ emailProviderId, accountId }: ActionParams) =>
    api
      .post<ApiResponse<void>>(`/emails/${emailProviderId}/${action}`, null, {
        params: { accountId },
      })
      .then(() => undefined)
}

export function useArchiveEmail() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: makeAction('archive'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['results'] }),
  })
}

export function useFlagEmail() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: makeAction('flag'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['results'] }),
  })
}

export function useMarkReadEmail() {
  return useMutation({ mutationFn: makeAction('read') })
}
