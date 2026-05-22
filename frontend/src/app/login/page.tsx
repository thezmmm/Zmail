'use client'

import { useEffect } from 'react'
import { isAuthenticated } from '@/lib/auth'
import { useRouter } from 'next/navigation'

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1'

export default function LoginPage() {
  const router = useRouter()

  useEffect(() => {
    if (isAuthenticated()) router.replace('/')
  }, [router])

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-10">
      <div className="text-center">
        <h1 className="text-3xl font-semibold tracking-tight">Zmail</h1>
        <p className="mt-1 text-sm text-gray-500">Connect your email to get started</p>
      </div>

      <div className="flex flex-col gap-3 w-72">
        <a
          href={`${API_BASE}/auth/gmail/login`}
          className="flex items-center justify-center gap-3 rounded-lg border border-gray-700 bg-gray-900 px-4 py-3 text-sm font-medium transition-colors hover:bg-gray-800"
        >
          <GoogleIcon />
          Continue with Gmail
        </a>

        <a
          href={`${API_BASE}/auth/msgraph/login`}
          className="flex items-center justify-center gap-3 rounded-lg border border-gray-700 bg-gray-900 px-4 py-3 text-sm font-medium transition-colors hover:bg-gray-800"
        >
          <MicrosoftIcon />
          Continue with Outlook
        </a>
      </div>
    </main>
  )
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
        fill="#4285F4"
      />
      <path
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
        fill="#34A853"
      />
      <path
        d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z"
        fill="#FBBC05"
      />
      <path
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
        fill="#EA4335"
      />
    </svg>
  )
}

function MicrosoftIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M11.4 2H2v9.4h9.4V2z" fill="#F25022" />
      <path d="M22 2h-9.4v9.4H22V2z" fill="#7FBA00" />
      <path d="M11.4 12.6H2V22h9.4v-9.4z" fill="#00A4EF" />
      <path d="M22 12.6h-9.4V22H22v-9.4z" fill="#FFB900" />
    </svg>
  )
}