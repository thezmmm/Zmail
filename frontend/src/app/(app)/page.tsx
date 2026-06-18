'use client'

import { useState, useEffect, useRef } from 'react'
import Link from 'next/link'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useResults } from '@/hooks/useResults'
import { useEmailSearch } from '@/hooks/useEmailSearch'
import ResultCard from '@/components/email/ResultCard'
import CategoryBadge from '@/components/email/CategoryBadge'
import PriorityBadge from '@/components/email/PriorityBadge'
import EmptyState from '@/components/ui/EmptyState'
import Spinner from '@/components/ui/Spinner'
import Button from '@/components/ui/Button'
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
  const [category, setCategory]       = useState<Category | undefined>(undefined)
  const [page, setPage]               = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [searchQuery, setSearchQuery] = useState('')

  // Debounce search input by 400 ms
  useEffect(() => {
    const t = setTimeout(() => setSearchQuery(searchInput.trim()), 400)
    return () => clearTimeout(t)
  }, [searchInput])

  const isSearching = searchQuery.length > 0

  const { data, isLoading, isFetching, isError } =
    useResults(isSearching ? undefined : category, page)

  const {
    data: searchResults,
    isFetching: isSearchFetching,
    isError: isSearchError,
  } = useEmailSearch(searchQuery)

  const listRef = useRef<HTMLDivElement>(null)

  // Scroll list back to top whenever the page or category filter changes
  useEffect(() => {
    listRef.current?.scrollTo({ top: 0 })
  }, [category, page])

  function changeCategory(value: Category | undefined) {
    setCategory(value)
    setPage(0)
  }

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="border-b border-gray-800 px-6 py-4">
        <div className="flex items-center justify-between">
          <h1 className="text-base font-semibold text-gray-100">收件箱</h1>
        </div>

        {/* Search */}
        <div className="mt-3 relative">
          <input
            type="text"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="语义搜索邮件…"
            className="w-full rounded-lg border border-gray-700 bg-gray-800 px-3 py-1.5 text-xs text-gray-100 placeholder-gray-500 outline-none focus:border-gray-500"
          />
          {searchInput && (
            <button
              onClick={() => { setSearchInput(''); setSearchQuery('') }}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300 text-xs"
            >
              ✕
            </button>
          )}
        </div>

        {/* Category tabs — hidden while searching */}
        {!isSearching && (
          <div className="mt-2 flex flex-wrap gap-1">
            {TABS.map(tab => (
              <button
                key={tab.label}
                onClick={() => changeCategory(tab.value)}
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
        )}
      </div>

      {/* List */}
      <div ref={listRef} className="flex-1 overflow-y-auto px-6 py-4">
        {isSearching ? (
          isSearchFetching ? (
            <div className="flex justify-center pt-20"><Spinner /></div>
          ) : isSearchError ? (
            <EmptyState title="搜索失败" description="请检查后端服务是否正常运行" />
          ) : !searchResults || searchResults.length === 0 ? (
            <EmptyState title="未找到相关邮件" description="尝试换个关键词或描述" />
          ) : (
            <div className="space-y-2">
              <p className="text-[10px] text-gray-500 mb-1">找到 {searchResults.length} 封相关邮件</p>
              {searchResults.map(r => (
                <Link
                  key={r.id}
                  href={`/emails/${r.id}`}
                  className="block rounded-lg border border-gray-800 bg-gray-900 p-4 transition-colors hover:border-gray-700 hover:bg-gray-900/80"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-xs text-gray-500">{r.sender}</p>
                      <p className="mt-0.5 truncate text-sm font-medium text-gray-100">
                        {r.subject ?? '（无主题）'}
                      </p>
                      {r.summary && (
                        <p className="mt-1 line-clamp-2 text-xs leading-relaxed text-gray-400">
                          {r.summary}
                        </p>
                      )}
                    </div>
                    <PriorityBadge priority={r.priority} />
                  </div>
                  <div className="mt-3">
                    <CategoryBadge category={r.category} />
                  </div>
                </Link>
              ))}
            </div>
          )
        ) : isLoading ? (
          <div className="flex justify-center pt-20"><Spinner /></div>
        ) : isError ? (
          <EmptyState title="加载失败" description="请检查后端服务是否正常运行" />
        ) : !data || (data.content.length === 0 && page === 0) ? (
          <EmptyState title="暂无邮件" description="邮件处理完成后将显示在这里" />
        ) : (
          <>
            {isFetching ? (
              <div className="flex justify-center py-16"><Spinner /></div>
            ) : data.content.length === 0 ? (
              <EmptyState title="没有更多邮件了" description="已经是最后一页" />
            ) : (
              <div className="space-y-2">
                {data.content.map(result => (
                  <ResultCard key={result.id} result={result} />
                ))}
              </div>
            )}

            {/* Pagination — moving past the last synced page triggers the backend to sync more.
                Next stays enabled even on what looks like the last page: only an empty result
                after actually trying the next page means there's truly nothing further. */}
            <div className="mt-4 flex items-center justify-between">
              <Button
                variant="secondary"
                size="sm"
                disabled={page === 0 || isFetching}
                onClick={() => setPage(p => Math.max(0, p - 1))}
              >
                <ChevronLeft className="h-3.5 w-3.5" />上一页
              </Button>
              <p className="text-[10px] text-gray-500">
                {isFetching ? '同步中…' : `第 ${data.number + 1} 页`}
              </p>
              <Button
                variant="secondary"
                size="sm"
                disabled={isFetching || data.content.length === 0}
                onClick={() => setPage(p => p + 1)}
              >
                下一页<ChevronRight className="h-3.5 w-3.5" />
              </Button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
