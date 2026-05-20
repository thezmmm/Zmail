'use client'

import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'

interface HealthResponse {
  status: string
  service: string
  timestamp: string
}

export default function HomePage() {
  const { data, isLoading, isError } = useQuery<HealthResponse>({
    queryKey: ['health'],
    queryFn: () => api.get<HealthResponse>('/health').then(r => r.data),
    retry: false,
    refetchInterval: 10_000,
  })

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8">
      <div className="text-center">
        <h1 className="text-3xl font-semibold tracking-tight">Zmail</h1>
        <p className="mt-1 text-sm text-gray-500">AI-powered email agent</p>
      </div>

      <div className="rounded-lg border border-gray-800 bg-gray-900 px-6 py-4 text-sm">
        <div className="flex items-center gap-3">
          <span className="text-gray-400">Backend</span>
          {isLoading ? (
            <StatusBadge color="gray" label="checking…" />
          ) : isError ? (
            <StatusBadge color="red" label="unreachable" />
          ) : (
            <StatusBadge color="green" label={data?.status ?? 'UP'} />
          )}
        </div>
        {data && (
          <p className="mt-2 text-xs text-gray-600">
            {data.service} · {new Date(data.timestamp).toLocaleTimeString()}
          </p>
        )}
      </div>
    </main>
  )
}

function StatusBadge({ color, label }: { color: 'green' | 'red' | 'gray'; label: string }) {
  const styles = {
    green: 'bg-green-900/40 text-green-400',
    red: 'bg-red-900/40 text-red-400',
    gray: 'bg-gray-800 text-gray-500',
  }
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${styles[color]}`}>
      {label}
    </span>
  )
}