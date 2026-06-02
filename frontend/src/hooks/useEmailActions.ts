import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
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

const successLabels = { archive: '已归档', flag: '已标记', read: '已标记为已读' }
const errorLabels  = { archive: '归档失败', flag: '标记失败', read: '标记已读失败' }

function makeHook(action: 'archive' | 'flag' | 'read') {
  return function useAction() {
    const qc = useQueryClient()
    return useMutation({
      mutationFn: makeAction(action),
      onSuccess: () => {
        toast.success(successLabels[action])
        qc.invalidateQueries({ queryKey: ['results'] })
      },
      onError: () => toast.error(errorLabels[action]),
    })
  }
}

export const useArchiveEmail = makeHook('archive')
export const useFlagEmail    = makeHook('flag')
export const useMarkReadEmail = makeHook('read')
