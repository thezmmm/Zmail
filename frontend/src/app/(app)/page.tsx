'use client'

import { useState, useEffect, useRef } from 'react'
import { useResults } from '@/hooks/useResults'
import ResultCard from '@/components/email/ResultCard'
import EmptyState from '@/components/ui/EmptyState'
import Spinner from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import type { Category } from '@/types'

const TABS: { label: string; value: Category | undefined }[] = [
  { label: '全部',  value: undefined },
  { label: '工作',  value: 'WORK' },
  { label: '个人',  value: 'PERSONAL' },
  { label: '财务',  value: 'FINANCE' },
  { label: '推广',  value: 'PROMOTIONS' },
  { label: '其他',  value: 'OTHER' },
]

export default function InboxPage() {
  const [category, setCategory] = useState<Category | undefined>(undefined)
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading, isError } =
    useResults(category)

  const sentinelRef = useRef<HTMLDivElement>(null)

  // Trigger next page when sentinel enters viewport
  useEffect(() => {
    const el = sentinelRef.current
    if (!el) return
    const observer = new IntersectionObserver(
      entries => {
        if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
          fetchNextPage()
        }
      },
      { rootMargin: '200px' },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  const results = [
    ...new Map(
      (data?.pages.flatMap(p => p.content) ?? []).map(r => [r.id, r]),
    ).values(),
  ]

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="border-b border-gray-800 px-6 py-4">
        <h1 className="text-base font-semibold text-gray-100">收件箱</h1>
        <div className="mt-3 flex flex-wrap gap-1">
          {TABS.map(tab => (
            <button
              key={tab.label}
              onClick={() => setCategory(tab.value)}
              className={cn(
                'rounded-full px-3 py-1 text-xs transition-colors',
                category === tab.value
                  ? 'bg-gray-700 text-gray-100'
                  : 'text-gray-500 hover:bg-gray-800 hover:text-gray-300',
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* List */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        {isLoading ? (
          <div className="flex justify-center pt-20"><Spinner /></div>
        ) : isError ? (
          <EmptyState title="加载失败" description="请检查后端服务是否正常运行" />
        ) : results.length === 0 ? (
          <EmptyState title="暂无邮件" description="邮件处理完成后将显示在这里" />
        ) : (
          <div className="space-y-2">
            {results.map(result => (
              <ResultCard key={result.id} result={result} />
            ))}

            {/* Sentinel — triggers next page load when visible */}
            <div ref={sentinelRef} className="h-4" />

            {isFetchingNextPage && (
              <div className="flex justify-center py-3">
                <Spinner className="h-4 w-4" />
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
