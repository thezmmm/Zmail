import Link from 'next/link'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import PriorityBadge from './PriorityBadge'
import CategoryBadge from './CategoryBadge'
import type { ProcessingResult } from '@/types'

const actionLabels: Record<string, string> = {
  REPLY:   '已回复',
  ARCHIVE: '已归档',
  FLAG:    '已标记',
  NONE:    '待处理',
}

export default function ResultCard({ result }: { result: ProcessingResult }) {
  const timeAgo = formatDistanceToNow(new Date(result.processedAt), {
    addSuffix: true,
    locale: zhCN,
  })

  return (
    <Link
      href={`/emails/${result.id}`}
      className="block rounded-lg border border-gray-800 bg-gray-900 p-4 transition-colors hover:border-gray-700 hover:bg-gray-900/80"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <p className="truncate text-xs text-gray-500">{result.sender}</p>
          <p className="mt-0.5 truncate text-sm font-medium text-gray-100">
            {result.subject ?? '（无主题）'}
          </p>
          {result.summary && (
            <p className="mt-1 line-clamp-2 text-xs leading-relaxed text-gray-400">
              {result.summary}
            </p>
          )}
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1.5">
          <PriorityBadge priority={result.priority} />
          <span className="text-[10px] text-gray-600">{timeAgo}</span>
        </div>
      </div>

      <div className="mt-3 flex items-center gap-2">
        <CategoryBadge category={result.category} />
        <span className={`text-[10px] ${result.actionTaken === 'NONE' ? 'text-yellow-600' : 'text-gray-600'}`}>
          {actionLabels[result.actionTaken] ?? result.actionTaken}
        </span>
        {result.draftStatus === 'PENDING_REVIEW' && (
          <span className="ml-auto rounded-full bg-blue-900/30 px-2 py-0.5 text-[10px] font-medium text-blue-400">
            草稿待审
          </span>
        )}
      </div>
    </Link>
  )
}