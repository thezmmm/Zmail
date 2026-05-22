'use client'

import { useEffect } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useQueryClient } from '@tanstack/react-query'
import { Suspense } from 'react'

function LinkCallbackHandler() {
  const router      = useRouter()
  const params      = useSearchParams()
  const queryClient = useQueryClient()

  useEffect(() => {
    // Invalidate accounts cache so the accounts page shows the new entry immediately
    queryClient.invalidateQueries({ queryKey: ['accounts'] })

    const t = setTimeout(() => router.replace('/accounts'), 1500)
    return () => clearTimeout(t)
  }, [queryClient, router])

  const provider = params.get('provider') === 'gmail' ? 'Gmail' : 'Outlook'

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-3">
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-green-900/40">
        <svg className="h-5 w-5 text-green-400" viewBox="0 0 20 20" fill="currentColor">
          <path
            fillRule="evenodd"
            d="M16.707 5.293a1 1 0 00-1.414 0L8 12.586 4.707 9.293a1 1 0 00-1.414 1.414l4 4a1 1 0 001.414 0l8-8a1 1 0 000-1.414z"
            clipRule="evenodd"
          />
        </svg>
      </div>
      <p className="text-sm font-medium text-gray-200">{provider} 账户已成功关联</p>
      <p className="text-xs text-gray-500">正在跳转到账户管理…</p>
    </main>
  )
}

export default function LinkCallbackPage() {
  return (
    <Suspense>
      <LinkCallbackHandler />
    </Suspense>
  )
}