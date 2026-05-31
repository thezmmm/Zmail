'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { isAuthenticated, clearToken } from '@/lib/auth'
import api from '@/lib/api'
import Sidebar from '@/components/layout/Sidebar'
import Spinner from '@/components/ui/Spinner'

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (!isAuthenticated()) {
      router.replace('/login')
      return
    }
    // Verify the JWT still points to a live user (guards against DB wipes or account deletion)
    api.get('/users/me').then(() => {
      setReady(true)
    }).catch(() => {
      clearToken()
      router.replace('/login')
    })
  }, [router])

  if (!ready) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-950">
        <Spinner className="h-6 w-6" />
      </div>
    )
  }

  return (
    <div className="flex h-screen overflow-hidden bg-gray-950">
      <Sidebar />
      <main className="flex-1 overflow-y-auto">
        {children}
      </main>
    </div>
  )
}