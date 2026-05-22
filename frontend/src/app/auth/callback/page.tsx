'use client'

import { useEffect } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { setToken } from '@/lib/auth'
import { Suspense } from 'react'

function CallbackHandler() {
  const router = useRouter()
  const params = useSearchParams()

  useEffect(() => {
    const token = params.get('token')
    if (token) {
      setToken(token)
      router.replace('/')
    } else {
      router.replace('/login')
    }
  }, [params, router])

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p className="text-sm text-gray-500">Signing in…</p>
    </main>
  )
}

export default function AuthCallbackPage() {
  return (
    <Suspense>
      <CallbackHandler />
    </Suspense>
  )
}