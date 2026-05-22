import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import type { ApiResponse, UserDto } from '@/types'

export function useUser() {
  return useQuery({
    queryKey: ['user'],
    queryFn: () =>
      api.get<ApiResponse<UserDto>>('/users/me').then(r => r.data.data!),
    staleTime: 5 * 60 * 1000,
  })
}