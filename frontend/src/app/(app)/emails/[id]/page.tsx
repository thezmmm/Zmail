'use client'

import { useParams, useRouter } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle, XCircle, Archive, Flag, MailOpen } from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import api from '@/lib/api'
import { useApproveDraft, useRejectDraft } from '@/hooks/useDrafts'
import { useArchiveEmail, useFlagEmail, useMarkReadEmail } from '@/hooks/useEmailActions'
import PriorityBadge from '@/components/email/PriorityBadge'
import CategoryBadge from '@/components/email/CategoryBadge'
import Button from '@/components/ui/Button'
import Spinner from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import type { ApiResponse, ProcessingResult, Sentiment } from '@/types'

const sentimentStyles: Record<Sentiment, string> = {
  POSITIVE: 'text-green-400',
  NEUTRAL:  'text-gray-400',
  NEGATIVE: 'text-red-400',
}

const sentimentLabels: Record<Sentiment, string> = {
  POSITIVE: '积极',
  NEUTRAL:  '中性',
  NEGATIVE: '消极',
}

export default function EmailDetailPage() {
  const { id } = useParams<{ id: string }>()
  const router = useRouter()

  const { data: result, isLoading, isError } = useQuery({
    queryKey: ['results', id],
    queryFn: () =>
      api
        .get<ApiResponse<ProcessingResult>>(`/results/${id}`)
        .then(r => r.data.data!),
    enabled: !!id,
  })

  const approve  = useApproveDraft()
  const reject   = useRejectDraft()
  const archive  = useArchiveEmail()
  const flag     = useFlagEmail()
  const markRead = useMarkReadEmail()

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Spinner className="h-6 w-6" />
      </div>
    )
  }

  if (isError || !result) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3">
        <p className="text-sm text-gray-500">加载失败</p>
        <Button variant="ghost" size="sm" onClick={() => router.back()}>
          返回
        </Button>
      </div>
    )
  }

  const timeAgo = formatDistanceToNow(new Date(result.processedAt), {
    addSuffix: true,
    locale: zhCN,
  })

  return (
    <div className="mx-auto max-w-2xl px-6 py-6">
      {/* Back */}
      <button
        onClick={() => router.back()}
        className="mb-4 flex items-center gap-1.5 text-xs text-gray-500 transition-colors hover:text-gray-300"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        返回
      </button>

      {/* Header */}
      <div className="rounded-lg border border-gray-800 bg-gray-900 p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <h1 className="text-base font-semibold text-gray-100 leading-snug">
              {result.subject ?? '（无主题）'}
            </h1>
            <p className="mt-1 text-xs text-gray-500">{result.sender}</p>
          </div>
          <PriorityBadge priority={result.priority} />
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <CategoryBadge category={result.category} />
          <span className={cn('text-xs font-medium', sentimentStyles[result.sentiment])}>
            {sentimentLabels[result.sentiment]}
          </span>
          <span className="ml-auto text-[10px] text-gray-600">{timeAgo}</span>
        </div>
      </div>

      {/* Summary */}
      <section className="mt-4 rounded-lg border border-gray-800 bg-gray-900 p-5">
        <h2 className="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-500">
          AI 摘要
        </h2>
        <p className="text-sm leading-relaxed text-gray-300">
          {result.summary ?? '暂无摘要'}
        </p>
      </section>

      {/* Action Items */}
      {result.actionItems && result.actionItems.length > 0 && (
        <section className="mt-4 rounded-lg border border-gray-800 bg-gray-900 p-5">
          <h2 className="mb-3 text-xs font-semibold uppercase tracking-wider text-gray-500">
            待办事项
          </h2>
          <ul className="space-y-2">
            {result.actionItems.map((item, i) => (
              <li key={i} className="flex items-start gap-2 text-sm text-gray-300">
                <span className="mt-0.5 h-4 w-4 shrink-0 rounded-full border border-gray-600 flex items-center justify-center">
                  <span className="h-1.5 w-1.5 rounded-full bg-gray-500" />
                </span>
                {item}
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Draft */}
      {result.draftStatus === 'PENDING_REVIEW' && result.replyDraft && (
        <section className="mt-4 rounded-lg border border-blue-800/50 bg-blue-950/30 p-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-blue-400">
              草稿待审批
            </h2>
            <div className="flex gap-2">
              <Button
                variant="danger"
                size="sm"
                loading={reject.isPending}
                onClick={() => reject.mutate(result.id)}
              >
                <XCircle className="h-3.5 w-3.5" />
                拒绝
              </Button>
              <Button
                variant="primary"
                size="sm"
                loading={approve.isPending}
                onClick={() => approve.mutate(result.id)}
              >
                <CheckCircle className="h-3.5 w-3.5" />
                批准发送
              </Button>
            </div>
          </div>
          <pre className="whitespace-pre-wrap rounded-md bg-gray-900/60 p-4 text-xs leading-relaxed text-gray-300">
            {result.replyDraft}
          </pre>
        </section>
      )}

      {result.draftStatus === 'SENT' && (
        <div className="mt-4 rounded-lg border border-green-800/40 bg-green-950/20 px-4 py-3">
          <p className="text-xs text-green-400">草稿已发送</p>
        </div>
      )}

      {result.draftStatus === 'REJECTED' && (
        <div className="mt-4 rounded-lg border border-gray-800 bg-gray-900/40 px-4 py-3">
          <p className="text-xs text-gray-500">草稿已拒绝</p>
        </div>
      )}

      {/* Manual actions */}
      <section className="mt-4 rounded-lg border border-gray-800 bg-gray-900 p-5">
        <h2 className="mb-3 text-xs font-semibold uppercase tracking-wider text-gray-500">
          手动操作
        </h2>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            size="sm"
            loading={archive.isPending}
            onClick={() =>
              archive.mutate({
                emailProviderId: result.emailProviderId,
                accountId: result.accountId,
              })
            }
          >
            <Archive className="h-3.5 w-3.5" />
            归档
          </Button>
          <Button
            variant="secondary"
            size="sm"
            loading={flag.isPending}
            onClick={() =>
              flag.mutate({
                emailProviderId: result.emailProviderId,
                accountId: result.accountId,
              })
            }
          >
            <Flag className="h-3.5 w-3.5" />
            标记
          </Button>
          <Button
            variant="secondary"
            size="sm"
            loading={markRead.isPending}
            onClick={() =>
              markRead.mutate({
                emailProviderId: result.emailProviderId,
                accountId: result.accountId,
              })
            }
          >
            <MailOpen className="h-3.5 w-3.5" />
            标记已读
          </Button>
        </div>
      </section>
    </div>
  )
}
