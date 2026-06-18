'use client'

import { useState } from 'react'
import { X, Search } from 'lucide-react'
import { useResults } from '@/hooks/useResults'
import { cn } from '@/lib/cn'
import type { ProcessingResult } from '@/types'

interface EmailPickerPanelProps {
  selected: ProcessingResult[]
  onToggle: (result: ProcessingResult) => void
  onClose: () => void
}

export default function EmailPickerPanel({
  selected,
  onToggle,
  onClose,
}: EmailPickerPanelProps) {
  const [filter, setFilter] = useState('')
  const { data } = useResults(undefined, 0)

  const allEmails = data?.content ?? []
  const filtered = filter.trim()
    ? allEmails.filter(
        e =>
          e.subject?.toLowerCase().includes(filter.toLowerCase()) ||
          e.sender?.toLowerCase().includes(filter.toLowerCase()),
      )
    : allEmails

  const selectedIds = new Set(selected.map(e => e.id))

  return (
    <div className="border-t border-gray-800 bg-gray-950">
      <div className="flex items-center justify-between px-4 pt-3 pb-2">
        <span className="text-xs font-medium text-gray-400">选择邮件附加到消息</span>
        <button
          onClick={onClose}
          className="rounded p-0.5 text-gray-500 hover:bg-gray-800 hover:text-gray-300"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>

      <div className="relative mx-4 mb-2">
        <Search className="absolute left-2.5 top-1/2 h-3 w-3 -translate-y-1/2 text-gray-600" />
        <input
          type="text"
          value={filter}
          onChange={e => setFilter(e.target.value)}
          placeholder="搜索主题或发件人…"
          className="w-full rounded-md border border-gray-800 bg-gray-900 py-1 pl-7 pr-3 text-xs text-gray-300 placeholder-gray-600 outline-none focus:border-gray-700"
        />
      </div>

      <ul className="max-h-44 overflow-y-auto px-2 pb-2 space-y-0.5">
        {filtered.length === 0 ? (
          <li className="px-2 py-3 text-center text-xs text-gray-600">
            {allEmails.length === 0 ? '暂无已处理邮件' : '无匹配结果'}
          </li>
        ) : (
          filtered.slice(0, 30).map(email => {
            const isSelected = selectedIds.has(email.id)
            return (
              <li key={email.id}>
                <button
                  onClick={() => onToggle(email)}
                  className={cn(
                    'flex w-full items-start gap-2.5 rounded-md px-2.5 py-1.5 text-left transition-colors',
                    isSelected
                      ? 'bg-blue-900/40 text-blue-200'
                      : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200',
                  )}
                >
                  <div
                    className={cn(
                      'mt-0.5 h-3.5 w-3.5 shrink-0 rounded border transition-colors',
                      isSelected
                        ? 'border-blue-500 bg-blue-500'
                        : 'border-gray-600',
                    )}
                  >
                    {isSelected && (
                      <svg viewBox="0 0 12 12" className="h-full w-full text-white" fill="none" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M2 6l3 3 5-5" />
                      </svg>
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-xs font-medium leading-tight">
                      {email.subject ?? '（无主题）'}
                    </p>
                    <p className="truncate text-[10px] text-gray-500">{email.sender}</p>
                  </div>
                </button>
              </li>
            )
          })
        )}
      </ul>
    </div>
  )
}