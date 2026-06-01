'use client'

import DOMPurify from 'dompurify'
import { useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import {
  ArrowLeft, CheckCircle, XCircle, Archive, Flag, MailOpen, Sparkles, FileText,
} from 'lucide-react'
import { format } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import api from '@/lib/api'
import { useApproveDraft, useRejectDraft } from '@/hooks/useDrafts'
import { useArchiveEmail, useFlagEmail, useMarkReadEmail } from '@/hooks/useEmailActions'
import { useAnalyzeResult, useGenerateDraft } from '@/hooks/useResults'
import PriorityBadge from '@/components/email/PriorityBadge'
import CategoryBadge from '@/components/email/CategoryBadge'
import Button from '@/components/ui/Button'
import Spinner from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import type { ApiResponse, ActionTaken, EmailMessage, ProcessingResult, Sentiment } from '@/types'

const sentimentLabel: Record<Sentiment, string> = {
  POSITIVE: '积极', NEUTRAL: '中性', NEGATIVE: '消极',
}
const sentimentCls: Record<Sentiment, string> = {
  POSITIVE: 'bg-green-900/40 text-green-400 border-green-800/50',
  NEUTRAL:  'bg-gray-800/60 text-gray-400 border-gray-700/50',
  NEGATIVE: 'bg-red-900/40 text-red-400 border-red-800/50',
}
const actionLabel: Record<ActionTaken, string> = {
  REPLY: '建议回复', ARCHIVE: '建议归档', FLAG: '建议标记', NONE: '无需操作',
}
const actionCls: Record<ActionTaken, string> = {
  REPLY:   'bg-blue-900/30 text-blue-400 border-blue-800/50',
  ARCHIVE: 'bg-gray-800/60 text-gray-400 border-gray-700/50',
  FLAG:    'bg-amber-900/30 text-amber-400 border-amber-800/50',
  NONE:    'bg-gray-800/40 text-gray-500 border-gray-700/40',
}

function isHtml(body: string) {
  const t = body.trimStart().toLowerCase()
  return t.startsWith('<!doctype') || t.startsWith('<html') || t.includes('<body')
}

export default function EmailDetailClient() {
  const { id } = useParams<{ id: string }>()
  const router = useRouter()

  const { data: result, isLoading, isError } = useQuery({
    queryKey: ['results', id],
    queryFn: () =>
      api.get<ApiResponse<ProcessingResult>>(`/results/${id}`).then(r => r.data.data!),
    enabled: !!id,
  })

  const { data: email, isLoading: emailLoading } = useQuery({
    queryKey: ['email-body', result?.emailProviderId, result?.accountId],
    queryFn: () =>
      api
        .get<ApiResponse<EmailMessage>>(
          `/emails/${result!.emailProviderId}?accountId=${result!.accountId}`,
        )
        .then(r => r.data.data!),
    enabled: !!result,
  })

  const analyze       = useAnalyzeResult(id)
  const generateDraft = useGenerateDraft(id)
  const approve       = useApproveDraft()
  const reject        = useRejectDraft()
  const archive       = useArchiveEmail()
  const flag          = useFlagEmail()
  const markRead      = useMarkReadEmail()

  useEffect(() => {
    if (result && !result.analyzed && !analyze.isPending && !analyze.isSuccess) {
      analyze.mutate()
    }
  }, [result?.id, result?.analyzed]) // eslint-disable-line react-hooks/exhaustive-deps

  // Prefer the query cache (result) over the mutation snapshot (analyze.data):
  // - useAnalyzeResult.onSuccess already writes to ['results', id], so result stays fresh
  // - Approve/reject optimistic updates write to ['results', id], so they surface immediately
  const current: ProcessingResult | undefined = result ?? analyze.data

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Spinner className="h-6 w-6" />
      </div>
    )
  }
  if (isError || !current) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3">
        <p className="text-sm text-gray-500">加载失败</p>
        <Button variant="ghost" size="sm" onClick={() => router.back()}>返回</Button>
      </div>
    )
  }

  const receivedAt = format(
    new Date(current.receivedAt ?? current.processedAt),
    'yyyy年M月d日 HH:mm',
    { locale: zhCN },
  )

  const htmlBody  = email?.body ? isHtml(email.body) : false
  const plainBody = email?.body && !htmlBody ? email.body : null
  const analyzing = analyze.isPending

  return (
    <div className="flex h-full flex-col overflow-hidden">

      {/* ── 操作栏 ── */}
      <div className="flex flex-none items-center justify-between border-b border-gray-800 px-5 py-2.5">
        <button
          onClick={() => router.back()}
          className="flex items-center gap-1.5 text-xs text-gray-500 transition-colors hover:text-gray-300"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          返回
        </button>
        <div className="flex items-center gap-1.5">
          <Button variant="secondary" size="sm" loading={archive.isPending}
            onClick={() => archive.mutate({ emailProviderId: current.emailProviderId, accountId: current.accountId })}>
            <Archive className="h-3.5 w-3.5" />归档
          </Button>
          <Button variant="secondary" size="sm" loading={flag.isPending}
            onClick={() => flag.mutate({ emailProviderId: current.emailProviderId, accountId: current.accountId })}>
            <Flag className="h-3.5 w-3.5" />标记
          </Button>
          <Button variant="secondary" size="sm" loading={markRead.isPending}
            onClick={() => markRead.mutate({ emailProviderId: current.emailProviderId, accountId: current.accountId })}>
            <MailOpen className="h-3.5 w-3.5" />已读
          </Button>
        </div>
      </div>

      {/* ── 信息区（全宽，随内容自适应高度） ── */}
      <div className="flex-none border-b border-gray-800 px-6 py-4 space-y-3">

        {/* 主题 + 发件人 + 时间 */}
        <div>
          <h1 className="text-lg font-semibold leading-snug text-gray-100">
            {current.subject || '（无主题）'}
          </h1>
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-500">
            <span className="font-medium text-gray-400">{current.sender}</span>
            <span>·</span>
            <span>{receivedAt}</span>
          </div>
        </div>

        {/* 分类徽章 */}
        <div className="flex flex-wrap items-center gap-1.5">
          <CategoryBadge category={current.category} />
          <PriorityBadge priority={current.priority} />
          {current.sentiment in sentimentLabel && (
            <span className={cn(
              'rounded-full border px-2 py-0.5 text-[11px] font-medium',
              sentimentCls[current.sentiment],
            )}>
              {sentimentLabel[current.sentiment]}
            </span>
          )}
          {current.requiresResponse && (
            <span className="rounded-full border border-amber-800/50 bg-amber-900/30 px-2 py-0.5 text-[11px] font-medium text-amber-400">
              需要回复
            </span>
          )}
          {current.actionTaken && current.actionTaken !== 'NONE' && (
            <span className={cn(
              'flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium',
              actionCls[current.actionTaken],
            )}>
              <Sparkles className="h-2.5 w-2.5" />
              {actionLabel[current.actionTaken]}
            </span>
          )}
        </div>

        {/* AI 分析 */}
        {analyzing ? (
          <div className="flex items-center gap-2 text-xs text-gray-500">
            <Spinner className="h-3.5 w-3.5" />
            AI 正在分析…
          </div>
        ) : (
          <div className="flex gap-6">
            {/* 摘要 */}
            <div className="min-w-0 flex-1">
              <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-gray-600">AI 摘要</p>
              <p className="text-xs leading-relaxed text-gray-300">{current.summary || '暂无摘要'}</p>
            </div>
            {/* 待办 */}
            {current.actionItems && current.actionItems.length > 0 && (
              <div className="w-64 shrink-0">
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-gray-600">待办事项</p>
                <ul className="space-y-1">
                  {current.actionItems.map((item, i) => (
                    <li key={i} className="flex items-start gap-2 text-xs text-gray-300">
                      <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-gray-500" />
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {/* 草稿 */}
        {generateDraft.isPending ? (
          /* 生成中：骨架屏占位，避免按钮点击后毫无反馈 */
          <div className="animate-pulse rounded-xl border border-blue-800/30 bg-blue-950/20 p-3.5">
            <div className="mb-3 flex items-center justify-between">
              <div className="h-2.5 w-16 rounded bg-blue-900/50" />
              <div className="flex gap-1">
                <div className="h-6 w-10 rounded-md bg-gray-800/70" />
                <div className="h-6 w-14 rounded-md bg-gray-800/70" />
              </div>
            </div>
            <div className="space-y-2 rounded-lg bg-gray-900/60 p-3">
              <div className="h-2 w-full rounded bg-gray-700/60" />
              <div className="h-2 w-10/12 rounded bg-gray-700/60" />
              <div className="h-2 w-11/12 rounded bg-gray-700/60" />
              <div className="h-2 w-8/12 rounded bg-gray-700/60" />
              <div className="h-2 w-full rounded bg-gray-700/60" />
              <div className="h-2 w-9/12 rounded bg-gray-700/60" />
            </div>
          </div>
        ) : current.draftStatus === 'PENDING_REVIEW' && current.replyDraft ? (
          <div className={cn(
            'rounded-xl border border-blue-800/50 bg-blue-950/30 p-3.5 transition-opacity duration-150',
            (approve.isPending || reject.isPending) && 'pointer-events-none opacity-50',
          )}>
            <div className="mb-2 flex items-center justify-between">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-blue-400">草稿待审批</p>
              <div className="flex gap-1">
                <Button variant="danger" size="sm" loading={reject.isPending}
                  onClick={() => reject.mutate(current.id)}>
                  <XCircle className="h-3 w-3" />拒绝
                </Button>
                <Button variant="primary" size="sm" loading={approve.isPending}
                  onClick={() => approve.mutate(current.id)}>
                  <CheckCircle className="h-3 w-3" />批准发送
                </Button>
              </div>
            </div>
            <pre className="whitespace-pre-wrap rounded-lg bg-gray-900/60 p-3 text-[11px] leading-relaxed text-gray-300">
              {current.replyDraft}
            </pre>
          </div>
        ) : current.draftStatus === 'SENT' ? (
          <p className="text-xs text-green-400">草稿已发送</p>
        ) : current.draftStatus === 'REJECTED' ? (
          <div className="flex items-center gap-3">
            <p className="text-xs text-gray-500">草稿已拒绝</p>
            <Button variant="secondary" size="sm" onClick={() => generateDraft.mutate()}>
              <FileText className="h-3 w-3" />重新生成
            </Button>
          </div>
        ) : !current.replyDraft ? (
          <div>
            <Button variant="secondary" size="sm" onClick={() => generateDraft.mutate()}>
              <FileText className="h-3.5 w-3.5" />生成草稿
            </Button>
          </div>
        ) : null}
      </div>

      {/* ── 原文（撑满剩余高度，独立滚动，隐藏滚动条） ── */}
      <div className={cn(
        'flex-1 overflow-y-auto [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]',
        htmlBody && email?.body ? 'bg-white' : 'p-6',
      )}>
        {emailLoading ? (
          <div className="flex items-center justify-center py-12">
            <Spinner className="h-4 w-4" />
          </div>
        ) : htmlBody && email?.body ? (
          <div
            className="prose prose-sm max-w-none p-6 text-gray-900"
            dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(email.body) }}
          />
        ) : plainBody ? (
          <pre className="whitespace-pre-wrap text-sm leading-relaxed text-gray-300">
            {plainBody}
          </pre>
        ) : (
          <p className="text-sm text-gray-600">无原文内容</p>
        )}
      </div>

    </div>
  )
}
