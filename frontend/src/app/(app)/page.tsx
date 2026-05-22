'use client'

import { useState } from 'react'
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

  const results = data?.pages.flatMap(p => p.content) ?? []

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

            {hasNextPage && (
              <div className="flex justify-center pt-4 pb-2">
                <button
                  onClick={() => fetchNextPage()}
                  disabled={isFetchingNextPage}
                  className="flex items-center gap-2 rounded-md px-4 py-2 text-xs text-gray-500 transition-colors hover:bg-gray-800 hover:text-gray-300 disabled:opacity-50"
                >
                  {isFetchingNextPage ? <Spinner className="h-3.5 w-3.5" /> : '加载更多'}
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}