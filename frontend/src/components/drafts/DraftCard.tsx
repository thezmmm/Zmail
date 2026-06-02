'use client'

import { useState } from 'react'
import { CheckCircle, XCircle, Bot, User } from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { cn } from '@/lib/cn'
import Button from '@/components/ui/Button'
import type { Draft } from '@/types'

interface DraftCardProps {
  draft: Draft
  onApprove: (id: string, body: string) => void
  onReject: (id: string) => void
  isApprovePending: boolean
  isRejectPending: boolean
  disabled: boolean
}

export default function DraftCard({
  draft, onApprove, onReject,
  isApprovePending, isRejectPending, disabled,
}: DraftCardProps) {
  const [body, setBody] = useState(draft.body)
  const isDirty = body !== draft.body

  const timeAgo = formatDistanceToNow(new Date(draft.createdAt), {
    addSuffix: true,
    locale: zhCN,
  })

  return (
    <div className="rounded-lg border border-gray-800 bg-gray-900 p-5">
      {/* Meta */}
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          {/* Source badge */}
          <div className="mb-1.5 flex items-center gap-1.5">
            {draft.source === 'AI' ? (
              <span className="flex items-center gap-1 rounded-full bg-blue-900/30 px-2 py-0.5 text-[10px] font-medium text-blue-400">
                <Bot className="h-2.5 w-2.5" />AI 生成
              </span>
            ) : (
              <span className="flex items-center gap-1 rounded-full bg-gray-800 px-2 py-0.5 text-[10px] font-medium text-gray-400">
                <User className="h-2.5 w-2.5" />手动撰写
              </span>
            )}
          </div>
          <p className="text-[10px] text-gray-500">
            收件人：{draft.to.join(', ')}
          </p>
          {draft.subject && (
            <p className="mt-0.5 truncate text-sm font-medium text-gray-100">
              {draft.subject}
            </p>
          )}
        </div>
        <span className="shrink-0 text-[10px] text-gray-600">{timeAgo}</span>
      </div>

      {/* Editable body */}
      <div className="mt-3">
        <div className="flex items-center justify-between mb-1">
          <p className="text-[10px] font-semibold uppercase tracking-wider text-blue-500">
            邮件正文
          </p>
          {isDirty && (
            <span className="text-[10px] text-amber-400">已修改</span>
          )}
        </div>
        <textarea
          value={body}
          onChange={e => setBody(e.target.value)}
          disabled={disabled}
          rows={6}
          className={cn(
            'w-full resize-y rounded-md border bg-gray-950 p-3 text-xs leading-relaxed text-gray-300',
            'placeholder:text-gray-600 focus:outline-none focus:ring-1 focus:ring-blue-600',
            'disabled:opacity-50 disabled:cursor-not-allowed',
            isDirty ? 'border-amber-800/50' : 'border-gray-800',
          )}
        />
      </div>

      {/* Actions */}
      <div className="mt-4 flex justify-end gap-2">
        <Button
          variant="danger"
          size="sm"
          loading={isRejectPending}
          disabled={disabled}
          onClick={() => onReject(draft.id)}
        >
          <XCircle className="h-3.5 w-3.5" />
          拒绝
        </Button>
        <Button
          variant="primary"
          size="sm"
          loading={isApprovePending}
          disabled={disabled}
          onClick={() => onApprove(draft.id, body)}
        >
          <CheckCircle className="h-3.5 w-3.5" />
          {isDirty ? '保存并发送' : '批准发送'}
        </Button>
      </div>
    </div>
  )
}
