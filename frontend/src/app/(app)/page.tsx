'use client'

import { useState, useEffect, useRef, useMemo } from 'react'
import Link from 'next/link'
import { useResults } from '@/hooks/useResults'
import { useEmailSearch } from '@/hooks/useEmailSearch'
import ResultCard from '@/components/email/ResultCard'
import CategoryBadge from '@/components/email/CategoryBadge'
import PriorityBadge from '@/components/email/PriorityBadge'
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
  const [category, setCategory]       = useState<Category | undefined>(undefined)
  const [searchInput, setSearchInput] = useState('')
  const [searchQuery, setSearchQuery] = useState('')

  // Debounce search input by 400 ms
  useEffect(() => {
    const t = setTimeout(() => setSearchQuery(searchInput.trim()), 400)
    return () => clearTimeout(t)
  }, [searchInput])

  const isSearching = searchQuery.length > 0

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading, isError } =
    useResults(isSearching ? undefined : category)

  const {
    data: searchResults,
    isFetching: isSearchFetching,
    isError: isSearchError,
  } = useEmailSearch(searchQuery)

  const sentinelRef = useRef<HTMLDivElement>(null)
  const listRef     = useRef<HTMLDivElement>(null)

  // Scroll list back to top whenever the category filter changes
  useEffect(() => {
    listRef.current?.scrollTo({ top: 0 })
  }, [category])

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

  const results = useMemo(() => {
    const deduped = [
      ...new Map(
        (data?.pages.flatMap(p => p.content) ?? []).map(r => [r.id, r]),
      ).values(),
    ]
    return deduped.sort((a, b) => {
      const ta = new Date(a.receivedAt ?? a.processedAt).getTime()
      const tb = new Date(b.receivedAt ?? b.processedAt).getTime()
      return tb - ta
    })
  }, [data])

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="border-b border-gray-800 px-6 py-4">
        <h1 className="text-base font-semibold text-gray-100">收件箱</h1>

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
