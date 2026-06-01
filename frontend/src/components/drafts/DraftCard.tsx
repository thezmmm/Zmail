import { CheckCircle, XCircle } from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import Button from '@/components/ui/Button'
import type { ProcessingResult } from '@/types'

interface DraftCardProps {
  draft: ProcessingResult
  onApprove: (id: string) => void
  onReject: (id: string) => void
  isApprovePending: boolean
  isRejectPending: boolean
  disabled: boolean
}

export default function DraftCard({ draft, onApprove, onReject, isApprovePending, isRejectPending, disabled }: DraftCardProps) {
  const timeAgo = formatDistanceToNow(new Date(draft.receivedAt ?? draft.processedAt), {
    addSuffix: true,
    locale: zhCN,
  })

  return (
    <div className="rounded-lg border border-gray-800 bg-gray-900 p-5">
      {/* Meta */}
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <p className="truncate text-xs text-gray-500">{draft.sender}</p>
          <p className="mt-0.5 truncate text-sm font-medium text-gray-100">
            {draft.subject ?? '（无主题）'}
          </p>
        </div>
        <span className="shrink-0 text-[10px] text-gray-600">{timeAgo}</span>
      </div>

      {/* Draft preview */}
      {draft.replyDraft && (
        <div className="mt-3 rounded-md border border-blue-900/40 bg-blue-950/20 p-3">
          <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-wider text-blue-500">
            草稿内容
          </p>
          <p className="line-clamp-4 whitespace-pre-wrap text-xs leading-relaxed text-gray-300">
            {draft.replyDraft}
          </p>
        </div>
      )}

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
          onClick={() => onApprove(draft.id)}
        >
          <CheckCircle className="h-3.5 w-3.5" />
          批准发送
        </Button>
      </div>
    </div>
  )
}
